<?php

declare(strict_types=1);

namespace App\Sync;

use App\Core\Auth;
use App\Core\Database;
use RuntimeException;
use Throwable;

/**
 * The shop's computers running the desktop app: activation (the owner signs
 * in once, online), authenticating each later request, and switching a
 * computer off.
 *
 * Errors are RuntimeExceptions whose message is a code the API returns and
 * the app translates (see ApiController).
 */
final class DeviceService
{
    /**
     * Registers this computer for the owner's shop and returns
     * ['device' => row, 'secret' => string]. The secret is what the computer
     * authenticates with from now on; only its hash is stored, so this is
     * the one time it exists outside the computer.
     *
     * Reactivating on a computer that already had the app (same shop, same
     * fingerprint — a reinstall) retires the old record and issues a new
     * code, so the receipt numbers the new install starts from can never
     * collide with ones the old install printed.
     */
    public static function activate(
        string $login,
        string $password,
        string $deviceUuid,
        string $fingerprint,
        string $name,
        string $appVersion
    ): array {
        if (!self::isUuid($deviceUuid) || !preg_match('/^[a-f0-9]{64}$/', $fingerprint)) {
            throw new RuntimeException('invalid_request');
        }

        $user = Auth::verifyCredentials($login, $password);
        if ($user === null) {
            throw new RuntimeException(Auth::wasLockedOut() ? 'login_locked' : 'login_failed');
        }
        if ($user['role'] !== 'owner') {
            throw new RuntimeException('owner_only');
        }

        return self::register($user, $deviceUuid, $fingerprint, $name, $appVersion);
    }

    /** How long a "Telefonni ulash" code works. */
    public const PAIR_CODE_MINUTES = 10;

    /**
     * A new one-time 6-digit code for the owner's shop (earlier unused ones
     * stop working). Shown once on the website, typed into the phone app.
     */
    public static function createPairCode(int $shopId, int $userId): string
    {
        $pdo = Database::connect();
        $pdo->prepare("UPDATE device_pair_codes SET used_at = datetime('now') WHERE shop_id = ? AND used_at IS NULL")->execute([$shopId]);

        // Unique among the codes that still work, so a code names one shop.
        do {
            $code = str_pad((string) random_int(0, 999999), 6, '0', STR_PAD_LEFT);
            $taken = $pdo->prepare("SELECT 1 FROM device_pair_codes WHERE code_hash = ? AND used_at IS NULL AND expires_at > datetime('now')");
            $taken->execute([hash('sha256', $code)]);
        } while ($taken->fetchColumn() !== false);

        $pdo->prepare(
            "INSERT INTO device_pair_codes (shop_id, user_id, code_hash, expires_at) VALUES (?, ?, ?, datetime('now', '+" . self::PAIR_CODE_MINUTES . " minutes'))"
        )->execute([$shopId, $userId, hash('sha256', $code)]);

        return $code;
    }

    /**
     * Activation with a pairing code instead of a password (owners who sign
     * in with Google / Telegram). At most 10 wrong codes per address per 10
     * minutes: a code has a million values and lives ten minutes.
     */
    public static function activateWithCode(
        string $code,
        string $deviceUuid,
        string $fingerprint,
        string $name,
        string $appVersion,
        string $clientIp
    ): array {
        if (!self::isUuid($deviceUuid) || !preg_match('/^[a-f0-9]{64}$/', $fingerprint)) {
            throw new RuntimeException('invalid_request');
        }

        $pdo = Database::connect();
        $limitKey = 'pair_fail_' . substr(hash('sha256', $clientIp), 0, 32);
        $stmt = $pdo->prepare('SELECT value FROM server_keys WHERE name = ?');
        $stmt->execute([$limitKey]);
        $fails = json_decode((string) $stmt->fetchColumn(), true);
        $window = is_array($fails) && ($fails['since'] ?? 0) > time() - 600 ? $fails : ['since' => time(), 'count' => 0];
        if ($window['count'] >= 10) {
            throw new RuntimeException('login_locked');
        }

        $code = preg_replace('/\D/', '', $code) ?? '';
        $row = null;
        if (strlen($code) === 6) {
            $find = $pdo->prepare(
                "SELECT * FROM device_pair_codes WHERE code_hash = ? AND used_at IS NULL AND expires_at > datetime('now') LIMIT 1"
            );
            $find->execute([hash('sha256', $code)]);
            $row = $find->fetch() ?: null;
        }

        $user = null;
        if ($row !== null) {
            $u = $pdo->prepare("SELECT * FROM users WHERE id = ? AND shop_id = ? AND role = 'owner' AND status = 'active'");
            $u->execute([(int) $row['user_id'], (int) $row['shop_id']]);
            $user = $u->fetch() ?: null;
            $shop = \App\Models\Shop::find((int) $row['shop_id']);
            if ($user !== null && (!$shop || $shop['status'] !== 'active' || \App\Models\Shop::accessExpired($shop))) {
                throw new RuntimeException('shop_blocked');
            }
        }

        if ($user === null) {
            $window['count']++;
            $pdo->prepare('INSERT INTO server_keys (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value')
                ->execute([$limitKey, json_encode($window)]);
            throw new RuntimeException('code_invalid');
        }

        $pdo->prepare("UPDATE device_pair_codes SET used_at = datetime('now') WHERE id = ?")->execute([(int) $row['id']]);

        return self::register($user, $deviceUuid, $fingerprint, $name, $appVersion);
    }

    private static function register(array $user, string $deviceUuid, string $fingerprint, string $name, string $appVersion): array
    {
        $shopId = (int) $user['shop_id'];
        $secret = bin2hex(random_bytes(32));

        $pdo = Database::beginImmediate();
        try {
            $existing = $pdo->prepare('SELECT * FROM devices WHERE uuid = ?');
            $existing->execute([$deviceUuid]);
            if ($existing->fetch() !== false) {
                // The app generates a fresh uuid for every install.
                throw new RuntimeException('invalid_request');
            }

            $pdo->prepare("UPDATE devices SET status = 'replaced' WHERE shop_id = ? AND fingerprint = ? AND status = 'active'")
                ->execute([$shopId, $fingerprint]);

            $codes = $pdo->prepare('SELECT code FROM devices WHERE shop_id = ?');
            $codes->execute([$shopId]);
            $highest = 0;
            foreach ($codes->fetchAll() as $row) {
                $highest = max($highest, (int) substr((string) $row['code'], 1));
            }

            $pdo->prepare(
                'INSERT INTO devices (shop_id, uuid, code, name, fingerprint, secret_hash, app_version, activated_by, last_seen_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime(\'now\'))'
            )->execute([
                $shopId,
                $deviceUuid,
                'K' . ($highest + 1),
                mb_substr(trim($name), 0, 100) ?: null,
                $fingerprint,
                hash('sha256', $secret),
                mb_substr($appVersion, 0, 20) ?: null,
                (int) $user['id'],
            ]);

            Database::commit($pdo);
        } catch (Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }

        return ['device' => self::findByUuid($deviceUuid), 'secret' => $secret];
    }

    /**
     * The device (with its shop) an "Authorization: Device <uuid>:<secret>"
     * header belongs to. Throws 'unauthorized' for anything that doesn't
     * match, 'device_revoked' / 'shop_blocked' when it matches but may no
     * longer work — the app locks itself on those two.
     *
     * @return array{device: array, shop: array}
     */
    public static function authenticate(string $authorization, string $appVersion = ''): array
    {
        if (!preg_match('/^Device ([A-Za-z0-9-]{32,36}):([a-f0-9]{64})$/', trim($authorization), $m)) {
            throw new RuntimeException('unauthorized');
        }

        $device = self::findByUuid($m[1]);
        if ($device === null || !hash_equals((string) $device['secret_hash'], hash('sha256', $m[2]))) {
            throw new RuntimeException('unauthorized');
        }

        if ($device['status'] !== 'active') {
            throw new RuntimeException('device_revoked');
        }

        $shop = Database::connect()->prepare('SELECT * FROM shops WHERE id = ?');
        $shop->execute([$device['shop_id']]);
        $shop = $shop->fetch();
        // A trial (or paid period) that ran out stops the computers too.
        if (!$shop || $shop['status'] !== 'active' || \App\Models\Shop::accessExpired($shop)) {
            throw new RuntimeException('shop_blocked');
        }

        Database::connect()
            ->prepare("UPDATE devices SET last_seen_at = datetime('now'), app_version = COALESCE(?, app_version) WHERE id = ?")
            ->execute([$appVersion !== '' ? mb_substr($appVersion, 0, 20) : null, $device['id']]);

        return ['device' => $device, 'shop' => $shop];
    }

    public static function findByUuid(string $uuid): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM devices WHERE uuid = ?');
        $stmt->execute([$uuid]);

        return $stmt->fetch() ?: null;
    }

    public static function allByShop(int $shopId): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT d.*, u.full_name AS activated_by_name
             FROM devices d LEFT JOIN users u ON u.id = d.activated_by
             WHERE d.shop_id = ?
             ORDER BY CASE d.status WHEN 'active' THEN 0 ELSE 1 END, d.id DESC"
        );
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }

    /** Switches a computer off; it stops at its next sync, or when its license runs out. */
    public static function revoke(int $deviceId, int $shopId): bool
    {
        $stmt = Database::connect()->prepare(
            "UPDATE devices SET status = 'revoked' WHERE id = ? AND shop_id = ? AND status = 'active'"
        );
        $stmt->execute([$deviceId, $shopId]);

        return $stmt->rowCount() === 1;
    }

    public static function isUuid(string $value): bool
    {
        return (bool) preg_match('/^[a-f0-9]{32}$|^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/', $value);
    }
}

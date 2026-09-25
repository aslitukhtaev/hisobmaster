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
        if (!$shop || $shop['status'] !== 'active') {
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

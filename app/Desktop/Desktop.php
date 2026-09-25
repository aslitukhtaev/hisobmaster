<?php

declare(strict_types=1);

namespace App\Desktop;

use App\Core\Database;
use App\Sync\License;

/**
 * The same application running inside the desktop app (APP_MODE=desktop):
 * the shell (desktop/ — Electron) starts PHP's built-in server on this code
 * with its own local database and passes, as environment variables:
 *
 *   APP_MODE=desktop
 *   DB_PATH              absolute path of the local database
 *   KASSIRON_SERVER      https://kassiron.uz
 *   DEVICE_FINGERPRINT   sha256 of this computer's Windows MachineGuid
 *   LICENSE_PUBLIC_KEY_B64  base64 of the server's PEM public key (built in)
 *   DESKTOP_APP_VERSION  version of the desktop app
 *
 * Its own bookkeeping lives in the desktop_state table (key => value).
 */
final class Desktop
{
    /** Days before the license runs out when the warning starts. */
    public const WARN_DAYS = 2;

    /** How far the clock may appear to go back before it counts as tampering. */
    private const CLOCK_TOLERANCE_SECONDS = 600;

    public static function enabled(): bool
    {
        return env('APP_MODE') === 'desktop';
    }

    public static function server(): string
    {
        return rtrim((string) env('KASSIRON_SERVER', 'https://kassiron.uz'), '/');
    }

    public static function fingerprint(): string
    {
        return strtolower((string) env('DEVICE_FINGERPRINT', ''));
    }

    public static function appVersion(): string
    {
        return (string) env('DESKTOP_APP_VERSION', '0.0.0');
    }

    public static function get(string $key, ?string $default = null): ?string
    {
        $stmt = Database::connect()->prepare('SELECT value FROM desktop_state WHERE key = ?');
        $stmt->execute([$key]);
        $value = $stmt->fetchColumn();

        return $value === false || $value === null ? $default : (string) $value;
    }

    public static function set(string $key, ?string $value): void
    {
        Database::connect()
            ->prepare('INSERT INTO desktop_state (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value')
            ->execute([$key, $value]);
    }

    public static function isActivated(): bool
    {
        return self::get('device_secret') !== null && self::get('local_shop_id') !== null;
    }

    public static function localShopId(): int
    {
        return (int) self::get('local_shop_id', '0');
    }

    /**
     * Whether this computer may record new sales/purchases/etc. right now,
     * and why not. States:
     *   not_activated — the owner hasn't signed in on this computer yet
     *   ok            — valid license (days_left tells how long offline)
     *   expired       — no successful sync for longer than the shop's offline_days
     *   revoked       — the owner / super admin switched this computer off
     *   shop_blocked  — the shop was blocked
     *   clock         — the computer's clock was moved back
     *   invalid       — missing/forged license, or one issued to another computer
     *
     * @return array{state: string, days_left: ?int, valid_until: ?int}
     */
    public static function licenseStatus(?int $now = null): array
    {
        $now ??= time();
        $result = static fn (string $state, ?array $payload = null): array => [
            'state' => $state,
            'valid_until' => $payload['valid_until'] ?? null,
            'days_left' => isset($payload['valid_until']) ? (int) ceil(($payload['valid_until'] - $now) / 86400) : null,
        ];

        if (!self::isActivated()) {
            return $result('not_activated');
        }

        $blocked = self::get('blocked_reason');
        if ($blocked === 'device_revoked' || $blocked === 'shop_blocked') {
            return $result($blocked === 'device_revoked' ? 'revoked' : 'shop_blocked');
        }

        $publicKey = (string) base64_decode((string) env('LICENSE_PUBLIC_KEY_B64', ''), true);
        $payload = $publicKey !== '' ? License::verify((string) self::get('license'), $publicKey) : null;
        if ($payload === null
            || ($payload['fingerprint'] ?? '') !== self::fingerprint()
            || ($payload['device_uuid'] ?? '') !== self::get('device_uuid')) {
            return $result('invalid', $payload);
        }

        if ($now < self::highWaterMark() - self::CLOCK_TOLERANCE_SECONDS) {
            return $result('clock', $payload);
        }

        if ($now > (int) $payload['valid_until']) {
            return $result('expired', $payload);
        }

        return $result('ok', $payload);
    }

    /**
     * The latest time this computer is known to have reached: the highest of
     * the recorded high-water mark and the newest sale — both only ever move
     * forward, so a clock set back to stretch an expiring license is caught.
     */
    public static function highWaterMark(): int
    {
        $stored = (int) self::get('clock_high_water', '0');
        $lastSale = Database::connect()->query('SELECT MAX(created_at) FROM sales')->fetchColumn();

        return max($stored, $lastSale ? (int) utc_timestamp((string) $lastSale) : 0);
    }

    public static function touchClock(?int $now = null): void
    {
        $now ??= time();
        if ($now > (int) self::get('clock_high_water', '0') + 60) {
            self::set('clock_high_water', (string) $now);
        }
    }

    /**
     * The next receipt number of this computer ("K2-000145"), counting on
     * from the last one. Called inside the sale's transaction, so two sales
     * can't get the same number.
     */
    public static function nextReceiptNo(): string
    {
        $next = (int) self::get('receipt_seq', '0') + 1;
        self::set('receipt_seq', (string) $next);

        return self::get('device_code', 'K') . '-' . str_pad((string) $next, 6, '0', STR_PAD_LEFT);
    }

    /**
     * What hasn't reached the server yet, in terms a cashier understands:
     * how many sales, and whether anything else (a product edit, an
     * expense...) is waiting too. One sale is several journal entries (the
     * sale, its lines, payments, stock), so the raw count would mislead.
     *
     * @return array{sales: int, other: bool}
     */
    public static function pending(): array
    {
        $pushedUpto = (int) self::get('pushed_upto', '0');
        $stmt = Database::connect()->prepare(
            "SELECT COUNT(DISTINCT CASE WHEN tbl = 'sales' THEN row_id END) AS sales, COUNT(*) AS total
             FROM sync_changes WHERE id > ?"
        );
        $stmt->execute([$pushedUpto]);
        $row = $stmt->fetch();

        return ['sales' => (int) $row['sales'], 'other' => (int) $row['total'] > 0 && (int) $row['sales'] === 0];
    }

    /** @return array<string, mixed> what the header indicator and the shell show */
    public static function status(): array
    {
        $license = self::licenseStatus();

        return [
            'activated' => self::isActivated(),
            'license' => $license,
            'pending' => self::isActivated() ? self::pending() : ['sales' => 0, 'other' => false],
            'last_sync_at' => self::get('last_sync_at'),
            'last_error' => self::get('last_sync_error'),
            'device_code' => self::get('device_code'),
            'app_version' => self::appVersion(),
        ];
    }

}

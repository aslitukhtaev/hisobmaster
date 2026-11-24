<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

/**
 * Thin key/value store per shop, backed by the generic `settings` table
 * already in schema.sql. Used so far for the shop-wide low-stock threshold
 * fallback (see Product::lowStock()).
 */
class Settings
{
    public static function get(int $shopId, string $key): ?string
    {
        $stmt = Database::connect()->prepare('SELECT value FROM settings WHERE shop_id = ? AND key = ?');
        $stmt->execute([$shopId, $key]);
        $value = $stmt->fetchColumn();

        return $value === false ? null : (string) $value;
    }

    public static function set(int $shopId, string $key, ?string $value): void
    {
        if ($value === null) {
            Database::connect()
                ->prepare('DELETE FROM settings WHERE shop_id = ? AND key = ?')
                ->execute([$shopId, $key]);
            return;
        }

        Database::connect()
            ->prepare('INSERT INTO settings (shop_id, key, value) VALUES (?, ?, ?)
                       ON CONFLICT(shop_id, key) DO UPDATE SET value = excluded.value')
            ->execute([$shopId, $key, $value]);
    }

    /** "Optom savdo": products get a wholesale price and the till a Chakana / Optom switch. */
    public static function wholesaleEnabled(int $shopId): bool
    {
        return self::get($shopId, 'wholesale_enabled') === '1';
    }

    /** Receipt logo / QR (a small PNG data URI kept in settings, so it syncs to the desktop app too). */
    public static function receiptImage(int $shopId, string $key): ?string
    {
        $value = self::get($shopId, $key);

        return ($value !== null && strpos($value, 'data:image/png;base64,') === 0) ? $value : null;
    }

    public static function lowStockThresholdDefault(int $shopId): ?float
    {
        $value = self::get($shopId, 'low_stock_threshold_default');

        return ($value !== null && $value !== '') ? (float) $value : null;
    }
}

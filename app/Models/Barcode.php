<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use App\Desktop\Desktop;

/**
 * The shop's own barcodes: EAN-13 = "2" + 2-digit device number + 9-digit
 * sequence + check digit. The 2xx range is reserved for in-store use, so it
 * never clashes with a manufacturer's code, and the device number keeps two
 * computers (or a phone) that are offline at the same time from handing out
 * the same number. The website is device 00.
 */
final class Barcode
{
    public static function checkDigit(string $twelve): int
    {
        $sum = 0;
        for ($i = 0; $i < 12; $i++) {
            $sum += (int) $twelve[$i] * ($i % 2 === 0 ? 1 : 3);
        }

        return (10 - $sum % 10) % 10;
    }

    public static function isEan13(string $code): bool
    {
        return preg_match('/^\d{13}$/', $code) === 1
            && self::checkDigit(substr($code, 0, 12)) === (int) $code[12];
    }

    /** 0 on the website; on a shop computer the number in its code ("K3" -> 3). */
    public static function deviceNumber(): int
    {
        if (!Desktop::enabled()) {
            return 0;
        }

        return (int) preg_replace('/\D/', '', (string) Desktop::get('device_code', '')) % 100;
    }

    /** The next free code of this device (highest sequence of its range + 1, skipping any taken). */
    public static function next(int $shopId): string
    {
        $prefix = '2' . str_pad((string) self::deviceNumber(), 2, '0', STR_PAD_LEFT);
        $glob = $prefix . str_repeat('[0-9]', 10);
        $pdo = Database::connect();

        $max = 0;
        foreach (['products', 'product_variants'] as $table) {
            $stmt = $pdo->prepare("SELECT MAX(CAST(SUBSTR(barcode, 4, 9) AS INTEGER)) FROM $table WHERE shop_id = ? AND barcode GLOB ?");
            $stmt->execute([$shopId, $glob]);
            $max = max($max, (int) $stmt->fetchColumn());
        }

        do {
            $max++;
            $twelve = $prefix . str_pad((string) $max, 9, '0', STR_PAD_LEFT);
            $code = $twelve . self::checkDigit($twelve);
        } while (Product::barcodeTaken($shopId, $code));

        return $code;
    }

    /** Active products and variants that have no barcode yet (those with variants are sold through the variants). */
    public static function countMissing(int $shopId): int
    {
        return count(self::missing($shopId));
    }

    /**
     * @param list<int>|null $productIds only these products (and their variants); null = all
     * @return list<array{table: string, id: int}>
     */
    public static function missing(int $shopId, ?array $productIds = null): array
    {
        $pdo = Database::connect();
        $only = $productIds !== null && $productIds !== []
            ? ' AND p.id IN (' . implode(',', array_map('intval', $productIds)) . ')'
            : '';
        $rows = [];

        $stmt = $pdo->prepare(
            "SELECT p.id FROM products p
             WHERE p.shop_id = ? AND p.status = 'active' AND (p.barcode IS NULL OR p.barcode = '')
               AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.status = 'active')$only
             ORDER BY p.id"
        );
        $stmt->execute([$shopId]);
        foreach ($stmt->fetchAll(\PDO::FETCH_COLUMN) as $id) {
            $rows[] = ['table' => 'products', 'id' => (int) $id];
        }

        $stmt = $pdo->prepare(
            "SELECT v.id FROM product_variants v INNER JOIN products p ON p.id = v.product_id
             WHERE v.shop_id = ? AND v.status = 'active' AND p.status = 'active' AND (v.barcode IS NULL OR v.barcode = '')$only
             ORDER BY v.id"
        );
        $stmt->execute([$shopId]);
        foreach ($stmt->fetchAll(\PDO::FETCH_COLUMN) as $id) {
            $rows[] = ['table' => 'product_variants', 'id' => (int) $id];
        }

        return $rows;
    }

    /** Gives every missing one a code, in one transaction. Returns how many got one. */
    public static function assignMissing(int $shopId, ?array $productIds = null): int
    {
        $pdo = Database::beginImmediate();
        try {
            $count = 0;
            foreach (self::missing($shopId, $productIds) as $row) {
                $code = self::next($shopId);
                $pdo->prepare("UPDATE {$row['table']} SET barcode = ? WHERE id = ? AND shop_id = ?")
                    ->execute([$code, $row['id'], $shopId]);
                $count++;
            }
            Database::commit($pdo);

            return $count;
        } catch (\Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }
    }

    /** One product or variant: its code if it has one, else a fresh one saved on it. Null when it isn't this shop's. */
    public static function ensureFor(int $shopId, int $productId, ?int $variantId): ?string
    {
        $pdo = Database::beginImmediate();
        try {
            $table = $variantId !== null ? 'product_variants' : 'products';
            $id = $variantId ?? $productId;
            $stmt = $pdo->prepare("SELECT barcode FROM $table WHERE id = ? AND shop_id = ?");
            $stmt->execute([$id, $shopId]);
            $row = $stmt->fetch();
            if ($row === false) {
                Database::commit($pdo);

                return null;
            }
            $code = (string) ($row['barcode'] ?? '');
            if ($code === '') {
                $code = self::next($shopId);
                $pdo->prepare("UPDATE $table SET barcode = ? WHERE id = ? AND shop_id = ?")->execute([$code, $id, $shopId]);
            }
            Database::commit($pdo);

            return $code;
        } catch (\Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }
    }
}

<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class ProductVariant
{
    public static function allByProduct(int $productId, int $shopId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM product_variants WHERE product_id = ? AND shop_id = ? ORDER BY variant_label'
        );
        $stmt->execute([$productId, $shopId]);

        return $stmt->fetchAll();
    }

    public static function activeByProduct(int $productId, int $shopId): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT * FROM product_variants WHERE product_id = ? AND shop_id = ? AND status = 'active' ORDER BY variant_label"
        );
        $stmt->execute([$productId, $shopId]);

        return $stmt->fetchAll();
    }

    /**
     * Every active variant in the shop, for building the POS's per-product
     * variant list in one query instead of one per product.
     */
    public static function activeAllByShop(int $shopId): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT * FROM product_variants WHERE shop_id = ? AND status = 'active' ORDER BY variant_label"
        );
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM product_variants WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function create(array $data): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare(
            'INSERT INTO product_variants (product_id, shop_id, variant_label, stock_qty, barcode, sell_price, cost_price, status)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $data['product_id'],
            $data['shop_id'],
            $data['variant_label'],
            $data['stock_qty'],
            $data['barcode'] ?? null,
            $data['sell_price'] ?? null,
            $data['cost_price'] ?? null,
            'active',
        ]);

        return (int) $pdo->lastInsertId();
    }

    public static function update(int $id, int $shopId, array $data): void
    {
        Database::connect()->prepare(
            'UPDATE product_variants
             SET variant_label = ?, stock_qty = ?, barcode = ?, sell_price = ?, cost_price = ?
             WHERE id = ? AND shop_id = ?'
        )->execute([
            $data['variant_label'],
            $data['stock_qty'],
            $data['barcode'] ?? null,
            $data['sell_price'] ?? null,
            $data['cost_price'] ?? null,
            $id,
            $shopId,
        ]);
    }

    /** Purchase flow's "update cost price" for a variant line (see Purchase::create()). */
    public static function updateCostPrice(int $id, int $shopId, float $costPrice): void
    {
        Database::connect()
            ->prepare('UPDATE product_variants SET cost_price = ? WHERE id = ? AND shop_id = ?')
            ->execute([$costPrice, $id, $shopId]);
    }

    public static function setStatus(int $id, int $shopId, string $status): void
    {
        Database::connect()
            ->prepare('UPDATE product_variants SET status = ? WHERE id = ? AND shop_id = ?')
            ->execute([$status, $id, $shopId]);
    }

    /**
     * Atomic conditional decrement, mirroring the products.stock_qty pattern
     * in Sale::create() exactly — the WHERE clause re-checks stock_qty in the
     * same statement that decrements it. Returns the affected row count (0 or
     * 1) so the caller can tell a successful decrement from "someone else
     * already sold this stock".
     */
    public static function decrementStock(int $id, int $productId, int $shopId, float $qty): int
    {
        $stmt = Database::connect()->prepare(
            'UPDATE product_variants SET stock_qty = stock_qty - ?
             WHERE id = ? AND product_id = ? AND shop_id = ? AND stock_qty >= ?'
        );
        $stmt->execute([$qty, $id, $productId, $shopId, $qty]);

        return $stmt->rowCount();
    }

    /**
     * Atomic restore (refund), mirroring the products.stock_qty restore in
     * Refund::create(). Returns the affected row count.
     */
    public static function incrementStock(int $id, int $shopId, float $qty): int
    {
        $stmt = Database::connect()->prepare(
            'UPDATE product_variants SET stock_qty = stock_qty + ? WHERE id = ? AND shop_id = ?'
        );
        $stmt->execute([$qty, $id, $shopId]);

        return $stmt->rowCount();
    }
}

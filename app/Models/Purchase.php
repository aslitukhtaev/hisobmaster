<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use RuntimeException;
use Throwable;

class Purchase
{
    /**
     * Records a restock: increases every line item's product stock_qty
     * (atomic conditional UPDATE, same shape as Refund::create()'s stock
     * restore) and, only when $updateCostPrice is true, also sets each
     * product's cost_price to that line's unit_cost.
     *
     * @param array<int, array{product_id: int, qty: float, unit_cost: float}> $items
     * @return int the new purchase id
     *
     * @throws RuntimeException when there are no items or a product is invalid
     */
    public static function create(
        int $shopId,
        int $userId,
        ?int $supplierId,
        array $items,
        ?string $note,
        bool $updateCostPrice
    ): int {
        if (empty($items)) {
            throw new RuntimeException('purchase_no_items');
        }

        // BEGIN IMMEDIATE up front, same reasoning as Sale::create()/Refund::create():
        // the whole purchase (stock increments across every line) must commit or
        // roll back as one unit, and no concurrent sale/refund/purchase on the
        // same products should be able to interleave with it.
        $pdo = Database::beginImmediate();

        try {
            $resolved = [];
            $totalAmount = 0.0;

            foreach ($items as $item) {
                $productId = (int) $item['product_id'];
                $variantId = !empty($item['variant_id']) ? (int) $item['variant_id'] : null;
                $qty = round((float) $item['qty'], 3);
                $unitCost = round((float) $item['unit_cost'], 2);

                $product = Product::find($productId, $shopId);
                if (!$product) {
                    throw new RuntimeException('invalid_product');
                }
                if ($qty <= 0 || $qty > QTY_MAX || $unitCost < 0 || $unitCost > MONEY_MAX) {
                    throw new RuntimeException('invalid_qty');
                }
                if (!qty_fits_unit($qty, $product['unit'])) {
                    throw new RuntimeException('qty_must_be_whole_product:' . $product['name']);
                }

                // Same rule as a sale: stock of a product with active variants
                // lives on the variants, so the purchase must say which one.
                $variant = null;
                if ($variantId !== null) {
                    $variant = ProductVariant::find($variantId, $shopId);
                    if (!$variant || (int) $variant['product_id'] !== $productId || $variant['status'] !== 'active') {
                        throw new RuntimeException('invalid_product');
                    }
                } elseif (!empty(ProductVariant::activeByProduct($productId, $shopId))) {
                    throw new RuntimeException('variant_required:' . $product['name']);
                }

                $subtotal = round($qty * $unitCost, 2);
                $totalAmount += $subtotal;

                $resolved[] = [
                    'product_id' => $productId,
                    'variant_id' => $variant !== null ? (int) $variant['id'] : null,
                    'variant_label' => $variant['variant_label'] ?? null,
                    'qty' => $qty,
                    'unit_cost' => $unitCost,
                    'subtotal' => $subtotal,
                ];
            }

            $totalAmount = round($totalAmount, 2);
            if ($totalAmount > MONEY_MAX) {
                throw new RuntimeException('amount_too_large');
            }

            $stmt = $pdo->prepare(
                'INSERT INTO purchases (shop_id, supplier_id, total_amount, note, created_by) VALUES (?, ?, ?, ?, ?)'
            );
            $stmt->execute([$shopId, $supplierId, $totalAmount, $note, $userId]);
            $purchaseId = (int) $pdo->lastInsertId();

            $itemStmt = $pdo->prepare(
                'INSERT INTO purchase_items (purchase_id, product_id, qty, unit_cost, subtotal, variant_id, variant_label)
                 VALUES (?, ?, ?, ?, ?, ?, ?)'
            );
            // Atomic restock: adds qty back and re-checks the row belongs to this
            // shop in the same statement, mirroring Refund::create()'s stock
            // restore rather than a read-then-write.
            $stockStmt = $pdo->prepare(
                'UPDATE products SET stock_qty = stock_qty + ? WHERE id = ? AND shop_id = ?'
            );

            foreach ($resolved as $item) {
                $itemStmt->execute([
                    $purchaseId, $item['product_id'], $item['qty'], $item['unit_cost'], $item['subtotal'],
                    $item['variant_id'], $item['variant_label'],
                ]);

                if ($item['variant_id'] !== null) {
                    $affected = ProductVariant::incrementStock($item['variant_id'], $shopId, $item['qty']);
                } else {
                    $stockStmt->execute([$item['qty'], $item['product_id'], $shopId]);
                    $affected = $stockStmt->rowCount();
                }
                if ($affected !== 1) {
                    throw new RuntimeException('invalid_product');
                }

                if ($updateCostPrice) {
                    if ($item['variant_id'] !== null) {
                        ProductVariant::updateCostPrice($item['variant_id'], $shopId, $item['unit_cost']);
                    } else {
                        Product::updateCostPrice($item['product_id'], $shopId, $item['unit_cost']);
                    }
                }
            }

            Database::commit($pdo);

            return $purchaseId;
        } catch (Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }
    }

    public static function recentByShop(int $shopId, int $limit = 100): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT p.*, s.name AS supplier_name, u.full_name AS created_by_name
             FROM purchases p
             LEFT JOIN suppliers s ON s.id = p.supplier_id
             LEFT JOIN users u ON u.id = p.created_by
             WHERE p.shop_id = :shop_id
             ORDER BY p.created_at DESC, p.id DESC
             LIMIT :limit'
        );
        $stmt->bindValue(':shop_id', $shopId, \PDO::PARAM_INT);
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare(
            'SELECT p.*, s.name AS supplier_name, u.full_name AS created_by_name
             FROM purchases p
             LEFT JOIN suppliers s ON s.id = p.supplier_id
             LEFT JOIN users u ON u.id = p.created_by
             WHERE p.id = ? AND p.shop_id = ?'
        );
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function items(int $purchaseId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT pi.*, p.name AS product_name, p.unit
             FROM purchase_items pi
             INNER JOIN products p ON p.id = pi.product_id
             WHERE pi.purchase_id = ?
             ORDER BY pi.id'
        );
        $stmt->execute([$purchaseId]);

        return $stmt->fetchAll();
    }
}

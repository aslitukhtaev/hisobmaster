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
                $qty = round((float) $item['qty'], 3);
                $unitCost = round((float) $item['unit_cost'], 2);

                $product = Product::find($productId, $shopId);
                if (!$product) {
                    throw new RuntimeException('invalid_product');
                }
                if ($qty <= 0 || $unitCost < 0) {
                    throw new RuntimeException('invalid_qty');
                }

                $subtotal = round($qty * $unitCost, 2);
                $totalAmount += $subtotal;

                $resolved[] = [
                    'product_id' => $productId,
                    'qty' => $qty,
                    'unit_cost' => $unitCost,
                    'subtotal' => $subtotal,
                ];
            }

            $totalAmount = round($totalAmount, 2);

            $stmt = $pdo->prepare(
                'INSERT INTO purchases (shop_id, supplier_id, total_amount, note, created_by) VALUES (?, ?, ?, ?, ?)'
            );
            $stmt->execute([$shopId, $supplierId, $totalAmount, $note, $userId]);
            $purchaseId = (int) $pdo->lastInsertId();

            $itemStmt = $pdo->prepare(
                'INSERT INTO purchase_items (purchase_id, product_id, qty, unit_cost, subtotal) VALUES (?, ?, ?, ?, ?)'
            );
            // Atomic restock: adds qty back and re-checks the row belongs to this
            // shop in the same statement, mirroring Refund::create()'s stock
            // restore rather than a read-then-write.
            $stockStmt = $pdo->prepare(
                'UPDATE products SET stock_qty = stock_qty + ? WHERE id = ? AND shop_id = ?'
            );

            foreach ($resolved as $item) {
                $itemStmt->execute([$purchaseId, $item['product_id'], $item['qty'], $item['unit_cost'], $item['subtotal']]);

                $stockStmt->execute([$item['qty'], $item['product_id'], $shopId]);
                if ($stockStmt->rowCount() !== 1) {
                    throw new RuntimeException('invalid_product');
                }

                if ($updateCostPrice) {
                    Product::updateCostPrice($item['product_id'], $shopId, $item['unit_cost']);
                }
            }

            $pdo->commit();

            return $purchaseId;
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
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

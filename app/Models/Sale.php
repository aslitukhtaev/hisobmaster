<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use RuntimeException;

class Sale
{
    /**
     * @param array<int, array{product_id: int, qty: float}> $items
     * @return int the new sale id
     *
     * @throws RuntimeException when an item is invalid or stock is insufficient
     */
    public static function create(
        int $shopId,
        int $cashierId,
        array $items,
        string $paymentType,
        float $discount,
        float $paidAmount,
        ?int $customerId
    ): int {
        if (empty($items)) {
            throw new RuntimeException('empty_cart');
        }

        // BEGIN IMMEDIATE grabs SQLite's write lock for the whole sale up front, so a
        // concurrent sale on another terminal can't interleave with the stock
        // decrement below (see Database::beginImmediate() and the stock-decrement
        // comment further down for why a plain beginTransaction() isn't enough).
        $pdo = Database::beginImmediate();

        try {
            $subtotal = 0.0;
            $resolvedItems = [];

            foreach ($items as $item) {
                $product = Product::find((int) $item['product_id'], $shopId);
                $qty = (float) $item['qty'];

                if (!$product || $product['status'] !== 'active') {
                    throw new RuntimeException('invalid_product');
                }

                if ($qty <= 0) {
                    throw new RuntimeException('invalid_qty');
                }

                // NOTE: no stock_qty check here — that would just be a second,
                // equally racy read-then-write. The authoritative check is the
                // atomic conditional UPDATE in the loop below.

                $unitPrice = (float) $product['sell_price'];
                $costPrice = (float) $product['cost_price'];
                $lineSubtotal = round($unitPrice * $qty, 2);
                $subtotal += $lineSubtotal;

                $resolvedItems[] = [
                    'product_id' => $product['id'],
                    'product_name' => $product['name'],
                    'qty' => $qty,
                    'unit_price' => $unitPrice,
                    'cost_price_snapshot' => $costPrice,
                    'subtotal' => $lineSubtotal,
                ];
            }

            $discount = max(0.0, min($discount, $subtotal));
            $total = round($subtotal - $discount, 2);

            $paidAmount = $paymentType === 'qarz'
                ? max(0.0, min($paidAmount, $total))
                : $total;

            $status = 'completed';

            $stmt = $pdo->prepare(
                'INSERT INTO sales (shop_id, cashier_id, customer_id, total, discount, payment_type, paid_amount, status)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
            );
            $stmt->execute([$shopId, $cashierId, $customerId, $total, $discount, $paymentType, $paidAmount, $status]);
            $saleId = (int) $pdo->lastInsertId();

            $itemStmt = $pdo->prepare(
                'INSERT INTO sale_items (sale_id, product_id, product_name, qty, unit_price, cost_price_snapshot, subtotal)
                 VALUES (?, ?, ?, ?, ?, ?, ?)'
            );
            // Atomic conditional decrement: the WHERE clause re-checks stock_qty in
            // the same statement that decrements it, so the check-and-write is one
            // indivisible step instead of a separate read followed by a write. Two
            // concurrent sales of the last unit can now never both succeed — only
            // one UPDATE will match a row and affect it.
            $stockStmt = $pdo->prepare(
                'UPDATE products SET stock_qty = stock_qty - ? WHERE id = ? AND shop_id = ? AND stock_qty >= ?'
            );

            foreach ($resolvedItems as $item) {
                $itemStmt->execute([
                    $saleId,
                    $item['product_id'],
                    $item['product_name'],
                    $item['qty'],
                    $item['unit_price'],
                    $item['cost_price_snapshot'],
                    $item['subtotal'],
                ]);

                $stockStmt->execute([$item['qty'], $item['product_id'], $shopId, $item['qty']]);

                if ($stockStmt->rowCount() !== 1) {
                    // Someone else already sold this stock between our read above and
                    // this write. Fail the whole sale (caught below, which rolls back
                    // everything — the sale row and any earlier line items too) rather
                    // than oversell.
                    throw new RuntimeException('insufficient_stock:' . $item['product_name']);
                }
            }

            $debtAmount = round($total - $paidAmount, 2);
            if ($paymentType === 'qarz' && $debtAmount > 0 && $customerId !== null) {
                DebtTransaction::record($shopId, $customerId, $saleId, 'qarz', $debtAmount, $cashierId);
            }

            $pdo->commit();

            return $saleId;
        } catch (\Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare(
            'SELECT s.*, u.full_name AS cashier_name, c.full_name AS customer_name, c.phone AS customer_phone
             FROM sales s
             LEFT JOIN users u ON u.id = s.cashier_id
             LEFT JOIN customers c ON c.id = s.customer_id
             WHERE s.id = ? AND s.shop_id = ?'
        );
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function items(int $saleId): array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM sale_items WHERE sale_id = ? ORDER BY id');
        $stmt->execute([$saleId]);

        return $stmt->fetchAll();
    }

    public static function recentByShop(int $shopId, int $limit = 50): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT s.*, u.full_name AS cashier_name, c.full_name AS customer_name
             FROM sales s
             LEFT JOIN users u ON u.id = s.cashier_id
             LEFT JOIN customers c ON c.id = s.customer_id
             WHERE s.shop_id = :shop_id
             ORDER BY s.created_at DESC, s.id DESC
             LIMIT :limit'
        );
        $stmt->bindValue(':shop_id', $shopId, \PDO::PARAM_INT);
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();

        return $stmt->fetchAll();
    }

    public static function todaysSummary(int $shopId): array
    {
        $pdo = Database::connect();

        // created_at is stored as SQLite's datetime('now'), which is always UTC.
        // "Today" has to mean the Tashkent calendar day (bootstrap.php sets PHP's
        // default timezone to Asia/Tashkent), so we compute that day's boundaries
        // in PHP and convert them to the matching UTC range here, rather than
        // asking SQLite's own (UTC) date('now') what day it is.
        [$startUtc, $endUtc] = tashkent_day_bounds_utc(date('Y-m-d'));

        $stmt = $pdo->prepare(
            "SELECT COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS revenue
             FROM sales WHERE shop_id = ? AND created_at >= ? AND created_at < ? AND status = 'completed'"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $row = $stmt->fetch();

        return [
            'count' => (int) ($row['cnt'] ?? 0),
            'revenue' => (float) ($row['revenue'] ?? 0),
        ];
    }
}

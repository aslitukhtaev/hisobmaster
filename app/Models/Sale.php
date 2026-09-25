<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use App\Desktop\Desktop;
use RuntimeException;

class Sale
{
    /**
     * @param array<int, array{product_id: int, qty: float, variant_id?: ?int}> $items
     * @return int the new sale id
     *
     * @throws RuntimeException when an item is invalid, stock is insufficient,
     *         or a debt remains with no customer to carry it
     */
    public static function create(
        int $shopId,
        int $cashierId,
        array $items,
        float $naqdAmount,
        float $kartaAmount,
        float $discount,
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
                $variantId = !empty($item['variant_id']) ? (int) $item['variant_id'] : null;

                if (!$product || $product['status'] !== 'active') {
                    throw new RuntimeException('invalid_product');
                }

                if ($qty <= 0 || $qty > QTY_MAX) {
                    throw new RuntimeException('invalid_qty');
                }

                if (!qty_fits_unit($qty, $product['unit'])) {
                    throw new RuntimeException('qty_must_be_whole_product:' . $product['name']);
                }

                $variant = null;
                if ($variantId !== null) {
                    $variant = ProductVariant::find($variantId, $shopId);
                    if (!$variant || (int) $variant['product_id'] !== (int) $product['id'] || $variant['status'] !== 'active') {
                        throw new RuntimeException('invalid_product');
                    }
                } elseif (!empty(ProductVariant::activeByProduct((int) $product['id'], $shopId))) {
                    // This product has at least one active variant — cashiers must
                    // pick a specific one (its own stock, not the parent's, is what
                    // actually gets decremented below), rather than silently
                    // falling back to the parent product.
                    throw new RuntimeException('variant_required:' . $product['name']);
                }

                // NOTE: no stock_qty check here — that would just be a second,
                // equally racy read-then-write. The authoritative check is the
                // atomic conditional UPDATE in the loop below.

                $unitPrice = $variant !== null && $variant['sell_price'] !== null
                    ? (float) $variant['sell_price']
                    : (float) $product['sell_price'];
                $costPrice = $variant !== null && $variant['cost_price'] !== null
                    ? (float) $variant['cost_price']
                    : (float) $product['cost_price'];
                $lineSubtotal = round($unitPrice * $qty, 2);
                $subtotal += $lineSubtotal;

                $resolvedItems[] = [
                    'product_id' => $product['id'],
                    'product_name' => $product['name'],
                    'variant_id' => $variant['id'] ?? null,
                    'variant_label' => $variant['variant_label'] ?? null,
                    'qty' => $qty,
                    'unit_price' => $unitPrice,
                    'cost_price_snapshot' => $costPrice,
                    'subtotal' => $lineSubtotal,
                ];
            }

            // A discount has to leave something to pay: 100% (or more than
            // the cart is worth) is never a discount, it's a giveaway, and a
            // negative one would be a hidden surcharge.
            if ($discount < 0 || !is_finite($discount)) {
                throw new RuntimeException('invalid_discount');
            }
            if ($discount > 0 && $discount >= $subtotal - 0.005) {
                throw new RuntimeException('discount_too_large');
            }
            $discount = round($discount, 2);
            $total = round($subtotal - $discount, 2);

            // naqd/karta are whatever the cashier explicitly collected up front;
            // qarz is never entered directly — it's always just what's left of
            // the total once naqd+karta are accounted for (clamped so the three
            // can never add up to more than the total). A card payment is
            // always exact, so it's applied first; cash may be more than
            // what's left (the customer hands over a bigger note) — only the
            // needed part counts as naqd and the rest is change, remembered in
            // cash_received so the receipt can show it.
            $cashTendered = max(0.0, $naqdAmount);
            $kartaAmount = max(0.0, min($kartaAmount, $total));
            $naqdAmount = max(0.0, min($cashTendered, round($total - $kartaAmount, 2)));
            $qarzAmount = max(0.0, round($total - $naqdAmount - $kartaAmount, 2));
            $cashReceived = $cashTendered > $naqdAmount + 0.005 ? round($cashTendered, 2) : null;

            if ($qarzAmount > 0 && $customerId === null) {
                throw new RuntimeException('customer_required_for_debt');
            }

            // paid_amount keeps its original meaning (total - debt), so every
            // pre-existing read of it — including this file's own receipt-page
            // debt calculation — stays correct without any change.
            $paidAmount = round($naqdAmount + $kartaAmount, 2);
            $paymentType = self::derivePaymentTypeLabel($naqdAmount, $kartaAmount, $qarzAmount);
            $status = 'completed';

            $stmt = $pdo->prepare(
                'INSERT INTO sales (shop_id, cashier_id, customer_id, total, discount, payment_type, paid_amount, status, cash_received, receipt_no)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
            );
            // On a shop's computer the receipt gets that computer's own
            // number ("K2-000145"), which stays the same on the server.
            $receiptNo = Desktop::enabled() ? Desktop::nextReceiptNo() : null;
            $stmt->execute([$shopId, $cashierId, $customerId, $total, $discount, $paymentType, $paidAmount, $status, $cashReceived, $receiptNo]);
            $saleId = (int) $pdo->lastInsertId();

            $paymentStmt = $pdo->prepare(
                'INSERT INTO sale_payments (sale_id, payment_type, amount) VALUES (?, ?, ?)'
            );
            foreach (['naqd' => $naqdAmount, 'karta' => $kartaAmount, 'qarz' => $qarzAmount] as $type => $amount) {
                if ($amount > 0) {
                    $paymentStmt->execute([$saleId, $type, $amount]);
                }
            }

            $itemStmt = $pdo->prepare(
                'INSERT INTO sale_items (sale_id, product_id, product_name, qty, unit_price, cost_price_snapshot, subtotal, variant_id, variant_label)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
            );
            // Atomic conditional decrement: the WHERE clause re-checks stock_qty in
            // the same statement that decrements it, so the check-and-write is one
            // indivisible step instead of a separate read followed by a write. Two
            // concurrent sales of the last unit can now never both succeed — only
            // one UPDATE will match a row and affect it. A variant sale decrements
            // the variant's own stock_qty (ProductVariant::decrementStock(), same
            // shape) instead of the parent product's.
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
                    $item['variant_id'],
                    $item['variant_label'],
                ]);

                if ($item['variant_id'] !== null) {
                    $affected = ProductVariant::decrementStock($item['variant_id'], $item['product_id'], $shopId, $item['qty']);
                } else {
                    $stockStmt->execute([$item['qty'], $item['product_id'], $shopId, $item['qty']]);
                    $affected = $stockStmt->rowCount();
                }

                if ($affected !== 1) {
                    // Someone else already sold this stock between our read above and
                    // this write. Fail the whole sale (caught below, which rolls back
                    // everything — the sale row and any earlier line items too) rather
                    // than oversell.
                    $label = $item['variant_label'] !== null
                        ? $item['product_name'] . ' (' . $item['variant_label'] . ')'
                        : $item['product_name'];
                    throw new RuntimeException('insufficient_stock:' . $label);
                }
            }

            if ($qarzAmount > 0 && $customerId !== null) {
                DebtTransaction::record($shopId, $customerId, $saleId, 'qarz', $qarzAmount, $cashierId, nested: true);
            }

            Database::commit($pdo);

            return $saleId;
        } catch (\Throwable $e) {
            Database::rollback($pdo);
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

    /**
     * The naqd/karta/qarz breakdown of a single sale. Every sale created since
     * split payments shipped has at least one row here; older sales have none
     * (they only ever used a single payment_type, still readable straight off
     * the sales row itself).
     */
    public static function payments(int $saleId): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT * FROM sale_payments WHERE sale_id = ?
             ORDER BY CASE payment_type WHEN 'naqd' THEN 1 WHEN 'karta' THEN 2 WHEN 'qarz' THEN 3 ELSE 4 END"
        );
        $stmt->execute([$saleId]);

        return $stmt->fetchAll();
    }

    /**
     * How much of a sale's total went through each payment method. Sales
     * with sale_payments rows read them directly; older single-payment-type
     * sales derive it from their own row: the qarz part is always
     * total - paid_amount, and whatever was paid up front went through that
     * sale's payment_type (karta), or cash for anything else — a legacy
     * 'qarz' sale's partial up-front payment was taken in cash.
     *
     * @return array{naqd: float, karta: float, qarz: float}
     */
    public static function paymentSplit(array $sale): array
    {
        $split = ['naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0];
        $rows = self::payments((int) $sale['id']);

        if (!empty($rows)) {
            foreach ($rows as $row) {
                if (isset($split[$row['payment_type']])) {
                    $split[$row['payment_type']] += (float) $row['amount'];
                }
            }

            return $split;
        }

        $total = (float) $sale['total'];
        $paid = min((float) $sale['paid_amount'], $total);
        $split['qarz'] = max(0.0, round($total - $paid, 2));
        $split[$sale['payment_type'] === 'karta' ? 'karta' : 'naqd'] = $paid;

        return $split;
    }

    /**
     * A short label for the sales.payment_type summary column: the single type
     * name when only one method was used (so old single-payment-type sales and
     * reports keep reading exactly what they always did), or 'aralash' (mixed)
     * once two or more methods carry a nonzero amount.
     */
    private static function derivePaymentTypeLabel(float $naqd, float $karta, float $qarz): string
    {
        $active = array_keys(array_filter(
            ['naqd' => $naqd, 'karta' => $karta, 'qarz' => $qarz],
            static fn (float $amount): bool => $amount > 0.0
        ));

        if (count($active) === 1) {
            return $active[0];
        }

        // A zero-total sale (fully discounted) has no active payment method at
        // all — default it to 'naqd' rather than the meaningless 'aralash'.
        return count($active) === 0 ? 'naqd' : 'aralash';
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

    /**
     * Every completed-or-not sale in a Tashkent calendar date range, newest
     * first — used by the sales list's optional date filter and its CSV
     * export, so both read off the same rows. Unlike recentByShop() this has
     * no LIMIT, since a date range is already a bounded window.
     */
    public static function rangeByShop(int $shopId, string $from, string $to): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);

        $stmt = Database::connect()->prepare(
            'SELECT s.*, u.full_name AS cashier_name, c.full_name AS customer_name
             FROM sales s
             LEFT JOIN users u ON u.id = s.cashier_id
             LEFT JOIN customers c ON c.id = s.customer_id
             WHERE s.shop_id = ? AND s.created_at >= ? AND s.created_at < ?
             ORDER BY s.created_at DESC, s.id DESC'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);

        return $stmt->fetchAll();
    }

    /**
     * Every sale tied to one customer (paid-in-full naqd/karta sales included,
     * not just the ones that left a debt_transactions row), newest first —
     * the customer detail page's "purchase history" section. This is
     * distinct from the debt ledger: a customer can have sales here that
     * never touch debt_transactions at all.
     */
    public static function byCustomer(int $customerId, int $shopId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT s.*, (SELECT COUNT(*) FROM sale_items si WHERE si.sale_id = s.id) AS item_count
             FROM sales s
             WHERE s.customer_id = ? AND s.shop_id = ?
             ORDER BY s.created_at DESC, s.id DESC'
        );
        $stmt->execute([$customerId, $shopId]);

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

        // Net of today's refunds, matching the reports page's revenue.
        $stmt = $pdo->prepare(
            'SELECT COALESCE(SUM(total_amount), 0) FROM refunds WHERE shop_id = ? AND created_at >= ? AND created_at < ?'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $refunded = (float) $stmt->fetchColumn();

        return [
            'count' => (int) ($row['cnt'] ?? 0),
            'revenue' => round((float) ($row['revenue'] ?? 0) - $refunded, 2),
        ];
    }
}

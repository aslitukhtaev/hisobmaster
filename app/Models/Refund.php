<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use RuntimeException;
use Throwable;

class Refund
{
    /**
     * Every line item of a sale, alongside how much of it has already been
     * refunded (summed across every prior refund of this sale) and how much
     * of it is still eligible to be returned.
     */
    public static function refundableForSale(int $saleId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT si.*, COALESCE(ri.refunded_qty, 0) AS refunded_qty
             FROM sale_items si
             LEFT JOIN (
                 SELECT sale_item_id, SUM(qty) AS refunded_qty
                 FROM refund_items
                 GROUP BY sale_item_id
             ) ri ON ri.sale_item_id = si.id
             WHERE si.sale_id = ?
             ORDER BY si.id'
        );
        $stmt->execute([$saleId]);
        $rows = $stmt->fetchAll();

        foreach ($rows as &$row) {
            $row['remaining_qty'] = max(0.0, round((float) $row['qty'] - (float) $row['refunded_qty'], 2));
        }
        unset($row);

        return $rows;
    }

    /**
     * Splits a refund amount across naqd/karta/qarz in the same proportion
     * the original sale was paid. Every share but one is rounded directly;
     * the remaining one (naqd first, then karta, then qarz — whichever the
     * sale actually used) absorbs the rounding difference, so the shares
     * always sum to exactly $amount and a method the sale never used always
     * gets exactly 0.
     *
     * Pure function (no DB access) — also used by database/migrate.php to
     * backfill refund_payments for refunds recorded before it existed.
     *
     * @return array{naqd: float, karta: float, qarz: float}
     */
    public static function allocateByPayment(float $amount, float $naqd, float $karta, float $qarz): array
    {
        $result = ['naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0];
        $split = ['naqd' => $naqd, 'karta' => $karta, 'qarz' => $qarz];
        $total = $naqd + $karta + $qarz;

        if ($amount <= 0 || $total <= 0) {
            $result['naqd'] = max(0.0, $amount);
            return $result;
        }

        $remainderKey = null;
        foreach ($split as $type => $value) {
            if ($value > 0) {
                $remainderKey = $type;
                break;
            }
        }

        $assigned = 0.0;
        foreach ($split as $type => $value) {
            if ($type === $remainderKey || $value <= 0) {
                continue;
            }
            $result[$type] = round($amount * $value / $total, 2);
            $assigned += $result[$type];
        }
        $result[$remainderKey] = round($amount - $assigned, 2);

        return $result;
    }

    /**
     * Refunds within a UTC window, summed per payment method they went back
     * through (refund_payments).
     *
     * @return array{naqd: float, karta: float, qarz: float}
     */
    public static function totalsByPayment(int $shopId, string $startUtc, string $endUtc): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT rp.payment_type, COALESCE(SUM(rp.amount), 0) AS total
             FROM refund_payments rp
             INNER JOIN refunds r ON r.id = rp.refund_id
             WHERE r.shop_id = ? AND r.created_at >= ? AND r.created_at < ?
             GROUP BY rp.payment_type'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);

        $result = ['naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0];
        foreach ($stmt->fetchAll() as $row) {
            if (isset($result[$row['payment_type']])) {
                $result[$row['payment_type']] += (float) $row['total'];
            }
        }

        return $result;
    }

    /**
     * Issues a refund for some (or all) of a completed sale's line items.
     *
     * @param array<int, array{sale_item_id: int, qty: float}> $lines
     * @return int the new refund id
     *
     * @throws RuntimeException when the sale/item is invalid or a requested
     *         qty exceeds what's still refundable
     */
    public static function create(int $shopId, int $saleId, int $userId, array $lines, ?string $reason): int
    {
        if (empty($lines)) {
            throw new RuntimeException('refund_no_items_selected');
        }

        // Same reasoning as Sale::create(): BEGIN IMMEDIATE up front so a
        // concurrent refund or sale touching the same products/customer can't
        // interleave with the stock restore / debt adjustment below.
        $pdo = Database::beginImmediate();

        try {
            $sale = Sale::find($saleId, $shopId);
            if (!$sale) {
                throw new RuntimeException('sale_not_found');
            }

            $refundable = [];
            foreach (self::refundableForSale($saleId) as $row) {
                $refundable[(int) $row['id']] = $row;
            }

            // sale_items.subtotal is the pre-discount (gross) line amount; the
            // sale's discount was applied once, across the whole basket. To
            // refund the actual money the customer is owed — not more, not
            // less — each line's refund is scaled down by the same discount
            // ratio the whole sale carried, so a full refund of every
            // remaining item always sums to exactly what's left of sale.total.
            $saleSubtotal = round((float) $sale['total'] + (float) $sale['discount'], 2);
            $discountRatio = $saleSubtotal > 0 ? ((float) $sale['discount'] / $saleSubtotal) : 0.0;

            $resolved = [];
            $totalAmount = 0.0;

            foreach ($lines as $line) {
                $saleItemId = (int) ($line['sale_item_id'] ?? 0);
                $qty = round((float) ($line['qty'] ?? 0), 2);

                if (!isset($refundable[$saleItemId])) {
                    throw new RuntimeException('invalid_refund_item');
                }

                $item = $refundable[$saleItemId];

                if ($qty <= 0 || $qty > (float) $item['remaining_qty'] + 0.0001) {
                    throw new RuntimeException('invalid_refund_qty');
                }

                $gross = round((float) $item['unit_price'] * $qty, 2);
                $net = round($gross * (1 - $discountRatio), 2);
                $totalAmount += $net;

                $resolved[] = [
                    'sale_item_id' => $saleItemId,
                    'product_id' => (int) $item['product_id'],
                    'variant_id' => $item['variant_id'] !== null ? (int) $item['variant_id'] : null,
                    'qty' => $qty,
                    'amount' => $net,
                ];
            }

            $totalAmount = round($totalAmount, 2);

            $refundStmt = $pdo->prepare(
                'INSERT INTO refunds (sale_id, shop_id, refunded_by, total_amount, reason) VALUES (?, ?, ?, ?, ?)'
            );
            $refundStmt->execute([$saleId, $shopId, $userId, $totalAmount, $reason]);
            $refundId = (int) $pdo->lastInsertId();

            $itemStmt = $pdo->prepare(
                'INSERT INTO refund_items (refund_id, sale_item_id, qty, amount) VALUES (?, ?, ?, ?)'
            );
            // Atomic restore: a single UPDATE that both adds the qty back and
            // verifies the product row still belongs to this shop, mirroring
            // Sale::create()'s atomic conditional decrement rather than a
            // read-then-write. A line sold from a variant restores that
            // variant's own stock_qty (ProductVariant::incrementStock(), same
            // shape) instead of the parent product's.
            $stockStmt = $pdo->prepare(
                'UPDATE products SET stock_qty = stock_qty + ? WHERE id = ? AND shop_id = ?'
            );

            foreach ($resolved as $r) {
                $itemStmt->execute([$refundId, $r['sale_item_id'], $r['qty'], $r['amount']]);

                if ($r['variant_id'] !== null) {
                    $affected = ProductVariant::incrementStock($r['variant_id'], $shopId, $r['qty']);
                } else {
                    $stockStmt->execute([$r['qty'], $r['product_id'], $shopId]);
                    $affected = $stockStmt->rowCount();
                }

                if ($affected !== 1) {
                    // The product/variant row is gone or belongs to another shop —
                    // shouldn't happen (products/variants are never deleted, only
                    // deactivated), but fail loudly rather than silently
                    // dropping stock that should have been restored.
                    throw new RuntimeException('product_not_found');
                }
            }

            $split = Sale::paymentSplit($sale);
            $allocation = self::allocateByPayment($totalAmount, $split['naqd'], $split['karta'], $split['qarz']);

            $paymentStmt = $pdo->prepare(
                'INSERT INTO refund_payments (refund_id, payment_type, amount) VALUES (?, ?, ?)'
            );
            foreach ($allocation as $type => $amount) {
                if ($amount > 0) {
                    $paymentStmt->execute([$refundId, $type, $amount]);
                }
            }

            self::adjustDebtForRefund($shopId, $sale, $allocation['qarz'], $userId);

            Database::commit($pdo);

            return $refundId;
        } catch (Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }
    }

    /**
     * If the sale being refunded was paid partly or fully via qarz (debt),
     * this shrinks the customer's ledger by $debtPortion — the qarz share of
     * the refund from allocateByPayment(), the same figure recorded in
     * refund_payments, so the ledger and the reports can never disagree.
     *
     * The ledger only ever tracks a running balance (not "which sale a debt
     * came from" once other payments/refunds start mixing in), so there's no
     * way to know whether that specific slice of debt is still outstanding or
     * was already paid off by the time of the refund. It turns out not to
     * matter: recording it as a balance-reducing entry (the same arithmetic
     * DebtTransaction::record() already applies to a 'tolov' repayment)
     * handles both cases correctly in one step —
     *   - still outstanding: the customer simply owes that much less.
     *   - already paid off: the balance goes negative, i.e. becomes a credit
     *     the shop now owes the customer back, which is exactly correct since
     *     they paid for goods that came back.
     * Either way the shop's ledger balances exactly; nothing is double
     * counted or lost.
     */
    private static function adjustDebtForRefund(int $shopId, array $sale, float $debtPortion, int $userId): void
    {
        if ($sale['customer_id'] === null) {
            return;
        }

        if ($debtPortion > 0) {
            DebtTransaction::record($shopId, (int) $sale['customer_id'], (int) $sale['id'], 'refund', $debtPortion, $userId, nested: true);
        }
    }

    public static function historyForSale(int $saleId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT r.*, u.full_name AS refunded_by_name
             FROM refunds r
             LEFT JOIN users u ON u.id = r.refunded_by
             WHERE r.sale_id = ?
             ORDER BY r.id DESC'
        );
        $stmt->execute([$saleId]);
        $refunds = $stmt->fetchAll();

        if (empty($refunds)) {
            return [];
        }

        $itemStmt = Database::connect()->prepare(
            'SELECT ri.*, si.product_name
             FROM refund_items ri
             INNER JOIN sale_items si ON si.id = ri.sale_item_id
             WHERE ri.refund_id = ?
             ORDER BY ri.id'
        );

        foreach ($refunds as &$refund) {
            $itemStmt->execute([$refund['id']]);
            $refund['items'] = $itemStmt->fetchAll();
        }
        unset($refund);

        return $refunds;
    }
}

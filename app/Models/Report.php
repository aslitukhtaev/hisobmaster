<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use DateTimeImmutable;

class Report
{
    /**
     * @return array{0: string, 1: string, 2: string} [from, to, period]
     */
    public static function resolvePeriod(string $period, string $customFrom, string $customTo): array
    {
        if ($customFrom !== '' && $customTo !== '' && $customFrom <= $customTo) {
            return [$customFrom, $customTo, 'custom'];
        }

        $today = new DateTimeImmutable('today');
        $dayOfWeek = (int) $today->format('N'); // 1 (Monday) .. 7 (Sunday)
        $monday = $today->modify('-' . ($dayOfWeek - 1) . ' days');

        return match ($period) {
            'today' => [$today->format('Y-m-d'), $today->format('Y-m-d'), 'today'],
            'week' => [$monday->format('Y-m-d'), $today->format('Y-m-d'), 'week'],
            default => [$today->format('Y-m-01'), $today->format('Y-m-d'), 'month'],
        };
    }

    /**
     * The immediately preceding period of the same length in days, e.g. "this
     * month" (1st..19th, 19 days) compares against the preceding 19 days
     * ending the day before `from`. $from/$to are Tashkent calendar dates
     * (Y-m-d) exactly like every other range in this class — this only shifts
     * those two calendar dates backwards; the actual Tashkent->UTC conversion
     * for querying still happens exclusively inside tashkent_day_bounds_utc(),
     * called from summary() (or any other method here) with these shifted
     * dates exactly as it would with the original ones.
     *
     * @return array{0: string, 1: string} [prevFrom, prevTo]
     */
    public static function previousPeriod(string $from, string $to): array
    {
        $fromDate = new DateTimeImmutable($from);
        $toDate = new DateTimeImmutable($to);
        $days = (int) $fromDate->diff($toDate)->days + 1;

        $prevTo = $fromDate->modify('-1 day');
        $prevFrom = $prevTo->modify('-' . ($days - 1) . ' days');

        return [$prevFrom->format('Y-m-d'), $prevTo->format('Y-m-d')];
    }

    public static function summary(int $shopId, string $from, string $to): array
    {
        $pdo = Database::connect();

        // $from/$to are Tashkent calendar dates; convert to the matching UTC
        // range so they can be compared against the UTC-stored created_at
        // directly (see tashkent_day_bounds_utc() in helpers.php).
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);

        $stmt = $pdo->prepare(
            "SELECT COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS revenue
             FROM sales
             WHERE shop_id = ? AND status = 'completed' AND created_at >= ? AND created_at < ?"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $sales = $stmt->fetch();

        $stmt = $pdo->prepare(
            "SELECT COALESCE(SUM(si.qty * si.cost_price_snapshot), 0) AS cogs
             FROM sale_items si
             INNER JOIN sales s ON s.id = si.sale_id
             WHERE s.shop_id = ? AND s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $cogs = (float) $stmt->fetchColumn();

        $refunds = self::refundTotals($shopId, $startUtc, $endUtc);

        $expenses = Expense::totalByShop($shopId, $from, $to);
        $revenue = round((float) ($sales['revenue'] ?? 0) - $refunds['amount'], 2);
        $cogs = round($cogs - $refunds['cogs'], 2);
        $netProfit = round($revenue - $cogs - $expenses, 2);

        return [
            'sales_count' => (int) ($sales['cnt'] ?? 0),
            'revenue' => $revenue,
            'refunds' => $refunds['amount'],
            'cogs' => $cogs,
            'expenses' => $expenses,
            'net_profit' => $netProfit,
        ];
    }

    /**
     * Refunds issued within a UTC window (by the refund's own date — a return
     * counts in the period it happens, like any cash movement, not back in
     * the period of the original sale): the money given back, and the cost
     * of the goods that came back into stock (at each line's original
     * cost_price_snapshot), both of which reverse part of the period's
     * revenue/COGS.
     *
     * @return array{amount: float, cogs: float}
     */
    private static function refundTotals(int $shopId, string $startUtc, string $endUtc): array
    {
        $pdo = Database::connect();

        $stmt = $pdo->prepare(
            'SELECT COALESCE(SUM(total_amount), 0) FROM refunds WHERE shop_id = ? AND created_at >= ? AND created_at < ?'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $amount = (float) $stmt->fetchColumn();

        $stmt = $pdo->prepare(
            'SELECT COALESCE(SUM(ri.qty * si.cost_price_snapshot), 0)
             FROM refund_items ri
             INNER JOIN refunds r ON r.id = ri.refund_id
             INNER JOIN sale_items si ON si.id = ri.sale_item_id
             WHERE r.shop_id = ? AND r.created_at >= ? AND r.created_at < ?'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);

        return ['amount' => $amount, 'cogs' => (float) $stmt->fetchColumn()];
    }

    /**
     * Best sellers for a period, net of both the sale-level discount and any
     * refunds issued in the period. sale_items.subtotal is the pre-discount
     * line amount; each line's actual revenue is its share of the sale's
     * discounted total (subtotal * total / (total + discount)), the same
     * proportional split Refund::create() already uses for a refund line's
     * amount, so refund_items.amount can be subtracted from it directly.
     */
    public static function topProducts(int $shopId, string $from, string $to, int $limit = 10): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);
        $pdo = Database::connect();

        $stmt = $pdo->prepare(
            "SELECT si.product_id, MAX(si.product_name) AS product_name, SUM(si.qty) AS qty,
                    SUM(CASE WHEN (s.total + s.discount) > 0
                             THEN si.subtotal * s.total / (s.total + s.discount)
                             ELSE 0 END) AS revenue
             FROM sale_items si
             INNER JOIN sales s ON s.id = si.sale_id
             WHERE s.shop_id = ? AND s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
             GROUP BY si.product_id"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);

        $byProduct = [];
        foreach ($stmt->fetchAll() as $row) {
            $byProduct[(int) $row['product_id']] = [
                'product_id' => (int) $row['product_id'],
                'product_name' => (string) $row['product_name'],
                'qty_sold' => (float) $row['qty'],
                'revenue' => (float) $row['revenue'],
            ];
        }

        $stmt = $pdo->prepare(
            'SELECT si.product_id, MAX(si.product_name) AS product_name, SUM(ri.qty) AS qty, SUM(ri.amount) AS amount
             FROM refund_items ri
             INNER JOIN refunds r ON r.id = ri.refund_id
             INNER JOIN sale_items si ON si.id = ri.sale_item_id
             WHERE r.shop_id = ? AND r.created_at >= ? AND r.created_at < ?
             GROUP BY si.product_id'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);

        foreach ($stmt->fetchAll() as $row) {
            $productId = (int) $row['product_id'];
            $byProduct[$productId] ??= [
                'product_id' => $productId,
                'product_name' => (string) $row['product_name'],
                'qty_sold' => 0.0,
                'revenue' => 0.0,
            ];
            $byProduct[$productId]['qty_sold'] -= (float) $row['qty'];
            $byProduct[$productId]['revenue'] -= (float) $row['amount'];
        }

        $rows = array_values(array_filter(
            $byProduct,
            static fn (array $row): bool => $row['qty_sold'] > 0.0001
        ));
        foreach ($rows as &$row) {
            $row['qty_sold'] = round($row['qty_sold'], 2);
            $row['revenue'] = round($row['revenue'], 2);
        }
        unset($row);

        usort($rows, static fn (array $a, array $b): int => [$b['qty_sold'], $b['revenue']] <=> [$a['qty_sold'], $a['revenue']]);

        return array_slice($rows, 0, $limit);
    }

    /**
     * Splits completed-sales revenue into naqd/karta/qarz totals.
     *
     * A sale's total can now be split across several payment methods at once
     * (see sale_payments / Sale::create()), so this aggregates the itemized
     * sale_payments rows rather than sales.payment_type — which, for a mixed
     * sale, only ever says 'aralash' and carries no breakdown of its own.
     * Sales created before that shipped have no sale_payments rows at all;
     * those fall back to their single payment_type/total exactly as before.
     */
    public static function paymentBreakdown(int $shopId, string $from, string $to): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);
        $pdo = Database::connect();

        $result = ['naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0];

        $stmt = $pdo->prepare(
            "SELECT sp.payment_type, COALESCE(SUM(sp.amount), 0) AS total
             FROM sale_payments sp
             INNER JOIN sales s ON s.id = sp.sale_id
             WHERE s.shop_id = ? AND s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
             GROUP BY sp.payment_type"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            if (isset($result[$row['payment_type']])) {
                $result[$row['payment_type']] += (float) $row['total'];
            }
        }

        $stmt = $pdo->prepare(
            "SELECT s.payment_type, COALESCE(SUM(s.total), 0) AS total
             FROM sales s
             WHERE s.shop_id = ? AND s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
               AND NOT EXISTS (SELECT 1 FROM sale_payments sp WHERE sp.sale_id = s.id)
             GROUP BY s.payment_type"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            if (isset($result[$row['payment_type']])) {
                $result[$row['payment_type']] += (float) $row['total'];
            }
        }

        // Refunds issued in the period come back off the method each one
        // actually went back through (refund_payments), so a refunded debt
        // sale no longer counts as outstanding qarz here.
        foreach (Refund::totalsByPayment($shopId, $startUtc, $endUtc) as $type => $amount) {
            $result[$type] = round($result[$type] - $amount, 2);
        }

        return $result;
    }

    /**
     * The end-of-day (Z-report) view for a single Tashkent calendar day: cash
     * / card / debt-issued sales and refunds shop-wide and per cashier, plus
     * the net cash the drawer should hold. Only the naqd share of a refund
     * (refund_payments) is cash leaving the drawer — a refund of a card sale
     * goes back to the card, and one of a debt sale only reduces the
     * customer's ledger, so neither touches the cash count.
     */
    public static function shiftReport(int $shopId, string $ymd): array
    {
        $pdo = Database::connect();
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($ymd);

        $byCashier = [];
        $ensure = static function (int $id, string $name) use (&$byCashier): void {
            if (!isset($byCashier[$id])) {
                $byCashier[$id] = [
                    'cashier_id' => $id,
                    'cashier_name' => $name,
                    'naqd' => 0.0,
                    'karta' => 0.0,
                    'qarz' => 0.0,
                    'refunds' => 0.0,
                    'refunds_naqd' => 0.0,
                    'refunds_karta' => 0.0,
                    'refunds_qarz' => 0.0,
                ];
            }
        };

        // Itemized breakdown for sales that have sale_payments rows (every
        // sale created since split payments shipped).
        $stmt = $pdo->prepare(
            "SELECT s.cashier_id, u.full_name AS cashier_name, sp.payment_type, SUM(sp.amount) AS total
             FROM sales s
             INNER JOIN sale_payments sp ON sp.sale_id = s.id
             INNER JOIN users u ON u.id = s.cashier_id
             WHERE s.shop_id = ? AND s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
             GROUP BY s.cashier_id, sp.payment_type"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $paymentRows = $stmt->fetchAll();

        // Older sales with no sale_payments rows at all — fall back to their
        // own single payment_type/total.
        $stmt = $pdo->prepare(
            "SELECT s.cashier_id, u.full_name AS cashier_name, s.payment_type, SUM(s.total) AS total
             FROM sales s
             INNER JOIN users u ON u.id = s.cashier_id
             WHERE s.shop_id = ? AND s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
               AND NOT EXISTS (SELECT 1 FROM sale_payments sp WHERE sp.sale_id = s.id)
             GROUP BY s.cashier_id, s.payment_type"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $legacyRows = $stmt->fetchAll();

        foreach ([$paymentRows, $legacyRows] as $rows) {
            foreach ($rows as $row) {
                $cashierId = (int) $row['cashier_id'];
                $ensure($cashierId, (string) $row['cashier_name']);
                $type = $row['payment_type'];
                if (isset($byCashier[$cashierId][$type])) {
                    $byCashier[$cashierId][$type] += (float) $row['total'];
                }
            }
        }

        // Refunds are attributed to whoever processed them (refunded_by) —
        // that's whose drawer the cash came out of — split by the method each
        // one actually went back through.
        $stmt = $pdo->prepare(
            "SELECT r.refunded_by AS cashier_id, u.full_name AS cashier_name, rp.payment_type, SUM(rp.amount) AS total
             FROM refunds r
             INNER JOIN refund_payments rp ON rp.refund_id = r.id
             INNER JOIN users u ON u.id = r.refunded_by
             WHERE r.shop_id = ? AND r.created_at >= ? AND r.created_at < ?
             GROUP BY r.refunded_by, rp.payment_type"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $cashierId = (int) $row['cashier_id'];
            $ensure($cashierId, (string) $row['cashier_name']);
            $key = 'refunds_' . $row['payment_type'];
            if (isset($byCashier[$cashierId][$key])) {
                $byCashier[$cashierId][$key] += (float) $row['total'];
                $byCashier[$cashierId]['refunds'] += (float) $row['total'];
            }
        }

        foreach ($byCashier as &$row) {
            $row['net_cash'] = round($row['naqd'] - $row['refunds_naqd'], 2);
        }
        unset($row);

        usort($byCashier, static fn (array $a, array $b): int => strcmp((string) $a['cashier_name'], (string) $b['cashier_name']));
        $byCashier = array_values($byCashier);

        $totals = [
            'naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0,
            'refunds' => 0.0, 'refunds_naqd' => 0.0, 'refunds_karta' => 0.0, 'refunds_qarz' => 0.0,
        ];
        foreach ($byCashier as $row) {
            foreach ($totals as $key => $_) {
                $totals[$key] += $row[$key];
            }
        }
        foreach ($totals as $key => $value) {
            $totals[$key] = round($value, 2);
        }
        $totals['net_cash'] = round($totals['naqd'] - $totals['refunds_naqd'], 2);

        return ['totals' => $totals, 'by_cashier' => $byCashier];
    }

    public static function dailyRevenue(int $shopId, string $from, string $to): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);

        // created_at is stored in UTC; shifting it by Tashkent's fixed +5:00
        // offset before taking date() buckets each row into its correct
        // Tashkent calendar day instead of its UTC one.
        $stmt = Database::connect()->prepare(
            "SELECT date(created_at, '+5 hours') AS d, COALESCE(SUM(total), 0) AS revenue
             FROM sales
             WHERE shop_id = ? AND status = 'completed' AND created_at >= ? AND created_at < ?
             GROUP BY date(created_at, '+5 hours')"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $byDate = [];
        foreach ($stmt->fetchAll() as $row) {
            $byDate[$row['d']] = (float) $row['revenue'];
        }

        $stmt = Database::connect()->prepare(
            "SELECT date(created_at, '+5 hours') AS d, COALESCE(SUM(total_amount), 0) AS refunded
             FROM refunds
             WHERE shop_id = ? AND created_at >= ? AND created_at < ?
             GROUP BY date(created_at, '+5 hours')"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $byDate[$row['d']] = round(($byDate[$row['d']] ?? 0.0) - (float) $row['refunded'], 2);
        }

        $result = [];
        $current = new DateTimeImmutable($from);
        $end = new DateTimeImmutable($to);
        $dayLimit = 62; // safety cap so an accidental huge range can't loop forever

        while ($current <= $end && count($result) < $dayLimit) {
            $key = $current->format('Y-m-d');
            $result[] = ['date' => $key, 'revenue' => $byDate[$key] ?? 0.0];
            $current = $current->modify('+1 day');
        }

        return $result;
    }

    /**
     * Super-admin cross-shop rollup for the given period: one row per shop
     * (revenue/cogs/expenses/net_profit/sales_count, same shape as summary())
     * plus the sum of all of them. This is a *new* method that aggregates
     * across every shop rather than filtering to one shop_id — it does not
     * touch summary()/topProducts()/paymentBreakdown()/dailyRevenue()/
     * shiftReport() above, whose $shopId-scoped signatures other pages already
     * depend on.
     *
     * @return array{totals: array{sales_count: int, revenue: float, cogs: float, expenses: float, net_profit: float}, by_shop: array<int, array>}
     */
    public static function allShopsSummary(string $from, string $to): array
    {
        $byShop = self::perShopSummary($from, $to);

        $totals = ['sales_count' => 0, 'revenue' => 0.0, 'cogs' => 0.0, 'expenses' => 0.0, 'net_profit' => 0.0];
        foreach ($byShop as $row) {
            $totals['sales_count'] += $row['sales_count'];
            $totals['revenue'] += $row['revenue'];
            $totals['cogs'] += $row['cogs'];
            $totals['expenses'] += $row['expenses'];
            $totals['net_profit'] += $row['net_profit'];
        }

        return ['totals' => $totals, 'by_shop' => $byShop];
    }

    /**
     * Per-shop breakdown for the period, one row per shop that exists (even
     * one with zero activity), sorted by revenue descending. Three grouped
     * queries (sales, sale_items/cogs, expenses) rather than one join across
     * sales+sale_items+expenses — joining sale_items to sales would multiply
     * the sales-side SUM/COUNT by however many line items each sale has,
     * exactly the trap summary() avoids for a single shop by running the same
     * two queries separately.
     *
     * @return array<int, array{shop_id: int, shop_name: string, sales_count: int, revenue: float, cogs: float, expenses: float, net_profit: float}>
     */
    public static function perShopSummary(string $from, string $to): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);
        $pdo = Database::connect();

        $salesByShop = [];
        $stmt = $pdo->prepare(
            "SELECT shop_id, COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS revenue
             FROM sales
             WHERE status = 'completed' AND created_at >= ? AND created_at < ?
             GROUP BY shop_id"
        );
        $stmt->execute([$startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $salesByShop[(int) $row['shop_id']] = ['cnt' => (int) $row['cnt'], 'revenue' => (float) $row['revenue']];
        }

        $cogsByShop = [];
        $stmt = $pdo->prepare(
            "SELECT s.shop_id, COALESCE(SUM(si.qty * si.cost_price_snapshot), 0) AS cogs
             FROM sale_items si
             INNER JOIN sales s ON s.id = si.sale_id
             WHERE s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
             GROUP BY s.shop_id"
        );
        $stmt->execute([$startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $cogsByShop[(int) $row['shop_id']] = (float) $row['cogs'];
        }

        // Refunds in the period reverse part of each shop's revenue and COGS
        // (see refundTotals()) — two more grouped queries, same no-fan-out
        // reasoning as above.
        $refundsByShop = [];
        $stmt = $pdo->prepare(
            'SELECT shop_id, COALESCE(SUM(total_amount), 0) AS total
             FROM refunds WHERE created_at >= ? AND created_at < ?
             GROUP BY shop_id'
        );
        $stmt->execute([$startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $refundsByShop[(int) $row['shop_id']] = (float) $row['total'];
        }

        $refundCogsByShop = [];
        $stmt = $pdo->prepare(
            'SELECT r.shop_id, COALESCE(SUM(ri.qty * si.cost_price_snapshot), 0) AS cogs
             FROM refund_items ri
             INNER JOIN refunds r ON r.id = ri.refund_id
             INNER JOIN sale_items si ON si.id = ri.sale_item_id
             WHERE r.created_at >= ? AND r.created_at < ?
             GROUP BY r.shop_id'
        );
        $stmt->execute([$startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $refundCogsByShop[(int) $row['shop_id']] = (float) $row['cogs'];
        }

        // expense_date is a plain Tashkent calendar date (see Expense::totalByShop()),
        // not a UTC created_at timestamp, so it's compared directly against
        // $from/$to rather than through tashkent_day_bounds_utc().
        $expensesByShop = [];
        $stmt = $pdo->prepare(
            'SELECT shop_id, COALESCE(SUM(amount), 0) AS total
             FROM expenses
             WHERE expense_date BETWEEN ? AND ?
             GROUP BY shop_id'
        );
        $stmt->execute([$from, $to]);
        foreach ($stmt->fetchAll() as $row) {
            $expensesByShop[(int) $row['shop_id']] = (float) $row['total'];
        }

        $result = [];
        foreach (Shop::all() as $shop) {
            $shopId = (int) $shop['id'];
            $revenue = round(($salesByShop[$shopId]['revenue'] ?? 0.0) - ($refundsByShop[$shopId] ?? 0.0), 2);
            $cogs = round(($cogsByShop[$shopId] ?? 0.0) - ($refundCogsByShop[$shopId] ?? 0.0), 2);
            $expenses = $expensesByShop[$shopId] ?? 0.0;

            $result[] = [
                'shop_id' => $shopId,
                'shop_name' => (string) $shop['name'],
                'sales_count' => $salesByShop[$shopId]['cnt'] ?? 0,
                'revenue' => $revenue,
                'cogs' => $cogs,
                'expenses' => $expenses,
                'net_profit' => $revenue - $cogs - $expenses,
            ];
        }

        usort($result, static fn (array $a, array $b): int => $b['revenue'] <=> $a['revenue']);

        return $result;
    }

    /**
     * Per-cashier ranking for a period: sales count, revenue, COGS, net
     * contribution (revenue - COGS) and computed commission, one row per
     * shop user who can ring up a sale (the owner + every employee) — even
     * one with zero sales in the period — sorted by revenue descending.
     *
     * This extends shiftReport()'s "GROUP BY cashier_id, join users" pattern
     * from a single Tashkent day to an arbitrary period, and extends
     * summary()'s COGS-from-cost_price_snapshot pattern the same way,
     * grouped by cashier instead of shop-wide. Revenue/COGS are computed
     * once here (two grouped queries, same "don't join sale_items onto
     * sales directly" trap perShopSummary()'s docblock explains) and reused
     * for both the leaderboard ranking and the commission figure, rather
     * than running the same per-cashier revenue query twice for two
     * separate features.
     *
     * commission_rate/commission_amount are null when the user has no
     * commission_rate configured ("not eligible for commission this
     * period"), never 0 — a configured rate of 0 still produces a real
     * commission_amount of 0.0 ("eligible, earned nothing"), which is a
     * different fact from "not eligible" and is shown differently by the
     * caller (see reports/leaderboard.php).
     *
     * @return array<int, array{cashier_id:int, cashier_name:string, role:string,
     *     sales_count:int, revenue:float, cogs:float, net_contribution:float,
     *     commission_rate: ?float, commission_amount: ?float}>
     */
    public static function cashierLeaderboard(int $shopId, string $from, string $to): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);
        $pdo = Database::connect();

        $salesByCashier = [];
        $stmt = $pdo->prepare(
            "SELECT cashier_id, COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS revenue
             FROM sales
             WHERE shop_id = ? AND status = 'completed' AND created_at >= ? AND created_at < ?
             GROUP BY cashier_id"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $salesByCashier[(int) $row['cashier_id']] = ['cnt' => (int) $row['cnt'], 'revenue' => (float) $row['revenue']];
        }

        $cogsByCashier = [];
        $stmt = $pdo->prepare(
            "SELECT s.cashier_id, COALESCE(SUM(si.qty * si.cost_price_snapshot), 0) AS cogs
             FROM sale_items si
             INNER JOIN sales s ON s.id = si.sale_id
             WHERE s.shop_id = ? AND s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
             GROUP BY s.cashier_id"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $cogsByCashier[(int) $row['cashier_id']] = (float) $row['cogs'];
        }

        // Refunds in the period come off the revenue/COGS of whoever made the
        // original sale (not whoever processed the return), so commission is
        // never paid on goods that came back.
        $refundsByCashier = [];
        $stmt = $pdo->prepare(
            'SELECT s.cashier_id, COALESCE(SUM(r.total_amount), 0) AS total
             FROM refunds r
             INNER JOIN sales s ON s.id = r.sale_id
             WHERE r.shop_id = ? AND r.created_at >= ? AND r.created_at < ?
             GROUP BY s.cashier_id'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $refundsByCashier[(int) $row['cashier_id']] = (float) $row['total'];
        }

        $refundCogsByCashier = [];
        $stmt = $pdo->prepare(
            'SELECT s.cashier_id, COALESCE(SUM(ri.qty * si.cost_price_snapshot), 0) AS cogs
             FROM refund_items ri
             INNER JOIN refunds r ON r.id = ri.refund_id
             INNER JOIN sale_items si ON si.id = ri.sale_item_id
             INNER JOIN sales s ON s.id = r.sale_id
             WHERE r.shop_id = ? AND r.created_at >= ? AND r.created_at < ?
             GROUP BY s.cashier_id'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $refundCogsByCashier[(int) $row['cashier_id']] = (float) $row['cogs'];
        }

        $stmt = $pdo->prepare(
            "SELECT id, full_name, role, commission_rate FROM users
             WHERE shop_id = ? AND role IN ('owner', 'employee')
             ORDER BY full_name"
        );
        $stmt->execute([$shopId]);

        $result = [];
        foreach ($stmt->fetchAll() as $u) {
            $cashierId = (int) $u['id'];
            $revenue = round(($salesByCashier[$cashierId]['revenue'] ?? 0.0) - ($refundsByCashier[$cashierId] ?? 0.0), 2);
            $cogs = round(($cogsByCashier[$cashierId] ?? 0.0) - ($refundCogsByCashier[$cashierId] ?? 0.0), 2);
            $rate = $u['commission_rate'] !== null ? (float) $u['commission_rate'] : null;

            $result[] = [
                'cashier_id' => $cashierId,
                'cashier_name' => (string) $u['full_name'],
                'role' => (string) $u['role'],
                'sales_count' => $salesByCashier[$cashierId]['cnt'] ?? 0,
                'revenue' => $revenue,
                'cogs' => $cogs,
                'net_contribution' => $revenue - $cogs,
                'commission_rate' => $rate,
                'commission_amount' => $rate !== null ? max(0.0, round($revenue * $rate / 100, 2)) : null,
            ];
        }

        usort($result, static fn (array $a, array $b): int => $b['revenue'] <=> $a['revenue']);

        return $result;
    }
}

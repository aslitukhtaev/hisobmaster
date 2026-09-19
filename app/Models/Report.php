<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class Report
{
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

        $expenses = Expense::totalByShop($shopId, $from, $to);
        $revenue = (float) ($sales['revenue'] ?? 0);
        $netProfit = $revenue - $cogs - $expenses;

        return [
            'sales_count' => (int) ($sales['cnt'] ?? 0),
            'revenue' => $revenue,
            'cogs' => $cogs,
            'expenses' => $expenses,
            'net_profit' => $netProfit,
        ];
    }

    public static function topProducts(int $shopId, string $from, string $to, int $limit = 10): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);

        $stmt = Database::connect()->prepare(
            "SELECT si.product_id, si.product_name, SUM(si.qty) AS qty_sold, SUM(si.subtotal) AS revenue
             FROM sale_items si
             INNER JOIN sales s ON s.id = si.sale_id
             WHERE s.shop_id = :shop_id AND s.status = 'completed' AND s.created_at >= :start AND s.created_at < :end
             GROUP BY si.product_id, si.product_name
             ORDER BY qty_sold DESC
             LIMIT :limit"
        );
        $stmt->bindValue(':shop_id', $shopId, \PDO::PARAM_INT);
        $stmt->bindValue(':start', $startUtc);
        $stmt->bindValue(':end', $endUtc);
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();

        return $stmt->fetchAll();
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

        return $result;
    }

    /**
     * The end-of-day (Z-report) view for a single Tashkent calendar day: cash
     * / card / debt-issued / refund totals shop-wide and per cashier, plus the
     * net cash the drawer should hold (cash sales minus refunds paid out in
     * cash — refunds don't record which method they were returned through, so
     * every refund is assumed to have come back out of the cash drawer, which
     * is the conservative assumption for a small shop's till count).
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

        $stmt = $pdo->prepare(
            "SELECT r.refunded_by AS cashier_id, u.full_name AS cashier_name, SUM(r.total_amount) AS total
             FROM refunds r
             INNER JOIN users u ON u.id = r.refunded_by
             WHERE r.shop_id = ? AND r.created_at >= ? AND r.created_at < ?
             GROUP BY r.refunded_by"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        foreach ($stmt->fetchAll() as $row) {
            $cashierId = (int) $row['cashier_id'];
            $ensure($cashierId, (string) $row['cashier_name']);
            $byCashier[$cashierId]['refunds'] += (float) $row['total'];
        }

        foreach ($byCashier as &$row) {
            $row['net_cash'] = round($row['naqd'] - $row['refunds'], 2);
        }
        unset($row);

        usort($byCashier, static fn (array $a, array $b): int => strcmp((string) $a['cashier_name'], (string) $b['cashier_name']));
        $byCashier = array_values($byCashier);

        $totals = ['naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0, 'refunds' => 0.0];
        foreach ($byCashier as $row) {
            $totals['naqd'] += $row['naqd'];
            $totals['karta'] += $row['karta'];
            $totals['qarz'] += $row['qarz'];
            $totals['refunds'] += $row['refunds'];
        }
        $totals['net_cash'] = round($totals['naqd'] - $totals['refunds'], 2);

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

        $result = [];
        $current = new \DateTimeImmutable($from);
        $end = new \DateTimeImmutable($to);
        $dayLimit = 62; // safety cap so an accidental huge range can't loop forever

        while ($current <= $end && count($result) < $dayLimit) {
            $key = $current->format('Y-m-d');
            $result[] = ['date' => $key, 'revenue' => $byDate[$key] ?? 0.0];
            $current = $current->modify('+1 day');
        }

        return $result;
    }
}

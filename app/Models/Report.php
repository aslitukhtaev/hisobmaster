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

    public static function paymentBreakdown(int $shopId, string $from, string $to): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);

        $stmt = Database::connect()->prepare(
            "SELECT payment_type, COALESCE(SUM(total), 0) AS total
             FROM sales
             WHERE shop_id = ? AND status = 'completed' AND created_at >= ? AND created_at < ?
             GROUP BY payment_type"
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);
        $rows = $stmt->fetchAll();

        $result = ['naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0];
        foreach ($rows as $row) {
            $result[$row['payment_type']] = (float) $row['total'];
        }

        return $result;
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

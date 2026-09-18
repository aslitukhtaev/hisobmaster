<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class Report
{
    public static function summary(int $shopId, string $from, string $to): array
    {
        $pdo = Database::connect();

        $stmt = $pdo->prepare(
            "SELECT COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS revenue
             FROM sales
             WHERE shop_id = ? AND status = 'completed' AND date(created_at) BETWEEN ? AND ?"
        );
        $stmt->execute([$shopId, $from, $to]);
        $sales = $stmt->fetch();

        $stmt = $pdo->prepare(
            "SELECT COALESCE(SUM(si.qty * si.cost_price_snapshot), 0) AS cogs
             FROM sale_items si
             INNER JOIN sales s ON s.id = si.sale_id
             WHERE s.shop_id = ? AND s.status = 'completed' AND date(s.created_at) BETWEEN ? AND ?"
        );
        $stmt->execute([$shopId, $from, $to]);
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
        $stmt = Database::connect()->prepare(
            "SELECT si.product_id, si.product_name, SUM(si.qty) AS qty_sold, SUM(si.subtotal) AS revenue
             FROM sale_items si
             INNER JOIN sales s ON s.id = si.sale_id
             WHERE s.shop_id = :shop_id AND s.status = 'completed' AND date(s.created_at) BETWEEN :from AND :to
             GROUP BY si.product_id, si.product_name
             ORDER BY qty_sold DESC
             LIMIT :limit"
        );
        $stmt->bindValue(':shop_id', $shopId, \PDO::PARAM_INT);
        $stmt->bindValue(':from', $from);
        $stmt->bindValue(':to', $to);
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();

        return $stmt->fetchAll();
    }

    public static function paymentBreakdown(int $shopId, string $from, string $to): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT payment_type, COALESCE(SUM(total), 0) AS total
             FROM sales
             WHERE shop_id = ? AND status = 'completed' AND date(created_at) BETWEEN ? AND ?
             GROUP BY payment_type"
        );
        $stmt->execute([$shopId, $from, $to]);
        $rows = $stmt->fetchAll();

        $result = ['naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0];
        foreach ($rows as $row) {
            $result[$row['payment_type']] = (float) $row['total'];
        }

        return $result;
    }

    public static function dailyRevenue(int $shopId, string $from, string $to): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT date(created_at) AS d, COALESCE(SUM(total), 0) AS revenue
             FROM sales
             WHERE shop_id = ? AND status = 'completed' AND date(created_at) BETWEEN ? AND ?
             GROUP BY date(created_at)"
        );
        $stmt->execute([$shopId, $from, $to]);
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

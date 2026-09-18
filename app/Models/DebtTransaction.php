<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class DebtTransaction
{
    public static function currentBalance(int $customerId): float
    {
        $stmt = Database::connect()->prepare(
            'SELECT balance_after FROM debt_transactions WHERE customer_id = ? ORDER BY id DESC LIMIT 1'
        );
        $stmt->execute([$customerId]);
        $balance = $stmt->fetchColumn();

        return $balance !== false ? (float) $balance : 0.0;
    }

    public static function record(
        int $shopId,
        int $customerId,
        ?int $saleId,
        string $type,
        float $amount,
        int $createdBy
    ): void {
        $current = self::currentBalance($customerId);
        $balanceAfter = $type === 'qarz' ? $current + $amount : $current - $amount;

        Database::connect()->prepare(
            'INSERT INTO debt_transactions (shop_id, customer_id, sale_id, type, amount, balance_after, created_by)
             VALUES (?, ?, ?, ?, ?, ?, ?)'
        )->execute([$shopId, $customerId, $saleId, $type, $amount, $balanceAfter, $createdBy]);
    }

    public static function historyByCustomer(int $customerId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM debt_transactions WHERE customer_id = ? ORDER BY id DESC'
        );
        $stmt->execute([$customerId]);

        return $stmt->fetchAll();
    }

    public static function totalDebtByShop(int $shopId): float
    {
        // Each customer's current balance is their most recent balance_after row.
        $stmt = Database::connect()->prepare(
            'SELECT dt.customer_id, dt.balance_after FROM debt_transactions dt
             INNER JOIN (
                 SELECT customer_id, MAX(id) AS max_id FROM debt_transactions WHERE shop_id = ? GROUP BY customer_id
             ) latest ON latest.max_id = dt.id'
        );
        $stmt->execute([$shopId]);
        $rows = $stmt->fetchAll();

        $total = 0.0;
        foreach ($rows as $row) {
            $total += max(0.0, (float) $row['balance_after']);
        }

        return $total;
    }
}

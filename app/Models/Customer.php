<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class Customer
{
    public static function allByShop(int $shopId): array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM customers WHERE shop_id = ? ORDER BY full_name');
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM customers WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function create(int $shopId, string $fullName, ?string $phone, ?string $note = null): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare('INSERT INTO customers (shop_id, full_name, phone, note) VALUES (?, ?, ?, ?)');
        $stmt->execute([$shopId, $fullName, $phone, $note]);

        return (int) $pdo->lastInsertId();
    }

    public static function findOrCreate(int $shopId, string $fullName, ?string $phone): int
    {
        $phone = $phone !== null && trim($phone) !== '' ? trim($phone) : null;

        if ($phone !== null) {
            $stmt = Database::connect()->prepare(
                'SELECT id FROM customers WHERE shop_id = ? AND phone = ? LIMIT 1'
            );
            $stmt->execute([$shopId, $phone]);
            $id = $stmt->fetchColumn();

            if ($id !== false) {
                return (int) $id;
            }
        }

        return self::create($shopId, $fullName, $phone);
    }

    public static function update(int $id, int $shopId, string $fullName, ?string $phone, ?string $note): void
    {
        Database::connect()
            ->prepare('UPDATE customers SET full_name = ?, phone = ?, note = ? WHERE id = ? AND shop_id = ?')
            ->execute([$fullName, $phone, $note, $id, $shopId]);
    }

    /**
     * All customers in the shop with their current debt balance
     * (the balance_after of each customer's most recent ledger entry).
     */
    public static function allWithBalance(int $shopId, bool $onlyDebtors = false): array
    {
        $sql = "SELECT c.*, COALESCE(latest.balance_after, 0) AS balance
                FROM customers c
                LEFT JOIN (
                    SELECT dt1.customer_id, dt1.balance_after
                    FROM debt_transactions dt1
                    INNER JOIN (
                        SELECT customer_id, MAX(id) AS max_id FROM debt_transactions GROUP BY customer_id
                    ) dt2 ON dt2.customer_id = dt1.customer_id AND dt2.max_id = dt1.id
                ) latest ON latest.customer_id = c.id
                WHERE c.shop_id = ?";

        if ($onlyDebtors) {
            $sql .= ' AND COALESCE(latest.balance_after, 0) > 0';
        }

        $sql .= ' ORDER BY balance DESC, c.full_name';

        $stmt = Database::connect()->prepare($sql);
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }
}

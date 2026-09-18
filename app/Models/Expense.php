<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class Expense
{
    public static function allByShop(int $shopId, ?string $from = null, ?string $to = null): array
    {
        $sql = 'SELECT e.*, c.name AS category_name
                FROM expenses e
                LEFT JOIN categories c ON c.id = e.category_id
                WHERE e.shop_id = ?';
        $params = [$shopId];

        if ($from !== null && $to !== null) {
            $sql .= ' AND e.expense_date BETWEEN ? AND ?';
            $params[] = $from;
            $params[] = $to;
        }

        $sql .= ' ORDER BY e.expense_date DESC, e.id DESC';

        $stmt = Database::connect()->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM expenses WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function create(array $data): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare(
            'INSERT INTO expenses (shop_id, category_id, amount, description, expense_date, created_by)
             VALUES (?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $data['shop_id'],
            $data['category_id'] ?? null,
            $data['amount'],
            $data['description'] ?? null,
            $data['expense_date'],
            $data['created_by'],
        ]);

        return (int) $pdo->lastInsertId();
    }

    public static function update(int $id, int $shopId, array $data): void
    {
        Database::connect()->prepare(
            'UPDATE expenses SET category_id = ?, amount = ?, description = ?, expense_date = ? WHERE id = ? AND shop_id = ?'
        )->execute([
            $data['category_id'] ?? null,
            $data['amount'],
            $data['description'] ?? null,
            $data['expense_date'],
            $id,
            $shopId,
        ]);
    }

    public static function delete(int $id, int $shopId): void
    {
        Database::connect()
            ->prepare('DELETE FROM expenses WHERE id = ? AND shop_id = ?')
            ->execute([$id, $shopId]);
    }

    public static function totalByShop(int $shopId, ?string $from = null, ?string $to = null): float
    {
        $sql = 'SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE shop_id = ?';
        $params = [$shopId];

        if ($from !== null && $to !== null) {
            $sql .= ' AND expense_date BETWEEN ? AND ?';
            $params[] = $from;
            $params[] = $to;
        }

        $stmt = Database::connect()->prepare($sql);
        $stmt->execute($params);

        return (float) $stmt->fetchColumn();
    }
}

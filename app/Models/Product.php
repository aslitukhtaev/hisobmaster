<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class Product
{
    public static function allByShop(int $shopId, ?string $search = null): array
    {
        $sql = 'SELECT p.*, c.name AS category_name
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                WHERE p.shop_id = ?';
        $params = [$shopId];

        if ($search !== null && $search !== '') {
            $sql .= ' AND (p.name LIKE ? OR p.barcode LIKE ?)';
            $params[] = '%' . $search . '%';
            $params[] = '%' . $search . '%';
        }

        $sql .= ' ORDER BY p.name';

        $stmt = Database::connect()->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM products WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function create(array $data): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare(
            'INSERT INTO products (shop_id, category_id, name, unit, cost_price, sell_price, stock_qty, barcode, status)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $data['shop_id'],
            $data['category_id'] ?? null,
            $data['name'],
            $data['unit'],
            $data['cost_price'],
            $data['sell_price'],
            $data['stock_qty'],
            $data['barcode'] ?? null,
            'active',
        ]);

        return (int) $pdo->lastInsertId();
    }

    public static function update(int $id, int $shopId, array $data): void
    {
        Database::connect()->prepare(
            'UPDATE products
             SET category_id = ?, name = ?, unit = ?, cost_price = ?, sell_price = ?, stock_qty = ?, barcode = ?, updated_at = datetime(\'now\')
             WHERE id = ? AND shop_id = ?'
        )->execute([
            $data['category_id'] ?? null,
            $data['name'],
            $data['unit'],
            $data['cost_price'],
            $data['sell_price'],
            $data['stock_qty'],
            $data['barcode'] ?? null,
            $id,
            $shopId,
        ]);
    }

    public static function setStatus(int $id, int $shopId, string $status): void
    {
        Database::connect()
            ->prepare('UPDATE products SET status = ? WHERE id = ? AND shop_id = ?')
            ->execute([$status, $id, $shopId]);
    }

    public static function counts(int $shopId): array
    {
        $pdo = Database::connect();

        $total = (int) self::scalar($pdo, 'SELECT COUNT(*) FROM products WHERE shop_id = ?', [$shopId]);
        $active = (int) self::scalar(
            $pdo,
            "SELECT COUNT(*) FROM products WHERE shop_id = ? AND status = 'active'",
            [$shopId]
        );
        $stockValue = (float) self::scalar(
            $pdo,
            'SELECT COALESCE(SUM(cost_price * stock_qty), 0) FROM products WHERE shop_id = ?',
            [$shopId]
        );

        return ['total' => $total, 'active' => $active, 'stock_value' => $stockValue];
    }

    private static function scalar(\PDO $pdo, string $sql, array $params): mixed
    {
        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchColumn();
    }
}

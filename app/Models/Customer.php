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
}

<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class Category
{
    public static function allByShop(int $shopId, string $type = 'product'): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM categories WHERE shop_id = ? AND type = ? ORDER BY name'
        );
        $stmt->execute([$shopId, $type]);

        return $stmt->fetchAll();
    }

    public static function findOrCreate(int $shopId, string $name, string $type = 'product'): int
    {
        $pdo = Database::connect();

        $stmt = $pdo->prepare(
            'SELECT id FROM categories WHERE shop_id = ? AND type = ? AND LOWER(name) = LOWER(?)'
        );
        $stmt->execute([$shopId, $type, $name]);
        $id = $stmt->fetchColumn();

        if ($id !== false) {
            return (int) $id;
        }

        $insert = $pdo->prepare('INSERT INTO categories (shop_id, name, type) VALUES (?, ?, ?)');
        $insert->execute([$shopId, $name, $type]);

        return (int) $pdo->lastInsertId();
    }
}

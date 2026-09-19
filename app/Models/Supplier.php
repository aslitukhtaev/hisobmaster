<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class Supplier
{
    public static function allByShop(int $shopId): array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM suppliers WHERE shop_id = ? ORDER BY name');
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM suppliers WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function create(int $shopId, string $name, ?string $phone, ?string $address, ?string $note): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare('INSERT INTO suppliers (shop_id, name, phone, address, note) VALUES (?, ?, ?, ?, ?)');
        $stmt->execute([$shopId, $name, $phone, $address, $note]);

        return (int) $pdo->lastInsertId();
    }

    public static function update(int $id, int $shopId, string $name, ?string $phone, ?string $address, ?string $note): void
    {
        Database::connect()
            ->prepare('UPDATE suppliers SET name = ?, phone = ?, address = ?, note = ? WHERE id = ? AND shop_id = ?')
            ->execute([$name, $phone, $address, $note, $id, $shopId]);
    }
}

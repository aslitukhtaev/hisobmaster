<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class User
{
    public static function findById(int $id): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM users WHERE id = ?');
        $stmt->execute([$id]);
        return $stmt->fetch() ?: null;
    }

    public static function loginExists(string $login, ?int $excludeId = null): bool
    {
        $sql = 'SELECT COUNT(*) FROM users WHERE login = ?';
        $params = [$login];

        if ($excludeId !== null) {
            $sql .= ' AND id != ?';
            $params[] = $excludeId;
        }

        $stmt = Database::connect()->prepare($sql);
        $stmt->execute($params);

        return ((int) $stmt->fetchColumn()) > 0;
    }

    public static function create(array $data): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare(
            'INSERT INTO users (shop_id, role, full_name, phone, login, password_hash, lang, permissions_json, status)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $data['shop_id'] ?? null,
            $data['role'],
            $data['full_name'],
            $data['phone'] ?? null,
            $data['login'],
            password_hash($data['password'], PASSWORD_DEFAULT),
            $data['lang'] ?? 'uz',
            $data['permissions_json'] ?? null,
            $data['status'] ?? 'active',
        ]);

        return (int) $pdo->lastInsertId();
    }

    public static function updateProfile(int $id, array $data): void
    {
        Database::connect()
            ->prepare('UPDATE users SET full_name = ?, phone = ?, login = ? WHERE id = ?')
            ->execute([$data['full_name'], $data['phone'], $data['login'], $id]);
    }

    public static function updatePassword(int $id, string $password): void
    {
        Database::connect()
            ->prepare('UPDATE users SET password_hash = ? WHERE id = ?')
            ->execute([password_hash($password, PASSWORD_DEFAULT), $id]);
    }

    public static function ownerByShop(int $shopId): ?array
    {
        $stmt = Database::connect()->prepare("SELECT * FROM users WHERE shop_id = ? AND role = 'owner' LIMIT 1");
        $stmt->execute([$shopId]);
        return $stmt->fetch() ?: null;
    }
}

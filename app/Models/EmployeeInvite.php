<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class EmployeeInvite
{
    public static function create(int $shopId, int $createdBy, array $permissions, int $hoursValid = 72): array
    {
        $pdo = Database::connect();
        $token = bin2hex(random_bytes(24));
        $expiresAt = (new \DateTimeImmutable("+{$hoursValid} hours"))->format('Y-m-d H:i:s');

        $stmt = $pdo->prepare(
            'INSERT INTO employee_invites (shop_id, token, preset_permissions_json, created_by, expires_at)
             VALUES (?, ?, ?, ?, ?)'
        );
        $stmt->execute([$shopId, $token, json_encode(array_values($permissions)), $createdBy, $expiresAt]);

        return ['id' => (int) $pdo->lastInsertId(), 'token' => $token, 'expires_at' => $expiresAt];
    }

    public static function findValidByToken(string $token): ?array
    {
        $stmt = Database::connect()->prepare(
            "SELECT * FROM employee_invites
             WHERE token = ? AND used_at IS NULL AND expires_at > datetime('now')"
        );
        $stmt->execute([$token]);

        return $stmt->fetch() ?: null;
    }

    public static function markUsed(int $id, int $usedByUserId): void
    {
        Database::connect()
            ->prepare("UPDATE employee_invites SET used_at = datetime('now'), used_by = ? WHERE id = ?")
            ->execute([$usedByUserId, $id]);
    }

    public static function pendingByShop(int $shopId): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT * FROM employee_invites
             WHERE shop_id = ? AND used_at IS NULL AND expires_at > datetime('now')
             ORDER BY created_at DESC"
        );
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM employee_invites WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function revoke(int $id, int $shopId): void
    {
        Database::connect()
            ->prepare('DELETE FROM employee_invites WHERE id = ? AND shop_id = ?')
            ->execute([$id, $shopId]);
    }
}

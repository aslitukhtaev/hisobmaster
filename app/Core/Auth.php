<?php

declare(strict_types=1);

namespace App\Core;

class Auth
{
    private static ?array $user = null;
    private static bool $loaded = false;

    public static function attempt(string $login, string $password): bool
    {
        $stmt = Database::connect()->prepare('SELECT * FROM users WHERE login = ? AND status = ?');
        $stmt->execute([$login, 'active']);
        $user = $stmt->fetch();

        if (!$user || !password_verify($password, $user['password_hash'])) {
            return false;
        }

        if (!self::isShopActive($user['shop_id'])) {
            return false;
        }

        session_regenerate_id(true);
        $_SESSION['user_id'] = (int) $user['id'];
        self::$user = $user;
        self::$loaded = true;

        return true;
    }

    public static function logout(): void
    {
        self::$user = null;
        self::$loaded = true;
        $_SESSION = [];
        session_regenerate_id(true);
    }

    public static function check(): bool
    {
        return self::user() !== null;
    }

    public static function user(): ?array
    {
        if (self::$loaded) {
            return self::$user;
        }

        self::$loaded = true;

        if (empty($_SESSION['user_id'])) {
            return self::$user = null;
        }

        $stmt = Database::connect()->prepare('SELECT * FROM users WHERE id = ? AND status = ?');
        $stmt->execute([$_SESSION['user_id'], 'active']);
        $user = $stmt->fetch() ?: null;

        if ($user && !self::isShopActive($user['shop_id'])) {
            $user = null;
        }

        return self::$user = $user;
    }

    private static function isShopActive(?int $shopId): bool
    {
        if ($shopId === null) {
            return true;
        }

        $stmt = Database::connect()->prepare('SELECT status FROM shops WHERE id = ?');
        $stmt->execute([$shopId]);

        return $stmt->fetchColumn() === 'active';
    }

    public static function id(): ?int
    {
        $user = self::user();
        return $user ? (int) $user['id'] : null;
    }

    public static function role(): ?string
    {
        return self::user()['role'] ?? null;
    }

    public static function shopId(): ?int
    {
        $shopId = self::user()['shop_id'] ?? null;
        return $shopId !== null ? (int) $shopId : null;
    }

    public static function isSuperAdmin(): bool
    {
        return self::role() === 'super_admin';
    }

    public static function isOwner(): bool
    {
        return self::role() === 'owner';
    }

    public static function isEmployee(): bool
    {
        return self::role() === 'employee';
    }

    public static function can(string $permission): bool
    {
        $user = self::user();
        if (!$user) {
            return false;
        }

        if ($user['role'] === 'super_admin' || $user['role'] === 'owner') {
            return true;
        }

        $permissions = json_decode($user['permissions_json'] ?? '[]', true) ?: [];
        return in_array($permission, $permissions, true);
    }
}

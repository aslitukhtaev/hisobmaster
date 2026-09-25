<?php

declare(strict_types=1);

namespace App\Core;

class Auth
{
    private const MAX_LOGIN_ATTEMPTS = 5;
    private const LOCKOUT_MINUTES = 15;

    private static ?array $user = null;
    private static bool $loaded = false;
    private static bool $lockedOut = false;

    public static function attempt(string $login, string $password): bool
    {
        $user = self::verifyCredentials($login, $password);
        if ($user === null) {
            return false;
        }

        session_regenerate_id(true);
        $_SESSION['user_id'] = (int) $user['id'];
        self::$user = $user;
        self::$loaded = true;

        return true;
    }

    /**
     * The active user with this login and password (whose shop is active),
     * or null — with the same failed-attempt counting and lockout as the
     * login form, but without touching the session. Used by attempt() and by
     * the desktop app's activation, which logs in over the API.
     */
    public static function verifyCredentials(string $login, string $password): ?array
    {
        self::$lockedOut = false;

        $pdo = Database::connect();
        $stmt = $pdo->prepare('SELECT * FROM users WHERE login = ? AND status = ?');
        $stmt->execute([$login, 'active']);
        $user = $stmt->fetch();

        // Lockout must be checked before touching the password, and must behave the same
        // whether the login exists or not, so a locked account can't be told apart from a
        // wrong login/password from the outside — the only extra bit revealed at this stage
        // is a distinct locked-out message shown to the user submitting the form.
        if ($user !== false && self::isCurrentlyLocked($user)) {
            self::$lockedOut = true;
            return null;
        }

        if (!$user || !password_verify($password, $user['password_hash'])) {
            if ($user !== false) {
                self::registerFailedAttempt((int) $user['id'], (int) $user['failed_login_attempts']);
            }
            return null;
        }

        if (!self::isShopActive($user['shop_id'])) {
            return null;
        }

        self::resetFailedAttempts((int) $user['id']);

        return $user;
    }

    /**
     * True when the last attempt() call failed specifically because the account is locked out.
     */
    public static function wasLockedOut(): bool
    {
        return self::$lockedOut;
    }

    private static function isCurrentlyLocked(array $user): bool
    {
        if (empty($user['locked_until'])) {
            return false;
        }

        // Compare against the database's own clock (SQLite datetime('now') is UTC) rather than
        // PHP's date(), so the check stays correct regardless of PHP's configured timezone.
        $stmt = Database::connect()->query("SELECT datetime('now') AS now");
        $now = $stmt->fetchColumn();

        return $user['locked_until'] > $now;
    }

    private static function registerFailedAttempt(int $userId, int $currentAttempts): void
    {
        $pdo = Database::connect();
        $attempts = $currentAttempts + 1;

        if ($attempts >= self::MAX_LOGIN_ATTEMPTS) {
            $pdo->prepare(
                "UPDATE users SET failed_login_attempts = 0, locked_until = datetime('now', '+" . self::LOCKOUT_MINUTES . " minutes')
                 WHERE id = ?"
            )->execute([$userId]);
            return;
        }

        $pdo->prepare('UPDATE users SET failed_login_attempts = ? WHERE id = ?')->execute([$attempts, $userId]);
    }

    private static function resetFailedAttempts(int $userId): void
    {
        $pdo = Database::connect();
        $pdo->prepare('UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?')
            ->execute([$userId]);
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

        if ($user['role'] === 'owner') {
            return true;
        }

        $permissions = json_decode($user['permissions_json'] ?? '[]', true) ?: [];
        return in_array($permission, $permissions, true);
    }
}

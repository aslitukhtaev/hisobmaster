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

        self::signIn($user);

        return true;
    }

    /** Starts a signed-in session for an already verified, active user. */
    private static function signIn(array $user): void
    {
        session_regenerate_id(true);
        $_SESSION['user_id'] = (int) $user['id'];
        $_SESSION['auth_at'] = microtime(true);
        unset($_SESSION['remember_selector'], $_SESSION['telegram_user_id']);
        self::$user = $user;
        self::$loaded = true;
    }

    /**
     * A visit without a session, from a device that was remembered
     * ("Meni eslab qol"): signs its user in again. Called once per request,
     * before anything is sent (bootstrap.php).
     */
    public static function restoreRemembered(): void
    {
        if (session_status() !== PHP_SESSION_ACTIVE || !empty($_SESSION['user_id'])) {
            return;
        }

        $token = RememberMe::fromCookie();
        if ($token === null) {
            return;
        }

        $user = self::activeUser($token['user_id']);
        if ($user === null) {
            return;
        }

        self::signIn($user);
        $_SESSION['remember_selector'] = $token['selector'];
    }

    /**
     * Inside the Telegram WebApp: signs in the user this Telegram account is
     * linked to (App\Core\TelegramAuth). False when it isn't linked, or its
     * user can't sign in any more.
     */
    public static function attemptTelegram(array $telegramUser): bool
    {
        $userId = TelegramAuth::linkedUserId((int) $telegramUser['id']);
        $user = $userId !== null ? self::activeUser($userId) : null;
        if ($user === null) {
            return false;
        }

        self::signIn($user);
        $_SESSION['telegram_user_id'] = (int) $telegramUser['id'];

        return true;
    }

    /**
     * Ends the user's sessions and remembered devices everywhere — "sign out
     * everywhere", a password change or reset. $keepCurrent: except this
     * device (its own session, remembered token and Telegram link).
     */
    public static function signOutEverywhere(int $userId, bool $keepCurrent): void
    {
        $current = $keepCurrent && self::id() === $userId;
        RememberMe::removeAll($userId, $current ? ($_SESSION['remember_selector'] ?? null) : null);
        TelegramAuth::removeAll($userId, $current && isset($_SESSION['telegram_user_id']) ? (int) $_SESSION['telegram_user_id'] : null);
        Database::connect()->prepare("UPDATE users SET sessions_revoked_at = strftime('%Y-%m-%d %H:%M:%f', 'now') WHERE id = ?")->execute([$userId]);

        if ($current) {
            $_SESSION['auth_at'] = microtime(true);
        }
    }

    /** "2026-09-30 09:17:03.512" (UTC) as a Unix timestamp with fractions. */
    private static function utcMoment(?string $utc): ?float
    {
        if ($utc === null || $utc === '') {
            return null;
        }
        try {
            return (float) (new \DateTimeImmutable($utc, new \DateTimeZone('UTC')))->format('U.u');
        } catch (\Exception) {
            return null;
        }
    }

    private static function activeUser(int $userId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM users WHERE id = ? AND status = ?');
        $stmt->execute([$userId, 'active']);
        $user = $stmt->fetch() ?: null;

        return $user && self::isShopActive($user['shop_id'] !== null ? (int) $user['shop_id'] : null) ? $user : null;
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

    /** Signing out on this device — it is forgotten too (remembered token, Telegram link). */
    public static function logout(): void
    {
        RememberMe::forgetThisDevice();
        if (isset($_SESSION['telegram_user_id'])) {
            TelegramAuth::unlink((int) $_SESSION['telegram_user_id']);
        }

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

        $user = self::activeUser((int) $_SESSION['user_id']);

        // Signed out from elsewhere: "sign out everywhere" / a new password
        // since this session began, or this device removed in the profile.
        if ($user !== null) {
            // Both to the millisecond: a device that signed in a moment
            // before "sign out everywhere" is signed out too.
            $revokedAt = self::utcMoment($user['sessions_revoked_at'] ?? null);
            $selector = $_SESSION['remember_selector'] ?? null;
            $telegramId = $_SESSION['telegram_user_id'] ?? null;
            if (($revokedAt !== null && (float) ($_SESSION['auth_at'] ?? 0) < $revokedAt)
                || (is_string($selector) && !RememberMe::exists($selector, (int) $user['id']))
                || ($telegramId !== null && !TelegramAuth::isLinked((int) $telegramId, (int) $user['id']))) {
                unset($_SESSION['user_id'], $_SESSION['remember_selector'], $_SESSION['telegram_user_id']);
                $user = null;
            }
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

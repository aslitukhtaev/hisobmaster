<?php

declare(strict_types=1);

namespace App\Core;

/**
 * "Meni eslab qol": the password is asked once per device — on the
 * website, in the Telegram WebApp and in the desktop app.
 *
 * The cookie holds "<selector>:<validator>"; the database keeps the
 * selector and a sha256 of the validator only, so a leaked database signs
 * nobody in. A token lives DAYS from its last use (each day of use pushes
 * it forward) and ends when its owner signs out on that device, removes the
 * device in the profile, signs out everywhere or has the password changed.
 */
final class RememberMe
{
    public const COOKIE = 'kassiron_remember';
    public const DAYS = 365;

    public static function issue(int $userId): void
    {
        $selector = bin2hex(random_bytes(12));
        $validator = bin2hex(random_bytes(32));
        $expires = time() + self::DAYS * 86400;

        Database::connect()->prepare(
            'INSERT INTO remember_tokens (user_id, selector, validator_hash, user_agent, expires_at) VALUES (?, ?, ?, ?, ?)'
        )->execute([
            $userId,
            $selector,
            hash('sha256', $validator),
            mb_substr((string) ($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 300),
            gmdate('Y-m-d H:i:s', $expires),
        ]);

        self::setCookie($selector . ':' . $validator, $expires);
        $_SESSION['remember_selector'] = $selector;
    }

    /**
     * The token of this device's cookie, if it is valid:
     * ['user_id' => int, 'selector' => string]. An invalid cookie is removed.
     */
    public static function fromCookie(): ?array
    {
        $cookie = (string) ($_COOKIE[self::COOKIE] ?? '');
        if ($cookie === '') {
            return null;
        }
        if (!preg_match('/^([a-f0-9]{24}):([a-f0-9]{64})$/', $cookie, $m)) {
            self::clearCookie();
            return null;
        }

        $pdo = Database::connect();
        $stmt = $pdo->prepare('SELECT * FROM remember_tokens WHERE selector = ?');
        $stmt->execute([$m[1]]);
        $token = $stmt->fetch();

        $now = gmdate('Y-m-d H:i:s');
        if (!$token || !hash_equals((string) $token['validator_hash'], hash('sha256', $m[2])) || $token['expires_at'] < $now) {
            self::clearCookie();
            return null;
        }

        // Sliding expiry, written at most once a day.
        if ($token['last_used_at'] < gmdate('Y-m-d H:i:s', time() - 86400)) {
            $expires = time() + self::DAYS * 86400;
            $pdo->prepare("UPDATE remember_tokens SET last_used_at = datetime('now'), expires_at = ? WHERE id = ?")
                ->execute([gmdate('Y-m-d H:i:s', $expires), $token['id']]);
            self::setCookie($cookie, $expires);
        }

        return ['user_id' => (int) $token['user_id'], 'selector' => (string) $token['selector']];
    }

    /** Signing out on this device: its token and cookie go. */
    public static function forgetThisDevice(): void
    {
        $selector = $_SESSION['remember_selector'] ?? null;
        if (is_string($selector) && $selector !== '') {
            Database::connect()->prepare('DELETE FROM remember_tokens WHERE selector = ?')->execute([$selector]);
        }
        self::clearCookie();
    }

    public static function exists(string $selector, int $userId): bool
    {
        $stmt = Database::connect()->prepare('SELECT 1 FROM remember_tokens WHERE selector = ? AND user_id = ?');
        $stmt->execute([$selector, $userId]);

        return $stmt->fetchColumn() !== false;
    }

    /** @return list<array> the user's remembered devices, most recently used first */
    public static function forUser(int $userId): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT id, selector, user_agent, created_at, last_used_at FROM remember_tokens
             WHERE user_id = ? AND expires_at >= datetime('now') ORDER BY last_used_at DESC, id DESC"
        );
        $stmt->execute([$userId]);

        return $stmt->fetchAll();
    }

    public static function remove(int $id, int $userId): void
    {
        Database::connect()->prepare('DELETE FROM remember_tokens WHERE id = ? AND user_id = ?')->execute([$id, $userId]);
    }

    /** Every device of the user but $keepSelector's. */
    public static function removeAll(int $userId, ?string $keepSelector = null): void
    {
        Database::connect()->prepare('DELETE FROM remember_tokens WHERE user_id = ? AND selector IS NOT ?')
            ->execute([$userId, $keepSelector]);
    }

    private static function setCookie(string $value, int $expires): void
    {
        setcookie(self::COOKIE, $value, [
            'expires' => $expires,
            'path' => '/',
            'secure' => request_is_https(),
            'httponly' => true,
            'samesite' => 'Lax',
        ]);
        $_COOKIE[self::COOKIE] = $value;
    }

    private static function clearCookie(): void
    {
        setcookie(self::COOKIE, '', [
            'expires' => time() - 3600,
            'path' => '/',
            'secure' => request_is_https(),
            'httponly' => true,
            'samesite' => 'Lax',
        ]);
        unset($_COOKIE[self::COOKIE]);
    }
}

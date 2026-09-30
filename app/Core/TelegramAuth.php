<?php

declare(strict_types=1);

namespace App\Core;

/**
 * Signing in inside the Telegram WebApp without the password. The first
 * time, the user signs in with the password there, and their Telegram
 * account is linked to that user (telegram_links). After that, the WebApp's
 * initData — signed by Telegram with the bot's token, so it can't be made
 * up — names the Telegram account, and the linked user is signed in.
 * Signing out inside Telegram removes the link.
 *
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 */
final class TelegramAuth
{
    /** How old an initData may be (it is signed when the WebApp opens). */
    private const MAX_AGE_SECONDS = 86400;

    public static function enabled(): bool
    {
        return (string) env('TELEGRAM_BOT_TOKEN', '') !== '';
    }

    /**
     * The Telegram user of a genuine, recent initData ({id, first_name, ...}),
     * or null.
     */
    public static function verify(string $initData, ?int $now = null): ?array
    {
        $token = (string) env('TELEGRAM_BOT_TOKEN', '');
        if ($token === '' || $initData === '' || strlen($initData) > 4096) {
            return null;
        }

        parse_str($initData, $fields);
        $hash = $fields['hash'] ?? null;
        if (!is_string($hash) || $hash === '') {
            return null;
        }
        unset($fields['hash']);

        $lines = [];
        foreach ($fields as $key => $value) {
            if (!is_string($value)) {
                return null;
            }
            $lines[] = $key . '=' . $value;
        }
        sort($lines, SORT_STRING);

        $secret = hash_hmac('sha256', $token, 'WebAppData', true);
        if (!hash_equals(hash_hmac('sha256', implode("\n", $lines), $secret), strtolower($hash))) {
            return null;
        }

        $authDate = (int) ($fields['auth_date'] ?? 0);
        if ($authDate <= 0 || abs(($now ?? time()) - $authDate) > self::MAX_AGE_SECONDS) {
            return null;
        }

        $user = json_decode((string) ($fields['user'] ?? ''), true);

        return is_array($user) && isset($user['id']) && is_int($user['id']) ? $user : null;
    }

    public static function link(array $telegramUser, int $userId): void
    {
        $name = trim(($telegramUser['first_name'] ?? '') . ' ' . ($telegramUser['last_name'] ?? ''));
        if (!empty($telegramUser['username'])) {
            $name .= ' (@' . $telegramUser['username'] . ')';
        }

        Database::connect()->prepare(
            "INSERT INTO telegram_links (telegram_user_id, user_id, telegram_name) VALUES (?, ?, ?)
             ON CONFLICT(telegram_user_id) DO UPDATE SET user_id = excluded.user_id, telegram_name = excluded.telegram_name,
                 created_at = datetime('now'), last_used_at = datetime('now')"
        )->execute([(int) $telegramUser['id'], $userId, mb_substr(trim($name), 0, 120)]);
    }

    /** The user linked to this Telegram account, or null. */
    public static function linkedUserId(int $telegramUserId): ?int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare('SELECT user_id FROM telegram_links WHERE telegram_user_id = ?');
        $stmt->execute([$telegramUserId]);
        $userId = $stmt->fetchColumn();
        if ($userId === false) {
            return null;
        }
        $pdo->prepare("UPDATE telegram_links SET last_used_at = datetime('now') WHERE telegram_user_id = ?")->execute([$telegramUserId]);

        return (int) $userId;
    }

    public static function isLinked(int $telegramUserId, int $userId): bool
    {
        $stmt = Database::connect()->prepare('SELECT 1 FROM telegram_links WHERE telegram_user_id = ? AND user_id = ?');
        $stmt->execute([$telegramUserId, $userId]);

        return $stmt->fetchColumn() !== false;
    }

    /** @return list<array> */
    public static function forUser(int $userId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT id, telegram_user_id, telegram_name, created_at, last_used_at FROM telegram_links WHERE user_id = ? ORDER BY last_used_at DESC'
        );
        $stmt->execute([$userId]);

        return $stmt->fetchAll();
    }

    public static function unlink(int $telegramUserId): void
    {
        Database::connect()->prepare('DELETE FROM telegram_links WHERE telegram_user_id = ?')->execute([$telegramUserId]);
    }

    public static function remove(int $id, int $userId): void
    {
        Database::connect()->prepare('DELETE FROM telegram_links WHERE id = ? AND user_id = ?')->execute([$id, $userId]);
    }

    /** Every Telegram account of the user but $keepTelegramUserId. */
    public static function removeAll(int $userId, ?int $keepTelegramUserId = null): void
    {
        Database::connect()->prepare('DELETE FROM telegram_links WHERE user_id = ? AND telegram_user_id IS NOT ?')
            ->execute([$userId, $keepTelegramUserId]);
    }
}

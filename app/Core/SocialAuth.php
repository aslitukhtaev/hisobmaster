<?php

declare(strict_types=1);

namespace App\Core;

use RuntimeException;

/**
 * Signing in (and signing up) with Google or Telegram — both as plain
 * redirects, with no third-party script on our pages:
 *
 * - Google: OAuth 2.0 / OpenID Connect authorization code. The id_token comes
 *   back from Google's token endpoint over TLS in exchange for the code and
 *   our client secret, so (per OpenID Connect Core 3.1.3.7) its claims are
 *   trusted without checking the signature; aud/iss/exp are still checked.
 *   Needs GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET, and
 *   <APP_URL>/auth/google/callback as an authorised redirect URI.
 * - Telegram: the Login Widget's own page (oauth.telegram.org/auth) sends the
 *   user back with #tgAuthResult — the same fields the widget gives, signed
 *   with sha256(bot token). Needs TELEGRAM_BOT_TOKEN and the site's domain
 *   set for the bot (BotFather → /setdomain).
 *
 * https://developers.google.com/identity/openid-connect/openid-connect
 * https://core.telegram.org/widgets/login#checking-authorization
 */
final class SocialAuth
{
    private const TELEGRAM_MAX_AGE_SECONDS = 86400;

    public static function googleEnabled(): bool
    {
        return (string) env('GOOGLE_CLIENT_ID', '') !== '' && (string) env('GOOGLE_CLIENT_SECRET', '') !== ''
            && self::appUrl() !== '';
    }

    public static function telegramEnabled(): bool
    {
        return self::telegramBotId() !== null && self::appUrl() !== '';
    }

    public static function appUrl(): string
    {
        return rtrim((string) env('APP_URL', ''), '/');
    }

    public static function googleRedirectUri(): string
    {
        return self::appUrl() . '/auth/google/callback';
    }

    public static function googleAuthUrl(string $state): string
    {
        return 'https://accounts.google.com/o/oauth2/v2/auth?' . http_build_query([
            'client_id' => (string) env('GOOGLE_CLIENT_ID', ''),
            'redirect_uri' => self::googleRedirectUri(),
            'response_type' => 'code',
            'scope' => 'openid email profile',
            'state' => $state,
            'prompt' => 'select_account',
        ]);
    }

    /**
     * The Google account behind an authorization code:
     * {subject, email, name}. Throws when Google refuses it.
     */
    public static function googleExchange(string $code): array
    {
        if ($code === '' || strlen($code) > 2048) {
            throw new RuntimeException('google_failed');
        }

        $ch = curl_init('https://oauth2.googleapis.com/token');
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => http_build_query([
                'code' => $code,
                'client_id' => (string) env('GOOGLE_CLIENT_ID', ''),
                'client_secret' => (string) env('GOOGLE_CLIENT_SECRET', ''),
                'redirect_uri' => self::googleRedirectUri(),
                'grant_type' => 'authorization_code',
            ]),
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 15,
            CURLOPT_HTTPHEADER => ['Accept: application/json'],
        ]);
        $raw = curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        $response = is_string($raw) ? json_decode($raw, true) : null;
        $idToken = is_array($response) ? (string) ($response['id_token'] ?? '') : '';
        if ($status !== 200 || $idToken === '') {
            throw new RuntimeException('google_failed');
        }

        $parts = explode('.', $idToken);
        $claims = count($parts) === 3 ? json_decode(self::base64urlDecode($parts[1]), true) : null;
        if (!is_array($claims)
            || ($claims['aud'] ?? null) !== (string) env('GOOGLE_CLIENT_ID', '')
            || !in_array($claims['iss'] ?? null, ['https://accounts.google.com', 'accounts.google.com'], true)
            || (int) ($claims['exp'] ?? 0) < time() - 60
            || !is_string($claims['sub'] ?? null) || $claims['sub'] === '') {
            throw new RuntimeException('google_failed');
        }

        $verified = ($claims['email_verified'] ?? false) === true || ($claims['email_verified'] ?? '') === 'true';

        return [
            'provider' => 'google',
            'subject' => $claims['sub'],
            'email' => $verified ? mb_substr((string) ($claims['email'] ?? ''), 0, 190) : null,
            'name' => mb_substr(trim((string) ($claims['name'] ?? '')), 0, 120),
        ];
    }

    /** The numeric bot id: the part of the bot token before ":". */
    public static function telegramBotId(): ?string
    {
        $token = (string) env('TELEGRAM_BOT_TOKEN', '');
        $id = strstr($token, ':', true);

        return is_string($id) && ctype_digit($id) ? $id : null;
    }

    public static function telegramAuthUrl(): string
    {
        return 'https://oauth.telegram.org/auth?' . http_build_query([
            'bot_id' => (string) self::telegramBotId(),
            'origin' => self::appUrl(),
            'request_access' => 'write',
            'return_to' => self::appUrl() . '/auth/telegram/callback',
        ]);
    }

    /**
     * The Telegram account of genuine, recent Login Widget data
     * ({id, first_name, last_name?, username?, auth_date, hash}), or null.
     */
    public static function telegramVerify(array $data, ?int $now = null): ?array
    {
        $token = (string) env('TELEGRAM_BOT_TOKEN', '');
        $hash = $data['hash'] ?? null;
        if ($token === '' || !is_string($hash) || $hash === '') {
            return null;
        }
        unset($data['hash']);

        $lines = [];
        foreach ($data as $key => $value) {
            if (is_bool($value) || is_array($value) || $value === null) {
                return null;
            }
            $lines[] = $key . '=' . $value;
        }
        sort($lines, SORT_STRING);

        $expected = hash_hmac('sha256', implode("\n", $lines), hash('sha256', $token, true));
        if (!hash_equals($expected, strtolower($hash))) {
            return null;
        }

        $authDate = (int) ($data['auth_date'] ?? 0);
        if ($authDate <= 0 || abs(($now ?? time()) - $authDate) > self::TELEGRAM_MAX_AGE_SECONDS) {
            return null;
        }

        $id = $data['id'] ?? null;
        if (!is_int($id) && !(is_string($id) && ctype_digit($id))) {
            return null;
        }

        return self::telegramIdentity($data);
    }

    /** {provider, subject, email, name} of a verified Telegram user (widget or WebApp). */
    public static function telegramIdentity(array $user): array
    {
        $name = trim(($user['first_name'] ?? '') . ' ' . ($user['last_name'] ?? ''));

        return [
            'provider' => 'telegram',
            'subject' => (string) $user['id'],
            'email' => null,
            'name' => mb_substr($name, 0, 120),
            'username' => isset($user['username']) ? mb_substr((string) $user['username'], 0, 64) : null,
        ];
    }

    /** The user this provider account signs in, or null. */
    public static function userIdFor(string $provider, string $subject): ?int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare('SELECT user_id FROM auth_identities WHERE provider = ? AND subject = ?');
        $stmt->execute([$provider, $subject]);
        $userId = $stmt->fetchColumn();
        if ($userId === false) {
            return null;
        }
        $pdo->prepare("UPDATE auth_identities SET last_used_at = datetime('now') WHERE provider = ? AND subject = ?")
            ->execute([$provider, $subject]);

        return (int) $userId;
    }

    public static function link(array $identity, int $userId): void
    {
        $label = $identity['email'] ?? null;
        if ($label === null && !empty($identity['username'])) {
            $label = '@' . $identity['username'];
        }

        Database::connect()->prepare(
            "INSERT INTO auth_identities (provider, subject, user_id, email, display_name) VALUES (?, ?, ?, ?, ?)
             ON CONFLICT(provider, subject) DO UPDATE SET user_id = excluded.user_id, email = excluded.email,
                 display_name = excluded.display_name, last_used_at = datetime('now')"
        )->execute([$identity['provider'], $identity['subject'], $userId, $label, $identity['name'] ?: null]);
    }

    /** @return list<array> */
    public static function forUser(int $userId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT id, provider, email, display_name, created_at, last_used_at FROM auth_identities WHERE user_id = ? ORDER BY created_at'
        );
        $stmt->execute([$userId]);

        return $stmt->fetchAll();
    }

    public static function remove(int $id, int $userId): void
    {
        Database::connect()->prepare('DELETE FROM auth_identities WHERE id = ? AND user_id = ?')->execute([$id, $userId]);
    }

    private static function base64urlDecode(string $value): string
    {
        return (string) base64_decode(strtr($value, '-_', '+/') . str_repeat('=', (4 - strlen($value) % 4) % 4));
    }
}

<?php

declare(strict_types=1);

namespace App\Sync;

use App\Core\Database;
use RuntimeException;

/**
 * The desktop app's permission to work: a small JSON document naming the
 * shop and the one computer it was issued to, signed with the server's
 * Ed25519 key. The app checks the signature with the public key built into
 * it, so it works offline, can't be forged, and a copy moved to another
 * computer doesn't match that computer's fingerprint.
 *
 * License model: one shop, one lifetime license, any number of computers.
 * `valid_until` is not a payment deadline — every sync issues a fresh token
 * that runs `offline_days` (default 7) from now, so a computer that was
 * switched off (lost, sold) or whose shop was blocked stops within that
 * many days even if it never comes back online.
 *
 * Token format: base64url(payload JSON) "." base64url(signature of that
 * first part).
 */
final class License
{
    private const KEY_NAME = 'license_ed25519_secret';

    /** @param array $shop shops row; @param array $device devices row */
    public static function issue(array $shop, array $device, ?int $now = null): string
    {
        $now ??= time();
        $offlineDays = max(1, (int) ($shop['offline_days'] ?? 7));

        $payload = [
            'v' => 1,
            'license' => 'lifetime',
            'shop_id' => (int) $shop['id'],
            'shop_name' => (string) $shop['name'],
            'device_uuid' => (string) $device['uuid'],
            'device_code' => (string) $device['code'],
            'fingerprint' => (string) $device['fingerprint'],
            'issued_at' => $now,
            'valid_until' => $now + $offlineDays * 86400,
        ];

        $body = self::base64url(json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR));
        $signature = sodium_crypto_sign_detached($body, self::secretKey());

        return $body . '.' . self::base64url($signature);
    }

    /**
     * The payload of a token with a valid signature, or null. Only checks
     * the signature — whether it's still in date and for this computer is
     * the caller's decision.
     */
    public static function verify(string $token, ?string $publicKeyBase64 = null): ?array
    {
        $parts = explode('.', $token);
        if (count($parts) !== 2) {
            return null;
        }
        [$body, $signature] = $parts;

        $publicKey = $publicKeyBase64 !== null ? base64_decode($publicKeyBase64, true) : self::publicKeyRaw();
        $signatureRaw = self::base64urlDecode($signature);
        if ($publicKey === false || strlen($publicKey) !== SODIUM_CRYPTO_SIGN_PUBLICKEYBYTES
            || $signatureRaw === null || strlen($signatureRaw) !== SODIUM_CRYPTO_SIGN_BYTES) {
            return null;
        }

        if (!sodium_crypto_sign_verify_detached($signatureRaw, $body, $publicKey)) {
            return null;
        }

        $json = self::base64urlDecode($body);
        $payload = $json !== null ? json_decode($json, true) : null;

        return is_array($payload) ? $payload : null;
    }

    /** Base64 public key — what the desktop app is built with. */
    public static function publicKey(): string
    {
        return base64_encode(self::publicKeyRaw());
    }

    private static function publicKeyRaw(): string
    {
        return sodium_crypto_sign_publickey_from_secretkey(self::secretKey());
    }

    /**
     * LICENSE_SECRET_KEY from .env when set (base64 of a 64-byte Ed25519
     * secret key); otherwise a key generated on first use and kept in the
     * server_keys table, so it survives deploys and is part of every
     * database backup. Losing it would invalidate every issued license.
     */
    private static function secretKey(): string
    {
        static $key = null;
        if ($key !== null) {
            return $key;
        }

        $fromEnv = (string) env('LICENSE_SECRET_KEY', '');
        if ($fromEnv !== '') {
            $decoded = base64_decode($fromEnv, true);
            if ($decoded === false || strlen($decoded) !== SODIUM_CRYPTO_SIGN_SECRETKEYBYTES) {
                throw new RuntimeException('LICENSE_SECRET_KEY is not a base64 Ed25519 secret key');
            }

            return $key = $decoded;
        }

        $pdo = Database::connect();
        $stmt = $pdo->prepare('SELECT value FROM server_keys WHERE name = ?');
        $stmt->execute([self::KEY_NAME]);
        $stored = $stmt->fetchColumn();

        if ($stored === false) {
            $generated = base64_encode(sodium_crypto_sign_secretkey(sodium_crypto_sign_keypair()));
            // OR IGNORE + re-read: two first requests racing both end up
            // with whichever key was written first.
            $pdo->prepare('INSERT OR IGNORE INTO server_keys (name, value) VALUES (?, ?)')
                ->execute([self::KEY_NAME, $generated]);
            $stmt->execute([self::KEY_NAME]);
            $stored = $stmt->fetchColumn();
        }

        return $key = (string) base64_decode((string) $stored, true);
    }

    public static function base64url(string $raw): string
    {
        return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
    }

    public static function base64urlDecode(string $encoded): ?string
    {
        $decoded = base64_decode(strtr($encoded, '-_', '+/'), true);

        return $decoded === false ? null : $decoded;
    }
}

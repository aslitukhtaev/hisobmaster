<?php

declare(strict_types=1);

namespace App\Sync;

use App\Core\Database;
use RuntimeException;

/**
 * The desktop app's permission to work: a small JSON document naming the
 * shop and the one computer it was issued to, signed with the server's
 * private key (ECDSA P-256 / SHA-256 via OpenSSL — available in every PHP
 * build, unlike the sodium extension). The app checks the signature with the
 * public key built into it, so it works offline, can't be forged, and a copy
 * moved to another computer doesn't match that computer's fingerprint.
 *
 * License model: one shop, one lifetime license, any number of computers.
 * `valid_until` is not a payment deadline — every sync issues a fresh token
 * that runs `offline_days` (default 7) from now, so a computer that was
 * switched off (lost, sold) or whose shop was blocked stops within that
 * many days even if it never comes back online.
 *
 * Token format: base64url(payload JSON) "." base64url(DER signature of that
 * first part).
 */
final class License
{
    public const ALGORITHM = 'ecdsa-p256-sha256';

    private const KEY_NAME = 'license_ecdsa_p256_private';

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
        if (!openssl_sign($body, $signature, self::privateKey(), OPENSSL_ALGO_SHA256)) {
            throw new RuntimeException('license_sign_failed');
        }

        return $body . '.' . self::base64url($signature);
    }

    /** Base64 signature of arbitrary bytes (e.g. a desktop code package). */
    public static function sign(string $bytes): string
    {
        if (!openssl_sign($bytes, $signature, self::privateKey(), OPENSSL_ALGO_SHA256)) {
            throw new RuntimeException('license_sign_failed');
        }

        return base64_encode($signature);
    }

    /**
     * The payload of a token with a valid signature, or null. Only checks
     * the signature — whether it's still in date and for this computer is
     * the caller's decision.
     */
    public static function verify(string $token, ?string $publicKeyPem = null): ?array
    {
        $parts = explode('.', $token);
        if (count($parts) !== 2) {
            return null;
        }
        [$body, $signature] = $parts;

        $signatureRaw = self::base64urlDecode($signature);
        $publicKey = openssl_pkey_get_public($publicKeyPem ?? self::publicKey());
        if ($signatureRaw === null || $publicKey === false) {
            return null;
        }

        if (openssl_verify($body, $signatureRaw, $publicKey, OPENSSL_ALGO_SHA256) !== 1) {
            return null;
        }

        $json = self::base64urlDecode($body);
        $payload = $json !== null ? json_decode($json, true) : null;

        return is_array($payload) ? $payload : null;
    }

    /** PEM public key — what the desktop app is built with (not a secret). */
    public static function publicKey(): string
    {
        $details = openssl_pkey_get_details(self::privateKey());

        return (string) $details['key'];
    }

    /**
     * LICENSE_PRIVATE_KEY_B64 from .env when set (base64 of a PEM EC P-256
     * private key); otherwise a key generated on first use and kept in the
     * server_keys table, so it survives deploys and is part of every
     * database backup. Losing it would invalidate every issued license.
     */
    private static function privateKey(): \OpenSSLAsymmetricKey
    {
        static $key = null;
        if ($key !== null) {
            return $key;
        }

        $pem = (string) base64_decode((string) env('LICENSE_PRIVATE_KEY_B64', ''), true);

        if ($pem === '') {
            $pdo = Database::connect();
            $stmt = $pdo->prepare('SELECT value FROM server_keys WHERE name = ?');
            $stmt->execute([self::KEY_NAME]);
            $stored = $stmt->fetchColumn();

            if ($stored === false) {
                $generated = openssl_pkey_new(['private_key_type' => OPENSSL_KEYTYPE_EC, 'curve_name' => 'prime256v1']);
                if ($generated === false || !openssl_pkey_export($generated, $generatedPem)) {
                    throw new RuntimeException('license_key_generation_failed');
                }
                // OR IGNORE + re-read: two first requests racing both end up
                // with whichever key was written first.
                $pdo->prepare('INSERT OR IGNORE INTO server_keys (name, value) VALUES (?, ?)')
                    ->execute([self::KEY_NAME, $generatedPem]);
                $stmt->execute([self::KEY_NAME]);
                $stored = $stmt->fetchColumn();
            }

            $pem = (string) $stored;
        }

        $key = openssl_pkey_get_private($pem);
        if ($key === false) {
            throw new RuntimeException('license_key_invalid');
        }

        return $key;
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

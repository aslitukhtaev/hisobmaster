<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Request;
use App\Sync\CodePackage;
use App\Sync\DeviceService;
use App\Sync\License;
use App\Sync\SyncService;
use PDOException;
use RuntimeException;
use Throwable;

/**
 * JSON API the desktop app talks to. No session, no CSRF token: a computer
 * authenticates every request with its device secret (X-Device-Auth header,
 * see device()), except activation, which is the owner's login and password.
 *
 * Every successful response carries a fresh license token and the server
 * time (the app compares it with its own clock). Errors are
 * {"error": "<code>"} with a matching HTTP status; the app shows its own
 * translated message for each code.
 */
class ApiController
{
    private const STATUS = [
        'invalid_request' => 400,
        'login_failed' => 401,
        'login_locked' => 429,
        'owner_only' => 403,
        'unauthorized' => 401,
        'device_revoked' => 403,
        'shop_blocked' => 403,
    ];

    public function activate(Request $request): void
    {
        $this->handle(function () use ($request): array {
            $body = $this->body();
            $result = DeviceService::activate(
                (string) ($body['login'] ?? ''),
                (string) ($body['password'] ?? ''),
                strtolower((string) ($body['device_uuid'] ?? '')),
                strtolower((string) ($body['fingerprint'] ?? '')),
                (string) ($body['name'] ?? ''),
                (string) ($body['app_version'] ?? '')
            );

            $auth = DeviceService::authenticate('Device ' . $result['device']['uuid'] . ':' . $result['secret']);

            return [
                'device' => ['uuid' => $result['device']['uuid'], 'code' => $result['device']['code']],
                'secret' => $result['secret'],
                'license' => License::issue($auth['shop'], $auth['device']),
            ] + SyncService::shopState($auth['shop']);
        });
    }

    public function push(Request $request): void
    {
        $this->handle(function () use ($request): array {
            $auth = $this->device($request);
            $changes = $this->body()['changes'] ?? null;
            if (!is_array($changes) || count($changes) > 2000) {
                throw new RuntimeException('invalid_request');
            }

            return SyncService::push($auth['device'], $changes)
                + ['license' => License::issue($auth['shop'], $auth['device'])];
        });
    }

    public function pull(Request $request): void
    {
        $this->handle(function () use ($request): array {
            $auth = $this->device($request);
            $body = $this->body();

            $cursor = isset($body['cursor']) && is_numeric($body['cursor']) ? (int) $body['cursor'] : null;
            $snap = isset($body['snap']) && is_array($body['snap']) ? $body['snap'] : null;

            SyncService::pruneJournalQuietly();

            return SyncService::pull($auth['device'], $cursor, $snap)
                + SyncService::shopState($auth['shop'])
                + ['license' => License::issue($auth['shop'], $auth['device'])];
        });
    }

    /**
     * Is there newer code for this computer? It sends the version of the code
     * it runs and the date of its newest changelog entry; the answer carries
     * the package's size, hash, signature and what's new since then.
     */
    public function updateCheck(Request $request): void
    {
        $this->handle(function () use ($request): array {
            $this->device($request);
            $body = $this->body();
            $package = CodePackage::current();
            $theirs = (string) ($body['version'] ?? '');
            $lastDate = isset($body['changelog_date']) && preg_match('/^\d{4}-\d{2}-\d{2}$/', (string) $body['changelog_date'])
                ? (string) $body['changelog_date']
                : null;

            return [
                'available' => $theirs !== $package['version'],
                'version' => $package['version'],
                'created_at' => $package['created_at'],
                'size' => $package['size'],
                'sha256' => $package['sha256'],
                'signature' => $package['signature'],
                'notes' => CodePackage::notesSince($lastDate),
            ];
        });
    }

    /** The code package itself (the version updateCheck announced). */
    public function package(Request $request): void
    {
        try {
            $this->device($request);
            $package = CodePackage::current();
            if ((string) ($this->body()['version'] ?? '') !== $package['version']) {
                $this->json(409, ['error' => 'version_changed']);
            }
        } catch (RuntimeException $e) {
            $this->json(self::STATUS[$e->getMessage()] ?? 500, ['error' => isset(self::STATUS[$e->getMessage()]) ? $e->getMessage() : 'server_error']);
        }

        http_response_code(200);
        header('Content-Type: application/octet-stream');
        header('Content-Length: ' . $package['size']);
        header('X-Code-Version: ' . $package['version']);
        header('Cache-Control: no-store');
        readfile($package['file']);
        exit;
    }

    /** Built into the desktop app at build time (not a secret). */
    public function publicKey(Request $request): void
    {
        $this->handle(static fn (): array => ['public_key' => License::publicKey(), 'algorithm' => License::ALGORITHM]);
    }

    /**
     * The app sends its credentials in X-Device-Auth ("Device <uuid>:<secret>");
     * the standard Authorization header is accepted too, but some Apache/CGI
     * setups don't pass it through to PHP, so the app doesn't rely on it.
     *
     * @return array{device: array, shop: array}
     */
    private function device(Request $request): array
    {
        return DeviceService::authenticate(
            (string) ($_SERVER['HTTP_X_DEVICE_AUTH'] ?? $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? ''),
            (string) ($_SERVER['HTTP_X_APP_VERSION'] ?? '')
        );
    }

    private function body(): array
    {
        $decoded = json_decode((string) file_get_contents('php://input'), true);

        return is_array($decoded) ? $decoded : [];
    }

    private function handle(callable $action): void
    {
        try {
            $this->json(200, $action() + ['server_time' => time()]);
        } catch (PDOException $e) {
            log_exception($e);
            $this->json(500, ['error' => 'server_error']);
        } catch (RuntimeException $e) {
            $code = $e->getMessage();
            if (!isset(self::STATUS[$code])) {
                log_exception($e);
                $this->json(500, ['error' => 'server_error']);
            }
            $this->json(self::STATUS[$code], ['error' => $code]);
        } catch (Throwable $e) {
            log_exception($e);
            $this->json(500, ['error' => 'server_error']);
        }
    }

    private function json(int $status, array $data): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }
}

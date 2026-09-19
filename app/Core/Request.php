<?php

declare(strict_types=1);

namespace App\Core;

class Request
{
    public function method(): string
    {
        return $_SERVER['REQUEST_METHOD'] ?? 'GET';
    }

    public function path(): string
    {
        $uri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';
        $scriptDir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '')), '/');

        if ($scriptDir !== '' && str_starts_with($uri, $scriptDir)) {
            $uri = substr($uri, strlen($scriptDir));
        }

        $uri = '/' . ltrim($uri, '/');
        $trimmed = rtrim($uri, '/');

        return $trimmed === '' ? '/' : $trimmed;
    }

    public function input(string $key, mixed $default = null): mixed
    {
        return $_POST[$key] ?? $_GET[$key] ?? $default;
    }

    public function all(): array
    {
        return $_REQUEST;
    }

    public function only(array $keys): array
    {
        return array_intersect_key($this->all(), array_flip($keys));
    }

    /**
     * The raw $_FILES entry for a single <input type="file" name="$key">, or
     * null when nothing was uploaded under that name (including a form
     * submitted without selecting a file, where PHP still sets
     * error = UPLOAD_ERR_NO_FILE rather than omitting the key).
     */
    public function file(string $key): ?array
    {
        $file = $_FILES[$key] ?? null;
        if (!is_array($file) || ($file['error'] ?? UPLOAD_ERR_NO_FILE) === UPLOAD_ERR_NO_FILE) {
            return null;
        }

        return $file;
    }
}

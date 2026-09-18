<?php

declare(strict_types=1);

use App\Core\Auth;

function env(string $key, mixed $default = null): mixed
{
    $value = getenv($key);
    if ($value === false) {
        return $default;
    }

    return match ($value) {
        'true' => true,
        'false' => false,
        default => $value,
    };
}

function base_url(string $path = ''): string
{
    $url = rtrim((string) env('APP_URL', ''), '/');
    return $url . '/' . ltrim($path, '/');
}

function asset(string $path): string
{
    return '/assets/' . ltrim($path, '/');
}

function redirect(string $path): never
{
    header('Location: ' . $path);
    exit;
}

function e(?string $value): string
{
    return htmlspecialchars($value ?? '', ENT_QUOTES, 'UTF-8');
}

function csrf_token(): string
{
    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }

    return $_SESSION['csrf_token'];
}

function csrf_field(): string
{
    return '<input type="hidden" name="_csrf" value="' . e(csrf_token()) . '">';
}

function csrf_verify(?string $token): bool
{
    return !empty($token) && !empty($_SESSION['csrf_token']) && hash_equals($_SESSION['csrf_token'], $token);
}

function flash(string $key, ?string $message = null): ?string
{
    if ($message !== null) {
        $_SESSION['flash'][$key] = $message;
        return null;
    }

    $value = $_SESSION['flash'][$key] ?? null;
    unset($_SESSION['flash'][$key]);
    return $value;
}

function old(string $key, string $default = ''): string
{
    return $_SESSION['old'][$key] ?? $default;
}

function money(float $amount, string $currency = "so'm"): string
{
    return number_format($amount, 0, '.', ' ') . ' ' . $currency;
}

function can(string $permission): bool
{
    return Auth::can($permission);
}

function current_lang(): string
{
    return $_SESSION['lang'] ?? (string) env('APP_DEFAULT_LANG', 'uz');
}

function trans(string $key, array $params = []): string
{
    static $translations = [];

    $lang = current_lang();

    if (!isset($translations[$lang])) {
        $file = BASE_PATH . "/app/lang/{$lang}.php";
        $translations[$lang] = is_file($file) ? require $file : [];
    }

    $text = $translations[$lang][$key] ?? $key;

    foreach ($params as $paramKey => $paramValue) {
        $text = str_replace(':' . $paramKey, (string) $paramValue, $text);
    }

    return $text;
}

function t(string $key, array $params = []): string
{
    return trans($key, $params);
}

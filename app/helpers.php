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

function keep_old(array $data): void
{
    $_SESSION['old'] = $data;
}

function old(string $key, string $default = ''): string
{
    static $data = null;

    if ($data === null) {
        $data = $_SESSION['old'] ?? [];
        unset($_SESSION['old']);
    }

    return (string) ($data[$key] ?? $default);
}

/**
 * Converts a calendar day (or day range), read as Asia/Tashkent local time, into
 * the matching UTC datetime bounds [start, endExclusive) for filtering a
 * UTC-stored `created_at` column.
 *
 * `created_at` columns are all stored via SQLite's `datetime('now')`, which is
 * always UTC. PHP's default timezone is Asia/Tashkent (set in bootstrap.php),
 * so "today" or a report's "from/to" range are Tashkent calendar dates — but
 * SQLite's own `date()`/`datetime('now')` know nothing about that timezone, so
 * comparing a Tashkent-local date directly against `date(created_at)` (a UTC
 * date) mis-buckets sales made near midnight Tashkent time (UTC+5). Computing
 * the range boundary in PHP and comparing against the raw UTC `created_at`
 * value sidesteps that entirely.
 *
 * @return array{0: string, 1: string} [utc_start, utc_end_exclusive] as 'Y-m-d H:i:s'
 */
function tashkent_day_bounds_utc(string $fromYmd, ?string $toYmd = null): array
{
    $tashkent = new DateTimeZone('Asia/Tashkent');
    $utc = new DateTimeZone('UTC');

    $start = new DateTimeImmutable($fromYmd . ' 00:00:00', $tashkent);
    $end = (new DateTimeImmutable(($toYmd ?? $fromYmd) . ' 00:00:00', $tashkent))->modify('+1 day');

    return [
        $start->setTimezone($utc)->format('Y-m-d H:i:s'),
        $end->setTimezone($utc)->format('Y-m-d H:i:s'),
    ];
}

function money(float $amount, string $currency = "so'm"): string
{
    return number_format($amount, 0, '.', ' ') . ' ' . $currency;
}

/**
 * Percentage change from $previous to $current, for the reports page's
 * period-comparison indicator. Null when $previous is zero — "up/down by
 * some percent of zero" is undefined, so the caller shows a plain "new"
 * state instead of a misleading number.
 */
function percent_change(float $previous, float $current): ?float
{
    if (abs($previous) < 0.00001) {
        return null;
    }

    return ($current - $previous) / abs($previous) * 100;
}

function format_qty(float $value): string
{
    if (floor($value) == $value) {
        return number_format($value, 0, '.', ' ');
    }

    return rtrim(rtrim(number_format($value, 2, '.', ' '), '0'), '.');
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

/**
 * Writes an unexpected (non-translation-key) exception to PHP's own error
 * log — the user only ever sees a generic translated message
 * ('unexpected_error'), so this is the only record of what actually broke.
 * Temporary/diagnostic use is exactly the same call as any other use.
 */
function log_exception(\Throwable $e): void
{
    error_log(sprintf(
        '[KassirON] %s: %s in %s:%d%s',
        get_class($e),
        $e->getMessage(),
        $e->getFile(),
        $e->getLine(),
        "\n" . $e->getTraceAsString()
    ));
}

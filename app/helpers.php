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

/**
 * A UTC 'Y-m-d H:i:s' column value (anything written by SQLite's
 * datetime('now')) as a Unix timestamp. strtotime() alone would read it as
 * Asia/Tashkent (PHP's default timezone here) and be 5 hours off.
 */
function utc_timestamp(?string $utc): ?int
{
    if ($utc === null || $utc === '') {
        return null;
    }

    try {
        return (new DateTimeImmutable($utc, new DateTimeZone('UTC')))->getTimestamp();
    } catch (Exception) {
        return null;
    }
}

/**
 * A UTC column value formatted in Tashkent local time — the only way a
 * stored timestamp should ever reach the screen or an export.
 */
function local_datetime(?string $utc, string $format = 'd.m.Y H:i'): string
{
    $timestamp = utc_timestamp($utc);

    return $timestamp === null ? '' : date($format, $timestamp);
}

function money(float $amount, ?string $currency = null): string
{
    return number_format($amount, 0, '.', ' ') . ' ' . ($currency ?? t('currency_symbol'));
}

/**
 * Largest amount any money field accepts (1 trillion so'm). Well beyond a
 * real shop's figures, and far enough below 2^53 that a float still holds
 * every whole so'm exactly — 99 999 999 999 999 999 used to be accepted and
 * come back as 100 000 000 000 015 008.
 */
const MONEY_MAX = 1000000000000;

/** The Windows installer of the desktop app (latest GitHub release). */
const DESKTOP_DOWNLOAD_URL = 'https://github.com/aslitukhtaev/hisobmaster/releases/latest/download/KassirON-Setup.exe';

/** Same idea for quantities (stock, sale/purchase/refund qty). */
const QTY_MAX = 1000000000;

/**
 * Whether $raw is a usable money amount: numeric, finite, 0..MONEY_MAX
 * ($allowZero = false additionally rejects 0).
 */
function valid_money(mixed $raw, bool $allowZero = true): bool
{
    if (!is_numeric($raw)) {
        return false;
    }
    $value = (float) $raw;

    return is_finite($value) && $value <= MONEY_MAX && ($allowZero ? $value >= 0 : $value > 0);
}

/**
 * Units that are measured, not counted, so a fractional quantity makes
 * sense (1.35 kg, 0.5 l). Every other unit ("dona", "quti", "pachka", ...)
 * only ever moves in whole pieces — stock, sales, purchases and refunds are
 * all validated against this one list, and the POS/purchase/refund pages
 * get the per-product answer from here too instead of keeping their own copy.
 */
const FRACTIONAL_UNITS = ['kg', 'g', 'gr', 'gramm', 'litr', 'l', 'ml', 'metr', 'm', 'sm', 'кг', 'г', 'гр', 'л', 'литр', 'мл', 'м', 'метр', 'см'];

function unit_allows_fraction(?string $unit): bool
{
    return in_array(mb_strtolower(trim((string) $unit)), FRACTIONAL_UNITS, true);
}

function is_whole_number(float $value): bool
{
    return abs($value - round($value)) < 0.000001;
}

/**
 * Whether a quantity fits its product's unit: any positive-or-zero amount
 * for a measured unit, whole numbers only for a counted one.
 */
function qty_fits_unit(float $qty, ?string $unit): bool
{
    return unit_allows_fraction($unit) || is_whole_number($qty);
}

/**
 * Text that a spreadsheet would run as a formula when the CSV is opened
 * (=HYPERLINK(...), +cmd, @SUM, -2+3...). Plain negative numbers are not
 * formulas and are left alone.
 */
function looks_like_formula(string $value): bool
{
    if ($value === '' || is_numeric($value)) {
        return false;
    }

    return in_array($value[0], ['=', '+', '-', '@', "\t", "\r"], true);
}

/**
 * fputcsv() with every formula-looking text cell neutralised by a leading
 * apostrophe (the OWASP "CSV injection" mitigation) — every CSV export goes
 * through this, since names, descriptions and notes are all user input.
 */
function csv_row($handle, array $fields): void
{
    fputcsv($handle, array_map(
        static fn ($field) => is_string($field) && looks_like_formula($field) ? "'" . $field : $field,
        $fields
    ));
}

/**
 * Normalises a phone number to "+998XXXXXXXXX" (or "+<country><number>"
 * for a foreign one). Spaces, dashes, dots and brackets are ignored; a bare
 * 9-digit Uzbek number gets +998 prepended. Returns '' for empty input and
 * null for anything that isn't a phone number at all ("abc-telefon", too
 * few/many digits).
 */
function normalize_phone(?string $raw): ?string
{
    $raw = trim((string) $raw);
    if ($raw === '') {
        return '';
    }

    $compact = preg_replace('/[\s\-\.\(\)]/u', '', $raw);
    if (!preg_match('/^\+?\d+$/', (string) $compact)) {
        return null;
    }

    $digits = ltrim((string) $compact, '+');
    if (strlen($digits) === 9) {
        return '+998' . $digits;
    }
    if (strlen($digits) === 12 && str_starts_with($digits, '998')) {
        return '+' . $digits;
    }
    if (str_starts_with((string) $compact, '+') && strlen($digits) >= 10 && strlen($digits) <= 15) {
        return '+' . $digits;
    }

    return null;
}

/**
 * Digits-only international form for wa.me / tel: links. Numbers saved
 * before normalize_phone() existed may be a bare 9-digit local number
 * ("997026720"), which WhatsApp would read as a foreign number — those get
 * the 998 country code.
 */
function phone_digits_international(?string $phone): string
{
    $digits = preg_replace('/\D/', '', (string) $phone);
    if (strlen((string) $digits) === 9) {
        return '998' . $digits;
    }

    return (string) $digits;
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

/**
 * Per-request nonce that lets this app's own inline <script> blocks run
 * under the Content-Security-Policy (see send_security_headers()), while an
 * injected script without it is refused by the browser.
 */
function csp_nonce(): string
{
    static $nonce = null;

    return $nonce ??= base64_encode(random_bytes(16));
}

/**
 * Browser-side hardening headers, sent once per web request before anything
 * is rendered:
 * - CSP: scripts only from this site, Telegram's WebApp SDK, or a nonce'd
 *   inline block; no plugins; forms only post back here. frame-ancestors
 *   still lets Telegram's web client (web.telegram.org) show the app inside
 *   its WebApp iframe — X-Frame-Options can't name another origin, and
 *   browsers that understand frame-ancestors ignore it, so it's the
 *   SAMEORIGIN fallback for old ones only.
 * - nosniff, a referrer policy, and camera for this site only (barcode scan).
 */
function send_security_headers(): void
{
    if (headers_sent()) {
        return;
    }

    $csp = implode('; ', [
        "default-src 'self'",
        "script-src 'self' 'nonce-" . csp_nonce() . "' https://telegram.org",
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data: blob:",
        "font-src 'self' data:",
        "connect-src 'self'",
        "media-src 'self' blob:",
        "worker-src 'self'",
        "manifest-src 'self'",
        "object-src 'none'",
        "base-uri 'self'",
        "form-action 'self'",
        "frame-ancestors 'self' https://web.telegram.org https://*.telegram.org",
    ]);

    header('Content-Security-Policy: ' . $csp);
    header('X-Frame-Options: SAMEORIGIN');
    header('X-Content-Type-Options: nosniff');
    header('Referrer-Policy: strict-origin-when-cross-origin');
    header('Permissions-Policy: camera=(self), microphone=(), geolocation=(), payment=()');

    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
    if ($https) {
        header('Strict-Transport-Security: max-age=31536000');
    }
}

/**
 * Ends the request with a real 404 page — for a GET of a record that
 * doesn't exist (or belongs to another shop), instead of a redirect that
 * answers 200 and hides the problem.
 */
function abort_404(): never
{
    http_response_code(404);
    \App\Core\View::render('errors/404', [], 'layouts/auth');
    exit;
}

/**
 * The number shown and printed for a sale: its receipt_no when it has one
 * (sales made on a shop's computer, e.g. "K2-000145", keep the number their
 * paper receipt was printed with), otherwise its id.
 */
function receipt_number(array $sale): string
{
    $receiptNo = trim((string) ($sale['receipt_no'] ?? ''));

    return $receiptNo !== '' ? $receiptNo : (string) (int) ($sale['id'] ?? 0);
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
 * "<n> <label>" with the label in the right grammatical number: Russian
 * picks <key>_one / <key>_few / <key>_many (1 товар, 2 товара, 5 товаров);
 * Uzbek nouns don't change after a numeral, so the same forms are equal
 * there.
 */
function count_label(int $n, string $key): string
{
    $mod10 = $n % 10;
    $mod100 = $n % 100;
    if ($mod10 === 1 && $mod100 !== 11) {
        $form = 'one';
    } elseif ($mod10 >= 2 && $mod10 <= 4 && ($mod100 < 12 || $mod100 > 14)) {
        $form = 'few';
    } else {
        $form = 'many';
    }

    return number_format($n, 0, '.', ' ') . ' ' . t($key . '_' . $form);
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

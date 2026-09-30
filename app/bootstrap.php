<?php

declare(strict_types=1);

spl_autoload_register(function (string $class): void {
    $prefix = 'App\\';
    if (!str_starts_with($class, $prefix)) {
        return;
    }

    $relative = substr($class, strlen($prefix));
    $path = BASE_PATH . '/app/' . str_replace('\\', '/', $relative) . '.php';

    if (is_file($path)) {
        require $path;
    }
});

App\Core\Env::load(BASE_PATH . '/.env');
if (!is_file(BASE_PATH . '/.env')) {
    App\Core\Env::load(BASE_PATH . '/.env.example');
}

require BASE_PATH . '/app/helpers.php';

// The desktop app's JSON API (/api/...) authenticates every request itself
// and never sends a cookie, so it gets no session (one would be a new,
// empty session file per request).
$isApiRequest = str_starts_with((string) parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH), '/api/');

// A session lasts a week of inactivity instead of PHP's 24 minutes, and its
// files live in the app's own folder, where the server's own session cleaner
// (a cron job on Debian/Ubuntu using php.ini's 24 minutes) can't cut it short.
// The desktop app passes its own folder (desktop/lib/php-server.js). Staying
// signed in beyond that — across browser restarts — is "Meni eslab qol"
// (App\Core\RememberMe).
ini_set('session.gc_maxlifetime', (string) (7 * 86400));
ini_set('session.gc_probability', '1');
ini_set('session.gc_divisor', '100');
// (Not from command-line scripts: a folder they create could belong to
// another system user than the web server's.)
if (env('APP_MODE') !== 'desktop' && PHP_SAPI !== 'cli') {
    $sessionDir = BASE_PATH . '/database/sessions';
    if (!is_dir($sessionDir)) {
        @mkdir($sessionDir, 0770, true);
    }
    if (is_dir($sessionDir) && is_writable($sessionDir)) {
        session_save_path($sessionDir);
    }
}

session_set_cookie_params([
    'lifetime' => 0,
    'path' => '/',
    'secure' => request_is_https(),
    'httponly' => true,
    'samesite' => 'Lax',
]);
if (!$isApiRequest) {
    session_start();
    App\Core\Auth::restoreRemembered();
}

date_default_timezone_set('Asia/Tashkent');

if (!isset($_SESSION['lang'])) {
    $_SESSION['lang'] = env('APP_DEFAULT_LANG', 'uz');
}

$router = new App\Core\Router();
$router->setMiddlewareMap([
    'auth' => App\Middleware\AuthMiddleware::class,
    'guest' => App\Middleware\GuestMiddleware::class,
    'csrf' => App\Middleware\CsrfMiddleware::class,
    'permission' => App\Middleware\PermissionMiddleware::class,
    'role' => App\Middleware\RoleMiddleware::class,
    'web_only' => App\Middleware\WebOnlyMiddleware::class,
]);

require BASE_PATH . '/routes.php';

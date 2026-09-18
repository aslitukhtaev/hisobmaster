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

session_set_cookie_params([
    'lifetime' => 0,
    'path' => '/',
    'httponly' => true,
    'samesite' => 'Lax',
]);
session_start();

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
]);

require BASE_PATH . '/routes.php';

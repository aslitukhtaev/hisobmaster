<?php

declare(strict_types=1);

define('BASE_PATH', dirname(__DIR__));

require BASE_PATH . '/app/bootstrap.php';

send_security_headers();

/** @var App\Core\Router $router */
$request = new App\Core\Request();
App\Desktop\DesktopGuard::handle($request);
$router->dispatch($request);

<?php

declare(strict_types=1);

use App\Controllers\AuthController;
use App\Controllers\DashboardController;
use App\Controllers\LocaleController;
use App\Controllers\ProfileController;
use App\Controllers\SuperAdminController;

/** @var App\Core\Router $router */

$router->get('/login', [AuthController::class, 'showLogin'], ['guest']);
$router->post('/login', [AuthController::class, 'login'], ['guest', 'csrf']);
$router->post('/logout', [AuthController::class, 'logout'], ['auth', 'csrf']);

$router->post('/lang/{lang}', [LocaleController::class, 'switch']);

$router->get('/', [DashboardController::class, 'index'], ['auth']);

$router->get('/profile', [ProfileController::class, 'show'], ['auth']);
$router->post('/profile', [ProfileController::class, 'update'], ['auth', 'csrf']);

$router->get('/superadmin/shops', [SuperAdminController::class, 'shops'], ['auth', 'role:super_admin']);
$router->get('/superadmin/shops/create', [SuperAdminController::class, 'createForm'], ['auth', 'role:super_admin']);
$router->post('/superadmin/shops', [SuperAdminController::class, 'store'], ['auth', 'role:super_admin', 'csrf']);
$router->get('/superadmin/shops/{id}/created', [SuperAdminController::class, 'created'], ['auth', 'role:super_admin']);
$router->post('/superadmin/shops/{id}/toggle-status', [SuperAdminController::class, 'toggleStatus'], ['auth', 'role:super_admin', 'csrf']);
$router->post('/superadmin/shops/{id}/reset-password', [SuperAdminController::class, 'resetPassword'], ['auth', 'role:super_admin', 'csrf']);

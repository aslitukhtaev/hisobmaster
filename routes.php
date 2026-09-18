<?php

declare(strict_types=1);

use App\Controllers\AuthController;
use App\Controllers\DashboardController;
use App\Controllers\LocaleController;
use App\Controllers\ProductController;
use App\Controllers\ProfileController;
use App\Controllers\SaleController;
use App\Controllers\SuperAdminController;

/** @var App\Core\Router $router */

$router->get('/login', [AuthController::class, 'showLogin'], ['guest']);
$router->post('/login', [AuthController::class, 'login'], ['guest', 'csrf']);
$router->post('/logout', [AuthController::class, 'logout'], ['auth', 'csrf']);

$router->post('/lang/{lang}', [LocaleController::class, 'switch']);

$router->get('/', [DashboardController::class, 'index'], ['auth']);

$router->get('/profile', [ProfileController::class, 'show'], ['auth']);
$router->post('/profile', [ProfileController::class, 'update'], ['auth', 'csrf']);
$router->post('/profile/shop', [ProfileController::class, 'updateShop'], ['auth', 'role:owner', 'csrf']);

$router->get('/superadmin/shops', [SuperAdminController::class, 'shops'], ['auth', 'role:super_admin']);
$router->get('/superadmin/shops/create', [SuperAdminController::class, 'createForm'], ['auth', 'role:super_admin']);
$router->post('/superadmin/shops', [SuperAdminController::class, 'store'], ['auth', 'role:super_admin', 'csrf']);
$router->get('/superadmin/shops/{id}/created', [SuperAdminController::class, 'created'], ['auth', 'role:super_admin']);
$router->post('/superadmin/shops/{id}/toggle-status', [SuperAdminController::class, 'toggleStatus'], ['auth', 'role:super_admin', 'csrf']);
$router->post('/superadmin/shops/{id}/reset-password', [SuperAdminController::class, 'resetPassword'], ['auth', 'role:super_admin', 'csrf']);

$router->get('/products', [ProductController::class, 'index'], ['auth', 'permission:products']);
$router->get('/products/create', [ProductController::class, 'createForm'], ['auth', 'permission:products']);
$router->post('/products', [ProductController::class, 'store'], ['auth', 'permission:products', 'csrf']);
$router->get('/products/{id}/edit', [ProductController::class, 'editForm'], ['auth', 'permission:products']);
$router->post('/products/{id}', [ProductController::class, 'update'], ['auth', 'permission:products', 'csrf']);
$router->post('/products/{id}/toggle-status', [ProductController::class, 'toggleStatus'], ['auth', 'permission:products', 'csrf']);

$router->get('/sales', [SaleController::class, 'index'], ['auth', 'permission:sales']);
$router->get('/sales/new', [SaleController::class, 'newForm'], ['auth', 'permission:sales']);
$router->post('/sales', [SaleController::class, 'store'], ['auth', 'permission:sales', 'csrf']);
$router->get('/sales/{id}', [SaleController::class, 'receipt'], ['auth', 'permission:sales']);

<?php

declare(strict_types=1);

use App\Controllers\AuthController;
use App\Controllers\CustomerController;
use App\Controllers\DashboardController;
use App\Controllers\EmployeeController;
use App\Controllers\ExpenseController;
use App\Controllers\JoinController;
use App\Controllers\LocaleController;
use App\Controllers\ProductController;
use App\Controllers\ProfileController;
use App\Controllers\ReportController;
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

$router->get('/customers', [CustomerController::class, 'index'], ['auth', 'permission:customers']);
$router->get('/customers/create', [CustomerController::class, 'createForm'], ['auth', 'permission:customers']);
$router->post('/customers', [CustomerController::class, 'store'], ['auth', 'permission:customers', 'csrf']);
$router->get('/customers/{id}/edit', [CustomerController::class, 'editForm'], ['auth', 'permission:customers']);
$router->post('/customers/{id}', [CustomerController::class, 'update'], ['auth', 'permission:customers', 'csrf']);
$router->post('/customers/{id}/payment', [CustomerController::class, 'recordPayment'], ['auth', 'permission:customers', 'csrf']);
$router->get('/customers/{id}', [CustomerController::class, 'show'], ['auth', 'permission:customers']);

$router->get('/expenses', [ExpenseController::class, 'index'], ['auth', 'permission:expenses']);
$router->get('/expenses/create', [ExpenseController::class, 'createForm'], ['auth', 'permission:expenses']);
$router->post('/expenses', [ExpenseController::class, 'store'], ['auth', 'permission:expenses', 'csrf']);
$router->get('/expenses/{id}/edit', [ExpenseController::class, 'editForm'], ['auth', 'permission:expenses']);
$router->post('/expenses/{id}', [ExpenseController::class, 'update'], ['auth', 'permission:expenses', 'csrf']);
$router->post('/expenses/{id}/delete', [ExpenseController::class, 'delete'], ['auth', 'permission:expenses', 'csrf']);

$router->get('/reports', [ReportController::class, 'index'], ['auth', 'permission:reports']);

$router->get('/employees', [EmployeeController::class, 'index'], ['auth', 'role:owner']);
$router->get('/employees/invite', [EmployeeController::class, 'inviteForm'], ['auth', 'role:owner']);
$router->post('/employees/invite', [EmployeeController::class, 'createInvite'], ['auth', 'role:owner', 'csrf']);
$router->post('/employees/invites/{id}/revoke', [EmployeeController::class, 'revokeInvite'], ['auth', 'role:owner', 'csrf']);
$router->get('/employees/{id}/edit', [EmployeeController::class, 'editPermissions'], ['auth', 'role:owner']);
$router->post('/employees/{id}', [EmployeeController::class, 'updatePermissions'], ['auth', 'role:owner', 'csrf']);
$router->post('/employees/{id}/toggle-status', [EmployeeController::class, 'toggleStatus'], ['auth', 'role:owner', 'csrf']);

$router->get('/join/{token}', [JoinController::class, 'show'], ['guest']);
$router->post('/join/{token}', [JoinController::class, 'register'], ['guest', 'csrf']);

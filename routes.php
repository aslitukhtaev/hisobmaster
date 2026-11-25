<?php

declare(strict_types=1);

use App\Controllers\ActivityLogController;
use App\Controllers\ApiController;
use App\Controllers\AttendanceController;
use App\Controllers\AuthController;
use App\Controllers\BarcodeController;
use App\Controllers\CategoryController;
use App\Controllers\CustomerController;
use App\Controllers\DashboardController;
use App\Controllers\DesktopController;
use App\Controllers\DeviceController;
use App\Controllers\EmployeeController;
use App\Controllers\ExpenseController;
use App\Controllers\HelpController;
use App\Controllers\SettingsController;
use App\Controllers\SignupController;
use App\Controllers\QuickEntryController;
use App\Controllers\HomeController;
use App\Controllers\JoinController;
use App\Controllers\LabelController;
use App\Controllers\LocaleController;
use App\Controllers\ProductController;
use App\Controllers\ProfileController;
use App\Controllers\PurchaseController;
use App\Controllers\ReportController;
use App\Controllers\SaleController;
use App\Controllers\WholesaleRestoreController;
use App\Controllers\SupplierController;
use App\Controllers\SuperAdminController;
use App\Controllers\TelegramController;

/** @var App\Core\Router $router */

$router->get('/login', [AuthController::class, 'showLogin'], ['guest']);
$router->post('/login', [AuthController::class, 'login'], ['guest', 'csrf']);
$router->post('/login/telegram', [AuthController::class, 'telegram'], ['guest', 'csrf']);
$router->post('/logout', [AuthController::class, 'logout'], ['auth', 'csrf']);

// Sign in / sign up with Google or Telegram (SignupController). No guest
// middleware: signed in, the same flow links the account in the profile.
$router->get('/auth/google', [SignupController::class, 'googleStart']);
$router->get('/auth/google/callback', [SignupController::class, 'googleCallback']);
$router->get('/auth/telegram', [SignupController::class, 'telegramStart']);
$router->get('/auth/telegram/callback', [SignupController::class, 'telegramCallback']);
$router->post('/auth/telegram', [SignupController::class, 'telegramVerify'], ['csrf']);
$router->get('/signup', [SignupController::class, 'form'], ['guest']);
$router->post('/signup', [SignupController::class, 'store'], ['guest', 'csrf']);
$router->get('/subscription', [SignupController::class, 'subscription'], ['auth']);

$router->post('/lang/{lang}', [LocaleController::class, 'switch']);

$router->get('/', [HomeController::class, 'index']);
$router->post('/onboarding/dismiss', [DashboardController::class, 'dismissOnboarding'], ['auth', 'csrf']);

$router->get('/help', [HelpController::class, 'index'], ['auth']);
$router->get('/settings', [SettingsController::class, 'index'], ['auth']);

$router->get('/profile', [ProfileController::class, 'show'], ['auth']);
$router->post('/profile', [ProfileController::class, 'update'], ['auth', 'web_only', 'csrf']);
// Not web_only: in the desktop app it is saved on the server through the API (ProfileController::updateShop).
$router->post('/profile/shop', [ProfileController::class, 'updateShop'], ['auth', 'role:owner', 'csrf']);
$router->post('/profile/devices/sign-out-others', [ProfileController::class, 'signOutOthers'], ['auth', 'web_only', 'csrf']);
$router->post('/profile/devices/{id}/remove', [ProfileController::class, 'removeDevice'], ['auth', 'web_only', 'csrf']);
$router->post('/profile/identities/{id}/remove', [ProfileController::class, 'removeIdentity'], ['auth', 'web_only', 'csrf']);
$router->post('/profile/telegram/{id}/remove', [ProfileController::class, 'removeTelegram'], ['auth', 'web_only', 'csrf']);

$router->get('/superadmin/shops', [SuperAdminController::class, 'shops'], ['auth', 'role:super_admin']);
$router->get('/superadmin/reports', [SuperAdminController::class, 'reports'], ['auth', 'role:super_admin']);
$router->get('/superadmin/backup', [SuperAdminController::class, 'downloadBackup'], ['auth', 'role:super_admin']);
$router->get('/superadmin/backups', [SuperAdminController::class, 'backups'], ['auth', 'role:super_admin']);
$router->post('/superadmin/backups/create', [SuperAdminController::class, 'createBackup'], ['auth', 'role:super_admin', 'csrf']);
$router->get('/superadmin/backups/{filename}/download', [SuperAdminController::class, 'downloadBackupFile'], ['auth', 'role:super_admin']);
$router->post('/superadmin/backups/{filename}/delete', [SuperAdminController::class, 'deleteBackupFile'], ['auth', 'role:super_admin', 'csrf']);
$router->get('/superadmin/shops/create', [SuperAdminController::class, 'createForm'], ['auth', 'role:super_admin']);
$router->post('/superadmin/shops', [SuperAdminController::class, 'store'], ['auth', 'role:super_admin', 'csrf']);
$router->get('/superadmin/shops/{id}/created', [SuperAdminController::class, 'created'], ['auth', 'role:super_admin']);
$router->post('/superadmin/shops/{id}/toggle-status', [SuperAdminController::class, 'toggleStatus'], ['auth', 'role:super_admin', 'csrf']);
$router->post('/superadmin/shops/{id}/access', [SuperAdminController::class, 'updateAccess'], ['auth', 'role:super_admin', 'csrf']);
$router->post('/superadmin/shops/{id}/reset-password', [SuperAdminController::class, 'resetPassword'], ['auth', 'role:super_admin', 'csrf']);
$router->get('/superadmin/shops/{id}/devices', [SuperAdminController::class, 'devices'], ['auth', 'role:super_admin']);
$router->post('/superadmin/shops/{id}/devices/{deviceId}/revoke', [SuperAdminController::class, 'revokeDevice'], ['auth', 'role:super_admin', 'csrf']);
$router->post('/superadmin/shops/{id}/offline-days', [SuperAdminController::class, 'updateOfflineDays'], ['auth', 'role:super_admin', 'csrf']);

$router->get('/devices', [DeviceController::class, 'index'], ['auth', 'role:owner', 'web_only']);
$router->post('/devices/pair', [DeviceController::class, 'pair'], ['auth', 'role:owner', 'web_only', 'csrf']);
$router->post('/devices/{id}/revoke', [DeviceController::class, 'revoke'], ['auth', 'role:owner', 'web_only', 'csrf']);

$router->get('/products', [ProductController::class, 'index'], ['auth', 'permission:products']);
$router->get('/products/create', [ProductController::class, 'createForm'], ['auth', 'permission:products']);
$router->post('/products', [ProductController::class, 'store'], ['auth', 'permission:products', 'csrf']);
// Registered before /products/{id}/edit below for readability, though there's
// no actual pattern collision (different segment counts).
$router->get('/products/import', [ProductController::class, 'importForm'], ['auth', 'permission:products']);
$router->get('/products/quick', [QuickEntryController::class, 'productsForm'], ['auth', 'permission:products']);
$router->get('/products/bulk-edit', [QuickEntryController::class, 'bulkEditForm'], ['auth', 'permission:products']);
$router->post('/products/bulk-edit', [QuickEntryController::class, 'bulkEditStore'], ['auth', 'permission:products', 'csrf']);
// Reads the server's backups: website only.
$router->get('/products/restore-wholesale', [WholesaleRestoreController::class, 'show'], ['auth', 'role:owner', 'web_only']);
$router->post('/products/restore-wholesale', [WholesaleRestoreController::class, 'restore'], ['auth', 'role:owner', 'web_only', 'csrf']);
$router->post('/products/quick', [QuickEntryController::class, 'productsStore'], ['auth', 'permission:products', 'csrf']);
$router->post('/products/import/preview', [ProductController::class, 'importPreview'], ['auth', 'permission:products', 'csrf']);
$router->post('/products/import/commit', [ProductController::class, 'importCommit'], ['auth', 'permission:products', 'csrf']);
$router->get('/products/{id}/edit', [ProductController::class, 'editForm'], ['auth', 'permission:products']);
$router->post('/products/{id}', [ProductController::class, 'update'], ['auth', 'permission:products', 'csrf']);
$router->post('/products/{id}/toggle-status', [ProductController::class, 'toggleStatus'], ['auth', 'permission:products', 'csrf']);
$router->post('/products/{id}/variants', [ProductController::class, 'variantStore'], ['auth', 'permission:products', 'csrf']);
$router->post('/products/{id}/variants/{variantId}', [ProductController::class, 'variantUpdate'], ['auth', 'permission:products', 'csrf']);
$router->post('/products/{id}/variants/{variantId}/toggle-status', [ProductController::class, 'variantToggleStatus'], ['auth', 'permission:products', 'csrf']);

// The shop's own barcodes.
$router->post('/barcodes/next', [BarcodeController::class, 'next'], ['auth', 'permission:products', 'csrf']);
$router->post('/barcodes/assign', [BarcodeController::class, 'assign'], ['auth', 'permission:products', 'csrf']);
$router->post('/barcodes/generate-missing', [BarcodeController::class, 'generateMissing'], ['auth', 'permission:products', 'csrf']);

// Price labels; the defaults are the owner's.
$router->get('/labels', [LabelController::class, 'index'], ['auth', 'permission:products']);
$router->get('/labels/search', [LabelController::class, 'search'], ['auth', 'permission:products']);
$router->post('/labels/sizes', [LabelController::class, 'saveSize'], ['auth', 'permission:products', 'csrf']);
$router->post('/labels/sizes/delete', [LabelController::class, 'deleteSize'], ['auth', 'permission:products', 'csrf']);
$router->get('/labels/settings', [LabelController::class, 'settingsForm'], ['auth', 'role:owner']);
$router->post('/labels/settings', [LabelController::class, 'saveSettings'], ['auth', 'role:owner', 'csrf']);

// Product categories need "products", expense ones "expenses" — checked in the controller.
$router->get('/categories', [CategoryController::class, 'index'], ['auth']);
$router->post('/categories', [CategoryController::class, 'store'], ['auth', 'csrf']);
$router->post('/categories/{id}', [CategoryController::class, 'update'], ['auth', 'csrf']);
$router->post('/categories/{id}/merge', [CategoryController::class, 'merge'], ['auth', 'csrf']);
$router->post('/categories/{id}/delete', [CategoryController::class, 'delete'], ['auth', 'csrf']);

$router->get('/suppliers', [SupplierController::class, 'index'], ['auth', 'permission:products']);
$router->get('/suppliers/create', [SupplierController::class, 'createForm'], ['auth', 'permission:products']);
$router->post('/suppliers', [SupplierController::class, 'store'], ['auth', 'permission:products', 'csrf']);
$router->get('/suppliers/{id}/edit', [SupplierController::class, 'editForm'], ['auth', 'permission:products']);
$router->post('/suppliers/{id}', [SupplierController::class, 'update'], ['auth', 'permission:products', 'csrf']);

$router->get('/purchases', [PurchaseController::class, 'index'], ['auth', 'permission:products']);
$router->get('/purchases/create', [PurchaseController::class, 'createForm'], ['auth', 'permission:products']);
$router->post('/purchases', [PurchaseController::class, 'store'], ['auth', 'permission:products', 'csrf']);
$router->get('/purchases/{id}', [PurchaseController::class, 'show'], ['auth', 'permission:products']);

$router->get('/sales', [SaleController::class, 'index'], ['auth', 'permission:sales']);
$router->get('/sales/new', [SaleController::class, 'newForm'], ['auth', 'permission:sales']);
$router->post('/sales', [SaleController::class, 'store'], ['auth', 'permission:sales', 'csrf']);
// Registered before the single-segment /sales/{id} route below, since that
// route's {id} pattern would otherwise swallow "shift-report" (and "export")
// as if it were a sale id.
$router->get('/sales/shift-report', [SaleController::class, 'shiftReport'], ['auth', 'permission:sales']);
$router->get('/sales/export', [SaleController::class, 'exportCsv'], ['auth', 'permission:sales']);
$router->get('/sales/{id}/refund', [SaleController::class, 'refundForm'], ['auth', 'permission:sales']);
$router->get('/sales/{id}/print', [SaleController::class, 'printReceipt'], ['auth', 'permission:sales']);
$router->post('/sales/{id}/refund', [SaleController::class, 'refundStore'], ['auth', 'permission:sales', 'csrf']);
$router->get('/sales/{id}', [SaleController::class, 'receipt'], ['auth', 'permission:sales']);

$router->get('/customers', [CustomerController::class, 'index'], ['auth', 'permission:customers']);
$router->get('/customers/create', [CustomerController::class, 'createForm'], ['auth', 'permission:customers']);
$router->get('/customers/quick-debts', [QuickEntryController::class, 'debtsForm'], ['auth', 'permission:customers']);
$router->post('/customers/quick-debts', [QuickEntryController::class, 'debtsStore'], ['auth', 'permission:customers', 'csrf']);
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
$router->get('/reports/print', [ReportController::class, 'print'], ['auth', 'permission:reports']);
$router->get('/reports/export', [ReportController::class, 'exportCsv'], ['auth', 'permission:reports']);
$router->get('/reports/leaderboard', [ReportController::class, 'leaderboard'], ['auth', 'permission:reports']);

// Clock in/out: any logged-in shop user, not gated by a business-data
// permission — see AttendanceController.
$router->post('/attendance/clock-in', [AttendanceController::class, 'clockIn'], ['auth', 'csrf']);
$router->post('/attendance/clock-out', [AttendanceController::class, 'clockOut'], ['auth', 'csrf']);

$router->get('/employees', [EmployeeController::class, 'index'], ['auth', 'role:owner', 'web_only']);
$router->get('/employees/attendance', [AttendanceController::class, 'history'], ['auth', 'role:owner']);
$router->get('/employees/invite', [EmployeeController::class, 'inviteForm'], ['auth', 'role:owner', 'web_only']);
$router->post('/employees/invite', [EmployeeController::class, 'createInvite'], ['auth', 'role:owner', 'web_only', 'csrf']);
$router->post('/employees/invites/{id}/revoke', [EmployeeController::class, 'revokeInvite'], ['auth', 'role:owner', 'web_only', 'csrf']);
$router->get('/employees/{id}/edit', [EmployeeController::class, 'editPermissions'], ['auth', 'role:owner', 'web_only']);
$router->post('/employees/{id}', [EmployeeController::class, 'updatePermissions'], ['auth', 'role:owner', 'web_only', 'csrf']);
$router->post('/employees/{id}/toggle-status', [EmployeeController::class, 'toggleStatus'], ['auth', 'role:owner', 'web_only', 'csrf']);

$router->get('/join/{token}', [JoinController::class, 'show'], ['guest', 'web_only']);
$router->post('/join/{token}', [JoinController::class, 'register'], ['guest', 'web_only', 'csrf']);

$router->post('/telegram/webhook', [TelegramController::class, 'webhook'], []);

$router->get('/activity', [ActivityLogController::class, 'index'], ['auth', 'role:owner']);
$router->get('/activity/export', [ActivityLogController::class, 'exportCsv'], ['auth', 'role:owner']);

// Desktop app API (JSON, authenticated per request — see ApiController).
$router->post('/api/device/activate', [ApiController::class, 'activate']);
$router->post('/api/sync/push', [ApiController::class, 'push']);
$router->post('/api/sync/pull', [ApiController::class, 'pull']);
$router->post('/api/device/shop-settings', [ApiController::class, 'shopSettings']);
$router->post('/api/desktop/update', [ApiController::class, 'updateCheck']);
$router->post('/api/desktop/package', [ApiController::class, 'package']);
$router->get('/api/license/public-key', [ApiController::class, 'publicKey']);

// Desktop app only (404 on the website — see DesktopController::desktopOnly()).
$router->get('/desktop/activate', [DesktopController::class, 'activateForm']);
$router->post('/desktop/activate', [DesktopController::class, 'activate'], ['csrf']);
$router->get('/desktop/web-only', [DesktopController::class, 'webOnly'], ['auth']);
$router->post('/desktop/sync', [DesktopController::class, 'syncNow'], ['auth', 'csrf']);
$router->get('/desktop/status', [DesktopController::class, 'status']);
$router->get('/desktop/status-bar', [DesktopController::class, 'statusBar'], ['auth']);
$router->get('/desktop/printer', [DesktopController::class, 'printer'], ['auth']);
$router->get('/desktop/printer/test', [DesktopController::class, 'printerTest'], ['auth']);

<?php

use App\Core\Auth;
use App\Models\Attendance;
use App\Models\DebtTransaction;
use App\Models\Product;
use App\Models\Sale;

$pageTitle = t('dashboard');

$cards = [
    ['icon' => '🧾', 'title' => t('sales'), 'href' => '/sales', 'permission' => 'sales'],
    ['icon' => '📦', 'title' => t('products'), 'href' => '/products', 'permission' => 'products'],
    ['icon' => '🚚', 'title' => t('suppliers'), 'href' => '/suppliers', 'permission' => 'products'],
    ['icon' => '👥', 'title' => t('customers'), 'href' => '/customers', 'permission' => 'customers'],
    ['icon' => '💸', 'title' => t('expenses'), 'href' => '/expenses', 'permission' => 'expenses'],
    ['icon' => '📊', 'title' => t('reports'), 'href' => '/reports', 'permission' => 'reports'],
];

if (Auth::isOwner()) {
    $cards[] = ['icon' => '🧑‍🤝‍🧑', 'title' => t('employees'), 'href' => '/employees', 'permission' => null];
}

foreach ($cards as &$card) {
    $card['accessible'] = $card['permission'] === null || can($card['permission']);
}
unset($card);

$shopId = Auth::shopId();
$canSales = can('sales');
$canProducts = can('products');
$canCustomers = can('customers');

$productCounts = ($shopId && $canProducts) ? Product::counts((int) $shopId) : null;
$todaySales = ($shopId && $canSales) ? Sale::todaysSummary((int) $shopId) : null;
$totalDebt = ($shopId && $canCustomers) ? DebtTransaction::totalDebtByShop((int) $shopId) : 0.0;

// Clock-in/out: any logged-in shop user (owner or employee), not gated by a
// business-data permission — see AttendanceController. Not shown for
// super_admin, who has no shop_id and never works a shift.
$openShift = $shopId ? Attendance::openShiftFor((int) Auth::id()) : null;
$showAttendance = $shopId !== null;
?>
<?php if ($showOnboarding): ?>
<div class="onboarding-banner">
    <div class="onboarding-banner-icon">👋</div>
    <div class="onboarding-banner-body">
        <div class="onboarding-banner-title"><?= e(t('onboarding_welcome_title')) ?></div>
        <p class="onboarding-banner-text"><?= e(t('onboarding_welcome_body')) ?></p>
        <div class="onboarding-banner-actions">
            <a href="/help" class="btn btn-ghost btn-sm"><?= e(t('onboarding_help_link')) ?></a>
            <form method="post" action="/onboarding/dismiss">
                <?= csrf_field() ?>
                <button type="submit" class="btn btn-primary btn-sm"><?= e(t('onboarding_dismiss_button')) ?></button>
            </form>
        </div>
    </div>
</div>
<?php endif; ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('welcome', ['name' => $user['full_name'] ?? ''])) ?></h1>
        <p class="muted"><?= e(t('system_running')) ?> — <?= e(t('app_name')) ?></p>
    </div>
    <div class="row-actions">
        <?php if ($showAttendance): ?>
            <form method="post" action="<?= $openShift ? '/attendance/clock-out' : '/attendance/clock-in' ?>" class="attendance-form">
                <?= csrf_field() ?>
                <button type="submit" class="btn <?= $openShift ? 'btn-ghost attendance-btn-dashboard clocked-in' : 'btn-primary' ?>">
                    <?= e($openShift ? t('clock_out_button') : t('clock_in_button')) ?>
                </button>
            </form>
        <?php endif; ?>
        <?php if ($canSales): ?>
            <a href="/sales/new" class="btn btn-primary"><?= e(t('new_sale')) ?></a>
        <?php endif; ?>
    </div>
</section>

<?php if ($todaySales || ($productCounts && $productCounts['total'] > 0)): ?>
<section class="stat-grid">
    <div class="stat-tile">
        <div class="stat-value"><?= (int) ($todaySales['count'] ?? 0) ?></div>
        <div class="stat-label"><?= e(t('todays_sales_count')) ?></div>
    </div>
    <div class="stat-tile" style="grid-column: span 2;">
        <div class="stat-value" style="font-size:1.15rem;"><?= money((float) ($todaySales['revenue'] ?? 0)) ?></div>
        <div class="stat-label"><?= e(t('todays_revenue')) ?></div>
    </div>
</section>
<?php endif; ?>

<?php if ($productCounts && $productCounts['total'] > 0): ?>
<section class="stat-grid">
    <div class="stat-tile">
        <div class="stat-value"><?= (int) $productCounts['active'] ?></div>
        <div class="stat-label"><?= e(t('active_products_label')) ?></div>
    </div>
    <div class="stat-tile" style="grid-column: span 2;">
        <div class="stat-value" style="font-size:1.15rem;"><?= money((float) $productCounts['stock_value']) ?></div>
        <div class="stat-label"><?= e(t('stock_value')) ?></div>
    </div>
</section>
<?php endif; ?>

<?php if ($totalDebt > 0): ?>
<section class="stat-tile stock-value-tile" style="border-left:3px solid var(--danger);">
    <span class="stat-label"><?= e(t('total_debt_label')) ?></span>
    <a href="/customers" class="stat-value stock-value-amount" style="color:var(--danger);"><?= money($totalDebt) ?></a>
</section>
<?php endif; ?>

<?php if ($lowStockCounts && $lowStockCounts['low'] > 0): ?>
<a href="/products?filter=low_stock" class="card low-stock-tile">
    <div class="low-stock-tile-icon">⚠️</div>
    <div class="low-stock-tile-body">
        <div class="low-stock-tile-title"><?= e(t('low_stock_alert_title')) ?></div>
        <div class="muted">
            <?= (int) $lowStockCounts['low'] ?> <?= e(t('low_stock_count_label')) ?>
            <?php if ($lowStockCounts['out'] > 0): ?>
                · <?= (int) $lowStockCounts['out'] ?> <?= e(t('out_of_stock_count_label')) ?>
            <?php endif; ?>
        </div>
    </div>
    <div class="low-stock-tile-arrow">→</div>
</a>
<?php endif; ?>

<section class="card-grid">
    <?php foreach ($cards as $card): $tag = $card['accessible'] ? 'a' : 'div'; ?>
        <<?= $tag ?> <?= $card['accessible'] ? 'href="' . e($card['href']) . '"' : '' ?> class="module-card <?= $card['accessible'] ? '' : 'module-card-disabled' ?>">
            <div class="module-icon"><?= $card['icon'] ?></div>
            <div class="module-title"><?= e($card['title']) ?></div>
            <div class="module-badge"><?= $card['accessible'] ? e(t('open_module')) : e(t('no_access_badge')) ?></div>
        </<?= $tag ?>>
    <?php endforeach; ?>
</section>

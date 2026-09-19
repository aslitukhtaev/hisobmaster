<?php

use App\Core\Auth;
use App\Models\DebtTransaction;
use App\Models\Product;
use App\Models\Sale;

$pageTitle = t('dashboard');

$cards = [
    ['icon' => '🧾', 'title' => t('sales'), 'href' => '/sales', 'permission' => 'sales'],
    ['icon' => '📦', 'title' => t('products'), 'href' => '/products', 'permission' => 'products'],
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
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('welcome', ['name' => $user['full_name'] ?? ''])) ?></h1>
        <p class="muted"><?= e(t('system_running')) ?> — <?= e(t('app_name')) ?></p>
    </div>
    <?php if ($canSales): ?>
        <a href="/sales/new" class="btn btn-primary"><?= e(t('new_sale')) ?></a>
    <?php endif; ?>
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

<section class="card-grid">
    <?php foreach ($cards as $card): $tag = $card['accessible'] ? 'a' : 'div'; ?>
        <<?= $tag ?> <?= $card['accessible'] ? 'href="' . e($card['href']) . '"' : '' ?> class="module-card <?= $card['accessible'] ? '' : 'module-card-disabled' ?>">
            <div class="module-icon"><?= $card['icon'] ?></div>
            <div class="module-title"><?= e($card['title']) ?></div>
            <div class="module-badge"><?= $card['accessible'] ? e(t('open_module')) : e(t('no_access_badge')) ?></div>
        </<?= $tag ?>>
    <?php endforeach; ?>
</section>

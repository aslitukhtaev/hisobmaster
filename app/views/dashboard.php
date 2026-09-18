<?php

use App\Core\Auth;
use App\Models\DebtTransaction;
use App\Models\Product;
use App\Models\Sale;

$cards = [
    ['icon' => '🧾', 'title' => t('sales'), 'href' => '/sales', 'implemented' => true],
    ['icon' => '📦', 'title' => t('products'), 'href' => '/products', 'implemented' => true],
    ['icon' => '👥', 'title' => t('customers'), 'href' => '/customers', 'implemented' => true],
    ['icon' => '💸', 'title' => t('expenses'), 'href' => '#', 'implemented' => false],
    ['icon' => '📊', 'title' => t('reports'), 'href' => '#', 'implemented' => false],
    ['icon' => '🧑‍🤝‍🧑', 'title' => t('employees'), 'href' => '#', 'implemented' => false],
];

$shopId = Auth::shopId();
$productCounts = $shopId ? Product::counts((int) $shopId) : null;
$todaySales = $shopId ? Sale::todaysSummary((int) $shopId) : null;
$totalDebt = $shopId ? DebtTransaction::totalDebtByShop((int) $shopId) : 0.0;
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('welcome', ['name' => $user['full_name'] ?? ''])) ?></h1>
        <p class="muted"><?= e(t('system_running')) ?> — HisobMaster</p>
    </div>
    <a href="/sales/new" class="btn btn-primary"><?= e(t('new_sale')) ?></a>
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
    <?php foreach ($cards as $card): $tag = $card['implemented'] ? 'a' : 'div'; ?>
        <<?= $tag ?> <?= $card['implemented'] ? 'href="' . e($card['href']) . '"' : '' ?> class="module-card <?= $card['implemented'] ? '' : 'module-card-disabled' ?>">
            <div class="module-icon"><?= $card['icon'] ?></div>
            <div class="module-title"><?= e($card['title']) ?></div>
            <div class="module-badge"><?= $card['implemented'] ? e(t('open_module')) : e(t('coming_soon')) ?></div>
        </<?= $tag ?>>
    <?php endforeach; ?>
</section>

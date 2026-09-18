<?php

use App\Core\Auth;
use App\Models\Product;

$cards = [
    ['icon' => '🧾', 'title' => t('sales'), 'href' => '#', 'implemented' => false],
    ['icon' => '📦', 'title' => t('products'), 'href' => '/products', 'implemented' => true],
    ['icon' => '👥', 'title' => t('customers'), 'href' => '#', 'implemented' => false],
    ['icon' => '💸', 'title' => t('expenses'), 'href' => '#', 'implemented' => false],
    ['icon' => '📊', 'title' => t('reports'), 'href' => '#', 'implemented' => false],
    ['icon' => '🧑‍🤝‍🧑', 'title' => t('employees'), 'href' => '#', 'implemented' => false],
];

$productCounts = Auth::shopId() ? Product::counts((int) Auth::shopId()) : null;
?>
<section class="page-head">
    <h1><?= e(t('welcome', ['name' => $user['full_name'] ?? ''])) ?></h1>
    <p class="muted"><?= e(t('system_running')) ?> — HisobMaster</p>
</section>

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

<section class="card-grid">
    <?php foreach ($cards as $card): $tag = $card['implemented'] ? 'a' : 'div'; ?>
        <<?= $tag ?> <?= $card['implemented'] ? 'href="' . e($card['href']) . '"' : '' ?> class="module-card <?= $card['implemented'] ? '' : 'module-card-disabled' ?>">
            <div class="module-icon"><?= $card['icon'] ?></div>
            <div class="module-title"><?= e($card['title']) ?></div>
            <div class="module-badge"><?= $card['implemented'] ? e(t('open_module')) : e(t('coming_soon')) ?></div>
        </<?= $tag ?>>
    <?php endforeach; ?>
</section>

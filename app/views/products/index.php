<?php $pageTitle = t('products'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('products')) ?></h1>
        <p class="muted">
            <?= (int) $counts['total'] ?> <?= e(t('products_count_label')) ?> ·
            <?= (int) $counts['active'] ?> <?= e(t('active_label')) ?>
        </p>
    </div>
    <div class="row-actions">
        <a href="/products/import" class="btn btn-ghost"><?= e(t('import_products')) ?></a>
        <a href="/products/create" class="btn btn-primary"><?= e(t('add_product')) ?></a>
    </div>
</section>

<?php if (can('prices')): ?>
<section class="stat-tile stock-value-tile">
    <span class="stat-label"><?= e(t('stock_value')) ?></span>
    <span class="stat-value stock-value-amount"><?= money((float) $counts['stock_value']) ?></span>
</section>
<?php endif; ?>

<?php if ($lowStockCounts['low'] > 0): ?>
<div class="filter-tabs">
    <a href="/products" class="filter-tab <?= !$lowStockOnly ? 'active' : '' ?>"><?= e(t('filter_all_products')) ?></a>
    <a href="/products?filter=low_stock" class="filter-tab <?= $lowStockOnly ? 'active' : '' ?>">
        <?= e(t('filter_low_stock_only')) ?> (<?= (int) $lowStockCounts['low'] ?>)
    </a>
</div>
<?php endif; ?>

<?php if (!$lowStockOnly): ?>
<form method="get" action="/products" class="search-bar">
    <label class="sr-only" for="product-search-q"><?= e(t('search_products_placeholder')) ?></label>
    <input type="text" id="product-search-q" name="q" placeholder="<?= e(t('search_products_placeholder')) ?>" value="<?= e($search) ?>">
    <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('search')) ?></button>
</form>
<?php endif; ?>

<?php if (empty($products)): ?>
    <div class="card">
        <p class="muted"><?= e($lowStockOnly ? t('no_low_stock_products') : ($search !== '' ? t('no_products_found') : t('no_products_yet'))) ?></p>
    </div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('product_name')) ?></th>
                        <th><?= e(t('category')) ?></th>
                        <?php if (can('prices')): ?><th><?= e(t('cost_price')) ?></th><?php endif; ?>
                        <th><?= e(t('sell_price')) ?></th>
                        <th><?= e(t('stock_qty')) ?></th>
                        <th><?= e(t('status')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($products as $product):
                    $effectiveThreshold = $product['low_stock_threshold'] !== null
                        ? (float) $product['low_stock_threshold']
                        : $shopDefaultThreshold;
                    $isLowStock = $effectiveThreshold !== null && (float) $product['stock_qty'] <= $effectiveThreshold;
                ?>
                    <tr>
                        <td><?= e($product['name']) ?></td>
                        <td class="muted" data-label="<?= e(t('category')) ?>"><?= e($product['category_name'] ?? '—') ?></td>
                        <?php if (can('prices')): ?>
                            <td data-label="<?= e(t('cost_price')) ?>"><?= money((float) $product['cost_price']) ?></td>
                        <?php endif; ?>
                        <td data-label="<?= e(t('sell_price')) ?>"><?= money((float) $product['sell_price']) ?></td>
                        <td data-label="<?= e(t('stock_qty')) ?>">
                            <?= e(format_qty((float) $product['stock_qty'])) ?> <?= e($product['unit']) ?>
                            <?php if ($isLowStock): ?>
                                <span class="stock-low-badge" title="<?= e(t('low_stock_badge_title')) ?>">
                                    <?= e((float) $product['stock_qty'] <= 0 ? t('out_of_stock_badge') : t('low_stock_badge')) ?>
                                </span>
                            <?php endif; ?>
                        </td>
                        <td data-label="<?= e(t('status')) ?>">
                            <span class="status-pill <?= $product['status'] === 'active' ? 'status-active' : 'status-blocked' ?>">
                                <?= e($product['status'] === 'active' ? t('active_status') : t('inactive_status')) ?>
                            </span>
                        </td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <div class="row-actions">
                                <a href="/products/<?= (int) $product['id'] ?>/edit" class="btn btn-ghost btn-sm"><?= e(t('edit')) ?></a>
                                <form method="post" action="/products/<?= (int) $product['id'] ?>/toggle-status"
                                      onsubmit="return confirm('<?= e($product['status'] === 'active' ? t('confirm_deactivate') : t('confirm_activate_product')) ?>');">
                                    <?= csrf_field() ?>
                                    <button type="submit" class="btn btn-ghost btn-sm">
                                        <?= e($product['status'] === 'active' ? t('deactivate') : t('toggle_activate')) ?>
                                    </button>
                                </form>
                            </div>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

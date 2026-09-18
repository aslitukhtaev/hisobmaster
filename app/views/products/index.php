<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('products')) ?></h1>
        <p class="muted">
            <?= (int) $counts['total'] ?> <?= e(t('products_count_label')) ?> ·
            <?= (int) $counts['active'] ?> <?= e(t('active_label')) ?>
        </p>
    </div>
    <a href="/products/create" class="btn btn-primary"><?= e(t('add_product')) ?></a>
</section>

<section class="stat-tile stock-value-tile">
    <span class="stat-label"><?= e(t('stock_value')) ?></span>
    <span class="stat-value stock-value-amount"><?= money((float) $counts['stock_value']) ?></span>
</section>

<form method="get" action="/products" class="search-bar">
    <input type="text" name="q" placeholder="<?= e(t('search_products_placeholder')) ?>" value="<?= e($search) ?>">
    <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('search')) ?></button>
</form>

<?php if (empty($products)): ?>
    <div class="card">
        <p class="muted"><?= e($search !== '' ? t('no_products_found') : t('no_products_yet')) ?></p>
    </div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('product_name')) ?></th>
                        <th><?= e(t('category')) ?></th>
                        <th><?= e(t('cost_price')) ?></th>
                        <th><?= e(t('sell_price')) ?></th>
                        <th><?= e(t('stock_qty')) ?></th>
                        <th><?= e(t('status')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($products as $product): ?>
                    <tr>
                        <td><?= e($product['name']) ?></td>
                        <td class="muted" data-label="<?= e(t('category')) ?>"><?= e($product['category_name'] ?? '—') ?></td>
                        <td data-label="<?= e(t('cost_price')) ?>"><?= money((float) $product['cost_price']) ?></td>
                        <td data-label="<?= e(t('sell_price')) ?>"><?= money((float) $product['sell_price']) ?></td>
                        <td data-label="<?= e(t('stock_qty')) ?>"><?= e(format_qty((float) $product['stock_qty'])) ?> <?= e($product['unit']) ?></td>
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

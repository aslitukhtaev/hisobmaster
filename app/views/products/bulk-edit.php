<?php
$pageTitle = t('bulk_edit_title');

$columns = [
    ['key' => 'name', 'label' => t('product_name'), 'wide' => true],
    ['key' => 'barcode', 'label' => t('barcode')],
    ['key' => 'sell_price', 'label' => $wholesale ? t('retail_price') : t('sell_price'), 'number' => true],
];
if ($wholesale) {
    $columns[] = ['key' => 'wholesale_price', 'label' => t('wholesale_price'), 'number' => true];
}
if ($canPrices) {
    $columns[] = ['key' => 'cost_price', 'label' => t('cost_price'), 'number' => true];
}
$bulkI18n = [
    'saving' => t('quick_saving'),
    'saved' => t('bulk_edit_saved'),
    'errors' => t('quick_errors_summary'),
    'changes' => t('bulk_edit_changes'),
    'nothing' => t('bulk_edit_nothing'),
    'failed' => t('quick_failed'),
    'more' => t('bulk_edit_more'),
    'empty' => t('bulk_edit_empty'),
    'leave' => t('bulk_edit_leave'),
    'variantOf' => t('bulk_edit_variant_of'),
];
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('bulk_edit_title')) ?></h1>
        <p class="muted"><?= e(t('bulk_edit_hint')) ?></p>
    </div>
    <div class="row-actions">
        <a href="/products/quick" class="btn btn-ghost"><?= e(t('quick_products_title')) ?></a>
        <a href="/products" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
    </div>
</section>

<div class="card bulk-filters">
    <label class="sr-only" for="bulk-search"><?= e(t('search_products_placeholder')) ?></label>
    <input type="search" id="bulk-search" placeholder="<?= e(t('search_products_placeholder')) ?>" autocomplete="off">
    <?php if (!empty($categories)): ?>
        <label class="sr-only" for="bulk-category"><?= e(t('category')) ?></label>
        <select id="bulk-category">
            <option value=""><?= e(t('all_categories')) ?></option>
            <?php foreach ($categories as $cat): ?><option value="<?= e($cat['name']) ?>"><?= e($cat['name']) ?></option><?php endforeach; ?>
        </select>
    <?php endif; ?>
    <label class="bulk-only-changed"><input type="checkbox" id="bulk-only-changed"> <?= e(t('bulk_edit_only_changed')) ?></label>
</div>

<div class="card quick-card">
    <div class="quick-grid-wrap">
        <table class="quick-grid bulk-grid" id="bulk-grid"
               data-csrf="<?= e(csrf_token()) ?>"
               data-columns="<?= e(json_encode($columns, JSON_UNESCAPED_UNICODE)) ?>"
               data-i18n="<?= e(json_encode($bulkI18n, JSON_UNESCAPED_UNICODE)) ?>">
            <thead>
                <tr>
                    <th class="quick-num">#</th>
                    <?php foreach ($columns as $column): ?>
                        <th<?= !empty($column['wide']) ? ' class="quick-wide"' : '' ?>><?= e($column['label']) ?></th>
                    <?php endforeach; ?>
                </tr>
            </thead>
            <tbody></tbody>
        </table>
    </div>
    <p class="muted bulk-more" id="bulk-more" hidden></p>
    <div class="quick-footer">
        <span class="muted" id="bulk-count"></span>
        <span class="quick-spacer"></span>
        <span class="quick-result" id="bulk-result" role="status" aria-live="polite"></span>
        <button type="button" class="btn btn-primary" id="bulk-save"><?= e(t('save')) ?></button>
    </div>
</div>
<script nonce="<?= e(csp_nonce()) ?>">
    window.HM_BULK_ROWS = <?= json_encode($rows, JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>;
</script>
<script src="<?= asset('js/bulk-edit.js') ?>" defer></script>

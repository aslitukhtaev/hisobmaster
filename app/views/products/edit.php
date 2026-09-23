<section class="page-head">
    <h1><?= e(t('edit_product')) ?></h1>
</section>

<?php if ($hasActiveVariants && (float) $product['stock_qty'] > 0): ?>
    <div class="alert alert-warning"><?= e(t('parent_stock_leftover_warning', ['qty' => format_qty((float) $product['stock_qty']), 'unit' => $product['unit']])) ?></div>
<?php endif; ?>

<?php
$actionUrl = '/products/' . (int) $product['id'];
$submitLabel = t('save');
require BASE_PATH . '/app/views/products/_form.php';
?>

<section class="page-head" style="margin-top:24px;">
    <h1><?= e(t('variants_title')) ?></h1>
    <p class="muted"><?= e(t('variants_hint')) ?></p>
</section>

<?php if (!empty($variants)): ?>
    <?php foreach ($variants as $variant): ?>
    <div class="card variant-row-card">
        <form method="post" action="/products/<?= (int) $product['id'] ?>/variants/<?= (int) $variant['id'] ?>" class="stack">
            <?= csrf_field() ?>
            <div class="field-row">
                <label class="field">
                    <span><?= e(t('variant_label')) ?></span>
                    <input type="text" name="variant_label" maxlength="100" value="<?= e($variant['variant_label']) ?>" required>
                </label>
                <label class="field">
                    <span><?= e(t('stock_qty')) ?></span>
                    <input type="number" step="<?= unit_allows_fraction($product['unit']) ? 'any' : '1' ?>" min="0" inputmode="decimal" name="variant_stock_qty" value="<?= e((string) (float) $variant['stock_qty']) ?>" required>
                </label>
            </div>
            <div class="field-row">
                <label class="field">
                    <span><?= e(t('barcode')) ?> (<?= e(t('optional')) ?>)</span>
                    <input type="text" name="variant_barcode" maxlength="64" value="<?= e($variant['barcode'] ?? '') ?>">
                </label>
                <?php if (can('prices')): ?>
                <label class="field">
                    <span><?= e(t('cost_price')) ?> (<?= e(t('optional')) ?>)</span>
                    <input type="number" step="0.01" min="0" inputmode="decimal" name="variant_cost_price" placeholder="<?= e(t('same_as_product')) ?>" value="<?= e($variant['cost_price'] !== null ? (string) $variant['cost_price'] : '') ?>">
                </label>
                <?php endif; ?>
            </div>
            <div class="field-row">
                <label class="field">
                    <span><?= e(t('sell_price')) ?> (<?= e(t('optional')) ?>)</span>
                    <input type="number" step="0.01" min="0" inputmode="decimal" name="variant_sell_price" placeholder="<?= e(t('same_as_product')) ?>" value="<?= e($variant['sell_price'] !== null ? (string) $variant['sell_price'] : '') ?>">
                </label>
                <div class="field">
                    <span><?= e(t('status')) ?></span>
                    <span class="status-pill <?= $variant['status'] === 'active' ? 'status-active' : 'status-blocked' ?>" style="width:fit-content;">
                        <?= e($variant['status'] === 'active' ? t('active_status') : t('inactive_status')) ?>
                    </span>
                </div>
            </div>
            <div class="row-actions">
                <button type="submit" class="btn btn-primary btn-sm"><?= e(t('save')) ?></button>
            </div>
        </form>
        <form method="post" action="/products/<?= (int) $product['id'] ?>/variants/<?= (int) $variant['id'] ?>/toggle-status"
              data-confirm="<?= e($variant['status'] === 'active' ? t('confirm_deactivate_variant') : t('confirm_activate_variant')) ?>" style="margin-top:8px;">
            <?= csrf_field() ?>
            <button type="submit" class="btn btn-ghost btn-sm">
                <?= e($variant['status'] === 'active' ? t('deactivate') : t('toggle_activate')) ?>
            </button>
        </form>
    </div>
    <?php endforeach; ?>
<?php else: ?>
<div class="card">
    <p class="muted"><?= e(t('no_variants_yet')) ?></p>
</div>
<?php endif; ?>

<div class="card form-card" style="margin-top:14px;">
    <h2><?= e(t('add_variant')) ?></h2>
    <form method="post" action="/products/<?= (int) $product['id'] ?>/variants" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('variant_label')) ?></span>
            <input type="text" name="variant_label" required maxlength="100" placeholder="<?= e(t('variant_label_placeholder')) ?>">
        </label>
        <?php $takesParentStock = !$hasActiveVariants && (float) $product['stock_qty'] > 0; ?>
        <div class="field-row">
            <label class="field">
                <span><?= e(t('stock_qty')) ?></span>
                <input type="number" step="<?= unit_allows_fraction($product['unit']) ? 'any' : '1' ?>" min="0" inputmode="decimal" name="variant_stock_qty" required
                       value="<?= e($takesParentStock ? (string) (float) $product['stock_qty'] : '0') ?>">
                <?php if ($takesParentStock): ?>
                    <input type="hidden" name="take_parent_stock" value="1">
                    <span class="field-warning"><?= e(t('first_variant_takes_stock_hint', ['qty' => format_qty((float) $product['stock_qty']), 'unit' => $product['unit']])) ?></span>
                <?php endif; ?>
            </label>
            <label class="field">
                <span><?= e(t('barcode')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="text" name="variant_barcode" maxlength="64">
            </label>
        </div>
        <div class="field-row">
            <?php if (can('prices')): ?>
            <label class="field">
                <span><?= e(t('cost_price')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="number" step="0.01" min="0" inputmode="decimal" name="variant_cost_price" placeholder="<?= e(t('same_as_product')) ?>">
            </label>
            <?php endif; ?>
            <label class="field">
                <span><?= e(t('sell_price')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="number" step="0.01" min="0" inputmode="decimal" name="variant_sell_price" placeholder="<?= e(t('same_as_product')) ?>">
            </label>
        </div>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('add_variant')) ?></button>
    </form>
</div>

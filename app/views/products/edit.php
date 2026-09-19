<section class="page-head">
    <h1><?= e(t('edit_product')) ?></h1>
</section>

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
                    <input type="text" name="variant_label" value="<?= e($variant['variant_label']) ?>" required>
                </label>
                <label class="field">
                    <span><?= e(t('stock_qty')) ?></span>
                    <input type="number" step="0.01" min="0" inputmode="decimal" name="variant_stock_qty" value="<?= e((string) $variant['stock_qty']) ?>" required>
                </label>
            </div>
            <div class="field-row">
                <label class="field">
                    <span><?= e(t('barcode')) ?> (<?= e(t('optional')) ?>)</span>
                    <input type="text" name="variant_barcode" value="<?= e($variant['barcode'] ?? '') ?>">
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
              onsubmit="return confirm('<?= e($variant['status'] === 'active' ? t('confirm_deactivate_variant') : t('confirm_activate_variant')) ?>');" style="margin-top:8px;">
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
            <input type="text" name="variant_label" required placeholder="<?= e(t('variant_label_placeholder')) ?>">
        </label>
        <div class="field-row">
            <label class="field">
                <span><?= e(t('stock_qty')) ?></span>
                <input type="number" step="0.01" min="0" inputmode="decimal" name="variant_stock_qty" required value="0">
            </label>
            <label class="field">
                <span><?= e(t('barcode')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="text" name="variant_barcode">
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

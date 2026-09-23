<?php $pageTitle = t('record_purchase'); ?>
<section class="page-head">
    <h1><?= e(t('record_purchase')) ?></h1>
</section>

<?php if (!$hasProducts): ?>
    <div class="card">
        <p class="muted"><?= e(t('no_products_for_purchase')) ?></p>
        <a href="/products/create" class="btn btn-primary" style="margin-top:14px;"><?= e(t('add_product')) ?></a>
    </div>
<?php else: ?>

<div class="card form-card" style="max-width:820px;">
    <form method="post" action="/purchases" id="purchase-form" class="stack">
        <?= csrf_field() ?>
        <div class="field-row">
            <label class="field">
                <span><?= e(t('supplier_name')) ?> (<?= e(t('optional')) ?>)</span>
                <select name="supplier_id">
                    <option value=""><?= e(t('no_supplier_option')) ?></option>
                    <?php foreach ($suppliers as $supplier): ?>
                        <option value="<?= (int) $supplier['id'] ?>" <?= old('supplier_id') === (string) $supplier['id'] ? 'selected' : '' ?>>
                            <?= e($supplier['name']) ?>
                        </option>
                    <?php endforeach; ?>
                </select>
            </label>
            <label class="field">
                <span><?= e(t('note')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="text" name="note" value="<?= e(old('note')) ?>">
            </label>
        </div>

        <div class="purchase-items-wrap">
            <table class="purchase-items-table">
                <thead>
                    <tr>
                        <th><?= e(t('product_name')) ?></th>
                        <th><?= e(t('qty_short')) ?></th>
                        <th><?= e(t('unit_cost_label')) ?></th>
                        <th><?= e(t('sum')) ?></th>
                        <th></th>
                    </tr>
                </thead>
                <tbody id="purchase-items-body"></tbody>
            </table>
        </div>
        <button type="button" id="add-purchase-item-btn" class="btn btn-ghost btn-sm"><?= e(t('add_item_row')) ?></button>

        <label class="permission-check">
            <input type="checkbox" name="update_cost_price_checkbox" id="update-cost-price-checkbox">
            <span><?= e(t('update_cost_price_label')) ?></span>
        </label>
        <input type="hidden" name="update_cost_price" id="update-cost-price-field" value="0">

        <div class="cart-total-row">
            <span><?= e(t('total')) ?></span>
            <strong id="purchase-total">0 <?= e(t('currency_symbol')) ?></strong>
        </div>

        <input type="hidden" name="items" id="purchase-items-field" value="[]">
        <button type="submit" id="submit-purchase-btn" class="btn btn-primary btn-block" disabled><?= e(t('record_purchase')) ?></button>
    </form>
</div>

<script nonce="<?= e(csp_nonce()) ?>">
    window.HM_PURCHASE_PRODUCTS = <?= $productsJson ?>;
    window.HM_PURCHASE_I18N = {
        removeRow: <?= json_encode(t('remove_row'), JSON_UNESCAPED_UNICODE) ?>,
        packsLabel: <?= json_encode(t('packs_label'), JSON_UNESCAPED_UNICODE) ?>,
        packHint: <?= json_encode(t('pack_size_example'), JSON_UNESCAPED_UNICODE) ?>,
        currency: <?= json_encode(t('currency_symbol'), JSON_UNESCAPED_UNICODE) ?>
    };
</script>
<script src="<?= asset('js/purchase.js') ?>" defer></script>
<?php endif; ?>

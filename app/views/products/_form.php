<?php $product = $product ?? []; ?>
<div class="card form-card">
    <form method="post" action="<?= e($actionUrl) ?>" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('product_name')) ?></span>
            <input type="text" name="name" required autofocus value="<?= e(old('name', $product['name'] ?? '')) ?>">
        </label>

        <div class="field-row">
            <label class="field">
                <span><?= e(t('category')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="text" name="category" list="category-list" value="<?= e(old('category', $product['category_name'] ?? '')) ?>">
                <datalist id="category-list">
                    <?php foreach ($categories as $cat): ?>
                        <option value="<?= e($cat['name']) ?>">
                    <?php endforeach; ?>
                </datalist>
            </label>
            <label class="field">
                <span><?= e(t('unit')) ?></span>
                <input type="text" name="unit" list="unit-list" value="<?= e(old('unit', $product['unit'] ?? 'dona')) ?>">
                <datalist id="unit-list">
                    <option value="dona"></option>
                    <option value="kg"></option>
                    <option value="litr"></option>
                    <option value="metr"></option>
                    <option value="quti"></option>
                </datalist>
            </label>
        </div>

        <div class="field-row">
            <label class="field">
                <span><?= e(t('cost_price')) ?></span>
                <input type="number" step="0.01" min="0" inputmode="decimal" name="cost_price" required
                       value="<?= e(old('cost_price', isset($product['cost_price']) ? (string) $product['cost_price'] : '')) ?>">
            </label>
            <label class="field">
                <span><?= e(t('sell_price')) ?></span>
                <input type="number" step="0.01" min="0" inputmode="decimal" name="sell_price" required
                       value="<?= e(old('sell_price', isset($product['sell_price']) ? (string) $product['sell_price'] : '')) ?>">
            </label>
        </div>

        <div class="field-row">
            <label class="field">
                <span><?= e(t('stock_qty')) ?></span>
                <input type="number" step="0.01" min="0" inputmode="decimal" name="stock_qty" required
                       value="<?= e(old('stock_qty', isset($product['stock_qty']) ? (string) $product['stock_qty'] : '')) ?>">
            </label>
            <label class="field">
                <span><?= e(t('barcode')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="text" name="barcode" value="<?= e(old('barcode', $product['barcode'] ?? '')) ?>">
            </label>
        </div>

        <button type="submit" class="btn btn-primary btn-block"><?= e($submitLabel) ?></button>
    </form>
</div>

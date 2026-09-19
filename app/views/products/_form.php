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
            <?php if (can('prices')): ?>
                <label class="field">
                    <span><?= e(t('cost_price')) ?></span>
                    <input type="number" step="0.01" min="0" inputmode="decimal" name="cost_price" required
                           value="<?= e(old('cost_price', isset($product['cost_price']) ? (string) $product['cost_price'] : '')) ?>">
                </label>
            <?php endif; ?>
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
                <div class="scan-input-row">
                    <input type="text" name="barcode" id="product-barcode-input" value="<?= e(old('barcode', $product['barcode'] ?? '')) ?>">
                    <button type="button" class="scan-btn barcode-scan-btn" data-target="#product-barcode-input" aria-label="<?= e(t('scan_barcode')) ?>" title="<?= e(t('scan_barcode')) ?>">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8V5a1 1 0 0 1 1-1h3M20 8V5a1 1 0 0 0-1-1h-3M4 16v3a1 1 0 0 0 1 1h3M20 16v3a1 1 0 0 1-1 1h-3M6 8v8M10 8v8M14 8v8M18 8v8"/></svg>
                    </button>
                </div>
            </label>
        </div>

        <div class="field-row">
            <label class="field">
                <span><?= e(t('low_stock_threshold_label')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="number" step="0.01" min="0" inputmode="decimal" name="low_stock_threshold"
                       value="<?= e(old('low_stock_threshold', isset($product['low_stock_threshold']) && $product['low_stock_threshold'] !== null ? (string) $product['low_stock_threshold'] : '')) ?>">
                <span class="muted"><?= e(t('low_stock_threshold_hint')) ?></span>
            </label>
            <label class="field">
                <span><?= e(t('pack_size_label')) ?> (<?= e(t('optional')) ?>)</span>
                <input type="number" step="1" min="1" inputmode="numeric" name="pack_size" id="product-pack-size-input"
                       value="<?= e(old('pack_size', isset($product['pack_size']) && $product['pack_size'] !== null ? (string) $product['pack_size'] : '')) ?>">
                <span class="muted" id="pack-size-hint"><?= e(t('pack_size_hint')) ?></span>
            </label>
        </div>

        <button type="submit" class="btn btn-primary btn-block"><?= e($submitLabel) ?></button>
    </form>
</div>
<script>
    (function () {
        var input = document.getElementById('product-pack-size-input');
        var hint = document.getElementById('pack-size-hint');
        var unitInput = document.querySelector('input[name="unit"]');
        if (!input || !hint) { return; }
        var baseHint = <?= json_encode(t('pack_size_hint'), JSON_UNESCAPED_UNICODE) ?>;
        var exampleTpl = <?= json_encode(t('pack_size_example'), JSON_UNESCAPED_UNICODE) ?>;
        function update() {
            var n = parseInt(input.value, 10);
            var unit = (unitInput && unitInput.value) ? unitInput.value : 'dona';
            if (n > 0) {
                hint.textContent = exampleTpl.replace(':n', n).replace(':unit', unit);
            } else {
                hint.textContent = baseHint;
            }
        }
        input.addEventListener('input', update);
        if (unitInput) { unitInput.addEventListener('input', update); }
        update();
    })();
</script>

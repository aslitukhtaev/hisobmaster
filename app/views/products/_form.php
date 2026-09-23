<?php
$product = $product ?? [];
$hasActiveVariants = $hasActiveVariants ?? false;
$variantStockTotal = $variantStockTotal ?? 0.0;
$parentLeftover = $hasActiveVariants ? (float) ($product['stock_qty'] ?? 0) : 0.0;
?>
<div class="card form-card">
    <form method="post" action="<?= e($actionUrl) ?>" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('product_name')) ?></span>
            <input type="text" name="name" required autofocus maxlength="200" value="<?= e(old('name', $product['name'] ?? '')) ?>">
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
                    <input type="number" step="0.01" min="0" max="<?= MONEY_MAX ?>" inputmode="decimal" name="cost_price" required
                           value="<?= e(old('cost_price', isset($product['cost_price']) ? (string) $product['cost_price'] : '')) ?>">
                </label>
            <?php endif; ?>
            <label class="field">
                <span><?= e(t('sell_price')) ?></span>
                <input type="number" step="0.01" min="0" max="<?= MONEY_MAX ?>" inputmode="decimal" name="sell_price" required
                       value="<?= e(old('sell_price', isset($product['sell_price']) ? (string) $product['sell_price'] : '')) ?>">
                <span class="field-warning" id="sell-below-cost-hint" hidden><?= e(t('sell_below_cost_hint')) ?></span>
            </label>
        </div>

        <div class="field-row">
            <?php if ($hasActiveVariants): ?>
                <div class="field">
                    <span><?= e(t('variant_stock_total_label')) ?></span>
                    <strong><?= e(format_qty($variantStockTotal)) ?> <?= e($product['unit'] ?? '') ?></strong>
                    <span class="muted"><?= e(t('variant_stock_managed_hint')) ?></span>
                    <?php if ($parentLeftover > 0): ?>
                        <label class="field" style="margin-top:8px;">
                            <span><?= e(t('parent_stock_leftover_field')) ?></span>
                            <input type="number" step="any" min="0" max="<?= e((string) $parentLeftover) ?>" inputmode="decimal" name="stock_qty" id="product-stock-input"
                                   value="<?= e(old('stock_qty', (string) $parentLeftover)) ?>">
                        </label>
                    <?php endif; ?>
                </div>
            <?php else: ?>
            <label class="field">
                <span><?= e(t('stock_qty')) ?></span>
                <input type="number" step="any" min="0" inputmode="decimal" name="stock_qty" id="product-stock-input" required
                       value="<?= e(old('stock_qty', isset($product['stock_qty']) ? (string) $product['stock_qty'] : '')) ?>">
                <span class="field-warning" id="stock-whole-hint" hidden></span>
            </label>
            <?php endif; ?>
            <label class="field">
                <span><?= e(t('barcode')) ?> (<?= e(t('optional')) ?>)</span>
                <div class="scan-input-row">
                    <input type="text" name="barcode" id="product-barcode-input" maxlength="64" value="<?= e(old('barcode', $product['barcode'] ?? '')) ?>">
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
<script nonce="<?= e(csp_nonce()) ?>">
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

    // Live versions of two server-side checks: a sell price below cost
    // (allowed, but flagged) and a fractional stock for a counted unit.
    (function () {
        var fractionalUnits = <?= json_encode(FRACTIONAL_UNITS, JSON_UNESCAPED_UNICODE) ?>;
        var costInput = document.querySelector('input[name="cost_price"]');
        var sellInput = document.querySelector('input[name="sell_price"]');
        var belowCostHint = document.getElementById('sell-below-cost-hint');
        var unitInput = document.querySelector('input[name="unit"]');
        var stockInput = document.getElementById('product-stock-input');
        var wholeHint = document.getElementById('stock-whole-hint');
        var wholeTpl = <?= json_encode(t('qty_must_be_whole'), JSON_UNESCAPED_UNICODE) ?>;

        function checkPrices() {
            if (!costInput || !sellInput || !belowCostHint) { return; }
            var cost = parseFloat(costInput.value);
            var sell = parseFloat(sellInput.value);
            belowCostHint.hidden = !(cost > 0 && sell >= 0 && sell < cost);
        }
        function checkStock() {
            if (!stockInput || !wholeHint || !unitInput) { return; }
            var unit = (unitInput.value || 'dona').trim().toLowerCase();
            var fractional = fractionalUnits.indexOf(unit) !== -1;
            var qty = parseFloat(stockInput.value);
            var bad = !fractional && !isNaN(qty) && Math.abs(qty - Math.round(qty)) > 0.000001;
            wholeHint.textContent = wholeTpl.replace(':unit', unit);
            wholeHint.hidden = !bad;
            stockInput.setCustomValidity(bad ? wholeHint.textContent : '');
        }
        [costInput, sellInput].forEach(function (el) { if (el) { el.addEventListener('input', checkPrices); } });
        [unitInput, stockInput].forEach(function (el) { if (el) { el.addEventListener('input', checkStock); } });
        checkPrices();
        checkStock();
    })();
</script>

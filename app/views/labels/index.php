<?php use App\Core\Auth; ?>
<?php $pageTitle = t('labels_title'); ?>
<section class="page-head page-head-row no-print">
    <div>
        <h1><?= e(t('labels_title')) ?></h1>
        <p class="muted"><?= e(t('labels_hint')) ?></p>
    </div>
    <div class="row-actions">
        <?php if (Auth::isOwner()): ?>
            <a href="/labels/settings" class="btn btn-ghost"><?= e(t('label_settings_title')) ?></a>
        <?php endif; ?>
        <a href="/products" class="btn btn-ghost"><?= e(t('products')) ?></a>
    </div>
</section>

<form id="label-csrf" class="no-print"><?= csrf_field() ?></form>

<div class="label-layout no-print">
    <div class="label-main">
        <div class="card label-card">
            <h2><?= e(t('label_products')) ?></h2>
            <div id="label-items"></div>
            <div class="label-add">
                <input type="search" id="label-search" autocomplete="off" placeholder="<?= e(t('label_add_placeholder')) ?>">
                <div id="label-search-results" class="label-results" hidden></div>
            </div>
            <div class="row-actions">
                <button type="button" class="btn btn-ghost btn-sm" id="label-qty-stock"><?= e(t('label_qty_stock')) ?></button>
                <button type="button" class="btn btn-ghost btn-sm" id="label-qty-one"><?= e(t('label_qty_one')) ?></button>
            </div>
        </div>

        <div class="card label-card">
            <h2><?= e(t('label_size')) ?></h2>
            <div class="label-chips" id="label-sizes"></div>
            <div class="label-custom" id="label-custom" hidden>
                <label class="field"><span><?= e(t('label_width')) ?></span><input type="number" id="label-w" min="15" max="200" inputmode="numeric"></label>
                <label class="field"><span><?= e(t('label_height')) ?></span><input type="number" id="label-h" min="15" max="200" inputmode="numeric"></label>
                <label class="field"><span><?= e(t('label_size_name')) ?></span><input type="text" id="label-size-name" maxlength="30" placeholder="<?= e(t('label_size_name_ph')) ?>"></label>
                <button type="button" class="btn btn-ghost btn-sm" id="label-size-save"><?= e(t('label_size_save')) ?></button>
            </div>
        </div>

        <div class="card label-card">
            <h2><?= e(t('label_show')) ?></h2>
            <div class="label-checks">
                <label class="permission-check"><input type="checkbox" id="opt-name" checked><span><?= e(t('label_opt_name')) ?></span></label>
                <label class="permission-check"><input type="checkbox" id="opt-variant" checked><span><?= e(t('label_opt_variant')) ?></span></label>
                <label class="permission-check"><input type="checkbox" id="opt-price" checked><span><?= e(t('label_opt_price')) ?></span></label>
                <label class="permission-check"><input type="checkbox" id="opt-digits" checked><span><?= e(t('label_opt_digits')) ?></span></label>
                <label class="permission-check"><input type="checkbox" id="opt-shop"><span><?= e(t('label_opt_shop')) ?></span></label>
            </div>
            <div class="label-mode" id="label-mode" hidden>
                <span class="muted"><?= e(t('label_price_which')) ?></span>
                <label class="radio-pill"><input type="radio" name="label-mode" value="retail" checked><span><?= e(t('price_mode_retail')) ?></span></label>
                <label class="radio-pill"><input type="radio" name="label-mode" value="wholesale"><span><?= e(t('price_mode_wholesale')) ?></span></label>
                <label class="radio-pill"><input type="radio" name="label-mode" value="both"><span><?= e(t('label_price_both')) ?></span></label>
            </div>
            <?php if (Auth::isOwner()): ?>
                <button type="button" class="btn btn-ghost btn-sm" id="label-save-default"><?= e(t('label_save_default')) ?></button>
                <span class="muted" id="label-default-status" role="status"></span>
            <?php endif; ?>
        </div>
    </div>

    <aside class="label-side">
        <div class="card label-card label-preview-card">
            <h2><?= e(t('label_preview')) ?></h2>
            <div class="label-preview-wrap"><div id="label-preview"></div></div>
            <p class="muted" id="label-preview-note"></p>
            <p class="muted" id="label-total"></p>
            <p class="field-error" id="label-warning" hidden></p>
            <p class="muted label-hint" id="label-hint"></p>
            <div class="row-actions">
                <button type="button" class="btn btn-primary" id="label-print"><?= e(t('print')) ?></button>
            </div>
        </div>
    </aside>
</div>

<div id="label-sheet" class="label-sheet" aria-hidden="true"></div>

<script nonce="<?= e(csp_nonce()) ?>">
    window.HM_LABELS = <?= $labelData ?>;
    window.HM_LABEL_I18N = <?= json_encode([
        'noItems' => t('label_no_items'),
        'noBarcode' => t('label_no_barcode'),
        'makeCode' => t('generate_barcode'),
        'noWholesale' => t('label_no_wholesale'),
        'remove' => t('quick_remove_row'),
        'custom' => t('label_size_other'),
        'wholesaleMark' => t('wholesale_short'),
        'retailMark' => t('price_mode_retail'),
        'total' => t('label_total'),
        'hint' => t('label_printer_hint'),
        'needCodes' => t('label_need_codes'),
        'confirmWholesale' => t('label_confirm_wholesale'),
        'printAnyway' => t('label_print_anyway'),
        'tooSmall' => t('label_too_small'),
        'savedDefault' => t('label_settings_saved'),
        'currency' => t('currency_symbol'),
        'delete' => t('delete'),
    ], JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>;
</script>
<style id="label-page-style"></style>
<script src="<?= asset('js/vendor/JsBarcode.all.min.js') ?>" defer></script>
<script src="<?= asset('js/barcode-tools.js') ?>" defer></script>
<script src="<?= asset('js/labels.js') ?>" defer></script>

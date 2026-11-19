<?php
$pageTitle = t('new_sale');
$pageKind = 'till';

$posIcon = static function (string $name): string {
    $paths = [
        'cash' => '<rect x="2.5" y="6" width="19" height="12" rx="2"/><circle cx="12" cy="12" r="2.6"/><path d="M6 9.5v5M18 9.5v5"/>',
        'card' => '<rect x="2.5" y="5.5" width="19" height="13" rx="2"/><path d="M2.5 10h19"/><path d="M6 15h4"/>',
        'qr' => '<rect x="3.5" y="3.5" width="6.5" height="6.5" rx="1"/><rect x="14" y="3.5" width="6.5" height="6.5" rx="1"/><rect x="3.5" y="14" width="6.5" height="6.5" rx="1"/><path d="M14 14h3v3h-3z"/><path d="M20.5 14v.01M20.5 17.5v3h-3"/>',
        'debt' => '<circle cx="9" cy="8" r="3.2"/><path d="M2.5 19c0-3.3 2.9-6 6.5-6 1.2 0 2.400.3 3.400.9"/><circle cx="17.5" cy="16.5" r="4"/><path d="M17.5 14.500v2l1.300 1"/>',
        'menu' => '<path d="M4 7h16M4 12h16M4 17h16"/>',
        'search' => '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.300-4.300"/>',
        'trash' => '<path d="M4 7h16"/><path d="M9 7V4.500h6V7"/><path d="M6.500 7l1 12.500h9l1-12.500"/>',
        'check' => '<path d="m5 12.500 4.500 4.500L19 7.500"/>',
        'user' => '<circle cx="12" cy="8" r="3.500"/><path d="M5 20c0-3.900 3.100-7 7-7s7 3.100 7 7"/>',
        'cart' => '<circle cx="9" cy="20" r="1.400"/><circle cx="17" cy="20" r="1.400"/><path d="M2.500 3h2.400l2.100 11.200A2 2 0 0 0 9 16h8.200a2 2 0 0 0 2-1.600L21 7H6"/>',
    ];
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' . ($paths[$name] ?? '') . '</svg>';
};
?>
<?php /* POS mode's own slim top bar (hidden otherwise): the brand, whose till this is, the time, and the way back to the menu. */ ?>
<div class="pos-bar">
    <img class="pos-bar-logo logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="<?= e(t('app_name')) ?>">
    <img class="pos-bar-logo logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="<?= e(t('app_name')) ?>">
    <?php if (!empty($shopName)): ?><span class="pos-bar-shop"><?= e($shopName) ?></span><?php endif; ?>
    <span class="pos-bar-spacer"></span>
    <span class="pos-bar-chip"><?= $posIcon('user') ?><?= e($user['full_name'] ?? '') ?></span>
    <span class="pos-bar-clock" id="pos-clock"></span>
    <button type="button" class="btn btn-ghost pos-exit js-pos-toggle" title="<?= e(t('pos_exit_hint')) ?>"><?= $posIcon('menu') ?><?= e(t('pos_exit')) ?></button>
</div>
<section class="page-head page-head-row">
    <h1><?= e(t('new_sale')) ?></h1>
    <button type="button" class="btn btn-primary btn-sm pos-mode-toggle js-pos-toggle"
            title="<?= e(t('pos_mode_hint')) ?>"><?= e(t('pos_mode')) ?></button>
</section>

<?php if (!empty($lastSale)): ?>
<div class="last-sale" id="last-sale">
    <div class="last-sale-main">
        <strong>✓ <?= e(t('last_sale_done', ['number' => receipt_number($lastSale)])) ?></strong>
        <span class="last-sale-total"><?= e(t('total')) ?>: <b><?= money((float) $lastSale['total']) ?></b></span>
        <?php if ($lastChange > 0): ?>
            <span class="last-sale-change"><?= e(t('change_due')) ?>: <b><?= money($lastChange) ?></b></span>
        <?php endif; ?>
    </div>
    <div class="row-actions">
        <button type="button" class="btn btn-ghost btn-sm" data-copy="<?= e((string) (int) round((float) $lastSale['total'])) ?>"
                data-copied="<?= e(t('copied')) ?>"><?= e(t('copy_amount')) ?></button>
        <?php if ($isDesktop): ?>
            <button type="button" class="btn btn-ghost btn-sm" id="receipt-print-btn"
                    data-print-path="/sales/<?= (int) $lastSale['id'] ?>/print" data-paper="<?= (int) $paperWidth ?>"
                    data-receipt-url="/sales/<?= (int) $lastSale['id'] ?>" data-autoprint="1"><?= e(t('print')) ?></button>
        <?php endif; ?>
        <a href="/sales/<?= (int) $lastSale['id'] ?>" class="btn btn-ghost btn-sm"><?= e(t('view_receipt')) ?></a>
    </div>
</div>
<?php if ($isDesktop): ?>
<div class="print-status" id="print-status" role="status" aria-live="polite" hidden
     data-sent="<?= e(t('printer_sent')) ?>" data-printing="<?= e(t('printer_printing')) ?>"
     data-failed="<?= e(t('printer_failed')) ?>" data-no-printer="<?= e(t('printer_not_chosen')) ?>"
     data-dialog="<?= e(t('printer_use_dialog')) ?>" data-settings="<?= e(t('printer_settings_link')) ?>"></div>
<?php endif; ?>
<?php endif; ?>

<?php if (!$hasProducts): ?>
    <div class="card">
        <p class="muted"><?= e(t('no_active_products_for_sale')) ?></p>
        <a href="/products/create" class="btn btn-primary" style="margin-top:14px;"><?= e(t('add_product')) ?></a>
    </div>
<?php else: ?>
<div class="pos-layout">
    <div class="pos-products">
        <label class="sr-only" for="product-search"><?= e(t('search_products_placeholder')) ?></label>
        <div class="scan-input-row" style="margin-bottom:12px;">
            <div class="pos-search-wrap">
                <?= $posIcon('search') ?>
                <input type="text" id="product-search" class="pos-search" placeholder="<?= e(t('search_products_placeholder')) ?>" autofocus>
            </div>
            <button type="button" class="scan-btn barcode-scan-btn" data-target="#product-search" aria-label="<?= e(t('scan_barcode')) ?>" title="<?= e(t('scan_barcode')) ?>">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8V5a1 1 0 0 1 1-1h3M20 8V5a1 1 0 0 0-1-1h-3M4 16v3a1 1 0 0 0 1 1h3M20 16v3a1 1 0 0 1-1 1h-3M6 8v8M10 8v8M14 8v8M18 8v8"/></svg>
            </button>
        </div>
        <?php if (!empty($tillCategories)): ?>
            <div class="pos-categories" id="pos-categories" role="group" aria-label="<?= e(t('categories')) ?>">
                <button type="button" class="pos-category active" data-category="" aria-pressed="true"><?= e(t('till_all_categories')) ?></button>
                <?php foreach ($tillCategories as $catId => $catName): ?>
                    <button type="button" class="pos-category" data-category="<?= (int) $catId ?>" aria-pressed="false"><?= e($catName) ?></button>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
        <div id="product-list" class="pos-product-list"></div>
    </div>

    <div class="pos-cart card">
        <div class="cart-head">
            <h2 class="cart-title"><?= e(t('cart')) ?><span class="cart-count" id="cart-count" hidden></span></h2>
            <button type="button" class="cart-clear" id="cart-clear" hidden><?= $posIcon('trash') ?><?= e(t('clear_cart')) ?></button>
        </div>
        <div id="cart-list" class="cart-list"></div>
        <div id="cart-empty-msg" class="cart-empty">
            <?= $posIcon('cart') ?>
            <p class="muted"><?= e(t('cart_empty_hint')) ?></p>
        </div>

        <form id="sale-form" method="post" action="/sales">
            <?= csrf_field() ?>
            <input type="hidden" name="cart" id="cart-field">
            <input type="hidden" name="price_type" id="price-type-field" value="retail">

            <div class="cart-totals">
                <label class="field"<?= $canDiscount ? '' : ' hidden' ?>>
                    <span><?= e(t('discount')) ?></span>
                    <div class="discount-input-row">
                        <input type="number" id="discount-input" min="0" max="<?= MONEY_MAX ?>" data-max-amount="<?= MONEY_MAX ?>" step="0.01" value="0" inputmode="decimal">
                        <div class="discount-mode-toggle" role="group" aria-label="<?= e(t('discount_mode_toggle_label')) ?>">
                            <button type="button" class="discount-mode-btn active" data-mode="amount"><?= e(t('discount_mode_fixed')) ?></button>
                            <button type="button" class="discount-mode-btn" data-mode="percent">%</button>
                        </div>
                    </div>
                    <span class="muted" id="discount-computed-label" style="display:none;"></span>
                    <span class="field-error" id="discount-error" hidden><?= e(t('discount_too_large')) ?></span>
                </label>
                <?php if ($wholesaleEnabled): ?>
                    <div class="price-mode-toggle" role="group" aria-label="<?= e(t('price_mode_label')) ?>">
                        <button type="button" class="price-mode-btn active" data-price-mode="retail" aria-pressed="true"><?= e(t('price_mode_retail')) ?></button>
                        <button type="button" class="price-mode-btn" data-price-mode="wholesale" aria-pressed="false"><?= e(t('price_mode_wholesale')) ?></button>
                    </div>
                <?php endif; ?>
                <div class="cart-total-row">
                    <span><?= e(t('total')) ?><span class="wholesale-badge" id="wholesale-badge" hidden><?= e(t('wholesale_short')) ?></span></span>
                    <strong id="cart-total">0 <?= e(t('currency_symbol')) ?></strong>
                </div>
            </div>
            <input type="hidden" name="discount" id="discount-field" value="0">

            <button type="button" class="pos-extra-toggle" id="pos-extra-toggle"><?= e(t('pos_extra')) ?></button>

            <div class="payment-types">
                <button type="button" class="payment-btn active" data-preset="naqd"><?= $posIcon('cash') ?><span><?= e(t('payment_naqd')) ?></span></button>
                <button type="button" class="payment-btn" data-preset="karta"><?= $posIcon('card') ?><span><?= e(t('payment_karta')) ?></span></button>
                <button type="button" class="payment-btn" data-preset="qarz"><?= $posIcon('debt') ?><span><?= e(t('payment_qarz')) ?></span></button>
            </div>

            <div class="field-row pay-amounts">
                <label class="field">
                    <span><?= e(t('payment_naqd')) ?></span>
                    <input type="number" id="naqd-amount-input" min="0" max="<?= MONEY_MAX ?>" step="0.01" value="0" inputmode="decimal">
                </label>
                <label class="field">
                    <span><?= e(t('payment_karta')) ?></span>
                    <input type="number" id="karta-amount-input" min="0" max="<?= MONEY_MAX ?>" step="0.01" value="0" inputmode="decimal">
                </label>
            </div>
            <div class="cart-change-row" id="change-row" hidden>
                <span><?= e(t('change_due')) ?></span>
                <strong id="change-amount"></strong>
            </div>
            <input type="hidden" name="naqd_amount" id="naqd-amount-field" value="0">
            <input type="hidden" name="karta_amount" id="karta-amount-field" value="0">
            <input type="hidden" name="qr_amount" id="qr-amount-field" value="0">
            <input type="hidden" id="qr-amount-input" value="0">

            <div id="debt-fields" class="stack" style="display:none;">
                <div class="field">
                    <span><?= e(t('customer')) ?></span>
                    <div class="customer-combo">
                        <input type="text" id="customer-search" autocomplete="off" spellcheck="false"
                               placeholder="<?= e(t('customer_search_placeholder')) ?>"
                               role="combobox" aria-expanded="false" aria-controls="customer-results" aria-autocomplete="list">
                        <button type="button" class="customer-clear" id="customer-clear" aria-label="<?= e(t('close')) ?>" hidden>✕</button>
                        <ul class="customer-results" id="customer-results" role="listbox" hidden></ul>
                    </div>
                    <input type="hidden" id="customer-select" value="">
                </div>
                <div id="new-customer-fields" class="field-row" style="display:none;">
                    <label class="field">
                        <span><?= e(t('full_name_label')) ?></span>
                        <input type="text" id="new-customer-name">
                    </label>
                    <label class="field">
                        <span><?= e(t('phone')) ?> (<?= e(t('optional')) ?>)</span>
                        <input type="tel" id="new-customer-phone" placeholder="+998 90 123 45 67" inputmode="tel">
                    </label>
                </div>
                <p class="muted" id="debt-remaining-label"></p>
            </div>
            <input type="hidden" name="customer_id" id="customer-id-field" value="">
            <input type="hidden" name="customer_name" id="customer-name-field" value="">
            <input type="hidden" name="customer_phone" id="customer-phone-field" value="">

            <div class="alert alert-error" id="sale-form-error" role="alert" hidden></div>
            <button type="submit" id="complete-sale-btn" class="btn btn-primary btn-block"><?= $posIcon('check') ?><?= e(t('complete_sale')) ?></button>
            <p class="muted pos-shortcuts"><?= e(t('pos_shortcuts_hint')) ?></p>
        </form>
    </div>
</div>

<?php
// If the previous submission failed validation (e.g. insufficient stock or a
// bad discount value), repopulate the cart/JS state from the flashed old
// input instead of starting the cashier over with an empty cart.
$oldCart = json_decode(old('cart', '[]'), true);
if (!is_array($oldCart)) {
    $oldCart = [];
}
?>
<script nonce="<?= e(csp_nonce()) ?>">
    window.HM_PRODUCTS = <?= $productsJson ?>;
    window.HM_EMPTY_BARCODES = <?= $emptyBarcodesJson ?>;
    window.HM_CUSTOMERS = <?= $customersJson ?>;
    window.HM_I18N = {
        noResults: <?= json_encode(t('no_products_found'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        moreProducts: <?= json_encode(t('pos_more_products'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        debtRemainingLabel: <?= json_encode(t('debt_remaining_label'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        creditLimitPosLabel: <?= json_encode(t('credit_limit_pos_label'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        creditLimitExceededWarning: <?= json_encode(t('credit_limit_exceeded_warning'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        confirmExceedCreditLimit: <?= json_encode(t('confirm_exceed_credit_limit'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        stockLimitReached: <?= json_encode(t('stock_limit_reached'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        decreaseQty: <?= json_encode(t('decrease_qty'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        increaseQty: <?= json_encode(t('increase_qty'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        removeFromCart: <?= json_encode(t('remove_from_cart'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        editQty: <?= json_encode(t('edit_qty_label'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        discountEqualsLabel: <?= json_encode(t('discount_equals_label'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        pickVariantHint: <?= json_encode(t('pick_variant_hint'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        scanNotFound: <?= json_encode(t('scan_not_found'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        scanOutOfStock: <?= json_encode(t('scan_out_of_stock'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        scanRecordPurchase: <?= json_encode(t('record_purchase'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        scanAddProduct: <?= json_encode(t('scan_add_product'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        customerNew: <?= json_encode(t('customer_new_option'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        customerDebt: <?= json_encode(t('customer_debt_short'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        customerNone: <?= json_encode(t('customer_no_match'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        emptyCart: <?= json_encode(t('cart_empty_submit'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        discountTooLarge: <?= json_encode(t('discount_too_large'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        currency: <?= json_encode(t('currency_symbol'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>
    };
    window.HM_CAN_DISCOUNT = <?= $canDiscount ? 'true' : 'false' ?>;
    window.HM_CAN_PRODUCTS = <?= can('products') ? 'true' : 'false' ?>;
    window.HM_OLD_CART = <?= json_encode($oldCart, JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>;
    window.HM_OLD_SALE = {
        discount: <?= json_encode(old('discount', '0'), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        naqdAmount: <?= json_encode(old('naqd_amount', ''), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        kartaAmount: <?= json_encode(old('karta_amount', ''), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        qrAmount: <?= json_encode(old('qr_amount', ''), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        customerId: <?= json_encode(old('customer_id', ''), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        customerName: <?= json_encode(old('customer_name', ''), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        customerPhone: <?= json_encode(old('customer_phone', ''), JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>,
        priceType: <?= json_encode($wholesaleEnabled ? old('price_type', 'retail') : 'retail', JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) ?>
    };
</script>
<script src="<?= asset('js/sale.js') ?>" defer></script>
<?php endif; ?>

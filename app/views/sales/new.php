<?php $pageTitle = t('new_sale'); ?>
<section class="page-head">
    <h1><?= e(t('new_sale')) ?></h1>
</section>

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
            <input type="text" id="product-search" class="pos-search" placeholder="<?= e(t('search_products_placeholder')) ?>" autofocus style="margin-bottom:0;">
            <button type="button" class="scan-btn barcode-scan-btn" data-target="#product-search" aria-label="<?= e(t('scan_barcode')) ?>" title="<?= e(t('scan_barcode')) ?>">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8V5a1 1 0 0 1 1-1h3M20 8V5a1 1 0 0 0-1-1h-3M4 16v3a1 1 0 0 0 1 1h3M20 16v3a1 1 0 0 1-1 1h-3M6 8v8M10 8v8M14 8v8M18 8v8"/></svg>
            </button>
        </div>
        <div id="product-list" class="pos-product-list"></div>
    </div>

    <div class="pos-cart card">
        <h2><?= e(t('cart')) ?></h2>
        <div id="cart-list" class="cart-list"></div>
        <p id="cart-empty-msg" class="muted"><?= e(t('cart_empty_hint')) ?></p>

        <form id="sale-form" method="post" action="/sales">
            <?= csrf_field() ?>
            <input type="hidden" name="cart" id="cart-field">

            <div class="cart-totals">
                <label class="field">
                    <span><?= e(t('discount')) ?></span>
                    <div class="discount-input-row">
                        <input type="number" id="discount-input" min="0" step="0.01" value="0" inputmode="decimal">
                        <div class="discount-mode-toggle" role="group" aria-label="<?= e(t('discount_mode_toggle_label')) ?>">
                            <button type="button" class="discount-mode-btn active" data-mode="amount"><?= e(t('discount_mode_fixed')) ?></button>
                            <button type="button" class="discount-mode-btn" data-mode="percent">%</button>
                        </div>
                    </div>
                    <span class="muted" id="discount-computed-label" style="display:none;"></span>
                </label>
                <div class="cart-total-row">
                    <span><?= e(t('total')) ?></span>
                    <strong id="cart-total">0 so'm</strong>
                </div>
            </div>
            <input type="hidden" name="discount" id="discount-field" value="0">

            <div class="payment-types">
                <button type="button" class="payment-btn active" data-preset="naqd"><?= e(t('payment_naqd')) ?></button>
                <button type="button" class="payment-btn" data-preset="karta"><?= e(t('payment_karta')) ?></button>
                <button type="button" class="payment-btn" data-preset="qarz"><?= e(t('payment_qarz')) ?></button>
            </div>

            <div class="field-row">
                <label class="field">
                    <span><?= e(t('payment_naqd')) ?></span>
                    <input type="number" id="naqd-amount-input" min="0" step="0.01" value="0" inputmode="decimal">
                </label>
                <label class="field">
                    <span><?= e(t('payment_karta')) ?></span>
                    <input type="number" id="karta-amount-input" min="0" step="0.01" value="0" inputmode="decimal">
                </label>
            </div>
            <input type="hidden" name="naqd_amount" id="naqd-amount-field" value="0">
            <input type="hidden" name="karta_amount" id="karta-amount-field" value="0">

            <div id="debt-fields" class="stack" style="display:none;">
                <label class="field">
                    <span><?= e(t('customer')) ?></span>
                    <select id="customer-select">
                        <option value=""><?= e(t('new_customer_option')) ?></option>
                    </select>
                </label>
                <div id="new-customer-fields" class="field-row">
                    <label class="field">
                        <span><?= e(t('full_name_label')) ?></span>
                        <input type="text" id="new-customer-name">
                    </label>
                    <label class="field">
                        <span><?= e(t('phone')) ?> (<?= e(t('optional')) ?>)</span>
                        <input type="text" id="new-customer-phone">
                    </label>
                </div>
                <p class="muted" id="debt-remaining-label"></p>
            </div>
            <input type="hidden" name="customer_id" id="customer-id-field" value="">
            <input type="hidden" name="customer_name" id="customer-name-field" value="">
            <input type="hidden" name="customer_phone" id="customer-phone-field" value="">

            <button type="submit" id="complete-sale-btn" class="btn btn-primary btn-block" disabled><?= e(t('complete_sale')) ?></button>
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
<script>
    window.HM_PRODUCTS = <?= $productsJson ?>;
    window.HM_CUSTOMERS = <?= $customersJson ?>;
    window.HM_I18N = {
        noResults: <?= json_encode(t('no_products_found'), JSON_UNESCAPED_UNICODE) ?>,
        debtRemainingLabel: <?= json_encode(t('debt_remaining_label'), JSON_UNESCAPED_UNICODE) ?>,
        creditLimitPosLabel: <?= json_encode(t('credit_limit_pos_label'), JSON_UNESCAPED_UNICODE) ?>,
        creditLimitExceededWarning: <?= json_encode(t('credit_limit_exceeded_warning'), JSON_UNESCAPED_UNICODE) ?>,
        confirmExceedCreditLimit: <?= json_encode(t('confirm_exceed_credit_limit'), JSON_UNESCAPED_UNICODE) ?>,
        stockLimitReached: <?= json_encode(t('stock_limit_reached'), JSON_UNESCAPED_UNICODE) ?>,
        decreaseQty: <?= json_encode(t('decrease_qty'), JSON_UNESCAPED_UNICODE) ?>,
        increaseQty: <?= json_encode(t('increase_qty'), JSON_UNESCAPED_UNICODE) ?>,
        removeFromCart: <?= json_encode(t('remove_from_cart'), JSON_UNESCAPED_UNICODE) ?>,
        editQty: <?= json_encode(t('edit_qty_label'), JSON_UNESCAPED_UNICODE) ?>,
        discountEqualsLabel: <?= json_encode(t('discount_equals_label'), JSON_UNESCAPED_UNICODE) ?>,
        pickVariantHint: <?= json_encode(t('pick_variant_hint'), JSON_UNESCAPED_UNICODE) ?>,
        currency: "so'm"
    };
    window.HM_OLD_CART = <?= json_encode($oldCart, JSON_UNESCAPED_UNICODE) ?>;
    window.HM_OLD_SALE = {
        discount: <?= json_encode(old('discount', '0'), JSON_UNESCAPED_UNICODE) ?>,
        naqdAmount: <?= json_encode(old('naqd_amount', ''), JSON_UNESCAPED_UNICODE) ?>,
        kartaAmount: <?= json_encode(old('karta_amount', ''), JSON_UNESCAPED_UNICODE) ?>,
        customerId: <?= json_encode(old('customer_id', ''), JSON_UNESCAPED_UNICODE) ?>,
        customerName: <?= json_encode(old('customer_name', ''), JSON_UNESCAPED_UNICODE) ?>,
        customerPhone: <?= json_encode(old('customer_phone', ''), JSON_UNESCAPED_UNICODE) ?>
    };
</script>
<script src="<?= asset('js/sale.js') ?>" defer></script>
<?php endif; ?>

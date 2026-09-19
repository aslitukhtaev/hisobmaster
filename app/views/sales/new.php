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
        <input type="text" id="product-search" class="pos-search" placeholder="<?= e(t('search_products_placeholder')) ?>" autofocus>
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
                    <input type="number" id="discount-input" min="0" step="0.01" value="0" inputmode="decimal">
                </label>
                <div class="cart-total-row">
                    <span><?= e(t('total')) ?></span>
                    <strong id="cart-total">0 so'm</strong>
                </div>
            </div>
            <input type="hidden" name="discount" id="discount-field" value="0">

            <div class="payment-types">
                <button type="button" class="payment-btn active" data-type="naqd"><?= e(t('payment_naqd')) ?></button>
                <button type="button" class="payment-btn" data-type="karta"><?= e(t('payment_karta')) ?></button>
                <button type="button" class="payment-btn" data-type="qarz"><?= e(t('payment_qarz')) ?></button>
            </div>
            <input type="hidden" name="payment_type" id="payment-type-field" value="naqd">

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
                <label class="field">
                    <span><?= e(t('paid_now')) ?></span>
                    <input type="number" id="paid-amount-input" min="0" step="0.01" value="0" inputmode="decimal">
                </label>
                <p class="muted" id="debt-remaining-label"></p>
            </div>
            <input type="hidden" name="paid_amount" id="paid-amount-field" value="0">
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
        stockLimitReached: <?= json_encode(t('stock_limit_reached'), JSON_UNESCAPED_UNICODE) ?>,
        currency: "so'm"
    };
    window.HM_OLD_CART = <?= json_encode($oldCart, JSON_UNESCAPED_UNICODE) ?>;
    window.HM_OLD_SALE = {
        paymentType: <?= json_encode(old('payment_type', 'naqd'), JSON_UNESCAPED_UNICODE) ?>,
        discount: <?= json_encode(old('discount', '0'), JSON_UNESCAPED_UNICODE) ?>,
        paidAmount: <?= json_encode(old('paid_amount', '0'), JSON_UNESCAPED_UNICODE) ?>,
        customerId: <?= json_encode(old('customer_id', ''), JSON_UNESCAPED_UNICODE) ?>,
        customerName: <?= json_encode(old('customer_name', ''), JSON_UNESCAPED_UNICODE) ?>,
        customerPhone: <?= json_encode(old('customer_phone', ''), JSON_UNESCAPED_UNICODE) ?>
    };
</script>
<script src="<?= asset('js/sale.js') ?>" defer></script>
<?php endif; ?>

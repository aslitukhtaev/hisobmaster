<section class="page-head">
    <h1><?= e(t('profile')) ?></h1>
    <p class="muted"><?= e($user['full_name'] ?? '') ?></p>
</section>

<div class="card form-card">
    <form method="post" action="/profile" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('full_name_label')) ?></span>
            <input type="text" name="full_name" required value="<?= e(old('full_name', $user['full_name'] ?? '')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('phone')) ?></span>
            <input type="text" name="phone" value="<?= e(old('phone', $user['phone'] ?? '')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('login')) ?></span>
            <input type="text" name="login" required value="<?= e(old('login', $user['login'] ?? '')) ?>">
        </label>

        <hr class="divider">
        <p class="muted"><?= e(t('change_password_optional')) ?></p>

        <label class="field">
            <span><?= e(t('current_password')) ?></span>
            <input type="password" name="current_password" autocomplete="current-password">
        </label>
        <label class="field">
            <span><?= e(t('new_password')) ?></span>
            <input type="password" name="new_password" autocomplete="new-password">
        </label>
        <label class="field">
            <span><?= e(t('confirm_password')) ?></span>
            <input type="password" name="confirm_password" autocomplete="new-password">
        </label>

        <button type="submit" class="btn btn-primary btn-block"><?= e(t('save')) ?></button>
    </form>
</div>

<?php if (!empty($shop)): ?>
<section class="page-head" style="margin-top:24px;">
    <h1><?= e(t('shop_settings')) ?></h1>
    <p class="muted"><?= e(t('shop_settings_hint')) ?></p>
</section>

<div class="card form-card">
    <form method="post" action="/profile/shop" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('shop_name')) ?></span>
            <input type="text" name="shop_name" required value="<?= e(old('shop_name', $shop['name'])) ?>">
        </label>
        <label class="field">
            <span><?= e(t('address')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="shop_address" value="<?= e(old('shop_address', $shop['address'] ?? '')) ?>">
        </label>
        <div class="field">
            <span><?= e(t('receipt_width_label')) ?></span>
            <div class="payment-types">
                <label class="radio-pill">
                    <input type="radio" name="receipt_printer_width" value="80" <?= (int) $shop['receipt_printer_width'] === 80 ? 'checked' : '' ?>>
                    <span>80mm</span>
                </label>
                <label class="radio-pill">
                    <input type="radio" name="receipt_printer_width" value="58" <?= (int) $shop['receipt_printer_width'] === 58 ? 'checked' : '' ?>>
                    <span>58mm</span>
                </label>
            </div>
        </div>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('save')) ?></button>
    </form>
</div>
<?php endif; ?>

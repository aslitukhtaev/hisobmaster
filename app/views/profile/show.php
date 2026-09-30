<?php $pageTitle = t('profile'); ?>
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

<?php if ($devices !== null): ?>
<section class="page-head" style="margin-top:24px;" id="devices">
    <h1><?= e(t('devices_signed_in_title')) ?></h1>
    <p class="muted"><?= e(t('devices_signed_in_hint')) ?></p>
</section>

<div class="card form-card">
    <?php if ($devices === [] && $telegramLinks === []): ?>
        <p class="muted"><?= e(t('devices_none')) ?></p>
    <?php else: ?>
        <ul class="device-list">
            <?php foreach ($devices as $device): $isCurrent = $device['selector'] === $currentSelector; ?>
                <li class="device-row">
                    <div class="device-info">
                        <strong><?= e(device_label($device['user_agent'])) ?></strong>
                        <?php if ($isCurrent): ?><span class="device-current"><?= e(t('device_this')) ?></span><?php endif; ?>
                        <div class="muted"><?= e(t('device_last_used', ['date' => local_datetime($device['last_used_at'])])) ?></div>
                    </div>
                    <?php if (!$isCurrent): ?>
                        <form method="post" action="/profile/devices/<?= (int) $device['id'] ?>/remove" data-confirm="<?= e(t('device_remove_confirm')) ?>">
                            <?= csrf_field() ?>
                            <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('device_remove')) ?></button>
                        </form>
                    <?php endif; ?>
                </li>
            <?php endforeach; ?>
            <?php foreach ($telegramLinks as $link): $isCurrent = $currentTelegramId !== null && (int) $link['telegram_user_id'] === (int) $currentTelegramId; ?>
                <li class="device-row">
                    <div class="device-info">
                        <strong>Telegram<?= $link['telegram_name'] ? ': ' . e($link['telegram_name']) : '' ?></strong>
                        <?php if ($isCurrent): ?><span class="device-current"><?= e(t('device_this')) ?></span><?php endif; ?>
                        <div class="muted"><?= e(t('device_last_used', ['date' => local_datetime($link['last_used_at'])])) ?></div>
                    </div>
                    <?php if (!$isCurrent): ?>
                        <form method="post" action="/profile/telegram/<?= (int) $link['id'] ?>/remove" data-confirm="<?= e(t('device_remove_confirm')) ?>">
                            <?= csrf_field() ?>
                            <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('device_remove')) ?></button>
                        </form>
                    <?php endif; ?>
                </li>
            <?php endforeach; ?>
        </ul>
    <?php endif; ?>
    <form method="post" action="/profile/devices/sign-out-others" data-confirm="<?= e(t('devices_sign_out_others_confirm')) ?>" style="margin-top:14px;">
        <?= csrf_field() ?>
        <button type="submit" class="btn btn-ghost btn-block"><?= e(t('devices_sign_out_others')) ?></button>
    </form>
</div>
<?php endif; ?>

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
        <label class="field">
            <span><?= e(t('low_stock_threshold_default_label')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="number" step="0.01" min="0" inputmode="decimal" name="low_stock_threshold_default"
                   value="<?= e(old('low_stock_threshold_default', $lowStockThresholdDefault ?? '')) ?>">
        </label>
        <p class="muted"><?= e(t('low_stock_threshold_default_hint')) ?></p>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('save')) ?></button>
    </form>
</div>
<?php endif; ?>

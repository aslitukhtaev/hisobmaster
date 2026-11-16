<?php
$pageTitle = t('signup_title');
$providerLabel = $identity['provider'] === 'google' ? 'Google' : 'Telegram';
$who = $identity['email'] ?? (!empty($identity['username']) ? '@' . $identity['username'] : ($identity['name'] ?? ''));
?>
<div class="auth-card">
    <div class="auth-brand">
        <img class="auth-logo logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="<?= e(t('app_name')) ?>">
        <img class="auth-logo logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="<?= e(t('app_name')) ?>">
    </div>

    <div class="signup-head">
        <span class="signup-badge"><?= e(t('signup_trial_badge', ['days' => $trialDays])) ?></span>
        <h1><?= e(t('signup_title')) ?></h1>
        <p class="muted"><?= e(t('signup_as', ['provider' => $providerLabel, 'who' => $who])) ?></p>
    </div>

    <?php require BASE_PATH . '/app/views/partials/flash.php'; ?>

    <form method="post" action="/signup" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('signup_shop_name')) ?></span>
            <input type="text" name="shop_name" required maxlength="120" autofocus value="<?= e(old('shop_name')) ?>" placeholder="<?= e(t('signup_shop_placeholder')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('signup_your_name')) ?></span>
            <input type="text" name="full_name" required maxlength="120" autocomplete="name" value="<?= e(old('full_name', $identity['name'] ?? '')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('phone')) ?></span>
            <input type="tel" name="phone" required inputmode="tel" autocomplete="tel" placeholder="+998 90 123 45 67" value="<?= e(old('phone')) ?>">
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('signup_submit', ['days' => $trialDays])) ?></button>
        <p class="muted signup-note"><?= e(t('signup_note')) ?></p>
    </form>
    <p class="signup-back"><a href="/login"><?= e(t('back')) ?></a></p>
</div>

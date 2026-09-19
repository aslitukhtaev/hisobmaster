<?php $pageTitle = t('login_button'); ?>
<div class="auth-card">
    <div class="auth-brand">
        <img class="auth-logo logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="<?= e(t('app_name')) ?>">
        <img class="auth-logo logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="<?= e(t('app_name')) ?>">
        <p class="muted"><?= e(t('tagline')) ?></p>
    </div>

    <?php require BASE_PATH . '/app/views/partials/flash.php'; ?>

    <form method="post" action="/login" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('login')) ?></span>
            <input type="text" name="login" autocomplete="username" required autofocus value="<?= e(old('login')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('password')) ?></span>
            <input type="password" name="password" autocomplete="current-password" required>
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('login_button')) ?></button>
    </form>
</div>

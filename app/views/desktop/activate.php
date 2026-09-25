<?php $pageTitle = t('desktop_activate_title'); ?>
<div class="auth-card">
    <div class="auth-brand">
        <img class="auth-logo logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="<?= e(t('app_name')) ?>">
        <img class="auth-logo logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="<?= e(t('app_name')) ?>">
        <p class="muted"><?= e(t('desktop_activate_title')) ?></p>
    </div>

    <p class="muted" style="margin-bottom:14px;font-size:.88rem;"><?= e(t('desktop_activate_hint')) ?></p>

    <?php require BASE_PATH . '/app/views/partials/flash.php'; ?>

    <form method="post" action="/desktop/activate" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('login')) ?></span>
            <input type="text" name="login" autocomplete="username" required autofocus value="<?= e(old('login')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('password')) ?></span>
            <input type="password" name="password" autocomplete="current-password" required>
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('desktop_activate_button')) ?></button>
    </form>
    <p class="muted" style="margin-top:14px;font-size:.78rem;text-align:center;"><?= e(preg_replace('#^https?://#', '', $server)) ?></p>
</div>

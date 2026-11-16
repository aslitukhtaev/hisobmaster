<?php $pageTitle = t('login_button'); ?>
<div class="auth-card">
    <div class="auth-brand">
        <img class="auth-logo logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="<?= e(t('app_name')) ?>">
        <img class="auth-logo logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="<?= e(t('app_name')) ?>">
        <p class="muted"><?= e(t('tagline')) ?></p>
    </div>

    <?php require BASE_PATH . '/app/views/partials/flash.php'; ?>

    <?php if (!empty($googleLogin) || !empty($telegramLogin)): ?>
        <div class="social-login">
            <?php if (!empty($googleLogin)): ?>
                <a class="btn btn-block social-btn social-google" href="/auth/google" data-no-prefetch>
                    <svg viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"/><path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z"/></svg>
                    <span><?= e(t('login_with_google')) ?></span>
                </a>
            <?php endif; ?>
            <?php if (!empty($telegramLogin)): ?>
                <a class="btn btn-block social-btn social-telegram js-telegram-login" href="/auth/telegram" data-no-prefetch>
                    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="12" fill="#29a9eb"/><path fill="#fff" d="M5.4 11.8 16.9 7.4c.5-.2 1 .1.8.9l-2 9.3c-.1.6-.5.8-1 .5l-3-2.2-1.4 1.4c-.2.2-.3.3-.6.3l.2-3.1 5.6-5c.2-.2 0-.3-.3-.1l-6.9 4.3-3-.9c-.6-.2-.7-.6.1-1z"/></svg>
                    <span><?= e(t('login_with_telegram')) ?></span>
                </a>
            <?php endif; ?>
            <p class="social-trial"><?= e(t('signup_trial_hint', ['days' => App\Models\Shop::TRIAL_DAYS])) ?></p>
        </div>
        <div class="auth-divider"><span><?= e(t('or_with_password')) ?></span></div>
    <?php endif; ?>

    <p class="muted telegram-login-status" id="telegram-login-status" hidden><?= e(t('telegram_signing_in')) ?></p>

    <form method="post" action="/login" class="stack"<?= !empty($telegramAuto) ? ' data-telegram-auto="1"' : '' ?>>
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('login')) ?></span>
            <input type="text" name="login" autocomplete="username" required autofocus value="<?= e(old('login')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('password')) ?></span>
            <input type="password" name="password" autocomplete="current-password" required>
        </label>
        <label class="permission-check">
            <input type="checkbox" name="remember" value="1" checked>
            <span><?= e(t('remember_me')) ?></span>
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('login_button')) ?></button>
    </form>
    <a class="android-download" href="<?= e(ANDROID_DOWNLOAD_URL) ?>" download data-android-only hidden><?= e(t('lp_android_download')) ?> ↓</a>
</div>
<script nonce="<?= e(csp_nonce()) ?>">
    // Only on an Android phone's browser, not inside the app itself.
    var inApp = /[?&]source=android/.test(location.search) || document.referrer.indexOf('android-app://') === 0;
    try {
        if (inApp) { localStorage.setItem('kassiron-android-app', '1'); }
        inApp = inApp || localStorage.getItem('kassiron-android-app') === '1';
    } catch (e) {}
    if (/Android/i.test(navigator.userAgent) && !inApp && !window.matchMedia('(display-mode: standalone)').matches) {
        document.querySelector('[data-android-only]').hidden = false;
    }
</script>

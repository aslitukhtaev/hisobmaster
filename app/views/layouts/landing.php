<?php
// The public landing page at "/" for visitors (HomeController). Uses
// app.css's tokens and components, so the day/night themes and the language
// switch work as everywhere else.
$appUrl = rtrim((string) env('APP_URL', ''), '/');
$siteUrl = $appUrl !== '' ? $appUrl : (request_is_https() ? 'https://' : 'http://') . ($_SERVER['HTTP_HOST'] ?? 'kassiron.uz');
?>
<!doctype html>
<html lang="<?= e(current_lang()) ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <script nonce="<?= e(csp_nonce()) ?>">
        (function () {
            // Opened from the Telegram bot (its WebApp passes tgWebAppData in
            // the address): there, the visitor is about to sign in.
            if (/tgWebAppData=/.test(window.location.hash)) {
                window.location.replace('/login' + window.location.hash);
                return;
            }
            try {
                var t = localStorage.getItem('kassiron-theme');
                if (t === 'light' || t === 'dark') {
                    document.documentElement.setAttribute('data-theme', t);
                }
            } catch (e) {}
        })();
    </script>
    <title><?= e(t('lp_meta_title')) ?></title>
    <meta name="description" content="<?= e(t('lp_meta_description')) ?>">
    <link rel="canonical" href="<?= e($siteUrl) ?>/">
    <meta property="og:type" content="website">
    <meta property="og:site_name" content="KassirON">
    <meta property="og:title" content="<?= e(t('lp_meta_title')) ?>">
    <meta property="og:description" content="<?= e(t('lp_meta_description')) ?>">
    <meta property="og:url" content="<?= e($siteUrl) ?>/">
    <meta property="og:image" content="<?= e($siteUrl) ?>/assets/img/landing/og.jpg">
    <meta property="og:image:width" content="1200">
    <meta property="og:image:height" content="630">
    <meta property="og:locale" content="<?= current_lang() === 'ru' ? 'ru_RU' : 'uz_UZ' ?>">
    <meta name="twitter:card" content="summary_large_image">
    <link rel="stylesheet" href="<?= asset('css/app.css') ?>">
    <link rel="stylesheet" href="<?= asset('css/landing.css') ?>">
    <link rel="manifest" href="/manifest.json">
    <link rel="icon" href="<?= asset('img/icon-32.png') ?>" type="image/png" sizes="32x32">
    <link rel="icon" href="<?= asset('img/icon-192.png') ?>" type="image/png" sizes="192x192">
    <link rel="apple-touch-icon" href="<?= asset('img/icon-180.png') ?>">
    <meta name="theme-color" content="#059669" media="(prefers-color-scheme: light)">
    <meta name="theme-color" content="#121e2e" media="(prefers-color-scheme: dark)">
</head>
<body class="lp-body">
    <a class="lp-skip" href="#main"><?= e(t('lp_skip_to_content')) ?></a>
    <header class="lp-header">
        <div class="lp-container lp-header-row">
            <a href="/" class="lp-logo" aria-label="KassirON">
                <img class="logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="KassirON" width="640" height="213">
                <img class="logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="KassirON" width="640" height="213">
            </a>
            <nav class="lp-nav" aria-label="<?= e(t('lp_nav_label')) ?>">
                <a href="#features"><?= e(t('lp_nav_features')) ?></a>
                <a href="#desktop"><?= e(t('lp_nav_desktop')) ?></a>
                <a href="#start"><?= e(t('lp_nav_start')) ?></a>
                <a href="#contact"><?= e(t('lp_nav_contact')) ?></a>
            </nav>
            <div class="lp-header-actions">
                <?php require BASE_PATH . '/app/views/partials/theme-toggle.php'; ?>
                <?php require BASE_PATH . '/app/views/partials/lang-switcher.php'; ?>
                <a href="/login" class="btn btn-primary lp-login"><?= e(t('lp_login')) ?></a>
            </div>
        </div>
    </header>

    <main id="main">
        <?= $content ?>
    </main>

    <footer class="lp-footer">
        <div class="lp-container lp-footer-row">
            <div class="lp-footer-brand">
                <img class="logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="KassirON" width="640" height="213" loading="lazy">
                <img class="logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="KassirON" width="640" height="213" loading="lazy">
                <p><?= e(t('lp_footer_tagline')) ?></p>
            </div>
            <nav class="lp-footer-links" aria-label="<?= e(t('lp_nav_label')) ?>">
                <a href="/login"><?= e(t('lp_login')) ?></a>
                <a href="#desktop"><?= e(t('lp_nav_desktop')) ?></a>
                <a href="tel:<?= e(SUPPORT_PHONE) ?>"><?= e(SUPPORT_PHONE_DISPLAY) ?></a>
                <a href="https://t.me/<?= e(SUPPORT_TELEGRAM) ?>" target="_blank" rel="noopener noreferrer">Telegram</a>
            </nav>
            <p class="lp-copy">© <?= date('Y') ?> KassirON</p>
        </div>
    </footer>
    <script src="<?= asset('js/app.js') ?>" defer></script>
</body>
</html>

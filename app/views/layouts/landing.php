<?php
// The public landing page at "/" for visitors (HomeController). Uses
// app.css's tokens and components, so the day/night themes and the language
// switch work as everywhere else; landing.js adds the interactive parts
// (the page is complete and readable without it).
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
            // Scroll-reveal hides content until landing.js shows it: if that
            // script never arrives, show everything after a few seconds.
            document.documentElement.classList.add('lp-js');
            setTimeout(function () {
                if (!window.__lpReady) {
                    document.documentElement.classList.remove('lp-js');
                }
            }, 4000);
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
    <link rel="preload" href="/assets/fonts/manrope-latin-wght-normal.woff2" as="font" type="font/woff2" crossorigin>
    <link rel="preload" href="/assets/fonts/onest-latin-wght-normal.woff2" as="font" type="font/woff2" crossorigin>
    <link rel="stylesheet" href="<?= asset('css/app.css') ?>">
    <link rel="stylesheet" href="<?= asset('css/landing.css') ?>">
    <link rel="manifest" href="/manifest.json">
    <link rel="icon" href="<?= asset('img/icon-32.png') ?>" type="image/png" sizes="32x32">
    <link rel="icon" href="<?= asset('img/icon-192.png') ?>" type="image/png" sizes="192x192">
    <link rel="apple-touch-icon" href="<?= asset('img/icon-180.png') ?>">
    <meta name="theme-color" content="#a9fa06" media="(prefers-color-scheme: light)">
    <meta name="theme-color" content="#11150e" media="(prefers-color-scheme: dark)">
</head>
<body class="lp-body">
    <a class="lp-skip" href="#main"><?= e(t('lp_skip_to_content')) ?></a>
    <div class="lp-progress" aria-hidden="true"><i></i></div>
    <header class="lp-header" id="lp-header">
        <div class="lp-container">
            <div class="lp-header-row">
                <a href="/" class="lp-logo" aria-label="KassirON">
                    <img src="<?= asset('img/logo-light.png') ?>" alt="KassirON" width="640" height="148">
                </a>
                <nav class="lp-nav" aria-label="<?= e(t('lp_nav_label')) ?>">
                    <a href="#features"><?= e(t('lp_nav_features')) ?></a>
                    <a href="#tour"><?= e(t('lp_nav_tour')) ?></a>
                    <a href="#desktop"><?= e(t('lp_nav_desktop')) ?></a>
                    <a href="#faq"><?= e(t('lp_nav_faq')) ?></a>
                    <a href="#contact"><?= e(t('lp_nav_contact')) ?></a>
                </nav>
                <div class="lp-header-actions">
                    <?php require BASE_PATH . '/app/views/partials/theme-toggle.php'; ?>
                    <?php require BASE_PATH . '/app/views/partials/lang-switcher.php'; ?>
                    <a href="/login" class="lp-pillbtn lp-login"><span><?= e(t('lp_login')) ?></span><i aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M7 17 17 7M8 7h9v9"/></svg></i></a>
                    <button type="button" class="lp-burger" id="lp-burger" aria-expanded="false" aria-controls="lp-mnav" aria-label="<?= e(t('lp_menu')) ?>">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                            <path class="lp-burger-open" d="M4 7h16M4 12h16M4 17h16"/>
                            <path class="lp-burger-close" d="M6 6l12 12M18 6 6 18"/>
                        </svg>
                    </button>
                </div>
            </div>
            <nav class="lp-mnav" id="lp-mnav" hidden aria-label="<?= e(t('lp_nav_label')) ?>">
                <a href="#features"><?= e(t('lp_nav_features')) ?></a>
                <a href="#tour"><?= e(t('lp_nav_tour')) ?></a>
                <a href="#desktop"><?= e(t('lp_nav_desktop')) ?></a>
                <a href="#faq"><?= e(t('lp_nav_faq')) ?></a>
                <a href="#contact"><?= e(t('lp_nav_contact')) ?></a>
                <a href="/login" class="lp-mnav-login"><?= e(t('lp_login')) ?></a>
            </nav>
        </div>
    </header>

    <main id="main">
        <?= $content ?>
    </main>

    <footer class="lp-footer">
        <div class="lp-container">
            <div class="lp-footer-grid">
                <div class="lp-footer-brand">
                    <img src="<?= asset('img/logo-light.png') ?>" alt="KassirON" width="640" height="148" loading="lazy">
                    <p><?= e(t('lp_footer_tagline')) ?></p>
                </div>
                <nav class="lp-footer-col" aria-label="<?= e(t('lp_footer_product')) ?>">
                    <h3><?= e(t('lp_footer_product')) ?></h3>
                    <a href="#features"><?= e(t('lp_nav_features')) ?></a>
                    <a href="#tour"><?= e(t('lp_nav_tour')) ?></a>
                    <a href="#desktop"><?= e(t('lp_nav_desktop')) ?></a>
                    <a href="#start"><?= e(t('lp_nav_start')) ?></a>
                    <a href="#faq"><?= e(t('lp_nav_faq')) ?></a>
                </nav>
                <nav class="lp-footer-col" aria-label="<?= e(t('lp_footer_contact')) ?>">
                    <h3><?= e(t('lp_footer_contact')) ?></h3>
                    <a href="tel:<?= e(SUPPORT_PHONE) ?>"><?= e(SUPPORT_PHONE_DISPLAY) ?></a>
                    <a href="https://t.me/<?= e(SUPPORT_TELEGRAM) ?>" target="_blank" rel="noopener noreferrer">Telegram</a>
                    <a href="/login"><?= e(t('lp_login')) ?></a>
                </nav>
            </div>
            <div class="lp-footer-bar">
                <span>© <?= date('Y') ?> KassirON</span>
                <span>kassiron.uz</span>
            </div>
        </div>
        <div class="lp-wordmark" aria-hidden="true">KassirON</div>
    </footer>
    <script src="<?= asset('js/app.js') ?>" defer></script>
    <script src="<?= asset('js/landing.js') ?>" defer></script>
</body>
</html>

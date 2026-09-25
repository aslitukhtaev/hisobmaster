<!doctype html>
<html lang="<?= e(current_lang()) ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <script nonce="<?= e(csp_nonce()) ?>">
        (function () {
            try {
                var t = localStorage.getItem('kassiron-theme');
                if (t === 'light' || t === 'dark') {
                    document.documentElement.setAttribute('data-theme', t);
                }
            } catch (e) {}
        })();
    </script>
    <title><?= isset($pageTitle) ? e($pageTitle) . ' — ' . e(t('app_name')) : e(t('app_name')) ?></title>
    <link rel="stylesheet" href="<?= asset('css/app.css') ?>">
    <link rel="manifest" href="/manifest.json">
    <link rel="icon" href="<?= asset('img/icon-192.png') ?>" type="image/png">
    <link rel="apple-touch-icon" href="<?= asset('img/icon-192.png') ?>">
    <meta name="theme-color" content="#059669" media="(prefers-color-scheme: light)">
    <meta name="theme-color" content="#121e2e" media="(prefers-color-scheme: dark)">
    <?php if (!App\Desktop\Desktop::enabled()): /* Telegram WebApp SDK: website only — the desktop app works offline */ ?>
    <script src="https://telegram.org/js/telegram-web-app.js"></script>
    <?php endif; ?>
</head>
<body class="auth-body">
    <div class="auth-topbar">
        <?php require BASE_PATH . '/app/views/partials/theme-toggle.php'; ?>
        <?php require BASE_PATH . '/app/views/partials/lang-switcher.php'; ?>
    </div>
    <main class="auth-wrap">
        <?= $content ?>
    </main>
    <?php if (!App\Desktop\Desktop::enabled()): ?>
    <script src="<?= asset('js/telegram.js') ?>"></script>
    <?php endif; ?>
    <script src="<?= asset('js/app.js') ?>" defer></script>
</body>
</html>

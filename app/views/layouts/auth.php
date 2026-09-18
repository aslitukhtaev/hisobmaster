<!doctype html>
<html lang="<?= e(current_lang()) ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title><?= isset($pageTitle) ? e($pageTitle) . ' — ' . e(t('app_name')) : e(t('app_name')) ?></title>
    <link rel="stylesheet" href="<?= asset('css/app.css') ?>">
    <link rel="manifest" href="/manifest.json">
    <link rel="icon" href="<?= asset('img/icon-192.png') ?>" type="image/png">
    <link rel="apple-touch-icon" href="<?= asset('img/icon-192.png') ?>">
    <meta name="theme-color" content="#4f46e5">
    <script src="https://telegram.org/js/telegram-web-app.js"></script>
</head>
<body class="auth-body">
    <div class="auth-topbar">
        <?php require BASE_PATH . '/app/views/partials/lang-switcher.php'; ?>
    </div>
    <main class="auth-wrap">
        <?= $content ?>
    </main>
    <script src="<?= asset('js/telegram.js') ?>"></script>
    <script src="<?= asset('js/app.js') ?>" defer></script>
</body>
</html>

<!doctype html>
<html lang="<?= e(current_lang()) ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title><?= e(t('app_name')) ?></title>
    <link rel="stylesheet" href="<?= asset('css/app.css') ?>">
</head>
<body class="auth-body">
    <div class="auth-topbar">
        <?php require BASE_PATH . '/app/views/partials/lang-switcher.php'; ?>
    </div>
    <main class="auth-wrap">
        <?= $content ?>
    </main>
    <script src="<?= asset('js/app.js') ?>" defer></script>
</body>
</html>

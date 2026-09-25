<?php
// A receipt alone, as the desktop app prints it on a thermal printer: a
// page exactly the paper's width ($paperWidth: 80 or 58 mm), no header,
// menu or scripts — see desktop/lib/receipt-printer.js.
?>
<!doctype html>
<html lang="<?= e(current_lang()) ?>">
<head>
    <meta charset="utf-8">
    <title><?= isset($pageTitle) ? e($pageTitle) : e(t('app_name')) ?></title>
    <link rel="stylesheet" href="<?= asset('css/receipt-print.css') ?>">
</head>
<body class="paper-<?= (int) $paperWidth === 58 ? 58 : 80 ?>">
<?= $content ?>
</body>
</html>

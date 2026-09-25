<?php $pageTitle = t('devices_title'); ?>
<section class="page-head">
    <h1><?= e(t('devices_title')) ?></h1>
    <p class="muted"><?= e(t('devices_hint', ['days' => $offlineDays])) ?></p>
</section>

<?php $revokeUrl = '/devices/%d/revoke'; require BASE_PATH . '/app/views/devices/_table.php'; ?>

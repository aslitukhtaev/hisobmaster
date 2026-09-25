<?php $pageTitle = t('devices_title'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('devices_title')) ?></h1>
        <p class="muted"><?= e(t('devices_hint', ['days' => $offlineDays])) ?></p>
    </div>
    <a href="<?= e(DESKTOP_DOWNLOAD_URL) ?>" class="btn btn-primary" rel="noopener"><?= e(t('desktop_download')) ?></a>
</section>

<div class="card" style="margin-bottom:14px;">
    <p class="muted" style="margin:0;"><?= e(t('desktop_download_hint')) ?></p>
</div>

<?php $revokeUrl = '/devices/%d/revoke'; require BASE_PATH . '/app/views/devices/_table.php'; ?>

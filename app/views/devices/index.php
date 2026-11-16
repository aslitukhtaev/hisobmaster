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

<div class="card pair-card" id="pair" style="margin-bottom:14px;">
    <div>
        <h2><?= e(t('pair_title')) ?></h2>
        <p class="muted"><?= e(t('pair_hint')) ?></p>
    </div>
    <?php if (!empty($pairCode)): ?>
        <div class="pair-code" aria-live="polite"><?= e(substr($pairCode, 0, 3) . ' ' . substr($pairCode, 3)) ?></div>
        <p class="muted"><?= e(t('pair_valid', ['minutes' => $pairMinutes])) ?></p>
    <?php else: ?>
        <div class="row-actions">
            <form method="post" action="/devices/pair">
                <?= csrf_field() ?>
                <button type="submit" class="btn btn-primary"><?= e(t('pair_button')) ?></button>
            </form>
            <a class="btn btn-ghost" href="<?= e(ANDROID_DOWNLOAD_URL) ?>" download><?= e(t('lp_android_download')) ?></a>
        </div>
    <?php endif; ?>
</div>

<?php $revokeUrl = '/devices/%d/revoke'; require BASE_PATH . '/app/views/devices/_table.php'; ?>

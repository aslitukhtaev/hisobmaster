<?php $pageTitle = t('devices_title') . ' — ' . $shop['name']; ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('devices_title')) ?> — <?= e($shop['name']) ?></h1>
        <p class="muted"><?= e(t('devices_hint', ['days' => (int) $shop['offline_days']])) ?></p>
    </div>
    <a href="/superadmin/shops" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
</section>

<div class="card form-card" style="margin-bottom:14px;">
    <form method="post" action="/superadmin/shops/<?= (int) $shop['id'] ?>/offline-days" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('offline_days_label')) ?></span>
            <input type="number" name="offline_days" min="1" max="90" step="1" required value="<?= (int) $shop['offline_days'] ?>">
            <span class="muted"><?= e(t('offline_days_hint')) ?></span>
        </label>
        <button type="submit" class="btn btn-primary btn-sm" style="width:fit-content;"><?= e(t('save')) ?></button>
    </form>
</div>

<?php $revokeUrl = '/superadmin/shops/' . (int) $shop['id'] . '/devices/%d/revoke'; require BASE_PATH . '/app/views/devices/_table.php'; ?>

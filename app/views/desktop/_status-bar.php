<?php
use App\Desktop\Desktop;

$desktopStatus = Desktop::status();
$licenseState = $desktopStatus['license']['state'];
$daysLeft = $desktopStatus['license']['days_left'];
$offline = $desktopStatus['last_error'] === 'offline';
$pending = $desktopStatus['pending'];
$hasPending = $pending['sales'] > 0 || $pending['other'];
$dot = $licenseState !== 'ok' || $offline ? 'red' : ($hasPending ? 'amber' : 'green');
if ($pending['sales'] > 0) {
    $label = t('desktop_status_pending_sales', ['count' => $pending['sales']]);
} elseif ($pending['other']) {
    $label = t('desktop_status_pending_other');
} elseif ($offline) {
    $label = t('desktop_status_offline');
} else {
    $label = t('desktop_status_synced');
}
$lastSync = $desktopStatus['last_sync_at'] ? t('desktop_status_last_sync', ['time' => local_datetime($desktopStatus['last_sync_at'], 'H:i')]) : t('desktop_status_never');
?>
<div id="desktop-status"><div class="desktop-bar" id="desktop-bar">
    <span class="desktop-dot desktop-dot-<?= e($dot) ?>" aria-hidden="true"></span>
    <span class="desktop-bar-label"><?= e($label) ?></span>
    <span class="muted desktop-bar-sub"><?= e($lastSync) ?><?php if ($licenseState === 'ok' && $daysLeft !== null && $daysLeft <= Desktop::WARN_DAYS + 3): ?> · <?= e(t('desktop_license_days_left', ['days' => $daysLeft])) ?><?php endif; ?></span>
    <form method="post" action="/desktop/sync" class="desktop-bar-form">
        <?= csrf_field() ?>
        <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('desktop_sync_now')) ?></button>
    </form>
</div>
<?php if ($licenseState !== 'ok'): ?>
    <div class="alert alert-error" role="alert"><?= e(t('desktop_locked_' . $licenseState)) ?></div>
<?php elseif ($daysLeft !== null && $daysLeft <= Desktop::WARN_DAYS): ?>
    <div class="alert alert-warning" role="status"><?= e(t('desktop_license_expiring', ['days' => max(0, $daysLeft)])) ?></div>
<?php endif; ?>
</div>

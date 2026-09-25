<?php
/**
 * @var array $devices DeviceService::allByShop() rows
 * @var string $revokeUrl  "/devices/%d/revoke" style format with the device id
 */
$statusClass = ['active' => 'status-active', 'revoked' => 'status-blocked', 'replaced' => 'status-blocked'];
?>
<?php if (empty($devices)): ?>
    <div class="card"><p class="muted"><?= e(t('no_devices_yet')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('device_name')) ?></th>
                        <th><?= e(t('device_code')) ?></th>
                        <th><?= e(t('device_last_seen')) ?></th>
                        <th><?= e(t('device_app_version')) ?></th>
                        <th><?= e(t('status')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($devices as $device): ?>
                    <tr>
                        <td class="cell-wrap">
                            <?= e($device['name'] ?: '—') ?>
                            <span class="muted stock-variant-note"><?= e(t('device_activated_at', ['date' => local_datetime($device['created_at'], 'd.m.Y'), 'name' => $device['activated_by_name'] ?? '—'])) ?></span>
                        </td>
                        <td data-label="<?= e(t('device_code')) ?>"><?= e($device['code']) ?></td>
                        <td data-label="<?= e(t('device_last_seen')) ?>" class="muted"><?= e($device['last_seen_at'] ? local_datetime($device['last_seen_at']) : '—') ?></td>
                        <td data-label="<?= e(t('device_app_version')) ?>" class="muted"><?= e($device['app_version'] ?: '—') ?></td>
                        <td data-label="<?= e(t('status')) ?>">
                            <span class="status-pill <?= $statusClass[$device['status']] ?? 'status-blocked' ?>"><?= e(t('device_status_' . $device['status'])) ?></span>
                        </td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <?php if ($device['status'] === 'active'): ?>
                                <form method="post" action="<?= e(sprintf($revokeUrl, (int) $device['id'])) ?>" data-confirm="<?= e(t('confirm_device_revoke')) ?>">
                                    <?= csrf_field() ?>
                                    <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('device_revoke')) ?></button>
                                </form>
                            <?php else: ?>
                                <span class="muted">—</span>
                            <?php endif; ?>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('shops')) ?></h1>
        <p class="muted"><?= count($shops) ?> <?= e(t('shops_count_label')) ?></p>
    </div>
    <a href="/superadmin/shops/create" class="btn btn-primary"><?= e(t('create_shop')) ?></a>
</section>

<?php if (empty($shops)): ?>
    <div class="card"><p class="muted"><?= e(t('no_shops_yet')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('shop_name')) ?></th>
                        <th><?= e(t('owner_full_name')) ?></th>
                        <th><?= e(t('phone')) ?></th>
                        <th><?= e(t('status')) ?></th>
                        <th><?= e(t('created_at_label')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($shops as $shop): ?>
                    <tr>
                        <td><?= e($shop['name']) ?></td>
                        <td data-label="<?= e(t('owner_full_name')) ?>"><?= e($shop['owner_full_name']) ?></td>
                        <td data-label="<?= e(t('phone')) ?>"><?= e($shop['phone']) ?></td>
                        <td data-label="<?= e(t('status')) ?>">
                            <span class="status-pill <?= $shop['status'] === 'active' ? 'status-active' : 'status-blocked' ?>">
                                <?= e($shop['status'] === 'active' ? t('active_status') : t('blocked_status')) ?>
                            </span>
                        </td>
                        <td class="muted" data-label="<?= e(t('created_at_label')) ?>"><?= e(substr((string) $shop['created_at'], 0, 10)) ?></td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <div class="row-actions">
                                <form method="post" action="/superadmin/shops/<?= (int) $shop['id'] ?>/reset-password"
                                      onsubmit="return confirm('<?= e(t('confirm_reset_password')) ?>');">
                                    <?= csrf_field() ?>
                                    <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('reset_password')) ?></button>
                                </form>
                                <form method="post" action="/superadmin/shops/<?= (int) $shop['id'] ?>/toggle-status"
                                      onsubmit="return confirm('<?= e($shop['status'] === 'active' ? t('confirm_block') : t('confirm_activate')) ?>');">
                                    <?= csrf_field() ?>
                                    <button type="submit" class="btn btn-ghost btn-sm">
                                        <?= e($shop['status'] === 'active' ? t('toggle_block') : t('toggle_activate')) ?>
                                    </button>
                                </form>
                            </div>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

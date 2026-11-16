<?php $pageTitle = t('shops'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('shops')) ?></h1>
        <p class="muted"><?= e(count_label(count($shops), 'shops_count_label')) ?></p>
    </div>
    <div class="row-actions">
        <a href="/superadmin/backups" class="btn btn-ghost"><?= e(t('backups_nav')) ?></a>
        <a href="/superadmin/shops/create" class="btn btn-primary"><?= e(t('create_shop')) ?></a>
    </div>
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
                        <th><?= e(t('shop_access')) ?></th>
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
                        <td data-label="<?= e(t('shop_access')) ?>">
                            <?php $accessLeft = App\Models\Shop::daysLeft($shop); ?>
                            <?php if ($accessLeft === null): ?>
                                <span class="muted"><?= e(t('shop_access_unlimited')) ?></span>
                            <?php else: ?>
                                <span class="status-pill <?= $accessLeft > 0 ? 'status-active' : 'status-blocked' ?>">
                                    <?= e(t(($shop['plan'] ?? '') === 'trial' ? 'shop_plan_trial' : 'shop_plan_paid')) ?> · <?= e(local_datetime($shop['access_until'], 'd.m.Y')) ?>
                                </span>
                            <?php endif; ?>
                        </td>
                        <td class="muted" data-label="<?= e(t('created_at_label')) ?>"><?= e(local_datetime($shop['created_at'], 'd.m.Y')) ?></td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <div class="row-actions">
                                <form method="post" action="/superadmin/shops/<?= (int) $shop['id'] ?>/access">
                                    <?= csrf_field() ?>
                                    <input type="hidden" name="extend" value="30">
                                    <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('shop_extend_30')) ?></button>
                                </form>
                                <?php if ($accessLeft !== null): ?>
                                    <form method="post" action="/superadmin/shops/<?= (int) $shop['id'] ?>/access">
                                        <?= csrf_field() ?>
                                        <input type="hidden" name="extend" value="unlimited">
                                        <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('shop_extend_unlimited')) ?></button>
                                    </form>
                                <?php endif; ?>
                                <a href="/superadmin/shops/<?= (int) $shop['id'] ?>/devices" class="btn btn-ghost btn-sm"><?= e(t('devices_title')) ?></a>
                                <form method="post" action="/superadmin/shops/<?= (int) $shop['id'] ?>/reset-password"
                                      data-confirm="<?= e(t('confirm_reset_password')) ?>">
                                    <?= csrf_field() ?>
                                    <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('reset_password')) ?></button>
                                </form>
                                <form method="post" action="/superadmin/shops/<?= (int) $shop['id'] ?>/toggle-status"
                                      data-confirm="<?= e($shop['status'] === 'active' ? t('confirm_block') : t('confirm_activate')) ?>">
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

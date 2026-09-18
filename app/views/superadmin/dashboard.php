<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('welcome', ['name' => $user['full_name'] ?? ''])) ?></h1>
        <p class="muted"><?= e(t('system_running')) ?> — HisobMaster</p>
    </div>
    <a href="/superadmin/shops/create" class="btn btn-primary"><?= e(t('create_shop')) ?></a>
</section>

<section class="stat-grid">
    <div class="stat-tile">
        <div class="stat-value"><?= (int) $counts['total'] ?></div>
        <div class="stat-label"><?= e(t('total_shops')) ?></div>
    </div>
    <div class="stat-tile">
        <div class="stat-value"><?= (int) $counts['active'] ?></div>
        <div class="stat-label"><?= e(t('active_shops')) ?></div>
    </div>
    <div class="stat-tile">
        <div class="stat-value"><?= (int) $counts['blocked'] ?></div>
        <div class="stat-label"><?= e(t('blocked_shops')) ?></div>
    </div>
</section>

<section class="card">
    <div class="card-header-row">
        <h2><?= e(t('recent_shops')) ?></h2>
        <a href="/superadmin/shops" class="btn btn-ghost btn-sm"><?= e(t('view_all')) ?></a>
    </div>

    <?php if (empty($recentShops)): ?>
        <p class="muted"><?= e(t('no_shops_yet')) ?></p>
    <?php else: ?>
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('shop_name')) ?></th>
                        <th><?= e(t('owner_full_name')) ?></th>
                        <th><?= e(t('phone')) ?></th>
                        <th><?= e(t('status')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($recentShops as $shop): ?>
                    <tr>
                        <td><?= e($shop['name']) ?></td>
                        <td><?= e($shop['owner_full_name']) ?></td>
                        <td><?= e($shop['phone']) ?></td>
                        <td>
                            <span class="status-pill <?= $shop['status'] === 'active' ? 'status-active' : 'status-blocked' ?>">
                                <?= e($shop['status'] === 'active' ? t('active_status') : t('blocked_status')) ?>
                            </span>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    <?php endif; ?>
</section>

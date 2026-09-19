<?php $pageTitle = t('employees'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('employees')) ?></h1>
        <p class="muted"><?= count($employees) ?> <?= e(t('employees_count_label')) ?></p>
    </div>
    <div class="row-actions">
        <a href="/employees/attendance" class="btn btn-ghost"><?= e(t('attendance_history_title')) ?></a>
        <a href="/employees/invite" class="btn btn-primary"><?= e(t('invite_employee')) ?></a>
    </div>
</section>

<?php if (!empty($invites)): ?>
<div class="card" style="margin-bottom:18px;">
    <h2><?= e(t('pending_invites')) ?></h2>
    <div class="stack" style="gap:10px; margin-top:12px;">
    <?php foreach ($invites as $invite): ?>
        <div class="invite-row">
            <span class="muted"><?= e(t('expires_at_label')) ?>: <?= e(substr((string) $invite['expires_at'], 0, 16)) ?></span>
            <form method="post" action="/employees/invites/<?= (int) $invite['id'] ?>/revoke"
                  onsubmit="return confirm('<?= e(t('confirm_revoke_invite')) ?>');">
                <?= csrf_field() ?>
                <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('revoke_invite')) ?></button>
            </form>
        </div>
    <?php endforeach; ?>
    </div>
</div>
<?php endif; ?>

<?php if (empty($employees)): ?>
    <div class="card"><p class="muted"><?= e(t('no_employees_yet')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('full_name_label')) ?></th>
                        <th><?= e(t('login')) ?></th>
                        <th><?= e(t('permissions_label')) ?></th>
                        <th><?= e(t('commission_label')) ?></th>
                        <th><?= e(t('status')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($employees as $employee): $empPerms = json_decode($employee['permissions_json'] ?? '[]', true) ?: []; ?>
                    <tr>
                        <td><?= e($employee['full_name']) ?></td>
                        <td data-label="<?= e(t('login')) ?>" class="muted"><?= e($employee['login']) ?></td>
                        <td data-label="<?= e(t('permissions_label')) ?>">
                            <?php if (empty($empPerms)): ?>
                                <span class="muted">—</span>
                            <?php else: ?>
                                <?php foreach ($empPerms as $p): ?>
                                    <span class="perm-badge"><?= e(t('permission_' . $p)) ?></span>
                                <?php endforeach; ?>
                            <?php endif; ?>
                        </td>
                        <td data-label="<?= e(t('commission_label')) ?>">
                            <?php if ($employee['commission_rate'] !== null): ?>
                                <?= e(format_qty((float) $employee['commission_rate']) . '%') ?>
                            <?php else: ?>
                                <span class="muted">—</span>
                            <?php endif; ?>
                        </td>
                        <td data-label="<?= e(t('status')) ?>">
                            <span class="status-pill <?= $employee['status'] === 'active' ? 'status-active' : 'status-blocked' ?>">
                                <?= e($employee['status'] === 'active' ? t('active_status') : t('blocked_status')) ?>
                            </span>
                        </td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <div class="row-actions">
                                <a href="/employees/<?= (int) $employee['id'] ?>/edit" class="btn btn-ghost btn-sm"><?= e(t('edit_permissions')) ?></a>
                                <form method="post" action="/employees/<?= (int) $employee['id'] ?>/toggle-status"
                                      onsubmit="return confirm('<?= e($employee['status'] === 'active' ? t('confirm_block_employee') : t('confirm_activate_employee')) ?>');">
                                    <?= csrf_field() ?>
                                    <button type="submit" class="btn btn-ghost btn-sm">
                                        <?= e($employee['status'] === 'active' ? t('toggle_block') : t('toggle_activate')) ?>
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

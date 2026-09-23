<?php $pageTitle = t('backups_title'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('backups_title')) ?></h1>
        <p class="muted"><?= e(count_label(count($backups), 'backups_count_label')) ?></p>
    </div>
    <div class="row-actions">
        <a href="/superadmin/backup" class="btn btn-ghost"><?= e(t('download_current_db')) ?></a>
        <form method="post" action="/superadmin/backups/create">
            <?= csrf_field() ?>
            <button type="submit" class="btn btn-primary"><?= e(t('create_backup_now')) ?></button>
        </form>
    </div>
</section>

<div class="alert alert-info"><?= e(t('backups_honesty_note')) ?></div>

<?php if (empty($backups)): ?>
    <div class="card"><p class="muted"><?= e(t('no_backups_yet')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('backup_created_at')) ?></th>
                        <th><?= e(t('backup_size')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($backups as $backup): ?>
                    <tr>
                        <td><?= e(date('Y-m-d H:i', $backup['created_at'])) ?></td>
                        <td class="muted"><?= number_format($backup['size'] / 1024 / 1024, 2) ?> MB</td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <div class="row-actions">
                                <a href="/superadmin/backups/<?= e($backup['filename']) ?>/download" class="btn btn-ghost btn-sm"><?= e(t('download')) ?></a>
                                <form method="post" action="/superadmin/backups/<?= e($backup['filename']) ?>/delete"
                                      data-confirm="<?= e(t('confirm_delete_backup')) ?>">
                                    <?= csrf_field() ?>
                                    <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('delete')) ?></button>
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

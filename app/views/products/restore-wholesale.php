<?php
$pageTitle = t('wholesale_restore_title');
$when = static fn (?int $ts): string => $ts ? local_datetime(gmdate('Y-m-d H:i:s', $ts)) : '—';
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('wholesale_restore_title')) ?></h1>
        <p class="muted"><?= e(t('wholesale_restore_hint')) ?></p>
    </div>
    <div class="row-actions">
        <a href="/products" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
    </div>
</section>

<?php if ($backupCount === 0): ?>
    <div class="card"><p class="muted"><?= e(t('wholesale_restore_no_backups')) ?></p></div>
<?php elseif ($rows === []): ?>
    <div class="card">
        <p><?= e(t('wholesale_restore_nothing')) ?></p>
        <p class="muted"><?= e(t('wholesale_restore_backups', ['count' => $backupCount, 'from' => $when($oldestBackup), 'to' => $when($newestBackup)])) ?></p>
    </div>
<?php else: ?>
    <form method="post" action="/products/restore-wholesale" class="card">
        <?= csrf_field() ?>
        <p><?= e(t('wholesale_restore_found', ['count' => count($rows)])) ?></p>
        <p class="muted"><?= e(t('wholesale_restore_backups', ['count' => $backupCount, 'from' => $when($oldestBackup), 'to' => $when($newestBackup)])) ?></p>
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><input type="checkbox" id="restore-all" checked aria-label="<?= e(t('select_all')) ?>"></th>
                        <th><?= e(t('product_name')) ?></th>
                        <th style="text-align:right"><?= e(t('retail_price')) ?></th>
                        <th style="text-align:right"><?= e(t('wholesale_price')) ?></th>
                        <th><?= e(t('wholesale_restore_from')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($rows as $row): ?>
                    <tr>
                        <td><input type="checkbox" name="pick[]" value="<?= e($row['key']) ?>" class="restore-pick" checked aria-label="<?= e($row['name']) ?>"></td>
                        <td><?= e($row['name']) ?></td>
                        <td style="text-align:right"><?= $row['sell_price'] !== null ? money($row['sell_price']) : '—' ?></td>
                        <td style="text-align:right"><strong><?= money($row['wholesale_price']) ?></strong></td>
                        <td class="muted"><?= e($when($row['backup_at'])) ?></td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
        <div class="row-actions" style="margin-top:14px;">
            <button type="submit" class="btn btn-primary"><?= e(t('wholesale_restore_button')) ?></button>
        </div>
    </form>
    <script nonce="<?= e(csp_nonce()) ?>">
        document.getElementById('restore-all').addEventListener('change', function () {
            var on = this.checked;
            document.querySelectorAll('.restore-pick').forEach(function (box) { box.checked = on; });
        });
    </script>
<?php endif; ?>

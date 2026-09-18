<?php $pageTitle = t('activity_log'); ?>
<section class="page-head">
    <h1><?= e(t('activity_log')) ?></h1>
    <p class="muted"><?= e(t('activity_log_hint')) ?></p>
</section>

<?php if (empty($entries)): ?>
    <div class="card"><p class="muted"><?= e(t('no_activity_yet')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('sale_date')) ?></th>
                        <th><?= e(t('full_name_label')) ?></th>
                        <th><?= e(t('activity_description')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($entries as $entry): ?>
                    <?php $meta = json_decode($entry['meta_json'] ?? '[]', true) ?: []; ?>
                    <tr>
                        <td><?= e(substr((string) $entry['created_at'], 0, 16)) ?></td>
                        <td data-label="<?= e(t('full_name_label')) ?>" class="muted"><?= e($entry['user_name'] ?? '—') ?></td>
                        <td data-label="<?= e(t('activity_description')) ?>"><?= e(t('activity_' . $entry['action'], $meta)) ?></td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

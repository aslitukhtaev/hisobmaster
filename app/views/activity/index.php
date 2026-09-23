<?php
$pageTitle = t('activity_log');
$hasFilters = $filters['user_id'] !== null || $filters['action'] !== null || $filters['from'] !== '' || $filters['search'] !== '';

$queryFor = static function (array $overrides = []) use ($filters): string {
    $params = array_merge([
        'user_id' => $filters['user_id'] ?? '',
        'action' => $filters['action'] ?? '',
        'from' => $filters['from'],
        'to' => $filters['to'],
        'q' => $filters['search'],
    ], $overrides);
    return http_build_query(array_filter($params, static fn ($v) => $v !== '' && $v !== null));
};
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('activity_log')) ?></h1>
        <p class="muted"><?= e(t('activity_log_hint')) ?></p>
    </div>
    <div class="row-actions">
        <a href="/activity/export?<?= $queryFor() ?>" class="btn btn-ghost"><?= e(t('activity_export_csv')) ?></a>
    </div>
</section>

<form method="get" action="/activity" class="date-filter-bar activity-filter-bar">
    <label class="field">
        <span><?= e(t('activity_filter_user')) ?></span>
        <select name="user_id">
            <option value=""><?= e(t('activity_filter_all_users')) ?></option>
            <?php foreach ($users as $u): ?>
                <option value="<?= (int) $u['id'] ?>" <?= $filters['user_id'] === (int) $u['id'] ? 'selected' : '' ?>>
                    <?= e($u['full_name']) ?>
                </option>
            <?php endforeach; ?>
        </select>
    </label>
    <label class="field">
        <span><?= e(t('activity_filter_action')) ?></span>
        <select name="action">
            <option value=""><?= e(t('activity_filter_all_actions')) ?></option>
            <?php foreach ($actions as $actionKey): ?>
                <option value="<?= e($actionKey) ?>" <?= $filters['action'] === $actionKey ? 'selected' : '' ?>>
                    <?= e(t('activity_action_' . $actionKey)) ?>
                </option>
            <?php endforeach; ?>
        </select>
    </label>
    <label class="field">
        <span><?= e(t('from_date')) ?></span>
        <input type="date" name="from" value="<?= e($filters['from']) ?>">
    </label>
    <label class="field">
        <span><?= e(t('to_date')) ?></span>
        <input type="date" name="to" value="<?= e($filters['to']) ?>">
    </label>
    <label class="field field-grow">
        <span><?= e(t('activity_filter_search')) ?></span>
        <input type="text" name="q" placeholder="<?= e(t('activity_filter_search_placeholder')) ?>" value="<?= e($filters['search']) ?>">
    </label>
    <div class="field-actions">
        <button type="submit" class="btn btn-primary btn-sm"><?= e(t('activity_filter_apply')) ?></button>
        <?php if ($hasFilters): ?>
            <a href="/activity" class="btn btn-ghost btn-sm"><?= e(t('activity_filter_reset')) ?></a>
        <?php endif; ?>
    </div>
</form>

<?php if (empty($entries)): ?>
    <div class="card"><p class="muted"><?= e($hasFilters ? t('no_activity_matches') : t('no_activity_yet')) ?></p></div>
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
                        <td><?= e(local_datetime($entry['created_at'])) ?></td>
                        <td data-label="<?= e(t('full_name_label')) ?>" class="muted"><?= e($entry['user_name'] ?? '—') ?></td>
                        <td data-label="<?= e(t('activity_description')) ?>"><?= e(t('activity_' . $entry['action'], $meta)) ?></td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

<section class="page-head">
    <h1><?= e(t('edit_permissions')) ?></h1>
    <p class="muted"><?= e($employee['full_name']) ?></p>
</section>

<div class="card form-card">
    <form method="post" action="/employees/<?= (int) $employee['id'] ?>" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('commission_rate_field_label')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="number" step="0.01" min="0" max="100" inputmode="decimal" name="commission_rate"
                   value="<?= e($employee['commission_rate'] !== null ? (string) $employee['commission_rate'] : '') ?>">
            <span class="muted"><?= e(t('commission_rate_field_hint')) ?></span>
        </label>
        <hr class="divider">
        <?php $checked = $employeePermissions; require BASE_PATH . '/app/views/employees/_permission_checkboxes.php'; ?>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('save')) ?></button>
    </form>
</div>

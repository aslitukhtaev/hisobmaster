<section class="page-head">
    <h1><?= e(t('edit_permissions')) ?></h1>
    <p class="muted"><?= e($employee['full_name']) ?></p>
</section>

<div class="card form-card">
    <form method="post" action="/employees/<?= (int) $employee['id'] ?>" class="stack">
        <?= csrf_field() ?>
        <?php $checked = $employeePermissions; require BASE_PATH . '/app/views/employees/_permission_checkboxes.php'; ?>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('save')) ?></button>
    </form>
</div>

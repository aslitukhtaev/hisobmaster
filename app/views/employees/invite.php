<section class="page-head">
    <h1><?= e(t('invite_employee')) ?></h1>
    <p class="muted"><?= e(t('invite_employee_hint')) ?></p>
</section>

<div class="card form-card">
    <form method="post" action="/employees/invite" class="stack">
        <?= csrf_field() ?>
        <span class="field-label-standalone"><?= e(t('choose_permissions')) ?></span>
        <?php $checked = []; require BASE_PATH . '/app/views/employees/_permission_checkboxes.php'; ?>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('generate_invite_link')) ?></button>
    </form>
</div>

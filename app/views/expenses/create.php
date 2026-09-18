<section class="page-head">
    <h1><?= e(t('add_expense')) ?></h1>
</section>

<?php $actionUrl = '/expenses'; $submitLabel = t('add_expense'); require BASE_PATH . '/app/views/expenses/_form.php'; ?>

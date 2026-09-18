<section class="page-head">
    <h1><?= e(t('edit_expense')) ?></h1>
</section>

<?php
$actionUrl = '/expenses/' . (int) $expense['id'];
$submitLabel = t('save');
require BASE_PATH . '/app/views/expenses/_form.php';
?>

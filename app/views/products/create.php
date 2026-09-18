<section class="page-head">
    <h1><?= e(t('add_product')) ?></h1>
</section>

<?php
$actionUrl = '/products';
$submitLabel = t('add_product');
require BASE_PATH . '/app/views/products/_form.php';
?>

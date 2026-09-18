<section class="page-head">
    <h1><?= e(t('edit_product')) ?></h1>
</section>

<?php
$actionUrl = '/products/' . (int) $product['id'];
$submitLabel = t('save');
require BASE_PATH . '/app/views/products/_form.php';
?>

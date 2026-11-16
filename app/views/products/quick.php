<?php
$pageTitle = t('quick_products_title');

$gridColumns = [
    ['key' => 'barcode', 'label' => t('barcode'), 'type' => 'text', 'placeholder' => t('quick_scan_here')],
    ['key' => 'name', 'label' => t('product_name'), 'type' => 'text', 'required' => true, 'wide' => true],
    ['key' => 'sell_price', 'label' => t('sell_price'), 'type' => 'number', 'required' => true],
];
if ($canPrices) {
    $gridColumns[] = ['key' => 'cost_price', 'label' => t('cost_price'), 'type' => 'number'];
}
$gridColumns[] = ['key' => 'stock_qty', 'label' => t('stock_qty'), 'type' => 'number'];
$gridColumns[] = ['key' => 'unit', 'label' => t('unit'), 'type' => 'text', 'default' => 'dona', 'list' => ['dona', 'kg', 'litr', 'metr', 'quti', 'qop', 'pachka']];

$gridEndpoint = '/products/quick';
$gridPrefill = $prefillBarcode !== '' ? ['barcode' => $prefillBarcode] : null;
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('quick_products_title')) ?></h1>
        <p class="muted"><?= e(t('quick_products_hint')) ?></p>
    </div>
    <div class="row-actions">
        <a href="/products/import" class="btn btn-ghost"><?= e(t('import_products')) ?></a>
        <a href="/products" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
    </div>
</section>

<?php require BASE_PATH . '/app/views/partials/quick-grid.php'; ?>

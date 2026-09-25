<?php $pageTitle = t('printer_test_title'); ?>
<div class="receipt">
    <div class="receipt-header">
        <div class="receipt-shop-name"><?= e($shop['name'] ?? t('app_name')) ?></div>
        <div class="receipt-meta"><?= e(t('printer_test_title')) ?></div>
        <div class="receipt-meta"><?= e(local_datetime(gmdate('Y-m-d H:i:s'))) ?><?= $deviceCode ? ' · ' . e($deviceCode) : '' ?></div>
    </div>

    <hr class="receipt-divider">

    <table class="receipt-items">
        <thead>
            <tr>
                <th><?= e(t('product_name')) ?></th>
                <th><?= e(t('qty_short')) ?></th>
                <th><?= e(t('sum')) ?></th>
            </tr>
        </thead>
        <tbody>
            <tr><td><?= e(t('printer_test_item')) ?> 1</td><td>2</td><td><?= money(24000) ?></td></tr>
            <tr><td><?= e(t('printer_test_item')) ?> 2 — <?= e(t('printer_test_long')) ?></td><td>1</td><td><?= money(1250000) ?></td></tr>
        </tbody>
    </table>

    <hr class="receipt-divider">

    <div class="receipt-total-row"><span><?= e(t('total')) ?></span><span><?= money(1274000) ?></span></div>
    <div class="receipt-line"><span><?= e(t('printer_paper_label')) ?></span><span><?= (int) $paperWidth ?> mm</span></div>

    <div class="receipt-header" style="margin-top:10px;">
        <div class="receipt-meta"><?= e(t('printer_test_ok')) ?></div>
    </div>
</div>

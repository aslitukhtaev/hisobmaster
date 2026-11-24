<?php $pageTitle = t('printer_test_title'); ?>
<div class="receipt">
    <div class="receipt-header">
        <div class="receipt-shop-name"><?= e($shop['name'] ?? t('app_name')) ?></div>
        <div class="receipt-meta"><?= e(t('printer_test_title')) ?></div>
        <div class="receipt-meta"><?= e(local_datetime(gmdate('Y-m-d H:i:s'))) ?><?= $deviceCode ? ' · ' . e($deviceCode) : '' ?></div>
    </div>

    <hr class="receipt-divider">

    <div class="receipt-items">
        <div class="receipt-item">
            <div class="receipt-item-name"><?= e(t('printer_test_item')) ?> 1</div>
            <div class="receipt-item-calc"><span>2 × <?= money(12000) ?></span><span class="receipt-item-sum"><?= money(24000) ?></span></div>
        </div>
        <div class="receipt-item">
            <div class="receipt-item-name"><?= e(t('printer_test_item')) ?> 2 — <?= e(t('printer_test_long')) ?></div>
            <div class="receipt-item-calc"><span>1 × <?= money(1250000) ?></span><span class="receipt-item-sum"><?= money(1250000) ?></span></div>
        </div>
    </div>

    <hr class="receipt-divider">

    <div class="receipt-total-row"><span><?= e(t('total')) ?></span><span><?= money(1274000) ?></span></div>
    <div class="receipt-line"><span><?= e(t('printer_paper_label')) ?></span><span><?= (int) $paperWidth ?> mm</span></div>

    <div class="receipt-header" style="margin-top:10px;">
        <div class="receipt-meta"><?= e(t('printer_test_ok')) ?></div>
    </div>
</div>

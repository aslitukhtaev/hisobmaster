<?php
$printerWidth = (int) ($shop['receipt_printer_width'] ?? 80);
$widthClass = $printerWidth === 58 ? 'receipt-58' : 'receipt-80';
$debtAmount = (float) $sale['total'] - (float) $sale['paid_amount'];
?>
<style>
    @page { size: <?= $printerWidth ?>mm 297mm; margin: 2mm; }
</style>
<section class="page-head page-head-row no-print">
    <div>
        <h1><?= e(t('receipt_title')) ?> #<?= (int) $sale['id'] ?></h1>
    </div>
    <div class="row-actions">
        <button type="button" class="btn btn-primary" onclick="window.print()"><?= e(t('print')) ?></button>
        <a href="/sales" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
    </div>
</section>

<div class="receipt <?= $widthClass ?>">
    <div class="receipt-header">
        <div class="receipt-shop-name"><?= e($shop['name'] ?? '') ?></div>
        <?php if (!empty($shop['address'])): ?><div class="receipt-meta"><?= e($shop['address']) ?></div><?php endif; ?>
        <?php if (!empty($shop['phone'])): ?><div class="receipt-meta"><?= e($shop['phone']) ?></div><?php endif; ?>
        <div class="receipt-meta">№<?= (int) $sale['id'] ?> · <?= e(substr((string) $sale['created_at'], 0, 16)) ?></div>
        <div class="receipt-meta"><?= e(t('cashier')) ?>: <?= e($sale['cashier_name'] ?? '') ?></div>
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
        <?php foreach ($items as $item): ?>
            <tr>
                <td><?= e($item['product_name']) ?></td>
                <td><?= e(format_qty((float) $item['qty'])) ?></td>
                <td><?= money((float) $item['subtotal']) ?></td>
            </tr>
        <?php endforeach; ?>
        </tbody>
    </table>

    <hr class="receipt-divider">

    <?php if ((float) $sale['discount'] > 0): ?>
        <div class="receipt-line"><span><?= e(t('discount')) ?></span><span>-<?= money((float) $sale['discount']) ?></span></div>
    <?php endif; ?>
    <div class="receipt-total-row"><span><?= e(t('total')) ?></span><span><?= money((float) $sale['total']) ?></span></div>
    <div class="receipt-line"><span><?= e(t('payment_type')) ?></span><span><?= e(t('payment_' . $sale['payment_type'])) ?></span></div>

    <?php if ($sale['payment_type'] === 'qarz'): ?>
        <div class="receipt-line"><span><?= e(t('paid_now')) ?></span><span><?= money((float) $sale['paid_amount']) ?></span></div>
        <div class="receipt-line"><span><?= e(t('debt_amount')) ?></span><span><?= money($debtAmount) ?></span></div>
        <?php if (!empty($sale['customer_name'])): ?>
            <div class="receipt-line"><span><?= e(t('customer')) ?></span><span><?= e($sale['customer_name']) ?></span></div>
        <?php endif; ?>
    <?php endif; ?>

    <div class="receipt-header" style="margin-top:14px;">
        <div class="receipt-meta"><?= e(t('thank_you_note')) ?></div>
    </div>
</div>

<?php
$printerWidth = (int) ($shop['receipt_printer_width'] ?? 80);
$widthClass = $printerWidth === 58 ? 'receipt-58' : 'receipt-80';
// total - paid_amount is always the qarz (debt) portion of the sale by
// construction (see Sale::create()), for split-payment sales too — paid_amount
// is defined there as naqd + karta specifically so this keeps working unchanged.
$debtAmount = round((float) $sale['total'] - (float) $sale['paid_amount'], 2);
?>
<style>
    @page { size: <?= $printerWidth ?>mm 297mm; margin: 2mm; }
</style>
<section class="page-head page-head-row no-print">
    <div>
        <h1><?= e(t('receipt_title')) ?> #<?= (int) $sale['id'] ?></h1>
    </div>
    <div class="row-actions">
        <?php if ($canRefund): ?>
            <a href="/sales/<?= (int) $sale['id'] ?>/refund" class="btn btn-ghost"><?= e(t('refund_action')) ?></a>
        <?php endif; ?>
        <button type="button" class="btn btn-primary" id="receipt-print-btn"><?= e(t('print')) ?></button>
        <a href="/sales" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
    </div>
</section>
<p class="muted no-print" id="telegram-print-hint" style="display:none;"><?= e(t('telegram_print_hint')) ?></p>

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
                <td><?= e($item['product_name']) ?><?= !empty($item['variant_label']) ? ' — ' . e($item['variant_label']) : '' ?></td>
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

    <?php if (count($payments) > 1): ?>
        <?php foreach ($payments as $payment): ?>
            <div class="receipt-line"><span><?= e(t('payment_' . $payment['payment_type'])) ?></span><span><?= money((float) $payment['amount']) ?></span></div>
        <?php endforeach; ?>
    <?php else: ?>
        <div class="receipt-line"><span><?= e(t('payment_type')) ?></span><span><?= e(t('payment_' . $sale['payment_type'])) ?></span></div>
    <?php endif; ?>

    <?php if ($debtAmount > 0): ?>
        <?php if (count($payments) <= 1): ?>
            <div class="receipt-line"><span><?= e(t('paid_now')) ?></span><span><?= money((float) $sale['paid_amount']) ?></span></div>
            <div class="receipt-line"><span><?= e(t('debt_amount')) ?></span><span><?= money($debtAmount) ?></span></div>
        <?php endif; ?>
        <?php if (!empty($sale['customer_name'])): ?>
            <div class="receipt-line"><span><?= e(t('customer')) ?></span><span><?= e($sale['customer_name']) ?></span></div>
        <?php endif; ?>
    <?php endif; ?>

    <?php if (!empty($refunds)): ?>
        <hr class="receipt-divider">
        <div class="receipt-line" style="font-weight:800;"><span><?= e(t('refund_history')) ?></span><span></span></div>
        <?php foreach ($refunds as $refund): ?>
            <div class="receipt-line">
                <span><?= e(substr((string) $refund['created_at'], 0, 16)) ?> · <?= e($refund['refunded_by_name'] ?? '') ?></span>
                <span>-<?= money((float) $refund['total_amount']) ?></span>
            </div>
            <?php foreach ($refund['items'] as $refundItem): ?>
                <div class="receipt-line"><span><?= e($refundItem['product_name']) ?> × <?= e(format_qty((float) $refundItem['qty'])) ?></span><span></span></div>
            <?php endforeach; ?>
            <?php if (!empty($refund['reason'])): ?>
                <div class="receipt-meta"><?= e($refund['reason']) ?></div>
            <?php endif; ?>
        <?php endforeach; ?>
    <?php endif; ?>

    <div class="receipt-header" style="margin-top:14px;">
        <div class="receipt-meta"><?= e(t('thank_you_note')) ?></div>
    </div>
</div>

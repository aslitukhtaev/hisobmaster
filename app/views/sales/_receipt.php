<?php
// The receipt itself — shown on the receipt page (sales/receipt.php) and
// printed on its own by the desktop app (sales/print.php). Needs $sale,
// $items, $payments, $refunds, $shop.
$printerWidth = (int) ($shop['receipt_printer_width'] ?? 80);
$widthClass = $printerWidth === 58 ? 'receipt-58' : 'receipt-80';
// total - paid_amount is always the qarz (debt) portion of the sale by
// construction (see Sale::create()), for split-payment sales too — paid_amount
// is defined there as naqd + karta specifically so this keeps working unchanged.
$debtAmount = round((float) $sale['total'] - (float) $sale['paid_amount'], 2);
$logoSrc = App\Models\Settings::receiptImage((int) ($shop['id'] ?? 0), 'receipt_logo');
$qrSrc = App\Models\Settings::receiptImage((int) ($shop['id'] ?? 0), 'receipt_qr');
?>
<div class="receipt <?= $widthClass ?>">
    <div class="receipt-header">
        <?php if ($logoSrc !== null): ?><img class="receipt-logo" src="<?= e($logoSrc) ?>" alt=""><?php endif; ?>
        <div class="receipt-shop-name"><?= e($shop['name'] ?? '') ?></div>
        <div class="receipt-meta"><?= e(local_datetime($sale['created_at'])) ?> · №<?= e(receipt_number($sale)) ?></div>
        <?php if (($sale['price_type'] ?? 'retail') === 'wholesale'): ?>
            <div class="receipt-meta receipt-wholesale"><?= e(t('receipt_wholesale')) ?></div>
        <?php endif; ?>
    </div>

    <hr class="receipt-divider">

    <div class="receipt-items">
    <?php foreach ($items as $item): ?>
        <div class="receipt-item">
            <div class="receipt-item-name"><?= e($item['product_name']) ?><?= !empty($item['variant_label']) ? ' — ' . e($item['variant_label']) : '' ?></div>
            <div class="receipt-item-calc">
                <span><?= e(format_qty((float) $item['qty'])) ?> × <?= money((float) $item['unit_price']) ?></span>
                <span class="receipt-item-sum"><?= money((float) $item['subtotal']) ?></span>
            </div>
        </div>
    <?php endforeach; ?>
    </div>

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

    <?php
    $cashReceived = isset($sale['cash_received']) && $sale['cash_received'] !== null ? (float) $sale['cash_received'] : 0.0;
    if ($cashReceived > 0):
        $naqdPaid = 0.0;
        foreach ($payments as $payment) {
            if ($payment['payment_type'] === 'naqd') {
                $naqdPaid += (float) $payment['amount'];
            }
        }
    ?>
        <div class="receipt-line"><span><?= e(t('cash_received_label')) ?></span><span><?= money($cashReceived) ?></span></div>
        <div class="receipt-line"><span><?= e(t('change_due')) ?></span><span><?= money(max(0.0, $cashReceived - $naqdPaid)) ?></span></div>
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
                <span><?= e(local_datetime($refund['created_at'])) ?> · <?= e($refund['refunded_by_name'] ?? '') ?></span>
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
        <?php if ($qrSrc !== null): ?><img class="receipt-qr" src="<?= e($qrSrc) ?>" alt=""><?php endif; ?>
        <div class="receipt-meta"><?= e(t('thank_you_note')) ?></div>
        <?php
        $about = array_filter([trim((string) ($shop['address'] ?? '')), trim((string) ($shop['phone'] ?? ''))], 'strlen');
        if ($about): ?>
            <div class="receipt-meta receipt-small"><?= e(implode(' · ', $about)) ?></div>
        <?php endif; ?>
        <?php if (!empty($sale['cashier_name'])): ?>
            <div class="receipt-meta receipt-small"><?= e(t('cashier')) ?>: <?= e($sale['cashier_name']) ?></div>
        <?php endif; ?>
    </div>
</div>

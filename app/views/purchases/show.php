<?php $pageTitle = t('purchase_details'); ?>
<section class="page-head">
    <h1><?= e(t('purchase_details')) ?></h1>
    <p class="muted"><?= e(date('d.m.Y H:i', strtotime((string) $purchase['created_at']))) ?></p>
</section>

<div class="card" style="margin-bottom:16px;">
    <div class="cred-row">
        <span class="cred-label"><?= e(t('supplier_name')) ?></span>
        <span><?= e($purchase['supplier_name'] ?? t('no_supplier_option')) ?></span>
    </div>
    <div class="cred-row">
        <span class="cred-label"><?= e(t('cashier')) ?></span>
        <span><?= e($purchase['created_by_name'] ?? '—') ?></span>
    </div>
    <?php if (!empty($purchase['note'])): ?>
    <div class="cred-row">
        <span class="cred-label"><?= e(t('note')) ?></span>
        <span><?= e($purchase['note']) ?></span>
    </div>
    <?php endif; ?>
    <div class="cred-row">
        <span class="cred-label"><?= e(t('amount')) ?></span>
        <span class="cred-value"><?= money((float) $purchase['total_amount']) ?></span>
    </div>
</div>

<div class="card table-card">
    <div class="table-wrap">
        <table class="table">
            <thead>
                <tr>
                    <th><?= e(t('product_name')) ?></th>
                    <th><?= e(t('qty_short')) ?></th>
                    <th><?= e(t('unit_cost_label')) ?></th>
                    <th><?= e(t('sum')) ?></th>
                </tr>
            </thead>
            <tbody>
            <?php foreach ($items as $item): ?>
                <tr>
                    <td><?= e($item['product_name']) ?></td>
                    <td data-label="<?= e(t('qty_short')) ?>"><?= e(format_qty((float) $item['qty'])) ?> <?= e($item['unit']) ?></td>
                    <td data-label="<?= e(t('unit_cost_label')) ?>"><?= money((float) $item['unit_cost']) ?></td>
                    <td data-label="<?= e(t('sum')) ?>"><?= money((float) $item['subtotal']) ?></td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>

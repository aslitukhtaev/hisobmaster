<?php $pageTitle = t('refund_title'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('refund_title')) ?> #<?= (int) $sale['id'] ?></h1>
        <p class="muted"><?= e(t('refund_hint')) ?></p>
    </div>
    <a href="/sales/<?= (int) $sale['id'] ?>" class="btn btn-ghost"><?= e(t('back_to_receipt')) ?></a>
</section>

<form method="post" action="/sales/<?= (int) $sale['id'] ?>/refund" class="stack" id="refund-form"
      onsubmit="return confirm('<?= e(t('confirm_refund')) ?>');">
    <?= csrf_field() ?>

    <div class="card table-card">
        <div class="table-wrap">
            <table class="table" id="refund-items-table">
                <thead>
                    <tr>
                        <th><?= e(t('product_name')) ?></th>
                        <th><?= e(t('sold_qty_label')) ?></th>
                        <th><?= e(t('already_refunded_label')) ?></th>
                        <th><?= e(t('remaining_qty_label')) ?></th>
                        <th><?= e(t('refund_qty_label')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($lines as $line): $remaining = (float) $line['remaining_qty']; ?>
                    <tr data-unit-price="<?= (float) $line['unit_price'] ?>">
                        <td data-label="<?= e(t('product_name')) ?>"><?= e($line['product_name']) ?></td>
                        <td data-label="<?= e(t('sold_qty_label')) ?>"><?= e(format_qty((float) $line['qty'])) ?></td>
                        <td data-label="<?= e(t('already_refunded_label')) ?>"><?= e(format_qty((float) $line['refunded_qty'])) ?></td>
                        <td data-label="<?= e(t('remaining_qty_label')) ?>"><?= e(format_qty($remaining)) ?></td>
                        <td data-label="<?= e(t('refund_qty_label')) ?>">
                            <?php if ($remaining > 0): ?>
                                <input type="number" class="refund-qty-input" name="qty[<?= (int) $line['id'] ?>]"
                                       min="0" max="<?= $remaining ?>" step="0.01" value="0" inputmode="decimal"
                                       aria-label="<?= e(t('refund_qty_label')) ?> — <?= e($line['product_name']) ?>">
                            <?php else: ?>
                                <span class="muted">—</span>
                            <?php endif; ?>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>

    <label class="field">
        <span><?= e(t('refund_reason_label')) ?> (<?= e(t('optional')) ?>)</span>
        <textarea name="reason" rows="2"></textarea>
    </label>

    <div class="cart-total-row">
        <span><?= e(t('estimated_refund_total_label')) ?></span>
        <strong id="refund-total-preview">0 so'm</strong>
    </div>

    <button type="submit" class="btn btn-primary btn-block" id="refund-submit-btn" disabled><?= e(t('refund_action')) ?></button>
</form>

<script src="<?= asset('js/refund.js') ?>" defer></script>

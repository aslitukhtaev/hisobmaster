<?php $pageTitle = t('shift_report_title'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('shift_report_title')) ?></h1>
        <p class="muted"><?= e($today) ?> · <?= e(t('shift_report_hint')) ?></p>
    </div>
    <a href="/sales" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
</section>

<div class="card-grid">
    <div class="stat-tile">
        <div class="stat-value"><?= money((float) $totals['naqd']) ?></div>
        <div class="stat-label"><?= e(t('cash_sales_label')) ?></div>
    </div>
    <div class="stat-tile">
        <div class="stat-value"><?= money((float) $totals['karta']) ?></div>
        <div class="stat-label"><?= e(t('card_sales_label')) ?></div>
    </div>
    <div class="stat-tile">
        <div class="stat-value"><?= money((float) $totals['qarz']) ?></div>
        <div class="stat-label"><?= e(t('debt_issued_label')) ?></div>
    </div>
    <div class="stat-tile">
        <div class="stat-value"><?= money((float) $totals['refunds']) ?></div>
        <div class="stat-label"><?= e(t('refunds_total_label')) ?></div>
    </div>
</div>

<section class="stat-tile net-profit-tile <?= (float) $totals['net_cash'] >= 0 ? 'net-profit-positive' : 'net-profit-negative' ?>" style="margin-top:14px;">
    <span class="stat-label"><?= e(t('net_cash_expected_label')) ?></span>
    <span class="net-profit-amount"><?= money((float) $totals['net_cash']) ?></span>
</section>

<?php if (!empty($byCashier)): ?>
    <h2 style="margin-top:20px;"><?= e(t('per_cashier_breakdown_title')) ?></h2>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('cashier')) ?></th>
                        <th><?= e(t('payment_naqd')) ?></th>
                        <th><?= e(t('payment_karta')) ?></th>
                        <th><?= e(t('payment_qarz')) ?></th>
                        <th><?= e(t('refund_action')) ?></th>
                        <th><?= e(t('net_cash_short_label')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($byCashier as $row): ?>
                    <tr>
                        <td><?= e($row['cashier_name']) ?></td>
                        <td data-label="<?= e(t('payment_naqd')) ?>"><?= money((float) $row['naqd']) ?></td>
                        <td data-label="<?= e(t('payment_karta')) ?>"><?= money((float) $row['karta']) ?></td>
                        <td data-label="<?= e(t('payment_qarz')) ?>"><?= money((float) $row['qarz']) ?></td>
                        <td data-label="<?= e(t('refund_action')) ?>"><?= money((float) $row['refunds']) ?></td>
                        <td data-label="<?= e(t('net_cash_short_label')) ?>"><?= money((float) $row['net_cash']) ?></td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php else: ?>
    <div class="card" style="margin-top:16px;"><p class="muted"><?= e(t('no_sales_in_period')) ?></p></div>
<?php endif; ?>

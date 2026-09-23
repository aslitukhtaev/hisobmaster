<?php $pageTitle = t('sales'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('sales')) ?></h1>
        <p class="muted">
            <?= (int) $today['count'] ?> <?= e(t('todays_sales_count_label')) ?> ·
            <?= money((float) $today['revenue']) ?>
        </p>
    </div>
    <div class="row-actions">
        <a href="/sales/shift-report" class="btn btn-ghost"><?= e(t('shift_report_button')) ?></a>
        <a href="/sales/new" class="btn btn-primary"><?= e(t('new_sale')) ?></a>
    </div>
</section>

<form method="get" action="/sales" class="date-filter-bar">
    <label class="field">
        <span><?= e(t('from_date')) ?></span>
        <input type="date" name="from" value="<?= e($from) ?>">
    </label>
    <label class="field">
        <span><?= e(t('to_date')) ?></span>
        <input type="date" name="to" value="<?= e($to) ?>">
    </label>
    <button type="submit" class="btn btn-ghost"><?= e(t('filter_apply')) ?></button>
    <?php if ($filtered): ?>
        <a href="/sales" class="btn btn-ghost"><?= e(t('reset_filter')) ?></a>
    <?php endif; ?>
    <a href="/sales/export<?= $filtered ? '?from=' . e($from) . '&amp;to=' . e($to) : '' ?>" class="btn btn-ghost"><?= e(t('export_csv_button')) ?></a>
</form>

<?php if (empty($sales)): ?>
    <div class="card"><p class="muted"><?= e(t('no_sales_yet')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('sale_date')) ?></th>
                        <th><?= e(t('cashier')) ?></th>
                        <th><?= e(t('customer')) ?></th>
                        <th><?= e(t('total')) ?></th>
                        <th><?= e(t('payment_type')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($sales as $sale): ?>
                    <?php
                    $pillClass = match ($sale['payment_type']) {
                        'qarz' => 'status-blocked',
                        default => 'status-active',
                    };
                    ?>
                    <tr>
                        <td><?= e(local_datetime($sale['created_at'])) ?></td>
                        <td data-label="<?= e(t('cashier')) ?>"><?= e($sale['cashier_name'] ?? '—') ?></td>
                        <td data-label="<?= e(t('customer')) ?>"><?= e($sale['customer_name'] ?? '—') ?></td>
                        <td data-label="<?= e(t('total')) ?>"><?= money((float) $sale['total']) ?></td>
                        <td data-label="<?= e(t('payment_type')) ?>">
                            <span class="status-pill <?= $pillClass ?>"><?= e(t('payment_' . $sale['payment_type'])) ?></span>
                        </td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <a href="/sales/<?= (int) $sale['id'] ?>" class="btn btn-ghost btn-sm"><?= e(t('view_receipt')) ?></a>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

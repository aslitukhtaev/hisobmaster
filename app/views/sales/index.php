<?php $pageTitle = t('sales'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('sales')) ?></h1>
        <p class="muted">
            <?= (int) $today['count'] ?> <?= e(t('todays_sales_count_label')) ?> ·
            <?= money((float) $today['revenue']) ?>
        </p>
    </div>
    <a href="/sales/new" class="btn btn-primary"><?= e(t('new_sale')) ?></a>
</section>

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
                        <td><?= e(substr((string) $sale['created_at'], 0, 16)) ?></td>
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

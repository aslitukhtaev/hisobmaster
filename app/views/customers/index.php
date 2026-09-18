<?php $pageTitle = t('customers'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('customers')) ?></h1>
        <p class="muted"><?= count($customers) ?> <?= e(t('customers_count_label')) ?></p>
    </div>
    <a href="/customers/create" class="btn btn-primary"><?= e(t('add_customer')) ?></a>
</section>

<section class="stat-tile stock-value-tile">
    <span class="stat-label"><?= e(t('total_debt_label')) ?></span>
    <span class="stat-value stock-value-amount"><?= money((float) $totalDebt) ?></span>
</section>

<div class="filter-tabs">
    <a href="/customers?filter=debtors" class="filter-tab <?= $onlyDebtors ? 'active' : '' ?>"><?= e(t('filter_debtors_only')) ?></a>
    <a href="/customers?filter=all" class="filter-tab <?= !$onlyDebtors ? 'active' : '' ?>"><?= e(t('filter_all_customers')) ?></a>
</div>

<?php if (empty($customers)): ?>
    <div class="card">
        <p class="muted"><?= e($onlyDebtors ? t('no_debtors') : t('no_customers_yet')) ?></p>
    </div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('full_name_label')) ?></th>
                        <th><?= e(t('phone')) ?></th>
                        <th><?= e(t('debt_balance')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($customers as $customer): ?>
                    <tr>
                        <td><?= e($customer['full_name']) ?></td>
                        <td data-label="<?= e(t('phone')) ?>" class="muted"><?= e($customer['phone'] ?? '—') ?></td>
                        <td data-label="<?= e(t('debt_balance')) ?>">
                            <?php if ((float) $customer['balance'] > 0): ?>
                                <span class="status-pill status-blocked"><?= money((float) $customer['balance']) ?></span>
                            <?php else: ?>
                                <span class="status-pill status-active"><?= e(t('no_debt')) ?></span>
                            <?php endif; ?>
                        </td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <a href="/customers/<?= (int) $customer['id'] ?>" class="btn btn-ghost btn-sm"><?= e(t('view_details')) ?></a>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

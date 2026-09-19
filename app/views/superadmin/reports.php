<?php $pageTitle = t('consolidated_reports'); ?>
<section class="page-head">
    <h1><?= e(t('consolidated_reports')) ?></h1>
    <p class="muted"><?= e($from) ?> — <?= e($to) ?></p>
</section>

<div class="filter-tabs">
    <a href="/superadmin/reports?period=today" class="filter-tab <?= $period === 'today' ? 'active' : '' ?>"><?= e(t('period_today')) ?></a>
    <a href="/superadmin/reports?period=week" class="filter-tab <?= $period === 'week' ? 'active' : '' ?>"><?= e(t('period_week')) ?></a>
    <a href="/superadmin/reports?period=month" class="filter-tab <?= $period === 'month' ? 'active' : '' ?>"><?= e(t('period_month')) ?></a>
</div>

<form method="get" action="/superadmin/reports" class="date-filter-bar">
    <label class="field">
        <span><?= e(t('from_date')) ?></span>
        <input type="date" name="from" value="<?= e($from) ?>">
    </label>
    <label class="field">
        <span><?= e(t('to_date')) ?></span>
        <input type="date" name="to" value="<?= e($to) ?>">
    </label>
    <button type="submit" class="btn btn-ghost"><?= e(t('filter_apply')) ?></button>
</form>

<section class="stat-grid">
    <div class="stat-tile">
        <div class="stat-value"><?= (int) $totals['sales_count'] ?></div>
        <div class="stat-label"><?= e(t('sales_count_label')) ?></div>
    </div>
    <div class="stat-tile" style="grid-column: span 2;">
        <div class="stat-value" style="font-size:1.15rem;"><?= money((float) $totals['revenue']) ?></div>
        <div class="stat-label"><?= e(t('total_revenue')) ?></div>
    </div>
</section>

<section class="stat-tile net-profit-tile <?= (float) $totals['net_profit'] >= 0 ? 'net-profit-positive' : 'net-profit-negative' ?>">
    <span class="stat-label"><?= e(t('net_profit')) ?></span>
    <span class="net-profit-amount"><?= money((float) $totals['net_profit']) ?></span>
</section>

<h2 style="margin-top:20px;"><?= e(t('per_shop_breakdown_title')) ?></h2>
<?php if (empty($byShop)): ?>
    <div class="card"><p class="muted"><?= e(t('no_shops_yet')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('shop_name')) ?></th>
                        <th><?= e(t('sales_count_label')) ?></th>
                        <th><?= e(t('total_revenue')) ?></th>
                        <th><?= e(t('expenses')) ?></th>
                        <th><?= e(t('net_profit')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($byShop as $row): ?>
                    <tr>
                        <td><?= e($row['shop_name']) ?></td>
                        <td data-label="<?= e(t('sales_count_label')) ?>"><?= (int) $row['sales_count'] ?></td>
                        <td data-label="<?= e(t('total_revenue')) ?>"><?= money((float) $row['revenue']) ?></td>
                        <td data-label="<?= e(t('expenses')) ?>"><?= money((float) $row['expenses']) ?></td>
                        <td data-label="<?= e(t('net_profit')) ?>"><?= money((float) $row['net_profit']) ?></td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

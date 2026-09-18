<?php
$pageTitle = t('reports');
$revenues = array_column($dailyRevenue, 'revenue');
$maxRevenue = $revenues ? max(max($revenues), 1) : 1;
$hasSales = array_sum($revenues) > 0;
$labelStep = max(1, (int) ceil(count($dailyRevenue) / 8));

$paymentColors = ['naqd' => '#14b8a6', 'karta' => '#4f46e5', 'qarz' => '#dc2626'];
$paymentTotal = array_sum($paymentBreakdown) ?: 1;

$netProfit = (float) $summary['net_profit'];
?>
<section class="page-head">
    <h1><?= e(t('reports')) ?></h1>
    <p class="muted"><?= e($from) ?> — <?= e($to) ?></p>
</section>

<div class="filter-tabs">
    <a href="/reports?period=today" class="filter-tab <?= $period === 'today' ? 'active' : '' ?>"><?= e(t('period_today')) ?></a>
    <a href="/reports?period=week" class="filter-tab <?= $period === 'week' ? 'active' : '' ?>"><?= e(t('period_week')) ?></a>
    <a href="/reports?period=month" class="filter-tab <?= $period === 'month' ? 'active' : '' ?>"><?= e(t('period_month')) ?></a>
</div>

<form method="get" action="/reports" class="date-filter-bar">
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
        <div class="stat-value"><?= (int) $summary['sales_count'] ?></div>
        <div class="stat-label"><?= e(t('sales_count_label')) ?></div>
    </div>
    <div class="stat-tile" style="grid-column: span 2;">
        <div class="stat-value" style="font-size:1.15rem;"><?= money((float) $summary['revenue']) ?></div>
        <div class="stat-label"><?= e(t('total_revenue')) ?></div>
    </div>
</section>

<section class="stat-grid">
    <div class="stat-tile">
        <div class="stat-value" style="font-size:1.05rem;"><?= money((float) $summary['cogs']) ?></div>
        <div class="stat-label"><?= e(t('total_cogs')) ?></div>
    </div>
    <div class="stat-tile">
        <div class="stat-value" style="font-size:1.05rem;"><?= money((float) $summary['expenses']) ?></div>
        <div class="stat-label"><?= e(t('expenses')) ?></div>
    </div>
</section>

<section class="stat-tile net-profit-tile <?= $netProfit >= 0 ? 'net-profit-positive' : 'net-profit-negative' ?>">
    <span class="stat-label"><?= e(t('net_profit')) ?></span>
    <span class="net-profit-amount"><?= money($netProfit) ?></span>
</section>

<div class="card">
    <h2><?= e(t('daily_revenue_chart')) ?></h2>
    <?php if (!$hasSales): ?>
        <p class="muted"><?= e(t('no_sales_in_period')) ?></p>
    <?php else: ?>
        <div class="bar-chart">
            <?php foreach ($dailyRevenue as $index => $day):
                $heightPct = max(2, round($day['revenue'] / $maxRevenue * 100));
                $dayLabel = substr($day['date'], 8, 2);
                $showLabel = $index % $labelStep === 0 || $index === count($dailyRevenue) - 1;
                $tooltip = $day['date'] . ': ' . money((float) $day['revenue']);
            ?>
                <div class="bar-col">
                    <div class="bar" style="height: <?= $heightPct ?>%;" tabindex="0"
                         data-tooltip="<?= e($tooltip) ?>" aria-label="<?= e($tooltip) ?>"></div>
                    <span class="bar-label"><?= $showLabel ? e($dayLabel) : '' ?></span>
                </div>
            <?php endforeach; ?>
        </div>
    <?php endif; ?>
</div>

<div class="card">
    <h2><?= e(t('payment_breakdown_title')) ?></h2>
    <div class="hbar-list">
        <?php foreach (['naqd', 'karta', 'qarz'] as $type):
            $val = $paymentBreakdown[$type];
            $pct = $paymentTotal > 0 ? round($val / $paymentTotal * 100) : 0;
        ?>
            <div class="hbar-row">
                <span class="hbar-label"><?= e(t('payment_' . $type)) ?></span>
                <div class="hbar-track">
                    <div class="hbar-fill" style="width: <?= $val > 0 ? max(2, $pct) : 0 ?>%; background: <?= $paymentColors[$type] ?>;"></div>
                </div>
                <span class="hbar-value"><?= money($val) ?></span>
            </div>
        <?php endforeach; ?>
    </div>
</div>

<h2 style="margin-top:20px;"><?= e(t('top_products_title')) ?></h2>
<?php if (empty($topProducts)): ?>
    <div class="card"><p class="muted"><?= e(t('no_sales_in_period')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('product_name')) ?></th>
                        <th><?= e(t('qty_sold')) ?></th>
                        <th><?= e(t('revenue')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($topProducts as $row): ?>
                    <tr>
                        <td><?= e($row['product_name']) ?></td>
                        <td data-label="<?= e(t('qty_sold')) ?>"><?= e(format_qty((float) $row['qty_sold'])) ?></td>
                        <td data-label="<?= e(t('revenue')) ?>"><?= money((float) $row['revenue']) ?></td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

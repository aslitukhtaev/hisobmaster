<?php
$pageTitle = t('reports');
$revenues = array_column($dailyRevenue, 'revenue');
$maxRevenue = $revenues ? max(max($revenues), 1) : 1;
$hasSales = array_sum($revenues) > 0;
$labelStep = max(1, (int) ceil(count($dailyRevenue) / 8));

$paymentColors = ['naqd' => '#14b8a6', 'karta' => '#4f46e5', 'qarz' => '#dc2626'];
$paymentTotal = array_sum($paymentBreakdown) ?: 1;

$netProfit = (float) $summary['net_profit'];

// Fold anything past the 7th category into one "Other" bucket, mirroring the
// dataviz categorical-palette guidance of capping distinct hues and folding
// the rest — keeps the hbar-list readable regardless of how many expense
// categories a shop has accumulated.
$expenseMaxSlots = 7;
$expenseRows = $expenseBreakdown;
if (count($expenseBreakdown) > $expenseMaxSlots) {
    $expenseRows = array_slice($expenseBreakdown, 0, $expenseMaxSlots - 1);
    $otherTotal = 0.0;
    foreach (array_slice($expenseBreakdown, $expenseMaxSlots - 1) as $row) {
        $otherTotal += $row['total'];
    }
    $expenseRows[] = ['category_name' => null, 'total' => $otherTotal, 'is_other' => true];
}
$expenseTotal = array_sum(array_column($expenseBreakdown, 'total')) ?: 1;

$comparisonMetrics = [
    ['key' => 'sales_count', 'label' => t('sales_count_label'), 'money' => false],
    ['key' => 'revenue', 'label' => t('total_revenue'), 'money' => true],
    ['key' => 'expenses', 'label' => t('expenses'), 'money' => true],
    ['key' => 'net_profit', 'label' => t('net_profit'), 'money' => true],
];
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('reports')) ?></h1>
        <p class="muted"><?= e($from) ?> — <?= e($to) ?></p>
    </div>
    <div class="row-actions">
        <a href="/reports/leaderboard?from=<?= e($from) ?>&amp;to=<?= e($to) ?>" class="btn btn-ghost"><?= e(t('leaderboard_title')) ?></a>
        <a href="/reports/export?from=<?= e($from) ?>&amp;to=<?= e($to) ?>" class="btn btn-ghost"><?= e(t('export_csv_button')) ?></a>
        <a href="/reports/print?from=<?= e($from) ?>&amp;to=<?= e($to) ?>" class="btn btn-ghost"><?= e(t('print_report_button')) ?></a>
    </div>
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
    <label class="permission-check" style="flex:0 0 auto;">
        <input type="checkbox" name="compare" value="1" <?= $compare ? 'checked' : '' ?>>
        <span><?= e(t('compare_previous_period')) ?></span>
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
        <?php if ((float) ($summary['refunds'] ?? 0) > 0): ?>
            <div class="stat-sub"><?= e(t('revenue_net_of_refunds', ['amount' => money((float) $summary['refunds'])])) ?></div>
        <?php endif; ?>
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

<?php if ($compare && $comparison !== null): ?>
    <div class="card">
        <h2><?= e(t('period_comparison_title')) ?></h2>
        <p class="muted"><?= e($comparison['from']) ?> — <?= e($comparison['to']) ?> <?= e(t('vs_current_period_label')) ?></p>
        <div class="table-wrap" style="margin-top:10px;">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('comparison_metric_label')) ?></th>
                        <th><?= e(t('current_period_label')) ?></th>
                        <th><?= e(t('previous_period_label')) ?></th>
                        <th><?= e(t('change_label')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($comparisonMetrics as $metric):
                    $cur = (float) $summary[$metric['key']];
                    $prev = (float) $comparison['summary'][$metric['key']];
                    $pct = percent_change($prev, $cur);
                    $fmt = static fn (float $v): string => $metric['money'] ? money($v) : number_format($v, 0, '.', ' ');
                    if ($pct === null) {
                        $deltaClass = 'delta-flat';
                        $deltaText = '—';
                    } elseif ($pct > 0.05) {
                        $deltaClass = 'delta-up';
                        $deltaText = '▲ +' . number_format($pct, 1, '.', ' ') . '%';
                    } elseif ($pct < -0.05) {
                        $deltaClass = 'delta-down';
                        $deltaText = '▼ ' . number_format($pct, 1, '.', ' ') . '%';
                    } else {
                        $deltaClass = 'delta-flat';
                        $deltaText = '0%';
                    }
                ?>
                    <tr>
                        <td><?= e($metric['label']) ?></td>
                        <td data-label="<?= e(t('current_period_label')) ?>"><?= e($fmt($cur)) ?></td>
                        <td data-label="<?= e(t('previous_period_label')) ?>"><?= e($fmt($prev)) ?></td>
                        <td data-label="<?= e(t('change_label')) ?>">
                            <span class="delta-badge <?= $deltaClass ?>"><?= e($deltaText) ?></span>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

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

<div class="card">
    <h2><?= e(t('expense_category_breakdown_title')) ?></h2>
    <?php if (empty($expenseRows)): ?>
        <p class="muted"><?= e(t('no_expenses_in_period')) ?></p>
    <?php else: ?>
        <div class="hbar-list">
            <?php foreach ($expenseRows as $index => $row):
                $val = (float) $row['total'];
                $pct = $expenseTotal > 0 ? round($val / $expenseTotal * 100) : 0;
                $label = !empty($row['is_other']) ? t('other_category') : ($row['category_name'] ?? t('no_category'));
                $color = 'var(--chart-' . (($index % 8) + 1) . ')';
                $tooltip = $label . ': ' . money($val);
            ?>
                <div class="hbar-row hbar-row-wide">
                    <span class="hbar-label" title="<?= e($label) ?>"><?= e($label) ?></span>
                    <div class="hbar-track">
                        <div class="hbar-fill" style="width: <?= $val > 0 ? max(2, $pct) : 0 ?>%; background: <?= $color ?>;"
                             data-tooltip="<?= e($tooltip) ?>"></div>
                    </div>
                    <span class="hbar-value"><?= money($val) ?></span>
                </div>
            <?php endforeach; ?>
        </div>
    <?php endif; ?>
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

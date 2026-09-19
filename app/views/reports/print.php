<?php
$pageTitle = t('print_report_button');
$paymentColors = ['naqd' => '#14b8a6', 'karta' => '#4f46e5', 'qarz' => '#dc2626'];
$paymentTotal = array_sum($paymentBreakdown) ?: 1;
$netProfit = (float) $summary['net_profit'];
$expenseTotal = array_sum(array_column($expenseBreakdown, 'total')) ?: 1;
?>
<section class="page-head page-head-row no-print">
    <div>
        <h1><?= e(t('reports')) ?></h1>
        <p class="muted"><?= e($from) ?> — <?= e($to) ?></p>
    </div>
    <div class="row-actions">
        <button type="button" class="btn btn-primary js-print-btn"><?= e(t('print_report_button')) ?></button>
        <a href="/reports?from=<?= e($from) ?>&amp;to=<?= e($to) ?>" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
    </div>
</section>
<p class="muted no-print" id="telegram-print-hint" style="display:none;"><?= e(t('telegram_print_hint')) ?></p>

<div class="print-report stack">
    <div class="receipt-header" style="text-align:left;">
        <div class="receipt-shop-name"><?= e($shop['name'] ?? '') ?></div>
        <?php if (!empty($shop['address'])): ?><div class="receipt-meta"><?= e($shop['address']) ?></div><?php endif; ?>
        <h1 style="margin-top:8px;"><?= e(t('reports')) ?></h1>
        <p class="muted"><?= e($from) ?> — <?= e($to) ?></p>
    </div>

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
        <?php if (empty($expenseBreakdown)): ?>
            <p class="muted"><?= e(t('no_expenses_in_period')) ?></p>
        <?php else: ?>
            <div class="hbar-list">
                <?php foreach ($expenseBreakdown as $index => $row):
                    $val = (float) $row['total'];
                    $pct = $expenseTotal > 0 ? round($val / $expenseTotal * 100) : 0;
                    $label = $row['category_name'] ?? t('no_category');
                    $color = 'var(--chart-' . (($index % 8) + 1) . ')';
                ?>
                    <div class="hbar-row hbar-row-wide">
                        <span class="hbar-label" title="<?= e($label) ?>"><?= e($label) ?></span>
                        <div class="hbar-track">
                            <div class="hbar-fill" style="width: <?= $val > 0 ? max(2, $pct) : 0 ?>%; background: <?= $color ?>;"></div>
                        </div>
                        <span class="hbar-value"><?= money($val) ?></span>
                    </div>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
    </div>

    <div class="card table-card">
        <h2 style="padding:8px 12px 0;"><?= e(t('top_products_title')) ?></h2>
        <?php if (empty($topProducts)): ?>
            <p class="muted" style="padding:0 12px 12px;"><?= e(t('no_sales_in_period')) ?></p>
        <?php else: ?>
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
        <?php endif; ?>
    </div>
</div>

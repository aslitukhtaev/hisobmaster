<?php
$pageTitle = t('leaderboard_title');
$revenues = array_column($rows, 'revenue');
$maxRevenue = $revenues ? max(max($revenues), 1) : 1;
$hasSales = array_sum($revenues) > 0;

$roleLabels = ['owner' => t('role_owner'), 'employee' => t('role_employee')];
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('leaderboard_title')) ?></h1>
        <p class="muted"><?= e($from) ?> — <?= e($to) ?></p>
    </div>
    <a href="/reports" class="btn btn-ghost"><?= e(t('back_to_reports')) ?></a>
</section>

<div class="filter-tabs">
    <a href="/reports/leaderboard?period=today" class="filter-tab <?= $period === 'today' ? 'active' : '' ?>"><?= e(t('period_today')) ?></a>
    <a href="/reports/leaderboard?period=week" class="filter-tab <?= $period === 'week' ? 'active' : '' ?>"><?= e(t('period_week')) ?></a>
    <a href="/reports/leaderboard?period=month" class="filter-tab <?= $period === 'month' ? 'active' : '' ?>"><?= e(t('period_month')) ?></a>
</div>

<form method="get" action="/reports/leaderboard" class="date-filter-bar">
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

<div class="card">
    <h2><?= e(t('leaderboard_ranking_title')) ?></h2>
    <?php if (!$hasSales): ?>
        <p class="muted"><?= e(t('no_sales_in_period')) ?></p>
    <?php else: ?>
        <div class="hbar-list">
            <?php foreach ($rows as $index => $row):
                $pct = $maxRevenue > 0 ? round($row['revenue'] / $maxRevenue * 100) : 0;
                $isSelf = $row['cashier_id'] === $viewerId;
                $label = ($index + 1) . '. ' . $row['cashier_name'];
                $tooltip = $row['cashier_name'] . ': ' . money((float) $row['revenue']);
            ?>
                <div class="hbar-row hbar-row-wide">
                    <span class="hbar-label" title="<?= e($label) ?>">
                        <?= e($label) ?><?php if ($isSelf && !$isOwner): ?> <span class="muted">(<?= e(t('you_badge')) ?>)</span><?php endif; ?>
                    </span>
                    <div class="hbar-track">
                        <div class="hbar-fill" style="width: <?= $row['revenue'] > 0 ? max(2, $pct) : 0 ?>%; background: var(--chart-<?= ($index % 8) + 1 ?>);"
                             data-tooltip="<?= e($tooltip) ?>"></div>
                    </div>
                    <span class="hbar-value"><?= money((float) $row['revenue']) ?></span>
                </div>
            <?php endforeach; ?>
        </div>
    <?php endif; ?>
</div>

<h2 style="margin-top:20px;"><?= e(t('leaderboard_details_title')) ?></h2>
<div class="card table-card">
    <div class="table-wrap">
        <table class="table">
            <thead>
                <tr>
                    <th><?= e(t('rank_label')) ?></th>
                    <th><?= e(t('cashier_name_label')) ?></th>
                    <th><?= e(t('sales_count_label')) ?></th>
                    <th><?= e(t('total_revenue')) ?></th>
                    <th><?= e(t('net_contribution_label')) ?></th>
                    <th><?= e(t('commission_label')) ?></th>
                </tr>
            </thead>
            <tbody>
            <?php foreach ($rows as $index => $row):
                $isSelf = $row['cashier_id'] === $viewerId;
                $canSeeCommission = $isOwner || $isSelf;
            ?>
                <tr>
                    <td data-label="<?= e(t('rank_label')) ?>"><?= $index + 1 ?></td>
                    <td>
                        <?= e($row['cashier_name']) ?>
                        <div class="muted" style="font-size:.76rem;">
                            <?= e($roleLabels[$row['role']] ?? $row['role']) ?><?php if ($isSelf && !$isOwner): ?> · <?= e(t('you_badge')) ?><?php endif; ?>
                        </div>
                    </td>
                    <td data-label="<?= e(t('sales_count_label')) ?>"><?= (int) $row['sales_count'] ?></td>
                    <td data-label="<?= e(t('total_revenue')) ?>"><?= money((float) $row['revenue']) ?></td>
                    <td data-label="<?= e(t('net_contribution_label')) ?>"><?= money((float) $row['net_contribution']) ?></td>
                    <td data-label="<?= e(t('commission_label')) ?>">
                        <?php if (!$canSeeCommission): ?>
                            <span class="muted" title="<?= e(t('commission_hidden_note')) ?>">—</span>
                        <?php elseif ($row['commission_rate'] === null): ?>
                            <span class="muted">—</span>
                        <?php else: ?>
                            <?= money((float) $row['commission_amount']) ?>
                            <div class="muted" style="font-size:.76rem;"><?= e(format_qty((float) $row['commission_rate'])) ?>%</div>
                        <?php endif; ?>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
    <?php if (!$isOwner): ?>
        <p class="muted" style="margin-top:12px; font-size:.78rem;"><?= e(t('commission_hidden_note')) ?></p>
    <?php endif; ?>
</div>

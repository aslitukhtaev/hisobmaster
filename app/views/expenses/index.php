<?php
$pageTitle = t('expenses');
$categoryTotal = array_sum(array_column($categoryBreakdown, 'total')) ?: 1;
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('expenses')) ?></h1>
        <p class="muted"><?= count($expenses) ?> <?= e(t('expenses_count_label')) ?></p>
    </div>
    <a href="/expenses/create" class="btn btn-primary"><?= e(t('add_expense')) ?></a>
</section>

<section class="stat-tile stock-value-tile">
    <span class="stat-label"><?= e(t('total_expenses_period')) ?></span>
    <span class="stat-value stock-value-amount"><?= money((float) $total) ?></span>
</section>

<form method="get" action="/expenses" class="date-filter-bar">
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

<?php if (!empty($categoryBreakdown)): ?>
    <div class="card">
        <h2><?= e(t('expense_category_breakdown_title')) ?></h2>
        <div class="hbar-list">
            <?php foreach ($categoryBreakdown as $index => $row):
                $val = (float) $row['total'];
                $pct = $categoryTotal > 0 ? round($val / $categoryTotal * 100) : 0;
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
    </div>
<?php endif; ?>

<?php if (empty($expenses)): ?>
    <div class="card"><p class="muted"><?= e(t('no_expenses_yet')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('expense_date_label')) ?></th>
                        <th><?= e(t('category')) ?></th>
                        <th><?= e(t('description')) ?></th>
                        <th><?= e(t('amount')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($expenses as $expense): ?>
                    <tr>
                        <td><?= e($expense['expense_date']) ?></td>
                        <td data-label="<?= e(t('category')) ?>" class="muted"><?= e($expense['category_name'] ?? '—') ?></td>
                        <td data-label="<?= e(t('description')) ?>" class="muted"><?= e($expense['description'] ?? '—') ?></td>
                        <td data-label="<?= e(t('amount')) ?>"><?= money((float) $expense['amount']) ?></td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <div class="row-actions">
                                <a href="/expenses/<?= (int) $expense['id'] ?>/edit" class="btn btn-ghost btn-sm"><?= e(t('edit')) ?></a>
                                <form method="post" action="/expenses/<?= (int) $expense['id'] ?>/delete"
                                      onsubmit="return confirm('<?= e(t('confirm_delete_expense')) ?>');">
                                    <?= csrf_field() ?>
                                    <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('delete')) ?></button>
                                </form>
                            </div>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

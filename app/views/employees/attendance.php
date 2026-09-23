<?php
$pageTitle = t('attendance_history_title');

$formatMinutes = static function (int $minutes): string {
    $h = intdiv($minutes, 60);
    $m = $minutes % 60;
    return t('shift_duration_format', ['h' => (string) $h, 'm' => str_pad((string) $m, 2, '0', STR_PAD_LEFT)]);
};

// Minutes worked in a shift; an open shift counts up to now, so a shift
// left open since yesterday shows how long it's really been open.
$shiftMinutes = static function (array $row): int {
    $start = utc_timestamp($row['clock_in']) ?? time();
    $end = $row['clock_out'] !== null ? (utc_timestamp($row['clock_out']) ?? $start) : time();
    return max(0, (int) round(($end - $start) / 60));
};
// An open shift longer than this was almost certainly never clocked out.
$staleOpenMinutes = 14 * 60;

$totalMinutes = 0;
foreach ($rows as $row) {
    $totalMinutes += $shiftMinutes($row);
}
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('attendance_history_title')) ?></h1>
        <p class="muted"><?= e(t('attendance_history_hint')) ?></p>
    </div>
    <a href="/employees" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
</section>

<div class="filter-tabs">
    <a href="/employees/attendance?period=today" class="filter-tab <?= $period === 'today' ? 'active' : '' ?>"><?= e(t('period_today')) ?></a>
    <a href="/employees/attendance?period=week" class="filter-tab <?= $period === 'week' ? 'active' : '' ?>"><?= e(t('period_week')) ?></a>
    <a href="/employees/attendance?period=month" class="filter-tab <?= $period === 'month' ? 'active' : '' ?>"><?= e(t('period_month')) ?></a>
</div>

<form method="get" action="/employees/attendance" class="date-filter-bar">
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
        <div class="stat-value"><?= (int) $openCount ?></div>
        <div class="stat-label"><?= e(t('attendance_open_shifts_label')) ?></div>
    </div>
    <div class="stat-tile">
        <div class="stat-value"><?= count($rows) ?></div>
        <div class="stat-label"><?= e(t('attendance_total_shifts_label')) ?></div>
    </div>
    <div class="stat-tile">
        <div class="stat-value" style="font-size:1.1rem;"><?= e($formatMinutes($totalMinutes)) ?></div>
        <div class="stat-label"><?= e(t('attendance_total_hours_label')) ?></div>
    </div>
</section>

<?php if (empty($rows)): ?>
    <div class="card"><p class="muted"><?= e(t('no_attendance_in_period')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('full_name_label')) ?></th>
                        <th><?= e(t('clock_in_label')) ?></th>
                        <th><?= e(t('clock_out_label')) ?></th>
                        <th><?= e(t('shift_duration_label')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($rows as $row):
                    $isOpen = $row['clock_out'] === null;
                    $duration = $shiftMinutes($row);
                ?>
                    <tr>
                        <td><?= e($row['user_name']) ?></td>
                        <td data-label="<?= e(t('clock_in_label')) ?>" class="muted"><?= e(local_datetime($row['clock_in'])) ?></td>
                        <td data-label="<?= e(t('clock_out_label')) ?>">
                            <?php if ($isOpen && $duration > $staleOpenMinutes): ?>
                                <span class="status-pill status-blocked"><?= e(t('attendance_not_clocked_out')) ?></span>
                            <?php elseif ($isOpen): ?>
                                <span class="status-pill status-active"><?= e(t('attendance_currently_working')) ?></span>
                            <?php else: ?>
                                <span class="muted"><?= e(local_datetime($row['clock_out'])) ?></span>
                            <?php endif; ?>
                        </td>
                        <td data-label="<?= e(t('shift_duration_label')) ?>">
                            <?= e($formatMinutes($duration)) ?>
                            <?php if ($isOpen): ?><span class="muted"> · <?= e(t('attendance_ongoing')) ?></span><?php endif; ?>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

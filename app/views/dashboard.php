<?php

use App\Core\Auth;
use App\Models\Attendance;
use App\Models\DebtTransaction;
use App\Models\Product;
use App\Models\Sale;

$pageTitle = t('dashboard');

$shopId = Auth::shopId();
$canSales = can('sales');
$canProducts = can('products');
$canCustomers = can('customers');

$productCounts = ($shopId && $canProducts) ? Product::counts((int) $shopId) : null;
$todaySales = ($shopId && $canSales) ? Sale::todaysSummary((int) $shopId) : null;
$totalDebt = ($shopId && $canCustomers) ? DebtTransaction::totalDebtByShop((int) $shopId) : 0.0;

// Clock-in/out: any logged-in shop user (owner or employee), not gated by a
// business-data permission — see AttendanceController.
$openShift = $shopId ? Attendance::openShiftFor((int) Auth::id()) : null;
$showAttendance = $shopId !== null;

$hour = (int) date('G');
$greeting = $hour < 11 ? t('greet_morning') : ($hour < 18 ? t('greet_day') : t('greet_evening'));
$firstName = explode(' ', trim((string) ($user['full_name'] ?? '')))[0] ?? '';

$dashIcon = static function (string $name): string {
    $paths = [
        'till' => '<rect x="3" y="11" width="18" height="9" rx="1.5"/><path d="M7 11V5h10v6"/><path d="M10 8h4"/><path d="M8 15.5h8"/>',
        'coins' => '<ellipse cx="9" cy="7" rx="6" ry="2.6"/><path d="M3 7v5c0 1.4 2.7 2.600 6 2.600s6-1.200 6-2.600V7"/><path d="M15 10.400c3.400.2 6 1.300 6 2.600 0 1.400-2.700 2.600-6 2.600-1 0-1.900-.1-2.700-.3"/><path d="M21 13v4.500c0 1.400-2.700 2.600-6 2.600s-6-1.200-6-2.600V15"/>',
        'receipt' => '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6M9 16h3"/>',
        'debt' => '<circle cx="9" cy="8" r="3.200"/><path d="M2.500 19c0-3.300 2.900-6 6.500-6 1.200 0 2.400.3 3.400.9"/><circle cx="17.500" cy="16.500" r="4"/><path d="M17.500 14.500v2l1.300 1"/>',
        'box' => '<path d="M3.500 7.500 12 3l8.500 4.500L12 12z"/><path d="M3.500 7.500V16L12 20.500 20.500 16V7.500"/><path d="M12 12v8.500"/>',
        'boxplus' => '<path d="M3.500 7.500 12 3l8.500 4.500L12 12z"/><path d="M3.500 7.500V16L12 20.500"/><path d="M12 12v8.500"/><path d="M18 14v6M15 17h6"/>',
        'notebook' => '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 3v18"/><path d="M12.500 8h3.500M12.500 12h3.500"/><path d="M3 7h3M3 12h3M3 17h3"/>',
        'wallet' => '<rect x="2.500" y="6" width="19" height="13" rx="2"/><path d="M2.500 10h19"/><circle cx="17" cy="14.500" r="1.200"/>',
        'chart' => '<path d="M4 20V10"/><path d="M11 20V4"/><path d="M18 20v-7"/>',
        'zreport' => '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8l-8 8h8"/>',
        'alert' => '<path d="M12 3 2.500 20h19z"/><path d="M12 10v4.500"/><circle cx="12" cy="17.300" r=".1" stroke-width="2.400"/>',
        'clock' => '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.500 2"/>',
        'arrow' => '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
        'sparkle' => '<path d="M12 3v4M12 17v4M3 12h4M17 12h4"/><path d="m6.300 6.300 2.100 2.100M15.600 15.600l2.100 2.100M6.300 17.700l2.100-2.100M15.600 8.400l2.100-2.100"/>',
    ];
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' . ($paths[$name] ?? '') . '</svg>';
};

$actions = [
    ['href' => '/sales/new', 'icon' => 'till', 'title' => t('nav_till'), 'hint' => t('dash_qa_till'), 'tone' => 'lime', 'ok' => $canSales],
    ['href' => '/products/quick', 'icon' => 'boxplus', 'title' => t('quick_products_title'), 'hint' => t('dash_qa_products'), 'tone' => 'blue', 'ok' => $canProducts],
    ['href' => '/customers/quick-debts', 'icon' => 'notebook', 'title' => t('quick_debts_title'), 'hint' => t('dash_qa_debts'), 'tone' => 'red', 'ok' => $canCustomers],
    ['href' => '/expenses/create', 'icon' => 'wallet', 'title' => t('add_expense'), 'hint' => t('dash_qa_expense'), 'tone' => 'amber', 'ok' => can('expenses')],
    ['href' => '/sales/shift-report', 'icon' => 'zreport', 'title' => t('shift_report_button'), 'hint' => t('dash_qa_shift'), 'tone' => 'teal', 'ok' => $canSales],
    ['href' => '/reports', 'icon' => 'chart', 'title' => t('reports'), 'hint' => t('dash_qa_reports'), 'tone' => 'violet', 'ok' => can('reports')],
];
$actions = array_values(array_filter($actions, static fn (array $a): bool => $a['ok']));
?>
<?php if ($showOnboarding): ?>
<div class="onboarding-banner">
    <div class="onboarding-banner-icon">👋</div>
    <div class="onboarding-banner-body">
        <div class="onboarding-banner-title"><?= e(t('onboarding_welcome_title')) ?></div>
        <p class="onboarding-banner-text"><?= e(t('onboarding_welcome_body')) ?></p>
        <div class="onboarding-banner-actions">
            <a href="/help" class="btn btn-ghost btn-sm"><?= e(t('onboarding_help_link')) ?></a>
            <form method="post" action="/onboarding/dismiss">
                <?= csrf_field() ?>
                <button type="submit" class="btn btn-primary btn-sm"><?= e(t('onboarding_dismiss_button')) ?></button>
            </form>
        </div>
    </div>
</div>
<?php endif; ?>

<section class="dash-hero">
    <div class="dash-hero-glow" aria-hidden="true"></div>
    <div class="dash-hero-text">
        <span class="dash-hero-date"><?= $dashIcon('sparkle') ?><?= e(date('d.m.Y')) ?></span>
        <h1><?= e($greeting) ?><?= $firstName !== '' ? ', ' . e($firstName) : '' ?>!</h1>
        <?php if ($todaySales !== null): ?>
            <p><?= e(t('dash_hero_today', ['count' => (int) $todaySales['count'], 'revenue' => money((float) $todaySales['revenue'])])) ?></p>
        <?php else: ?>
            <p><?= e(t('system_running')) ?></p>
        <?php endif; ?>
    </div>
    <div class="dash-hero-actions">
        <?php if ($canSales): ?>
            <a href="/sales/new" class="dash-cta"><?= $dashIcon('till') ?><span><?= e(t('dash_open_till')) ?></span><?= $dashIcon('arrow') ?></a>
        <?php endif; ?>
        <?php if ($showAttendance): ?>
            <form method="post" action="<?= $openShift ? '/attendance/clock-out' : '/attendance/clock-in' ?>">
                <?= csrf_field() ?>
                <button type="submit" class="dash-shift<?= $openShift ? ' is-on' : '' ?>">
                    <?= $dashIcon('clock') ?><?= e($openShift ? t('clock_out_button') : t('clock_in_button')) ?>
                </button>
            </form>
        <?php endif; ?>
    </div>
</section>

<section class="kpi-grid">
    <?php if ($todaySales !== null): ?>
        <a href="/sales" class="kpi-card tone-lime">
            <span class="kpi-icon"><?= $dashIcon('coins') ?></span>
            <span class="kpi-label"><?= e(t('todays_revenue')) ?></span>
            <span class="kpi-value"><?= money((float) $todaySales['revenue']) ?></span>
        </a>
        <a href="/sales" class="kpi-card tone-blue">
            <span class="kpi-icon"><?= $dashIcon('receipt') ?></span>
            <span class="kpi-label"><?= e(t('dash_sales_today')) ?></span>
            <span class="kpi-value"><?= (int) $todaySales['count'] ?></span>
        </a>
    <?php endif; ?>
    <?php if ($canCustomers): ?>
        <a href="/customers?filter=debtors" class="kpi-card tone-red">
            <span class="kpi-icon"><?= $dashIcon('debt') ?></span>
            <span class="kpi-label"><?= e(t('total_debt_label')) ?></span>
            <span class="kpi-value"><?= money($totalDebt) ?></span>
        </a>
    <?php endif; ?>
    <?php if ($productCounts !== null): ?>
        <a href="/products" class="kpi-card tone-violet">
            <span class="kpi-icon"><?= $dashIcon('box') ?></span>
            <span class="kpi-label"><?= e(t('active_products_label')) ?></span>
            <span class="kpi-value"><?= (int) $productCounts['active'] ?></span>
            <?php if (can('prices')): ?>
                <span class="kpi-sub"><?= e(t('stock_value')) ?>: <?= money((float) $productCounts['stock_value']) ?></span>
            <?php endif; ?>
        </a>
    <?php endif; ?>
</section>

<?php if ($lowStockCounts && $lowStockCounts['low'] > 0): ?>
<a href="/products?filter=low_stock" class="dash-alert">
    <span class="dash-alert-icon"><?= $dashIcon('alert') ?></span>
    <span class="dash-alert-body">
        <strong><?= e(t('low_stock_alert_title')) ?></strong>
        <span>
            <?= e(count_label((int) $lowStockCounts['low'], 'low_stock_count_label')) ?>
            <?php if ($lowStockCounts['out'] > 0): ?>
                · <?= e(count_label((int) $lowStockCounts['out'], 'out_of_stock_count_label')) ?>
            <?php endif; ?>
        </span>
    </span>
    <span class="dash-alert-arrow"><?= $dashIcon('arrow') ?></span>
</a>
<?php endif; ?>

<?php if ($actions !== []): ?>
<h2 class="dash-section-title"><?= e(t('dash_quick_actions')) ?></h2>
<section class="qa-grid">
    <?php foreach ($actions as $action): ?>
        <a href="<?= e($action['href']) ?>" class="qa-card tone-<?= e($action['tone']) ?>">
            <span class="qa-icon"><?= $dashIcon($action['icon']) ?></span>
            <span class="qa-text">
                <span class="qa-title"><?= e($action['title']) ?></span>
                <span class="qa-hint"><?= e($action['hint']) ?></span>
            </span>
            <span class="qa-arrow"><?= $dashIcon('arrow') ?></span>
        </a>
    <?php endforeach; ?>
</section>
<?php endif; ?>

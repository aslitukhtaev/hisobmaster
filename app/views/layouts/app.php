<?php

use App\Core\Auth;
use App\Models\Shop;

$currentPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$isActive = static function (string $href) use ($currentPath): bool {
    if ($href === '#') {
        return false;
    }
    if ($href === '/') {
        return $currentPath === '/';
    }
    return str_starts_with($currentPath, $href);
};

if (Auth::isSuperAdmin()) {
    $navItems = [
        ['href' => '/', 'label' => t('dashboard'), 'icon' => 'home', 'implemented' => true, 'permission' => null],
        ['href' => '/superadmin/shops', 'label' => t('shops'), 'icon' => 'shop', 'implemented' => true, 'permission' => null],
        ['href' => '/superadmin/reports', 'label' => t('consolidated_reports_nav'), 'icon' => 'chart', 'implemented' => true, 'permission' => null],
        ['href' => '/profile', 'label' => t('profile'), 'icon' => 'user', 'implemented' => true, 'permission' => null],
    ];
} else {
    $navItems = [
        ['href' => '/', 'label' => t('dashboard'), 'icon' => 'home', 'implemented' => true, 'permission' => null],
        ['href' => '/sales', 'label' => t('sales'), 'icon' => 'cart', 'implemented' => true, 'permission' => 'sales'],
        ['href' => '/products', 'label' => t('products'), 'icon' => 'box', 'implemented' => true, 'permission' => 'products'],
        ['href' => '/suppliers', 'label' => t('suppliers'), 'icon' => 'truck', 'implemented' => true, 'permission' => 'products'],
        ['href' => '/customers', 'label' => t('customers'), 'icon' => 'users', 'implemented' => true, 'permission' => 'customers'],
        ['href' => '/expenses', 'label' => t('expenses'), 'icon' => 'wallet', 'implemented' => true, 'permission' => 'expenses'],
        ['href' => '/reports', 'label' => t('reports'), 'icon' => 'chart', 'implemented' => true, 'permission' => 'reports'],
    ];

    if (Auth::isOwner()) {
        $navItems[] = ['href' => '/employees', 'label' => t('employees'), 'icon' => 'userplus', 'implemented' => true, 'permission' => null];
        $navItems[] = ['href' => '/activity', 'label' => t('activity_log'), 'icon' => 'clock', 'implemented' => true, 'permission' => null];
    }

    $navItems[] = ['href' => '/profile', 'label' => t('profile'), 'icon' => 'user', 'implemented' => true, 'permission' => null];
}

foreach ($navItems as &$navItem) {
    $navItem['accessible'] = $navItem['implemented'] && ($navItem['permission'] === null || can($navItem['permission']));
}
unset($navItem);

$icon = static function (string $name): string {
    $paths = [
        'home' => '<path d="M3 11.5 12 4l9 7.5"/><path d="M5 10v9a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1v-9"/>',
        'cart' => '<circle cx="9" cy="20" r="1.4"/><circle cx="17" cy="20" r="1.4"/><path d="M2.5 3h2.4l2.1 11.2A2 2 0 0 0 9 16h8.2a2 2 0 0 0 2-1.6L21 7H6"/>',
        'box' => '<path d="M3.5 7.5 12 3l8.5 4.5L12 12z"/><path d="M3.5 7.5V16L12 20.5 20.5 16V7.5"/><path d="M12 12v8.5"/>',
        'users' => '<circle cx="9" cy="8" r="3"/><path d="M2.5 19c0-3.3 2.9-6 6.5-6s6.5 2.7 6.5 6"/><circle cx="17.5" cy="9" r="2.4"/><path d="M15.8 13.3c2.6.5 4.7 2.6 4.7 5.2"/>',
        'wallet' => '<rect x="2.5" y="6" width="19" height="13" rx="2"/><path d="M2.5 10h19"/><circle cx="17" cy="14" r="1.2"/>',
        'chart' => '<path d="M4 20V10"/><path d="M11 20V4"/><path d="M18 20v-7"/>',
        'userplus' => '<circle cx="9" cy="8" r="3.2"/><path d="M2.5 19c0-3.3 2.9-6 6.5-6s6.5 2.7 6.5 6"/><path d="M18.5 8v5"/><path d="M16 10.5h5"/>',
        'shop' => '<path d="M3 9.5 4 4h16l1 5.5"/><path d="M4 9.5v10a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-10"/><path d="M9 20.5v-6h6v6"/>',
        'user' => '<circle cx="12" cy="8" r="3.5"/><path d="M5 20c0-3.9 3.1-7 7-7s7 3.1 7 7"/>',
        'gear' => '<circle cx="12" cy="12" r="3"/><path d="M19.4 13.5a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5v.2a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1h-.2a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3h.1a1.7 1.7 0 0 0 1-1.5v-.2a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v.1a1.7 1.7 0 0 0 1.5 1h.2a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.6 1z"/>',
        'clock' => '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
        'more' => '<circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/>',
        'truck' => '<rect x="2.5" y="7" width="12" height="9"/><path d="M14.5 10h4l3 3.5V16h-7z"/><circle cx="7" cy="18.5" r="1.6"/><circle cx="17" cy="18.5" r="1.6"/>',
    ];
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' . ($paths[$name] ?? '') . '</svg>';
};

$roleLabel = match ($user['role'] ?? '') {
    'super_admin' => t('role_super_admin'),
    'owner' => t('role_owner'),
    'employee' => t('role_employee'),
    default => '',
};

$shopName = '';
if (!Auth::isSuperAdmin() && Auth::shopId()) {
    $shop = Shop::find((int) Auth::shopId());
    $shopName = $shop['name'] ?? '';
}
?>
<!doctype html>
<html lang="<?= e(current_lang()) ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <script>
        (function () {
            try {
                var t = localStorage.getItem('kassiron-theme');
                if (t === 'light' || t === 'dark') {
                    document.documentElement.setAttribute('data-theme', t);
                }
            } catch (e) {}
        })();
    </script>
    <title><?= isset($pageTitle) ? e($pageTitle) . ' — ' . e(t('app_name')) : e(t('app_name')) ?></title>
    <link rel="stylesheet" href="<?= asset('css/app.css') ?>">
    <link rel="manifest" href="/manifest.json">
    <link rel="icon" href="<?= asset('img/icon-192.png') ?>" type="image/png">
    <link rel="apple-touch-icon" href="<?= asset('img/icon-192.png') ?>">
    <meta name="theme-color" content="#059669" media="(prefers-color-scheme: light)">
    <meta name="theme-color" content="#121e2e" media="(prefers-color-scheme: dark)">
    <script src="https://telegram.org/js/telegram-web-app.js"></script>
</head>
<body>
    <script src="<?= asset('js/telegram.js') ?>"></script>
    <div class="shell">
        <aside class="sidebar">
            <div class="brand">
                <img class="brand-logo logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="<?= e(t('app_name')) ?>">
                <img class="brand-logo logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="<?= e(t('app_name')) ?>">
            </div>
            <nav class="side-nav">
                <?php foreach ($navItems as $item): $active = $isActive($item['href']); ?>
                    <a href="<?= e($item['accessible'] ? $item['href'] : '#') ?>" class="side-link <?= $active ? 'active' : ($item['accessible'] ? '' : 'disabled') ?>">
                        <span class="side-icon"><?= $icon($item['icon']) ?></span>
                        <span><?= e($item['label']) ?></span>
                        <?php if (!$item['implemented']): ?>
                            <span class="badge"><?= e(t('coming_soon')) ?></span>
                        <?php elseif (!$item['accessible']): ?>
                            <span class="badge badge-locked"><?= e(t('no_access_badge')) ?></span>
                        <?php endif; ?>
                    </a>
                <?php endforeach; ?>
            </nav>
        </aside>

        <div class="main">
            <header class="topbar">
                <div class="topbar-title">
                    <span class="role-badge"><?= e($roleLabel) ?></span>
                    <?php if ($shopName !== ''): ?><span class="shop-name-label"><?= e($shopName) ?></span><?php endif; ?>
                </div>
                <div class="topbar-actions">
                    <?php require BASE_PATH . '/app/views/partials/theme-toggle.php'; ?>
                    <?php require BASE_PATH . '/app/views/partials/lang-switcher.php'; ?>
                    <a href="/profile" class="user-chip">
                        <span class="user-avatar"><?= e(mb_substr((string) ($user['full_name'] ?? '?'), 0, 1)) ?></span>
                        <span class="user-name"><?= e($user['full_name'] ?? '') ?></span>
                    </a>
                    <form method="post" action="/logout">
                        <?= csrf_field() ?>
                        <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('logout')) ?></button>
                    </form>
                </div>
            </header>

            <main class="content">
                <?php require BASE_PATH . '/app/views/partials/flash.php'; ?>
                <?= $content ?>
            </main>

            <nav class="bottom-nav">
                <?php foreach (array_slice($navItems, 0, 4) as $item): $active = $isActive($item['href']); ?>
                    <a href="<?= e($item['accessible'] ? $item['href'] : '#') ?>" class="bottom-link <?= $active ? 'active' : ($item['accessible'] ? '' : 'disabled') ?>">
                        <span class="bottom-icon"><?= $icon($item['icon']) ?></span>
                        <span class="bottom-label"><?= e($item['label']) ?></span>
                    </a>
                <?php endforeach; ?>
                <?php if (count($navItems) > 4): ?>
                    <button type="button" class="bottom-link" id="more-nav-toggle" aria-expanded="false" aria-controls="mobile-drawer">
                        <span class="bottom-icon"><?= $icon('more') ?></span>
                        <span class="bottom-label"><?= e(t('more_menu')) ?></span>
                    </button>
                <?php endif; ?>
            </nav>

            <div class="mobile-drawer" id="mobile-drawer" role="dialog" aria-modal="true" aria-label="<?= e(t('more_menu')) ?>">
                <div class="mobile-drawer-sheet" tabindex="-1">
                    <?php foreach ($navItems as $item): $active = $isActive($item['href']); ?>
                        <a href="<?= e($item['accessible'] ? $item['href'] : '#') ?>" class="drawer-link <?= $active ? 'active' : ($item['accessible'] ? '' : 'disabled') ?>">
                            <span class="side-icon"><?= $icon($item['icon']) ?></span>
                            <span><?= e($item['label']) ?></span>
                            <?php if (!$item['accessible']): ?>
                                <span class="badge badge-locked"><?= e(t('no_access_badge')) ?></span>
                            <?php endif; ?>
                        </a>
                    <?php endforeach; ?>
                </div>
            </div>
        </div>
    </div>

    <script>
        window.HM_BARCODE_I18N = {
            scanBarcode: <?= json_encode(t('scan_barcode'), JSON_UNESCAPED_UNICODE) ?>,
            scanUnsupported: <?= json_encode(t('scan_unsupported'), JSON_UNESCAPED_UNICODE) ?>,
            scanHint: <?= json_encode(t('scan_hint'), JSON_UNESCAPED_UNICODE) ?>,
            close: <?= json_encode(t('close'), JSON_UNESCAPED_UNICODE) ?>
        };
    </script>
    <script src="<?= asset('js/app.js') ?>" defer></script>
    <script src="<?= asset('js/barcode-scan.js') ?>" defer></script>
</body>
</html>

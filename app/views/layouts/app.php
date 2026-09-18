<?php
$navItems = [
    ['href' => '/', 'label' => t('dashboard'), 'icon' => 'home', 'active' => true],
    ['href' => '#', 'label' => t('sales'), 'icon' => 'cart', 'active' => false],
    ['href' => '#', 'label' => t('products'), 'icon' => 'box', 'active' => false],
    ['href' => '#', 'label' => t('customers'), 'icon' => 'users', 'active' => false],
    ['href' => '#', 'label' => t('expenses'), 'icon' => 'wallet', 'active' => false],
    ['href' => '#', 'label' => t('reports'), 'icon' => 'chart', 'active' => false],
    ['href' => '#', 'label' => t('employees'), 'icon' => 'userplus', 'active' => false],
];

$icon = static function (string $name): string {
    $paths = [
        'home' => '<path d="M3 11.5 12 4l9 7.5"/><path d="M5 10v9a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1v-9"/>',
        'cart' => '<circle cx="9" cy="20" r="1.4"/><circle cx="17" cy="20" r="1.4"/><path d="M2.5 3h2.4l2.1 11.2A2 2 0 0 0 9 16h8.2a2 2 0 0 0 2-1.6L21 7H6"/>',
        'box' => '<path d="M3.5 7.5 12 3l8.5 4.5L12 12z"/><path d="M3.5 7.5V16L12 20.5 20.5 16V7.5"/><path d="M12 12v8.5"/>',
        'users' => '<circle cx="9" cy="8" r="3"/><path d="M2.5 19c0-3.3 2.9-6 6.5-6s6.5 2.7 6.5 6"/><circle cx="17.5" cy="9" r="2.4"/><path d="M15.8 13.3c2.6.5 4.7 2.6 4.7 5.2"/>',
        'wallet' => '<rect x="2.5" y="6" width="19" height="13" rx="2"/><path d="M2.5 10h19"/><circle cx="17" cy="14" r="1.2"/>',
        'chart' => '<path d="M4 20V10"/><path d="M11 20V4"/><path d="M18 20v-7"/>',
        'userplus' => '<circle cx="9" cy="8" r="3.2"/><path d="M2.5 19c0-3.3 2.9-6 6.5-6s6.5 2.7 6.5 6"/><path d="M18.5 8v5"/><path d="M16 10.5h5"/>',
        'gear' => '<circle cx="12" cy="12" r="3"/><path d="M19.4 13.5a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5v.2a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1h-.2a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3h.1a1.7 1.7 0 0 0 1-1.5v-.2a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v.1a1.7 1.7 0 0 0 1.5 1h.2a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.6 1z"/>',
    ];
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' . ($paths[$name] ?? '') . '</svg>';
};

$roleLabel = match ($user['role'] ?? '') {
    'super_admin' => t('role_super_admin'),
    'owner' => t('role_owner'),
    'employee' => t('role_employee'),
    default => '',
};
?>
<!doctype html>
<html lang="<?= e(current_lang()) ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title><?= e(t('app_name')) ?></title>
    <link rel="stylesheet" href="<?= asset('css/app.css') ?>">
</head>
<body>
    <div class="shell">
        <aside class="sidebar">
            <div class="brand">
                <div class="brand-logo">HM</div>
                <span class="brand-name"><?= e(t('app_name')) ?></span>
            </div>
            <nav class="side-nav">
                <?php foreach ($navItems as $item): ?>
                    <a href="<?= e($item['href']) ?>" class="side-link <?= $item['active'] ? 'active' : 'disabled' ?>">
                        <span class="side-icon"><?= $icon($item['icon']) ?></span>
                        <span><?= e($item['label']) ?></span>
                        <?php if (!$item['active']): ?><span class="badge"><?= e(t('coming_soon')) ?></span><?php endif; ?>
                    </a>
                <?php endforeach; ?>
            </nav>
        </aside>

        <div class="main">
            <header class="topbar">
                <div class="topbar-title">
                    <span class="role-badge"><?= e($roleLabel) ?></span>
                </div>
                <div class="topbar-actions">
                    <?php require BASE_PATH . '/app/views/partials/lang-switcher.php'; ?>
                    <div class="user-chip">
                        <span class="user-avatar"><?= e(mb_substr((string) ($user['full_name'] ?? '?'), 0, 1)) ?></span>
                        <span class="user-name"><?= e($user['full_name'] ?? '') ?></span>
                    </div>
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
                <?php foreach (array_slice($navItems, 0, 5) as $item): ?>
                    <a href="<?= e($item['href']) ?>" class="bottom-link <?= $item['active'] ? 'active' : 'disabled' ?>">
                        <span class="bottom-icon"><?= $icon($item['icon']) ?></span>
                        <span class="bottom-label"><?= e($item['label']) ?></span>
                    </a>
                <?php endforeach; ?>
            </nav>
        </div>
    </div>

    <script src="<?= asset('js/app.js') ?>" defer></script>
</body>
</html>

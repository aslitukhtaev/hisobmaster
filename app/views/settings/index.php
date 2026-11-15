<?php
$pageTitle = t('settings_nav');

// The same line icons as the side menu (layouts/app.php).
$settingsIcon = static function (string $name): string {
    $paths = [
        'user' => '<circle cx="12" cy="8" r="3.5"/><path d="M5 20c0-3.900 3.100-7 7-7s7 3.100 7 7"/>',
        'userplus' => '<circle cx="9" cy="8" r="3.200"/><path d="M2.500 19c0-3.300 2.900-6 6.500-6s6.500 2.700 6.500 6"/><path d="M18.500 8v5"/><path d="M16 10.500h5"/>',
        'truck' => '<rect x="2.500" y="7" width="12" height="9"/><path d="M14.500 10h4l3 3.500V16h-7z"/><circle cx="7" cy="18.500" r="1.600"/><circle cx="17" cy="18.500" r="1.600"/>',
        'clock' => '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.500 2"/>',
        'monitor' => '<rect x="3" y="4" width="18" height="12" rx="1.500"/><path d="M8 20h8"/><path d="M12 16v4"/>',
        'printer' => '<path d="M7 8V3.500h10V8"/><rect x="3" y="8" width="18" height="8.500" rx="1.500"/><path d="M7 14h10v6.500H7z"/>',
        'help' => '<circle cx="12" cy="12" r="9"/><path d="M9.500 9.300a2.500 2.500 0 1 1 3.700 2.400c-.8.5-1.200 1-1.200 2"/><circle cx="12" cy="16.700" r=".1" stroke-width="2.400"/>',
    ];
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' . ($paths[$name] ?? '') . '</svg>';
};
?>
<section class="page-head">
    <h1><?= e(t('settings_nav')) ?></h1>
    <p class="muted"><?= e(t('settings_hint')) ?></p>
</section>

<div class="settings-list">
    <?php foreach ($items as $item): ?>
        <a href="<?= e($item['href']) ?>" class="settings-item">
            <span class="side-icon"><?= $settingsIcon($item['icon']) ?></span>
            <span>
                <span class="settings-item-title"><?= e($item['title']) ?></span><br>
                <span class="muted"><?= e($item['hint']) ?></span>
            </span>
        </a>
    <?php endforeach; ?>
</div>

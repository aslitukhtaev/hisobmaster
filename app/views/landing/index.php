<?php
// The landing page (layouts/landing.php). Screenshots are of a demo shop,
// in both themes (img.logo-for-*-theme switches them like the logo).
$icon = static function (string $name): string {
    $paths = [
        'scan' => '<path d="M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16"/><path d="M8 8.5v7M11 8.5v7M14 8.5v7M17 8.5v7"/>',
        'box' => '<path d="M3.5 7.5 12 3l8.5 4.5L12 12z"/><path d="M3.5 7.5V16L12 20.5 20.5 16V7.5"/><path d="M12 12v8.5"/>',
        'book' => '<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 7.5h6M9 11.5h6M9 15.5h3"/>',
        'chart' => '<path d="M4 19.5h16"/><path d="m5 15 4.5-4.5 3 3L19 7"/><path d="M14.5 7H19v4.5"/>',
        'users' => '<circle cx="9" cy="8" r="3"/><path d="M2.5 19c0-3.3 2.9-6 6.5-6s6.5 2.7 6.5 6"/><circle cx="17.5" cy="9" r="2.4"/><path d="M15.8 13.3c2.6.5 4.7 2.6 4.7 5.2"/>',
        'truck' => '<rect x="2.5" y="7" width="12" height="9" rx="1"/><path d="M14.5 10h4l3 3.5V16h-7z"/><circle cx="7" cy="18.5" r="1.6"/><circle cx="17" cy="18.5" r="1.6"/>',
        'receipt' => '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6"/>',
        'send' => '<path d="M21.5 3.5 2.8 10.8c-.9.4-.9 1.6 0 1.9l4.7 1.6 1.8 5.6c.2.8 1.2 1 1.8.4l2.7-2.6 4.9 3.6c.7.5 1.6.1 1.8-.7l3-15.7c.2-.9-.7-1.7-1.6-1.4z"/><path d="m7.5 14.3 10-7.3-7.4 8.6"/>',
        'globe' => '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.4 2.5 3.6 5.5 3.6 9s-1.2 6.5-3.6 9c-2.4-2.5-3.6-5.5-3.6-9S9.6 5.5 12 3z"/>',
        'monitor' => '<rect x="3" y="4" width="18" height="12" rx="1.5"/><path d="M8 20h8M12 16v4"/>',
        'phone' => '<path d="M21 16.4v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 1.1 3.7 2 2 0 0 1 3.1 1.5h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L7.1 9.4a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"/>',
        'download' => '<path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M4 20h16"/>',
        'check' => '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    ];

    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' . $paths[$name] . '</svg>';
};
$shot = static function (string $name, string $alt, int $width, int $height, bool $eager = false): string {
    $attrs = sprintf('width="%d" height="%d" decoding="async"', $width, $height);
    return sprintf(
        '<img class="logo-for-light-theme" src="%s" alt="%s" %s%s><img class="logo-for-dark-theme" src="%s" alt="%s" %s loading="lazy">',
        asset("img/landing/$name-light.webp"), e($alt), $attrs, $eager ? ' fetchpriority="high"' : ' loading="lazy"',
        asset("img/landing/$name-dark.webp"), e($alt), $attrs
    );
};
$features = [
    ['scan', 'sales'], ['box', 'stock'], ['book', 'debt'], ['chart', 'reports'],
    ['users', 'staff'], ['truck', 'purchases'], ['receipt', 'receipt'], ['send', 'telegram'],
];
?>
<section class="lp-hero">
    <div class="lp-container">
        <span class="lp-eyebrow"><?= e(t('lp_eyebrow')) ?></span>
        <h1 class="lp-title">
            <?= e(t('lp_hero_title_1')) ?>
            <span class="lp-grad"><?= e(t('lp_hero_title_accent')) ?></span>
            <?= e(t('lp_hero_title_2')) ?>
        </h1>
        <p class="lp-lead"><?= e(t('lp_hero_lead')) ?></p>
        <div class="lp-cta">
            <a href="/login" class="btn btn-primary lp-btn-lg"><?= e(t('lp_login')) ?></a>
            <a href="#contact" class="btn btn-ghost lp-btn-lg"><?= e(t('lp_cta_contact')) ?></a>
        </div>
        <ul class="lp-checks">
            <li><?= $icon('check') ?><?= e(t('lp_check_offline')) ?></li>
            <li><?= $icon('check') ?><?= e(t('lp_check_barcode')) ?></li>
            <li><?= $icon('check') ?><?= e(t('lp_check_lang')) ?></li>
        </ul>
    </div>
    <div class="lp-hero-visual">
        <div class="lp-browser">
            <div class="lp-browser-bar" aria-hidden="true"><i></i><i></i><i></i><span class="lp-url">kassiron.uz</span></div>
            <?= $shot('reports', t('lp_hero_img_alt'), 1440, 900, true) ?>
        </div>
        <div class="lp-phone">
            <?= $shot('mobile', t('lp_phone_img_alt'), 540, 1169) ?>
        </div>
    </div>
</section>

<section class="lp-section lp-platforms" aria-labelledby="lp-platforms-title">
    <div class="lp-container">
        <h2 class="lp-h2" id="lp-platforms-title"><?= e(t('lp_platforms_title')) ?></h2>
        <div class="lp-platform-grid">
            <?php foreach ([['globe', 'web'], ['send', 'tg'], ['monitor', 'win']] as [$ic, $key]): ?>
                <div class="lp-platform">
                    <span class="lp-icon"><?= $icon($ic) ?></span>
                    <div>
                        <h3><?= e(t('lp_platform_' . $key)) ?></h3>
                        <p><?= e(t('lp_platform_' . $key . '_text')) ?></p>
                    </div>
                </div>
            <?php endforeach; ?>
        </div>
    </div>
</section>

<section class="lp-section" id="features" aria-labelledby="lp-features-title">
    <div class="lp-container">
        <h2 class="lp-h2" id="lp-features-title"><?= e(t('lp_features_title')) ?></h2>
        <p class="lp-sub"><?= e(t('lp_features_lead')) ?></p>
        <div class="lp-features">
            <?php foreach ($features as [$ic, $key]): ?>
                <article class="lp-feature">
                    <span class="lp-icon"><?= $icon($ic) ?></span>
                    <h3><?= e(t('lp_f_' . $key)) ?></h3>
                    <p><?= e(t('lp_f_' . $key . '_text')) ?></p>
                </article>
            <?php endforeach; ?>
        </div>
    </div>
</section>

<section class="lp-section lp-section-tint" aria-labelledby="lp-debt-title">
    <div class="lp-container lp-show">
        <div class="lp-show-copy">
            <h2 class="lp-h2 lp-left" id="lp-debt-title"><?= e(t('lp_show_debt_title')) ?></h2>
            <p class="lp-sub lp-left"><?= e(t('lp_show_debt_text')) ?></p>
            <ul class="lp-list">
                <?php foreach (['lp_show_debt_1', 'lp_show_debt_2', 'lp_show_debt_3'] as $key): ?>
                    <li><span class="lp-tick"><?= $icon('check') ?></span><?= e(t($key)) ?></li>
                <?php endforeach; ?>
            </ul>
        </div>
        <div class="lp-frame">
            <?= $shot('customers', t('lp_show_debt_alt'), 1440, 660) ?>
        </div>
    </div>
</section>

<section class="lp-desktop" id="desktop" aria-labelledby="lp-desktop-title">
    <div class="lp-container lp-show">
        <div class="lp-show-copy">
            <span class="lp-eyebrow lp-eyebrow-dark"><?= e(t('lp_desktop_eyebrow')) ?></span>
            <h2 class="lp-h2 lp-left" id="lp-desktop-title"><?= e(t('lp_desktop_title')) ?></h2>
            <p class="lp-sub lp-left"><?= e(t('lp_desktop_text')) ?></p>
            <ul class="lp-list">
                <?php foreach (['lp_desktop_1', 'lp_desktop_2', 'lp_desktop_3', 'lp_desktop_4'] as $key): ?>
                    <li><span class="lp-tick"><?= $icon('check') ?></span><?= e(t($key)) ?></li>
                <?php endforeach; ?>
            </ul>
            <div class="lp-desktop-cta">
                <a href="<?= e(DESKTOP_DOWNLOAD_URL) ?>" class="btn btn-primary lp-btn-lg"><?= $icon('download') ?><?= e(t('lp_desktop_download')) ?></a>
                <p><?= e(t('lp_desktop_note')) ?></p>
            </div>
        </div>
        <div class="lp-window">
            <div class="lp-window-bar" aria-hidden="true">
                <span class="lp-window-title">KassirON</span>
                <span class="lp-window-buttons"><i></i><i></i><i></i></span>
            </div>
            <img src="<?= asset('img/landing/pos-dark.webp') ?>" alt="<?= e(t('lp_desktop_img_alt')) ?>" width="1440" height="900" loading="lazy" decoding="async">
            <div class="lp-offline" aria-hidden="true">
                <span class="lp-offline-dot"></span>
                <span><strong><?= e(t('lp_offline_badge')) ?></strong><small><?= e(t('lp_offline_queue')) ?></small></span>
            </div>
        </div>
    </div>
</section>

<section class="lp-section" id="start" aria-labelledby="lp-start-title">
    <div class="lp-container">
        <h2 class="lp-h2" id="lp-start-title"><?= e(t('lp_start_title')) ?></h2>
        <ol class="lp-steps">
            <?php foreach ([1, 2, 3] as $n): ?>
                <li class="lp-step">
                    <span class="lp-step-num"><?= $n ?></span>
                    <h3><?= e(t('lp_step_' . $n)) ?></h3>
                    <p><?= e(t('lp_step_' . $n . '_text')) ?></p>
                </li>
            <?php endforeach; ?>
        </ol>
    </div>
</section>

<section class="lp-section lp-contact" id="contact" aria-labelledby="lp-contact-title">
    <div class="lp-container">
        <div class="lp-cta-card">
            <h2 id="lp-contact-title"><?= e(t('lp_contact_title')) ?></h2>
            <p><?= e(t('lp_contact_text')) ?></p>
            <div class="lp-cta">
                <a href="tel:<?= e(SUPPORT_PHONE) ?>" class="btn lp-btn-lg lp-btn-white"><?= $icon('phone') ?><?= e(SUPPORT_PHONE_DISPLAY) ?></a>
                <a href="https://t.me/<?= e(SUPPORT_TELEGRAM) ?>" class="btn lp-btn-lg lp-btn-outline" target="_blank" rel="noopener noreferrer"><?= $icon('send') ?><?= e(t('lp_contact_tg')) ?></a>
            </div>
            <p class="lp-cta-client"><?= e(t('lp_contact_client')) ?> <a href="/login"><?= e(t('lp_login')) ?> →</a></p>
        </div>
    </div>
</section>

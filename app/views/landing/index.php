<?php
// The landing page (layouts/landing.php). Screenshots are of a demo shop,
// in both themes (img.logo-for-*-theme switches them like the logo).
// Every animated/interactive piece is progressive enhancement from
// assets/js/landing.js: without it the page is a complete, static one.
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
        'arrow' => '<path d="M5 12h14M13 6l6 6-6 6"/>',
        'cloud' => '<path d="M7.5 18.5a4.5 4.5 0 0 1-.7-8.95A6 6 0 0 1 18.3 10.4 4.1 4.1 0 0 1 17.5 18.5z"/>',
        'bell' => '<path d="M6 16.5V11a6 6 0 1 1 12 0v5.5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
        'minus' => '<path d="M6 12h12"/>',
        'plus' => '<path d="M12 6v12M6 12h12"/>',
        'bolt' => '<path d="M13 2.5 5 13.5h6l-1 8 8-11h-6z"/>',
        'sync' => '<path d="M20 11a8 8 0 0 0-14.5-4.2L4 8.5"/><path d="M4 4v4.5h4.5"/><path d="M4 13a8 8 0 0 0 14.5 4.2L20 15.5"/><path d="M20 20v-4.5h-4.5"/>',
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
// A decorative barcode: alternating bar/gap widths on a 1-unit grid.
$barcode = static function (int $height = 60): string {
    $widths = [2, 1, 1, 3, 1, 2, 2, 1, 3, 1, 1, 2, 1, 1, 3, 2, 1, 2, 1, 1, 3, 1, 2, 1, 2, 2, 1, 1, 3, 1, 1, 2, 3, 1, 2, 1];
    $x = 0;
    $bars = '';
    foreach ($widths as $i => $w) {
        if ($i % 2 === 0) {
            $bars .= sprintf('<rect x="%d" y="0" width="%d" height="%d"/>', $x, $w, $height);
        }
        $x += $w;
    }

    return sprintf('<svg viewBox="0 0 %d %d" fill="currentColor" preserveAspectRatio="none" aria-hidden="true">%s</svg>', $x, $height, $bars);
};
$cur = t('lp_demo_currency');
$features = [
    'sales' => 'scan', 'stock' => 'box', 'reports' => 'chart', 'debt' => 'book',
    'staff' => 'users', 'purchases' => 'truck', 'receipt' => 'receipt', 'telegram' => 'send',
];
$demoProducts = [
    ['bread', '🍞', 4000], ['milk', '🥛', 12500], ['tea', '🍵', 24000],
    ['candy', '🍬', 18000], ['eggs', '🥚', 22000], ['choc', '🍫', 15000],
];
$tour = [
    ['sales', 'scan', 'pos'],
    ['reports', 'chart', 'reports'],
    ['telegram', 'send', 'mobile'],
];
$badgeText = mb_strtoupper(t('lp_badge_text'), 'UTF-8') . ' ✦ ';
$badgeFont = round(2 * M_PI * 46 / (mb_strlen($badgeText, 'UTF-8') * 0.74), 2);
?>
<section class="lp-hero" id="top">
    <div class="lp-aurora" aria-hidden="true"><i></i><i></i><i></i></div>
    <div class="lp-gridbg" aria-hidden="true"><span></span></div>
    <div class="lp-container lp-hero-grid">
        <div class="lp-hero-copy">
            <span class="lp-eyebrow" data-reveal><span class="lp-live"></span><?= e(t('lp_eyebrow')) ?></span>
            <h1 class="lp-title" data-reveal style="--d:.06s">
                <?= e(t('lp_hero_title_1')) ?>
                <span class="lp-grad lp-squig-wrap"><?= e(t('lp_hero_title_accent')) ?>
                    <svg class="lp-squig" viewBox="0 0 300 14" preserveAspectRatio="none" aria-hidden="true"><path pathLength="1" d="M3 9c22-8 41 6 64-1s40-5 62 1 41 4 64-2 40-3 64 1 30 1 40-2"/></svg>
                </span>
                <?= e(t('lp_hero_title_2')) ?>
            </h1>
            <p class="lp-lead" data-reveal style="--d:.12s"><?= e(t('lp_hero_lead')) ?></p>
            <div class="lp-cta lp-cta-left" data-reveal style="--d:.18s">
                <a href="/login" class="btn lp-btn-lg lp-btn-main"><?= e(t('lp_login')) ?><?= $icon('arrow') ?></a>
                <a href="#contact" class="btn btn-ghost lp-btn-lg lp-btn-glass"><?= e(t('lp_cta_contact')) ?></a>
            </div>
            <ul class="lp-checks" data-reveal style="--d:.24s">
                <li><?= $icon('check') ?><?= e(t('lp_check_offline')) ?></li>
                <li><?= $icon('check') ?><?= e(t('lp_check_barcode')) ?></li>
                <li><?= $icon('check') ?><?= e(t('lp_check_lang')) ?></li>
            </ul>
        </div>

        <div class="lp-stage" id="lp-stage" data-reveal style="--d:.14s">
            <div class="lp-float lp-float-rev">
                <span class="lp-float-icon"><?= $icon('chart') ?></span>
                <span class="lp-float-text">
                    <small><?= e(t('lp_demo_today')) ?></small>
                    <strong><span id="lp-rev" data-value="4280000">4 280 000</span> <?= e($cur) ?></strong>
                </span>
                <svg class="lp-spark" viewBox="0 0 90 28" aria-hidden="true"><path pathLength="1" d="M2 22 14 17 26 20 38 11 50 14 62 6 74 9 88 2"/></svg>
            </div>
            <div class="lp-float lp-float-sync" id="lp-sync" data-t-syncing="<?= e(t('lp_net_syncing')) ?>" data-t-synced="<?= e(t('lp_net_synced')) ?>">
                <span class="lp-float-icon"><?= $icon('sync') ?></span>
                <span class="lp-float-text">
                    <strong data-sync-text><?= e(t('lp_net_synced')) ?></strong>
                    <span class="lp-devices" aria-hidden="true"><?= $icon('monitor') ?><?= $icon('globe') ?><?= $icon('send') ?></span>
                </span>
            </div>

            <div class="lp-pos" id="lp-pos"
                 data-cur="<?= e($cur) ?>"
                 data-shop="Baraka Market"
                 data-lang="<?= e(current_lang()) ?>"
                 data-t-empty="<?= e(t('lp_demo_empty')) ?>"
                 data-t-cash="<?= e(t('lp_demo_cash')) ?>"
                 data-t-card="<?= e(t('lp_demo_card')) ?>"
                 data-t-debt="<?= e(t('lp_demo_debt')) ?>"
                 data-t-total="<?= e(t('lp_demo_total')) ?>"
                 data-t-thanks="<?= e(t('lp_demo_thanks')) ?>"
                 data-t-debtnote="<?= e(t('lp_demo_debt_noted')) ?>">
                <div class="lp-pos-bar" aria-hidden="true">
                    <i></i><i></i><i></i>
                    <span class="lp-pos-name">KassirON</span>
                    <span class="lp-pos-pill"><span class="lp-live"></span><?= e(t('lp_net_online')) ?></span>
                </div>
                <div class="lp-pos-body">
                    <div class="lp-pos-products">
                        <div class="lp-pos-title">
                            <strong><?= e(t('lp_demo_title')) ?></strong>
                            <small><?= e(t('lp_demo_hint')) ?></small>
                        </div>
                        <div class="lp-tiles">
                            <?php foreach ($demoProducts as [$id, $emoji, $price]): ?>
                                <button type="button" class="lp-tile" data-id="<?= e($id) ?>" data-name="<?= e(t('lp_demo_p_' . $id)) ?>" data-price="<?= $price ?>" data-emoji="<?= $emoji ?>">
                                    <span class="lp-tile-qty" aria-hidden="true"></span>
                                    <span class="lp-tile-emoji" aria-hidden="true"><?= $emoji ?></span>
                                    <span class="lp-tile-name"><?= e(t('lp_demo_p_' . $id)) ?></span>
                                    <span class="lp-tile-price"><?= number_format($price, 0, '.', ' ') ?></span>
                                </button>
                            <?php endforeach; ?>
                        </div>
                    </div>
                    <div class="lp-pos-cart">
                        <div class="lp-cart-head"><strong><?= e(t('lp_demo_cart')) ?></strong><span class="lp-cart-count" id="lp-cart-count">0</span></div>
                        <ul class="lp-cart-lines" id="lp-cart-lines" aria-live="polite">
                            <li class="lp-cart-empty"><?= e(t('lp_demo_empty')) ?></li>
                        </ul>
                        <div class="lp-cart-total"><span><?= e(t('lp_demo_total')) ?></span><strong><span id="lp-total">0</span> <small><?= e($cur) ?></small></strong></div>
                        <div class="lp-pay" role="radiogroup" aria-label="<?= e(t('lp_demo_total')) ?>">
                            <button type="button" role="radio" aria-checked="true" class="is-on" data-pay="cash"><?= e(t('lp_demo_cash')) ?></button>
                            <button type="button" role="radio" aria-checked="false" data-pay="card"><?= e(t('lp_demo_card')) ?></button>
                            <button type="button" role="radio" aria-checked="false" data-pay="debt"><?= e(t('lp_demo_debt')) ?></button>
                        </div>
                        <button type="button" class="lp-sell" id="lp-sell" disabled><?= e(t('lp_demo_sell')) ?></button>

                        <div class="lp-receipt-wrap" id="lp-receipt-wrap" hidden>
                            <div class="lp-receipt" id="lp-receipt">
                                <div class="lp-receipt-head"><strong data-r="shop"></strong><span data-r="date"></span></div>
                                <ul class="lp-receipt-lines" data-r="lines"></ul>
                                <div class="lp-receipt-sum"><span><?= e(t('lp_demo_total')) ?></span><strong data-r="total"></strong></div>
                                <div class="lp-receipt-pay" data-r="pay"></div>
                                <div class="lp-receipt-code"><?= $barcode(34) ?></div>
                                <div class="lp-receipt-thanks"><?= e(t('lp_demo_thanks')) ?></div>
                            </div>
                            <button type="button" class="lp-again" id="lp-again"><?= $icon('plus') ?><?= e(t('lp_demo_again')) ?></button>
                        </div>
                    </div>
                </div>
                <div class="lp-toast" id="lp-toast" role="status"></div>
            </div>
        </div>
    </div>
</section>

<section class="lp-tape" aria-hidden="true">
    <div class="lp-tape-band lp-tape-a">
        <div class="lp-tape-track">
            <?php for ($loop = 0; $loop < 2; $loop++): ?>
                <div class="lp-tape-set">
                    <?php foreach ($features as $key => $ic): ?>
                        <span><?= $icon($ic) ?><?= e(t('lp_f_' . $key)) ?></span><b>✦</b>
                    <?php endforeach; ?>
                </div>
            <?php endfor; ?>
        </div>
    </div>
    <div class="lp-tape-band lp-tape-b">
        <div class="lp-tape-track lp-tape-rev">
            <?php for ($loop = 0; $loop < 2; $loop++): ?>
                <div class="lp-tape-set">
                    <?php foreach (['lp_check_offline', 'lp_check_barcode', 'lp_check_lang', 'lp_platform_web', 'lp_platform_tg', 'lp_platform_win'] as $key): ?>
                        <span><?= e(t($key)) ?></span><b>●</b>
                    <?php endforeach; ?>
                </div>
            <?php endfor; ?>
        </div>
    </div>
</section>

<section class="lp-section lp-platforms" aria-labelledby="lp-platforms-title">
    <div class="lp-container">
        <h2 class="lp-h2" id="lp-platforms-title" data-reveal><?= e(t('lp_platforms_title')) ?></h2>
        <div class="lp-sync" data-reveal>
            <?php foreach ([['globe', 'web'], ['send', 'tg'], ['monitor', 'win']] as $i => [$ic, $key]): ?>
                <div class="lp-node" style="--i:<?= $i ?>">
                    <span class="lp-node-ico"><?= $icon($ic) ?></span>
                    <h3><?= e(t('lp_platform_' . $key)) ?></h3>
                    <p><?= e(t('lp_platform_' . $key . '_text')) ?></p>
                </div>
            <?php endforeach; ?>
        </div>
    </div>
</section>

<section class="lp-section" id="features" aria-labelledby="lp-features-title">
    <div class="lp-container">
        <h2 class="lp-h2" id="lp-features-title" data-reveal><?= e(t('lp_features_title')) ?></h2>
        <p class="lp-sub" data-reveal><?= e(t('lp_features_lead')) ?></p>
        <div class="lp-bento">
            <article class="lp-bcard lp-b-sales lp-spot" data-reveal>
                <div class="lv lv-scan" aria-hidden="true">
                    <div class="lv-scan-code"><?= $barcode(60) ?><i class="lv-laser"></i></div>
                    <div class="lv-scan-chip"><?= $icon('check') ?>12 500 <?= e($cur) ?></div>
                </div>
                <h3><?= e(t('lp_f_sales')) ?></h3>
                <p><?= e(t('lp_f_sales_text')) ?></p>
            </article>
            <article class="lp-bcard lp-b-stock lp-spot" data-reveal style="--d:.06s">
                <div class="lv lv-stock" aria-hidden="true">
                    <span style="--h:72%"></span><span style="--h:54%"></span><span style="--h:88%"></span>
                    <span class="is-low" style="--h:16%"></span><span style="--h:62%"></span><span style="--h:40%"></span>
                    <em class="lv-bell"><?= $icon('bell') ?></em>
                </div>
                <h3><?= e(t('lp_f_stock')) ?></h3>
                <p><?= e(t('lp_f_stock_text')) ?></p>
            </article>
            <article class="lp-bcard lp-b-reports lp-spot" data-reveal>
                <div class="lv lv-chart" aria-hidden="true">
                    <div class="lv-bars">
                        <?php foreach ([38, 52, 44, 66, 58, 74, 62, 86, 70, 94] as $i => $h): ?>
                            <span style="--h:<?= $h ?>%;--i:<?= $i ?>"></span>
                        <?php endforeach; ?>
                    </div>
                    <svg viewBox="0 0 200 80" preserveAspectRatio="none"><path pathLength="1" d="M4 62 24 50 44 56 64 38 84 44 104 28 124 36 144 18 164 24 196 6"/></svg>
                </div>
                <h3><?= e(t('lp_f_reports')) ?></h3>
                <p><?= e(t('lp_f_reports_text')) ?></p>
            </article>
            <article class="lp-bcard lp-b-debt lp-spot" data-reveal style="--d:.06s">
                <div class="lv lv-ledger" aria-hidden="true">
                    <div class="lv-row"><b>A</b><span><i style="width:72%"></i></span><em>120 000</em></div>
                    <div class="lv-row lv-row-paid"><b>D</b><span><i style="width:46%"></i></span><em>45 000</em><u><?= $icon('check') ?></u></div>
                    <div class="lv-row"><b>S</b><span><i style="width:58%"></i></span><em>80 000</em></div>
                </div>
                <h3><?= e(t('lp_f_debt')) ?></h3>
                <p><?= e(t('lp_f_debt_text')) ?></p>
            </article>
            <article class="lp-bcard lp-b-staff lp-spot" data-reveal style="--d:.12s">
                <div class="lv lv-staff" aria-hidden="true">
                    <?php foreach (['A', 'D', 'S'] as $i => $ch): ?>
                        <div class="lv-person" style="--i:<?= $i ?>"><b><?= $ch ?></b><span></span><u class="lv-sw"><i></i></u></div>
                    <?php endforeach; ?>
                </div>
                <h3><?= e(t('lp_f_staff')) ?></h3>
                <p><?= e(t('lp_f_staff_text')) ?></p>
            </article>
            <article class="lp-bcard lp-b-purchases lp-spot" data-reveal>
                <div class="lv lv-truck" aria-hidden="true">
                    <div class="lv-shelf"><span></span><span></span><span></span></div>
                    <div class="lv-road"></div>
                    <div class="lv-truck-ico"><?= $icon('truck') ?></div>
                </div>
                <h3><?= e(t('lp_f_purchases')) ?></h3>
                <p><?= e(t('lp_f_purchases_text')) ?></p>
            </article>
            <article class="lp-bcard lp-b-receipt lp-spot" data-reveal style="--d:.06s">
                <div class="lv lv-print" aria-hidden="true">
                    <div class="lv-slot"></div>
                    <div class="lv-paper">
                        <span></span><span></span><span class="short"></span><span></span>
                        <div class="lv-paper-code"><?= $barcode(18) ?></div>
                    </div>
                </div>
                <h3><?= e(t('lp_f_receipt')) ?></h3>
                <p><?= e(t('lp_f_receipt_text')) ?></p>
            </article>
            <article class="lp-bcard lp-b-telegram lp-spot" data-reveal style="--d:.12s">
                <div class="lv lv-chat" aria-hidden="true">
                    <div class="lv-bubble"><small><?= e(t('lp_demo_today')) ?></small><strong>4 280 000 <?= e($cur) ?></strong></div>
                    <div class="lv-typing"><i></i><i></i><i></i></div>
                </div>
                <h3><?= e(t('lp_f_telegram')) ?></h3>
                <p><?= e(t('lp_f_telegram_text')) ?></p>
            </article>
        </div>
    </div>
</section>

<section class="lp-section lp-tour" id="tour" aria-labelledby="lp-tour-title">
    <div class="lp-container">
        <h2 class="lp-h2" id="lp-tour-title" data-reveal><?= e(t('lp_tour_title')) ?></h2>
        <p class="lp-sub" data-reveal><?= e(t('lp_tour_lead')) ?></p>
        <div class="lp-tour-ui" id="lp-tour" data-reveal>
            <div class="lp-tour-tabs" role="tablist" aria-orientation="vertical" aria-label="<?= e(t('lp_tour_title')) ?>">
                <?php foreach ($tour as $i => [$key, $ic, $img]): ?>
                    <button type="button" role="tab" class="lp-tour-tab<?= $i === 0 ? ' is-active' : '' ?>" id="lp-tab-<?= $key ?>" aria-controls="lp-pan-<?= $key ?>" aria-selected="<?= $i === 0 ? 'true' : 'false' ?>" tabindex="<?= $i === 0 ? '0' : '-1' ?>">
                        <span class="lp-icon"><?= $icon($ic) ?></span>
                        <span class="lp-tour-tab-body">
                            <strong><?= e(t('lp_f_' . $key)) ?></strong>
                            <span class="lp-tour-tab-text"><span><?= e(t('lp_f_' . $key . '_text')) ?></span></span>
                        </span>
                        <i class="lp-tour-progress" aria-hidden="true"></i>
                    </button>
                <?php endforeach; ?>
            </div>
            <div class="lp-tour-view">
                <?php foreach ($tour as $i => [$key, $ic, $img]): ?>
                    <div class="lp-tour-panel<?= $i === 0 ? ' is-active' : '' ?>" role="tabpanel" id="lp-pan-<?= $key ?>" aria-labelledby="lp-tab-<?= $key ?>" tabindex="0">
                        <?php if ($img === 'mobile'): ?>
                            <div class="lp-tour-phone">
                                <div class="lp-phone"><?= $shot('mobile', t('lp_phone_img_alt'), 540, 1169) ?></div>
                            </div>
                        <?php else: ?>
                            <div class="lp-browser">
                                <div class="lp-browser-bar" aria-hidden="true"><i></i><i></i><i></i><span class="lp-url">kassiron.uz</span></div>
                                <?= $shot($img, $img === 'reports' ? t('lp_hero_img_alt') : t('lp_desktop_img_alt'), 1440, 900) ?>
                            </div>
                        <?php endif; ?>
                    </div>
                <?php endforeach; ?>
            </div>
        </div>
    </div>
</section>

<section class="lp-section lp-debt" aria-labelledby="lp-debt-title">
    <div class="lp-container lp-show">
        <div class="lp-show-copy" data-reveal>
            <h2 class="lp-h2 lp-left" id="lp-debt-title"><?= e(t('lp_show_debt_title')) ?></h2>
            <p class="lp-sub lp-left"><?= e(t('lp_show_debt_text')) ?></p>
            <ul class="lp-list">
                <?php foreach (['lp_show_debt_1', 'lp_show_debt_2', 'lp_show_debt_3'] as $key): ?>
                    <li><span class="lp-tick"><?= $icon('check') ?></span><?= e(t($key)) ?></li>
                <?php endforeach; ?>
            </ul>
        </div>
        <div class="lp-debt-vis" data-reveal style="--d:.1s">
            <div class="lp-paper" aria-hidden="true">
                <i class="lp-paper-clip"></i>
                <span class="lp-paper-tag"><?= e(t('lp_note_old')) ?></span>
                <ul>
                    <li><span>Anvar</span><b>120 000</b></li>
                    <li class="is-struck"><span>Dilnoza</span><b>45 000</b></li>
                    <li><span>Sardor</span><b>80 000</b></li>
                    <li class="is-struck"><span>Malika</span><b>230 000</b></li>
                    <li class="lp-paper-sum"><span><?= e(t('lp_demo_total')) ?></span><b>???</b></li>
                </ul>
                <i class="lp-paper-ring"></i>
            </div>
            <div class="lp-frame lp-debt-app">
                <?= $shot('customers', t('lp_show_debt_alt'), 1440, 660) ?>
            </div>
        </div>
    </div>
</section>

<section class="lp-desktop" id="desktop" aria-labelledby="lp-desktop-title">
    <div class="lp-desktop-bg" aria-hidden="true"><i></i><i></i></div>
    <div class="lp-container lp-show">
        <div class="lp-show-copy" data-reveal>
            <span class="lp-eyebrow lp-eyebrow-dark"><?= e(t('lp_desktop_eyebrow')) ?></span>
            <h2 class="lp-h2 lp-left" id="lp-desktop-title"><?= e(t('lp_desktop_title')) ?></h2>
            <p class="lp-sub lp-left"><?= e(t('lp_desktop_text')) ?></p>
            <ul class="lp-list">
                <?php foreach (['lp_desktop_1', 'lp_desktop_2', 'lp_desktop_3', 'lp_desktop_4'] as $key): ?>
                    <li><span class="lp-tick"><?= $icon('check') ?></span><?= e(t($key)) ?></li>
                <?php endforeach; ?>
            </ul>
            <div class="lp-desktop-cta">
                <a href="<?= e(DESKTOP_DOWNLOAD_URL) ?>" class="btn lp-btn-lg lp-btn-main"><?= $icon('download') ?><?= e(t('lp_desktop_download')) ?></a>
                <p><?= e(t('lp_desktop_note')) ?></p>
            </div>
        </div>
        <div class="lp-window" id="lp-net" data-state="online" data-reveal style="--d:.1s"
             data-cur="<?= e($cur) ?>"
             data-t-online="<?= e(t('lp_net_online')) ?>"
             data-t-offline="<?= e(t('lp_offline_badge')) ?>"
             data-t-syncing="<?= e(t('lp_net_syncing')) ?>"
             data-t-synced="<?= e(t('lp_net_synced')) ?>"
             data-t-queue="<?= e(t('lp_net_queue')) ?>">
            <div class="lp-window-bar">
                <span class="lp-window-title">KassirON</span>
                <span class="lp-cloud" aria-hidden="true"><?= $icon('cloud') ?><i></i></span>
                <span class="lp-window-buttons" aria-hidden="true"><i></i><i></i><i></i></span>
            </div>
            <img src="<?= asset('img/landing/pos-dark.webp') ?>" alt="<?= e(t('lp_desktop_img_alt')) ?>" width="1440" height="900" loading="lazy" decoding="async">
            <div class="lp-net-toasts" id="lp-net-toasts" aria-hidden="true"></div>
            <div class="lp-net-panel">
                <div class="lp-net-state" aria-live="polite">
                    <span class="lp-net-dot"></span>
                    <span><strong data-net="title"><?= e(t('lp_net_online')) ?></strong><small data-net="sub"><?= e(t('lp_net_synced')) ?></small></span>
                </div>
                <button type="button" class="lp-switch" id="lp-net-switch" role="switch" aria-checked="true">
                    <span><?= e(t('lp_net_label')) ?></span>
                    <u class="lp-switch-track" aria-hidden="true"><i></i></u>
                </button>
            </div>
            <p class="lp-net-hint" id="lp-net-hint"><?= e(t('lp_net_hint')) ?></p>
        </div>
    </div>
</section>

<section class="lp-section" id="start" aria-labelledby="lp-start-title">
    <div class="lp-container">
        <h2 class="lp-h2" id="lp-start-title" data-reveal><?= e(t('lp_start_title')) ?></h2>
        <ol class="lp-steps" data-reveal>
            <?php foreach ([1, 2, 3] as $n): ?>
                <li class="lp-step" style="--i:<?= $n - 1 ?>">
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
        <div class="lp-cta-card" data-reveal>
            <div class="lp-cta-mesh" aria-hidden="true"><i></i><i></i><i></i></div>
            <svg class="lp-badge" viewBox="0 0 120 120" aria-hidden="true">
                <defs><path id="lp-badge-path" d="M60 60m-46 0a46 46 0 1 1 92 0a46 46 0 1 1-92 0"/></defs>
                <g class="lp-badge-ring"><text font-size="<?= $badgeFont ?>" textLength="289" lengthAdjust="spacing"><textPath href="#lp-badge-path"><?= e($badgeText) ?></textPath></text></g>
                <text class="lp-badge-inf" x="60" y="74" text-anchor="middle">∞</text>
            </svg>
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

<?php
$pageTitle = t('help_title');

if ($isSuperAdmin) {
    $faq = ['backup', 'theme'];
} else {
    $faq = ['sales', 'refund', 'variants', 'debt', 'expenses', 'reports', 'attendance'];
    if ($isOwner) {
        $faq[] = 'employees';
        $faq[] = 'activity';
    }
    $faq[] = 'theme';
}
?>
<section class="page-head">
    <h1><?= e(t('help_title')) ?></h1>
    <p class="muted"><?= e(t('help_hint')) ?></p>
</section>

<?php if (!$isSuperAdmin): ?>
<div class="card support-card">
    <h2><?= e(t('support_title')) ?></h2>
    <p class="muted"><?= e(t('support_hint')) ?></p>
    <div class="support-links">
        <a class="support-link" href="tel:<?= e(SUPPORT_PHONE) ?>">
            <span class="support-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16.4v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 1.1 3.7 2 2 0 0 1 3.1 1.5h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L7.1 9.4a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"/></svg></span>
            <span class="support-text">
                <span class="support-label"><?= e(t('support_phone')) ?></span>
                <strong><?= e(SUPPORT_PHONE_DISPLAY) ?></strong>
            </span>
        </a>
        <a class="support-link" href="https://t.me/<?= e(SUPPORT_TELEGRAM) ?>" target="_blank" rel="noopener noreferrer" data-telegram-chat>
            <span class="support-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21.5 3.5 2.8 10.8c-.9.4-.9 1.6 0 1.9l4.7 1.6 1.8 5.6c.2.8 1.2 1 1.8.4l2.7-2.6 4.9 3.6c.7.5 1.6.1 1.8-.7l3-15.7c.2-.9-.7-1.7-1.6-1.4z"/><path d="m7.5 14.3 10-7.3-7.4 8.6"/></svg></span>
            <span class="support-text">
                <span class="support-label">Telegram</span>
                <strong>@<?= e(SUPPORT_TELEGRAM) ?></strong>
            </span>
        </a>
    </div>
</div>
<?php endif; ?>

<div class="card faq-card">
    <?php foreach ($faq as $key): ?>
        <details class="faq-item">
            <summary><?= e(t('help_q_' . $key)) ?></summary>
            <p><?= e(t('help_a_' . $key)) ?></p>
        </details>
    <?php endforeach; ?>
</div>

<?php
$pageTitle = t('subscription_title');
$isTrial = ($shop['plan'] ?? '') === 'trial';
?>
<div class="subscription-card card">
    <div class="subscription-icon<?= $expired ? ' is-expired' : '' ?>" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5"/><path d="M9 2h6"/></svg>
    </div>
    <?php if ($expired): ?>
        <h1><?= e(t($isTrial ? 'subscription_trial_over' : 'subscription_over')) ?></h1>
        <p class="muted"><?= e(t('subscription_over_text')) ?></p>
    <?php elseif ($daysLeft !== null): ?>
        <h1><?= e(t($isTrial ? 'subscription_trial_left' : 'subscription_left', ['days' => $daysLeft])) ?></h1>
        <p class="muted"><?= e(t('subscription_left_text')) ?></p>
    <?php else: ?>
        <h1><?= e(t('subscription_active')) ?></h1>
    <?php endif; ?>

    <div class="subscription-actions">
        <a class="btn btn-primary" href="https://t.me/<?= e(SUPPORT_TELEGRAM) ?>" target="_blank" rel="noopener noreferrer" data-telegram-chat><?= e(t('subscription_pay_telegram')) ?></a>
        <a class="btn" href="tel:<?= e(SUPPORT_PHONE) ?>"><?= e(SUPPORT_PHONE_DISPLAY) ?></a>
    </div>
    <p class="muted subscription-safe"><?= e(t('subscription_data_safe')) ?></p>
    <?php if ($expired): ?>
        <form method="post" action="/logout" class="subscription-logout">
            <?= csrf_field() ?>
            <button type="submit" class="btn-link"><?= e(t('logout')) ?></button>
        </form>
    <?php endif; ?>
</div>

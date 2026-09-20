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

<div class="card faq-card">
    <?php foreach ($faq as $key): ?>
        <details class="faq-item">
            <summary><?= e(t('help_q_' . $key)) ?></summary>
            <p><?= e(t('help_a_' . $key)) ?></p>
        </details>
    <?php endforeach; ?>
</div>

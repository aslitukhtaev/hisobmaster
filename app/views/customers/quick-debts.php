<?php
$pageTitle = t('quick_debts_title');

$gridColumns = [
    ['key' => 'name', 'label' => t('full_name_label'), 'type' => 'text', 'required' => true, 'wide' => true],
    ['key' => 'phone', 'label' => t('phone'), 'type' => 'tel', 'placeholder' => '+998 90 123 45 67'],
    ['key' => 'amount', 'label' => t('debt_amount'), 'type' => 'number', 'required' => true],
    ['key' => 'note', 'label' => t('note'), 'type' => 'text', 'wide' => true],
];
$gridEndpoint = '/customers/quick-debts';
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('quick_debts_title')) ?></h1>
        <p class="muted"><?= e(t('quick_debts_hint')) ?></p>
    </div>
    <a href="/customers" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
</section>

<?php require BASE_PATH . '/app/views/partials/quick-grid.php'; ?>

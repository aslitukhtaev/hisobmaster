<?php
$cards = [
    ['icon' => '🧾', 'title' => t('sales'), 'desc' => t('coming_soon')],
    ['icon' => '📦', 'title' => t('products'), 'desc' => t('coming_soon')],
    ['icon' => '👥', 'title' => t('customers'), 'desc' => t('coming_soon')],
    ['icon' => '💸', 'title' => t('expenses'), 'desc' => t('coming_soon')],
    ['icon' => '📊', 'title' => t('reports'), 'desc' => t('coming_soon')],
    ['icon' => '🧑‍🤝‍🧑', 'title' => t('employees'), 'desc' => t('coming_soon')],
];
?>
<section class="page-head">
    <h1><?= e(t('welcome', ['name' => $user['full_name'] ?? ''])) ?></h1>
    <p class="muted"><?= e(t('system_running')) ?> — HisobMaster</p>
</section>

<section class="card-grid">
    <?php foreach ($cards as $card): ?>
        <div class="module-card">
            <div class="module-icon"><?= $card['icon'] ?></div>
            <div class="module-title"><?= e($card['title']) ?></div>
            <div class="module-badge"><?= e($card['desc']) ?></div>
        </div>
    <?php endforeach; ?>
</section>

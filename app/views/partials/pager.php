<?php
/**
 * "Oldingi / Keyingi" for a paged list. Expects $page (1-based) and $hasNext;
 * keeps the page's other query parameters (search, filters).
 */
$pagerLink = static function (int $to): string {
    $query = $_GET;
    $query['page'] = $to;
    if ($to === 1) {
        unset($query['page']);
    }
    $path = strtok((string) ($_SERVER['REQUEST_URI'] ?? '/'), '?');

    return e($path . ($query !== [] ? '?' . http_build_query($query) : ''));
};
?>
<?php if ($page > 1 || $hasNext): ?>
<nav class="pager" aria-label="<?= e(t('pager_label')) ?>">
    <?php if ($page > 1): ?>
        <a class="btn btn-ghost btn-sm" href="<?= $pagerLink($page - 1) ?>">← <?= e(t('pager_prev')) ?></a>
    <?php else: ?>
        <span></span>
    <?php endif; ?>
    <span class="muted"><?= e(t('pager_page', ['page' => $page])) ?></span>
    <?php if ($hasNext): ?>
        <a class="btn btn-ghost btn-sm" href="<?= $pagerLink($page + 1) ?>"><?= e(t('pager_next')) ?> →</a>
    <?php else: ?>
        <span></span>
    <?php endif; ?>
</nav>
<?php endif; ?>

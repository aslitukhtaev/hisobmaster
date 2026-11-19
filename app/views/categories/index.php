<?php
$pageTitle = t('categories');
$isExpense = $type === 'expense';
$listUrl = $isExpense ? '/expenses' : '/products';
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('categories')) ?></h1>
        <p class="muted"><?= e(t($isExpense ? 'categories_hint_expense' : 'categories_hint_product')) ?></p>
    </div>
    <a href="<?= $listUrl ?>" class="btn btn-ghost"><?= e(t($isExpense ? 'expenses' : 'products')) ?></a>
</section>

<?php if (count($types) > 1): ?>
<div class="filter-tabs">
    <?php foreach ($types as $tab): ?>
        <a href="/categories?type=<?= e($tab) ?>" class="filter-tab <?= $tab === $type ? 'active' : '' ?>"><?= e(t('categories_tab_' . $tab)) ?></a>
    <?php endforeach; ?>
</div>
<?php endif; ?>

<form method="post" action="/categories" class="search-bar category-add">
    <?= csrf_field() ?>
    <input type="hidden" name="type" value="<?= e($type) ?>">
    <label class="sr-only" for="category-new-name"><?= e(t('category_name')) ?></label>
    <input type="text" id="category-new-name" name="name" maxlength="100" required placeholder="<?= e(t('category_new_placeholder')) ?>">
    <button type="submit" class="btn btn-primary btn-sm"><?= e(t('category_add')) ?></button>
</form>

<?php if (empty($categories)): ?>
    <div class="card">
        <p class="muted"><?= e(t('categories_empty')) ?></p>
    </div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table category-table">
                <thead>
                    <tr>
                        <th><?= e(t('category_name')) ?></th>
                        <th><?= e(t($isExpense ? 'categories_count_expense' : 'categories_count_product')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($categories as $category):
                    $id = (int) $category['id'];
                    $count = (int) $category['item_count'];
                    $others = array_filter($categories, static fn ($c) => (int) $c['id'] !== $id);
                ?>
                    <tr>
                        <td class="cell-wrap"><strong><?= e($category['name']) ?></strong></td>
                        <td data-label="<?= e(t($isExpense ? 'categories_count_expense' : 'categories_count_product')) ?>">
                            <?php if ($count > 0 && !$isExpense): ?>
                                <a href="/products?category=<?= $id ?>"><?= $count ?></a>
                            <?php else: ?>
                                <span class="muted"><?= $count ?></span>
                            <?php endif; ?>
                        </td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <details class="category-actions">
                                <summary class="btn btn-ghost btn-sm"><?= e(t('edit')) ?></summary>
                                <div class="category-actions-body">
                                    <form method="post" action="/categories/<?= $id ?>" class="category-action">
                                        <?= csrf_field() ?>
                                        <label class="field">
                                            <span><?= e(t('category_rename')) ?></span>
                                            <input type="text" name="name" maxlength="100" required value="<?= e($category['name']) ?>">
                                        </label>
                                        <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('save')) ?></button>
                                    </form>
                                    <?php if (!empty($others)): ?>
                                    <form method="post" action="/categories/<?= $id ?>/merge" class="category-action"
                                          data-confirm="<?= e(t('category_merge_confirm', ['name' => $category['name'], 'count' => $count])) ?>">
                                        <?= csrf_field() ?>
                                        <label class="field">
                                            <span><?= e(t('category_merge_into')) ?></span>
                                            <select name="into" required>
                                                <option value=""><?= e(t('category_merge_choose')) ?></option>
                                                <?php foreach ($others as $other): ?>
                                                    <option value="<?= (int) $other['id'] ?>"><?= e($other['name']) ?></option>
                                                <?php endforeach; ?>
                                            </select>
                                        </label>
                                        <button type="submit" class="btn btn-ghost btn-sm"><?= e(t('category_merge')) ?></button>
                                    </form>
                                    <?php endif; ?>
                                    <form method="post" action="/categories/<?= $id ?>/delete" class="category-action"
                                          data-confirm="<?= e(t($isExpense ? 'category_delete_confirm_expense' : 'category_delete_confirm_product', ['name' => $category['name'], 'count' => $count])) ?>">
                                        <?= csrf_field() ?>
                                        <button type="submit" class="btn btn-ghost btn-sm btn-danger-text"><?= e(t('delete')) ?></button>
                                    </form>
                                </div>
                            </details>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

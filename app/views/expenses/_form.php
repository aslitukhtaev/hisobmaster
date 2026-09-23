<?php $expense = $expense ?? []; ?>
<div class="card form-card">
    <form method="post" action="<?= e($actionUrl) ?>" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('amount')) ?></span>
            <input type="number" step="0.01" min="0.01" max="<?= MONEY_MAX ?>" inputmode="decimal" name="amount" required autofocus
                   value="<?= e(old('amount', isset($expense['amount']) ? (string) $expense['amount'] : '')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('category')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="category" list="expense-category-list"
                   value="<?= e(old('category', $expense['category_name'] ?? '')) ?>">
            <datalist id="expense-category-list">
                <?php foreach ($categories as $cat): ?>
                    <option value="<?= e($cat['name']) ?>">
                <?php endforeach; ?>
            </datalist>
        </label>
        <label class="field">
            <span><?= e(t('expense_date_label')) ?></span>
            <input type="date" name="expense_date" required min="2000-01-01" max="<?= e(date('Y-m-d')) ?>"
                   value="<?= e(old('expense_date', $expense['expense_date'] ?? date('Y-m-d'))) ?>">
        </label>
        <label class="field">
            <span><?= e(t('description')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="description" value="<?= e(old('description', $expense['description'] ?? '')) ?>">
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e($submitLabel) ?></button>
    </form>
</div>

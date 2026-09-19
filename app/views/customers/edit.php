<section class="page-head">
    <h1><?= e(t('edit_customer')) ?></h1>
</section>

<div class="card form-card">
    <form method="post" action="/customers/<?= (int) $customer['id'] ?>" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('full_name_label')) ?></span>
            <input type="text" name="full_name" required value="<?= e(old('full_name', $customer['full_name'])) ?>">
        </label>
        <label class="field">
            <span><?= e(t('phone')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="phone" value="<?= e(old('phone', $customer['phone'] ?? '')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('note')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="note" value="<?= e(old('note', $customer['note'] ?? '')) ?>">
        </label>
        <hr class="divider">
        <label class="field">
            <span><?= e(t('credit_limit_label')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="number" name="credit_limit" min="0" step="0.01" inputmode="decimal"
                   value="<?= e(old('credit_limit', $customer['credit_limit'] !== null ? (string) $customer['credit_limit'] : '')) ?>">
            <span class="muted"><?= e(t('credit_limit_hint')) ?></span>
        </label>
        <label class="field">
            <span><?= e(t('debt_due_date_label')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="date" name="debt_due_date"
                   value="<?= e(old('debt_due_date', $customer['debt_due_date'] ?? '')) ?>">
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('save')) ?></button>
    </form>
</div>

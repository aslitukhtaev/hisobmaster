<section class="page-head">
    <h1><?= e(t('edit_supplier')) ?></h1>
</section>

<div class="card form-card">
    <form method="post" action="/suppliers/<?= (int) $supplier['id'] ?>" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('supplier_name')) ?></span>
            <input type="text" name="name" required value="<?= e(old('name', $supplier['name'])) ?>">
        </label>
        <label class="field">
            <span><?= e(t('phone')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="phone" value="<?= e(old('phone', $supplier['phone'] ?? '')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('address')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="address" value="<?= e(old('address', $supplier['address'] ?? '')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('note')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="note" value="<?= e(old('note', $supplier['note'] ?? '')) ?>">
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('save')) ?></button>
    </form>
</div>

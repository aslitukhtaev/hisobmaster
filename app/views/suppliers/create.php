<section class="page-head">
    <h1><?= e(t('add_supplier')) ?></h1>
</section>

<div class="card form-card">
    <form method="post" action="/suppliers" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('supplier_name')) ?></span>
            <input type="text" name="name" required autofocus value="<?= e(old('name')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('phone')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="phone" value="<?= e(old('phone')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('address')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="address" value="<?= e(old('address')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('note')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="note" value="<?= e(old('note')) ?>">
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('add_supplier')) ?></button>
    </form>
</div>

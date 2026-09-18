<section class="page-head">
    <h1><?= e(t('add_customer')) ?></h1>
</section>

<div class="card form-card">
    <form method="post" action="/customers" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('full_name_label')) ?></span>
            <input type="text" name="full_name" required autofocus value="<?= e(old('full_name')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('phone')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="phone" value="<?= e(old('phone')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('note')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="note" value="<?= e(old('note')) ?>">
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('add_customer')) ?></button>
    </form>
</div>

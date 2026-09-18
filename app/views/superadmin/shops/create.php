<section class="page-head">
    <h1><?= e(t('create_shop')) ?></h1>
    <p class="muted"><?= e(t('create_shop_hint')) ?></p>
</section>

<div class="card form-card">
    <form method="post" action="/superadmin/shops" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('shop_name')) ?></span>
            <input type="text" name="name" required value="<?= e(old('name')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('owner_full_name')) ?></span>
            <input type="text" name="owner_full_name" required value="<?= e(old('owner_full_name')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('phone')) ?></span>
            <input type="text" name="phone" required placeholder="+998 90 123 45 67" value="<?= e(old('phone')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('address')) ?> (<?= e(t('optional')) ?>)</span>
            <input type="text" name="address" value="<?= e(old('address')) ?>">
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('create_shop')) ?></button>
    </form>
</div>

<div class="auth-card">
    <div class="auth-brand">
        <img class="auth-logo logo-for-light-theme" src="<?= asset('img/logo-dark.png') ?>" alt="<?= e(t('app_name')) ?>">
        <img class="auth-logo logo-for-dark-theme" src="<?= asset('img/logo-light.png') ?>" alt="<?= e(t('app_name')) ?>">
        <h1><?= e($shop['name'] ?? '') ?></h1>
        <p class="muted"><?= e(t('join_as_employee_hint')) ?></p>
    </div>

    <?php require BASE_PATH . '/app/views/partials/flash.php'; ?>

    <form method="post" action="/join/<?= e($token) ?>" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('full_name_label')) ?></span>
            <input type="text" name="full_name" required autofocus value="<?= e(old('full_name')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('phone')) ?></span>
            <input type="text" name="phone" value="<?= e(old('phone')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('login')) ?></span>
            <input type="text" name="login" required value="<?= e(old('login')) ?>">
        </label>
        <label class="field">
            <span><?= e(t('password')) ?></span>
            <input type="password" name="password" autocomplete="new-password" required>
        </label>
        <label class="field">
            <span><?= e(t('confirm_password')) ?></span>
            <input type="password" name="confirm_password" autocomplete="new-password" required>
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('join_button')) ?></button>
    </form>
</div>

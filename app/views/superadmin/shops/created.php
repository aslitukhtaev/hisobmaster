<section class="page-head">
    <h1><?= e(t('new_shop_credentials')) ?></h1>
    <p class="muted"><?= e(t('credentials_warning')) ?></p>
</section>

<div class="card form-card">
    <p class="muted" style="margin-bottom:14px;font-weight:600;"><?= e($creds['shop_name'] ?? '') ?></p>

    <div class="cred-row">
        <span class="cred-label"><?= e(t('login')) ?></span>
        <span class="cred-value"><?= e($creds['login'] ?? '') ?></span>
    </div>
    <div class="cred-row">
        <span class="cred-label"><?= e(t('password')) ?></span>
        <span class="cred-value"><?= e($creds['password'] ?? '') ?></span>
    </div>

    <a href="/superadmin/shops" class="btn btn-primary btn-block" style="margin-top:20px;"><?= e(t('back_to_list')) ?></a>
</div>

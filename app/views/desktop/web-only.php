<?php $pageTitle = t('desktop_web_only_title'); ?>
<section class="page-head">
    <h1><?= e(t('desktop_web_only_title')) ?></h1>
</section>
<div class="card form-card">
    <p><?= e(t('desktop_web_only_text', ['url' => preg_replace('#^https?://#', '', $server)])) ?></p>
    <a href="<?= e($server) ?>" class="btn btn-primary" target="_blank" rel="noopener" style="margin-top:14px;"><?= e(t('desktop_open_site')) ?></a>
</div>

<?php $error = flash('error'); $warning = flash('warning'); $success = flash('success'); ?>
<?php if ($error): ?>
    <div class="alert alert-error flash-alert" role="alert"><?= e($error) ?></div>
<?php endif; ?>
<?php if ($warning): ?>
    <div class="alert alert-warning flash-alert" role="status"><?= e($warning) ?></div>
<?php endif; ?>
<?php if ($success): ?>
    <div class="alert alert-success flash-alert" role="status"><?= e($success) ?></div>
<?php endif; ?>

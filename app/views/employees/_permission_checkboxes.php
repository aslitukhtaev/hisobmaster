<?php $checked = $checked ?? []; ?>
<div class="permission-grid">
    <?php foreach ($permissions as $permission): ?>
        <label class="permission-check">
            <input type="checkbox" name="permissions[]" value="<?= e($permission) ?>"
                   <?= in_array($permission, $checked, true) ? 'checked' : '' ?>>
            <span><?= e(t('permission_' . $permission)) ?></span>
        </label>
    <?php endforeach; ?>
</div>

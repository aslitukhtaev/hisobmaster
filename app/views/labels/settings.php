<?php
use App\Core\Auth;

$pageTitle = t('label_settings_title');
$isPreset = false;
foreach ($presets as [$pw, $ph]) {
    if ((int) $settings['w'] === $pw && (int) $settings['h'] === $ph) {
        $isPreset = true;
    }
}
?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('label_settings_title')) ?></h1>
        <p class="muted"><?= e(t('label_settings_hint')) ?></p>
    </div>
    <a href="/labels" class="btn btn-ghost"><?= e(t('labels_title')) ?></a>
</section>

<div class="card form-card">
    <form method="post" action="/labels/settings" class="stack">
        <?= csrf_field() ?>
        <div class="field-row">
            <label class="field">
                <span><?= e(t('label_width')) ?> (mm)</span>
                <input type="number" name="w" min="15" max="200" inputmode="numeric" value="<?= (int) $settings['w'] ?>" required>
            </label>
            <label class="field">
                <span><?= e(t('label_height')) ?> (mm)</span>
                <input type="number" name="h" min="15" max="200" inputmode="numeric" value="<?= (int) $settings['h'] ?>" required>
            </label>
        </div>
        <p class="muted">
            <?= e(t('label_presets')) ?>:
            <?php foreach ($presets as [$pw, $ph]): ?><span class="status-pill"><?= $pw ?>×<?= $ph ?></span> <?php endforeach; ?>
            <?php foreach ($sizes as $size): ?><span class="status-pill"><?= e($size['name']) ?> (<?= (int) $size['w'] ?>×<?= (int) $size['h'] ?>)</span> <?php endforeach; ?>
        </p>

        <div class="label-checks">
            <label class="permission-check"><input type="checkbox" name="name" value="1" <?= $settings['name'] ? 'checked' : '' ?>><span><?= e(t('label_opt_name')) ?></span></label>
            <label class="permission-check"><input type="checkbox" name="variant" value="1" <?= $settings['variant'] ? 'checked' : '' ?>><span><?= e(t('label_opt_variant')) ?></span></label>
            <label class="permission-check"><input type="checkbox" name="price" value="1" <?= $settings['price'] ? 'checked' : '' ?>><span><?= e(t('label_opt_price')) ?></span></label>
            <label class="permission-check"><input type="checkbox" name="digits" value="1" <?= $settings['digits'] ? 'checked' : '' ?>><span><?= e(t('label_opt_digits')) ?></span></label>
            <label class="permission-check"><input type="checkbox" name="shop" value="1" <?= $settings['shop'] ? 'checked' : '' ?>><span><?= e(t('label_opt_shop')) ?></span></label>
        </div>

        <?php if ($wholesale): ?>
            <div class="label-mode">
                <span class="muted"><?= e(t('label_price_which')) ?></span>
                <label class="radio-pill"><input type="radio" name="price_mode" value="retail" <?= $settings['price_mode'] === 'retail' ? 'checked' : '' ?>><span><?= e(t('price_mode_retail')) ?></span></label>
                <label class="radio-pill"><input type="radio" name="price_mode" value="wholesale" <?= $settings['price_mode'] === 'wholesale' ? 'checked' : '' ?>><span><?= e(t('price_mode_wholesale')) ?></span></label>
                <label class="radio-pill"><input type="radio" name="price_mode" value="both" <?= $settings['price_mode'] === 'both' ? 'checked' : '' ?>><span><?= e(t('label_price_both')) ?></span></label>
            </div>
        <?php else: ?>
            <input type="hidden" name="price_mode" value="retail">
        <?php endif; ?>

        <button type="submit" class="btn btn-primary btn-block"><?= e(t('save')) ?></button>
    </form>
</div>

<?php if (!empty($sizes)): ?>
<h2 style="margin-top:20px;"><?= e(t('label_my_sizes')) ?></h2>
<div class="card table-card">
    <div class="table-wrap">
        <table class="table">
            <tbody>
            <?php foreach ($sizes as $i => $size): ?>
                <tr>
                    <td class="cell-wrap"><strong><?= e($size['name']) ?></strong></td>
                    <td data-label="mm"><?= (int) $size['w'] ?> × <?= (int) $size['h'] ?></td>
                    <td>
                        <form method="post" action="/labels/sizes/delete" data-confirm="<?= e(t('label_size_delete_confirm', ['name' => $size['name']])) ?>">
                            <?= csrf_field() ?>
                            <input type="hidden" name="index" value="<?= (int) $i ?>">
                            <button type="submit" class="btn btn-ghost btn-sm btn-danger-text"><?= e(t('delete')) ?></button>
                        </form>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>
<?php endif; ?>

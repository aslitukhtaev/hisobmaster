<?php $pageTitle = t('import_products'); ?>
<section class="page-head">
    <h1><?= e(t('import_preview_title')) ?></h1>
</section>

<div class="import-summary-row">
    <span class="status-pill status-active"><?= count($validRows) ?> <?= e(t('import_ready_label')) ?></span>
    <?php if (!empty($skipped)): ?>
        <span class="status-pill status-blocked"><?= count($skipped) ?> <?= e(t('import_skipped_label')) ?></span>
    <?php endif; ?>
</div>

<?php if (!empty($validRows)): ?>
<div class="card table-card" style="margin-bottom:16px;">
    <div class="table-wrap">
        <table class="table">
            <thead>
                <tr>
                    <th><?= e(t('product_name')) ?></th>
                    <th><?= e(t('unit')) ?></th>
                    <th><?= e(t('cost_price')) ?></th>
                    <th><?= e(t('sell_price')) ?></th>
                    <th><?= e(t('stock_qty')) ?></th>
                    <th><?= e(t('barcode')) ?></th>
                    <th><?= e(t('status')) ?></th>
                </tr>
            </thead>
            <tbody>
            <?php foreach ($validRows as $row): ?>
                <tr>
                    <td><?= e($row['name']) ?></td>
                    <td data-label="<?= e(t('unit')) ?>" class="muted"><?= e($row['unit']) ?></td>
                    <td data-label="<?= e(t('cost_price')) ?>"><?= money((float) $row['cost_price']) ?></td>
                    <td data-label="<?= e(t('sell_price')) ?>"><?= money((float) $row['sell_price']) ?></td>
                    <td data-label="<?= e(t('stock_qty')) ?>"><?= e(format_qty((float) $row['stock_qty'])) ?></td>
                    <td data-label="<?= e(t('barcode')) ?>" class="muted"><?= e($row['barcode'] !== '' ? $row['barcode'] : '—') ?></td>
                    <td data-label="<?= e(t('status')) ?>">
                        <span class="status-pill <?= $row['will_update'] ? 'status-blocked' : 'status-active' ?>">
                            <?= e($row['will_update'] ? t('import_will_update') : t('import_will_create')) ?>
                        </span>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>
<?php endif; ?>

<?php if (!empty($skipped)): ?>
<div class="card table-card" style="margin-bottom:16px;">
    <div class="table-wrap">
        <table class="table">
            <thead>
                <tr>
                    <th><?= e(t('import_row_label')) ?></th>
                    <th><?= e(t('import_skip_reason_label')) ?></th>
                </tr>
            </thead>
            <tbody>
            <?php foreach ($skipped as $row): ?>
                <tr>
                    <td><?= (int) $row['row'] ?></td>
                    <td data-label="<?= e(t('import_skip_reason_label')) ?>" class="muted"><?= e($row['reason']) ?></td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>
<?php endif; ?>

<div class="row-actions">
    <a href="/products/import" class="btn btn-ghost"><?= e(t('import_cancel')) ?></a>
    <?php if (!empty($validRows)): ?>
    <form method="post" action="/products/import/commit">
        <?= csrf_field() ?>
        <input type="hidden" name="rows" value="<?= e($validRowsJson) ?>">
        <button type="submit" class="btn btn-primary"><?= e(t('confirm_import')) ?></button>
    </form>
    <?php endif; ?>
</div>

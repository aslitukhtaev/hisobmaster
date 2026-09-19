<?php $pageTitle = t('import_products'); ?>
<section class="page-head">
    <h1><?= e(t('import_products')) ?></h1>
    <p class="muted"><?= e(t('import_hint')) ?></p>
</section>

<div class="card form-card" style="max-width:560px;">
    <p class="muted"><?= e(t('import_columns_hint')) ?></p>
    <div class="import-format-hint">name,unit,cost_price,sell_price,stock_qty,barcode</div>

    <form method="post" action="/products/import/preview" enctype="multipart/form-data" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('csv_file_label')) ?></span>
            <input type="file" name="csv_file" accept=".csv,text/csv" required>
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('preview_import')) ?></button>
    </form>
</div>

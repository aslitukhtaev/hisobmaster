<?php $pageTitle = t('purchase_history'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('purchase_history')) ?></h1>
        <p class="muted"><?= count($purchases) ?> <?= e(t('purchases_count_label')) ?></p>
    </div>
    <a href="/purchases/create" class="btn btn-primary"><?= e(t('record_purchase')) ?></a>
</section>

<?php if (empty($purchases)): ?>
    <div class="card">
        <p class="muted"><?= e(t('no_purchases_yet')) ?></p>
    </div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('sale_date')) ?></th>
                        <th><?= e(t('supplier_name')) ?></th>
                        <th><?= e(t('amount')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($purchases as $purchase): ?>
                    <tr>
                        <td><?= e(local_datetime($purchase['created_at'])) ?></td>
                        <td data-label="<?= e(t('supplier_name')) ?>" class="muted"><?= e($purchase['supplier_name'] ?? t('no_supplier_option')) ?></td>
                        <td data-label="<?= e(t('amount')) ?>"><?= money((float) $purchase['total_amount']) ?></td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <a href="/purchases/<?= (int) $purchase['id'] ?>" class="btn btn-ghost btn-sm"><?= e(t('view_details')) ?></a>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

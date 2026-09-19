<?php $pageTitle = t('suppliers'); ?>
<section class="page-head page-head-row">
    <div>
        <h1><?= e(t('suppliers')) ?></h1>
        <p class="muted"><?= count($suppliers) ?> <?= e(t('suppliers_count_label')) ?></p>
    </div>
    <div class="row-actions">
        <a href="/purchases" class="btn btn-ghost"><?= e(t('purchase_history')) ?></a>
        <a href="/purchases/create" class="btn btn-primary"><?= e(t('record_purchase')) ?></a>
    </div>
</section>

<div class="row-actions" style="margin-bottom:16px;">
    <a href="/suppliers/create" class="btn btn-ghost"><?= e(t('add_supplier')) ?></a>
</div>

<?php if (empty($suppliers)): ?>
    <div class="card">
        <p class="muted"><?= e(t('no_suppliers_yet')) ?></p>
    </div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('supplier_name')) ?></th>
                        <th><?= e(t('phone')) ?></th>
                        <th><?= e(t('address')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($suppliers as $supplier): ?>
                    <tr>
                        <td><?= e($supplier['name']) ?></td>
                        <td data-label="<?= e(t('phone')) ?>" class="muted"><?= e($supplier['phone'] ?? '—') ?></td>
                        <td data-label="<?= e(t('address')) ?>" class="muted"><?= e($supplier['address'] ?? '—') ?></td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <a href="/suppliers/<?= (int) $supplier['id'] ?>/edit" class="btn btn-ghost btn-sm"><?= e(t('edit')) ?></a>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

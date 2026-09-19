<section class="page-head page-head-row">
    <div>
        <h1><?= e($customer['full_name']) ?></h1>
        <p class="muted"><?= e($customer['phone'] ?? t('no_phone')) ?></p>
    </div>
    <a href="/customers/<?= (int) $customer['id'] ?>/edit" class="btn btn-ghost"><?= e(t('edit')) ?></a>
</section>

<section class="stat-tile stock-value-tile">
    <span class="stat-label"><?= e(t('current_balance')) ?></span>
    <span class="stat-value stock-value-amount"><?= money((float) $balance) ?></span>
</section>

<?php if ((float) $balance > 0): ?>
<div class="card form-card">
    <h2><?= e(t('record_payment')) ?></h2>
    <form method="post" action="/customers/<?= (int) $customer['id'] ?>/payment" class="stack">
        <?= csrf_field() ?>
        <label class="field">
            <span><?= e(t('payment_amount')) ?></span>
            <input type="number" name="amount" min="0.01" step="0.01" max="<?= (float) $balance ?>" required inputmode="decimal">
        </label>
        <button type="submit" class="btn btn-primary btn-block"><?= e(t('record_payment')) ?></button>
    </form>
</div>
<?php endif; ?>

<?php if (!empty($customer['note'])): ?>
<div class="card" style="margin-bottom:16px;">
    <p class="muted"><?= e(t('note')) ?>:</p>
    <p><?= e($customer['note']) ?></p>
</div>
<?php endif; ?>

<h2 style="margin-top:20px;"><?= e(t('debt_history')) ?></h2>

<?php if (empty($history)): ?>
    <div class="card"><p class="muted"><?= e(t('no_debt_history')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('sale_date')) ?></th>
                        <th><?= e(t('debt_type')) ?></th>
                        <th><?= e(t('amount')) ?></th>
                        <th><?= e(t('debt_balance')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($history as $entry): ?>
                    <?php $debtTypeLabel = match ($entry['type']) {
                        'qarz' => t('debt_type_qarz'),
                        'refund' => t('debt_type_refund'),
                        default => t('debt_type_tolov'),
                    }; ?>
                    <tr>
                        <td><?= e(substr((string) $entry['created_at'], 0, 16)) ?></td>
                        <td data-label="<?= e(t('debt_type')) ?>">
                            <span class="status-pill <?= $entry['type'] === 'qarz' ? 'status-blocked' : 'status-active' ?>">
                                <?= e($debtTypeLabel) ?>
                            </span>
                        </td>
                        <td data-label="<?= e(t('amount')) ?>">
                            <?= $entry['type'] === 'qarz' ? '+' : '-' ?><?= money((float) $entry['amount']) ?>
                        </td>
                        <td data-label="<?= e(t('debt_balance')) ?>"><?= money((float) $entry['balance_after']) ?></td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <?php if (!empty($entry['sale_id'])): ?>
                                <a href="/sales/<?= (int) $entry['sale_id'] ?>" class="btn btn-ghost btn-sm"><?= e(t('view_receipt')) ?></a>
                            <?php else: ?>
                                <span class="muted">—</span>
                            <?php endif; ?>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    </div>
<?php endif; ?>

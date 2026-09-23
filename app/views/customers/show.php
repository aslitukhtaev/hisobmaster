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

<?php
$creditLimit = $customer['credit_limit'] !== null ? (float) $customer['credit_limit'] : null;
$creditLimitExceeded = $creditLimit !== null && (float) $balance > $creditLimit;
$dueDate = $customer['debt_due_date'] ?? null;
$isOverdue = $dueDate !== null && $dueDate !== '' && $dueDate < date('Y-m-d') && (float) $balance > 0;
?>

<section class="stat-tile stock-value-tile">
    <span class="stat-label"><?= e(t('credit_limit_label')) ?></span>
    <span class="stat-value stock-value-amount">
        <?= $creditLimit !== null ? money($creditLimit) : e(t('credit_limit_not_set')) ?>
        <?php if ($creditLimitExceeded): ?>
            <span class="status-pill status-blocked"><?= e(t('credit_limit_exceeded_badge')) ?></span>
        <?php endif; ?>
    </span>
</section>

<section class="stat-tile stock-value-tile">
    <span class="stat-label"><?= e(t('debt_due_date_label')) ?></span>
    <span class="stat-value stock-value-amount">
        <?= $dueDate ? e($dueDate) : e(t('debt_due_date_not_set')) ?>
        <?php if ($isOverdue): ?>
            <span class="status-pill status-blocked"><?= e(t('debt_overdue_badge')) ?></span>
        <?php endif; ?>
    </span>
</section>

<?php if ((float) $balance > 0): ?>
<div class="card form-card" id="debt-reminder">
    <h2><?= e(t('debt_reminder_title')) ?></h2>
    <p class="muted" style="margin-bottom:12px;"><?= e(t('debt_reminder_hint')) ?></p>
    <div class="stack">
        <label class="field">
            <span><?= e(t('debt_reminder_title')) ?></span>
            <textarea id="reminder-message-text" rows="4"><?= e($reminderMessage) ?></textarea>
        </label>
        <?php if (empty($customer['phone'])): ?>
            <p class="muted"><?= e(t('reminder_no_phone_hint')) ?></p>
        <?php endif; ?>
        <div class="row-actions">
            <a href="#" id="reminder-sms-btn" class="btn btn-ghost btn-sm"><?= e(t('send_via_sms')) ?></a>
            <a href="#" id="reminder-whatsapp-btn" class="btn btn-ghost btn-sm" target="_blank" rel="noopener"><?= e(t('send_via_whatsapp')) ?></a>
            <a href="#" id="reminder-telegram-btn" class="btn btn-ghost btn-sm" target="_blank" rel="noopener"><?= e(t('send_via_telegram')) ?></a>
            <button type="button" id="reminder-copy-btn" class="btn btn-ghost btn-sm"><?= e(t('copy_message')) ?></button>
        </div>
        <p class="muted" id="reminder-copy-status" role="status" aria-live="polite"></p>
    </div>
</div>
<script nonce="<?= e(csp_nonce()) ?>">
    window.HM_REMINDER = {
        message: <?= json_encode($reminderMessage, JSON_UNESCAPED_UNICODE) ?>,
        phone: <?= json_encode($customer['phone'] ?? '', JSON_UNESCAPED_UNICODE) ?>,
        phoneIntl: <?= json_encode(phone_digits_international($customer['phone'] ?? ''), JSON_UNESCAPED_UNICODE) ?>,
        copiedLabel: <?= json_encode(t('message_copied'), JSON_UNESCAPED_UNICODE) ?>,
        copyFailedLabel: <?= json_encode(t('copy_failed'), JSON_UNESCAPED_UNICODE) ?>
    };
</script>
<script src="<?= asset('js/debt-reminder.js') ?>" defer></script>
<?php endif; ?>

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

<h2 style="margin-top:20px;"><?= e(t('customer_purchase_history_title')) ?></h2>

<?php if (empty($purchases)): ?>
    <div class="card"><p class="muted"><?= e(t('no_purchase_history')) ?></p></div>
<?php else: ?>
    <div class="card table-card">
        <div class="table-wrap">
            <table class="table">
                <thead>
                    <tr>
                        <th><?= e(t('sale_date')) ?></th>
                        <th><?= e(t('items_count_column')) ?></th>
                        <th><?= e(t('total')) ?></th>
                        <th><?= e(t('payment_type')) ?></th>
                        <th><?= e(t('actions')) ?></th>
                    </tr>
                </thead>
                <tbody>
                <?php foreach ($purchases as $sale): ?>
                    <tr>
                        <td><?= e(local_datetime($sale['created_at'])) ?></td>
                        <td data-label="<?= e(t('items_count_column')) ?>"><?= (int) $sale['item_count'] ?></td>
                        <td data-label="<?= e(t('total')) ?>"><?= money((float) $sale['total']) ?></td>
                        <td data-label="<?= e(t('payment_type')) ?>">
                            <span class="status-pill status-active"><?= e(t('payment_' . $sale['payment_type'])) ?></span>
                        </td>
                        <td data-label="<?= e(t('actions')) ?>">
                            <a href="/sales/<?= (int) $sale['id'] ?>" class="btn btn-ghost btn-sm"><?= e(t('view_receipt')) ?></a>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
            </table>
        </div>
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
                        <td><?= e(local_datetime($entry['created_at'])) ?></td>
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

<?php
$printerWidth = (int) ($shop['receipt_printer_width'] ?? 80);
?>
<style>
    @page { size: <?= $printerWidth ?>mm 297mm; margin: 2mm; }
</style>
<section class="page-head page-head-row no-print">
    <div>
        <h1><?= e(t('receipt_title')) ?> #<?= e(receipt_number($sale)) ?></h1>
    </div>
    <div class="row-actions">
        <?php if ($canRefund): ?>
            <a href="/sales/<?= (int) $sale['id'] ?>/refund" class="btn btn-ghost"><?= e(t('refund_action')) ?></a>
        <?php endif; ?>
        <button type="button" class="btn btn-primary js-print-btn" id="receipt-print-btn"
                data-print-path="/sales/<?= (int) $sale['id'] ?>/print" data-paper="<?= $printerWidth ?>"
                <?= !empty($autoPrint) ? 'data-autoprint="1"' : '' ?>><?= e(t('print')) ?></button>
        <a href="/sales" class="btn btn-ghost"><?= e(t('back_to_list')) ?></a>
    </div>
</section>
<p class="muted no-print" id="telegram-print-hint" style="display:none;"><?= e(t('telegram_print_hint')) ?></p>
<?php if (App\Desktop\Desktop::enabled()): ?>
<div class="print-status no-print" id="print-status" role="status" aria-live="polite" hidden
     data-sent="<?= e(t('printer_sent')) ?>" data-printing="<?= e(t('printer_printing')) ?>"
     data-failed="<?= e(t('printer_failed')) ?>" data-no-printer="<?= e(t('printer_not_chosen')) ?>"
     data-dialog="<?= e(t('printer_use_dialog')) ?>" data-settings="<?= e(t('printer_settings_link')) ?>"></div>
<?php endif; ?>

<?php require BASE_PATH . '/app/views/sales/_receipt.php'; ?>

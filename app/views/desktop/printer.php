<?php $pageTitle = t('printer_title'); ?>
<section class="page-head">
    <h1><?= e(t('printer_title')) ?></h1>
    <p class="muted"><?= e(t('printer_hint')) ?></p>
</section>

<div class="card form-card" id="printer-settings"
     data-none="<?= e(t('printer_none_option')) ?>"
     data-default-mark="<?= e(t('printer_default_mark')) ?>"
     data-no-printers="<?= e(t('printer_no_printers')) ?>"
     data-saved="<?= e(t('printer_saved')) ?>"
     data-save-failed="<?= e(t('printer_save_failed')) ?>"
     data-test-sent="<?= e(t('printer_test_sent')) ?>"
     data-failed="<?= e(t('printer_failed')) ?>"
     data-choose-first="<?= e(t('printer_choose_first')) ?>"
     data-old-app="<?= e(t('printer_old_app')) ?>"
     data-printing="<?= e(t('printer_printing')) ?>"
     data-paper="<?= (int) $paperWidth ?>">
    <form class="stack" id="printer-form">
        <label class="field">
            <span><?= e(t('printer_label')) ?></span>
            <select id="printer-select" disabled>
                <option value=""><?= e(t('printer_loading')) ?></option>
            </select>
        </label>
        <div>
            <button type="button" class="btn btn-ghost btn-sm" id="printer-refresh" disabled><?= e(t('printer_refresh')) ?></button>
        </div>
        <label class="permission-check">
            <input type="checkbox" id="printer-auto" checked>
            <span><?= e(t('printer_auto_label')) ?></span>
        </label>
        <label class="field">
            <span><?= e(t('printer_copies_label')) ?></span>
            <select id="printer-copies">
                <option value="1">1</option>
                <option value="2">2</option>
                <option value="3">3</option>
            </select>
        </label>
        <p class="muted"><?= e(t('printer_paper_hint', ['width' => (int) $paperWidth])) ?></p>
        <div class="print-status" id="printer-status" role="status" aria-live="polite" hidden></div>
        <div class="row-actions">
            <button type="submit" class="btn btn-primary" id="printer-save" disabled><?= e(t('save')) ?></button>
            <button type="button" class="btn btn-ghost" id="printer-test" disabled><?= e(t('printer_test_button')) ?></button>
        </div>
    </form>
</div>

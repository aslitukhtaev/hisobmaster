<?php
/**
 * The spreadsheet-like grid of the quick-entry pages (QuickEntryController,
 * public/assets/js/quick-entry.js). Expects $gridColumns (key, label,
 * type: text|number|tel, placeholder?, list?: datalist options, default?),
 * $gridEndpoint and optionally $gridPrefill (values for the first row).
 */
$gridI18n = [
    'saving' => t('quick_saving'),
    'saved' => t('quick_saved_summary'),
    'errors' => t('quick_errors_summary'),
    'rows' => t('quick_rows_count'),
    'nothing' => t('quick_nothing'),
    'failed' => t('quick_failed'),
    'remove' => t('quick_remove_row'),
];
?>
<div class="card quick-card">
    <div class="quick-grid-wrap">
        <table class="quick-grid" id="quick-grid"
               data-endpoint="<?= e($gridEndpoint) ?>"
               data-csrf="<?= e(csrf_token()) ?>"
               data-columns="<?= e(json_encode($gridColumns, JSON_UNESCAPED_UNICODE)) ?>"
               data-prefill="<?= e(json_encode($gridPrefill ?? new stdClass(), JSON_UNESCAPED_UNICODE)) ?>"
               data-i18n="<?= e(json_encode($gridI18n, JSON_UNESCAPED_UNICODE)) ?>">
            <thead>
                <tr>
                    <th class="quick-num">#</th>
                    <?php foreach ($gridColumns as $column): ?>
                        <th<?= !empty($column['wide']) ? ' class="quick-wide"' : '' ?>><?= e($column['label']) ?><?= !empty($column['required']) ? ' <span class="quick-req">*</span>' : '' ?></th>
                    <?php endforeach; ?>
                    <th class="quick-x"></th>
                </tr>
            </thead>
            <tbody></tbody>
        </table>
    </div>
    <?php foreach ($gridColumns as $column): if (!empty($column['list'])): ?>
        <datalist id="quick-list-<?= e($column['key']) ?>">
            <?php foreach ($column['list'] as $option): ?><option value="<?= e($option) ?>"></option><?php endforeach; ?>
        </datalist>
    <?php endif; endforeach; ?>

    <div class="quick-footer">
        <button type="button" class="btn btn-ghost" id="quick-add-rows"><?= e(t('quick_add_rows')) ?></button>
        <span class="muted" id="quick-count"></span>
        <span class="quick-spacer"></span>
        <span class="quick-result" id="quick-result" role="status" aria-live="polite"></span>
        <button type="button" class="btn btn-primary" id="quick-save"><?= e(t('quick_save')) ?></button>
    </div>
</div>
<script src="<?= asset('js/quick-entry.js') ?>" defer></script>

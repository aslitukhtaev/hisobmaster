<?php
// Filled in by app.js when the desktop shell announces a code update; the
// texts travel as data attributes so the script needs no translations.
?>
<div id="desktop-update" class="alert alert-info desktop-update" role="status" hidden
     data-ready-title="<?= e(t('desktop_update_ready_title')) ?>"
     data-applied-title="<?= e(t('desktop_update_applied_title')) ?>"
     data-failed="<?= e(t('desktop_update_failed')) ?>"
     data-whats-new="<?= e(t('desktop_update_whats_new')) ?>"
     data-generic-note="<?= e(t('desktop_update_generic_note')) ?>"
     data-apply="<?= e(t('desktop_update_apply')) ?>"
     data-later="<?= e(t('desktop_update_later')) ?>"
     data-later-hint="<?= e(t('desktop_update_later_hint')) ?>"
     data-cart-warning="<?= e(t('desktop_update_cart_warning')) ?>"
     data-shell-ready="<?= e(t('desktop_shell_update_ready')) ?>"
     data-shell-restart="<?= e(t('desktop_shell_update_restart')) ?>"
     data-close="<?= e(t('close')) ?>"></div>

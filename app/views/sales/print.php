<?php
$pageTitle = t('receipt_title') . ' #' . receipt_number($sale);
require BASE_PATH . '/app/views/sales/_receipt.php';

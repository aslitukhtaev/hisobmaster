<?php

// Desktop app: one sync run (push local changes, pull the rest), started by
// the shell every minute and right after a sale. Prints the outcome and the
// current state as JSON on stdout for the shell.

declare(strict_types=1);

define('BASE_PATH', dirname(__DIR__));
$_SERVER['REQUEST_URI'] = '/desktop/sync-cli';

require BASE_PATH . '/app/bootstrap.php';

$result = App\Desktop\DesktopSync::run();
$license = App\Desktop\Desktop::get('license');

echo json_encode([
    'result' => $result,
    'status' => App\Desktop\Desktop::status(),
    // The shell verifies this itself too (signature, computer, date).
    'license' => $license,
], JSON_UNESCAPED_UNICODE) . "\n";

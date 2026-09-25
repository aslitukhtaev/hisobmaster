<?php

// Desktop app: code updates from the server (the shell verifies the
// signature and unpacks — see desktop/lib/code-updates.js).
//
//   php bin/desktop-update.php check
//       Is the server's code different from the code running here? Prints
//       {available, version, created_at, size, sha256, signature, notes}.
//   php bin/desktop-update.php download <directory> <version>
//       Saves the package as <directory>/<version>.bundle. Prints {file}.
//
// Output is JSON on stdout; {"error": "<code>"} when it didn't work.

declare(strict_types=1);

define('BASE_PATH', dirname(__DIR__));
$_SERVER['REQUEST_URI'] = '/desktop/update-cli';

require BASE_PATH . '/app/bootstrap.php';

use App\Desktop\Desktop;
use App\Desktop\DesktopSync;
use App\Desktop\SyncHttpException;
use App\Sync\CodePackage;

function out(array $data): never
{
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) . "\n";
    exit;
}

if (!Desktop::isActivated()) {
    out(['error' => 'not_activated']);
}

$command = $argv[1] ?? '';

try {
    if ($command === 'check') {
        $changelog = require BASE_PATH . '/app/changelog.php';
        $answer = DesktopSync::request('/api/desktop/update', [
            'version' => CodePackage::version(BASE_PATH),
            'changelog_date' => $changelog[0]['date'] ?? null,
        ]);
        out($answer + ['current' => CodePackage::version(BASE_PATH)]);
    }

    if ($command === 'download') {
        $dir = (string) ($argv[2] ?? '');
        $version = (string) ($argv[3] ?? '');
        if (!is_dir($dir) || !preg_match('/^[a-f0-9]{16}$/', $version)) {
            out(['error' => 'invalid_arguments']);
        }
        $bytes = DesktopSync::requestRaw('/api/desktop/package', ['version' => $version], timeout: 300);
        $file = $dir . DIRECTORY_SEPARATOR . $version . '.bundle';
        file_put_contents($file, $bytes);
        out(['file' => $file, 'size' => strlen($bytes), 'sha256' => hash('sha256', $bytes)]);
    }

    out(['error' => 'unknown_command']);
} catch (SyncHttpException $e) {
    out(['error' => $e->errorCode]);
} catch (Throwable $e) {
    log_exception($e);
    out(['error' => 'client_error']);
}

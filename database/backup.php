<?php

declare(strict_types=1);

/**
 * Manual/cron backup script. Running it once takes one snapshot — it does
 * NOT schedule anything by itself. To get a real daily backup you add a
 * crontab entry that runs this script, e.g. every day at 03:00 server time:
 *
 *   0 3 * * * /usr/bin/php /full/path/to/kassiron/database/backup.php >> /full/path/to/kassiron/database/backups/cron.log 2>&1
 *
 * (adjust the php binary path and the project path for your host; run
 * `which php` to find the former). Add it with `crontab -e`.
 *
 * If the host gives no cron access at all (common on cheap shared
 * hosting), this script is simply never scheduled — the app falls back to
 * an "opportunistic" backup taken from the super-admin dashboard instead
 * (see App\Models\Backup::shouldRunOpportunistic() and
 * DashboardController), which is best-effort and NOT a substitute for a
 * real cron schedule: it only runs when a super admin happens to open that
 * page, so a shop nobody logs into as super admin for weeks goes without
 * a backup for that long. Set up cron whenever the host allows it.
 */

define('BASE_PATH', dirname(__DIR__));
require BASE_PATH . '/app/bootstrap.php';

use App\Models\Backup;

$result = Backup::create();

echo "Zaxira yaratildi: {$result['filename']} (" . number_format($result['size'] / 1024, 1) . " KB)\n";

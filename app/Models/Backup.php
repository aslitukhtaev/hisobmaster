<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

/**
 * .db file snapshots, written via SQLite's own VACUUM INTO (a safe, live
 * copy — unlike a raw filesystem copy, it can't land mid-write) into
 * database/backups/. Two things create a snapshot, both funnelling through
 * create() below so there is exactly one way a backup gets made:
 *
 *  - database/backup.php, a CLI script meant to be run from cron. This is
 *    the only way a backup happens on a fixed schedule — nothing in this
 *    app can register a cron job on the host for the admin, so setting
 *    that up stays entirely manual (see that script's own header comment
 *    for the crontab line to add).
 *  - An opportunistic check on the super-admin dashboard (see
 *    DashboardController): if nothing has run in
 *    OPPORTUNISTIC_INTERVAL_HOURS, a backup is taken right then. This
 *    covers a shared host with no cron access at all, but it only fires
 *    when a super admin happens to load that page — a best-effort
 *    fallback, not a real schedule, and the super-admin UI says so rather
 *    than calling it "automatic".
 */
class Backup
{
    private const RETENTION = 30;
    private const OPPORTUNISTIC_INTERVAL_HOURS = 24;
    // A random 4-hex-char suffix, not just the second-resolution timestamp,
    // so two backups triggered within the same second (e.g. a manual "create
    // now" click landing in the same second as the cron script) never
    // collide into the same filename and silently overwrite each other.
    private const FILENAME_PATTERN = '/^kassiron-\d{4}-\d{2}-\d{2}-\d{6}-[a-f0-9]{4}\.db$/';

    public static function dir(): string
    {
        $dir = BASE_PATH . '/database/backups';
        if (!is_dir($dir)) {
            mkdir($dir, 0775, true);
        }

        return $dir;
    }

    /**
     * @return array{filename: string, size: int, created_at: int}
     */
    public static function create(): array
    {
        $filename = 'kassiron-' . date('Y-m-d-His') . '-' . bin2hex(random_bytes(2)) . '.db';
        $path = self::dir() . '/' . $filename;

        $pdo = Database::connect();
        $pdo->exec('VACUUM INTO ' . $pdo->quote($path));

        self::prune();

        return [
            'filename' => $filename,
            'size' => filesize($path) ?: 0,
            'created_at' => filemtime($path) ?: time(),
        ];
    }

    /**
     * Newest first.
     *
     * @return list<array{filename: string, size: int, created_at: int}>
     */
    public static function list(): array
    {
        $files = glob(self::dir() . '/kassiron-*.db') ?: [];
        $rows = [];

        foreach ($files as $file) {
            $rows[] = [
                'filename' => basename($file),
                'size' => filesize($file) ?: 0,
                'created_at' => filemtime($file) ?: 0,
            ];
        }

        usort($rows, static fn (array $a, array $b): int => $b['created_at'] <=> $a['created_at']);

        return $rows;
    }

    public static function latest(): ?array
    {
        return self::list()[0] ?? null;
    }

    public static function shouldRunOpportunistic(): bool
    {
        $latest = self::latest();
        if ($latest === null) {
            return true;
        }

        return $latest['created_at'] < time() - self::OPPORTUNISTIC_INTERVAL_HOURS * 3600;
    }

    /**
     * Full filesystem path for a backup filename, or null if it isn't a
     * name this class itself could have produced — guards download/delete
     * against path traversal from a URL segment.
     */
    public static function path(string $filename): ?string
    {
        if (!preg_match(self::FILENAME_PATTERN, $filename)) {
            return null;
        }

        $path = self::dir() . '/' . $filename;

        return is_file($path) ? $path : null;
    }

    public static function delete(string $filename): bool
    {
        $path = self::path($filename);

        return $path !== null && unlink($path);
    }

    private static function prune(): void
    {
        foreach (array_slice(self::list(), self::RETENTION) as $row) {
            $path = self::dir() . '/' . $row['filename'];
            if (is_file($path)) {
                unlink($path);
            }
        }
    }
}

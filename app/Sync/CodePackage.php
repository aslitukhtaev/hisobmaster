<?php

declare(strict_types=1);

namespace App\Sync;

use RuntimeException;

/**
 * The KassirON code as the desktop app runs it, packaged for its updates:
 * every change deployed to the server becomes a new package the computers
 * pick up (see desktop/lib/code-updates.js).
 *
 * Version: a content hash of the code files, so it changes exactly when the
 * code does — no one has to remember to bump a number. The same algorithm is
 * implemented in desktop/scripts/prepare-build.js for the code bundled with
 * the installer.
 *
 * Package: gzip of JSON {format, version, created_at, files: {path: base64}},
 * signed (the bytes as sent) with the license key, so the app accepts only
 * code that really came from this server.
 */
final class CodePackage
{
    /** What the desktop app runs — the same list as prepare-build.js's CODE_ITEMS. */
    public const ITEMS = ['app', 'public', 'bin', 'routes.php', 'database/migrations'];

    private const KEEP_PACKAGES = 3;

    /** @return array<string, string> relative path => absolute path, sorted by path */
    public static function files(string $root): array
    {
        $files = [];
        foreach (self::ITEMS as $item) {
            $path = $root . '/' . $item;
            if (is_file($path)) {
                $files[$item] = $path;
                continue;
            }
            if (!is_dir($path)) {
                continue;
            }
            $iterator = new \RecursiveIteratorIterator(new \RecursiveDirectoryIterator($path, \FilesystemIterator::SKIP_DOTS));
            foreach ($iterator as $file) {
                if ($file->isFile()) {
                    $relative = $item . '/' . str_replace('\\', '/', substr($file->getPathname(), strlen($path) + 1));
                    $files[$relative] = $file->getPathname();
                }
            }
        }
        ksort($files, SORT_STRING);

        return $files;
    }

    public static function version(string $root): string
    {
        $lines = [];
        foreach (self::files($root) as $relative => $absolute) {
            $lines[] = $relative . ':' . hash_file('sha256', $absolute);
        }

        return substr(hash('sha256', implode("\n", $lines)), 0, 16);
    }

    /**
     * The package of the code this server runs, built once per version and
     * kept in database/desktop-code/.
     *
     * @return array{version: string, created_at: int, size: int, sha256: string, signature: string, file: string}
     */
    public static function current(): array
    {
        $version = self::version(BASE_PATH);
        $dir = BASE_PATH . '/database/desktop-code';
        $metaFile = "$dir/$version.json";

        // A cached package is reused only if it was signed with today's key
        // (a restored database can bring back a different one).
        $keyId = hash('sha256', License::publicKey());
        if (is_file($metaFile)) {
            $meta = json_decode((string) file_get_contents($metaFile), true);
            if (is_array($meta) && is_file($meta['file'] ?? '') && ($meta['key_id'] ?? '') === $keyId) {
                return $meta;
            }
        }

        if (!is_dir($dir) && !mkdir($dir, 0775, true) && !is_dir($dir)) {
            throw new RuntimeException('desktop_code_dir');
        }

        $files = [];
        foreach (self::files(BASE_PATH) as $relative => $absolute) {
            $files[$relative] = base64_encode((string) file_get_contents($absolute));
        }
        $createdAt = time();
        $bytes = gzencode(json_encode([
            'format' => 1,
            'version' => $version,
            'created_at' => $createdAt,
            'files' => $files,
        ], JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR), 9);

        $bundle = "$dir/$version.bundle";
        file_put_contents("$bundle.tmp", $bytes);
        rename("$bundle.tmp", $bundle);

        $meta = [
            'version' => $version,
            'created_at' => $createdAt,
            'size' => strlen($bytes),
            'sha256' => hash('sha256', $bytes),
            'signature' => License::sign($bytes),
            'key_id' => $keyId,
            'file' => $bundle,
        ];
        file_put_contents($metaFile, json_encode($meta));
        self::prune($dir);

        return $meta;
    }

    /**
     * What's new since the computer's code: the changelog entries (app/
     * changelog.php) it doesn't have yet. New entries are only ever added at
     * the top, so a computer that has $knownCount of them lacks the first
     * (total - $knownCount). Apps from before the count was sent give only
     * the date of their newest entry: then, the entries dated after it.
     *
     * @return list<array{date: string, uz: string, ru: string}>
     */
    public static function notesSince(?string $lastDate, ?int $knownCount = null): array
    {
        $entries = require BASE_PATH . '/app/changelog.php';

        if ($knownCount !== null) {
            return array_slice($entries, 0, max(0, count($entries) - $knownCount));
        }

        return array_values(array_filter(
            $entries,
            static fn (array $entry): bool => $lastDate === null || $entry['date'] > $lastDate
        ));
    }

    private static function prune(string $dir): void
    {
        $metas = glob("$dir/*.json") ?: [];
        usort($metas, static fn (string $a, string $b): int => filemtime($b) <=> filemtime($a));
        foreach (array_slice($metas, self::KEEP_PACKAGES) as $old) {
            @unlink(substr($old, 0, -5) . '.bundle');
            @unlink($old);
        }
    }
}

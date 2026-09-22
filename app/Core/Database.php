<?php

declare(strict_types=1);

namespace App\Core;

use PDO;

class Database
{
    private static ?PDO $pdo = null;

    public static function connect(): PDO
    {
        if (self::$pdo === null) {
            $path = BASE_PATH . '/' . env('DB_PATH', 'database/kassiron.db');
            $dir = dirname($path);
            if (!is_dir($dir)) {
                mkdir($dir, 0775, true);
            }

            self::$pdo = new PDO('sqlite:' . $path);
            self::$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
            self::$pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
            self::$pdo->exec('PRAGMA foreign_keys = ON');
            // Without this, a connection that hits SQLite's write lock while
            // another one holds a BEGIN IMMEDIATE transaction (see
            // beginImmediate() below) fails immediately with "database is
            // locked" instead of waiting — which would turn "concurrent writers
            // serialize" into "the second one just errors out". This makes it
            // wait (retrying internally) for up to 5s before giving up.
            self::$pdo->exec('PRAGMA busy_timeout = 5000');
        }

        return self::$pdo;
    }

    /**
     * Starts a write-locking transaction: SQLite's `BEGIN IMMEDIATE` instead of
     * the deferred `BEGIN` that PDO::beginTransaction() issues. IMMEDIATE grabs
     * the reserved (write) lock right away, so a concurrent connection trying to
     * start its own write transaction blocks until this one commits or rolls
     * back, instead of both connections reading the same state and racing to
     * write based on it (a lost update).
     *
     * Commit/roll back with Database::commit()/Database::rollback() below, NOT
     * PDO's own commit()/rollBack() — on at least one real target environment
     * (PHP 8.3 under LiteSpeed's lsphp SAPI), PDO::commit() throws "There is no
     * active transaction" for a transaction started this way, because PDO's
     * core gates commit()/rollBack() on an internal flag that only
     * PDO::beginTransaction() sets, not a raw `BEGIN IMMEDIATE` exec — even
     * though SQLite itself genuinely has the transaction open. Going straight
     * to SQL for all three (BEGIN/COMMIT/ROLLBACK) sidesteps that gate
     * entirely instead of depending on it.
     */
    public static function beginImmediate(): PDO
    {
        $pdo = self::connect();
        $pdo->exec('BEGIN IMMEDIATE');

        return $pdo;
    }

    public static function commit(PDO $pdo): void
    {
        $pdo->exec('COMMIT');
    }

    /**
     * Silently a no-op when there's nothing to roll back (e.g. the failure
     * happened before BEGIN IMMEDIATE even ran) — callers use this from a
     * catch block that re-throws the original exception regardless, so a
     * rollback failure here must never mask it with a different one.
     */
    public static function rollback(PDO $pdo): void
    {
        try {
            $pdo->exec('ROLLBACK');
        } catch (\Throwable $e) {
            // Nothing to roll back — ignored, see doc comment above.
        }
    }
}

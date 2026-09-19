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
     * write based on it (a lost update). Commit/roll back with the normal
     * $pdo->commit() / $pdo->rollBack() — PDO's sqlite driver tracks the
     * transaction from the raw `BEGIN IMMEDIATE` exec just as it would from its
     * own beginTransaction().
     */
    public static function beginImmediate(): PDO
    {
        $pdo = self::connect();
        $pdo->exec('BEGIN IMMEDIATE');

        return $pdo;
    }
}

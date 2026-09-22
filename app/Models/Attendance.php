<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use Throwable;

class Attendance
{
    public static function find(int $id): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM attendance WHERE id = ?');
        $stmt->execute([$id]);

        return $stmt->fetch() ?: null;
    }

    /**
     * The user's currently open shift (clock_in set, clock_out still NULL),
     * or null when they aren't clocked in right now. Used both to decide
     * which button ("Ishga keldim" / "Ishni tugatdim") to show and, more
     * importantly, is re-checked server-side by clockIn()/clockOut() below —
     * never trust the button state alone.
     */
    public static function openShiftFor(int $userId): ?array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM attendance WHERE user_id = ? AND clock_out IS NULL ORDER BY id DESC LIMIT 1'
        );
        $stmt->execute([$userId]);

        return $stmt->fetch() ?: null;
    }

    /**
     * Opens a new shift for $userId, guarded against double-clock-in by a
     * BEGIN IMMEDIATE transaction: the "is there already an open shift" check
     * and the INSERT happen atomically, so two near-simultaneous clock-in
     * requests for the same user (double click, two open tabs) can't both
     * pass the check and create two open shifts at once — whichever gets the
     * write lock first commits its shift, the other then sees it and is
     * rejected (returns null) instead of racing it. This mirrors
     * DebtTransaction::record()'s read-then-insert guard.
     */
    public static function clockIn(int $shopId, int $userId): ?array
    {
        $pdo = Database::beginImmediate();

        try {
            $stmt = $pdo->prepare('SELECT id FROM attendance WHERE user_id = ? AND clock_out IS NULL LIMIT 1');
            $stmt->execute([$userId]);

            if ($stmt->fetch()) {
                if ($pdo->inTransaction()) {
                    $pdo->rollBack();
                }
                return null;
            }

            $pdo->prepare(
                "INSERT INTO attendance (shop_id, user_id, clock_in) VALUES (?, ?, datetime('now'))"
            )->execute([$shopId, $userId]);
            $id = (int) $pdo->lastInsertId();

            $pdo->commit();

            return self::find($id);
        } catch (Throwable $e) {
            // Guarded: commit()/an earlier statement can leave no transaction
            // open (e.g. SQLite implicitly aborting it under contention), in
            // which case rollBack() itself would throw "There is no active
            // transaction" and mask the real error with a confusing one.
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            throw $e;
        }
    }

    /**
     * Closes the user's currently open shift, if any. The UPDATE's own WHERE
     * clause (clock_out IS NULL) is the atomicity guard here — a single
     * statement needs no explicit transaction — so a clock-out request that
     * loses a race against another one for the same user just matches zero
     * rows the second time round instead of double-closing anything. Returns
     * false when there was no open shift to close (e.g. a stale button, or a
     * second tab's clock-out arriving after the first already closed it).
     */
    public static function clockOut(int $userId): bool
    {
        $stmt = Database::connect()->prepare(
            "UPDATE attendance SET clock_out = datetime('now') WHERE user_id = ? AND clock_out IS NULL"
        );
        $stmt->execute([$userId]);

        return $stmt->rowCount() > 0;
    }

    public static function openCountByShop(int $shopId): int
    {
        $stmt = Database::connect()->prepare(
            'SELECT COUNT(*) FROM attendance WHERE shop_id = ? AND clock_out IS NULL'
        );
        $stmt->execute([$shopId]);

        return (int) $stmt->fetchColumn();
    }

    /**
     * Every attendance row for the shop whose shift STARTED within the given
     * Tashkent calendar range (clock_in bucketed through
     * tashkent_day_bounds_utc(), exactly like every other created_at-range
     * query in this app), newest first, joined to the employee's name.
     */
    public static function historyByShop(int $shopId, string $from, string $to): array
    {
        [$startUtc, $endUtc] = tashkent_day_bounds_utc($from, $to);

        $stmt = Database::connect()->prepare(
            'SELECT a.*, u.full_name AS user_name
             FROM attendance a
             INNER JOIN users u ON u.id = a.user_id
             WHERE a.shop_id = ? AND a.clock_in >= ? AND a.clock_in < ?
             ORDER BY a.clock_in DESC'
        );
        $stmt->execute([$shopId, $startUtc, $endUtc]);

        return $stmt->fetchAll();
    }
}

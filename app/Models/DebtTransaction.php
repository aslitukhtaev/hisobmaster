<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use Throwable;

class DebtTransaction
{
    /**
     * A ledger row's effect on the balance: a 'qarz' adds to what the
     * customer owes, a 'tolov' (payment) or 'refund' takes it off.
     *
     * The balance is always this summed over every row, never "the
     * balance_after of the newest row": once the shop's computers sync
     * ledger rows recorded offline, rows no longer arrive in the order they
     * happened, and a newest-row balance would silently be wrong. A sum
     * doesn't depend on order at all.
     */
    public const SIGNED_AMOUNT_SQL = "CASE WHEN type = 'qarz' THEN amount ELSE -amount END";

    public static function currentBalance(int $customerId): float
    {
        $stmt = Database::connect()->prepare(
            'SELECT COALESCE(SUM(' . self::SIGNED_AMOUNT_SQL . '), 0) FROM debt_transactions WHERE customer_id = ?'
        );
        $stmt->execute([$customerId]);

        return round((float) $stmt->fetchColumn(), 2);
    }

    /**
     * Reads the customer's current balance and inserts the next ledger row with
     * the resulting balance_after.
     *
     * SQLite has no row-level `SELECT ... FOR UPDATE`, so this read-then-insert
     * is instead made safe with a `BEGIN IMMEDIATE` transaction (see
     * Database::beginImmediate()): that grabs SQLite's write lock up front, so a
     * concurrent call for the same customer (e.g. a payment recorded at the same
     * moment as a new credit sale) blocks until this one commits, instead of
     * both reading the same stale balance and racing to insert — which would
     * otherwise silently drop one of the two updates (a lost update on
     * balance_after).
     *
     * When record() is called from inside a transaction that's already open
     * (Sale::create() wraps the whole sale, including its debt row, in one
     * BEGIN IMMEDIATE; Refund::create() does the same for a refund's debt
     * adjustment), pass $nested = true so it just participates in that
     * transaction instead of trying to start a second one — PDO/SQLite
     * doesn't support nesting transactions, and the outer BEGIN IMMEDIATE
     * already holds the write lock this method needs. This is an explicit
     * flag from the caller rather than auto-detected via
     * `$pdo->inTransaction()`, since that call is not a reliable signal for
     * a transaction opened by a raw `BEGIN IMMEDIATE` on every PHP/PDO build
     * this app targets (see Database::commit()/rollback()'s doc comment).
     */
    public static function record(
        int $shopId,
        int $customerId,
        ?int $saleId,
        string $type,
        float $amount,
        int $createdBy,
        bool $nested = false
    ): void {
        $pdo = $nested ? Database::connect() : Database::beginImmediate();

        try {
            $current = self::currentBalance($customerId);
            $balanceAfter = $type === 'qarz' ? $current + $amount : $current - $amount;

            $pdo->prepare(
                'INSERT INTO debt_transactions (shop_id, customer_id, sale_id, type, amount, balance_after, created_by)
                 VALUES (?, ?, ?, ?, ?, ?, ?)'
            )->execute([$shopId, $customerId, $saleId, $type, $amount, $balanceAfter, $createdBy]);

            if (!$nested) {
                Database::commit($pdo);
            }
        } catch (Throwable $e) {
            if (!$nested) {
                Database::rollback($pdo);
            }
            throw $e;
        }
    }

    /**
     * The customer's ledger, newest first, with balance_after recomputed as
     * the running balance in the order the entries actually happened
     * (created_at, then id) — the stored value is only what the recording
     * database knew at that moment, which a synced entry from another
     * computer can make out of date.
     */
    public static function historyByCustomer(int $customerId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM debt_transactions WHERE customer_id = ? ORDER BY created_at ASC, id ASC'
        );
        $stmt->execute([$customerId]);
        $rows = $stmt->fetchAll();

        $running = 0.0;
        foreach ($rows as &$row) {
            $running = round($running + ($row['type'] === 'qarz' ? (float) $row['amount'] : -(float) $row['amount']), 2);
            $row['balance_after'] = $running;
        }
        unset($row);

        return array_reverse($rows);
    }

    public static function totalDebtByShop(int $shopId): float
    {
        // Per customer, so one customer's overpayment (a negative balance)
        // never reduces what the others owe.
        $stmt = Database::connect()->prepare(
            'SELECT customer_id, SUM(' . self::SIGNED_AMOUNT_SQL . ') AS balance
             FROM debt_transactions WHERE shop_id = ? GROUP BY customer_id'
        );
        $stmt->execute([$shopId]);

        $total = 0.0;
        foreach ($stmt->fetchAll() as $row) {
            $total += max(0.0, round((float) $row['balance'], 2));
        }

        return $total;
    }
}

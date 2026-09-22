<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use Throwable;

class DebtTransaction
{
    public static function currentBalance(int $customerId): float
    {
        $stmt = Database::connect()->prepare(
            'SELECT balance_after FROM debt_transactions WHERE customer_id = ? ORDER BY id DESC LIMIT 1'
        );
        $stmt->execute([$customerId]);
        $balance = $stmt->fetchColumn();

        return $balance !== false ? (float) $balance : 0.0;
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
     * BEGIN IMMEDIATE), it just participates in that transaction instead of
     * trying to start a second one — PDO/SQLite doesn't support nesting
     * transactions, and the outer BEGIN IMMEDIATE already holds the write lock
     * this method needs.
     */
    public static function record(
        int $shopId,
        int $customerId,
        ?int $saleId,
        string $type,
        float $amount,
        int $createdBy
    ): void {
        $pdo = Database::connect();
        $ownsTransaction = !$pdo->inTransaction();

        if ($ownsTransaction) {
            Database::beginImmediate();
        }

        try {
            $current = self::currentBalance($customerId);
            $balanceAfter = $type === 'qarz' ? $current + $amount : $current - $amount;

            $pdo->prepare(
                'INSERT INTO debt_transactions (shop_id, customer_id, sale_id, type, amount, balance_after, created_by)
                 VALUES (?, ?, ?, ?, ?, ?, ?)'
            )->execute([$shopId, $customerId, $saleId, $type, $amount, $balanceAfter, $createdBy]);

            if ($ownsTransaction) {
                $pdo->commit();
            }
        } catch (Throwable $e) {
            if ($ownsTransaction && $pdo->inTransaction()) {
                $pdo->rollBack();
            }
            throw $e;
        }
    }

    public static function historyByCustomer(int $customerId): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM debt_transactions WHERE customer_id = ? ORDER BY id DESC'
        );
        $stmt->execute([$customerId]);

        return $stmt->fetchAll();
    }

    public static function totalDebtByShop(int $shopId): float
    {
        // Each customer's current balance is their most recent balance_after row.
        $stmt = Database::connect()->prepare(
            'SELECT dt.customer_id, dt.balance_after FROM debt_transactions dt
             INNER JOIN (
                 SELECT customer_id, MAX(id) AS max_id FROM debt_transactions WHERE shop_id = ? GROUP BY customer_id
             ) latest ON latest.max_id = dt.id'
        );
        $stmt->execute([$shopId]);
        $rows = $stmt->fetchAll();

        $total = 0.0;
        foreach ($rows as $row) {
            $total += max(0.0, (float) $row['balance_after']);
        }

        return $total;
    }
}

<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use PDOException;

class Customer
{
    public static function allByShop(int $shopId): array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM customers WHERE shop_id = ? ORDER BY full_name');
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM customers WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function create(int $shopId, string $fullName, ?string $phone, ?string $note = null): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare('INSERT INTO customers (shop_id, full_name, phone, note) VALUES (?, ?, ?, ?)');
        $stmt->execute([$shopId, $fullName, $phone, $note]);

        return (int) $pdo->lastInsertId();
    }

    public static function findOrCreate(int $shopId, string $fullName, ?string $phone): int
    {
        $phone = $phone !== null && trim($phone) !== '' ? trim($phone) : null;

        if ($phone !== null) {
            $stmt = Database::connect()->prepare(
                'SELECT id FROM customers WHERE shop_id = ? AND phone = ? LIMIT 1'
            );
            $stmt->execute([$shopId, $phone]);
            $id = $stmt->fetchColumn();

            if ($id !== false) {
                return (int) $id;
            }
        }

        // Two concurrent requests for the same new phone number (e.g. two sales
        // submitted at nearly the same time) can both pass the SELECT above
        // before either INSERTs. Rather than trying to lock our way around that,
        // we rely on the UNIQUE(shop_id, phone) index (see schema.sql) to reject
        // the loser's INSERT, and fall back to re-selecting the row the winner
        // just created.
        try {
            return self::create($shopId, $fullName, $phone);
        } catch (PDOException $e) {
            if ($phone === null || !self::isUniqueViolation($e)) {
                throw $e;
            }

            $stmt = Database::connect()->prepare(
                'SELECT id FROM customers WHERE shop_id = ? AND phone = ? LIMIT 1'
            );
            $stmt->execute([$shopId, $phone]);
            $id = $stmt->fetchColumn();

            if ($id === false) {
                // Constraint failed but the row isn't there (shouldn't happen) —
                // rethrow rather than silently returning a bogus id.
                throw $e;
            }

            return (int) $id;
        }
    }

    private static function isUniqueViolation(PDOException $e): bool
    {
        // SQLite's PDO driver reports a UNIQUE constraint failure as SQLSTATE
        // 23000, with "UNIQUE constraint failed" in the driver message.
        return $e->getCode() === '23000'
            || str_contains($e->getMessage(), 'UNIQUE constraint failed');
    }

    public static function update(
        int $id,
        int $shopId,
        string $fullName,
        ?string $phone,
        ?string $note,
        ?float $creditLimit = null,
        ?string $debtDueDate = null
    ): void {
        Database::connect()
            ->prepare(
                'UPDATE customers SET full_name = ?, phone = ?, note = ?, credit_limit = ?, debt_due_date = ?
                 WHERE id = ? AND shop_id = ?'
            )
            ->execute([$fullName, $phone, $note, $creditLimit, $debtDueDate, $id, $shopId]);
    }

    /**
     * All customers in the shop with their current debt balance — the sum
     * of their ledger (see DebtTransaction::SIGNED_AMOUNT_SQL for why it's a
     * sum and not the newest row's balance_after).
     */
    public static function allWithBalance(int $shopId, bool $onlyDebtors = false): array
    {
        $sql = 'SELECT c.*, ROUND(COALESCE(ledger.balance, 0), 2) AS balance
                FROM customers c
                LEFT JOIN (
                    SELECT customer_id, SUM(' . DebtTransaction::SIGNED_AMOUNT_SQL . ') AS balance
                    FROM debt_transactions WHERE shop_id = ? GROUP BY customer_id
                ) ledger ON ledger.customer_id = c.id
                WHERE c.shop_id = ?';

        if ($onlyDebtors) {
            $sql .= ' AND ROUND(COALESCE(ledger.balance, 0), 2) > 0';
        }

        $sql .= ' ORDER BY balance DESC, c.full_name';

        $stmt = Database::connect()->prepare($sql);
        $stmt->execute([$shopId, $shopId]);

        return $stmt->fetchAll();
    }
}

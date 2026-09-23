<?php

declare(strict_types=1);

namespace App\Core;

use App\Models\Refund;
use PDO;
use Throwable;

/**
 * Every schema change and data backfill, in one idempotent pass. Used both by
 * database/migrate.php (CLI, verbose) and automatically on the first request
 * after a deploy (Database::connect() -> ensureUpToDate()): production is
 * deployed by a git push hook that only resets the code, so a schema change
 * must not depend on anyone remembering to run the CLI script.
 *
 * Bump VERSION whenever schema.sql or apply() changes — that's what makes an
 * existing database pick the change up (SQLite's PRAGMA user_version stores
 * the last applied VERSION in the file header, so the per-request check is a
 * single integer read).
 */
final class Migrator
{
    public const VERSION = 2;

    public static function ensureUpToDate(PDO $pdo): void
    {
        if ((int) $pdo->query('PRAGMA user_version')->fetchColumn() >= self::VERSION) {
            return;
        }

        self::migrate($pdo);
    }

    /**
     * Runs apply() under a write lock. Two requests racing here both run it —
     * the second one waits on the lock (busy_timeout) and then re-applies an
     * already-applied, idempotent migration, which is harmless.
     */
    public static function migrate(PDO $pdo, ?callable $log = null): void
    {
        $log ??= static function (string $message): void {
        };

        $pdo->exec('BEGIN IMMEDIATE');
        try {
            self::apply($pdo, $log);
            $pdo->exec('PRAGMA user_version = ' . self::VERSION);
            $pdo->exec('COMMIT');
        } catch (Throwable $e) {
            try {
                $pdo->exec('ROLLBACK');
            } catch (Throwable) {
                // Nothing to roll back.
            }
            throw $e;
        }
    }

    private static function apply(PDO $pdo, callable $log): void
    {
        $pdo->exec((string) file_get_contents(BASE_PATH . '/database/migrations/schema.sql'));

        // Existing databases keep their old tables (CREATE TABLE IF NOT EXISTS
        // leaves them alone), so columns added later are added here if missing.
        self::addColumns($pdo, $log, 'users', [
            'failed_login_attempts' => 'INTEGER NOT NULL DEFAULT 0',
            'locked_until' => 'TEXT',
            'commission_rate' => 'REAL',
            'onboarding_seen_at' => 'TEXT',
        ]);
        self::addColumns($pdo, $log, 'products', [
            'low_stock_threshold' => 'REAL',
            'pack_size' => 'INTEGER',
        ]);
        self::addColumns($pdo, $log, 'sale_items', [
            'variant_id' => 'INTEGER REFERENCES product_variants(id)',
            'variant_label' => 'TEXT',
        ]);
        self::addColumns($pdo, $log, 'customers', [
            'credit_limit' => 'REAL',
            'debt_due_date' => 'TEXT',
        ]);

        self::backfillRefundPayments($pdo, $log);
    }

    /**
     * @param array<string, string> $columns name => column definition
     */
    private static function addColumns(PDO $pdo, callable $log, string $table, array $columns): void
    {
        $existing = array_column($pdo->query("PRAGMA table_info($table)")->fetchAll(PDO::FETCH_ASSOC), 'name');

        foreach ($columns as $name => $definition) {
            if (!in_array($name, $existing, true)) {
                $pdo->exec("ALTER TABLE $table ADD COLUMN $name $definition");
                $log("$table jadvaliga $name ustuni qo'shildi.");
            }
        }
    }

    /**
     * Refunds recorded before refund_payments existed get the same
     * naqd/karta/qarz split a new refund gets (Refund::allocateByPayment(),
     * the same arithmetic the debt ledger already used for their qarz share).
     * Only refunds with no refund_payments rows at all are touched.
     */
    private static function backfillRefundPayments(PDO $pdo, callable $log): void
    {
        $pending = $pdo->query(
            'SELECT r.id AS refund_id, r.total_amount, s.id AS sale_id, s.total, s.paid_amount, s.payment_type
             FROM refunds r
             INNER JOIN sales s ON s.id = r.sale_id
             WHERE NOT EXISTS (SELECT 1 FROM refund_payments rp WHERE rp.refund_id = r.id)'
        )->fetchAll(PDO::FETCH_ASSOC);

        if (empty($pending)) {
            return;
        }

        $salePayments = $pdo->prepare('SELECT payment_type, amount FROM sale_payments WHERE sale_id = ?');
        $insert = $pdo->prepare('INSERT INTO refund_payments (refund_id, payment_type, amount) VALUES (?, ?, ?)');

        foreach ($pending as $row) {
            $split = ['naqd' => 0.0, 'karta' => 0.0, 'qarz' => 0.0];
            $salePayments->execute([$row['sale_id']]);
            $rows = $salePayments->fetchAll(PDO::FETCH_ASSOC);

            if (!empty($rows)) {
                foreach ($rows as $sp) {
                    if (isset($split[$sp['payment_type']])) {
                        $split[$sp['payment_type']] += (float) $sp['amount'];
                    }
                }
            } else {
                // Same legacy derivation as Sale::paymentSplit().
                $total = (float) $row['total'];
                $paid = min((float) $row['paid_amount'], $total);
                $split['qarz'] = max(0.0, round($total - $paid, 2));
                $split[$row['payment_type'] === 'karta' ? 'karta' : 'naqd'] = $paid;
            }

            $allocation = Refund::allocateByPayment((float) $row['total_amount'], $split['naqd'], $split['karta'], $split['qarz']);
            foreach ($allocation as $type => $amount) {
                if ($amount > 0) {
                    $insert->execute([$row['refund_id'], $type, $amount]);
                }
            }
        }

        $log(count($pending) . " ta eski qaytarish uchun refund_payments to'ldirildi.");
    }
}

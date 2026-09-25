<?php

declare(strict_types=1);

namespace App\Core;

use PDO;

/**
 * What the desktop app keeps in sync with the server, and how.
 *
 * Identity: every row in these tables carries a `uuid` (filled in by a
 * trigger on insert, see Migrator::addSyncIdentity()). The integer `id` is
 * only meaningful inside one database — a sale made offline on a shop's
 * computer gets whatever id is next there, which can already be taken on the
 * server — so across databases a row is its uuid, and a foreign key travels
 * as the uuid of the row it points to (FOREIGN_KEYS).
 *
 * Change capture: triggers (installTriggers()) append every insert, update
 * and delete to `sync_changes`, so none of the models need to know about
 * syncing. A counter column (COUNTERS — stock) is never sent as a value but
 * as the difference the change made ("-2", "+10"), so two computers selling
 * the same product offline both get subtracted, instead of the last one to
 * sync overwriting the other.
 *
 * Not listed on purpose: shops and settings (one row / a natural key per
 * shop, sent whole on every pull), employee_invites (online-only).
 */
final class SyncSchema
{
    /** In dependency order: a row's parents always come before it. */
    public const TABLES = [
        'users',
        'categories',
        'suppliers',
        'customers',
        'products',
        'product_variants',
        'purchases',
        'purchase_items',
        'sales',
        'sale_items',
        'sale_payments',
        'refunds',
        'refund_items',
        'refund_payments',
        'debt_transactions',
        'expenses',
        'attendance',
        'activity_log',
    ];

    /** table => [column => referenced table] (shop_id is handled separately). */
    public const FOREIGN_KEYS = [
        'users' => [],
        'categories' => [],
        'suppliers' => [],
        'customers' => [],
        'products' => ['category_id' => 'categories'],
        'product_variants' => ['product_id' => 'products'],
        'purchases' => ['supplier_id' => 'suppliers', 'created_by' => 'users'],
        'purchase_items' => ['purchase_id' => 'purchases', 'product_id' => 'products', 'variant_id' => 'product_variants'],
        'sales' => ['cashier_id' => 'users', 'customer_id' => 'customers'],
        'sale_items' => ['sale_id' => 'sales', 'product_id' => 'products', 'variant_id' => 'product_variants'],
        'sale_payments' => ['sale_id' => 'sales'],
        'refunds' => ['sale_id' => 'sales', 'refunded_by' => 'users'],
        'refund_items' => ['refund_id' => 'refunds', 'sale_item_id' => 'sale_items'],
        'refund_payments' => ['refund_id' => 'refunds'],
        'debt_transactions' => ['customer_id' => 'customers', 'sale_id' => 'sales', 'created_by' => 'users'],
        'expenses' => ['category_id' => 'categories', 'created_by' => 'users'],
        'attendance' => ['user_id' => 'users'],
        'activity_log' => ['user_id' => 'users'],
    ];

    /**
     * Tables without their own shop_id: the shop is their parent's
     * (table => [parent table, column pointing at it]).
     */
    public const PARENT_SHOP = [
        'purchase_items' => ['purchases', 'purchase_id'],
        'sale_items' => ['sales', 'sale_id'],
        'sale_payments' => ['sales', 'sale_id'],
        'refund_items' => ['refunds', 'refund_id'],
        'refund_payments' => ['refunds', 'refund_id'],
    ];

    /** Columns synced as differences, never as values. */
    public const COUNTERS = [
        'products' => ['stock_qty'],
        'product_variants' => ['stock_qty'],
    ];

    /** Local bookkeeping that is neither sent nor treated as a change. */
    public const LOCAL_COLUMNS = [
        'users' => ['failed_login_attempts', 'locked_until', 'onboarding_seen_at'],
    ];

    /**
     * Tables a computer may send changes to. Users are managed online only
     * (invites, permissions, passwords), so a computer only receives them.
     */
    public const PUSHABLE = [
        'categories', 'suppliers', 'customers', 'products', 'product_variants',
        'purchases', 'purchase_items', 'sales', 'sale_items', 'sale_payments',
        'refunds', 'refund_items', 'refund_payments', 'debt_transactions',
        'expenses', 'attendance', 'activity_log',
    ];

    /** The only rows the app ever deletes. */
    public const DELETABLE = ['expenses'];

    public static function hasShopColumn(string $table): bool
    {
        return !isset(self::PARENT_SHOP[$table]);
    }

    /**
     * SQL for the shop a row belongs to, given the row alias ("NEW", "OLD"
     * inside a trigger, or a table alias in a query).
     */
    public static function shopExpression(string $table, string $row): string
    {
        if (self::hasShopColumn($table)) {
            return "$row.shop_id";
        }

        [$parent, $column] = self::PARENT_SHOP[$table];

        return "(SELECT shop_id FROM $parent WHERE id = $row.$column)";
    }

    /**
     * The columns of $table that travel between databases as plain values:
     * everything but the local id, shop_id, counters and local bookkeeping.
     *
     * @return array<string, string> column => declared type
     */
    public static function dataColumns(PDO $pdo, string $table): array
    {
        $skip = array_merge(['id', 'shop_id'], self::COUNTERS[$table] ?? [], self::LOCAL_COLUMNS[$table] ?? []);
        $columns = [];

        foreach ($pdo->query("PRAGMA table_info($table)")->fetchAll(PDO::FETCH_ASSOC) as $column) {
            if (!in_array($column['name'], $skip, true)) {
                $columns[$column['name']] = strtoupper((string) $column['type']);
            }
        }

        return $columns;
    }

    /**
     * (Re)creates the change-capture triggers on every synced table. Dropped
     * and recreated on every migration, so a column added later is part of
     * the "did anything change" check without a separate step.
     *
     * The triggers stay silent while sync_context.suppress = 1 (the desktop
     * app applying rows it just received — they must not be sent back), and
     * stamp each entry with sync_context.source (the server applying a
     * computer's changes records which computer they came from).
     */
    public static function installTriggers(PDO $pdo): void
    {
        $guard = '(SELECT suppress FROM sync_context WHERE id = 1) = 0';
        $source = '(SELECT source FROM sync_context WHERE id = 1)';

        foreach (self::TABLES as $table) {
            foreach (['ai', 'au', 'ad'] as $suffix) {
                $pdo->exec("DROP TRIGGER IF EXISTS trg_{$table}_sync_$suffix");
            }

            $counters = self::COUNTERS[$table] ?? [];
            $shopNew = self::shopExpression($table, 'NEW');
            $shopOld = self::shopExpression($table, 'OLD');

            $insertCounters = '';
            $updateCounters = '';
            foreach ($counters as $column) {
                $insertCounters .= "
                    INSERT INTO sync_changes (shop_id, tbl, row_id, op, delta_col, delta, source)
                    SELECT $shopNew, '$table', NEW.id, 'delta', '$column', NEW.$column, $source
                    WHERE NEW.$column != 0;";
                $updateCounters .= "
                    INSERT INTO sync_changes (shop_id, tbl, row_id, op, delta_col, delta, source)
                    SELECT $shopNew, '$table', NEW.id, 'delta', '$column', NEW.$column - OLD.$column, $source
                    WHERE NEW.$column != OLD.$column;";
            }

            $changed = implode(' OR ', array_map(
                static fn (string $column): string => "NEW.$column IS NOT OLD.$column",
                array_keys(self::dataColumns($pdo, $table))
            ));

            $pdo->exec("
                CREATE TRIGGER trg_{$table}_sync_ai AFTER INSERT ON $table WHEN $guard
                BEGIN
                    INSERT INTO sync_changes (shop_id, tbl, row_id, op, source)
                    VALUES ($shopNew, '$table', NEW.id, 'upsert', $source);$insertCounters
                END");

            $pdo->exec("
                CREATE TRIGGER trg_{$table}_sync_au AFTER UPDATE ON $table WHEN $guard
                BEGIN
                    INSERT INTO sync_changes (shop_id, tbl, row_id, op, source)
                    SELECT $shopNew, '$table', NEW.id, 'upsert', $source
                    WHERE $changed;$updateCounters
                END");

            $pdo->exec("
                CREATE TRIGGER trg_{$table}_sync_ad AFTER DELETE ON $table WHEN $guard
                BEGIN
                    INSERT INTO sync_changes (shop_id, tbl, row_id, row_uuid, op, source)
                    VALUES ($shopOld, '$table', OLD.id, OLD.uuid, 'delete', $source);
                END");
        }
    }
}

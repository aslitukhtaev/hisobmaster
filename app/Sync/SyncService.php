<?php

declare(strict_types=1);

namespace App\Sync;

use App\Core\Database;
use App\Core\SyncSchema;
use App\Desktop\Desktop;
use PDO;
use RuntimeException;
use Throwable;

/**
 * Server side of the desktop app's sync: push (apply the changes a computer
 * recorded) and pull (everything that changed for the shop since the
 * computer last asked).
 *
 * Wire format of a row: its columns (SyncSchema::dataColumns() plus the
 * counters), with every foreign key replaced by the uuid of the row it
 * points to — ids mean nothing outside the database that assigned them.
 *
 * A pushed change:
 *   {"seq": 17, "tbl": "sales", "op": "upsert", "uuid": "…", "changed_at": "2026-09-25 10:00:00.123", "row": {…}}
 *   {"seq": 18, "tbl": "products", "op": "delta", "uuid": "…", "col": "stock_qty", "delta": -2, "changed_at": "…"}
 *   {"seq": 19, "tbl": "expenses", "op": "delete", "uuid": "…", "changed_at": "…"}
 */
final class SyncService
{
    public const PULL_LIMIT = 500;

    /**
     * How long journal entries are kept. Longer than a computer can work
     * offline (offline_days is at most 90), so a computer that syncs at all
     * finds every change it missed; one that comes back after even longer
     * downloads the shop again (see pull()).
     */
    public const JOURNAL_KEEP_DAYS = 120;

    private const PRUNED_UPTO_KEY = 'sync_journal_pruned_upto';

    private const PRUNED_AT_KEY = 'sync_journal_pruned_at';

    /** @var array<string, array<string, string>> */
    private static array $columnCache = [];

    /**
     * Applies a computer's changes in order, in one transaction, and returns
     * ['applied_seq' => highest seq now applied, 'rejected' => count].
     *
     * Each change is applied at most once: a batch resent after a dropped
     * connection skips everything up to the device's last_push_seq. A change
     * that can't be applied (unknown table, a reference to a row the server
     * doesn't have, a constraint) is rolled back on its own, recorded in
     * sync_rejects for inspection and skipped — one bad row must never stop
     * every later sale of that shop from reaching the server.
     */
    public static function push(array $device, array $changes): array
    {
        $shopId = (int) $device['shop_id'];
        $deviceId = (int) $device['id'];
        $rejected = 0;

        $pdo = Database::beginImmediate();
        try {
            $stmt = $pdo->prepare('SELECT last_push_seq FROM devices WHERE id = ?');
            $stmt->execute([$deviceId]);
            $applied = (int) $stmt->fetchColumn();

            $pdo->prepare('UPDATE sync_context SET source = ? WHERE id = 1')->execute([$deviceId]);

            foreach ($changes as $change) {
                $seq = is_array($change) ? (int) ($change['seq'] ?? 0) : 0;
                if ($seq <= $applied) {
                    continue;
                }

                $pdo->exec('SAVEPOINT sync_change');
                try {
                    self::applyChange($pdo, $shopId, $deviceId, $change);
                    $pdo->exec('RELEASE sync_change');
                } catch (Throwable $e) {
                    $pdo->exec('ROLLBACK TO sync_change');
                    $pdo->exec('RELEASE sync_change');
                    $pdo->prepare('INSERT INTO sync_rejects (device_id, seq, change_json, error) VALUES (?, ?, ?, ?)')
                        ->execute([$deviceId, $seq, json_encode($change, JSON_UNESCAPED_UNICODE), mb_substr($e->getMessage(), 0, 500)]);
                    $rejected++;
                }

                $applied = $seq;
            }

            $pdo->prepare('UPDATE sync_context SET source = NULL WHERE id = 1')->execute();
            $pdo->prepare("UPDATE devices SET last_push_seq = ?, last_sync_at = datetime('now') WHERE id = ?")
                ->execute([$applied, $deviceId]);

            Database::commit($pdo);
        } catch (Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }

        return ['applied_seq' => $applied, 'rejected' => $rejected];
    }

    private static function applyChange(PDO $pdo, int $shopId, int $deviceId, mixed $change): void
    {
        if (!is_array($change)) {
            throw new RuntimeException('bad_change');
        }

        $table = (string) ($change['tbl'] ?? '');
        $op = (string) ($change['op'] ?? '');
        $uuid = (string) ($change['uuid'] ?? '');

        if (!in_array($table, SyncSchema::PUSHABLE, true) || !DeviceService::isUuid($uuid)) {
            throw new RuntimeException("bad_change: $table/$op");
        }

        match ($op) {
            'upsert' => self::applyUpsert($pdo, $shopId, $deviceId, $table, $uuid, $change),
            'delta' => self::applyDelta($pdo, $shopId, $table, $uuid, $change),
            'delete' => self::applyDelete($pdo, $shopId, $table, $uuid),
            default => throw new RuntimeException("bad_op: $op"),
        };
    }

    private static function applyUpsert(PDO $pdo, int $shopId, int $deviceId, string $table, string $uuid, array $change): void
    {
        $row = $change['row'] ?? null;
        if (!is_array($row)) {
            throw new RuntimeException('bad_row');
        }

        $changedAt = self::changedAt($change['changed_at'] ?? null);
        $foreignKeys = SyncSchema::FOREIGN_KEYS[$table];
        $values = [];

        foreach (self::columns($pdo, $table) as $column => $type) {
            if ($column === 'uuid' || !array_key_exists($column, $row)) {
                continue;
            }

            $value = $row[$column];
            if (isset($foreignKeys[$column])) {
                $values[$column] = $value === null || $value === ''
                    ? null
                    : self::idForUuid($pdo, $foreignKeys[$column], (string) $value, $shopId);
            } else {
                $values[$column] = self::cast($value, $type);
            }
        }

        $existingId = self::idForUuid($pdo, $table, $uuid, $shopId, mustExist: false);

        if ($existingId === null) {
            if (SyncSchema::hasShopColumn($table)) {
                $values['shop_id'] = $shopId;
            }
            $values['uuid'] = $uuid;
            $columns = array_keys($values);
            $pdo->prepare(
                "INSERT INTO $table (" . implode(', ', $columns) . ') VALUES (' . implode(', ', array_fill(0, count($columns), '?')) . ')'
            )->execute(array_values($values));

            return;
        }

        // Last write wins: an edit made here (web, Telegram) or on another
        // computer after this one's is kept.
        $newer = $pdo->prepare(
            "SELECT 1 FROM sync_changes
             WHERE tbl = ? AND row_id = ? AND op = 'upsert' AND (source IS NULL OR source != ?) AND changed_at > ?
             LIMIT 1"
        );
        $newer->execute([$table, $existingId, $deviceId, $changedAt]);
        if ($newer->fetchColumn() !== false || $values === []) {
            return;
        }

        $assignments = implode(', ', array_map(static fn (string $column): string => "$column = ?", array_keys($values)));
        $pdo->prepare("UPDATE $table SET $assignments WHERE id = ?")
            ->execute([...array_values($values), $existingId]);
    }

    private static function applyDelta(PDO $pdo, int $shopId, string $table, string $uuid, array $change): void
    {
        $column = (string) ($change['col'] ?? '');
        $delta = $change['delta'] ?? null;

        if (!in_array($column, SyncSchema::COUNTERS[$table] ?? [], true)
            || !is_numeric($delta) || !is_finite((float) $delta) || abs((float) $delta) > QTY_MAX) {
            throw new RuntimeException('bad_delta');
        }

        $id = self::idForUuid($pdo, $table, $uuid, $shopId);
        $pdo->prepare("UPDATE $table SET $column = $column + ? WHERE id = ?")->execute([(float) $delta, $id]);
    }

    private static function applyDelete(PDO $pdo, int $shopId, string $table, string $uuid): void
    {
        if (!in_array($table, SyncSchema::DELETABLE, true)) {
            throw new RuntimeException('bad_delete');
        }

        // Already gone is fine — the result is the same.
        $id = self::idForUuid($pdo, $table, $uuid, $shopId, mustExist: false);
        if ($id !== null) {
            $pdo->prepare("DELETE FROM $table WHERE id = ?")->execute([$id]);
        }
    }

    /**
     * Everything that changed for the shop since the computer last asked.
     *
     * First sync ($cursor === null): a snapshot of every synced table, page
     * by page ($snap carries where the previous page stopped), ending with
     * the cursor to continue incrementally from. After that: the journal
     * entries after $cursor, each row sent once with its current values (in
     * the order it first changed, so a parent always arrives before the rows
     * that point to it). Rows are read at their current state, so sending
     * one again later is harmless.
     *
     * @return array{changes: list<array>, cursor: ?int, snap: ?array, more: bool}
     */
    public static function pull(array $device, ?int $cursor, ?array $snap, int $limit = self::PULL_LIMIT): array
    {
        $pdo = Database::connect();
        $shopId = (int) $device['shop_id'];

        if ($cursor === null) {
            return self::snapshotPage($pdo, $shopId, $snap, $limit);
        }

        // Entries this computer hasn't seen were already pruned: start over
        // with a snapshot (rows are sent at their current state, so the ones
        // it already has are simply overwritten with the same values).
        if ($cursor < self::prunedUpto($pdo)) {
            return self::snapshotPage($pdo, $shopId, null, $limit);
        }

        $stmt = $pdo->prepare('SELECT * FROM sync_changes WHERE shop_id = ? AND id > ? ORDER BY id LIMIT ?');
        $stmt->bindValue(1, $shopId, PDO::PARAM_INT);
        $stmt->bindValue(2, $cursor, PDO::PARAM_INT);
        $stmt->bindValue(3, $limit, PDO::PARAM_INT);
        $stmt->execute();
        $entries = $stmt->fetchAll();

        $rows = []; // "table:id" => [table, id, deleted uuid|null], first-change order
        foreach ($entries as $entry) {
            if (!in_array($entry['tbl'], SyncSchema::TABLES, true)) {
                continue;
            }
            $key = $entry['tbl'] . ':' . $entry['row_id'];
            $deletedUuid = $entry['op'] === 'delete' ? $entry['row_uuid'] : null;
            if (isset($rows[$key])) {
                $rows[$key][2] = $deletedUuid;
            } else {
                $rows[$key] = [$entry['tbl'], (int) $entry['row_id'], $deletedUuid];
            }
        }

        $changes = [];
        foreach ($rows as [$table, $id, $deletedUuid]) {
            if ($deletedUuid !== null) {
                $changes[] = ['tbl' => $table, 'op' => 'delete', 'uuid' => $deletedUuid];
                continue;
            }

            $row = self::fetchRow($pdo, $table, $id, $shopId);
            if ($row !== null) {
                $changes[] = ['tbl' => $table, 'op' => 'upsert', 'uuid' => $row['uuid'], 'row' => self::toWire($pdo, $table, $row)];
            }
        }

        $last = $entries === [] ? $cursor : (int) end($entries)['id'];

        return ['changes' => $changes, 'cursor' => $last, 'snap' => null, 'more' => count($entries) === $limit];
    }

    private static function snapshotPage(PDO $pdo, int $shopId, ?array $snap, int $limit): array
    {
        $tableIndex = (int) ($snap['t'] ?? 0);
        $afterId = (int) ($snap['after'] ?? 0);
        $upto = isset($snap['upto'])
            ? (int) $snap['upto']
            : (int) $pdo->query('SELECT COALESCE(MAX(id), 0) FROM sync_changes')->fetchColumn();

        $changes = [];
        while ($tableIndex < count(SyncSchema::TABLES) && count($changes) < $limit) {
            $table = SyncSchema::TABLES[$tableIndex];
            $want = $limit - count($changes);

            $stmt = $pdo->prepare(
                "SELECT t.* FROM $table t WHERE " . SyncSchema::shopExpression($table, 't') . ' = ? AND t.id > ? ORDER BY t.id LIMIT ?'
            );
            $stmt->bindValue(1, $shopId, PDO::PARAM_INT);
            $stmt->bindValue(2, $afterId, PDO::PARAM_INT);
            $stmt->bindValue(3, $want, PDO::PARAM_INT);
            $stmt->execute();
            $rows = $stmt->fetchAll();

            foreach ($rows as $row) {
                $changes[] = ['tbl' => $table, 'op' => 'upsert', 'uuid' => $row['uuid'], 'row' => self::toWire($pdo, $table, $row)];
            }

            if (count($rows) < $want) {
                $tableIndex++;
                $afterId = 0;
            } else {
                $afterId = (int) end($rows)['id'];
            }
        }

        if ($tableIndex >= count(SyncSchema::TABLES)) {
            // Anything written while the snapshot was being paged through is
            // after `upto` in the journal, so it comes with the next pull.
            return ['changes' => $changes, 'cursor' => $upto, 'snap' => null, 'more' => false];
        }

        return ['changes' => $changes, 'cursor' => null, 'snap' => ['t' => $tableIndex, 'after' => $afterId, 'upto' => $upto], 'more' => true];
    }

    /**
     * Deletes journal entries older than JOURNAL_KEEP_DAYS — at most once a
     * day, whoever calls it first (a computer's sync, a login on the site).
     * Every change of every shop is journaled, so without this the table
     * would only ever grow. Returns how many entries were deleted.
     */
    public static function pruneJournal(?int $now = null): int
    {
        // The desktop app keeps its own journal only until it's pushed
        // (DesktopSync::pruneJournal()).
        if (Desktop::enabled()) {
            return 0;
        }

        $now ??= time();
        $pdo = Database::connect();

        $stmt = $pdo->prepare('SELECT value FROM server_keys WHERE name = ?');
        $stmt->execute([self::PRUNED_AT_KEY]);
        if ((int) $stmt->fetchColumn() > $now - 86400) {
            return 0;
        }

        $pdo = Database::beginImmediate();
        try {
            $horizon = gmdate('Y-m-d H:i:s', $now - self::JOURNAL_KEEP_DAYS * 86400);
            $stmt = $pdo->prepare('SELECT MAX(id) FROM sync_changes WHERE changed_at < ?');
            $stmt->execute([$horizon]);
            $upto = (int) $stmt->fetchColumn();

            $deleted = 0;
            if ($upto > 0) {
                $delete = $pdo->prepare('DELETE FROM sync_changes WHERE id <= ?');
                $delete->execute([$upto]);
                $deleted = $delete->rowCount();
                self::setKey($pdo, self::PRUNED_UPTO_KEY, (string) max($upto, self::prunedUpto($pdo)));
            }
            self::setKey($pdo, self::PRUNED_AT_KEY, (string) $now);

            Database::commit($pdo);

            return $deleted;
        } catch (Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }
    }

    /** pruneJournal() for request handlers: housekeeping never fails a request. */
    public static function pruneJournalQuietly(): void
    {
        try {
            self::pruneJournal();
        } catch (Throwable $e) {
            log_exception($e);
        }
    }

    /** The newest journal entry already pruned (0 = none). */
    private static function prunedUpto(PDO $pdo): int
    {
        $stmt = $pdo->prepare('SELECT value FROM server_keys WHERE name = ?');
        $stmt->execute([self::PRUNED_UPTO_KEY]);

        return (int) $stmt->fetchColumn();
    }

    private static function setKey(PDO $pdo, string $name, string $value): void
    {
        $pdo->prepare('INSERT INTO server_keys (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value')
            ->execute([$name, $value]);
    }

    /** The shop row and settings the app needs; small, so sent whole every pull. */
    public static function shopState(array $shop): array
    {
        $stmt = Database::connect()->prepare('SELECT key, value FROM settings WHERE shop_id = ?');
        $stmt->execute([(int) $shop['id']]);

        return [
            'shop' => [
                'name' => $shop['name'],
                'owner_full_name' => $shop['owner_full_name'],
                'phone' => $shop['phone'],
                'address' => $shop['address'],
                'currency' => $shop['currency'],
                'receipt_printer_width' => (int) $shop['receipt_printer_width'],
                'offline_days' => (int) ($shop['offline_days'] ?? 7),
            ],
            'settings' => $stmt->fetchAll(PDO::FETCH_KEY_PAIR),
        ];
    }

    private static function fetchRow(PDO $pdo, string $table, int $id, int $shopId): ?array
    {
        $stmt = $pdo->prepare("SELECT t.* FROM $table t WHERE t.id = ? AND " . SyncSchema::shopExpression($table, 't') . ' = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    /** A row as sent to a computer: data columns + counters, foreign keys as uuids. */
    private static function toWire(PDO $pdo, string $table, array $row): array
    {
        $wire = [];
        $foreignKeys = SyncSchema::FOREIGN_KEYS[$table];

        foreach (array_merge(array_keys(self::columns($pdo, $table)), SyncSchema::COUNTERS[$table] ?? []) as $column) {
            $value = $row[$column] ?? null;
            if (isset($foreignKeys[$column]) && $value !== null) {
                $value = self::uuidForId($pdo, $foreignKeys[$column], (int) $value);
            }
            $wire[$column] = $value;
        }

        return $wire;
    }

    /**
     * The id of the row with this uuid in the shop — never a row of another
     * shop, whatever uuid a computer sends.
     */
    private static function idForUuid(PDO $pdo, string $table, string $uuid, int $shopId, bool $mustExist = true): ?int
    {
        $stmt = $pdo->prepare("SELECT t.id FROM $table t WHERE t.uuid = ? AND " . SyncSchema::shopExpression($table, 't') . ' = ?');
        $stmt->execute([$uuid, $shopId]);
        $id = $stmt->fetchColumn();

        if ($id === false) {
            if ($mustExist) {
                throw new RuntimeException("missing_reference: $table $uuid");
            }

            // A uuid that exists in another shop must not be treated as new.
            $other = $pdo->prepare("SELECT 1 FROM $table WHERE uuid = ?");
            $other->execute([$uuid]);
            if ($other->fetchColumn() !== false) {
                throw new RuntimeException("foreign_row: $table $uuid");
            }

            return null;
        }

        return (int) $id;
    }

    private static function uuidForId(PDO $pdo, string $table, int $id): ?string
    {
        static $cache = [];
        $key = "$table:$id";
        if (!array_key_exists($key, $cache)) {
            $stmt = $pdo->prepare("SELECT uuid FROM $table WHERE id = ?");
            $stmt->execute([$id]);
            $cache[$key] = $stmt->fetchColumn() ?: null;
        }

        return $cache[$key];
    }

    /** @return array<string, string> */
    private static function columns(PDO $pdo, string $table): array
    {
        return self::$columnCache[$table] ??= SyncSchema::dataColumns($pdo, $table);
    }

    private static function cast(mixed $value, string $type): mixed
    {
        if ($value === null) {
            return null;
        }
        if (is_array($value) || is_object($value)) {
            throw new RuntimeException('bad_value');
        }

        return match (true) {
            str_contains($type, 'INT') => is_numeric($value) ? (int) $value : throw new RuntimeException('bad_value'),
            str_contains($type, 'REAL') => is_numeric($value) && is_finite((float) $value) ? (float) $value : throw new RuntimeException('bad_value'),
            default => mb_substr((string) $value, 0, 10000),
        };
    }

    /** The computer's change time, or now when missing/malformed. */
    private static function changedAt(mixed $value): string
    {
        $value = (string) $value;

        return preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d{1,3})?$/', $value)
            ? $value
            : gmdate('Y-m-d H:i:s');
    }
}

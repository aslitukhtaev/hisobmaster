<?php

declare(strict_types=1);

namespace App\Desktop;

use App\Core\Database;
use App\Core\SyncSchema;
use PDO;
use Throwable;

/**
 * The desktop app's side of the sync (the server side is App\Sync\SyncService):
 *
 *   1. push — every local change the triggers journaled in sync_changes
 *      after `pushed_upto`, as the server expects them (foreign keys as
 *      uuids, counters as differences); the journal id is the change's seq,
 *      so a batch that reached the server but whose answer was lost is
 *      simply resent and skipped there.
 *   2. pull — everything that changed for the shop elsewhere (web, Telegram,
 *      the shop's other computers), applied with the triggers silenced so it
 *      isn't sent back.
 *
 * Runs in its own PHP process (bin/desktop-sync.php, started by the shell)
 * so a slow or dead connection never freezes the till, and under a file lock
 * so two runs never overlap.
 */
final class DesktopSync
{
    private const BATCH = 500;
    private const TIMEOUT_SECONDS = 20;

    /** @return array{ok: bool, error: ?string, pushed: int, pulled: int} */
    public static function run(): array
    {
        if (!Desktop::isActivated()) {
            return ['ok' => false, 'error' => 'not_activated', 'pushed' => 0, 'pulled' => 0];
        }

        $lock = fopen(self::lockPath(), 'c');
        if ($lock === false || !flock($lock, LOCK_EX | LOCK_NB)) {
            return ['ok' => false, 'error' => 'busy', 'pushed' => 0, 'pulled' => 0];
        }

        $pushed = 0;
        $pulled = 0;
        try {
            $pushed = self::push();
            $pulled = self::pull();

            Desktop::set('last_sync_at', gmdate('Y-m-d H:i:s'));
            Desktop::set('last_sync_error', null);
            Desktop::set('blocked_reason', null);
            self::pruneJournal();

            return ['ok' => true, 'error' => null, 'pushed' => $pushed, 'pulled' => $pulled];
        } catch (SyncHttpException $e) {
            if (in_array($e->errorCode, ['device_revoked', 'shop_blocked'], true)) {
                Desktop::set('blocked_reason', $e->errorCode);
            }
            Desktop::set('last_sync_error', $e->errorCode);

            return ['ok' => false, 'error' => $e->errorCode, 'pushed' => $pushed, 'pulled' => $pulled];
        } catch (Throwable $e) {
            log_exception($e);
            Desktop::set('last_sync_error', 'client_error');

            return ['ok' => false, 'error' => 'client_error', 'pushed' => $pushed, 'pulled' => $pulled];
        } finally {
            flock($lock, LOCK_UN);
            fclose($lock);
        }
    }

    /**
     * Signs this computer in: the owner's login and password go to the
     * server once, the device credentials and license come back, then the
     * whole shop is downloaded.
     */
    public static function activate(string $login, string $password, string $computerName): void
    {
        $pdo = Database::connect();
        $deviceUuid = Desktop::get('device_uuid');
        if ($deviceUuid === null || Desktop::get('device_secret') === null) {
            // A fresh identity for every activation attempt that didn't finish.
            $deviceUuid = bin2hex(random_bytes(16));
        }

        $response = self::request('/api/device/activate', [
            'login' => $login,
            'password' => $password,
            'device_uuid' => $deviceUuid,
            'fingerprint' => Desktop::fingerprint(),
            'name' => $computerName,
            'app_version' => Desktop::appVersion(),
        ], authenticated: false);

        Database::beginImmediate();
        try {
            $shopId = Desktop::localShopId();
            if ($shopId === 0) {
                $pdo->prepare("INSERT INTO shops (name, owner_full_name, phone, status) VALUES ('', '', '', 'active')")->execute();
                $shopId = (int) $pdo->lastInsertId();
            }

            Desktop::set('local_shop_id', (string) $shopId);
            Desktop::set('device_uuid', $deviceUuid);
            Desktop::set('device_code', (string) $response['device']['code']);
            Desktop::set('device_secret', (string) $response['secret']);
            Desktop::set('license', (string) $response['license']);
            Desktop::set('blocked_reason', null);
            Desktop::set('receipt_seq', '0');
            Desktop::set('pull_cursor', null);
            Desktop::set('pull_snap', null);
            // Everything in the journal so far (nothing, on a fresh install)
            // predates this identity and must not be sent under it.
            Desktop::set('pushed_upto', (string) (int) $pdo->query('SELECT COALESCE(MAX(id), 0) FROM sync_changes')->fetchColumn());
            self::applyShopState($pdo, $response, $shopId);

            Database::commit($pdo);
        } catch (Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }

        self::pull();
        Desktop::set('last_sync_at', gmdate('Y-m-d H:i:s'));
        Desktop::set('last_sync_error', null);
    }

    private static function push(): int
    {
        $pdo = Database::connect();
        $total = 0;

        while (true) {
            $pushedUpto = (int) Desktop::get('pushed_upto', '0');
            $stmt = $pdo->prepare('SELECT * FROM sync_changes WHERE id > ? ORDER BY id LIMIT ?');
            $stmt->bindValue(1, $pushedUpto, PDO::PARAM_INT);
            $stmt->bindValue(2, self::BATCH, PDO::PARAM_INT);
            $stmt->execute();
            $entries = $stmt->fetchAll();

            if ($entries === []) {
                return $total;
            }

            // A row is read at its current state when sent, so several
            // upserts of it in one batch (an insert, the uuid being filled
            // in, an edit) need to go only once — at its first position, so
            // it still arrives before rows that point to it, stamped with
            // its latest change time for last-write-wins.
            $latestUpsert = [];
            foreach ($entries as $entry) {
                if ($entry['op'] === 'upsert') {
                    $latestUpsert[$entry['tbl'] . ':' . $entry['row_id']] = $entry['changed_at'];
                }
            }

            $changes = [];
            $sent = [];
            foreach ($entries as $entry) {
                if ($entry['op'] === 'upsert') {
                    $key = $entry['tbl'] . ':' . $entry['row_id'];
                    if (isset($sent[$key])) {
                        continue;
                    }
                    $sent[$key] = true;
                    $entry['changed_at'] = $latestUpsert[$key];
                }

                $change = self::entryToChange($pdo, $entry);
                if ($change !== null) {
                    $changes[] = $change;
                }
            }

            if ($changes !== []) {
                // The server applies the whole batch or answers with an error
                // (thrown here), so on success everything read is done —
                // including entries there was nothing to send for.
                self::storeLicense(self::request('/api/sync/push', ['changes' => $changes]));
            }

            Desktop::set('pushed_upto', (string) (int) end($entries)['id']);
            $total += count($changes);

            if (count($entries) < self::BATCH) {
                return $total;
            }
        }
    }

    /** A journal entry as the server expects it, or null when there's nothing to send. */
    private static function entryToChange(PDO $pdo, array $entry): ?array
    {
        $table = (string) $entry['tbl'];
        if (!in_array($table, SyncSchema::PUSHABLE, true)) {
            return null;
        }

        $base = ['seq' => (int) $entry['id'], 'tbl' => $table, 'changed_at' => $entry['changed_at']];

        if ($entry['op'] === 'delete') {
            return $entry['row_uuid'] !== null ? $base + ['op' => 'delete', 'uuid' => $entry['row_uuid']] : null;
        }

        $stmt = $pdo->prepare("SELECT * FROM $table WHERE id = ?");
        $stmt->execute([(int) $entry['row_id']]);
        $row = $stmt->fetch();
        if ($row === false || $row['uuid'] === null) {
            // Gone since (its delete follows in the journal) — nothing to send.
            return null;
        }

        if ($entry['op'] === 'delta') {
            return $base + ['op' => 'delta', 'uuid' => $row['uuid'], 'col' => $entry['delta_col'], 'delta' => (float) $entry['delta']];
        }

        $wire = [];
        $foreignKeys = SyncSchema::FOREIGN_KEYS[$table];
        foreach (self::columns($pdo, $table) as $column) {
            $value = $row[$column];
            if (isset($foreignKeys[$column]) && $value !== null) {
                $ref = $pdo->prepare("SELECT uuid FROM {$foreignKeys[$column]} WHERE id = ?");
                $ref->execute([(int) $value]);
                $value = $ref->fetchColumn() ?: null;
            }
            $wire[$column] = $value;
        }

        return $base + ['op' => 'upsert', 'uuid' => $row['uuid'], 'row' => $wire];
    }

    private static function pull(): int
    {
        $pdo = Database::connect();
        $total = 0;

        do {
            $cursor = Desktop::get('pull_cursor');
            $snap = Desktop::get('pull_snap');
            $response = self::request('/api/sync/pull', [
                'cursor' => $cursor !== null ? (int) $cursor : null,
                'snap' => $snap !== null ? json_decode($snap, true) : null,
            ]);

            Database::beginImmediate();
            try {
                self::applyChanges($pdo, $response['changes'] ?? []);
                self::applyShopState($pdo, $response, Desktop::localShopId());
                Desktop::set('pull_cursor', $response['cursor'] !== null ? (string) $response['cursor'] : null);
                Desktop::set('pull_snap', $response['snap'] !== null ? json_encode($response['snap']) : null);
                self::storeLicense($response);
                Database::commit($pdo);
            } catch (Throwable $e) {
                Database::rollback($pdo);
                throw $e;
            }

            $total += count($response['changes'] ?? []);
        } while (!empty($response['more']));

        return $total;
    }

    /**
     * Applies rows received from the server, with the change triggers
     * silenced. A counter (stock) becomes the server's value plus whatever
     * this computer changed that the server hasn't seen yet; a row this
     * computer edited but hasn't sent yet keeps its local values (the server
     * decides between the two edits when it arrives).
     */
    public static function applyChanges(PDO $pdo, array $changes): void
    {
        $shopId = Desktop::localShopId();
        $pushedUpto = (int) Desktop::get('pushed_upto', '0');
        $pdo->exec('UPDATE sync_context SET suppress = 1 WHERE id = 1');

        try {
            $deferred = [];
            foreach ($changes as $change) {
                if (!self::applyOne($pdo, $change, $shopId, $pushedUpto)) {
                    $deferred[] = $change;
                }
            }
            // A row whose parent came later in the same batch.
            foreach ($deferred as $change) {
                if (!self::applyOne($pdo, $change, $shopId, $pushedUpto)) {
                    error_log('[KassirON sync] skipped ' . ($change['tbl'] ?? '?') . ' ' . ($change['uuid'] ?? '?') . ': missing parent');
                }
            }
        } finally {
            $pdo->exec('UPDATE sync_context SET suppress = 0 WHERE id = 1');
        }
    }

    /** False when a row it points to isn't here yet (retried once at the end of the batch). */
    private static function applyOne(PDO $pdo, array $change, int $shopId, int $pushedUpto): bool
    {
        $table = (string) ($change['tbl'] ?? '');
        $uuid = (string) ($change['uuid'] ?? '');
        if (!in_array($table, SyncSchema::TABLES, true) || $uuid === '') {
            return true;
        }

        if (($change['op'] ?? '') === 'delete') {
            $pdo->prepare("DELETE FROM $table WHERE uuid = ?")->execute([$uuid]);
            return true;
        }

        $row = is_array($change['row'] ?? null) ? $change['row'] : [];
        $foreignKeys = SyncSchema::FOREIGN_KEYS[$table];
        $values = [];

        foreach (self::columns($pdo, $table) as $column) {
            if (!array_key_exists($column, $row)) {
                continue;
            }
            $value = $row[$column];
            if (isset($foreignKeys[$column]) && $value !== null) {
                $ref = $pdo->prepare("SELECT id FROM {$foreignKeys[$column]} WHERE uuid = ?");
                $ref->execute([(string) $value]);
                $value = $ref->fetchColumn();
                if ($value === false) {
                    return false;
                }
            }
            $values[$column] = $value;
        }
        $values['uuid'] = $uuid;

        $existing = $pdo->prepare("SELECT id FROM $table WHERE uuid = ?");
        $existing->execute([$uuid]);
        $id = $existing->fetchColumn();

        $counters = [];
        foreach (SyncSchema::COUNTERS[$table] ?? [] as $column) {
            $pending = 0.0;
            if ($id !== false) {
                $stmt = $pdo->prepare("SELECT COALESCE(SUM(delta), 0) FROM sync_changes WHERE id > ? AND tbl = ? AND row_id = ? AND op = 'delta' AND delta_col = ?");
                $stmt->execute([$pushedUpto, $table, (int) $id, $column]);
                $pending = (float) $stmt->fetchColumn();
            }
            $counters[$column] = round((float) ($row[$column] ?? 0) + $pending, 6);
        }

        if ($id === false) {
            $insert = $values + $counters;
            if (SyncSchema::hasShopColumn($table)) {
                $insert['shop_id'] = $shopId;
            }
            $columns = array_keys($insert);
            $pdo->prepare("INSERT INTO $table (" . implode(', ', $columns) . ') VALUES (' . implode(', ', array_fill(0, count($columns), '?')) . ')')
                ->execute(array_values($insert));

            return true;
        }

        $localEdit = $pdo->prepare("SELECT 1 FROM sync_changes WHERE id > ? AND tbl = ? AND row_id = ? AND op = 'upsert' LIMIT 1");
        $localEdit->execute([$pushedUpto, $table, (int) $id]);
        $update = $localEdit->fetchColumn() !== false ? $counters : $values + $counters;

        if ($update !== []) {
            $assignments = implode(', ', array_map(static fn (string $column): string => "$column = ?", array_keys($update)));
            $pdo->prepare("UPDATE $table SET $assignments WHERE id = ?")->execute([...array_values($update), (int) $id]);
        }

        return true;
    }

    /** The shop row and settings, as the server sent them. */
    private static function applyShopState(PDO $pdo, array $response, int $shopId): void
    {
        $shop = $response['shop'] ?? null;
        if (!is_array($shop) || $shopId === 0) {
            return;
        }

        $pdo->prepare(
            'UPDATE shops SET name = ?, owner_full_name = ?, phone = ?, address = ?, currency = ?, receipt_printer_width = ?, offline_days = ? WHERE id = ?'
        )->execute([
            (string) $shop['name'],
            (string) $shop['owner_full_name'],
            (string) $shop['phone'],
            $shop['address'],
            (string) $shop['currency'],
            (int) $shop['receipt_printer_width'],
            (int) $shop['offline_days'],
            $shopId,
        ]);

        if (is_array($response['settings'] ?? null)) {
            $pdo->prepare('DELETE FROM settings WHERE shop_id = ?')->execute([$shopId]);
            $insert = $pdo->prepare('INSERT INTO settings (shop_id, key, value) VALUES (?, ?, ?)');
            foreach ($response['settings'] as $key => $value) {
                $insert->execute([$shopId, (string) $key, $value]);
            }
        }
    }

    private static function storeLicense(array $response): void
    {
        if (!empty($response['license'])) {
            Desktop::set('license', (string) $response['license']);
        }
        if (isset($response['server_time'])) {
            Desktop::set('server_time_offset', (string) ((int) $response['server_time'] - time()));
        }
    }

    /** Journal entries the server already has are no longer needed. */
    private static function pruneJournal(): void
    {
        Database::connect()->prepare('DELETE FROM sync_changes WHERE id <= ?')->execute([(int) Desktop::get('pushed_upto', '0')]);
    }

    /**
     * POSTs JSON to the server. Throws SyncHttpException with the server's
     * error code ("unauthorized", "device_revoked", ...) or "offline" when it
     * can't be reached at all.
     */
    private static function request(string $path, array $body, bool $authenticated = true): array
    {
        $headers = ['Content-Type: application/json', 'X-App-Version: ' . Desktop::appVersion()];
        if ($authenticated) {
            $headers[] = 'X-Device-Auth: Device ' . Desktop::get('device_uuid') . ':' . Desktop::get('device_secret');
        }

        $curl = curl_init(Desktop::server() . $path);
        curl_setopt_array($curl, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => json_encode($body, JSON_UNESCAPED_UNICODE),
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => 8,
            CURLOPT_TIMEOUT => self::TIMEOUT_SECONDS,
            CURLOPT_ENCODING => '',
        ]);
        $caBundle = (string) env('SSL_CERT_FILE', '');
        if ($caBundle !== '' && is_file($caBundle)) {
            curl_setopt($curl, CURLOPT_CAINFO, $caBundle);
        }

        $raw = curl_exec($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        curl_close($curl);

        if ($raw === false || $status === 0) {
            throw new SyncHttpException('offline');
        }

        $data = json_decode((string) $raw, true);
        if (!is_array($data)) {
            throw new SyncHttpException('server_error');
        }
        if ($status !== 200) {
            throw new SyncHttpException((string) ($data['error'] ?? 'server_error'));
        }

        return $data;
    }

    /** @return list<string> */
    private static function columns(PDO $pdo, string $table): array
    {
        static $cache = [];

        return $cache[$table] ??= array_keys(SyncSchema::dataColumns($pdo, $table));
    }

    private static function lockPath(): string
    {
        $configured = (string) env('DB_PATH', 'database/kassiron.db');
        $db = preg_match('#^(/|[A-Za-z]:[\\\\/])#', $configured) ? $configured : BASE_PATH . '/' . $configured;

        return $db . '.sync-lock';
    }
}

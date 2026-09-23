<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use PDO;

class ActivityLog
{
    /**
     * Every action string ActivityLog::record() is called with anywhere in
     * the app — kept as one list so the filter dropdown (and its
     * `activity_action_*` short labels) can't silently drift out of sync
     * with what actually gets logged.
     */
    public const KNOWN_ACTIONS = [
        'sale_created',
        'refund_created',
        'debt_payment_recorded',
        'expense_created',
        'expense_deleted',
        'employee_permissions_updated',
        'employee_status_changed',
        'products_imported',
        'product_created',
        'product_updated',
        'product_stock_changed',
        'product_status_changed',
    ];

    public static function record(?int $shopId, ?int $userId, string $action, array $meta = []): void
    {
        Database::connect()->prepare(
            'INSERT INTO activity_log (shop_id, user_id, action, meta_json) VALUES (?, ?, ?, ?)'
        )->execute([$shopId, $userId, $action, json_encode($meta, JSON_UNESCAPED_UNICODE)]);
    }

    /**
     * Every filter narrows the result (AND, never OR): a user + action +
     * date range + text search all have to match the same row. The text
     * search is the one exception that can't happen in SQL — it matches
     * against the human-readable rendered description (via t('activity_' .
     * action, meta)), not the raw action string or meta_json, so a search
     * for an amount or a customer name actually finds it. That means
     * fetching the SQL-filterable rows first (capped at $limit, newest
     * first) and then filtering those in PHP by rendered text — a shop's
     * activity log is small enough that this stays cheap.
     */
    public static function filtered(
        int $shopId,
        ?int $userId = null,
        ?string $action = null,
        ?string $fromUtc = null,
        ?string $toUtc = null,
        ?string $search = null,
        int $limit = 2000
    ): array {
        $sql = 'SELECT al.*, u.full_name AS user_name
                FROM activity_log al
                LEFT JOIN users u ON u.id = al.user_id
                WHERE al.shop_id = :shop_id';
        $params = [':shop_id' => $shopId];

        if ($userId !== null) {
            $sql .= ' AND al.user_id = :user_id';
            $params[':user_id'] = $userId;
        }

        if ($action !== null && $action !== '' && in_array($action, self::KNOWN_ACTIONS, true)) {
            $sql .= ' AND al.action = :action';
            $params[':action'] = $action;
        }

        if ($fromUtc !== null && $toUtc !== null) {
            $sql .= ' AND al.created_at >= :from_utc AND al.created_at < :to_utc';
            $params[':from_utc'] = $fromUtc;
            $params[':to_utc'] = $toUtc;
        }

        $sql .= ' ORDER BY al.id DESC LIMIT :limit';

        $stmt = Database::connect()->prepare($sql);
        foreach ($params as $key => $value) {
            $stmt->bindValue($key, $value, is_int($value) ? PDO::PARAM_INT : PDO::PARAM_STR);
        }
        $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
        $stmt->execute();
        $rows = $stmt->fetchAll();

        $search = trim((string) $search);
        if ($search === '') {
            return $rows;
        }

        $needle = mb_strtolower($search, 'UTF-8');

        return array_values(array_filter($rows, static function (array $row) use ($needle): bool {
            $meta = json_decode($row['meta_json'] ?? '[]', true) ?: [];
            $rendered = t('activity_' . $row['action'], $meta);
            $haystack = mb_strtolower($rendered . ' ' . ($row['user_name'] ?? ''), 'UTF-8');

            return mb_strpos($haystack, $needle) !== false;
        }));
    }
}

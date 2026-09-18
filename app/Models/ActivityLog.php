<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use PDO;

class ActivityLog
{
    public static function record(?int $shopId, ?int $userId, string $action, array $meta = []): void
    {
        Database::connect()->prepare(
            'INSERT INTO activity_log (shop_id, user_id, action, meta_json) VALUES (?, ?, ?, ?)'
        )->execute([$shopId, $userId, $action, json_encode($meta, JSON_UNESCAPED_UNICODE)]);
    }

    public static function recentByShop(int $shopId, int $limit = 100): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT al.*, u.full_name AS user_name
             FROM activity_log al
             LEFT JOIN users u ON u.id = al.user_id
             WHERE al.shop_id = :shop_id
             ORDER BY al.id DESC
             LIMIT :limit'
        );
        $stmt->bindValue(':shop_id', $shopId, PDO::PARAM_INT);
        $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
        $stmt->execute();

        return $stmt->fetchAll();
    }
}

<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use PDO;

class Shop
{
    public static function all(): array
    {
        return Database::connect()->query('SELECT * FROM shops ORDER BY created_at DESC')->fetchAll();
    }

    public static function find(int $id): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM shops WHERE id = ?');
        $stmt->execute([$id]);
        return $stmt->fetch() ?: null;
    }

    public static function create(array $data): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare(
            'INSERT INTO shops (name, owner_full_name, phone, address, currency, receipt_printer_width, status)
             VALUES (?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $data['name'],
            $data['owner_full_name'],
            $data['phone'],
            $data['address'] ?? null,
            $data['currency'] ?? "so'm",
            $data['receipt_printer_width'] ?? 80,
            'active',
        ]);

        return (int) $pdo->lastInsertId();
    }

    /** A self-signed-up shop: works for TRIAL_DAYS, then waits for payment. */
    public const TRIAL_DAYS = 7;

    public static function createTrial(string $name, string $ownerName, string $phone): int
    {
        $id = self::create(['name' => $name, 'owner_full_name' => $ownerName, 'phone' => $phone]);
        Database::connect()->prepare(
            "UPDATE shops SET plan = 'trial', access_until = datetime('now', '+" . self::TRIAL_DAYS . " days') WHERE id = ?"
        )->execute([$id]);

        return $id;
    }

    /**
     * Seconds of access left (negative once it ran out), or null when the
     * shop has no end date.
     */
    public static function secondsLeft(?array $shop): ?int
    {
        $until = $shop['access_until'] ?? null;
        if ($until === null || $until === '') {
            return null;
        }
        $end = strtotime($until . ' UTC');

        return $end === false ? null : $end - time();
    }

    public static function accessExpired(?array $shop): bool
    {
        $left = self::secondsLeft($shop);

        return $left !== null && $left <= 0;
    }

    /** Whole days left, rounded up (0 once expired); null = no end. */
    public static function daysLeft(?array $shop): ?int
    {
        $left = self::secondsLeft($shop);

        return $left === null ? null : max(0, (int) ceil($left / 86400));
    }

    /**
     * The super admin's "+N days" (from the later of now and the current
     * end) or "no end" ($days = null, also marks the shop paid).
     */
    public static function extendAccess(int $id, ?int $days, string $plan = 'paid'): void
    {
        $pdo = Database::connect();
        if ($days === null) {
            $pdo->prepare("UPDATE shops SET access_until = NULL, plan = 'paid' WHERE id = ?")->execute([$id]);
            return;
        }
        $pdo->prepare(
            "UPDATE shops SET plan = ?, access_until = datetime(MAX(COALESCE(access_until, datetime('now')), datetime('now')), ?) WHERE id = ?"
        )->execute([$plan, '+' . $days . ' days', $id]);
    }

    public static function setStatus(int $id, string $status): void
    {
        Database::connect()->prepare('UPDATE shops SET status = ? WHERE id = ?')->execute([$status, $id]);
    }

    public static function updateOfflineDays(int $id, int $days): void
    {
        Database::connect()->prepare('UPDATE shops SET offline_days = ? WHERE id = ?')->execute([$days, $id]);
    }

    public static function updateSettings(int $id, string $name, ?string $address, int $receiptPrinterWidth): void
    {
        Database::connect()
            ->prepare('UPDATE shops SET name = ?, address = ?, receipt_printer_width = ? WHERE id = ?')
            ->execute([$name, $address, $receiptPrinterWidth, $id]);
    }

    public static function counts(): array
    {
        $pdo = Database::connect();
        $total = (int) $pdo->query('SELECT COUNT(*) FROM shops')->fetchColumn();
        $active = (int) $pdo->query("SELECT COUNT(*) FROM shops WHERE status = 'active'")->fetchColumn();

        return ['total' => $total, 'active' => $active, 'blocked' => $total - $active];
    }

    public static function recent(int $limit = 5): array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM shops ORDER BY created_at DESC LIMIT :limit');
        $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
        $stmt->execute();

        return $stmt->fetchAll();
    }
}

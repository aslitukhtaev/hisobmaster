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

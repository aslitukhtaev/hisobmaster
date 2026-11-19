<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use RuntimeException;

/**
 * Product and expense categories. Names are compared the way a person reads
 * them: spaces at the ends and doubled inside don't count, nor does letter
 * case (Cyrillic too — SQLite's LOWER() only knows ASCII, so it's done here).
 */
class Category
{
    public const TYPES = ['product', 'expense'];

    public static function normalizeName(string $name): string
    {
        return trim((string) preg_replace('/\s+/u', ' ', $name));
    }

    private static function key(string $name): string
    {
        return mb_strtolower(self::normalizeName($name), 'UTF-8');
    }

    public static function allByShop(int $shopId, string $type = 'product'): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM categories WHERE shop_id = ? AND type = ? ORDER BY name COLLATE NOCASE'
        );
        $stmt->execute([$shopId, $type]);

        return $stmt->fetchAll();
    }

    /** With how many products (product type) or expenses (expense type) use each one. */
    public static function withCounts(int $shopId, string $type): array
    {
        $count = $type === 'expense'
            ? '(SELECT COUNT(*) FROM expenses x WHERE x.category_id = c.id)'
            : '(SELECT COUNT(*) FROM products p WHERE p.category_id = c.id)';
        $stmt = Database::connect()->prepare(
            "SELECT c.*, $count AS item_count FROM categories c WHERE c.shop_id = ? AND c.type = ? ORDER BY c.name COLLATE NOCASE"
        );
        $stmt->execute([$shopId, $type]);

        return $stmt->fetchAll();
    }

    /** Product categories that have at least one active product (the till's filter). */
    public static function forTill(int $shopId): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT c.id, c.name FROM categories c
             WHERE c.shop_id = ? AND c.type = 'product'
               AND EXISTS (SELECT 1 FROM products p WHERE p.category_id = c.id AND p.status = 'active')
             ORDER BY c.name COLLATE NOCASE"
        );
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM categories WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);
        $row = $stmt->fetch();

        return $row ?: null;
    }

    /** The category with this name (as a person reads it), or null. */
    public static function findByName(int $shopId, string $name, string $type = 'product'): ?array
    {
        $key = self::key($name);
        foreach (self::allByShop($shopId, $type) as $category) {
            if (self::key($category['name']) === $key) {
                return $category;
            }
        }

        return null;
    }

    public static function findOrCreate(int $shopId, string $name, string $type = 'product'): int
    {
        $existing = self::findByName($shopId, $name, $type);
        if ($existing !== null) {
            return (int) $existing['id'];
        }

        return self::create($shopId, $name, $type);
    }

    public static function create(int $shopId, string $name, string $type = 'product'): int
    {
        $pdo = Database::connect();
        $pdo->prepare('INSERT INTO categories (shop_id, name, type) VALUES (?, ?, ?)')
            ->execute([$shopId, self::normalizeName($name), $type]);

        return (int) $pdo->lastInsertId();
    }

    public static function rename(int $id, int $shopId, string $name): void
    {
        Database::connect()->prepare('UPDATE categories SET name = ? WHERE id = ? AND shop_id = ?')
            ->execute([self::normalizeName($name), $id, $shopId]);
    }

    /**
     * Moves every product and expense of $fromId to $intoId and deletes
     * $fromId. Returns how many products/expenses moved.
     */
    public static function merge(int $fromId, int $intoId, int $shopId): int
    {
        $from = self::find($fromId, $shopId);
        $into = self::find($intoId, $shopId);
        if ($from === null || $into === null || $fromId === $intoId || $from['type'] !== $into['type']) {
            throw new RuntimeException('bad_merge');
        }

        $pdo = Database::beginImmediate();
        try {
            $moved = 0;
            foreach (['products', 'expenses'] as $table) {
                $stmt = $pdo->prepare("UPDATE $table SET category_id = ? WHERE category_id = ? AND shop_id = ?");
                $stmt->execute([$intoId, $fromId, $shopId]);
                $moved += $stmt->rowCount();
            }
            $pdo->prepare('DELETE FROM categories WHERE id = ? AND shop_id = ?')->execute([$fromId, $shopId]);
            Database::commit($pdo);
        } catch (\Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }

        return $moved;
    }

    /** Deletes the category; its products and expenses stay, without a category. */
    public static function delete(int $id, int $shopId): void
    {
        $pdo = Database::beginImmediate();
        try {
            self::detach($pdo, $id);
            $pdo->prepare('DELETE FROM categories WHERE id = ? AND shop_id = ?')->execute([$id, $shopId]);
            Database::commit($pdo);
        } catch (\Throwable $e) {
            Database::rollback($pdo);
            throw $e;
        }
    }

    /**
     * Clears the category from its products and expenses as ordinary updates
     * (so they reach the other devices) before the row itself goes. Also used
     * when a device pushes a category delete.
     */
    public static function detach(\PDO $pdo, int $id): void
    {
        foreach (['products', 'expenses'] as $table) {
            $pdo->prepare("UPDATE $table SET category_id = NULL WHERE category_id = ?")->execute([$id]);
        }
    }
}

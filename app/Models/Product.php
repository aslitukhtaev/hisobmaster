<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;

class Product
{
    /**
     * Correlated subqueries giving, per product row "p", how many active
     * variants it has and their combined stock. A product with at least one
     * active variant is sold only through its variants (see Sale::create()),
     * so everywhere stock is shown or compared its "effective" stock is the
     * variants' total, not its own (unused) stock_qty.
     */
    private const VARIANT_AGGREGATES = "
        (SELECT COUNT(*) FROM product_variants pv WHERE pv.product_id = p.id AND pv.status = 'active') AS variant_count,
        (SELECT COALESCE(SUM(pv.stock_qty), 0) FROM product_variants pv WHERE pv.product_id = p.id AND pv.status = 'active') AS variant_stock";

    private const EFFECTIVE_STOCK = "
        CASE WHEN EXISTS (SELECT 1 FROM product_variants pv WHERE pv.product_id = p.id AND pv.status = 'active')
             THEN (SELECT COALESCE(SUM(pv.stock_qty), 0) FROM product_variants pv WHERE pv.product_id = p.id AND pv.status = 'active')
             ELSE p.stock_qty END";

    public static function allByShop(int $shopId, ?string $search = null): array
    {
        $sql = 'SELECT p.*, c.name AS category_name, ' . self::VARIANT_AGGREGATES . ', ' . self::EFFECTIVE_STOCK . ' AS effective_stock
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                WHERE p.shop_id = ?';
        $params = [$shopId];

        if ($search !== null && $search !== '') {
            // A variant's own barcode/label finds its product too.
            $sql .= ' AND (p.name LIKE ? OR p.barcode LIKE ? OR EXISTS (
                          SELECT 1 FROM product_variants pv
                          WHERE pv.product_id = p.id AND (pv.barcode LIKE ? OR pv.variant_label LIKE ?)))';
            $like = '%' . $search . '%';
            array_push($params, $like, $like, $like, $like);
        }

        $sql .= ' ORDER BY p.name';

        $stmt = Database::connect()->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM products WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function create(array $data): int
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare(
            'INSERT INTO products (shop_id, category_id, name, unit, cost_price, sell_price, stock_qty, barcode, status, low_stock_threshold, pack_size)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $data['shop_id'],
            $data['category_id'] ?? null,
            $data['name'],
            $data['unit'],
            $data['cost_price'],
            $data['sell_price'],
            $data['stock_qty'],
            $data['barcode'] ?? null,
            'active',
            $data['low_stock_threshold'] ?? null,
            $data['pack_size'] ?? null,
        ]);

        return (int) $pdo->lastInsertId();
    }

    public static function update(int $id, int $shopId, array $data): void
    {
        Database::connect()->prepare(
            'UPDATE products
             SET category_id = ?, name = ?, unit = ?, cost_price = ?, sell_price = ?, stock_qty = ?, barcode = ?,
                 low_stock_threshold = ?, pack_size = ?, updated_at = datetime(\'now\')
             WHERE id = ? AND shop_id = ?'
        )->execute([
            $data['category_id'] ?? null,
            $data['name'],
            $data['unit'],
            $data['cost_price'],
            $data['sell_price'],
            $data['stock_qty'],
            $data['barcode'] ?? null,
            $data['low_stock_threshold'] ?? null,
            $data['pack_size'] ?? null,
            $id,
            $shopId,
        ]);
    }

    /**
     * Sets a product's cost_price directly (used by the purchase-recording
     * flow when the shop owner explicitly opts to update the cost basis to
     * the new purchase price). Not a stock-affecting write, so a plain SET is
     * fine here — no read-then-write hazard, unlike stock_qty.
     */
    public static function updateCostPrice(int $id, int $shopId, float $costPrice): void
    {
        Database::connect()
            ->prepare("UPDATE products SET cost_price = ?, updated_at = datetime('now') WHERE id = ? AND shop_id = ?")
            ->execute([$costPrice, $id, $shopId]);
    }

    public static function findByBarcode(string $barcode, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM products WHERE shop_id = ? AND barcode = ? LIMIT 1');
        $stmt->execute([$shopId, $barcode]);

        return $stmt->fetch() ?: null;
    }

    /**
     * The variant (joined with its product's name) that owns $barcode, if any.
     */
    public static function findVariantByBarcode(string $barcode, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare(
            'SELECT pv.*, p.name AS product_name FROM product_variants pv
             JOIN products p ON p.id = pv.product_id
             WHERE pv.shop_id = ? AND pv.barcode = ? LIMIT 1'
        );
        $stmt->execute([$shopId, $barcode]);

        return $stmt->fetch() ?: null;
    }

    /**
     * Whether $barcode is already used anywhere in the shop — by another
     * product or by any product's variant (active or not, so reactivating
     * one can never create a clash). A barcode has to identify exactly one
     * sellable thing, or a scan can't add it to the cart unambiguously.
     */
    public static function barcodeTaken(int $shopId, string $barcode, ?int $exceptProductId = null, ?int $exceptVariantId = null): bool
    {
        $stmt = Database::connect()->prepare(
            'SELECT EXISTS (SELECT 1 FROM products WHERE shop_id = ? AND barcode = ? AND id != ?)
                 OR EXISTS (SELECT 1 FROM product_variants WHERE shop_id = ? AND barcode = ? AND id != ?)'
        );
        $stmt->execute([$shopId, $barcode, $exceptProductId ?? 0, $shopId, $barcode, $exceptVariantId ?? 0]);

        return (bool) $stmt->fetchColumn();
    }

    /**
     * Active products at or below their effective low-stock threshold: their
     * own low_stock_threshold when set, otherwise the shop-wide default (see
     * Settings::lowStockThresholdDefault()) when one is configured. A product
     * with neither never shows up here. Stock is the effective stock — the
     * variants' total for a product sold through variants.
     */
    public static function lowStock(int $shopId, ?float $shopDefaultThreshold): array
    {
        $sql = 'SELECT * FROM (
                    SELECT p.*, c.name AS category_name, ' . self::VARIANT_AGGREGATES . ', ' . self::EFFECTIVE_STOCK . ' AS effective_stock
                    FROM products p
                    LEFT JOIN categories c ON c.id = p.category_id
                    WHERE p.shop_id = ? AND p.status = ?
                ) t
                WHERE (t.low_stock_threshold IS NOT NULL AND t.effective_stock <= CAST(t.low_stock_threshold AS REAL))';
        $params = [$shopId, 'active'];

        if ($shopDefaultThreshold !== null) {
            // CAST: effective_stock is a CASE expression with no column
            // affinity, so a bound (text) parameter would otherwise be
            // compared as text and every number would count as "below" it.
            $sql .= ' OR (t.low_stock_threshold IS NULL AND t.effective_stock <= CAST(? AS REAL))';
            $params[] = $shopDefaultThreshold;
        }

        $sql .= ' ORDER BY t.effective_stock ASC, t.name';

        $stmt = Database::connect()->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchAll();
    }

    public static function lowStockCounts(int $shopId, ?float $shopDefaultThreshold): array
    {
        $rows = self::lowStock($shopId, $shopDefaultThreshold);
        $out = 0;
        foreach ($rows as $row) {
            if ((float) $row['effective_stock'] <= 0) {
                $out++;
            }
        }

        return ['low' => count($rows), 'out' => $out];
    }

    public static function setStatus(int $id, int $shopId, string $status): void
    {
        Database::connect()
            ->prepare('UPDATE products SET status = ? WHERE id = ? AND shop_id = ?')
            ->execute([$status, $id, $shopId]);
    }

    public static function counts(int $shopId): array
    {
        $pdo = Database::connect();

        $total = (int) self::scalar($pdo, 'SELECT COUNT(*) FROM products WHERE shop_id = ?', [$shopId]);
        $active = (int) self::scalar(
            $pdo,
            "SELECT COUNT(*) FROM products WHERE shop_id = ? AND status = 'active'",
            [$shopId]
        );
        // Products' own stock at their cost, plus every variant's stock at
        // the variant's cost (falling back to its product's, the same rule
        // Sale::create() snapshots) — variant stock used to be left out.
        $stockValue = (float) self::scalar(
            $pdo,
            'SELECT COALESCE((SELECT SUM(cost_price * stock_qty) FROM products WHERE shop_id = ?), 0)
                  + COALESCE((SELECT SUM(COALESCE(pv.cost_price, p.cost_price) * pv.stock_qty)
                              FROM product_variants pv JOIN products p ON p.id = pv.product_id
                              WHERE pv.shop_id = ?), 0)',
            [$shopId, $shopId]
        );

        return ['total' => $total, 'active' => $active, 'stock_value' => $stockValue];
    }

    private static function scalar(\PDO $pdo, string $sql, array $params): mixed
    {
        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchColumn();
    }
}

<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Database;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\Backup;
use App\Models\Product;
use App\Models\ProductVariant;
use PDO;
use Throwable;

/**
 * "O'chgan optom narxlarni tiklash": optom prices that are empty now but were
 * set in one of the server's backups (database/backups). Until sync sent only
 * the columns a computer or phone changed, editing a product there could
 * write its stale empty optom price over the website's. The owner sees what
 * each backup still has and puts back the ones they pick.
 */
class WholesaleRestoreController
{
    public function show(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $backups = Backup::list();

        View::render('products/restore-wholesale', [
            'rows' => self::lost($shopId, $backups),
            'backupCount' => count($backups),
            'oldestBackup' => $backups !== [] ? end($backups)['created_at'] : null,
            'newestBackup' => $backups[0]['created_at'] ?? null,
        ]);
    }

    public function restore(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $picked = $request->input('pick', []);
        $picked = is_array($picked) ? array_flip(array_map('strval', $picked)) : [];

        // The prices are read again from the backups, never taken from the form.
        $restored = 0;
        foreach (self::lost($shopId, Backup::list()) as $row) {
            if (!isset($picked[$row['key']])) {
                continue;
            }
            if ($row['type'] === 'variant') {
                ProductVariant::setWholesalePrice($row['id'], $shopId, $row['wholesale_price']);
            } else {
                Product::setWholesalePrice($row['id'], $shopId, $row['wholesale_price']);
            }
            $restored++;
        }

        if ($restored > 0) {
            ActivityLog::record($shopId, Auth::id(), 'wholesale_restored', ['count' => (string) $restored]);
            flash('success', t('wholesale_restored', ['count' => $restored]));
        } else {
            flash('error', t('wholesale_restore_none_picked'));
        }
        redirect('/products/restore-wholesale');
    }

    /**
     * Products and variants whose optom price is empty now but set in a
     * backup — the newest backup that has one.
     *
     * @param list<array{filename: string, created_at: int}> $backups newest first
     * @return list<array{key: string, type: string, id: int, name: string, sell_price: ?float, wholesale_price: float, backup_at: int}>
     */
    private static function lost(int $shopId, array $backups): array
    {
        $pdo = Database::connect();
        $products = $pdo->prepare(
            "SELECT id, name, sell_price FROM products WHERE shop_id = ? AND wholesale_price IS NULL AND status = 'active'"
        );
        $products->execute([$shopId]);
        $wanted = [];
        foreach ($products->fetchAll(PDO::FETCH_ASSOC) as $p) {
            $wanted['p:' . $p['id']] = ['type' => 'product', 'id' => (int) $p['id'], 'name' => (string) $p['name'], 'sell_price' => $p['sell_price'] !== null ? (float) $p['sell_price'] : null];
        }
        $variants = $pdo->prepare(
            "SELECT v.id, p.name || ' — ' || v.variant_label AS name, COALESCE(v.sell_price, p.sell_price) AS sell_price
             FROM product_variants v JOIN products p ON p.id = v.product_id
             WHERE v.shop_id = ? AND v.wholesale_price IS NULL AND v.status = 'active' AND p.status = 'active'"
        );
        $variants->execute([$shopId]);
        foreach ($variants->fetchAll(PDO::FETCH_ASSOC) as $v) {
            $wanted['v:' . $v['id']] = ['type' => 'variant', 'id' => (int) $v['id'], 'name' => (string) $v['name'], 'sell_price' => $v['sell_price'] !== null ? (float) $v['sell_price'] : null];
        }

        $found = [];
        foreach ($backups as $backup) {
            if (count($found) === count($wanted)) {
                break;
            }
            $path = Backup::path($backup['filename']);
            if ($path === null) {
                continue;
            }
            try {
                $old = new PDO('sqlite:' . $path, null, null, [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::SQLITE_ATTR_OPEN_FLAGS => PDO::SQLITE_OPEN_READONLY,
                ]);
                foreach (['products' => 'p', 'product_variants' => 'v'] as $table => $prefix) {
                    $columns = array_column($old->query("PRAGMA table_info($table)")->fetchAll(PDO::FETCH_ASSOC), 'name');
                    if (!in_array('wholesale_price', $columns, true)) {
                        continue; // a backup from before optom prices
                    }
                    $stmt = $old->prepare("SELECT id, wholesale_price FROM $table WHERE shop_id = ? AND wholesale_price IS NOT NULL");
                    $stmt->execute([$shopId]);
                    foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $row) {
                        $key = $prefix . ':' . $row['id'];
                        if (isset($wanted[$key]) && !isset($found[$key])) {
                            $found[$key] = $wanted[$key] + [
                                'key' => $key,
                                'wholesale_price' => (float) $row['wholesale_price'],
                                'backup_at' => (int) $backup['created_at'],
                            ];
                        }
                    }
                }
            } catch (Throwable $e) {
                log_exception($e); // an unreadable backup: the others still count
            } finally {
                $old = null;
            }
        }

        $rows = array_values($found);
        usort($rows, static fn (array $a, array $b): int => strcasecmp($a['name'], $b['name']));

        return $rows;
    }
}

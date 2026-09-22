<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Product;
use App\Models\Purchase;
use App\Models\Supplier;
use PDOException;
use RuntimeException;

class PurchaseController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        View::render('purchases/index', [
            'purchases' => Purchase::recentByShop($shopId),
        ]);
    }

    public function createForm(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        $products = Product::allByShop($shopId);
        $productsJson = array_map(static function (array $p): array {
            return [
                'id' => (int) $p['id'],
                'name' => $p['name'],
                'unit' => $p['unit'],
                'cost_price' => (float) $p['cost_price'],
                'pack_size' => $p['pack_size'] !== null ? (int) $p['pack_size'] : null,
            ];
        }, $products);

        View::render('purchases/create', [
            'suppliers' => Supplier::allByShop($shopId),
            'hasProducts' => count($products) > 0,
            'productsJson' => json_encode($productsJson, JSON_UNESCAPED_UNICODE),
        ]);
    }

    public function store(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $userId = (int) Auth::id();

        $supplierIdRaw = trim((string) $request->input('supplier_id', ''));
        $supplierId = $supplierIdRaw !== '' ? (int) $supplierIdRaw : null;
        $note = trim((string) $request->input('note', ''));
        $updateCostPrice = $request->input('update_cost_price') === '1';

        $rowsRaw = (string) $request->input('items', '[]');
        $rows = json_decode($rowsRaw, true);

        if (!is_array($rows) || empty($rows)) {
            flash('error', t('purchase_no_items'));
            redirect('/purchases/create');
        }

        $items = [];
        foreach ($rows as $row) {
            if (!isset($row['product_id'], $row['qty'], $row['unit_cost'])) {
                continue;
            }
            $items[] = [
                'product_id' => (int) $row['product_id'],
                'qty' => (float) $row['qty'],
                'unit_cost' => (float) $row['unit_cost'],
            ];
        }

        if (empty($items)) {
            flash('error', t('purchase_no_items'));
            redirect('/purchases/create');
        }

        try {
            Purchase::create($shopId, $userId, $supplierId, $items, $note !== '' ? $note : null, $updateCostPrice);
        } catch (PDOException $e) {
            log_exception($e);
            flash('error', t('unexpected_error'));
            redirect('/purchases/create');
        } catch (RuntimeException $e) {
            flash('error', t($e->getMessage()));
            redirect('/purchases/create');
        }

        flash('success', t('purchase_recorded'));
        redirect('/purchases');
    }

    public function show(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $purchase = Purchase::find((int) $id, $shopId);

        if (!$purchase) {
            flash('error', t('purchase_not_found'));
            redirect('/purchases');
        }

        View::render('purchases/show', [
            'purchase' => $purchase,
            'items' => Purchase::items((int) $id),
        ]);
    }
}

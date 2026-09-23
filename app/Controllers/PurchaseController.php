<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Product;
use App\Models\ProductVariant;
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
        $variantsByProduct = [];
        foreach (ProductVariant::activeAllByShop($shopId) as $variant) {
            $variantsByProduct[(int) $variant['product_id']][] = $variant;
        }

        // A product with active variants keeps its stock per variant (see
        // Sale::create()), so a purchase of it has to name the variant —
        // purchase.js offers those products only through their variants.
        $productsJson = array_map(static function (array $p) use ($variantsByProduct): array {
            return [
                'id' => (int) $p['id'],
                'name' => $p['name'],
                'unit' => $p['unit'],
                'cost_price' => (float) $p['cost_price'],
                'pack_size' => $p['pack_size'] !== null ? (int) $p['pack_size'] : null,
                'fractional' => unit_allows_fraction($p['unit']),
                'variants' => array_map(static fn (array $v): array => [
                    'id' => (int) $v['id'],
                    'label' => $v['variant_label'],
                    'cost_price' => $v['cost_price'] !== null ? (float) $v['cost_price'] : (float) $p['cost_price'],
                ], $variantsByProduct[(int) $p['id']] ?? []),
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
            if (!is_numeric($row['qty']) || !is_numeric($row['unit_cost']) || !valid_money($row['unit_cost']) || (float) $row['qty'] > QTY_MAX) {
                flash('error', t('amount_too_large'));
                redirect('/purchases/create');
            }
            $items[] = [
                'product_id' => (int) $row['product_id'],
                'variant_id' => !empty($row['variant_id']) ? (int) $row['variant_id'] : null,
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
            [$errorKey, $errorProduct] = array_pad(explode(':', $e->getMessage(), 2), 2, null);
            flash('error', $errorProduct !== null ? t($errorKey, ['product' => $errorProduct]) : t($errorKey));
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
            abort_404();
        }

        View::render('purchases/show', [
            'purchase' => $purchase,
            'items' => Purchase::items((int) $id),
        ]);
    }
}

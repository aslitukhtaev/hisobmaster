<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\Customer;
use App\Models\Product;
use App\Models\Sale;
use App\Models\Shop;
use RuntimeException;

class SaleController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        View::render('sales/index', [
            'sales' => Sale::recentByShop($shopId),
            'today' => Sale::todaysSummary($shopId),
        ]);
    }

    public function newForm(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        $activeProducts = array_values(array_filter(
            Product::allByShop($shopId),
            static fn (array $p) => $p['status'] === 'active' && (float) $p['stock_qty'] > 0
        ));

        $productsJson = array_map(static function (array $p): array {
            return [
                'id' => (int) $p['id'],
                'name' => $p['name'],
                'unit' => $p['unit'],
                'price' => (float) $p['sell_price'],
                'stock' => (float) $p['stock_qty'],
                'barcode' => $p['barcode'],
            ];
        }, $activeProducts);

        $customersJson = array_map(static function (array $c): array {
            return ['id' => (int) $c['id'], 'name' => $c['full_name'], 'phone' => $c['phone']];
        }, Customer::allByShop($shopId));

        View::render('sales/new', [
            'productsJson' => json_encode($productsJson, JSON_UNESCAPED_UNICODE),
            'customersJson' => json_encode($customersJson, JSON_UNESCAPED_UNICODE),
            'hasProducts' => count($activeProducts) > 0,
        ]);
    }

    public function store(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $cashierId = (int) Auth::id();

        $cartRaw = (string) $request->input('cart', '[]');
        $cart = json_decode($cartRaw, true);

        $paymentType = (string) $request->input('payment_type', 'naqd');
        if (!in_array($paymentType, ['naqd', 'karta', 'qarz'], true)) {
            $paymentType = 'naqd';
        }

        $discount = (float) $request->input('discount', 0);
        $paidAmount = (float) $request->input('paid_amount', 0);
        $customerIdInput = trim((string) $request->input('customer_id', ''));
        $customerName = trim((string) $request->input('customer_name', ''));
        $customerPhone = trim((string) $request->input('customer_phone', ''));

        // Kept across every redirect-back-with-error below so the cashier never
        // has to re-build the cart from scratch after a validation failure.
        $old = [
            'cart' => $cartRaw,
            'payment_type' => $paymentType,
            'discount' => (string) $discount,
            'paid_amount' => (string) $paidAmount,
            'customer_id' => $customerIdInput,
            'customer_name' => $customerName,
            'customer_phone' => $customerPhone,
        ];

        if (!is_array($cart) || empty($cart)) {
            flash('error', t('empty_cart'));
            keep_old($old);
            redirect('/sales/new');
        }

        $items = [];
        foreach ($cart as $row) {
            if (!isset($row['product_id'], $row['qty'])) {
                continue;
            }
            $items[] = ['product_id' => (int) $row['product_id'], 'qty' => (float) $row['qty']];
        }

        $customerId = null;
        if ($paymentType === 'qarz') {
            if ($customerIdInput !== '') {
                $existing = Customer::find((int) $customerIdInput, $shopId);
                $customerId = $existing ? (int) $existing['id'] : null;
            }

            if ($customerId === null) {
                if ($customerName === '') {
                    flash('error', t('customer_required_for_debt'));
                    keep_old($old);
                    redirect('/sales/new');
                }
                $customerId = Customer::findOrCreate($shopId, $customerName, $customerPhone !== '' ? $customerPhone : null);
            }
        }

        try {
            $saleId = Sale::create($shopId, $cashierId, $items, $paymentType, $discount, $paidAmount, $customerId);
        } catch (RuntimeException $e) {
            // Sale::create() throws either a plain translation key (e.g.
            // 'insufficient_stock') or 'insufficient_stock:Product name' when it
            // needs to name the product that ran out — see Sale::create().
            [$errorKey, $errorProduct] = array_pad(explode(':', $e->getMessage(), 2), 2, null);
            flash('error', $errorProduct !== null ? t($errorKey, ['product' => $errorProduct]) : t($errorKey));
            keep_old($old);
            redirect('/sales/new');
        }

        $sale = Sale::find($saleId, $shopId);
        ActivityLog::record($shopId, $cashierId, 'sale_created', [
            'sale_id' => $saleId,
            'total' => money((float) ($sale['total'] ?? 0)),
        ]);

        flash('success', t('sale_completed'));
        redirect("/sales/{$saleId}");
    }

    public function receipt(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $sale = Sale::find((int) $id, $shopId);

        if (!$sale) {
            flash('error', t('sale_not_found'));
            redirect('/sales');
        }

        View::render('sales/receipt', [
            'sale' => $sale,
            'items' => Sale::items((int) $id),
            'shop' => Shop::find($shopId),
        ]);
    }
}

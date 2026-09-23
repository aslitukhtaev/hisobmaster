<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\Customer;
use App\Models\Product;
use App\Models\ProductVariant;
use App\Models\Refund;
use App\Models\Report;
use App\Models\Sale;
use App\Models\Shop;
use PDOException;
use RuntimeException;

class SaleController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $from = (string) $request->input('from', '');
        $to = (string) $request->input('to', '');
        $filtered = $from !== '' && $to !== '' && $from <= $to;

        View::render('sales/index', [
            'sales' => $filtered ? Sale::rangeByShop($shopId, $from, $to) : Sale::recentByShop($shopId),
            'today' => Sale::todaysSummary($shopId),
            'from' => $from,
            'to' => $to,
            'filtered' => $filtered,
        ]);
    }

    /**
     * CSV export of the sales list — the same rows recentByShop()/rangeByShop()
     * would show on screen for the from/to filter currently in effect (see
     * index() above), so the exported row count always matches what's on the
     * page.
     */
    public function exportCsv(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $from = (string) $request->input('from', '');
        $to = (string) $request->input('to', '');
        $filtered = $from !== '' && $to !== '' && $from <= $to;

        $sales = $filtered ? Sale::rangeByShop($shopId, $from, $to) : Sale::recentByShop($shopId);

        $filename = 'sotuvlar_' . ($filtered ? $from . '_' . $to : date('Y-m-d')) . '.csv';

        header('Content-Type: text/csv; charset=UTF-8');
        header('Content-Disposition: attachment; filename="' . $filename . '"');
        header('Cache-Control: no-store');

        $out = fopen('php://output', 'w');
        fwrite($out, "\xEF\xBB\xBF");

        fputcsv($out, [t('sale_date'), t('cashier'), t('customer'), t('total'), t('payment_type')]);
        foreach ($sales as $sale) {
            fputcsv($out, [
                local_datetime($sale['created_at']),
                $sale['cashier_name'] ?? '',
                $sale['customer_name'] ?? '',
                number_format((float) $sale['total'], 2, '.', ''),
                t('payment_' . $sale['payment_type']),
            ]);
        }

        fclose($out);
        exit;
    }

    public function newForm(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        $variantsByProduct = [];
        foreach (ProductVariant::activeAllByShop($shopId) as $variant) {
            $variantsByProduct[(int) $variant['product_id']][] = $variant;
        }

        // A product with no active variants is sellable directly (needs its
        // own stock); a product WITH active variants is only sellable through
        // one of them (see Sale::create()), so it belongs in the list whenever
        // at least one of its variants still has stock, even if the parent's
        // own stock_qty (unused in that case) happens to be 0.
        $activeProducts = array_values(array_filter(
            Product::allByShop($shopId),
            static function (array $p) use ($variantsByProduct): bool {
                if ($p['status'] !== 'active') {
                    return false;
                }
                $variants = $variantsByProduct[(int) $p['id']] ?? [];
                if (!empty($variants)) {
                    return array_sum(array_map(static fn (array $v) => (float) $v['stock_qty'], $variants)) > 0;
                }
                return (float) $p['stock_qty'] > 0;
            }
        ));

        $productsJson = array_map(static function (array $p) use ($variantsByProduct): array {
            $variants = $variantsByProduct[(int) $p['id']] ?? [];

            return [
                'id' => (int) $p['id'],
                'name' => $p['name'],
                'unit' => $p['unit'],
                'price' => (float) $p['sell_price'],
                'stock' => (float) $p['stock_qty'],
                'barcode' => $p['barcode'],
                'variants' => array_map(static function (array $v) use ($p): array {
                    return [
                        'id' => (int) $v['id'],
                        'label' => $v['variant_label'],
                        'price' => $v['sell_price'] !== null ? (float) $v['sell_price'] : (float) $p['sell_price'],
                        'stock' => (float) $v['stock_qty'],
                        'barcode' => $v['barcode'],
                    ];
                }, $variants),
            ];
        }, $activeProducts);

        // allByShop() keeps the dropdown's existing alphabetical order; the
        // current balance/credit_limit are looked up separately (from
        // allWithBalance(), which already joins each customer's latest
        // debt_transactions balance — see Customer::allWithBalance()) and
        // merged in by id, so this doesn't change who's listed or in what
        // order, only what each option additionally carries for sale.js's
        // credit-limit warning.
        $balanceById = [];
        foreach (Customer::allWithBalance($shopId, false) as $c) {
            $balanceById[(int) $c['id']] = [
                'balance' => (float) ($c['balance'] ?? 0),
                'creditLimit' => $c['credit_limit'] !== null ? (float) $c['credit_limit'] : null,
            ];
        }

        $customersJson = array_map(static function (array $c) use ($balanceById): array {
            $extra = $balanceById[(int) $c['id']] ?? ['balance' => 0.0, 'creditLimit' => null];
            return [
                'id' => (int) $c['id'],
                'name' => $c['full_name'],
                'phone' => $c['phone'],
                'balance' => $extra['balance'],
                'creditLimit' => $extra['creditLimit'],
            ];
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

        $discount = (float) $request->input('discount', 0);
        $naqdAmount = (float) $request->input('naqd_amount', 0);
        $kartaAmount = (float) $request->input('karta_amount', 0);
        $customerIdInput = trim((string) $request->input('customer_id', ''));
        $customerName = trim((string) $request->input('customer_name', ''));
        $customerPhone = trim((string) $request->input('customer_phone', ''));

        // Kept across every redirect-back-with-error below so the cashier never
        // has to re-build the cart from scratch after a validation failure.
        $old = [
            'cart' => $cartRaw,
            'discount' => (string) $discount,
            'naqd_amount' => (string) $naqdAmount,
            'karta_amount' => (string) $kartaAmount,
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
            $items[] = [
                'product_id' => (int) $row['product_id'],
                'qty' => (float) $row['qty'],
                'variant_id' => !empty($row['variant_id']) ? (int) $row['variant_id'] : null,
            ];
        }

        // Whether the sale ends up leaving any qarz (debt) remainder isn't known
        // until Sale::create() has resolved real prices/discount, so a customer
        // is resolved here whenever one was given, regardless of the naqd/karta
        // split — Sale::create() itself throws customer_required_for_debt if a
        // debt remainder turns out to need one and none was provided.
        $customerId = null;
        if ($customerIdInput !== '') {
            $existing = Customer::find((int) $customerIdInput, $shopId);
            $customerId = $existing ? (int) $existing['id'] : null;
        }
        if ($customerId === null && $customerName !== '') {
            $customerId = Customer::findOrCreate($shopId, $customerName, $customerPhone !== '' ? $customerPhone : null);
        }

        try {
            $saleId = Sale::create($shopId, $cashierId, $items, $naqdAmount, $kartaAmount, $discount, $customerId);
        } catch (PDOException $e) {
            // A raw driver-level failure (e.g. a lock-contention edge case) —
            // never shown to the user verbatim, since PDOException extends
            // RuntimeException and would otherwise fall into the branch below
            // and get treated as one of Sale::create()'s own translation keys.
            log_exception($e);
            flash('error', t('unexpected_error'));
            keep_old($old);
            redirect('/sales/new');
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

        $refundableLines = Refund::refundableForSale((int) $id);
        $canRefund = array_reduce(
            $refundableLines,
            static fn (bool $carry, array $line): bool => $carry || (float) $line['remaining_qty'] > 0.0001,
            false
        );

        View::render('sales/receipt', [
            'sale' => $sale,
            'items' => Sale::items((int) $id),
            'payments' => Sale::payments((int) $id),
            'refunds' => Refund::historyForSale((int) $id),
            'canRefund' => $canRefund,
            'shop' => Shop::find($shopId),
        ]);
    }

    public function refundForm(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $sale = Sale::find((int) $id, $shopId);

        if (!$sale) {
            flash('error', t('sale_not_found'));
            redirect('/sales');
        }

        $lines = Refund::refundableForSale((int) $id);
        $hasRefundable = array_reduce(
            $lines,
            static fn (bool $carry, array $line): bool => $carry || (float) $line['remaining_qty'] > 0.0001,
            false
        );

        if (!$hasRefundable) {
            flash('error', t('no_refundable_items'));
            redirect("/sales/{$id}");
        }

        View::render('sales/refund', [
            'sale' => $sale,
            'lines' => $lines,
        ]);
    }

    public function refundStore(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $userId = (int) Auth::id();
        $sale = Sale::find((int) $id, $shopId);

        if (!$sale) {
            flash('error', t('sale_not_found'));
            redirect('/sales');
        }

        $qtyInputs = $request->input('qty', []);
        $reason = trim((string) $request->input('reason', ''));

        $lines = [];
        if (is_array($qtyInputs)) {
            foreach ($qtyInputs as $saleItemId => $qtyRaw) {
                $qty = (float) $qtyRaw;
                if ($qty > 0) {
                    $lines[] = ['sale_item_id' => (int) $saleItemId, 'qty' => $qty];
                }
            }
        }

        if (empty($lines)) {
            flash('error', t('refund_no_items_selected'));
            redirect("/sales/{$id}/refund");
        }

        try {
            $refundId = Refund::create($shopId, (int) $id, $userId, $lines, $reason !== '' ? $reason : null);
        } catch (PDOException $e) {
            log_exception($e);
            flash('error', t('unexpected_error'));
            redirect("/sales/{$id}/refund");
        } catch (RuntimeException $e) {
            flash('error', t($e->getMessage()));
            redirect("/sales/{$id}/refund");
        }

        $refund = null;
        foreach (Refund::historyForSale((int) $id) as $entry) {
            if ((int) $entry['id'] === $refundId) {
                $refund = $entry;
                break;
            }
        }

        ActivityLog::record($shopId, $userId, 'refund_created', [
            'sale_id' => (int) $id,
            'amount' => money((float) ($refund['total_amount'] ?? 0)),
        ]);

        flash('success', t('refund_completed'));
        redirect("/sales/{$id}");
    }

    public function shiftReport(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $today = date('Y-m-d');
        $report = Report::shiftReport($shopId, $today);

        View::render('sales/shift-report', [
            'totals' => $report['totals'],
            'byCashier' => $report['by_cashier'],
            'today' => $today,
        ]);
    }
}

<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Database;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\Customer;
use App\Models\DebtTransaction;
use App\Models\Product;
use Throwable;

/**
 * Fast, spreadsheet-like entry for the two jobs that take a new shop the
 * longest: its whole catalogue, and the debts customers already owe from
 * the paper notebook. One row per item, saved together; a row with a
 * mistake comes back marked and the rest are saved (public/assets/js/
 * quick-entry.js).
 */
class QuickEntryController
{
    private const MAX_ROWS = 500;

    public function productsForm(Request $request): void
    {
        View::render('products/quick', [
            'canPrices' => Auth::can('prices'),
            'prefillBarcode' => trim((string) $request->input('barcode', '')),
        ]);
    }

    public function productsStore(Request $request): void
    {
        $rows = $this->rows($request);
        $shopId = (int) Auth::shopId();
        $canPrices = Auth::can('prices');

        $created = [];
        $errors = [];
        $seenBarcodes = [];

        $pdo = Database::beginImmediate();
        try {
            foreach ($rows as $i => $row) {
                $index = (int) ($row['_row'] ?? $i);
                $name = trim((string) ($row['name'] ?? ''));
                $barcode = trim((string) ($row['barcode'] ?? ''));
                $unit = trim((string) ($row['unit'] ?? '')) ?: 'dona';
                $sell = self::number($row['sell_price'] ?? '');
                $cost = $canPrices ? self::number($row['cost_price'] ?? '') : '';
                $stock = self::number($row['stock_qty'] ?? '');
                $cost = $cost === '' ? '0' : $cost;
                $stock = $stock === '' ? '0' : $stock;

                $error = match (true) {
                    $name === '' => t('fill_required_fields'),
                    mb_strlen($name) > 200 => t('product_name_too_long'),
                    looks_like_formula($name) => t('product_name_formula'),
                    $sell === '' => t('quick_price_required'),
                    !is_numeric($sell) || !is_numeric($cost) || !is_numeric($stock) => t('values_must_be_positive'),
                    (float) $sell < 0 || (float) $cost < 0 || (float) $stock < 0 => t('values_must_be_positive'),
                    !valid_money($sell) || !valid_money($cost) || (float) $stock > QTY_MAX => t('amount_too_large'),
                    mb_strlen($unit) > 20 => t('values_must_be_positive'),
                    !qty_fits_unit((float) $stock, $unit) => t('qty_must_be_whole', ['unit' => $unit]),
                    mb_strlen($barcode) > 64 => t('barcode_too_long'),
                    $barcode !== '' && isset($seenBarcodes[$barcode]) => t('quick_barcode_duplicate'),
                    $barcode !== '' && Product::barcodeTaken($shopId, $barcode) => t('barcode_taken', ['barcode' => $barcode]),
                    default => null,
                };
                if ($error !== null) {
                    $errors[] = ['row' => $index, 'message' => $error];
                    continue;
                }

                Product::create([
                    'shop_id' => $shopId,
                    'name' => $name,
                    'unit' => $unit,
                    'cost_price' => (float) $cost,
                    'sell_price' => (float) $sell,
                    'stock_qty' => (float) $stock,
                    'barcode' => $barcode !== '' ? $barcode : null,
                ]);
                if ($barcode !== '') {
                    $seenBarcodes[$barcode] = true;
                }
                $created[] = $index;
            }
            Database::commit($pdo);
        } catch (Throwable $e) {
            Database::rollback($pdo);
            log_exception($e);
            $this->json(['ok' => false, 'message' => t('quick_failed')]);
        }

        if ($created !== []) {
            ActivityLog::record($shopId, (int) Auth::id(), 'products_bulk_created', ['count' => (string) count($created)]);
        }
        $this->json(['ok' => true, 'created' => $created, 'errors' => $errors]);
    }

    public function debtsForm(Request $request): void
    {
        View::render('customers/quick-debts');
    }

    public function debtsStore(Request $request): void
    {
        $rows = $this->rows($request);
        $shopId = (int) Auth::shopId();
        $userId = (int) Auth::id();

        $created = [];
        $errors = [];
        $total = 0.0;

        foreach ($rows as $i => $row) {
            $index = (int) ($row['_row'] ?? $i);
            $name = trim((string) ($row['name'] ?? ''));
            $phoneRaw = trim((string) ($row['phone'] ?? ''));
            $amount = self::number($row['amount'] ?? '');
            $note = trim((string) ($row['note'] ?? ''));
            $phone = normalize_phone($phoneRaw);

            $error = match (true) {
                $name === '' => t('fill_required_fields'),
                mb_strlen($name) > 200 || looks_like_formula($name) => t('product_name_formula'),
                $phone === null => t('phone_invalid'),
                $amount === '' => t('quick_amount_required'),
                !valid_money($amount, false) => is_numeric($amount) && (float) $amount > 0 ? t('amount_too_large') : t('payment_amount_invalid'),
                default => null,
            };
            if ($error !== null) {
                $errors[] = ['row' => $index, 'message' => $error];
                continue;
            }

            try {
                $customerId = $phone !== ''
                    ? Customer::findOrCreate($shopId, $name, $phone)
                    : Customer::create($shopId, $name, null, $note !== '' ? mb_substr($note, 0, 500) : null);
                DebtTransaction::record($shopId, $customerId, null, 'qarz', round((float) $amount, 2), $userId);
            } catch (Throwable $e) {
                log_exception($e);
                $errors[] = ['row' => $index, 'message' => t('quick_failed')];
                continue;
            }
            $created[] = $index;
            $total += (float) $amount;
        }

        if ($created !== []) {
            ActivityLog::record($shopId, $userId, 'debts_bulk_added', [
                'count' => (string) count($created),
                'total' => money($total),
            ]);
        }
        $this->json(['ok' => true, 'created' => $created, 'errors' => $errors]);
    }

    /** @return list<array<string, mixed>> */
    private function rows(Request $request): array
    {
        $rows = json_decode((string) $request->input('rows', '[]'), true);
        if (!is_array($rows) || count($rows) > self::MAX_ROWS) {
            $this->json(['ok' => false, 'message' => t('quick_failed')]);
        }

        return array_values(array_filter($rows, 'is_array'));
    }

    /** "15 000", "15,5" → "15000", "15.5": how people type and paste numbers. */
    private static function number(mixed $value): string
    {
        return str_replace([' ', "\u{00A0}", ','], ['', '', '.'], trim((string) $value));
    }

    private function json(array $data): never
    {
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode($data, JSON_UNESCAPED_UNICODE);
        exit;
    }
}

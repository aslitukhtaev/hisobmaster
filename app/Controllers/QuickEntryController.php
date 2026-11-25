<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Database;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\Category;
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
            'wholesale' => wholesale_enabled(),
            'categories' => Category::allByShop((int) Auth::shopId()),
            'prefillBarcode' => trim((string) $request->input('barcode', '')),
        ]);
    }

    public function productsStore(Request $request): void
    {
        $rows = $this->rows($request);
        $shopId = (int) Auth::shopId();
        $canPrices = Auth::can('prices');
        $wholesale = wholesale_enabled();
        // One category for the whole batch (the field above the grid).
        $category = Category::normalizeName((string) $request->input('category', ''));
        if (mb_strlen($category) > 100 || looks_like_formula($category)) {
            $this->json(['ok' => false, 'message' => t('category_name_formula')]);
        }

        $created = [];
        $errors = [];
        $seenBarcodes = [];

        $pdo = Database::beginImmediate();
        try {
            $categoryId = $category !== '' ? Category::findOrCreate($shopId, $category) : null;
            foreach ($rows as $i => $row) {
                $index = (int) ($row['_row'] ?? $i);
                $name = trim((string) ($row['name'] ?? ''));
                $barcode = trim((string) ($row['barcode'] ?? ''));
                $unit = trim((string) ($row['unit'] ?? '')) ?: 'dona';
                $sell = self::number($row['sell_price'] ?? '');
                $cost = $canPrices ? self::number($row['cost_price'] ?? '') : '';
                $stock = self::number($row['stock_qty'] ?? '');
                $wholesalePrice = $wholesale ? self::number($row['wholesale_price'] ?? '') : '';
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
                    $wholesalePrice !== '' && !valid_money($wholesalePrice) => is_numeric($wholesalePrice) && (float) $wholesalePrice > 0 ? t('amount_too_large') : t('values_must_be_positive'),
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

                $productId = Product::create([
                    'shop_id' => $shopId,
                    'category_id' => $categoryId,
                    'name' => $name,
                    'unit' => $unit,
                    'cost_price' => (float) $cost,
                    'sell_price' => (float) $sell,
                    'stock_qty' => (float) $stock,
                    'barcode' => $barcode !== '' ? $barcode : null,
                ]);
                if ($wholesalePrice !== '') {
                    Product::setWholesalePrice($productId, $shopId, (float) $wholesalePrice);
                }
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

    /**
     * "Tez o'zgartirish": every active product and variant in one grid —
     * name, barcode, prices — changed like a spreadsheet and saved together
     * (public/assets/js/bulk-edit.js). Only the cells that changed are sent
     * and written, so nothing else on the product is touched.
     */
    public function bulkEditForm(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $pdo = Database::connect();
        $rows = [];
        $products = $pdo->prepare(
            "SELECT p.id, p.name, p.barcode, p.sell_price, p.cost_price, p.wholesale_price, p.unit, COALESCE(c.name, '') AS category
             FROM products p LEFT JOIN categories c ON c.id = p.category_id
             WHERE p.shop_id = ? AND p.status = 'active' ORDER BY p.name COLLATE NOCASE"
        );
        $products->execute([$shopId]);
        $variants = $pdo->prepare(
            "SELECT id, product_id, variant_label, barcode, sell_price, cost_price, wholesale_price
             FROM product_variants WHERE shop_id = ? AND status = 'active' ORDER BY variant_label COLLATE NOCASE"
        );
        $variants->execute([$shopId]);
        $byProduct = [];
        foreach ($variants->fetchAll() as $v) {
            $byProduct[(int) $v['product_id']][] = $v;
        }
        $num = static fn (mixed $v): string => $v === null ? '' : rtrim(rtrim(number_format((float) $v, 2, '.', ''), '0'), '.');
        foreach ($products->fetchAll() as $p) {
            $rows[] = [
                'type' => 'product', 'id' => (int) $p['id'], 'parent' => null, 'category' => (string) $p['category'],
                'name' => (string) $p['name'], 'barcode' => (string) ($p['barcode'] ?? ''), 'sell_price' => $num($p['sell_price']),
                'wholesale_price' => $num($p['wholesale_price']), 'cost_price' => $num($p['cost_price']), 'unit' => (string) $p['unit'],
            ];
            foreach ($byProduct[(int) $p['id']] ?? [] as $v) {
                $rows[] = [
                    'type' => 'variant', 'id' => (int) $v['id'], 'parent' => (string) $p['name'], 'category' => (string) $p['category'],
                    'name' => (string) $v['variant_label'], 'barcode' => (string) ($v['barcode'] ?? ''), 'sell_price' => $num($v['sell_price']),
                    'wholesale_price' => $num($v['wholesale_price']), 'cost_price' => $num($v['cost_price']), 'unit' => (string) $p['unit'],
                ];
            }
        }

        View::render('products/bulk-edit', [
            'rows' => $rows,
            'canPrices' => Auth::can('prices'),
            'wholesale' => wholesale_enabled(),
            'categories' => Category::allByShop($shopId),
        ]);
    }

    /**
     * Saves the changed cells: rows of {_row, type: product|variant, id,
     * fields: {name, barcode, sell_price, wholesale_price, cost_price}} —
     * only the fields that changed. A row with a mistake comes back marked;
     * the others are saved.
     */
    public function bulkEditStore(Request $request): void
    {
        $rows = $this->rows($request);
        $shopId = (int) Auth::shopId();
        $canPrices = Auth::can('prices');
        $wholesale = wholesale_enabled();
        $pdo = Database::connect();

        $saved = [];
        $errors = [];
        $seenBarcodes = [];
        foreach ($rows as $i => $row) {
            $index = (int) ($row['_row'] ?? $i);
            $isVariant = ($row['type'] ?? '') === 'variant';
            $id = (int) ($row['id'] ?? 0);
            $fields = is_array($row['fields'] ?? null) ? $row['fields'] : [];
            $table = $isVariant ? 'product_variants' : 'products';
            $exists = $pdo->prepare("SELECT id FROM $table WHERE id = ? AND shop_id = ?");
            $exists->execute([$id, $shopId]);
            if ($id <= 0 || $exists->fetchColumn() === false) {
                $errors[] = ['row' => $index, 'message' => t('product_not_found')];
                continue;
            }

            $set = [];
            $error = null;
            foreach ($fields as $field => $raw) {
                $value = trim((string) $raw);
                if ($field === 'name') {
                    $max = $isVariant ? 100 : 200;
                    $error = match (true) {
                        $value === '' => t('fill_required_fields'),
                        mb_strlen($value) > $max => t('product_name_too_long'),
                        looks_like_formula($value) => t('product_name_formula'),
                        default => null,
                    };
                    $set[$isVariant ? 'variant_label' : 'name'] = $value;
                } elseif ($field === 'barcode') {
                    if ($value !== '') {
                        $error = match (true) {
                            mb_strlen($value) > 64 => t('barcode_too_long'),
                            isset($seenBarcodes[$value]) => t('quick_barcode_duplicate'),
                            Product::barcodeTaken($shopId, $value, $isVariant ? null : $id, $isVariant ? $id : null) => t('barcode_taken', ['barcode' => $value]),
                            default => null,
                        };
                        $seenBarcodes[$value] = true;
                    }
                    $set['barcode'] = $value !== '' ? $value : null;
                } elseif (in_array($field, ['sell_price', 'wholesale_price', 'cost_price'], true)) {
                    if (($field === 'wholesale_price' && !$wholesale) || ($field === 'cost_price' && !$canPrices)) {
                        continue;
                    }
                    $number = self::number($value);
                    // Empty: no optom price; a variant's empty price / cost = the product's.
                    $required = !$isVariant && $field === 'sell_price';
                    if ($number === '') {
                        if ($required) {
                            $error = t('quick_price_required');
                        }
                        $set[$field] = (!$isVariant && $field === 'cost_price') ? 0.0 : null;
                    } elseif (!is_numeric($number) || (float) $number < 0) {
                        $error = t('values_must_be_positive');
                    } elseif (!valid_money($number)) {
                        $error = t('amount_too_large');
                    } else {
                        $set[$field] = round((float) $number, 2);
                    }
                }
                if ($error !== null) {
                    break;
                }
            }
            if ($error !== null) {
                $errors[] = ['row' => $index, 'message' => $error];
                continue;
            }
            if ($set === []) {
                $saved[] = $index;
                continue;
            }

            try {
                if (!$isVariant) {
                    $set['updated_at'] = gmdate('Y-m-d H:i:s');
                }
                $assignments = implode(', ', array_map(static fn (string $c): string => "$c = ?", array_keys($set)));
                $pdo->prepare("UPDATE $table SET $assignments WHERE id = ? AND shop_id = ?")
                    ->execute([...array_values($set), $id, $shopId]);
            } catch (Throwable $e) {
                log_exception($e);
                $errors[] = ['row' => $index, 'message' => t('quick_failed')];
                continue;
            }
            $saved[] = $index;
        }

        if ($saved !== []) {
            ActivityLog::record($shopId, Auth::id(), 'products_bulk_edited', ['count' => (string) count($saved)]);
        }
        $this->json(['ok' => true, 'saved' => $saved, 'errors' => $errors]);
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

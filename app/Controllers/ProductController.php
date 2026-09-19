<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Database;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\Category;
use App\Models\Product;
use App\Models\ProductVariant;
use App\Models\Settings;

class ProductController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $search = trim((string) $request->input('q', ''));
        $lowStockOnly = $request->input('filter') === 'low_stock';
        $shopDefaultThreshold = Settings::lowStockThresholdDefault($shopId);

        $products = $lowStockOnly
            ? Product::lowStock($shopId, $shopDefaultThreshold)
            : Product::allByShop($shopId, $search);

        View::render('products/index', [
            'products' => $products,
            'counts' => Product::counts($shopId),
            'search' => $search,
            'lowStockOnly' => $lowStockOnly,
            'lowStockCounts' => Product::lowStockCounts($shopId, $shopDefaultThreshold),
            'shopDefaultThreshold' => $shopDefaultThreshold,
        ]);
    }

    public function createForm(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        View::render('products/create', [
            'categories' => Category::allByShop($shopId),
        ]);
    }

    public function store(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $data = $this->validate($request);

        if ($data === null) {
            redirect('/products/create');
        }

        $categoryId = $data['category'] !== ''
            ? Category::findOrCreate($shopId, $data['category'])
            : null;

        Product::create([
            'shop_id' => $shopId,
            'category_id' => $categoryId,
            'name' => $data['name'],
            'unit' => $data['unit'],
            'cost_price' => $data['cost_price'],
            'sell_price' => $data['sell_price'],
            'stock_qty' => $data['stock_qty'],
            'barcode' => $data['barcode'] !== '' ? $data['barcode'] : null,
            'low_stock_threshold' => $data['low_stock_threshold'],
            'pack_size' => $data['pack_size'],
        ]);

        flash('success', t('product_created'));
        redirect('/products');
    }

    public function editForm(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $product = Product::find((int) $id, $shopId);

        if (!$product) {
            flash('error', t('product_not_found'));
            redirect('/products');
        }

        View::render('products/edit', [
            'product' => $product,
            'categories' => Category::allByShop($shopId),
            'variants' => ProductVariant::allByProduct((int) $id, $shopId),
        ]);
    }

    public function update(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $product = Product::find((int) $id, $shopId);

        if (!$product) {
            flash('error', t('product_not_found'));
            redirect('/products');
        }

        $data = $this->validate($request, $product);

        if ($data === null) {
            redirect("/products/{$id}/edit");
        }

        $categoryId = $data['category'] !== ''
            ? Category::findOrCreate($shopId, $data['category'])
            : null;

        Product::update((int) $id, $shopId, [
            'category_id' => $categoryId,
            'name' => $data['name'],
            'unit' => $data['unit'],
            'cost_price' => $data['cost_price'],
            'sell_price' => $data['sell_price'],
            'stock_qty' => $data['stock_qty'],
            'barcode' => $data['barcode'] !== '' ? $data['barcode'] : null,
            'low_stock_threshold' => $data['low_stock_threshold'],
            'pack_size' => $data['pack_size'],
        ]);

        flash('success', t('product_updated'));
        redirect('/products');
    }

    public function toggleStatus(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $product = Product::find((int) $id, $shopId);

        if ($product) {
            $newStatus = $product['status'] === 'active' ? 'inactive' : 'active';
            Product::setStatus((int) $id, $shopId, $newStatus);
            flash('success', t('product_status_updated'));
        }

        redirect('/products');
    }

    /**
     * @return array{name: string, category: string, unit: string, cost_price: float, sell_price: float, stock_qty: float, barcode: string, low_stock_threshold: ?float, pack_size: ?int}|null
     */
    private function validate(Request $request, ?array $existingProduct = null): ?array
    {
        $canEditPrice = Auth::can('prices');

        $name = trim((string) $request->input('name', ''));
        $category = trim((string) $request->input('category', ''));
        $unit = trim((string) $request->input('unit', '')) ?: 'dona';
        $sellPrice = $request->input('sell_price', '');
        $stockQty = $request->input('stock_qty', '');
        $barcode = trim((string) $request->input('barcode', ''));
        $lowStockThresholdRaw = trim((string) $request->input('low_stock_threshold', ''));
        $packSizeRaw = trim((string) $request->input('pack_size', ''));

        $costPrice = $canEditPrice
            ? $request->input('cost_price', '')
            : (string) ($existingProduct['cost_price'] ?? 0);

        $old = [
            'name' => $name,
            'category' => $category,
            'unit' => $unit,
            'cost_price' => (string) $costPrice,
            'sell_price' => (string) $sellPrice,
            'stock_qty' => (string) $stockQty,
            'barcode' => $barcode,
            'low_stock_threshold' => $lowStockThresholdRaw,
            'pack_size' => $packSizeRaw,
        ];

        if ($name === '' || !is_numeric($costPrice) || !is_numeric($sellPrice) || !is_numeric($stockQty)) {
            flash('error', t('fill_required_fields'));
            keep_old($old);
            return null;
        }

        if ((float) $costPrice < 0 || (float) $sellPrice < 0 || (float) $stockQty < 0) {
            flash('error', t('values_must_be_positive'));
            keep_old($old);
            return null;
        }

        if ($lowStockThresholdRaw !== '' && (!is_numeric($lowStockThresholdRaw) || (float) $lowStockThresholdRaw < 0)) {
            flash('error', t('values_must_be_positive'));
            keep_old($old);
            return null;
        }

        if ($packSizeRaw !== '' && (!ctype_digit($packSizeRaw) || (int) $packSizeRaw < 1)) {
            flash('error', t('pack_size_invalid'));
            keep_old($old);
            return null;
        }

        return [
            'name' => $name,
            'category' => $category,
            'unit' => $unit,
            'cost_price' => (float) $costPrice,
            'sell_price' => (float) $sellPrice,
            'stock_qty' => (float) $stockQty,
            'barcode' => $barcode,
            'low_stock_threshold' => $lowStockThresholdRaw !== '' ? (float) $lowStockThresholdRaw : null,
            'pack_size' => $packSizeRaw !== '' ? (int) $packSizeRaw : null,
        ];
    }

    // ---------- Product variants ----------

    public function variantStore(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $product = Product::find((int) $id, $shopId);

        if (!$product) {
            flash('error', t('product_not_found'));
            redirect('/products');
        }

        $data = $this->validateVariant($request);
        if ($data === null) {
            redirect("/products/{$id}/edit");
        }

        ProductVariant::create([
            'product_id' => (int) $id,
            'shop_id' => $shopId,
            'variant_label' => $data['variant_label'],
            'stock_qty' => $data['stock_qty'],
            'barcode' => $data['barcode'] !== '' ? $data['barcode'] : null,
            'sell_price' => $data['sell_price'],
            'cost_price' => $data['cost_price'],
        ]);

        flash('success', t('variant_created'));
        redirect("/products/{$id}/edit");
    }

    public function variantUpdate(Request $request, string $id, string $variantId): void
    {
        $shopId = (int) Auth::shopId();
        $variant = ProductVariant::find((int) $variantId, $shopId);

        if (!$variant || (int) $variant['product_id'] !== (int) $id) {
            flash('error', t('variant_not_found'));
            redirect("/products/{$id}/edit");
        }

        $data = $this->validateVariant($request);
        if ($data === null) {
            redirect("/products/{$id}/edit");
        }

        ProductVariant::update((int) $variantId, $shopId, [
            'variant_label' => $data['variant_label'],
            'stock_qty' => $data['stock_qty'],
            'barcode' => $data['barcode'] !== '' ? $data['barcode'] : null,
            'sell_price' => $data['sell_price'],
            'cost_price' => $data['cost_price'],
        ]);

        flash('success', t('variant_updated'));
        redirect("/products/{$id}/edit");
    }

    public function variantToggleStatus(Request $request, string $id, string $variantId): void
    {
        $shopId = (int) Auth::shopId();
        $variant = ProductVariant::find((int) $variantId, $shopId);

        if ($variant && (int) $variant['product_id'] === (int) $id) {
            $newStatus = $variant['status'] === 'active' ? 'inactive' : 'active';
            ProductVariant::setStatus((int) $variantId, $shopId, $newStatus);
            flash('success', t('variant_status_updated'));
        }

        redirect("/products/{$id}/edit");
    }

    /**
     * @return array{variant_label: string, stock_qty: float, barcode: string, sell_price: ?float, cost_price: ?float}|null
     */
    private function validateVariant(Request $request): ?array
    {
        $label = trim((string) $request->input('variant_label', ''));
        $stockQty = $request->input('variant_stock_qty', '');
        $barcode = trim((string) $request->input('variant_barcode', ''));
        $sellPriceRaw = trim((string) $request->input('variant_sell_price', ''));
        $costPriceRaw = trim((string) $request->input('variant_cost_price', ''));

        if ($label === '' || !is_numeric($stockQty) || (float) $stockQty < 0) {
            flash('error', t('fill_required_fields'));
            return null;
        }

        if ($sellPriceRaw !== '' && (!is_numeric($sellPriceRaw) || (float) $sellPriceRaw < 0)) {
            flash('error', t('values_must_be_positive'));
            return null;
        }

        if ($costPriceRaw !== '' && (!is_numeric($costPriceRaw) || (float) $costPriceRaw < 0)) {
            flash('error', t('values_must_be_positive'));
            return null;
        }

        return [
            'variant_label' => $label,
            'stock_qty' => (float) $stockQty,
            'barcode' => $barcode,
            'sell_price' => $sellPriceRaw !== '' ? (float) $sellPriceRaw : null,
            'cost_price' => $costPriceRaw !== '' ? (float) $costPriceRaw : null,
        ];
    }

    // ---------- CSV import ----------

    public function importForm(Request $request): void
    {
        View::render('products/import');
    }

    /**
     * Parses the uploaded CSV and shows a preview: every row that passed
     * validation (ready to commit) plus every row that was skipped, with its
     * reason — nothing is written to the database yet.
     */
    public function importPreview(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $file = $request->file('csv_file');

        if ($file === null || !is_uploaded_file($file['tmp_name'])) {
            flash('error', t('import_file_required'));
            redirect('/products/import');
        }

        $handle = fopen($file['tmp_name'], 'r');
        if ($handle === false) {
            flash('error', t('import_file_required'));
            redirect('/products/import');
        }

        [$validRows, $skipped] = $this->parseImportCsv($handle, $shopId);
        fclose($handle);

        if (empty($validRows) && empty($skipped)) {
            flash('error', t('import_file_empty'));
            redirect('/products/import');
        }

        View::render('products/import-preview', [
            'validRows' => $validRows,
            'skipped' => $skipped,
            'validRowsJson' => json_encode($validRows, JSON_UNESCAPED_UNICODE),
        ]);
    }

    public function importCommit(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $rowsRaw = (string) $request->input('rows', '[]');
        $rows = json_decode($rowsRaw, true);

        if (!is_array($rows) || empty($rows)) {
            flash('error', t('import_file_empty'));
            redirect('/products/import');
        }

        $created = 0;
        $updated = 0;

        $pdo = Database::beginImmediate();
        try {
            foreach ($rows as $row) {
                if (!is_array($row) || !isset($row['name'])) {
                    continue;
                }

                $name = trim((string) $row['name']);
                $unit = trim((string) ($row['unit'] ?? '')) ?: 'dona';
                $costPrice = is_numeric($row['cost_price'] ?? null) ? (float) $row['cost_price'] : 0.0;
                $sellPrice = is_numeric($row['sell_price'] ?? null) ? (float) $row['sell_price'] : 0.0;
                $stockQty = is_numeric($row['stock_qty'] ?? null) ? (float) $row['stock_qty'] : 0.0;
                $barcode = trim((string) ($row['barcode'] ?? ''));

                if ($name === '') {
                    continue;
                }

                $existing = $barcode !== '' ? Product::findByBarcode($barcode, $shopId) : null;

                if ($existing !== null) {
                    // A plain SET, not a computed increment/decrement — the CSV
                    // gives an absolute stock count the owner wants recorded, so
                    // there is no prior value being read-then-written here (see
                    // Product::update()); it's the same shape the manual edit
                    // form already uses.
                    Product::update((int) $existing['id'], $shopId, [
                        'category_id' => $existing['category_id'],
                        'name' => $name,
                        'unit' => $unit,
                        'cost_price' => $costPrice,
                        'sell_price' => $sellPrice,
                        'stock_qty' => $stockQty,
                        'barcode' => $barcode !== '' ? $barcode : null,
                        'low_stock_threshold' => $existing['low_stock_threshold'],
                        'pack_size' => $existing['pack_size'],
                    ]);
                    $updated++;
                } else {
                    Product::create([
                        'shop_id' => $shopId,
                        'category_id' => null,
                        'name' => $name,
                        'unit' => $unit,
                        'cost_price' => $costPrice,
                        'sell_price' => $sellPrice,
                        'stock_qty' => $stockQty,
                        'barcode' => $barcode !== '' ? $barcode : null,
                    ]);
                    $created++;
                }
            }

            $pdo->commit();
        } catch (\Throwable $e) {
            $pdo->rollBack();
            flash('error', t('import_failed'));
            redirect('/products/import');
        }

        ActivityLog::record($shopId, (int) Auth::id(), 'products_imported', [
            'created' => $created,
            'updated' => $updated,
        ]);

        flash('success', t('import_completed', ['created' => $created, 'updated' => $updated]));
        redirect('/products');
    }

    /**
     * @return array{0: array<int, array<string, mixed>>, 1: array<int, array{row: int, reason: string}>}
     */
    private function parseImportCsv($handle, int $shopId): array
    {
        $header = fgetcsv($handle);
        if ($header === false) {
            return [[], []];
        }

        $normalizedHeader = array_map(
            static fn ($h) => strtolower(trim((string) $h)),
            $header
        );
        $expected = ['name', 'unit', 'cost_price', 'sell_price', 'stock_qty', 'barcode'];
        $indexOf = [];
        foreach ($expected as $col) {
            $pos = array_search($col, $normalizedHeader, true);
            $indexOf[$col] = $pos === false ? null : $pos;
        }

        $validRows = [];
        $skipped = [];
        $rowNumber = 1; // header was row 1

        while (($row = fgetcsv($handle)) !== false) {
            $rowNumber++;

            if (count(array_filter($row, static fn ($v) => trim((string) $v) !== '')) === 0) {
                continue; // blank line
            }

            $get = static function (string $col) use ($row, $indexOf): string {
                $i = $indexOf[$col];
                return $i !== null && isset($row[$i]) ? trim((string) $row[$i]) : '';
            };

            $name = $get('name');
            $costPrice = $get('cost_price');
            $sellPrice = $get('sell_price');
            $stockQty = $get('stock_qty');
            $barcode = $get('barcode');
            $unit = $get('unit') ?: 'dona';

            if ($name === '') {
                $skipped[] = ['row' => $rowNumber, 'reason' => t('import_error_missing_name')];
                continue;
            }
            if ($costPrice !== '' && !is_numeric($costPrice)) {
                $skipped[] = ['row' => $rowNumber, 'reason' => t('import_error_bad_cost_price')];
                continue;
            }
            if ($sellPrice !== '' && !is_numeric($sellPrice)) {
                $skipped[] = ['row' => $rowNumber, 'reason' => t('import_error_bad_sell_price')];
                continue;
            }
            if ($stockQty !== '' && !is_numeric($stockQty)) {
                $skipped[] = ['row' => $rowNumber, 'reason' => t('import_error_bad_stock_qty')];
                continue;
            }

            $willUpdate = $barcode !== '' && Product::findByBarcode($barcode, $shopId) !== null;

            $validRows[] = [
                'row' => $rowNumber,
                'name' => $name,
                'unit' => $unit,
                'cost_price' => $costPrice !== '' ? (float) $costPrice : 0.0,
                'sell_price' => $sellPrice !== '' ? (float) $sellPrice : 0.0,
                'stock_qty' => $stockQty !== '' ? (float) $stockQty : 0.0,
                'barcode' => $barcode,
                'will_update' => $willUpdate,
            ];
        }

        return [$validRows, $skipped];
    }
}

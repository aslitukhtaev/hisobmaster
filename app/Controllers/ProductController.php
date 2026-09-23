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

        ActivityLog::record($shopId, (int) Auth::id(), 'product_created', [
            'name' => $data['name'],
            'qty' => format_qty($data['stock_qty']) . ' ' . $data['unit'],
        ]);

        flash('success', t('product_created'));
        $this->warnIfBelowCost($data['sell_price'], $data['cost_price'], $data['name']);
        redirect('/products');
    }

    public function editForm(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $product = Product::find((int) $id, $shopId);

        if (!$product) {
            abort_404();
        }

        $variants = ProductVariant::allByProduct((int) $id, $shopId);
        $activeVariants = array_filter($variants, static fn (array $v) => $v['status'] === 'active');

        View::render('products/edit', [
            'product' => $product,
            'categories' => Category::allByShop($shopId),
            'variants' => $variants,
            'hasActiveVariants' => !empty($activeVariants),
            'variantStockTotal' => array_sum(array_map(static fn (array $v) => (float) $v['stock_qty'], $activeVariants)),
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

        $this->logProductChanges($shopId, $product, $data);

        flash('success', t('product_updated'));
        $this->warnIfBelowCost($data['sell_price'], $data['cost_price'], $data['name']);
        redirect('/products');
    }

    public function toggleStatus(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $product = Product::find((int) $id, $shopId);

        if ($product) {
            $newStatus = $product['status'] === 'active' ? 'inactive' : 'active';
            Product::setStatus((int) $id, $shopId, $newStatus);
            ActivityLog::record($shopId, (int) Auth::id(), 'product_status_changed', [
                'name' => $product['name'],
                'status' => t($newStatus === 'active' ? 'active_status' : 'inactive_status'),
            ]);
            flash('success', t('product_status_updated'));
        }

        redirect('/products');
    }

    /**
     * @return array{name: string, category: string, unit: string, cost_price: float, sell_price: float, stock_qty: float, barcode: string, low_stock_threshold: ?float, pack_size: ?int}|null
     */
    private function validate(Request $request, ?array $existingProduct = null): ?array
    {
        $shopId = (int) Auth::shopId();
        $canEditPrice = Auth::can('prices');

        $name = trim((string) $request->input('name', ''));
        $category = trim((string) $request->input('category', ''));
        $unit = trim((string) $request->input('unit', '')) ?: 'dona';
        $sellPrice = $request->input('sell_price', '');
        $barcode = trim((string) $request->input('barcode', ''));
        $lowStockThresholdRaw = trim((string) $request->input('low_stock_threshold', ''));
        $packSizeRaw = trim((string) $request->input('pack_size', ''));

        // A product sold through variants keeps its stock on the variants;
        // its own stock_qty can only be brought down (to clear a leftover
        // from before the variants existed — see products/edit), never
        // raised, since nothing can sell it.
        $hasActiveVariants = $existingProduct !== null
            && !empty(ProductVariant::activeByProduct((int) $existingProduct['id'], $shopId));
        $stockQty = $request->input('stock_qty', null);
        if ($hasActiveVariants && ($stockQty === null || $stockQty === '')) {
            $stockQty = (string) $existingProduct['stock_qty'];
        }
        $stockQty = (string) ($stockQty ?? '');

        $costPrice = $canEditPrice
            ? $request->input('cost_price', '')
            : (string) ($existingProduct['cost_price'] ?? 0);

        $old = [
            'name' => $name,
            'category' => $category,
            'unit' => $unit,
            'cost_price' => (string) $costPrice,
            'sell_price' => (string) $sellPrice,
            'stock_qty' => $stockQty,
            'barcode' => $barcode,
            'low_stock_threshold' => $lowStockThresholdRaw,
            'pack_size' => $packSizeRaw,
        ];

        $fail = static function (string $message) use ($old): ?array {
            flash('error', $message);
            keep_old($old);
            return null;
        };

        if ($name === '' || !is_numeric($costPrice) || !is_numeric($sellPrice) || !is_numeric($stockQty)) {
            return $fail(t('fill_required_fields'));
        }

        if (looks_like_formula($name)) {
            return $fail(t('product_name_formula'));
        }

        if (mb_strlen($name) > 200) {
            return $fail(t('product_name_too_long'));
        }

        if ((float) $costPrice < 0 || (float) $sellPrice < 0 || (float) $stockQty < 0) {
            return $fail(t('values_must_be_positive'));
        }

        if (!valid_money($costPrice) || !valid_money($sellPrice) || (float) $stockQty > QTY_MAX) {
            return $fail(t('amount_too_large'));
        }

        // A fractional count saved before whole units were enforced (e.g.
        // 2.5 dona) must not lock the product against every other edit — it
        // only has to become whole once the stock or unit is actually changed.
        $legacyStockUnchanged = $existingProduct !== null
            && abs((float) $stockQty - (float) $existingProduct['stock_qty']) < 0.000001
            && $unit === $existingProduct['unit'];
        if (!$legacyStockUnchanged && !qty_fits_unit((float) $stockQty, $unit)) {
            return $fail(t('qty_must_be_whole', ['unit' => $unit]));
        }

        if ($hasActiveVariants && (float) $stockQty > (float) $existingProduct['stock_qty'] + 0.000001) {
            return $fail(t('variant_product_stock_locked'));
        }

        if ($barcode !== '') {
            if (mb_strlen($barcode) > 64) {
                return $fail(t('barcode_too_long'));
            }
            if (Product::barcodeTaken($shopId, $barcode, $existingProduct !== null ? (int) $existingProduct['id'] : null)) {
                return $fail(t('barcode_taken', ['barcode' => $barcode]));
            }
        }

        if ($lowStockThresholdRaw !== '' && (!is_numeric($lowStockThresholdRaw) || (float) $lowStockThresholdRaw < 0 || (float) $lowStockThresholdRaw > QTY_MAX)) {
            return $fail(t('values_must_be_positive'));
        }

        if ($packSizeRaw !== '' && (!ctype_digit($packSizeRaw) || (int) $packSizeRaw < 1 || (int) $packSizeRaw > 100000)) {
            return $fail(t('pack_size_invalid'));
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

    /**
     * Selling below cost is sometimes deliberate (clearance), so it's saved
     * anyway — just never silently.
     */
    private function warnIfBelowCost(?float $sellPrice, ?float $costPrice, string $name): void
    {
        if ($sellPrice !== null && $costPrice !== null && $sellPrice < $costPrice) {
            flash('warning', t('sell_below_cost_warning', [
                'name' => $name,
                'sell' => money($sellPrice),
                'cost' => money($costPrice),
            ]));
        }
    }

    /**
     * Every manual edit is recorded: a stock change on its own entry (it is
     * what an audit of missing goods looks for first), everything else as
     * one "field: old → new" summary.
     */
    private function logProductChanges(int $shopId, array $before, array $after): void
    {
        $userId = (int) Auth::id();
        $name = $after['name'];

        if (abs((float) $before['stock_qty'] - $after['stock_qty']) > 0.000001) {
            ActivityLog::record($shopId, $userId, 'product_stock_changed', [
                'name' => $name,
                'old' => format_qty((float) $before['stock_qty']),
                'new' => format_qty($after['stock_qty']) . ' ' . $after['unit'],
            ]);
        }

        $beforeCategory = '';
        if (!empty($before['category_id'])) {
            foreach (Category::allByShop($shopId) as $cat) {
                if ((int) $cat['id'] === (int) $before['category_id']) {
                    $beforeCategory = $cat['name'];
                    break;
                }
            }
        }

        $moneyOrDash = static fn ($v) => $v === null ? '—' : money((float) $v);
        $qtyOrDash = static fn ($v) => $v === null || $v === '' ? '—' : format_qty((float) $v);
        $textOrDash = static fn ($v) => $v === null || $v === '' ? '—' : (string) $v;

        $fields = [
            ['product_name', $before['name'], $after['name'], $textOrDash],
            ['category', $beforeCategory, $after['category'], $textOrDash],
            ['unit', $before['unit'], $after['unit'], $textOrDash],
            ['cost_price', (float) $before['cost_price'], $after['cost_price'], $moneyOrDash],
            ['sell_price', (float) $before['sell_price'], $after['sell_price'], $moneyOrDash],
            ['barcode', $before['barcode'] ?? '', $after['barcode'], $textOrDash],
            ['low_stock_threshold_label', $before['low_stock_threshold'], $after['low_stock_threshold'], $qtyOrDash],
            ['pack_size_label', $before['pack_size'], $after['pack_size'], $qtyOrDash],
        ];

        $changes = [];
        foreach ($fields as [$labelKey, $old, $new, $format]) {
            if ($format($old) !== $format($new)) {
                $changes[] = t($labelKey) . ': ' . $format($old) . ' → ' . $format($new);
            }
        }

        if (!empty($changes)) {
            ActivityLog::record($shopId, $userId, 'product_updated', [
                'name' => $name,
                'changes' => implode('; ', $changes),
            ]);
        }
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

        $data = $this->validateVariant($request, $product);
        if ($data === null) {
            redirect("/products/{$id}/edit");
        }

        $isFirstVariant = empty(ProductVariant::activeByProduct((int) $id, $shopId));

        ProductVariant::create([
            'product_id' => (int) $id,
            'shop_id' => $shopId,
            'variant_label' => $data['variant_label'],
            'stock_qty' => $data['stock_qty'],
            'barcode' => $data['barcode'] !== '' ? $data['barcode'] : null,
            'sell_price' => $data['sell_price'],
            'cost_price' => $data['cost_price'],
        ]);

        $label = $product['name'] . ' — ' . $data['variant_label'];
        ActivityLog::record($shopId, (int) Auth::id(), 'product_created', [
            'name' => $label,
            'qty' => format_qty($data['stock_qty']) . ' ' . $product['unit'],
        ]);

        // Once a product has variants it only sells through them, so the
        // stock it held on its own until now would be stranded. The add
        // form pre-fills the first variant's stock with it (and says so);
        // here the product's own copy is cleared so it isn't counted twice.
        if ($isFirstVariant && $request->input('take_parent_stock') === '1' && (float) $product['stock_qty'] > 0) {
            Product::update((int) $id, $shopId, array_merge($product, ['stock_qty' => 0]));
            ActivityLog::record($shopId, (int) Auth::id(), 'product_stock_changed', [
                'name' => $product['name'],
                'old' => format_qty((float) $product['stock_qty']),
                'new' => '0 ' . $product['unit'] . ' (→ ' . $data['variant_label'] . ')',
            ]);
        }

        flash('success', t('variant_created'));
        $this->warnIfBelowCost(
            $data['sell_price'] ?? (float) $product['sell_price'],
            $data['cost_price'] ?? (float) $product['cost_price'],
            $label
        );
        redirect("/products/{$id}/edit");
    }

    public function variantUpdate(Request $request, string $id, string $variantId): void
    {
        $shopId = (int) Auth::shopId();
        $variant = ProductVariant::find((int) $variantId, $shopId);
        $product = Product::find((int) $id, $shopId);

        if (!$variant || !$product || (int) $variant['product_id'] !== (int) $id) {
            flash('error', t('variant_not_found'));
            redirect("/products/{$id}/edit");
        }

        $data = $this->validateVariant($request, $product, $variant);
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

        $label = $product['name'] . ' — ' . $data['variant_label'];
        $userId = (int) Auth::id();
        if (abs((float) $variant['stock_qty'] - $data['stock_qty']) > 0.000001) {
            ActivityLog::record($shopId, $userId, 'product_stock_changed', [
                'name' => $label,
                'old' => format_qty((float) $variant['stock_qty']),
                'new' => format_qty($data['stock_qty']) . ' ' . $product['unit'],
            ]);
        }

        $moneyOrSame = static fn ($v) => $v === null ? t('same_as_product') : money((float) $v);
        $changes = [];
        if ($variant['variant_label'] !== $data['variant_label']) {
            $changes[] = t('variant_label') . ': ' . $variant['variant_label'] . ' → ' . $data['variant_label'];
        }
        foreach (['sell_price', 'cost_price'] as $field) {
            $oldVal = $variant[$field] !== null ? (float) $variant[$field] : null;
            if ($moneyOrSame($oldVal) !== $moneyOrSame($data[$field])) {
                $changes[] = t($field) . ': ' . $moneyOrSame($oldVal) . ' → ' . $moneyOrSame($data[$field]);
            }
        }
        if ((string) ($variant['barcode'] ?? '') !== $data['barcode']) {
            $changes[] = t('barcode') . ': ' . (($variant['barcode'] ?? '') ?: '—') . ' → ' . ($data['barcode'] ?: '—');
        }
        if (!empty($changes)) {
            ActivityLog::record($shopId, $userId, 'product_updated', [
                'name' => $label,
                'changes' => implode('; ', $changes),
            ]);
        }

        flash('success', t('variant_updated'));
        $this->warnIfBelowCost(
            $data['sell_price'] ?? (float) $product['sell_price'],
            $data['cost_price'] ?? (float) $product['cost_price'],
            $label
        );
        redirect("/products/{$id}/edit");
    }

    public function variantToggleStatus(Request $request, string $id, string $variantId): void
    {
        $shopId = (int) Auth::shopId();
        $variant = ProductVariant::find((int) $variantId, $shopId);
        $product = Product::find((int) $id, $shopId);

        if ($variant && $product && (int) $variant['product_id'] === (int) $id) {
            $newStatus = $variant['status'] === 'active' ? 'inactive' : 'active';
            ProductVariant::setStatus((int) $variantId, $shopId, $newStatus);
            ActivityLog::record($shopId, (int) Auth::id(), 'product_status_changed', [
                'name' => $product['name'] . ' — ' . $variant['variant_label'],
                'status' => t($newStatus === 'active' ? 'active_status' : 'inactive_status'),
            ]);
            flash('success', t('variant_status_updated'));
        }

        redirect("/products/{$id}/edit");
    }

    /**
     * @return array{variant_label: string, stock_qty: float, barcode: string, sell_price: ?float, cost_price: ?float}|null
     */
    private function validateVariant(Request $request, array $product, ?array $existingVariant = null): ?array
    {
        $shopId = (int) Auth::shopId();
        $label = trim((string) $request->input('variant_label', ''));
        $stockQty = $request->input('variant_stock_qty', '');
        $barcode = trim((string) $request->input('variant_barcode', ''));
        $sellPriceRaw = trim((string) $request->input('variant_sell_price', ''));

        // Without the "prices" permission the cost field isn't shown, so an
        // edit keeps the variant's existing cost instead of wiping it.
        $costPriceRaw = Auth::can('prices')
            ? trim((string) $request->input('variant_cost_price', ''))
            : ($existingVariant !== null && $existingVariant['cost_price'] !== null ? (string) $existingVariant['cost_price'] : '');

        if ($label === '' || !is_numeric($stockQty) || (float) $stockQty < 0) {
            flash('error', t('fill_required_fields'));
            return null;
        }

        if (looks_like_formula($label) || mb_strlen($label) > 100) {
            flash('error', t('variant_label_invalid'));
            return null;
        }

        if ((float) $stockQty > QTY_MAX) {
            flash('error', t('amount_too_large'));
            return null;
        }

        $legacyStockUnchanged = $existingVariant !== null
            && abs((float) $stockQty - (float) $existingVariant['stock_qty']) < 0.000001;
        if (!$legacyStockUnchanged && !qty_fits_unit((float) $stockQty, $product['unit'])) {
            flash('error', t('qty_must_be_whole', ['unit' => $product['unit']]));
            return null;
        }

        if ($sellPriceRaw !== '' && !valid_money($sellPriceRaw)) {
            flash('error', is_numeric($sellPriceRaw) && (float) $sellPriceRaw > 0 ? t('amount_too_large') : t('values_must_be_positive'));
            return null;
        }

        if ($costPriceRaw !== '' && !valid_money($costPriceRaw)) {
            flash('error', is_numeric($costPriceRaw) && (float) $costPriceRaw > 0 ? t('amount_too_large') : t('values_must_be_positive'));
            return null;
        }

        if ($barcode !== '') {
            if (mb_strlen($barcode) > 64) {
                flash('error', t('barcode_too_long'));
                return null;
            }
            if (Product::barcodeTaken($shopId, $barcode, null, $existingVariant !== null ? (int) $existingVariant['id'] : null)) {
                flash('error', t('barcode_taken', ['barcode' => $barcode]));
                return null;
            }
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
        // The rows come back from the preview page as a hidden field, so they
        // are validated again here with exactly the same rules as the preview
        // — nothing the preview would have skipped can slip through.
        $seenBarcodes = [];

        $pdo = Database::beginImmediate();
        try {
            foreach ($rows as $row) {
                if (!is_array($row)) {
                    continue;
                }

                $clean = $this->validateImportRow(
                    [
                        'name' => (string) ($row['name'] ?? ''),
                        'unit' => (string) ($row['unit'] ?? ''),
                        'cost_price' => (string) ($row['cost_price'] ?? ''),
                        'sell_price' => (string) ($row['sell_price'] ?? ''),
                        'stock_qty' => (string) ($row['stock_qty'] ?? ''),
                        'barcode' => (string) ($row['barcode'] ?? ''),
                    ],
                    $shopId,
                    $seenBarcodes,
                    0
                );
                if (isset($clean['reason'])) {
                    continue;
                }

                $barcode = $clean['barcode'];
                $existing = $barcode !== '' ? Product::findByBarcode($barcode, $shopId) : null;

                if ($existing !== null) {
                    // A plain SET, not a computed increment/decrement — the CSV
                    // gives an absolute stock count the owner wants recorded, so
                    // there is no prior value being read-then-written here (see
                    // Product::update()); it's the same shape the manual edit
                    // form already uses.
                    Product::update((int) $existing['id'], $shopId, [
                        'category_id' => $existing['category_id'],
                        'name' => $clean['name'],
                        'unit' => $clean['unit'],
                        'cost_price' => $clean['cost_price'],
                        'sell_price' => $clean['sell_price'],
                        'stock_qty' => $clean['stock_qty'],
                        'barcode' => $barcode,
                        'low_stock_threshold' => $existing['low_stock_threshold'],
                        'pack_size' => $existing['pack_size'],
                    ]);
                    $updated++;
                } else {
                    Product::create([
                        'shop_id' => $shopId,
                        'category_id' => null,
                        'name' => $clean['name'],
                        'unit' => $clean['unit'],
                        'cost_price' => $clean['cost_price'],
                        'sell_price' => $clean['sell_price'],
                        'stock_qty' => $clean['stock_qty'],
                        'barcode' => $barcode !== '' ? $barcode : null,
                    ]);
                    $created++;
                }
            }

            Database::commit($pdo);
        } catch (\Throwable $e) {
            Database::rollback($pdo);
            log_exception($e);
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
     * One CSV row's validation, shared by the preview and the commit.
     * Returns the cleaned row, or ['reason' => ...] when it must be skipped.
     * $seenBarcodes collects the barcodes of rows accepted so far (barcode =>
     * CSV row number), so a barcode repeated further down the same file is
     * caught instead of silently overwriting the earlier row.
     *
     * @param array{name: string, unit: string, cost_price: string, sell_price: string, stock_qty: string, barcode: string} $raw
     * @param array<string, int> $seenBarcodes
     * @return array{name: string, unit: string, cost_price: float, sell_price: float, stock_qty: float, barcode: string, will_update: bool}|array{reason: string}
     */
    private function validateImportRow(array $raw, int $shopId, array &$seenBarcodes, int $rowNumber): array
    {
        $name = trim($raw['name']);
        $unit = trim($raw['unit']) ?: 'dona';
        $costPrice = trim($raw['cost_price']);
        $sellPrice = trim($raw['sell_price']);
        $stockQty = trim($raw['stock_qty']);
        $barcode = trim($raw['barcode']);

        if ($name === '') {
            return ['reason' => t('import_error_missing_name')];
        }
        if (looks_like_formula($name) || looks_like_formula($unit)) {
            return ['reason' => t('product_name_formula')];
        }
        if (mb_strlen($name) > 200) {
            return ['reason' => t('product_name_too_long')];
        }
        if ($costPrice !== '' && !valid_money($costPrice)) {
            return ['reason' => t('import_error_bad_cost_price')];
        }
        if ($sellPrice !== '' && !valid_money($sellPrice)) {
            return ['reason' => t('import_error_bad_sell_price')];
        }
        if ($stockQty !== '' && (!is_numeric($stockQty) || (float) $stockQty < 0 || (float) $stockQty > QTY_MAX)) {
            return ['reason' => t('import_error_bad_stock_qty')];
        }
        if ($stockQty !== '' && !qty_fits_unit((float) $stockQty, $unit)) {
            return ['reason' => t('qty_must_be_whole', ['unit' => $unit])];
        }

        $willUpdate = false;
        if ($barcode !== '') {
            if (mb_strlen($barcode) > 64 || looks_like_formula($barcode)) {
                return ['reason' => t('barcode_too_long')];
            }
            if (isset($seenBarcodes[$barcode])) {
                return ['reason' => t('import_error_duplicate_barcode', ['barcode' => $barcode, 'row' => $seenBarcodes[$barcode]])];
            }
            $variant = Product::findVariantByBarcode($barcode, $shopId);
            if ($variant !== null) {
                return ['reason' => t('import_error_barcode_on_variant', [
                    'barcode' => $barcode,
                    'name' => $variant['product_name'] . ' — ' . $variant['variant_label'],
                ])];
            }
            $willUpdate = Product::findByBarcode($barcode, $shopId) !== null;
            $seenBarcodes[$barcode] = $rowNumber;
        }

        return [
            'name' => $name,
            'unit' => $unit,
            'cost_price' => $costPrice !== '' ? (float) $costPrice : 0.0,
            'sell_price' => $sellPrice !== '' ? (float) $sellPrice : 0.0,
            'stock_qty' => $stockQty !== '' ? (float) $stockQty : 0.0,
            'barcode' => $barcode,
            'will_update' => $willUpdate,
        ];
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
            // Excel's "CSV UTF-8" puts a BOM in front of the first header.
            static fn ($h) => strtolower(trim(preg_replace('/^\xEF\xBB\xBF/', '', (string) $h))),
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
        $seenBarcodes = [];
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

            $clean = $this->validateImportRow([
                'name' => $get('name'),
                'unit' => $get('unit'),
                'cost_price' => $get('cost_price'),
                'sell_price' => $get('sell_price'),
                'stock_qty' => $get('stock_qty'),
                'barcode' => $get('barcode'),
            ], $shopId, $seenBarcodes, $rowNumber);

            if (isset($clean['reason'])) {
                $skipped[] = ['row' => $rowNumber, 'reason' => $clean['reason']];
                continue;
            }

            $validRows[] = ['row' => $rowNumber] + $clean;
        }

        return [$validRows, $skipped];
    }
}

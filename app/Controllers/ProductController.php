<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Category;
use App\Models\Product;

class ProductController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $search = trim((string) $request->input('q', ''));

        View::render('products/index', [
            'products' => Product::allByShop($shopId, $search),
            'counts' => Product::counts($shopId),
            'search' => $search,
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
     * @return array{name: string, category: string, unit: string, cost_price: float, sell_price: float, stock_qty: float, barcode: string}|null
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

        return [
            'name' => $name,
            'category' => $category,
            'unit' => $unit,
            'cost_price' => (float) $costPrice,
            'sell_price' => (float) $sellPrice,
            'stock_qty' => (float) $stockQty,
            'barcode' => $barcode,
        ];
    }
}

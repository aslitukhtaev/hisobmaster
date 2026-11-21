<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Product;
use App\Models\ProductVariant;
use App\Models\Purchase;
use App\Models\Settings;
use App\Models\Shop;

/**
 * Price labels (shtrix-kodli yorliq): which products, how many of each, the
 * size, what shows on them — then the browser prints them (@page is set to the
 * label size, so a label printer prints one label per page). Needs "products".
 * The defaults and the shop's own sizes are shop settings (label_settings /
 * label_sizes); only the owner changes the defaults.
 */
class LabelController
{
    /** Ready sizes, mm (width × height) — the usual rolls. */
    private const PRESETS = [[58, 40], [43, 25], [40, 30], [30, 20]];

    private const DEFAULTS = [
        'w' => 58, 'h' => 40, 'name' => true, 'price' => true, 'price_mode' => 'retail',
        'digits' => true, 'variant' => true, 'shop' => false,
    ];

    private const MAX_ITEMS = 300;

    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $items = [];

        foreach ($this->ids((string) $request->input('p', '')) as $productId) {
            $product = Product::find($productId, $shopId);
            if ($product !== null) {
                array_push($items, ...$this->itemsOfProduct($shopId, $product));
            }
        }
        foreach ($this->ids((string) $request->input('v', '')) as $variantId) {
            $variant = ProductVariant::find($variantId, $shopId);
            $product = $variant !== null ? Product::find((int) $variant['product_id'], $shopId) : null;
            if ($variant !== null && $product !== null) {
                $items[] = $this->item($product, $variant, null);
            }
        }
        $purchaseId = (int) $request->input('purchase', 0);
        if ($purchaseId > 0 && ($purchase = Purchase::find($purchaseId, $shopId)) !== null) {
            foreach (Purchase::items((int) $purchase['id']) as $line) {
                $product = Product::find((int) $line['product_id'], $shopId);
                if ($product === null) {
                    continue;
                }
                $variant = !empty($line['variant_id']) ? ProductVariant::find((int) $line['variant_id'], $shopId) : null;
                $items[] = $this->item($product, $variant, (int) ceil((float) $line['qty']));
            }
        }

        $settings = $this->settings($shopId);
        $shop = Shop::find($shopId);
        $data = [
            'items' => array_slice($items, 0, self::MAX_ITEMS),
            'settings' => $settings,
            'presets' => self::PRESETS,
            'sizes' => $this->customSizes($shopId),
            'wholesale' => Settings::wholesaleEnabled($shopId),
            'shopName' => (string) ($shop['name'] ?? ''),
            'owner' => Auth::isOwner(),
        ];

        View::render('labels/index', ['labelData' => json_encode($data, JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP)]);
    }

    /** Products for the "add" box on the label page. */
    public function search(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $q = trim((string) $request->input('q', ''));
        $items = [];
        if ($q !== '') {
            foreach (Product::allByShop($shopId, $q, null, 12) as $product) {
                if ($product['status'] === 'active') {
                    array_push($items, ...$this->itemsOfProduct($shopId, $product));
                }
            }
        }
        $this->json(['items' => $items]);
    }

    public function settingsForm(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        View::render('labels/settings', [
            'settings' => $this->settings($shopId),
            'presets' => self::PRESETS,
            'sizes' => $this->customSizes($shopId),
            'wholesale' => Settings::wholesaleEnabled($shopId),
        ]);
    }

    /** The defaults the label page opens with (owner). Also called by the label page's "save as default". */
    public function saveSettings(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $mode = (string) $request->input('price_mode', 'retail');
        $settings = [
            'w' => $this->mm($request->input('w'), self::DEFAULTS['w']),
            'h' => $this->mm($request->input('h'), self::DEFAULTS['h']),
            'name' => $request->input('name') === '1',
            'price' => $request->input('price') === '1',
            'price_mode' => in_array($mode, ['retail', 'wholesale', 'both'], true) ? $mode : 'retail',
            'digits' => $request->input('digits') === '1',
            'variant' => $request->input('variant') === '1',
            'shop' => $request->input('shop') === '1',
        ];
        Settings::set($shopId, 'label_settings', json_encode($settings));

        if ($request->input('ajax') === '1') {
            $this->json(['ok' => true]);
        }
        flash('success', t('label_settings_saved'));
        redirect('/labels/settings');
    }

    /** Adds a size of the shop's own ("Boshqa"). */
    public function saveSize(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $name = mb_substr(trim((string) $request->input('name', '')), 0, 30);
        $w = $this->mm($request->input('w'), 0);
        $h = $this->mm($request->input('h'), 0);
        if ($w === 0 || $h === 0) {
            http_response_code(422);
            $this->json(['error' => 'size']);
        }
        $sizes = $this->customSizes($shopId);
        $sizes[] = ['name' => $name !== '' ? $name : $w . '×' . $h, 'w' => $w, 'h' => $h];
        Settings::set($shopId, 'label_sizes', json_encode(array_slice($sizes, -12)));
        $this->json(['sizes' => $this->customSizes($shopId)]);
    }

    public function deleteSize(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $index = (int) $request->input('index', -1);
        $sizes = $this->customSizes($shopId);
        if (isset($sizes[$index])) {
            array_splice($sizes, $index, 1);
            Settings::set($shopId, 'label_sizes', $sizes !== [] ? json_encode($sizes) : null);
        }
        if ($request->input('ajax') === '1') {
            $this->json(['sizes' => $this->customSizes($shopId)]);
        }
        redirect('/labels/settings');
    }

    // ---------- helpers ----------

    /** @return list<int> */
    private function ids(string $csv): array
    {
        return array_values(array_unique(array_filter(
            array_map('intval', explode(',', $csv)),
            static fn (int $id): bool => $id > 0
        )));
    }

    private function mm(mixed $value, int $default): int
    {
        if (!is_numeric($value)) {
            return $default;
        }
        $mm = (int) round((float) $value);

        return $mm >= 15 && $mm <= 200 ? $mm : $default;
    }

    /** @return array<string, mixed> */
    private function settings(int $shopId): array
    {
        $saved = json_decode((string) Settings::get($shopId, 'label_settings'), true);

        return array_merge(self::DEFAULTS, is_array($saved) ? $saved : []);
    }

    /** @return list<array{name: string, w: int, h: int}> */
    private function customSizes(int $shopId): array
    {
        $saved = json_decode((string) Settings::get($shopId, 'label_sizes'), true);
        $sizes = [];
        foreach (is_array($saved) ? $saved : [] as $row) {
            if (is_array($row) && isset($row['w'], $row['h'])) {
                $sizes[] = ['name' => (string) ($row['name'] ?? ''), 'w' => (int) $row['w'], 'h' => (int) $row['h']];
            }
        }

        return $sizes;
    }

    /** A product, or one item per active variant when it is sold through variants. */
    private function itemsOfProduct(int $shopId, array $product): array
    {
        $variants = ProductVariant::activeByProduct((int) $product['id'], $shopId);
        if ($variants === []) {
            return [$this->item($product, null, null)];
        }

        return array_map(fn (array $variant): array => $this->item($product, $variant, null), $variants);
    }

    private function item(array $product, ?array $variant, ?int $qty): array
    {
        $stock = (float) ($variant !== null ? $variant['stock_qty'] : $product['stock_qty']);
        $wholesale = $variant !== null && ($variant['wholesale_price'] ?? null) !== null
            ? $variant['wholesale_price']
            : ($product['wholesale_price'] ?? null);

        return [
            'product_id' => (int) $product['id'],
            'variant_id' => $variant !== null ? (int) $variant['id'] : null,
            'name' => (string) $product['name'],
            'label' => $variant !== null ? (string) $variant['variant_label'] : '',
            'barcode' => (string) ($variant !== null ? ($variant['barcode'] ?? '') : ($product['barcode'] ?? '')),
            'price' => (float) ($variant !== null && $variant['sell_price'] !== null ? $variant['sell_price'] : $product['sell_price']),
            'wholesale' => $wholesale !== null ? (float) $wholesale : null,
            'stock' => $stock,
            'qty' => $qty ?? (int) min(999, max(1, floor($stock))),
        ];
    }

    private function json(array $data): never
    {
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode($data, JSON_UNESCAPED_UNICODE);
        exit;
    }
}

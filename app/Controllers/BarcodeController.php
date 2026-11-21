<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Models\ActivityLog;
use App\Models\Barcode;

/** Giving products the shop's own barcodes (see App\Models\Barcode). Needs the "products" permission. */
class BarcodeController
{
    /** A fresh code for a form's barcode field (not saved until the form is). */
    public function next(Request $request): void
    {
        $this->json(['code' => Barcode::next((int) Auth::shopId())]);
    }

    /** One product or variant: keeps its code, or gets a new one saved on it. */
    public function assign(Request $request): void
    {
        $variant = (int) $request->input('variant_id', 0);
        $code = Barcode::ensureFor((int) Auth::shopId(), (int) $request->input('product_id', 0), $variant > 0 ? $variant : null);
        if ($code === null) {
            http_response_code(404);
            $this->json(['error' => 'not_found']);
        }
        $this->json(['code' => $code]);
    }

    /** "Hammasiga kod berish": every active product/variant without a barcode (or just the ticked ones). */
    public function generateMissing(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $ids = array_values(array_filter(array_map('intval', (array) $request->input('ids', [])), static fn (int $id): bool => $id > 0));
        $count = Barcode::assignMissing($shopId, $ids !== [] ? $ids : null);

        if ($count > 0) {
            ActivityLog::record($shopId, (int) Auth::id(), 'barcodes_generated', ['count' => (string) $count]);
            flash('success', t('barcodes_generated', ['count' => $count]));
        } else {
            flash('success', t('barcodes_none_missing'));
        }
        redirect('/products');
    }

    private function json(array $data): never
    {
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode($data, JSON_UNESCAPED_UNICODE);
        exit;
    }
}

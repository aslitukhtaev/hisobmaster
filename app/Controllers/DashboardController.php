<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Product;
use App\Models\Settings;
use App\Models\Shop;

class DashboardController
{
    public function index(Request $request): void
    {
        if (Auth::isSuperAdmin()) {
            View::render('superadmin/dashboard', [
                'counts' => Shop::counts(),
                'recentShops' => Shop::recent(5),
            ]);
            return;
        }

        $shopId = Auth::shopId();
        $lowStockCounts = ($shopId && Auth::can('products'))
            ? Product::lowStockCounts((int) $shopId, Settings::lowStockThresholdDefault((int) $shopId))
            : null;

        View::render('dashboard', ['lowStockCounts' => $lowStockCounts]);
    }
}

<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Backup;
use App\Models\Product;
use App\Models\Settings;
use App\Models\Shop;
use App\Models\User;

class DashboardController
{
    public function index(Request $request): void
    {
        if (Auth::isSuperAdmin()) {
            // Opportunistic backup fallback (see App\Models\Backup's class
            // doc comment): best-effort only, so a failure here (e.g. a
            // read-only disk) must never break the dashboard itself.
            if (Backup::shouldRunOpportunistic()) {
                try {
                    Backup::create();
                    flash('success', t('backup_created_opportunistic'));
                } catch (\Throwable $e) {
                    // Silently skipped — the cron script or the next visit
                    // to this page will try again; nothing for the admin
                    // to act on right now.
                }
            }

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

        $user = Auth::user();
        $showOnboarding = $user !== null && empty($user['onboarding_seen_at']);

        View::render('dashboard', [
            'lowStockCounts' => $lowStockCounts,
            'showOnboarding' => $showOnboarding,
        ]);
    }

    public function dismissOnboarding(Request $request): void
    {
        User::markOnboardingSeen((int) Auth::id());
        redirect('/');
    }
}

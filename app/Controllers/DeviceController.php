<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Shop;
use App\Sync\DeviceService;

/**
 * The shop owner's list of computers running the desktop app. Any number of
 * computers may run it; the list is for seeing them and switching off one
 * that was lost or sold.
 */
class DeviceController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $shop = Shop::find($shopId);

        View::render('devices/index', [
            'devices' => DeviceService::allByShop($shopId),
            'offlineDays' => (int) ($shop['offline_days'] ?? 7),
        ]);
    }

    public function revoke(Request $request, string $id): void
    {
        if (DeviceService::revoke((int) $id, (int) Auth::shopId())) {
            flash('success', t('device_revoked_flash'));
        }

        redirect('/devices');
    }
}

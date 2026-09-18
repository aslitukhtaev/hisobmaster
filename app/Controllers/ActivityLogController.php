<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;

class ActivityLogController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        View::render('activity/index', [
            'entries' => ActivityLog::recentByShop($shopId, 150),
        ]);
    }
}

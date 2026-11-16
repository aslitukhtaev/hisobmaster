<?php

declare(strict_types=1);

namespace App\Middleware;

use App\Core\Auth;
use App\Core\Request;
use App\Desktop\Desktop;
use App\Models\Shop;

class AuthMiddleware
{
    /** Still open once the shop's trial / paid period ran out. */
    private const OPEN_WHEN_EXPIRED = ['/subscription', '/logout', '/profile', '/help', '/settings'];

    public static function handle(Request $request): ?string
    {
        if (!Auth::check()) {
            return '/login';
        }

        // The shop computer is stopped by its licence instead (the server
        // refuses to sync, see DeviceService::authenticate()).
        if (Auth::shopId() !== null && !Desktop::enabled() && !in_array($request->path(), self::OPEN_WHEN_EXPIRED, true)
            && Shop::accessExpired(current_shop())) {
            return '/subscription';
        }

        return null;
    }
}

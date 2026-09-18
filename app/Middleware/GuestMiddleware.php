<?php

declare(strict_types=1);

namespace App\Middleware;

use App\Core\Auth;
use App\Core\Request;

class GuestMiddleware
{
    public static function handle(Request $request): ?string
    {
        return Auth::check() ? '/' : null;
    }
}

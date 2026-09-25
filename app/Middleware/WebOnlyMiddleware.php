<?php

declare(strict_types=1);

namespace App\Middleware;

use App\Core\Request;
use App\Desktop\Desktop;

/**
 * Sections that change data only the server owns (employees and invites,
 * passwords, shop settings, the list of computers): in the desktop app they
 * point to the website instead — a change made there reaches this computer
 * with the next sync.
 */
class WebOnlyMiddleware
{
    public static function handle(Request $request): ?string
    {
        return Desktop::enabled() ? '/desktop/web-only' : null;
    }
}

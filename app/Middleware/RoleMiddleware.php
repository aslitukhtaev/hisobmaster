<?php

declare(strict_types=1);

namespace App\Middleware;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;

class RoleMiddleware
{
    public static function handle(Request $request, string $role): ?string
    {
        if (!Auth::check()) {
            return '/login';
        }

        if (Auth::role() !== $role) {
            http_response_code(403);
            View::render('errors/403', [], 'layouts/app');
            exit;
        }

        return null;
    }
}

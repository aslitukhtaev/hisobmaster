<?php

declare(strict_types=1);

namespace App\Middleware;

use App\Core\Request;

class CsrfMiddleware
{
    public static function handle(Request $request): ?string
    {
        if ($request->method() !== 'POST') {
            return null;
        }

        if (csrf_verify($request->input('_csrf'))) {
            return null;
        }

        flash('error', t('csrf_error'));
        return $_SERVER['HTTP_REFERER'] ?? '/';
    }
}

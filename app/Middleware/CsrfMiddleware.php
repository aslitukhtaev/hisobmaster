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

        // A token sent as anything but a string (_csrf[]=…) is simply wrong, not a crash.
        $token = $request->input('_csrf');
        if (is_string($token) && csrf_verify($token)) {
            return null;
        }

        flash('error', t('csrf_error'));
        return $_SERVER['HTTP_REFERER'] ?? '/';
    }
}

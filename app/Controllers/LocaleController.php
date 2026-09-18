<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Database;
use App\Core\Request;

class LocaleController
{
    private const ALLOWED = ['uz', 'ru'];

    public function switch(Request $request, string $lang): void
    {
        if (in_array($lang, self::ALLOWED, true)) {
            $_SESSION['lang'] = $lang;

            if (Auth::check()) {
                Database::connect()
                    ->prepare('UPDATE users SET lang = ? WHERE id = ?')
                    ->execute([$lang, Auth::id()]);
            }
        }

        redirect($_SERVER['HTTP_REFERER'] ?? '/');
    }
}

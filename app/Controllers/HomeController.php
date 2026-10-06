<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Desktop\Desktop;

class HomeController
{
    /**
     * "/": the app's home for a signed-in user, the landing page for a
     * visitor. The desktop app has no visitors — only people about to sign
     * in — and the Telegram WebApp opens /login (TelegramController).
     */
    public function index(Request $request): void
    {
        if (Auth::check()) {
            (new DashboardController())->index($request);
            return;
        }

        if (Desktop::enabled()) {
            redirect('/login');
        }

        View::render('landing/index', [], 'layouts/landing');
    }
}

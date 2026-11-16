<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Desktop\Desktop;
use App\Middleware\AuthMiddleware;

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
            // Not behind the auth middleware (visitors see the landing page),
            // so the expired-trial check is asked for here.
            $gate = AuthMiddleware::handle($request);
            if ($gate !== null) {
                redirect($gate);
            }
            // The desktop app starting up (no page led here) opens the till;
            // the "Bosh sahifa" link inside the app still shows the dashboard.
            if (Desktop::enabled() && empty($_SERVER['HTTP_REFERER']) && start_path() !== '/') {
                redirect(start_path());
            }
            (new DashboardController())->index($request);
            return;
        }

        if (Desktop::enabled()) {
            redirect('/login');
        }

        View::render('landing/index', [], 'layouts/landing');
    }
}

<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;

class DashboardController
{
    public function index(Request $request): void
    {
        View::render('dashboard', ['user' => Auth::user()]);
    }
}

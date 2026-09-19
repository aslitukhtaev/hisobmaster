<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;

class AuthController
{
    public function showLogin(Request $request): void
    {
        View::render('auth/login', [], 'layouts/auth');
    }

    public function login(Request $request): void
    {
        $login = trim((string) $request->input('login', ''));
        $password = (string) $request->input('password', '');

        if ($login === '' || $password === '') {
            flash('error', t('login_required'));
            keep_old(['login' => $login]);
            redirect('/login');
        }

        if (Auth::attempt($login, $password)) {
            $user = Auth::user();
            $_SESSION['lang'] = $user['lang'] ?? env('APP_DEFAULT_LANG', 'uz');
            redirect('/');
        }

        flash('error', Auth::wasLockedOut() ? t('login_locked') : t('login_failed'));
        keep_old(['login' => $login]);
        redirect('/login');
    }

    public function logout(Request $request): void
    {
        Auth::logout();
        redirect('/login');
    }
}

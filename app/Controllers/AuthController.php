<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\RememberMe;
use App\Core\Request;
use App\Core\TelegramAuth;
use App\Core\View;
use App\Desktop\Desktop;
use App\Sync\SyncService;

class AuthController
{
    public function showLogin(Request $request): void
    {
        View::render('auth/login', [
            'telegramAuto' => TelegramAuth::enabled() && !Desktop::enabled(),
        ], 'layouts/auth');
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
            if ($request->input('remember') === '1') {
                RememberMe::issue((int) $user['id']);
            }
            // Signed in inside the Telegram WebApp: next time Telegram alone
            // is enough (see TelegramAuth).
            $telegramUser = TelegramAuth::verify((string) $request->input('tg_init_data', ''));
            if ($telegramUser !== null && !Desktop::enabled()) {
                TelegramAuth::link($telegramUser, (int) $user['id']);
                $_SESSION['telegram_user_id'] = (int) $telegramUser['id'];
            }
            SyncService::pruneJournalQuietly();
            redirect('/');
        }

        flash('error', Auth::wasLockedOut() ? t('login_locked') : t('login_failed'));
        keep_old(['login' => $login]);
        redirect('/login');
    }

    /**
     * The login page opened inside the Telegram WebApp tries this first:
     * a Telegram account linked earlier signs its user in right away.
     */
    public function telegram(Request $request): void
    {
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');

        $telegramUser = Desktop::enabled() ? null : TelegramAuth::verify((string) $request->input('init_data', ''));
        if ($telegramUser === null || !Auth::attemptTelegram($telegramUser)) {
            echo json_encode(['ok' => false]);
            return;
        }

        $_SESSION['lang'] = Auth::user()['lang'] ?? env('APP_DEFAULT_LANG', 'uz');
        echo json_encode(['ok' => true, 'redirect' => '/']);
    }

    public function logout(Request $request): void
    {
        Auth::logout();
        redirect('/login');
    }
}

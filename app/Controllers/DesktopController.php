<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Request;
use App\Core\View;
use App\Desktop\Desktop;
use App\Desktop\DesktopSync;
use App\Desktop\SyncHttpException;
use Throwable;

/** Pages only the desktop app has: activation, "only on the website", sync. */
class DesktopController
{
    private const ACTIVATION_ERRORS = ['offline', 'login_failed', 'login_locked', 'owner_only', 'shop_blocked'];

    public function activateForm(Request $request): void
    {
        $this->desktopOnly();
        if (Desktop::isActivated()) {
            redirect('/');
        }

        View::render('desktop/activate', ['server' => Desktop::server()], 'layouts/auth');
    }

    public function activate(Request $request): void
    {
        $this->desktopOnly();
        if (Desktop::isActivated()) {
            redirect('/');
        }

        $login = trim((string) $request->input('login', ''));
        $password = (string) $request->input('password', '');
        if ($login === '' || $password === '') {
            flash('error', t('login_required'));
            keep_old(['login' => $login]);
            redirect('/desktop/activate');
        }

        try {
            DesktopSync::activate($login, $password, (string) env('DESKTOP_COMPUTER_NAME', ''));
        } catch (SyncHttpException $e) {
            $code = in_array($e->errorCode, self::ACTIVATION_ERRORS, true) ? $e->errorCode : 'generic';
            flash('error', t('desktop_error_' . $code));
            keep_old(['login' => $login]);
            redirect('/desktop/activate');
        } catch (Throwable $e) {
            log_exception($e);
            flash('error', t('desktop_error_generic'));
            keep_old(['login' => $login]);
            redirect('/desktop/activate');
        }

        flash('success', t('desktop_activated'));
        redirect('/login');
    }

    public function webOnly(Request $request): void
    {
        $this->desktopOnly();
        View::render('desktop/web-only', ['server' => Desktop::server()]);
    }

    /** The "Sinxronlash" button: syncs now and comes back. */
    public function syncNow(Request $request): void
    {
        $this->desktopOnly();
        $result = DesktopSync::run();

        if ($result['ok']) {
            flash('success', t('desktop_sync_ok'));
        } elseif ($result['error'] !== 'busy') {
            flash('error', t('desktop_sync_failed', ['reason' => t('desktop_error_' . (in_array($result['error'], ['offline', 'device_revoked', 'shop_blocked'], true) ? $result['error'] : 'generic'))]));
        }

        $back = parse_url((string) ($_SERVER['HTTP_REFERER'] ?? '/'), PHP_URL_PATH) ?: '/';
        redirect($back);
    }

    /** For the shell and the header indicator: no secrets, only state. */
    public function status(Request $request): void
    {
        $this->desktopOnly();
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode(Desktop::status(), JSON_UNESCAPED_UNICODE);
    }

    private function desktopOnly(): void
    {
        if (!Desktop::enabled()) {
            abort_404();
        }
    }
}

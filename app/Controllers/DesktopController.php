<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Desktop\Desktop;
use App\Desktop\DesktopSync;
use App\Desktop\SyncHttpException;
use App\Models\Shop;
use App\Sync\CodePackage;
use Throwable;

/** Pages only the desktop app has: activation, "only on the website", sync, printer. */
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

    /** Just the status bar, re-fetched by the page when a background sync finishes. */
    public function statusBar(Request $request): void
    {
        $this->desktopOnly();
        header('Cache-Control: no-store');
        View::render('desktop/_status-bar', [], null);
    }

    /** For the shell and the header indicator: no secrets, only state. */
    public function status(Request $request): void
    {
        $this->desktopOnly();
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode(Desktop::status() + ['code_version' => CodePackage::version(BASE_PATH)], JSON_UNESCAPED_UNICODE);
    }

    /** This computer's receipt printer — the choice itself is kept by the shell. */
    public function printer(Request $request): void
    {
        $this->desktopOnly();
        $shop = Shop::find((int) Auth::shopId());
        View::render('desktop/printer', ['paperWidth' => (int) ($shop['receipt_printer_width'] ?? 80)]);
    }

    /** The sample receipt "Sinov cheki" prints. */
    public function printerTest(Request $request): void
    {
        $this->desktopOnly();
        $shop = Shop::find((int) Auth::shopId());
        header('Cache-Control: no-store');
        View::render('desktop/printer-test', [
            'shop' => $shop,
            'paperWidth' => (int) ($shop['receipt_printer_width'] ?? 80),
            'deviceCode' => Desktop::get('device_code'),
        ], 'layouts/receipt-print');
    }

    private function desktopOnly(): void
    {
        if (!Desktop::enabled()) {
            abort_404();
        }
    }
}

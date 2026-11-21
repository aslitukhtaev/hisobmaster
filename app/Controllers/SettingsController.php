<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Desktop\Desktop;

/**
 * "Sozlamalar": one page linking everything that is set up once or opened
 * rarely, so the side menu holds only the daily work (see layouts/app.php).
 */
class SettingsController
{
    public function index(Request $request): void
    {
        $desktop = Desktop::enabled();
        $items = [
            ['href' => '/profile', 'icon' => 'user', 'title' => t('profile'), 'hint' => t('settings_profile_hint')],
        ];

        if (!Auth::isSuperAdmin()) {
            if (Auth::isOwner()) {
                // Employees and computers are managed on the website (see
                // WebOnlyMiddleware); the desktop app has attendance only.
                $items[] = $desktop
                    ? ['href' => '/employees/attendance', 'icon' => 'userplus', 'title' => t('attendance_history_title'), 'hint' => t('settings_attendance_hint')]
                    : ['href' => '/employees', 'icon' => 'userplus', 'title' => t('employees'), 'hint' => t('settings_employees_hint')];
            }
            if (Auth::can('products')) {
                $items[] = ['href' => '/suppliers', 'icon' => 'truck', 'title' => t('suppliers'), 'hint' => t('settings_suppliers_hint')];
            }
            if (Auth::isOwner()) {
                $items[] = ['href' => '/labels/settings', 'icon' => 'printer', 'title' => t('label_settings_title'), 'hint' => t('label_settings_short')];
                $items[] = ['href' => '/activity', 'icon' => 'clock', 'title' => t('activity_log'), 'hint' => t('settings_activity_hint')];
                if (!$desktop) {
                    $items[] = ['href' => '/devices', 'icon' => 'monitor', 'title' => t('devices_title'), 'hint' => t('settings_devices_hint')];
                }
            }
            if ($desktop) {
                $items[] = ['href' => '/desktop/printer', 'icon' => 'printer', 'title' => t('printer_nav'), 'hint' => t('settings_printer_hint')];
            }
        }

        $items[] = ['href' => '/help', 'icon' => 'help', 'title' => t('help_nav'), 'hint' => t('settings_help_hint')];

        View::render('settings/index', ['items' => $items]);
    }
}

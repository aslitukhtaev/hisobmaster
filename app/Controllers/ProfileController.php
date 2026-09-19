<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Settings;
use App\Models\Shop;
use App\Models\User;

class ProfileController
{
    public function show(Request $request): void
    {
        $shop = Auth::isOwner() ? Shop::find((int) Auth::shopId()) : null;
        $lowStockThresholdDefault = $shop ? Settings::lowStockThresholdDefault((int) Auth::shopId()) : null;

        View::render('profile/show', ['shop' => $shop, 'lowStockThresholdDefault' => $lowStockThresholdDefault]);
    }

    public function update(Request $request): void
    {
        $user = Auth::user();
        $userId = (int) $user['id'];

        $fullName = trim((string) $request->input('full_name', ''));
        $phone = trim((string) $request->input('phone', ''));
        $login = trim((string) $request->input('login', ''));

        $old = ['full_name' => $fullName, 'phone' => $phone, 'login' => $login];

        if ($fullName === '' || $login === '') {
            flash('error', t('fill_required_fields'));
            keep_old($old);
            redirect('/profile');
        }

        if (User::loginExists($login, $userId)) {
            flash('error', t('login_taken'));
            keep_old($old);
            redirect('/profile');
        }

        $newPassword = (string) $request->input('new_password', '');

        if ($newPassword !== '') {
            $currentPassword = (string) $request->input('current_password', '');
            $confirmPassword = (string) $request->input('confirm_password', '');

            if (!password_verify($currentPassword, $user['password_hash'])) {
                flash('error', t('current_password_wrong'));
                keep_old($old);
                redirect('/profile');
            }

            if (strlen($newPassword) < 6) {
                flash('error', t('password_too_short'));
                keep_old($old);
                redirect('/profile');
            }

            if ($newPassword !== $confirmPassword) {
                flash('error', t('passwords_not_match'));
                keep_old($old);
                redirect('/profile');
            }

            User::updatePassword($userId, $newPassword);
        }

        User::updateProfile($userId, [
            'full_name' => $fullName,
            'phone' => $phone !== '' ? $phone : null,
            'login' => $login,
        ]);

        flash('success', t('profile_updated'));
        redirect('/profile');
    }

    public function updateShop(Request $request): void
    {
        if (!Auth::isOwner()) {
            flash('error', t('error_403_title'));
            redirect('/profile');
        }

        $shopId = (int) Auth::shopId();
        $name = trim((string) $request->input('shop_name', ''));
        $address = trim((string) $request->input('shop_address', ''));
        $printerWidth = (int) $request->input('receipt_printer_width', 80);
        $lowStockDefaultRaw = trim((string) $request->input('low_stock_threshold_default', ''));

        if ($name === '') {
            flash('error', t('fill_required_fields'));
            redirect('/profile');
        }

        if ($lowStockDefaultRaw !== '' && (!is_numeric($lowStockDefaultRaw) || (float) $lowStockDefaultRaw < 0)) {
            flash('error', t('values_must_be_positive'));
            redirect('/profile');
        }

        if (!in_array($printerWidth, [58, 80], true)) {
            $printerWidth = 80;
        }

        Shop::updateSettings($shopId, $name, $address !== '' ? $address : null, $printerWidth);
        Settings::set($shopId, 'low_stock_threshold_default', $lowStockDefaultRaw !== '' ? $lowStockDefaultRaw : null);

        flash('success', t('shop_settings_updated'));
        redirect('/profile');
    }
}

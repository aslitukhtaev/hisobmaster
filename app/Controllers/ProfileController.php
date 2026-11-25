<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\RememberMe;
use App\Core\Request;
use App\Core\SocialAuth;
use App\Core\TelegramAuth;
use App\Core\View;
use App\Desktop\Desktop;
use App\Desktop\DesktopSync;
use App\Desktop\SyncHttpException;
use App\Models\Settings;
use App\Models\Shop;
use App\Models\ShopProfile;
use App\Models\User;

class ProfileController
{
    public function show(Request $request): void
    {
        $shop = Auth::isOwner() ? Shop::find((int) Auth::shopId()) : null;
        $lowStockThresholdDefault = $shop ? Settings::lowStockThresholdDefault((int) Auth::shopId()) : null;

        $userId = (int) Auth::id();
        View::render('profile/show', [
            'shop' => $shop,
            'lowStockThresholdDefault' => $lowStockThresholdDefault,
            'receiptLogo' => $shop ? Settings::receiptImage((int) Auth::shopId(), 'receipt_logo') : null,
            'receiptQr' => $shop ? Settings::receiptImage((int) Auth::shopId(), 'receipt_qr') : null,
            'wholesaleEnabled' => $shop ? Settings::wholesaleEnabled((int) Auth::shopId()) : false,
            // Where this account stays signed in (website only: the desktop
            // app has its own, local sign-ins).
            'devices' => Desktop::enabled() ? null : RememberMe::forUser($userId),
            'telegramLinks' => Desktop::enabled() ? null : TelegramAuth::forUser($userId),
            'currentSelector' => $_SESSION['remember_selector'] ?? null,
            'currentTelegramId' => $_SESSION['telegram_user_id'] ?? null,
            'identities' => Desktop::enabled() ? null : SocialAuth::forUser($userId),
            'googleEnabled' => SocialAuth::googleEnabled(),
            'telegramEnabled' => SocialAuth::telegramEnabled(),
        ]);
    }

    /** "Boshqa qurilmalardan chiqish": every other device must sign in again. */
    public function signOutOthers(Request $request): void
    {
        Auth::signOutEverywhere((int) Auth::id(), keepCurrent: true);
        flash('success', t('devices_signed_out_others'));
        redirect('/profile#devices');
    }

    public function removeDevice(Request $request, string $id): void
    {
        RememberMe::remove((int) $id, (int) Auth::id());
        flash('success', t('device_removed'));
        redirect('/profile#devices');
    }

    public function removeIdentity(Request $request, string $id): void
    {
        $user = Auth::user();
        // The last way in of an owner who never set a password stays.
        if ((int) ($user['password_set'] ?? 1) === 0 && count(SocialAuth::forUser((int) $user['id'])) <= 1) {
            flash('error', t('password_set_first'));
            redirect('/profile#social');
        }
        SocialAuth::remove((int) $id, (int) Auth::id());
        flash('success', t('social_removed'));
        redirect('/profile#social');
    }

    public function removeTelegram(Request $request, string $id): void
    {
        TelegramAuth::remove((int) $id, (int) Auth::id());
        flash('success', t('device_removed'));
        redirect('/profile#devices');
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

        $normalizedPhone = normalize_phone($phone);
        if ($normalizedPhone === null) {
            flash('error', t('phone_invalid'));
            keep_old($old);
            redirect('/profile');
        }
        $phone = $normalizedPhone;

        if (User::loginExists($login, $userId)) {
            flash('error', t('login_taken'));
            keep_old($old);
            redirect('/profile');
        }

        $newPassword = (string) $request->input('new_password', '');

        if ($newPassword !== '') {
            $currentPassword = (string) $request->input('current_password', '');
            $confirmPassword = (string) $request->input('confirm_password', '');

            // Signed up with Google / Telegram: there's no password they know yet.
            $hasPassword = (int) ($user['password_set'] ?? 1) === 1;
            if ($hasPassword && !password_verify($currentPassword, $user['password_hash'])) {
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
            // A new password signs every other device out.
            Auth::signOutEverywhere($userId, keepCurrent: true);
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

        $input = [
            'shop_name' => (string) $request->input('shop_name', ''),
            'shop_address' => (string) $request->input('shop_address', ''),
            'receipt_printer_width' => (string) $request->input('receipt_printer_width', '80'),
            'low_stock_threshold_default' => (string) $request->input('low_stock_threshold_default', ''),
            'wholesale_enabled' => $request->input('wholesale_enabled') === '1' ? '1' : '',
        ];
        $images = [];
        foreach (array_keys(ShopProfile::IMAGES) as $key) {
            $input['remove_' . $key] = $request->input('remove_' . $key) === '1' ? '1' : '';
            $file = $request->file($key);
            if ($file === null) {
                continue;
            }
            if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK || (int) $file['size'] > ShopProfile::MAX_UPLOAD_BYTES
                || !is_uploaded_file((string) $file['tmp_name'])) {
                flash('error', t('receipt_image_invalid'));
                redirect('/profile');
            }
            $images[$key] = (string) file_get_contents((string) $file['tmp_name']);
        }

        // The desktop app: the server keeps the shop's settings (and makes the
        // pictures — no image library here); the local copy is replaced by the
        // server's at every sync, so they are saved there and brought back.
        if (Desktop::enabled()) {
            try {
                $response = DesktopSync::request('/api/device/shop-settings', $input + ['images' => array_map('base64_encode', $images)]);
                DesktopSync::applyServerShopState($response);
            } catch (SyncHttpException $e) {
                $code = $e->getMessage();
                flash('error', in_array($code, ['fill_required_fields', 'values_must_be_positive', 'receipt_image_invalid'], true)
                    ? t($code)
                    : ($code === 'offline' ? t('shop_settings_need_internet') : t('shop_settings_sync_failed', ['code' => $code])));
                redirect('/profile');
            }
            flash('success', t('shop_settings_updated'));
            redirect('/profile');
        }

        $error = ShopProfile::save((int) Auth::shopId(), $input, $images);
        if ($error !== null) {
            flash('error', t($error));
            redirect('/profile');
        }

        flash('success', t('shop_settings_updated'));
        redirect('/profile');
    }
}

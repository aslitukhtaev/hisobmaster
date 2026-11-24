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
use App\Models\Settings;
use App\Models\Shop;
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
        Settings::set($shopId, 'wholesale_enabled', $request->input('wholesale_enabled') === '1' ? '1' : null);

        foreach (['receipt_logo' => 400, 'receipt_qr' => 320] as $key => $maxSide) {
            if ($request->input('remove_' . $key) === '1') {
                Settings::set($shopId, $key, null);
            }
            $file = $request->file($key);
            if ($file === null) {
                continue;
            }
            $png = self::receiptImagePng($file, $maxSide);
            if ($png === null) {
                flash('error', t('receipt_image_invalid'));
                redirect('/profile');
            }
            Settings::set($shopId, $key, 'data:image/png;base64,' . base64_encode($png));
        }

        flash('success', t('shop_settings_updated'));
        redirect('/profile');
    }

    /**
     * An uploaded picture as a small black-and-white-friendly PNG that fits
     * $maxSide px: thermal paper prints it as is. Null for anything that is
     * not a real, reasonably small image.
     */
    private static function receiptImagePng(array $file, int $maxSide): ?string
    {
        if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK || (int) $file['size'] > 3 * 1024 * 1024
            || !is_uploaded_file((string) $file['tmp_name']) || !function_exists('imagecreatefromstring')) {
            return null;
        }
        $info = @getimagesize((string) $file['tmp_name']);
        if ($info === false || !in_array($info[2], [IMAGETYPE_PNG, IMAGETYPE_JPEG, IMAGETYPE_GIF, IMAGETYPE_WEBP], true)
            || $info[0] < 1 || $info[1] < 1 || $info[0] * $info[1] > 25000000) {
            return null;
        }
        $src = @imagecreatefromstring((string) file_get_contents((string) $file['tmp_name']));
        if ($src === false) {
            return null;
        }

        $w = imagesx($src);
        $h = imagesy($src);
        $scale = min(1.0, $maxSide / max($w, $h));
        $nw = max(1, (int) round($w * $scale));
        $nh = max(1, (int) round($h * $scale));

        // Flatten onto white (transparent logos would print black) and go grayscale.
        $dst = imagecreatetruecolor($nw, $nh);
        imagefill($dst, 0, 0, imagecolorallocate($dst, 255, 255, 255));
        imagecopyresampled($dst, $src, 0, 0, 0, 0, $nw, $nh, $w, $h);
        imagefilter($dst, IMG_FILTER_GRAYSCALE);

        ob_start();
        imagepng($dst, null, 9);
        $png = (string) ob_get_clean();

        return strlen($png) <= 300 * 1024 ? $png : null;
    }
}

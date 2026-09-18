<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Request;
use App\Core\View;
use App\Models\Shop;
use App\Models\User;

class SuperAdminController
{
    public function shops(Request $request): void
    {
        View::render('superadmin/shops/index', ['shops' => Shop::all()]);
    }

    public function downloadBackup(Request $request): void
    {
        $path = BASE_PATH . '/' . env('DB_PATH', 'database/hisobmaster.db');

        if (!is_file($path)) {
            flash('error', t('backup_not_found'));
            redirect('/superadmin/shops');
        }

        $filename = 'hisobmaster-backup-' . date('Y-m-d-His') . '.db';

        header('Content-Type: application/octet-stream');
        header('Content-Disposition: attachment; filename="' . $filename . '"');
        header('Content-Length: ' . filesize($path));
        header('Cache-Control: no-store');
        readfile($path);
        exit;
    }

    public function createForm(Request $request): void
    {
        View::render('superadmin/shops/create');
    }

    public function store(Request $request): void
    {
        $name = trim((string) $request->input('name', ''));
        $ownerName = trim((string) $request->input('owner_full_name', ''));
        $phone = trim((string) $request->input('phone', ''));
        $address = trim((string) $request->input('address', ''));

        $old = ['name' => $name, 'owner_full_name' => $ownerName, 'phone' => $phone, 'address' => $address];

        if ($name === '' || $ownerName === '' || $phone === '') {
            flash('error', t('fill_required_fields'));
            keep_old($old);
            redirect('/superadmin/shops/create');
        }

        $shopId = Shop::create([
            'name' => $name,
            'owner_full_name' => $ownerName,
            'phone' => $phone,
            'address' => $address !== '' ? $address : null,
        ]);

        $login = 'dokon' . $shopId;
        $password = $this->generatePassword();

        User::create([
            'shop_id' => $shopId,
            'role' => 'owner',
            'full_name' => $ownerName,
            'phone' => $phone,
            'login' => $login,
            'password' => $password,
            'lang' => env('APP_DEFAULT_LANG', 'uz'),
        ]);

        $_SESSION['new_credentials'] = ['shop_name' => $name, 'login' => $login, 'password' => $password];
        redirect("/superadmin/shops/{$shopId}/created");
    }

    public function created(Request $request, string $id): void
    {
        $creds = $_SESSION['new_credentials'] ?? null;
        unset($_SESSION['new_credentials']);

        if (!$creds) {
            redirect('/superadmin/shops');
        }

        View::render('superadmin/shops/created', ['creds' => $creds]);
    }

    public function toggleStatus(Request $request, string $id): void
    {
        $shop = Shop::find((int) $id);

        if ($shop) {
            $newStatus = $shop['status'] === 'active' ? 'blocked' : 'active';
            Shop::setStatus((int) $id, $newStatus);
            flash('success', t('shop_status_updated'));
        }

        redirect('/superadmin/shops');
    }

    public function resetPassword(Request $request, string $id): void
    {
        $owner = User::ownerByShop((int) $id);

        if (!$owner) {
            flash('error', t('owner_not_found'));
            redirect('/superadmin/shops');
        }

        $password = $this->generatePassword();
        User::updatePassword((int) $owner['id'], $password);

        $shop = Shop::find((int) $id);
        $_SESSION['new_credentials'] = [
            'shop_name' => $shop['name'] ?? '',
            'login' => $owner['login'],
            'password' => $password,
        ];

        redirect("/superadmin/shops/{$id}/created");
    }

    private function generatePassword(int $length = 8): string
    {
        $chars = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
        $password = '';

        for ($i = 0; $i < $length; $i++) {
            $password .= $chars[random_int(0, strlen($chars) - 1)];
        }

        return $password;
    }
}

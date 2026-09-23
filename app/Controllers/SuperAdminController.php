<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Request;
use App\Core\View;
use App\Models\Backup;
use App\Models\Report;
use App\Models\Shop;
use App\Models\User;

class SuperAdminController
{
    public function shops(Request $request): void
    {
        View::render('superadmin/shops/index', ['shops' => Shop::all()]);
    }

    /**
     * Cross-shop consolidated report: total revenue/net-profit/sales-count
     * across every shop for the selected period, plus a per-shop breakdown.
     * Built on Report::allShopsSummary()/perShopSummary() — new methods that
     * aggregate across shops rather than filtering to one shop_id — so it
     * never touches the single-shop-scoped methods every other report page
     * relies on. Gated by the 'role:super_admin' middleware in routes.php,
     * same as every other /superadmin/* route.
     */
    public function reports(Request $request): void
    {
        $period = (string) $request->input('period', 'month');
        $customFrom = (string) $request->input('from', '');
        $customTo = (string) $request->input('to', '');

        [$from, $to, $period] = Report::resolvePeriod($period, $customFrom, $customTo);

        $allShops = Report::allShopsSummary($from, $to);

        View::render('superadmin/reports', [
            'totals' => $allShops['totals'],
            'byShop' => $allShops['by_shop'],
            'period' => $period,
            'from' => $from,
            'to' => $to,
        ]);
    }

    public function downloadBackup(Request $request): void
    {
        $path = BASE_PATH . '/' . env('DB_PATH', 'database/kassiron.db');

        if (!is_file($path)) {
            flash('error', t('backup_not_found'));
            redirect('/superadmin/shops');
        }

        $filename = 'kassiron-backup-' . date('Y-m-d-His') . '.db';

        header('Content-Type: application/octet-stream');
        header('Content-Disposition: attachment; filename="' . $filename . '"');
        header('Content-Length: ' . filesize($path));
        header('Cache-Control: no-store');
        readfile($path);
        exit;
    }

    /**
     * The backups list/manage page. See App\Models\Backup's class doc
     * comment for the honest distinction between the cron script and the
     * opportunistic dashboard fallback — this page states the same thing
     * to the super admin rather than calling either one "automatic".
     */
    public function backups(Request $request): void
    {
        View::render('superadmin/backups', ['backups' => Backup::list()]);
    }

    public function createBackup(Request $request): void
    {
        Backup::create();
        flash('success', t('backup_created'));
        redirect('/superadmin/backups');
    }

    public function downloadBackupFile(Request $request, string $filename): void
    {
        $path = Backup::path($filename);

        if ($path === null) {
            flash('error', t('backup_not_found'));
            redirect('/superadmin/backups');
        }

        header('Content-Type: application/octet-stream');
        header('Content-Disposition: attachment; filename="' . $filename . '"');
        header('Content-Length: ' . filesize($path));
        header('Cache-Control: no-store');
        readfile($path);
        exit;
    }

    public function deleteBackupFile(Request $request, string $filename): void
    {
        if (Backup::delete($filename)) {
            flash('success', t('backup_deleted'));
        } else {
            flash('error', t('backup_not_found'));
        }

        redirect('/superadmin/backups');
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

        $normalizedPhone = normalize_phone($phone);
        if ($normalizedPhone === null || $normalizedPhone === '') {
            flash('error', t('phone_invalid'));
            keep_old($old);
            redirect('/superadmin/shops/create');
        }
        $phone = $normalizedPhone;

        $shopId = Shop::create([
            'name' => $name,
            'owner_full_name' => $ownerName,
            'phone' => $phone,
            'address' => $address !== '' ? $address : null,
        ]);

        $login = $this->generateLogin($ownerName !== '' ? $ownerName : $name);
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

    /**
     * Do'kon egasi uchun login: manba matn (F.I.Sh yoki do'kon nomi) asosidagi qisqa slug
     * + 4 xonali tasodifiy raqam. Sequential ID'ga bog'liq emas, shuning uchun keyingi
     * do'konlarning loginini oldindan taxmin qilib bo'lmaydi. Band bo'lsa, raqam qismini
     * qayta generatsiya qilib, bir necha marta urinib ko'ramiz.
     */
    private function generateLogin(string $source): string
    {
        $base = $this->slugify($source);

        if ($base === '') {
            $base = 'dokon';
        }

        $base = substr($base, 0, 20);

        for ($attempt = 0; $attempt < 10; $attempt++) {
            $candidate = $base . random_int(1000, 9999);

            if (!User::loginExists($candidate)) {
                return $candidate;
            }
        }

        // Juda kam ehtimol bilan 10 marta ham band chiqsa, kengroq tasodifiy qo'shimcha bilan yakunlaymiz.
        return $base . bin2hex(random_bytes(4));
    }

    /**
     * Kirill/o'zbekcha maxsus harflarni lotin/ASCII'ga o'giradi va faqat [a-z0-9] qoldiradi.
     */
    private function slugify(string $text): string
    {
        $map = [
            'а' => 'a', 'б' => 'b', 'в' => 'v', 'г' => 'g', 'д' => 'd', 'е' => 'e', 'ё' => 'e',
            'ж' => 'zh', 'з' => 'z', 'и' => 'i', 'й' => 'y', 'к' => 'k', 'л' => 'l', 'м' => 'm',
            'н' => 'n', 'о' => 'o', 'п' => 'p', 'р' => 'r', 'с' => 's', 'т' => 't', 'у' => 'u',
            'ф' => 'f', 'х' => 'h', 'ц' => 'ts', 'ч' => 'ch', 'ш' => 'sh', 'щ' => 'sch', 'ъ' => '',
            'ы' => 'y', 'ь' => '', 'э' => 'e', 'ю' => 'yu', 'я' => 'ya',
            'ў' => 'o', 'қ' => 'q', 'ғ' => 'g', 'ҳ' => 'h',
        ];

        $text = mb_strtolower($text, 'UTF-8');
        $text = strtr($text, $map);

        return preg_replace('/[^a-z0-9]+/', '', $text) ?? '';
    }
}

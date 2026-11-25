<?php

declare(strict_types=1);

namespace App\Models;

/**
 * "Do'kon sozlamalari" (Profil): the shop's name, address, receipt width,
 * receipt logo and QR, low-stock default, optom selling. Saved here on the
 * server — from the website's form, or sent by the desktop app
 * (POST /api/device/shop-settings), which has no image library of its own
 * and whose local copy is replaced by the server's at every sync.
 */
final class ShopProfile
{
    /** Receipt pictures and the longest side each is kept at (px). */
    public const IMAGES = ['receipt_logo' => 400, 'receipt_qr' => 320];

    public const MAX_UPLOAD_BYTES = 3 * 1024 * 1024;

    /**
     * Saves the form. $input: shop_name, shop_address, receipt_printer_width,
     * low_stock_threshold_default, wholesale_enabled ("1"), remove_receipt_logo /
     * remove_receipt_qr ("1"). $images: picture key => the uploaded file's bytes.
     * Returns null, or the lang key of what is wrong (nothing is saved then).
     */
    public static function save(int $shopId, array $input, array $images): ?string
    {
        $name = trim((string) ($input['shop_name'] ?? ''));
        $address = trim((string) ($input['shop_address'] ?? ''));
        $printerWidth = (int) ($input['receipt_printer_width'] ?? 80);
        $lowStock = trim((string) ($input['low_stock_threshold_default'] ?? ''));

        if ($name === '') {
            return 'fill_required_fields';
        }
        if ($lowStock !== '' && (!is_numeric($lowStock) || (float) $lowStock < 0)) {
            return 'values_must_be_positive';
        }
        if (!in_array($printerWidth, [58, 80], true)) {
            $printerWidth = 80;
        }

        // Pictures first: a bad one stops everything before anything changes.
        $pngs = [];
        foreach (self::IMAGES as $key => $maxSide) {
            if (!isset($images[$key])) {
                continue;
            }
            $png = self::receiptImagePng((string) $images[$key], $maxSide, $key === 'receipt_qr');
            if ($png === null) {
                return 'receipt_image_invalid';
            }
            $pngs[$key] = $png;
        }

        Shop::updateSettings($shopId, mb_substr($name, 0, 255), $address !== '' ? mb_substr($address, 0, 500) : null, $printerWidth);
        Settings::set($shopId, 'low_stock_threshold_default', $lowStock !== '' ? $lowStock : null);
        Settings::set($shopId, 'wholesale_enabled', (string) ($input['wholesale_enabled'] ?? '') === '1' ? '1' : null);

        foreach (array_keys(self::IMAGES) as $key) {
            if (isset($pngs[$key])) {
                Settings::set($shopId, $key, 'data:image/png;base64,' . base64_encode($pngs[$key]));
            } elseif ((string) ($input['remove_' . $key] ?? '') === '1') {
                Settings::set($shopId, $key, null);
            }
        }

        return null;
    }

    /**
     * A picture as a small black-and-white PNG — what a thermal printer can
     * actually make, so the receipt on screen is the receipt on paper — fitting
     * $maxSide px, flattened onto white (a transparent logo would print black).
     * Every pixel ends up black or white: a QR code split at mid-grey (sharp
     * modules); a logo at the level that best separates it from its background
     * (Otsu), so coloured parts come out solid black and small lettering stays
     * readable, instead of the printer's own blotchy grey.
     * Null for anything that is not a real, reasonably small image.
     */
    public static function receiptImagePng(string $bytes, int $maxSide, bool $qr = false): ?string
    {
        if ($bytes === '' || strlen($bytes) > self::MAX_UPLOAD_BYTES || !function_exists('imagecreatefromstring')) {
            return null;
        }
        $info = @getimagesizefromstring($bytes);
        if ($info === false || !in_array($info[2], [IMAGETYPE_PNG, IMAGETYPE_JPEG, IMAGETYPE_GIF, IMAGETYPE_WEBP], true)
            || $info[0] < 1 || $info[1] < 1 || $info[0] * $info[1] > 25000000) {
            return null;
        }
        $src = @imagecreatefromstring($bytes);
        if ($src === false) {
            return null;
        }

        $w = imagesx($src);
        $h = imagesy($src);
        $scale = min(1.0, $maxSide / max($w, $h));
        $nw = max(1, (int) round($w * $scale));
        $nh = max(1, (int) round($h * $scale));

        $dst = imagecreatetruecolor($nw, $nh);
        imagefill($dst, 0, 0, imagecolorallocate($dst, 255, 255, 255));
        imagecopyresampled($dst, $src, 0, 0, 0, 0, $nw, $nh, $w, $h);
        imagefilter($dst, IMG_FILTER_GRAYSCALE);

        // Grey levels (after the filter red = green = blue), then 1 bit per pixel.
        $grey = [];
        $histogram = array_fill(0, 256, 0);
        for ($y = 0; $y < $nh; $y++) {
            for ($x = 0; $x < $nw; $x++) {
                $level = imagecolorat($dst, $x, $y) >> 16 & 0xFF;
                $grey[$y * $nw + $x] = $level;
                $histogram[$level]++;
            }
        }
        $threshold = $qr ? 128 : self::otsu($histogram, $nw * $nh);
        $bw = imagecreate($nw, $nh);
        imagecolorallocate($bw, 255, 255, 255); // the first colour: the background
        $black = imagecolorallocate($bw, 0, 0, 0);
        foreach ($grey as $i => $level) {
            if ($level < $threshold) {
                imagesetpixel($bw, $i % $nw, intdiv($i, $nw), $black);
            }
        }
        $dst = $bw;

        ob_start();
        imagepng($dst, null, 9);
        $png = (string) ob_get_clean();

        return strlen($png) <= 300 * 1024 ? $png : null;
    }

    /**
     * The grey level that best splits a picture in two (Otsu's method), kept
     * between 40 and 240 so a plain white or plain dark picture stays itself.
     *
     * @param list<int> $histogram pixels per grey level 0..255
     */
    private static function otsu(array $histogram, int $total): int
    {
        $sum = 0.0;
        foreach ($histogram as $level => $count) {
            $sum += $level * $count;
        }
        $sumBack = 0.0;
        $weightBack = 0;
        $best = 0.0;
        $threshold = 128;
        foreach ($histogram as $level => $count) {
            $weightBack += $count;
            if ($weightBack === 0) {
                continue;
            }
            $weightFore = $total - $weightBack;
            if ($weightFore === 0) {
                break;
            }
            $sumBack += $level * $count;
            $meanBack = $sumBack / $weightBack;
            $meanFore = ($sum - $sumBack) / $weightFore;
            $between = $weightBack * $weightFore * ($meanBack - $meanFore) ** 2;
            if ($between > $best) {
                $best = $between;
                $threshold = $level + 1;
            }
        }

        return max(40, min(240, $threshold));
    }
}

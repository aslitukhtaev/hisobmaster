<?php

declare(strict_types=1);

define('BASE_PATH', dirname(__DIR__));

$envFile = BASE_PATH . '/.env';
if (!is_file($envFile)) {
    $envFile = BASE_PATH . '/.env.example';
}
if (is_file($envFile)) {
    foreach (file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) {
            continue;
        }
        [$key, $value] = explode('=', $line, 2);
        $key = trim($key);
        $value = trim(trim($value), "\"'");
        if (getenv($key) === false) {
            putenv("$key=$value");
        }
    }
}

$dbPath = BASE_PATH . '/' . (getenv('DB_PATH') ?: 'database/kassiron.db');
$dbDir = dirname($dbPath);
if (!is_dir($dbDir)) {
    mkdir($dbDir, 0775, true);
}

$pdo = new PDO('sqlite:' . $dbPath);
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->exec('PRAGMA foreign_keys = ON');

$schema = file_get_contents(__DIR__ . '/migrations/schema.sql');
$pdo->exec($schema);
echo "Sxema muvaffaqiyatli yaratildi/yangilandi: {$dbPath}\n";

// Eski bazalarda users jadvali allaqachon mavjud bo'lishi mumkin (CREATE TABLE IF NOT EXISTS
// ularni o'zgartirmaydi), shuning uchun yangi ustunlarni mavjudligini tekshirib, kerak bo'lsa qo'shamiz.
$userColumns = array_column($pdo->query('PRAGMA table_info(users)')->fetchAll(PDO::FETCH_ASSOC), 'name');

if (!in_array('failed_login_attempts', $userColumns, true)) {
    $pdo->exec('ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0');
    echo "users jadvaliga failed_login_attempts ustuni qo'shildi.\n";
}

if (!in_array('locked_until', $userColumns, true)) {
    $pdo->exec('ALTER TABLE users ADD COLUMN locked_until TEXT');
    echo "users jadvaliga locked_until ustuni qo'shildi.\n";
}

// Eski bazalarda products jadvali allaqachon mavjud bo'lishi mumkin — kam
// tovar ogohlantirishi (low_stock_threshold) va karobka/quti hajmi
// (pack_size) ustunlari kerak bo'lsa qo'shiladi.
$productColumns = array_column($pdo->query('PRAGMA table_info(products)')->fetchAll(PDO::FETCH_ASSOC), 'name');

if (!in_array('low_stock_threshold', $productColumns, true)) {
    $pdo->exec('ALTER TABLE products ADD COLUMN low_stock_threshold REAL');
    echo "products jadvaliga low_stock_threshold ustuni qo'shildi.\n";
}

if (!in_array('pack_size', $productColumns, true)) {
    $pdo->exec('ALTER TABLE products ADD COLUMN pack_size INTEGER');
    echo "products jadvaliga pack_size ustuni qo'shildi.\n";
}

// sale_items jadvaliga variant qo'shildi (mahsulot variantlari moduli) —
// eski bazalarda bu ustunlar bo'lmasligi mumkin.
$saleItemColumns = array_column($pdo->query('PRAGMA table_info(sale_items)')->fetchAll(PDO::FETCH_ASSOC), 'name');

if (!in_array('variant_id', $saleItemColumns, true)) {
    $pdo->exec('ALTER TABLE sale_items ADD COLUMN variant_id INTEGER REFERENCES product_variants(id)');
    echo "sale_items jadvaliga variant_id ustuni qo'shildi.\n";
}

if (!in_array('variant_label', $saleItemColumns, true)) {
    $pdo->exec('ALTER TABLE sale_items ADD COLUMN variant_label TEXT');
    echo "sale_items jadvaliga variant_label ustuni qo'shildi.\n";
}

$adminLogin = getenv('SUPER_ADMIN_LOGIN') ?: 'admin';
$adminPassword = getenv('SUPER_ADMIN_PASSWORD') ?: 'change-me-please';

$stmt = $pdo->prepare('SELECT id FROM users WHERE role = ? LIMIT 1');
$stmt->execute(['super_admin']);

if ($stmt->fetch()) {
    echo "Super admin allaqachon mavjud, seed o'tkazib yuborildi.\n";
} else {
    $insert = $pdo->prepare(
        'INSERT INTO users (shop_id, role, full_name, phone, login, password_hash, lang, status)
         VALUES (NULL, ?, ?, ?, ?, ?, ?, ?)'
    );
    $insert->execute([
        'super_admin',
        'Super Admin',
        '',
        $adminLogin,
        password_hash($adminPassword, PASSWORD_DEFAULT),
        'uz',
        'active',
    ]);
    echo "Super admin yaratildi. Login: {$adminLogin}\n";
    echo "Parolni .env faylidagi SUPER_ADMIN_PASSWORD orqali o'zgartiring va qayta migratsiya qiling.\n";
}

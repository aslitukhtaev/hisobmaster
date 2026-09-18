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

$dbPath = BASE_PATH . '/' . (getenv('DB_PATH') ?: 'database/hisobmaster.db');
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

<?php

declare(strict_types=1);

/**
 * Sets the super admin's login and password, unlocks the account and signs
 * every device out. Run on the server:
 *
 *   php database/set-admin.php <login> <password>
 *   SUPER_ADMIN_LOGIN=... SUPER_ADMIN_PASSWORD=... php database/set-admin.php
 *
 * migrate.php only seeds a super admin when none exists, so changing
 * SUPER_ADMIN_PASSWORD in .env later does not change an existing account —
 * this script does.
 */

if (PHP_SAPI !== 'cli') {
    exit(1);
}

define('BASE_PATH', dirname(__DIR__));

$envFile = BASE_PATH . '/.env';
if (is_file($envFile)) {
    foreach (file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) {
            continue;
        }
        [$key, $value] = explode('=', $line, 2);
        $key = trim($key);
        if (getenv($key) === false) {
            putenv("$key=" . trim(trim($value), "\"'"));
        }
    }
}

$login = $argv[1] ?? getenv('SUPER_ADMIN_LOGIN');
$password = $argv[2] ?? getenv('SUPER_ADMIN_PASSWORD');
if (!is_string($login) || trim($login) === '' || !is_string($password) || $password === '') {
    fwrite(STDERR, "Foydalanish: php database/set-admin.php <login> <parol>\n");
    exit(1);
}
$login = trim($login);

$pdo = new PDO('sqlite:' . BASE_PATH . '/' . (getenv('DB_PATH') ?: 'database/kassiron.db'));
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->exec('PRAGMA busy_timeout = 5000');

$hash = password_hash($password, PASSWORD_DEFAULT);
$taken = $pdo->prepare("SELECT id FROM users WHERE login = ? AND role <> 'super_admin'");
$taken->execute([$login]);
if ($taken->fetch()) {
    fwrite(STDERR, "\"$login\" logini boshqa foydalanuvchiga tegishli.\n");
    exit(1);
}

$admin = $pdo->query("SELECT id FROM users WHERE role = 'super_admin' ORDER BY id LIMIT 1")->fetch();
if ($admin) {
    $pdo->prepare(
        "UPDATE users SET login = ?, password_hash = ?, status = 'active', failed_login_attempts = 0, locked_until = NULL,
                sessions_revoked_at = strftime('%Y-%m-%d %H:%M:%f', 'now')
         WHERE id = ?"
    )->execute([$login, $hash, $admin['id']]);
    echo "Super admin yangilandi. Login: {$login}\n";
} else {
    $pdo->prepare(
        "INSERT INTO users (shop_id, role, full_name, phone, login, password_hash, lang, status)
         VALUES (NULL, 'super_admin', 'Super Admin', '', ?, ?, 'uz', 'active')"
    )->execute([$login, $hash]);
    echo "Super admin yaratildi. Login: {$login}\n";
}

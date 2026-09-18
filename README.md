# HisobMaster

Kichik do'kon egalari uchun hisob-kitob tizimi: mahsulotlar (tannarx/sotish narxi), sotuvlar va termo-chek, qarzdorlar bilan chuqur ishlash, xarajatlar, sof foyda va eng ko'p sotilgan mahsulotlar bo'yicha hisobotlar, xodimlar (bir martalik referal-havola orqali qo'shiladi, granular huquqlar bilan). Tizim to'liq PHP'da yozilgan, ma'lumotlar bazasi — SQLite (`.db` fayl). Veb-saytda va Telegram bot WebApp'ida bir xil ishlaydi.

To'liq rejalashtirilgan arxitektura va bosqichlar uchun: loyihaning ishlab chiqish tarixidagi rejaga qarang.

## Talablar

- PHP 8.1+ (`pdo_sqlite` kengaytmasi bilan)
- Composer/Node **shart emas** — tizim build-siz, sof PHP + vanilla JS/CSS

## O'rnatish

```bash
cp .env.example .env
# .env faylida SUPER_ADMIN_LOGIN va SUPER_ADMIN_PASSWORD ni o'zgartiring
php database/migrate.php
php -S localhost:8000 -t public
```

So'ng brauzerda `http://localhost:8000` ni oching va `.env`dagi super admin login/paroli bilan kiring.

## Loyihaning joriy holati

- **0-bosqich (tayyor):** loyiha skeleti, SQLite sxema, core (Router/DB/Auth/View), login oqimi, o'zbek/rus tillari, responsive shell (sidebar + mobil bottom-nav).
- Keyingi bosqichlar (do'kon yaratish, mahsulotlar, sotuv/chek, qarzdorlar, hisobotlar, xodimlar, Telegram WebApp) navbat bilan qo'shiladi.

# KassirON

Kichik do'kon egalari uchun hisob-kitob tizimi: mahsulotlar (tannarx/sotish narxi), sotuvlar va termo-chek, qarzdorlar bilan chuqur ishlash, xarajatlar, sof foyda va eng ko'p sotilgan mahsulotlar bo'yicha hisobotlar, xodimlar (bir martalik referal-havola orqali qo'shiladi, granular huquqlar bilan). Tizim to'liq PHP'da yozilgan, ma'lumotlar bazasi — SQLite (`.db` fayl). Veb-saytda va Telegram bot WebApp'ida bir xil ishlaydi.

## Talablar

- PHP 8.1+ (`pdo_sqlite` va `curl` kengaytmalari bilan)
- Composer/Node **shart emas** — tizim build-siz, sof PHP + vanilla JS/CSS

## O'rnatish

```bash
cp .env.example .env
# .env faylida SUPER_ADMIN_LOGIN va SUPER_ADMIN_PASSWORD ni o'zgartiring
php database/migrate.php
php -S localhost:8000 -t public
```

So'ng brauzerda `http://localhost:8000` ni oching va `.env`dagi super admin login/paroli bilan kiring.

## Funksionallik

- **Super admin**: do'konlar yaratish/bloklash, login-parolni qayta tiklash.
- **Profil**: F.I.Sh/telefon/login/parol/til; do'kon egasi uchun do'kon nomi, manzili va chek printer kengligi (80mm/58mm) sozlamalari.
- **Mahsulotlar**: tannarx, sotish narxi, ombor qoldig'i, kategoriya, qidiruv.
- **Sotuv (kassa)**: mahsulot qidirish/skanerlash, savat, chegirma, naqd/karta/qarz to'lov, termo-chek chop etish.
- **Qarzdorlar**: mijoz balansi, to'liq qarz/to'lov tarixi, qisman to'lov qabul qilish.
- **Xarajatlar**: kategoriya bo'yicha kiritish, davr bo'yicha filtr.
- **Hisobotlar**: sof foyda, kunlik tushum grafigi, to'lov turlari taqsimoti, eng ko'p sotilgan mahsulotlar.
- **Xodimlar**: bir martalik taklif havolasi, har bir xodim uchun granular ruxsatlar (sotuv, mahsulot, narx, qarzdorlar, xarajat, hisobot).
- **Telegram WebApp**: bot orqali saytni WebApp sifatida ochish (quyida sozlash bo'yicha ko'rsatma).

## Telegram bot sozlash (ixtiyoriy)

1. Telegram'da [@BotFather](https://t.me/BotFather) orqali yangi bot yarating, tokenni oling.
2. `.env` faylida `TELEGRAM_BOT_TOKEN` ni kiriting. Xohlasangiz `TELEGRAM_WEBHOOK_SECRET` ga tasodifiy qatordan qo'ying (webhook so'rovlarini tekshirish uchun).
3. Sayt HTTPS orqali ochiq bo'lishi kerak (Telegram HTTP webhook'larni qabul qilmaydi). Webhookni ro'yxatdan o'tkazing:

   ```bash
   curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=<APP_URL>/telegram/webhook&secret_token=<TELEGRAM_WEBHOOK_SECRET>"
   ```

4. Botga `/start` yozing — bot "Ilovani ochish" tugmasi bilan javob beradi, u bosilganda sayt Telegram ichida WebApp sifatida ochiladi. Kirish baribir login/parol orqali amalga oshadi — Telegram faqat qulay kirish kanali.

## Loyiha tuzilishi

```
public/            web-root (index.php front controller, assets)
app/Core/          Router, Database, Auth, View, Env
app/Controllers/    barcha controller'lar
app/Models/         PDO ustidagi yupqa data-access klasslar
app/Middleware/     Auth/Guest/Csrf/Role/Permission middleware'lar
app/views/          PHP shablonlar (layout + modul bo'yicha papkalar)
app/lang/           uz.php / ru.php tarjimalar
database/           schema.sql + migrate.php
```

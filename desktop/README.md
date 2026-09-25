# KassirON — kompyuter dasturi (Windows)

Saytning aynan shu PHP kodi kompyuterning o'zida, o'z bazasi bilan ishlaydi:
internet bo'lmasa ham sotuv davom etadi, internet paydo bo'lishi bilan server
bilan o'zi sinxronlanadi. Litsenziya: bitta do'kon — bitta umrbod litsenziya,
kompyuterlar soni cheklanmagan.

## Qanday ishlaydi

- `main.js` — Electron: PHP'ni `127.0.0.1` dagi bo'sh portda ishga tushiradi va
  oynani ochadi. Baza: `%APPDATA%\KassirON\kassiron.db`.
- **Sinxron** — alohida jarayonda (`bin/desktop-sync.php`), har daqiqada va har
  sotuvdan keyin. Qoldiq "+/−" sifatida uzatiladi (bir nechta kassa bir
  mahsulotni sotsa ham to'g'ri).
- **Ruxsatnoma** — server imzolaydi (ECDSA P-256), kompyuterga bog'langan;
  har sinxronda yangilanadi va do'konning "internetsiz ishlash muddati"
  (standart 7 kun) davomida amal qiladi. Muddat o'tsa yoki kompyuter
  o'chirilsa — yangi amallar to'xtaydi, ko'rish ishlaydi.
- **Kod yangilanishi** — saytga push qilingan har bir o'zgarish kassiron.uz'dan
  imzolangan paket sifatida keladi (`app/Sync/CodePackage.php`). Dastur
  "Nima yangi"ni ko'rsatadi (`app/changelog.php`), "Hozir yangilash" yoki
  keyingi ochilishda o'zi yangilanadi. Oldin baza zaxiralanadi
  (`%APPDATA%\KassirON\backups`), xato bo'lsa oldingi versiyaga qaytadi.
- **Qobiq yangilanishi** (Electron/PHP) — GitHub Releases orqali
  (electron-updater); `desktop/package.json` dagi `version` oshirilganda.
- **Chek printeri** — "Printer" bo'limida har bir kompyuter o'z printerini
  tanlaydi (`%APPDATA%\KassirON\printer.json`: printer, sotuvdan keyin
  avtomatik chiqarish, nusxalar soni). Chek oynasiz chiqadi
  (`lib/receipt-printer.js`): `/sales/{id}/print` sahifasi yashirin oynada
  qog'oz kengligida (80/58 mm, do'kon sozlamasi) ochiladi va sahifa aynan chek
  uzunligida chop etiladi — bo'sh qog'oz ketmaydi. Printer tanlanmagan bo'lsa,
  odatdagi chop etish oynasi. Ishlab chiqishda `KASSIRON_PRINT_TO_PDF=<papka>`
  — printer o'rniga PDF.

## Yig'ish

GitHub Actions (`.github/workflows/desktop.yml`) `desktop/**` o'zgarganda
Windows'da yig'adi, yig'ilgan PHP bilan smoke test o'tkazadi va
`KassirON-Setup.exe` ni artifact qiladi. `version` yangi bo'lsa — GitHub
Release ham chiqaradi (yuklab olish havolasi: saytdagi "Kompyuterlar" sahifasi).

Lokal (Linux/macOS, tizim PHP'si bilan):

```bash
cd desktop
npm install
KASSIRON_SERVER=http://localhost:8000 npm start
```

## "Nima yangi"

Do'kon sezadigan o'zgarish kiritilganda `app/changelog.php` ga yozuv qo'shing
(sana, o'zbekcha, ruscha). Yozuvsiz o'zgarish ham kompyuterlarga yetib boradi —
"Kichik tuzatishlar va yaxshilanishlar" degan umumiy izoh bilan.

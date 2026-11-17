# KassirON — Android ilova

Native ilova (Kotlin + Jetpack Compose). **Internetsiz ham ishlaydi**: do'konning
ma'lumotlari telefondagi bazada, sotuvlar internet bo'lmasa ham qilinadi va
internet qaytganda serverga o'zi yuboriladi. Kompyuter dasturi bilan bir xil
sinxron protokoli (`/api/device/activate`, `/api/sync/push`, `/api/sync/pull`).

- Paket: `uz.kassiron.app`; minSdk 24 (Android 7+)
- Ulash: egasining login-paroli yoki saytdagi "Telefonni ulash" 6 xonali kodi
  (Google/Telegram bilan kirgan egalar uchun; Sozlamalar → Kompyuterlar)
- Kassa: qidiruv, kamera bilan shtrix-kod, Bluetooth termoprinter, naqd/karta/qarz
- Mahsulotlar, kirim, qarzdorlar, sotuvlar va qaytarish, xarajatlar, hisobotlar, davomat
- Arxitektura va qoidalar: [ARCHITECTURE.md](ARCHITECTURE.md)

## Yig'ish

```bash
bash android/build.sh          # imzolangan APK + AAB  → android/dist/
bash android/build.sh debug    # tez tekshiruv uchun debug APK
```

Kerak: JDK 17+ (Android Studio JBR `~/.jdks` da topiladi) va Android SDK.
Loyiha yo'lida kirill harflari bo'lgani uchun skript uni ASCII yo'lga ko'chirib
yig'adi.

Lokal server bilan sinash (emulyator kompyuterni `10.0.2.2` deb ko'radi):

```bash
KASSIRON_DEBUG_SERVER=http://10.0.2.2:8791 bash android/build.sh debug
KASSIRON_MINIFY_DEBUG=1 ...   # release'dagi kod qisqartirishni ham tekshirish
```

Saytdagi yuklab olish havolasi `public/downloads/KassirON.apk`: yangi versiyani
yig'gach shu yerga nusxalang va `app/build.gradle` dagi `versionCode`ni oshiring
(oshirmasa telefon yangilamaydi).

## Imzo kaliti — YO'QOTMANG

`~/.kassiron-android/kassiron-release.jks` va `keystore.properties` (parol).
Git'ga kiritilmaydi. Kalit yo'qolsa, o'rnatilgan ilovalarni yangilab bo'lmaydi —
nusxasini xavfsiz joyda saqlang.

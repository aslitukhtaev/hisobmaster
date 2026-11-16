# KassirON — Android ilova

Trusted Web Activity (TWA): `https://kassiron.uz` Chrome dvigatelida, butun
ekranda, brauzer panelisiz ochiladi. Ilova ~350 KB. Sayt yangilansa ilova
ham darhol yangi bo'ladi — qayta o'rnatish shart emas.

- Paket: `uz.kassiron.app`
- Ochiladigan sahifa: `/login?source=android` (kirgan bo'lsa — darhol kassa yoki bosh sahifa)
- Brauzer paneli yashirin bo'lishi uchun sayt `public/.well-known/assetlinks.json`
  da imzo kalitining SHA-256 barmoq izini e'lon qiladi.

## Yig'ish

```bash
bash android/build.sh
```

Natija: `android/dist/KassirON.apk` (to'g'ridan-to'g'ri o'rnatish uchun) va
`android/dist/KassirON.aab` (Google Play uchun). Saytdagi yuklab olish havolasi
`public/downloads/KassirON.apk` — yangi versiyani shu yerga nusxalang va
`app/build.gradle` dagi `versionCode`ni oshiring.

Kerak: JDK 17+ (Android Studio'ning JBR'i `~/.jdks` da topiladi) va Android SDK.

## Imzo kaliti — YO'QOTMANG

`~/.kassiron-android/kassiron-release.jks` va `keystore.properties` (parol).
Git'ga kiritilmaydi. Kalit yo'qolsa, o'rnatilgan ilovalarni yangilab bo'lmaydi —
nusxasini xavfsiz joyda saqlang.

## Google Play

Play Console'ga `.aab` yuklansa, Google o'z kaliti bilan qayta imzolaydi
(Play App Signing). Shunda Play Console → App integrity'dagi SHA-256 barmoq
izini ham `assetlinks.json`ga qo'shing, aks holda ilovada brauzer paneli
ko'rinib qoladi.

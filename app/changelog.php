<?php

// What's new, newest first — shown in the desktop app when an update
// arrives ("Nima yangi"). Add an entry for changes a shop would notice; a
// change without an entry still reaches the computers, with a generic note.
// New entries go at the top, and old ones are never removed: a computer is
// shown the entries above the ones it already has (CodePackage::notesSince()).

return [
    [
        'date' => '2026-11-15',
        'uz' => "POS rejimi qayta qurildi: kassa butun ekranda, menyusiz — faqat qidiruv, mahsulotlar, savat va to'lov. Chiqish uchun \"Menyu\" tugmasi.",
        'ru' => 'Режим POS переделан: касса на весь экран, без меню — только поиск, товары, корзина и оплата. Выход — кнопка «Меню».',
    ],
    [
        'date' => '2026-11-15',
        'uz' => "Menyu qisqardi: yangi \"Kassa\" bandi, kam ishlatiladigan bo'limlar (profil, xodimlar, ta'minotchilar, printer, yordam) \"Sozlamalar\" sahifasida.",
        'ru' => 'Меню стало короче: новый пункт «Касса», редкие разделы (профиль, сотрудники, поставщики, принтер, помощь) — на странице «Настройки».',
    ],
    [
        'date' => '2026-11-15',
        'uz' => "Yangi to'lov turi — QR (aralash to'lovda ham). Kunlik hisobot va hisobotlarda QR alohida ko'rinadi; sotuvdan keyin \"Summani nusxalash\" tugmasi. Mahsulotga MXIK, o'ram kodi va QQS maydonlari qo'shildi.",
        'ru' => 'Новый тип оплаты — QR (в том числе в смешанной оплате). QR виден отдельно в дневном отчёте и отчётах; после продажи — кнопка «Скопировать сумму». В товар добавлены поля МХИК, код упаковки и НДС.',
    ],
    [
        'date' => '2026-11-15',
        'uz' => "Sotuvdan keyin kassa darhol keyingi mijoz uchun ochiladi (chek o'zi chiqadi, qaytim tepada ko'rinadi); dastur ochilganda ham to'g'ri kassa ochiladi.",
        'ru' => 'После продажи касса сразу открывается для следующего покупателя (чек печатается сам, сдача видна сверху); при запуске программы тоже сразу открывается касса.',
    ],
    [
        'date' => '2026-11-15',
        'uz' => "POS rejimi: kassa sahifasidagi tugma bilan yoqiladi — tugmalar va yozuvlar kattaroq. F2 — qidiruv, F9 — sotuvni yakunlash.",
        'ru' => 'Режим POS: включается кнопкой на странице кассы — кнопки и надписи крупнее. F2 — поиск, F9 — завершить продажу.',
    ],
    [
        'date' => '2026-10-06',
        'uz' => "Yangi ilova ikonkasi: lime fon va power belgisi, kichik o'lchamda ham aniq ko'rinadi.",
        'ru' => 'Новая иконка приложения: лаймовый фон и значок питания, чётко читается даже в маленьком размере.',
    ],
    [
        'date' => '2026-10-06',
        'uz' => "Yangi dizayn: yangi logotip va ikonka, lime ranglar, yangi shriftlar.",
        'ru' => 'Новый дизайн: новый логотип и иконка, лаймовые цвета, новые шрифты.',
    ],
    [
        'date' => '2026-09-30',
        'uz' => "Login va parol qayta-qayta so'ralmaydi: dastur sizni eslab qoladi. Faollashtirishdan keyin darhol ichkaridasiz.",
        'ru' => 'Логин и пароль больше не спрашиваются каждый раз: программа запоминает вас. После активации вход выполняется сразу.',
    ],
    [
        'date' => '2026-09-29',
        'uz' => "Yangi KassirON ikonkasi.",
        'ru' => 'Новая иконка KassirON.',
    ],
    [
        'date' => '2026-09-25',
        'uz' => "Chek termoprinterga oynasiz, darhol chiqadi; sotuvdan keyin avtomatik chop etish. Printer bo'limida printerni tanlang.",
        'ru' => 'Чек печатается на термопринтер сразу, без окна печати; автоматическая печать после продажи. Выберите принтер в разделе «Принтер».',
    ],
    [
        'date' => '2026-09-25',
        'uz' => "Yordam bo'limida biz bilan bog'lanish: telefon va Telegram.",
        'ru' => 'В разделе «Помощь» — контакты для связи: телефон и Telegram.',
    ],
    [
        'date' => '2026-09-25',
        'uz' => "Kompyuter dasturi: internetsiz ishlash, avtomatik sinxronlash va yangilanish.",
        'ru' => 'Программа для компьютера: работа без интернета, автоматическая синхронизация и обновления.',
    ],
    [
        'date' => '2026-09-23',
        'uz' => "Shtrix-kod bilan tezkor sotuv, qaytim hisoblash, variantli mahsulotlar qoldig'i, chegirma ruxsati va boshqa tuzatishlar.",
        'ru' => 'Быстрая продажа по штрихкоду, расчёт сдачи, остатки товаров с вариантами, право на скидку и другие исправления.',
    ],
];

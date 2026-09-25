<?php

// What's new, newest first — shown in the desktop app when an update
// arrives ("Nima yangi"). Add an entry for changes a shop would notice; a
// change without an entry still reaches the computers, with a generic note.
// New entries go at the top, and old ones are never removed: a computer is
// shown the entries above the ones it already has (CodePackage::notesSince()).

return [
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

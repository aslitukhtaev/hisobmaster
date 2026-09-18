<?php $lang = current_lang(); ?>
<div class="lang-switcher">
    <form method="post" action="/lang/uz">
        <?= csrf_field() ?>
        <button type="submit" class="lang-btn <?= $lang === 'uz' ? 'active' : '' ?>">UZ</button>
    </form>
    <form method="post" action="/lang/ru">
        <?= csrf_field() ?>
        <button type="submit" class="lang-btn <?= $lang === 'ru' ? 'active' : '' ?>">RU</button>
    </form>
</div>

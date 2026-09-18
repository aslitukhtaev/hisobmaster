(function () {
    if (!window.Telegram || !window.Telegram.WebApp) {
        return;
    }

    var tg = window.Telegram.WebApp;

    try {
        tg.ready();
        tg.expand();
        tg.setHeaderColor('#4f46e5');
        tg.setBackgroundColor('#f4f5fb');
    } catch (e) {
        // Older Telegram clients may not support every WebApp call.
    }

    document.documentElement.classList.add('in-telegram');
})();

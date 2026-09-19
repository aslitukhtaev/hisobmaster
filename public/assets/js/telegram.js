(function () {
    if (!window.Telegram || !window.Telegram.WebApp) {
        return;
    }

    var tg = window.Telegram.WebApp;

    var isDark = document.documentElement.getAttribute('data-theme') === 'dark'
        || (!document.documentElement.getAttribute('data-theme') && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);

    try {
        tg.ready();
        tg.expand();
        tg.setHeaderColor(isDark ? '#121e2e' : '#059669');
        tg.setBackgroundColor(isDark ? '#0a1420' : '#f4f6f8');
    } catch (e) {
        // Older Telegram clients may not support every WebApp call.
    }

    document.documentElement.classList.add('in-telegram');
})();

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

    // A chat link (the support contact on the help page) opens that chat in
    // Telegram itself; followed as a plain link it would load t.me inside
    // the Mini App's own view.
    document.addEventListener('click', function (e) {
        var link = e.target.closest ? e.target.closest('a[data-telegram-chat]') : null;
        if (!link || !tg.initData || typeof tg.openTelegramLink !== 'function') {
            return;
        }
        e.preventDefault();
        try {
            tg.openTelegramLink(link.href);
        } catch (err) {
            window.open(link.href, '_blank');
        }
    });
})();

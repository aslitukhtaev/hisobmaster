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

    // The login page inside the Telegram WebApp (tg.initData is signed by
    // Telegram and empty anywhere else). A password sign-in here links this
    // Telegram account (the tg_init_data field), and from then on the page
    // signs in by itself — the password is asked once, even if Telegram
    // doesn't keep cookies.
    var loginForm = document.querySelector('form[action="/login"]');
    if (loginForm && tg.initData) {
        var field = document.createElement('input');
        field.type = 'hidden';
        field.name = 'tg_init_data';
        field.value = tg.initData;
        loginForm.appendChild(field);

        // Once per half a minute at most: if the sign-in doesn't stick
        // (cookies refused) the page would otherwise come back here and try
        // again forever. No sessionStorage at all: don't try.
        var mayTry = false;
        try {
            var last = Number(window.sessionStorage.getItem('kassiron-tg-auto') || 0);
            mayTry = Date.now() - last > 30000;
            if (mayTry) { window.sessionStorage.setItem('kassiron-tg-auto', String(Date.now())); }
        } catch (e) {
            mayTry = false;
        }

        if (mayTry && loginForm.getAttribute('data-telegram-auto') === '1') {
            var status = document.getElementById('telegram-login-status');
            var csrf = loginForm.querySelector('input[name="_csrf"]');
            var body = new URLSearchParams();
            body.set('_csrf', csrf ? csrf.value : '');
            body.set('init_data', tg.initData);
            if (status) { status.hidden = false; }
            loginForm.hidden = true;
            var showForm = function () {
                if (status) { status.hidden = true; }
                loginForm.hidden = false;
            };
            fetch('/login/telegram', { method: 'POST', body: body, credentials: 'same-origin', headers: { Accept: 'application/json' } })
                .then(function (response) {
                    // Redirected: already signed in (to the app) — or the
                    // request was refused (back to the login page: show it).
                    if (response.redirected) {
                        if (new URL(response.url).pathname !== '/login') {
                            window.location.replace(response.url);
                            return null;
                        }
                        return { ok: false };
                    }
                    return response.json();
                })
                .then(function (result) {
                    if (result && result.ok) {
                        window.location.replace(result.redirect || '/');
                    } else if (result !== null) {
                        showForm();
                    }
                })
                .catch(showForm);
        }
    }

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

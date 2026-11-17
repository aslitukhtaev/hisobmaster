(function () {
    if (!window.Telegram || !window.Telegram.WebApp) {
        return;
    }

    var tg = window.Telegram.WebApp;

    var isDark = document.documentElement.getAttribute('data-theme') === 'dark'
        || (!document.documentElement.getAttribute('data-theme') && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);

    var at = function (v) { return typeof tg.isVersionAtLeast === 'function' && tg.isVersionAtLeast(v); };
    var call = function (fn) { try { fn(); } catch (e) { /* older clients lack some calls */ } };
    var mobile = tg.platform === 'android' || tg.platform === 'ios';
    var surface = isDark ? '#11150e' : '#ffffff';
    var page = isDark ? '#090c07' : '#f4f6f1';

    call(function () { tg.ready(); });
    call(function () { tg.expand(); });
    // Full screen on phones (Bot API 8.0): the app gets the whole screen;
    // safe areas keep it clear of the status bar, notch and Telegram's
    // own buttons (see the --sa-* variables in app.css).
    if (mobile && at('8.0')) {
        call(function () { tg.requestFullscreen(); });
    }
    // A downward scroll must scroll the page, not close the app.
    if (at('7.7')) {
        call(function () { tg.disableVerticalSwipes(); });
    }
    call(function () { tg.setHeaderColor(surface); });
    call(function () { tg.setBackgroundColor(page); });
    if (at('7.10')) {
        call(function () { tg.setBottomBarColor(surface); });
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
                        // A Telegram account seen for the first time: its
                        // "Telegram bilan kirish" opens a new shop right here.
                        if (result.signup) {
                            Array.prototype.forEach.call(document.querySelectorAll('.js-telegram-login'), function (link) {
                                link.href = result.signup;
                            });
                        }
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

    // Telegram's back arrow on every page but the main ones.
    var path = location.pathname;
    var roots = ['/', '/login', '/sales/new', '/sales', '/products', '/customers', '/subscription', '/signup'];
    if (at('6.1') && tg.BackButton) {
        if (roots.indexOf(path) === -1) {
            call(function () { tg.BackButton.show(); });
            tg.BackButton.onClick(function () {
                if (history.length > 1 && document.referrer && new URL(document.referrer).origin === location.origin) {
                    history.back();
                } else {
                    location.href = '/';
                }
            });
        } else {
            call(function () { tg.BackButton.hide(); });
        }
    }

    // Light taps on buttons and the bottom menu; success / error buzz for messages.
    var haptic = at('6.1') && tg.HapticFeedback ? tg.HapticFeedback : null;
    if (haptic) {
        document.addEventListener('click', function (e) {
            var el = e.target.closest ? e.target.closest('.btn, .bottom-link, .product-tile, .payment-btn, button') : null;
            if (el && !el.disabled) {
                call(function () { haptic.impactOccurred('light'); });
            }
        }, true);
        if (document.querySelector('.flash-alert.alert-success')) {
            call(function () { haptic.notificationOccurred('success'); });
        } else if (document.querySelector('.flash-alert.alert-error')) {
            call(function () { haptic.notificationOccurred('error'); });
        }
    }

    // The till: closing the app with goods in the cart asks first.
    if (at('6.2')) {
        document.addEventListener('kassiron:cart', function (e) {
            var count = e.detail && e.detail.count ? e.detail.count : 0;
            call(function () {
                if (count > 0) { tg.enableClosingConfirmation(); } else { tg.disableClosingConfirmation(); }
            });
        });
    }
})();

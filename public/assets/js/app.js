if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/sw.js').catch(function () {
            // Installability is a progressive enhancement — ignore failures.
        });
    });
}

// Forms that need a confirmation carry it as data-confirm (inline onsubmit
// handlers are blocked by the Content-Security-Policy).
document.addEventListener('submit', function (e) {
    var form = e.target;
    var message = form && form.getAttribute ? form.getAttribute('data-confirm') : null;
    if (message && !window.confirm(message)) {
        e.preventDefault();
        e.stopImmediatePropagation();
    }
}, true);

document.addEventListener('DOMContentLoaded', function () {
    // Only a success message fades away on its own; an error or warning stays
    // until the user moves on, and page-level alerts (not flash messages)
    // are never touched.
    document.querySelectorAll('.flash-alert.alert-success').forEach(function (alertEl) {
        setTimeout(function () {
            alertEl.style.transition = 'opacity .4s ease';
            alertEl.style.opacity = '0';
            setTimeout(function () { alertEl.remove(); }, 400);
        }, 4000);
    });

    var themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        function effectiveTheme() {
            var current = document.documentElement.getAttribute('data-theme');
            if (current) {
                return current;
            }
            return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
        }

        themeToggle.setAttribute('aria-pressed', effectiveTheme() === 'dark' ? 'true' : 'false');

        themeToggle.addEventListener('click', function () {
            var next = effectiveTheme() === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', next);
            themeToggle.setAttribute('aria-pressed', next === 'dark' ? 'true' : 'false');
            try {
                localStorage.setItem('kassiron-theme', next);
            } catch (e) {}
        });
    }

    var moreToggle = document.getElementById('more-nav-toggle');
    var drawer = document.getElementById('mobile-drawer');
    if (moreToggle && drawer) {
        var drawerSheet = drawer.querySelector('.mobile-drawer-sheet');

        function openDrawer() {
            drawer.classList.add('open');
            moreToggle.setAttribute('aria-expanded', 'true');
            if (drawerSheet) {
                drawerSheet.focus();
            }
        }

        function closeDrawer() {
            drawer.classList.remove('open');
            moreToggle.setAttribute('aria-expanded', 'false');
            moreToggle.focus();
        }

        moreToggle.addEventListener('click', openDrawer);

        drawer.addEventListener('click', function (e) {
            if (e.target === drawer) {
                closeDrawer();
            }
        });

        drawer.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                closeDrawer();
            }
        });
    }

    // Telegram's in-app WebView (especially on iOS) frequently makes window.print()
    // a silent no-op — there is no dedicated print API in the WebApp SDK to fall
    // back to, so the best we can do is still attempt it and show a hint pointing
    // Telegram users at "Open in browser" if nothing happens. Shared by every
    // print-to-PDF button in the app (the receipt, the reports print view, …).
    var printBtns = document.querySelectorAll('.js-print-btn');
    if (printBtns.length) {
        printBtns.forEach(function (btn) {
            btn.addEventListener('click', function () {
                window.print();
            });
        });

        var printHint = document.getElementById('telegram-print-hint');
        if (printHint && document.documentElement.classList.contains('in-telegram')) {
            printHint.style.display = 'block';
        }
    }
});

// Desktop app: when a background sync finishes (the shell tells us through
// the preload bridge), refresh the status bar so "1 ta sotuv yuborilmagan"
// turns into "Sinxronlangan" without reloading the page.
(function () {
    if (!window.kassironDesktop || typeof window.kassironDesktop.onSyncStatus !== 'function') {
        return;
    }
    window.kassironDesktop.onSyncStatus(function () {
        var holder = document.getElementById('desktop-status');
        if (!holder) {
            return;
        }
        fetch('/desktop/status-bar', { credentials: 'same-origin', cache: 'no-store' })
            .then(function (response) { return response.ok ? response.text() : null; })
            .then(function (html) {
                if (!html) {
                    return;
                }
                var tmp = document.createElement('div');
                tmp.innerHTML = html;
                var fresh = tmp.querySelector('#desktop-status');
                if (fresh) {
                    holder.replaceWith(fresh);
                }
            })
            .catch(function () {});
    });
})();

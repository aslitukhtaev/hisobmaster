if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/sw.js').catch(function () {
            // Installability is a progressive enhancement — ignore failures.
        });
    });
}

// <input data-filter-rows="#table-id">: hides the table's rows that don't
// contain what is typed (name, phone...), case- and apostrophe-insensitive.
function kassironFold(text) {
    return String(text || '').toLowerCase().replace(/[\u2018\u2019\u02bb\u02bc`´]/g, "'").replace(/\s+/g, '');
}
document.addEventListener('input', function (e) {
    var input = e.target;
    var selector = input && input.getAttribute ? input.getAttribute('data-filter-rows') : null;
    if (!selector) {
        return;
    }
    var table = document.querySelector(selector);
    if (!table) {
        return;
    }
    var q = kassironFold(input.value);
    var shown = 0;
    table.querySelectorAll('tbody tr').forEach(function (tr) {
        var hit = q === '' || kassironFold(tr.textContent).indexOf(q) !== -1;
        tr.hidden = !hit;
        if (hit) {
            shown++;
        }
    });
    var empty = document.querySelector(input.getAttribute('data-filter-empty') || '');
    if (empty) {
        empty.hidden = shown > 0;
    }
});

// <button data-fill-target="#input" data-fill-value="123">: puts the value in (e.g. "To'liq" on a debt payment).
document.addEventListener('click', function (e) {
    var btn = e.target.closest ? e.target.closest('[data-fill-target]') : null;
    if (!btn) {
        return;
    }
    var input = document.querySelector(btn.getAttribute('data-fill-target'));
    if (input) {
        input.value = btn.getAttribute('data-fill-value') || '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.focus();
    }
});

// A filter <select data-autosubmit> applies as soon as it is changed.
document.addEventListener('change', function (e) {
    var el = e.target;
    if (el && el.hasAttribute && el.hasAttribute('data-autosubmit') && el.form) {
        el.form.submit();
    }
});

// Forms that need a confirmation carry it as data-confirm (inline onsubmit
// handlers are blocked by the Content-Security-Policy).
// Inside the Telegram WebApp the question is Telegram's own dialog (the
// browser's confirm() there looks foreign and, on some clients, never shows).
document.addEventListener('submit', function (e) {
    var form = e.target;
    var message = form && form.getAttribute ? form.getAttribute('data-confirm') : null;
    if (!message) {
        return;
    }
    if (form.getAttribute('data-confirmed') === '1') {
        form.removeAttribute('data-confirmed');
        return;
    }
    var tg = window.Telegram && window.Telegram.WebApp;
    if (tg && tg.initData && tg.isVersionAtLeast && tg.isVersionAtLeast('6.2')) {
        e.preventDefault();
        e.stopImmediatePropagation();
        var submitter = e.submitter || null;
        tg.showConfirm(message, function (ok) {
            if (!ok) {
                return;
            }
            form.setAttribute('data-confirmed', '1');
            if (form.requestSubmit) {
                form.requestSubmit(submitter);
            } else {
                form.submit();
            }
        });
        return;
    }
    if (!window.confirm(message)) {
        e.preventDefault();
        e.stopImmediatePropagation();
    }
}, true);

// A form is sent once: a second click (or Enter) while the page is still
// loading would otherwise create a second product, customer, payment...
// Runs last (bubble phase), so forms a page's own checks stopped stay usable.
document.addEventListener('submit', function (e) {
    var form = e.target;
    if (e.defaultPrevented || !form || (form.getAttribute('method') || 'get').toLowerCase() !== 'post') {
        return;
    }
    if (form.getAttribute('data-sending') === '1') {
        e.preventDefault();
        return;
    }
    form.setAttribute('data-sending', '1');
    // Disabled after this tick: a disabled button's name/value would not be sent.
    setTimeout(function () {
        form.querySelectorAll('button[type="submit"], button:not([type]), input[type="submit"]').forEach(function (b) {
            b.disabled = true;
        });
    }, 0);
    // A form that didn't leave the page (a file download, a slow network) can be sent again later.
    setTimeout(function () { releaseForm(form); }, 8000);
});
function releaseForm(form) {
    form.removeAttribute('data-sending');
    form.querySelectorAll('button[type="submit"], button:not([type]), input[type="submit"]').forEach(function (b) {
        b.disabled = false;
    });
}
// Back to a page from the browser's cache: its forms work again.
window.addEventListener('pageshow', function (e) {
    if (e.persisted) {
        document.querySelectorAll('form[data-sending="1"]').forEach(releaseForm);
    }
});

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

    // "Summani nusxalash": puts a plain number on the clipboard, to paste into
    // another register app (e.g. the tax cash register).
    document.querySelectorAll('[data-copy]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var value = btn.getAttribute('data-copy');
            var label = btn.textContent;
            var done = function () {
                btn.textContent = btn.getAttribute('data-copied') || label;
                setTimeout(function () { btn.textContent = label; }, 1500);
            };
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(value).then(done).catch(function () {});
            }
        });
    });

    // POS mode: the till alone on the whole screen — no menu, no top bar,
    // big buttons. Chosen on the till page, remembered on this computer (the
    // <head> script applies it before the page paints). In the desktop app
    // the window itself goes full screen and stays so from sale to sale;
    // in a browser, full screen can only follow the click.
    (function () {
        var root = document.documentElement;
        var posOn = function () { return root.getAttribute('data-pos') === 'on'; };

        function syncFullScreen(fromClick) {
            var want = posOn() && root.getAttribute('data-page') === 'till';
            var bridge = window.kassironDesktop;
            if (bridge && typeof bridge.setFullScreen === 'function') {
                bridge.setFullScreen(want);
                return;
            }
            if (!fromClick) {
                return;
            }
            if (want && !document.fullscreenElement && root.requestFullscreen) {
                root.requestFullscreen().catch(function () {});
            } else if (!want && document.fullscreenElement && document.exitFullscreen) {
                document.exitFullscreen().catch(function () {});
            }
        }

        document.querySelectorAll('.js-pos-toggle').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var next = !posOn();
                if (next) {
                    root.setAttribute('data-pos', 'on');
                } else {
                    root.removeAttribute('data-pos');
                }
                try {
                    localStorage.setItem('kassiron-pos', next ? 'on' : 'off');
                } catch (e) {}
                syncFullScreen(true);
                var search = document.getElementById('product-search');
                if (search) {
                    search.focus();
                }
            });
        });

        syncFullScreen(false);
    })();

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
                // In the desktop app a receipt goes straight to the receipt
                // printer instead (see "silent receipt printing" below).
                if (btn.hasAttribute('data-print-path') && window.kassironDesktop
                    && typeof window.kassironDesktop.printPage === 'function') {
                    return;
                }
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

// Desktop app: code updates announced by the shell — what's new, "Hozir
// yangilash" / "Keyinroq" (the update is applied at the next start anyway),
// and never while a sale is being rung up.
(function () {
    var bridge = window.kassironDesktop;
    var box = document.getElementById('desktop-update');
    if (!bridge || !box || typeof bridge.onUpdateReady !== 'function') {
        return;
    }
    var lang = document.documentElement.lang === 'ru' ? 'ru' : 'uz';
    var text = function (name) { return box.getAttribute('data-' + name) || ''; };

    function el(tag, className, content) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (content) { node.textContent = content; }
        return node;
    }

    function button(label, primary, onClick) {
        var b = el('button', 'btn btn-sm ' + (primary ? 'btn-primary' : 'btn-ghost'), label);
        b.type = 'button';
        b.addEventListener('click', onClick);
        return b;
    }

    function render(title, info, actions) {
        box.innerHTML = '';
        box.appendChild(el('strong', null, title));
        if (info && info.notes !== undefined) {
            box.appendChild(el('div', 'desktop-update-sub', text('whats-new')));
            var list = el('ul', 'desktop-update-notes');
            var notes = (info.notes || []).filter(function (n) { return n && n[lang]; });
            if (notes.length === 0) {
                list.appendChild(el('li', null, text('generic-note')));
            }
            notes.slice(0, 5).forEach(function (n) { list.appendChild(el('li', null, n[lang])); });
            box.appendChild(list);
        }
        var row = el('div', 'row-actions desktop-update-actions');
        actions.forEach(function (a) { row.appendChild(a); });
        box.appendChild(row);
        box.hidden = false;
    }

    function cartHasItems() {
        return document.querySelectorAll('#cart-list .cart-row').length > 0;
    }

    function dismissedKey(info) { return 'kassiron-update-later-' + (info && info.version); }

    bridge.onUpdateReady(function (info) {
        try { if (sessionStorage.getItem(dismissedKey(info))) { return; } } catch (e) { /* no storage */ }
        render(text('ready-title'), info, [
            button(text('apply'), true, function () {
                if (cartHasItems()) {
                    window.alert(text('cart-warning'));
                    return;
                }
                box.hidden = true;
                bridge.applyUpdate();
            }),
            button(text('later'), false, function () {
                try { sessionStorage.setItem(dismissedKey(info), '1'); } catch (e) { /* no storage */ }
                box.hidden = true;
            }),
        ]);
        box.appendChild(el('div', 'desktop-update-sub', text('later-hint')));
    });

    bridge.onUpdateApplied(function (info) {
        render(text('applied-title'), info, [button(text('close'), false, function () { box.hidden = true; })]);
    });

    bridge.onUpdateFailed(function () {
        render(text('failed'), null, [button(text('close'), false, function () { box.hidden = true; })]);
    });

    if (typeof bridge.onShellUpdateReady === 'function') {
        bridge.onShellUpdateReady(function () {
            render(text('shell-ready'), null, [
                button(text('shell-restart'), true, function () {
                    if (cartHasItems()) {
                        window.alert(text('cart-warning'));
                        return;
                    }
                    bridge.restartForShellUpdate();
                }),
                button(text('later'), false, function () { box.hidden = true; }),
            ]);
        });
    }
})();

// "Printers" that only make a file (Microsoft Print to PDF, XPS, OneNote,
// Fax): printing a receipt to one opens Windows' "save as" window in front
// of the till. Never offered or used as the receipt printer.
var KASSIRON_VIRTUAL_PRINTER = /pdf|xps|onenote|fax/i;

// Desktop app, silent receipt printing: "Chop etish" sends the receipt
// straight to this computer's receipt printer (chosen on /desktop/printer,
// kept by the shell) with no print dialog, and right after a sale the
// receipt prints by itself if the printer is set to. No printer chosen yet:
// the ordinary print dialog, with a pointer to the printer settings.
(function () {
    var bridge = window.kassironDesktop;
    var btn = document.getElementById('receipt-print-btn');
    var box = document.getElementById('print-status');
    if (!bridge || !btn || !box || typeof bridge.printPage !== 'function') {
        return;
    }
    var text = function (name) { return box.getAttribute('data-' + name) || ''; };
    var busy = false;

    // On the till page (the sale just rung up) there is no receipt on screen
    // to print through the dialog — that goes through the receipt's own page.
    var receiptUrl = btn.getAttribute('data-receipt-url');
    function printDialog() {
        if (receiptUrl) {
            window.location.href = receiptUrl;
        } else {
            window.print();
        }
    }

    // A sale finished on the till page goes to the server right away.
    if (receiptUrl && typeof bridge.syncNow === 'function') {
        bridge.syncNow();
    }

    function show(message, isError, actions) {
        box.innerHTML = '';
        box.classList.toggle('is-error', !!isError);
        var span = document.createElement('span');
        span.textContent = message;
        box.appendChild(span);
        (actions || []).forEach(function (action) {
            if (action === 'dialog') {
                var dialogBtn = document.createElement('button');
                dialogBtn.type = 'button';
                dialogBtn.className = 'btn btn-sm btn-ghost';
                dialogBtn.textContent = text('dialog');
                dialogBtn.addEventListener('click', printDialog);
                box.appendChild(dialogBtn);
            } else if (action === 'settings') {
                var link = document.createElement('a');
                link.href = '/desktop/printer';
                link.textContent = text('settings');
                box.appendChild(link);
            }
        });
        box.hidden = false;
    }

    function failed(reason) {
        // On the till page the dialog would mean leaving the till.
        show(text('failed').replace(':reason', reason || '?'), true, receiptUrl ? ['settings'] : ['dialog', 'settings']);
    }

    function print(auto) {
        if (busy) {
            return;
        }
        busy = true;
        btn.disabled = true;
        show(text('printing'));
        var settings = typeof bridge.printerSettings === 'function' ? bridge.printerSettings() : Promise.resolve(null);
        settings
            .then(function (saved) {
                if (saved && saved.deviceName && KASSIRON_VIRTUAL_PRINTER.test(saved.deviceName)) {
                    return { ok: false, error: 'no_printer' };
                }
                return bridge.printPage({ path: btn.getAttribute('data-print-path'), paper: Number(btn.getAttribute('data-paper')) || 80 });
            })
            .then(function (result) {
                if (result && result.ok) {
                    show(text('sent'));
                } else if (result && result.error === 'no_printer') {
                    if (auto) {
                        box.hidden = true;
                    } else {
                        show(text('no-printer'), false, ['settings']);
                        if (!receiptUrl) {
                            window.print();
                        }
                    }
                } else {
                    failed(result && result.error);
                }
            })
            .catch(function (e) { failed(e && e.message); })
            .then(function () {
                busy = false;
                btn.disabled = false;
            });
    }

    btn.addEventListener('click', function () { print(false); });

    if (btn.getAttribute('data-autoprint') === '1' && typeof bridge.printerSettings === 'function') {
        bridge.printerSettings().then(function (settings) {
            if (settings && settings.deviceName && settings.autoPrint) {
                print(true);
            }
        }).catch(function () {});
    }
})();

// Desktop app: the receipt printer settings page (/desktop/printer). The
// printers and the choice live in the shell; this page only shows them.
(function () {
    var root = document.getElementById('printer-settings');
    if (!root) {
        return;
    }
    var bridge = window.kassironDesktop;
    var text = function (name) { return root.getAttribute('data-' + name) || ''; };
    var form = document.getElementById('printer-form');
    var select = document.getElementById('printer-select');
    var auto = document.getElementById('printer-auto');
    var copies = document.getElementById('printer-copies');
    var status = document.getElementById('printer-status');
    var buttons = ['printer-save', 'printer-test', 'printer-refresh'].map(function (id) { return document.getElementById(id); });
    var saved = { deviceName: '', autoPrint: true, copies: 1 };

    function show(message, isError) {
        status.textContent = message;
        status.classList.toggle('is-error', !!isError);
        status.hidden = !message;
    }

    function option(value, label) {
        var node = document.createElement('option');
        node.value = value;
        node.textContent = label;
        select.appendChild(node);
    }

    if (!bridge || typeof bridge.printerList !== 'function') {
        select.innerHTML = '';
        option('', text('none'));
        show(text('old-app'), true);
        return;
    }

    function fill(printers) {
        select.innerHTML = '';
        option('', text('none'));
        var names = [];
        if (KASSIRON_VIRTUAL_PRINTER.test(saved.deviceName || '')) {
            saved.deviceName = '';
        }
        printers = printers.filter(function (printer) { return !KASSIRON_VIRTUAL_PRINTER.test(printer.name); });
        printers.forEach(function (printer) {
            names.push(printer.name);
            option(printer.name, (printer.displayName || printer.name) + (printer.isDefault ? ' ' + text('default-mark') : ''));
        });
        // A saved printer that isn't connected right now stays selected.
        if (saved.deviceName && names.indexOf(saved.deviceName) === -1) {
            option(saved.deviceName, saved.deviceName);
        }
        select.value = saved.deviceName || '';
        select.disabled = false;
        buttons.forEach(function (b) { b.disabled = false; });
        show(printers.length ? '' : text('no-printers'), !printers.length);
    }

    function load() {
        Promise.all([bridge.printerSettings(), bridge.printerList()]).then(function (results) {
            saved = results[0] || saved;
            auto.checked = saved.autoPrint !== false;
            copies.value = String(saved.copies || 1);
            fill(results[1] || []);
        }).catch(function (e) { show(text('save-failed') + ' ' + (e && e.message ? e.message : ''), true); });
    }

    function save() {
        return bridge.savePrinterSettings({
            deviceName: select.value,
            autoPrint: auto.checked,
            copies: Number(copies.value) || 1,
        }).then(function (result) {
            if (!result || !result.ok) {
                throw new Error(result && result.error ? result.error : '');
            }
            saved = result.settings;
            return saved;
        });
    }

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        save().then(function () { show(text('saved')); })
            .catch(function () { show(text('save-failed'), true); });
    });

    document.getElementById('printer-test').addEventListener('click', function () {
        if (!select.value) {
            show(text('choose-first'), true);
            return;
        }
        show(text('printing'));
        save().then(function () {
            return bridge.printPage({ path: '/desktop/printer/test', paper: Number(root.getAttribute('data-paper')) || 80 });
        }).then(function (result) {
            if (result && result.ok) {
                show(text('test-sent'));
            } else {
                show(text('failed').replace(':reason', (result && result.error) || '?'), true);
            }
        }).catch(function (e) { show(text('failed').replace(':reason', (e && e.message) || '?'), true); });
    });

    document.getElementById('printer-refresh').addEventListener('click', load);

    load();
})();

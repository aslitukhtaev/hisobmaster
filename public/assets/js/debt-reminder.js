(function () {
    'use strict';

    var root = document.getElementById('debt-reminder');
    if (!root) {
        return;
    }

    var data = window.HM_REMINDER || {};
    var textEl = document.getElementById('reminder-message-text');
    var smsBtn = document.getElementById('reminder-sms-btn');
    var waBtn = document.getElementById('reminder-whatsapp-btn');
    var tgBtn = document.getElementById('reminder-telegram-btn');
    var copyBtn = document.getElementById('reminder-copy-btn');
    var statusEl = document.getElementById('reminder-copy-status');

    function currentMessage() {
        return textEl ? textEl.value : (data.message || '');
    }

    // wa.me needs the full international number without "+". Phones are
    // stored as +998XXXXXXXXX now (normalize_phone()), but one saved
    // earlier may be a bare 9-digit local number, which WhatsApp would
    // treat as a foreign one — so it gets Uzbekistan's 998 (the server
    // already sends this as phoneIntl; this is the fallback).
    function internationalDigits(phone) {
        var digits = String(phone || '').replace(/[^0-9]/g, '');
        return digits.length === 9 ? '998' + digits : digits;
    }

    // iOS's SMS composer only accepts a "&body=" separator after the number;
    // every other platform (Android, desktop SMS-to-web bridges) expects the
    // usual "?body=". There's no feature-detection for this — it's a plain
    // platform quirk — so a simple userAgent check is the accepted way to
    // pick the right one.
    function isIOS() {
        return /iP(hone|od|ad)/.test(navigator.userAgent || '');
    }

    function setDisabled(link, disabled) {
        if (!link) {
            return;
        }
        if (disabled) {
            link.removeAttribute('href');
            link.setAttribute('aria-disabled', 'true');
        } else {
            link.removeAttribute('aria-disabled');
        }
    }

    function updateLinks() {
        var message = currentMessage();
        var encoded = encodeURIComponent(message);
        var phoneDigits = data.phoneIntl || internationalDigits(data.phone);

        if (smsBtn) {
            if (data.phone) {
                var sep = isIOS() ? '&' : '?';
                smsBtn.href = 'sms:' + encodeURIComponent(phoneDigits ? '+' + phoneDigits : data.phone) + sep + 'body=' + encoded;
                setDisabled(smsBtn, false);
            } else {
                setDisabled(smsBtn, true);
            }
        }

        if (waBtn) {
            if (phoneDigits) {
                waBtn.href = 'https://wa.me/' + phoneDigits + '?text=' + encoded;
                setDisabled(waBtn, false);
            } else {
                setDisabled(waBtn, true);
            }
        }

        // Telegram's own share-link scheme — it opens Telegram's UI for the
        // owner to pick a recipient themselves. There is no way to message a
        // customer's Telegram account directly from here (see README/report):
        // this codebase's bot integration is for staff login, not customer
        // messaging, and no customer row is linked to a chat id.
        if (tgBtn) {
            tgBtn.href = 'https://t.me/share/url?url=&text=' + encoded;
        }
    }

    [smsBtn, waBtn].forEach(function (link) {
        if (!link) {
            return;
        }
        link.addEventListener('click', function (e) {
            if (link.getAttribute('aria-disabled') === 'true') {
                e.preventDefault();
            }
        });
    });

    if (textEl) {
        textEl.addEventListener('input', updateLinks);
    }

    if (copyBtn) {
        copyBtn.addEventListener('click', function () {
            var message = currentMessage();

            function showStatus(text) {
                if (!statusEl) {
                    return;
                }
                statusEl.textContent = text;
                setTimeout(function () {
                    statusEl.textContent = '';
                }, 3000);
            }

            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(message).then(function () {
                    showStatus(data.copiedLabel || '');
                }).catch(function () {
                    showStatus(data.copyFailedLabel || '');
                });
                return;
            }

            // Clipboard API unavailable (older browser, non-HTTPS context) —
            // fall back to the legacy select+execCommand trick.
            try {
                if (textEl) {
                    textEl.focus();
                    textEl.select();
                    document.execCommand('copy');
                    showStatus(data.copiedLabel || '');
                } else {
                    showStatus(data.copyFailedLabel || '');
                }
            } catch (e) {
                showStatus(data.copyFailedLabel || '');
            }
        });
    }

    updateLinks();
})();

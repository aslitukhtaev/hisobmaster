<?php
/**
 * Telegram's WebApp SDK only inside the Telegram WebApp: everywhere else it
 * was a blocking script from telegram.org on every page. Telegram opens the
 * app with #tgWebAppData in the address; the tab remembers it for the pages
 * that follow. The desktop app never loads it (it works offline).
 */
if (App\Desktop\Desktop::enabled()) {
    return;
}
?>
<script nonce="<?= e(csp_nonce()) ?>">
    (function () {
        var inTelegram = false;
        try {
            inTelegram = /tgWebApp/.test(location.hash) || !!window.TelegramWebviewProxy
                || sessionStorage.getItem('kassiron-tg') === '1';
            if (inTelegram) {
                sessionStorage.setItem('kassiron-tg', '1');
            }
        } catch (e) {
            inTelegram = /tgWebApp/.test(location.hash) || !!window.TelegramWebviewProxy;
        }
        if (!inTelegram) {
            return;
        }
        var sdk = document.createElement('script');
        sdk.src = 'https://telegram.org/js/telegram-web-app.js';
        sdk.onload = function () {
            // telegram.js looks for the login form: only once the page is there.
            var start = function () {
                var app = document.createElement('script');
                app.src = <?= json_encode(asset('js/telegram.js')) ?>;
                document.head.appendChild(app);
            };
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', start);
            } else {
                start();
            }
        };
        document.head.appendChild(sdk);
    })();
</script>

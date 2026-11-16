<?php $pageTitle = t('login_with_telegram'); ?>
<div class="auth-card">
    <p class="muted" id="tg-callback-status"><?= e(t('telegram_signing_in')) ?></p>
    <p><a href="/login"><?= e(t('back')) ?></a></p>
</div>
<script nonce="<?= e(csp_nonce()) ?>">
    (function () {
        // oauth.telegram.org sends the signed result back in the #fragment
        // (never to the server by itself): base64 JSON of the Login Widget's
        // fields. The server checks the signature.
        var match = /tgAuthResult=([A-Za-z0-9_\-+\/=]+)/.exec(location.hash);
        if (!match) {
            location.replace('/login');
            return;
        }
        var json;
        try {
            var b64 = match[1].replace(/-/g, '+').replace(/_/g, '/');
            while (b64.length % 4) { b64 += '='; }
            json = decodeURIComponent(Array.prototype.map.call(atob(b64), function (c) {
                return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
            }).join(''));
        } catch (e) {
            location.replace('/login');
            return;
        }
        history.replaceState(null, '', location.pathname);
        var body = new URLSearchParams();
        body.set('_csrf', <?= json_encode(csrf_token()) ?>);
        body.set('result', json);
        fetch('/auth/telegram', { method: 'POST', body: body, credentials: 'same-origin', headers: { Accept: 'application/json' } })
            .then(function (r) { return r.json(); })
            .then(function (data) { location.replace((data && data.redirect) || '/login'); })
            .catch(function () { location.replace('/login'); });
    })();
</script>

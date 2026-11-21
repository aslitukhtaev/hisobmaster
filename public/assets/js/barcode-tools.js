(function () {
    'use strict';

    // ---- Barcode picture: EAN-13 when the code is a valid EAN-13, else Code 128.
    function ean13Valid(code) {
        if (!/^\d{13}$/.test(code)) {
            return false;
        }
        var sum = 0;
        for (var i = 0; i < 12; i++) {
            sum += Number(code[i]) * (i % 2 === 0 ? 1 : 3);
        }
        return (10 - (sum % 10)) % 10 === Number(code[12]);
    }
    window.kassironEan13Valid = ean13Valid;

    window.kassironDrawBarcode = function (svg, code, options) {
        options = options || {};
        if (!window.JsBarcode || !code) {
            return false;
        }
        try {
            window.JsBarcode(svg, code, {
                format: ean13Valid(code) ? 'EAN13' : 'CODE128',
                displayValue: options.displayValue !== false,
                fontSize: options.fontSize || 14,
                height: options.height || 50,
                margin: options.margin === undefined ? 0 : options.margin,
                width: options.width || 2
            });
            return true;
        } catch (e) {
            return false;
        }
    };

    function previewFor(input) {
        return document.querySelector('[data-barcode-preview-for="#' + input.id + '"]');
    }

    function refreshPreview(input) {
        var svg = previewFor(input);
        if (!svg) {
            return;
        }
        var code = input.value.trim();
        var ok = code !== '' && window.kassironDrawBarcode(svg, code);
        svg.hidden = !ok;
    }

    // ---- "Kod yaratish": a fresh code from the server goes into the field.
    document.addEventListener('click', function (e) {
        var btn = e.target.closest ? e.target.closest('[data-gen-barcode]') : null;
        if (!btn) {
            return;
        }
        var input = document.querySelector(btn.getAttribute('data-gen-barcode'));
        var csrf = document.querySelector('input[name="_csrf"]');
        if (!input || !csrf) {
            return;
        }
        btn.disabled = true;
        var body = new FormData();
        body.append('_csrf', csrf.value);
        fetch('/barcodes/next', { method: 'POST', body: body, credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data && data.code) {
                    input.value = data.code;
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                }
            })
            .catch(function () { /* the field simply stays as it was */ })
            .then(function () { btn.disabled = false; });
    });

    document.addEventListener('input', function (e) {
        if (e.target && e.target.id && previewFor(e.target)) {
            refreshPreview(e.target);
        }
    });

    window.addEventListener('load', function () {
        document.querySelectorAll('[data-barcode-preview-for]').forEach(function (svg) {
            var input = document.querySelector(svg.getAttribute('data-barcode-preview-for'));
            if (input) {
                refreshPreview(input);
            }
        });
    });

    // ---- Products list: tick products, then "Kod yaratish" / "Yorliq chop etish".
    var bar = document.getElementById('pick-bar');
    if (bar) {
        var count = document.getElementById('pick-count');
        var idsBox = document.getElementById('pick-ids');
        var labelsLink = document.getElementById('pick-labels');
        var all = document.getElementById('pick-all');

        var update = function () {
            var ticked = Array.prototype.slice.call(document.querySelectorAll('.pick-row:checked'));
            bar.hidden = ticked.length === 0;
            count.textContent = (count.getAttribute('data-label') || ':count').replace(':count', ticked.length);
            idsBox.innerHTML = '';
            ticked.forEach(function (c) {
                var h = document.createElement('input');
                h.type = 'hidden';
                h.name = 'ids[]';
                h.value = c.value;
                idsBox.appendChild(h);
            });
            labelsLink.href = '/labels?p=' + ticked.map(function (c) { return c.value; }).join(',');
        };
        document.addEventListener('change', function (e) {
            if (e.target.classList && e.target.classList.contains('pick-row')) {
                update();
            } else if (e.target === all) {
                document.querySelectorAll('.pick-row').forEach(function (c) { c.checked = all.checked; });
                update();
            }
        });
    }
})();

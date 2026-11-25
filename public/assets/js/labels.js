(function () {
    'use strict';

    var data = window.HM_LABELS || {};
    var t = window.HM_LABEL_I18N || {};
    var items = (data.items || []).map(function (it) { return Object.assign({}, it); });
    var presets = data.presets || [];
    var customSizes = data.sizes || [];
    var s = data.settings || {};
    var opts = {
        w: Number(s.w) || 58,
        h: Number(s.h) || 40,
        name: s.name !== false,
        variant: s.variant !== false,
        price: s.price !== false,
        digits: s.digits !== false,
        shop: !!s.shop,
        mode: data.wholesale ? (s.price_mode || 'retail') : 'retail'
    };
    var armedWholesale = false;
    var selected = 0;

    var $ = function (id) { return document.getElementById(id); };
    var csrf = function () { var el = document.querySelector('#label-csrf input[name="_csrf"]'); return el ? el.value : ''; };

    function esc(str) {
        return String(str == null ? '' : str).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }
    function money(n) {
        var v = Math.round(Number(n) * 100) / 100;
        var parts = v.toFixed(v % 1 === 0 ? 0 : 2).split('.');
        parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        return parts.join(',');
    }
    function post(url, fields) {
        var body = new FormData();
        body.append('_csrf', csrf());
        Object.keys(fields || {}).forEach(function (k) { body.append(k, fields[k]); });
        return fetch(url, { method: 'POST', body: body, credentials: 'same-origin' }).then(function (r) { return r.json(); });
    }

    // ---------- one label ----------
    function priceLines(item) {
        if (!opts.price) {
            return [];
        }
        var lines = [];
        if (opts.mode === 'retail' || opts.mode === 'both') {
            lines.push({ mark: opts.mode === 'both' ? (t.retailMark || '') : '', value: item.price });
        }
        if ((opts.mode === 'wholesale' || opts.mode === 'both') && item.wholesale !== null && item.wholesale !== undefined) {
            lines.push({ mark: t.wholesaleMark || 'Optom', value: item.wholesale });
        }
        return lines;
    }

    function makeLabel(item) {
        var el = document.createElement('div');
        el.className = 'lbl';
        el.style.width = opts.w + 'mm';
        el.style.height = opts.h + 'mm';
        // Sizes follow the label: a 30×20 label gets small type, a 58×40 big type.
        var base = Math.min(opts.h / 40, opts.w / 58);
        var html = '';
        if (opts.shop && data.shopName) {
            html += '<div class="lbl-shop" style="font-size:' + (1.9 * base).toFixed(2) + 'mm">' + esc(data.shopName) + '</div>';
        }
        if (opts.name) {
            var name = item.name + (opts.variant && item.label ? ' — ' + item.label : '');
            html += '<div class="lbl-name" style="font-size:' + (3.4 * base).toFixed(2) + 'mm">' + esc(name) + '</div>';
        }
        html += '<div class="lbl-code"><svg></svg></div>';
        var lines = priceLines(item);
        if (lines.length) {
            var size = (lines.length > 1 ? 3.3 : 4.6) * base;
            html += '<div class="lbl-prices">' + lines.map(function (l) {
                return '<div class="lbl-price" style="font-size:' + size.toFixed(2) + 'mm">' +
                    (l.mark ? '<span class="lbl-mark">' + esc(l.mark) + '</span> ' : '') +
                    '<b>' + esc(money(l.value)) + '</b>' + (opts.w >= 40 ? ' <span class="lbl-cur">' + esc(t.currency || '') + '</span>' : '') +
                    '</div>';
            }).join('') + '</div>';
        }
        el.innerHTML = html;
        if (item.barcode) {
            var svg = el.querySelector('svg');
            window.kassironDrawBarcode(svg, item.barcode, {
                displayValue: opts.digits, fontSize: 18, height: 60, width: 2, margin: 0
            });
            // Scale the drawing into the space left on the label, keeping its proportions.
            var w = svg.getAttribute('width'), h = svg.getAttribute('height');
            if (w && h) {
                svg.setAttribute('viewBox', '0 0 ' + parseFloat(w) + ' ' + parseFloat(h));
                svg.removeAttribute('width');
                svg.removeAttribute('height');
                svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
            }
        }
        return el;
    }

    /** Shrinks the type until everything fits the label; false when even small type doesn't. */
    function fit(el) {
        var parts = el.querySelectorAll('.lbl-name, .lbl-price, .lbl-shop');
        for (var i = 0; i < 10 && el.scrollHeight > el.clientHeight + 1; i++) {
            parts.forEach(function (p) { p.style.fontSize = (parseFloat(p.style.fontSize) * 0.9).toFixed(2) + 'mm'; });
        }
        var code = el.querySelector('.lbl-code');
        return el.scrollHeight <= el.clientHeight + 1 && (!code || code.clientHeight >= 6);
    }

    // ---------- page parts ----------
    function renderItems() {
        var box = $('label-items');
        if (!items.length) {
            box.innerHTML = '<p class="muted">' + esc(t.noItems) + '</p>';
            return;
        }
        box.innerHTML = items.map(function (it, i) {
            var warn = '';
            if (!it.barcode) {
                warn += '<span class="label-warn">⚠ ' + esc(t.noBarcode) + ' <button type="button" class="btn btn-ghost btn-sm" data-make="' + i + '">' + esc(t.makeCode) + '</button></span>';
            }
            if (opts.price && opts.mode !== 'retail' && (it.wholesale === null || it.wholesale === undefined)) {
                warn += '<span class="label-warn">⚠ ' + esc(t.noWholesale) + '</span>';
            }
            return '<div class="label-row' + (i === selected ? ' is-selected' : '') + '" data-i="' + i + '">' +
                '<div class="label-row-main"><strong>' + esc(it.name) + '</strong>' + (it.label ? ' <span class="muted">' + esc(it.label) + '</span>' : '') +
                '<div class="muted label-row-code">' + esc(it.barcode || '—') + '</div>' + warn + '</div>' +
                '<div class="qty-stepper">' +
                    '<button type="button" data-dec="' + i + '">−</button>' +
                    '<input type="text" inputmode="numeric" class="qty-direct-input" data-qty="' + i + '" value="' + it.qty + '">' +
                    '<button type="button" data-inc="' + i + '">+</button>' +
                '</div>' +
                '<button type="button" class="cart-remove" data-del="' + i + '" aria-label="' + esc(t.remove) + '">✕</button>' +
            '</div>';
        }).join('');
    }

    function renderSizes() {
        var box = $('label-sizes');
        var isCustom = true;
        var chips = presets.map(function (p) {
            var on = p[0] === opts.w && p[1] === opts.h;
            if (on) { isCustom = false; }
            return '<button type="button" class="label-chip' + (on ? ' active' : '') + '" data-w="' + p[0] + '" data-h="' + p[1] + '">' + p[0] + '×' + p[1] + '</button>';
        });
        customSizes.forEach(function (c, i) {
            var on = c.w === opts.w && c.h === opts.h;
            if (on) { isCustom = false; }
            chips.push('<span class="label-chip-wrap"><button type="button" class="label-chip' + (on ? ' active' : '') + '" data-w="' + c.w + '" data-h="' + c.h + '">' + esc(c.name) + ' <span class="muted">' + c.w + '×' + c.h + '</span></button>' +
                '<button type="button" class="label-chip-x" data-del-size="' + i + '" aria-label="' + esc(t.delete) + '">×</button></span>');
        });
        chips.push('<button type="button" class="label-chip' + (isCustom ? ' active' : '') + '" data-custom="1">' + esc(t.custom) + '</button>');
        box.innerHTML = chips.join('');
        $('label-custom').hidden = !isCustom && !box.dataset.customOpen;
        $('label-w').value = opts.w;
        $('label-h').value = opts.h;
    }

    function renderPreview() {
        var page = $('label-page-style');
        // No page size here: a size wider than tall (58×40) makes the browser
        // print "landscape", and label printer drivers then turn the whole page
        // 90° — the barcode came out sideways across two labels. The page is the
        // printer's own label (its paper size), upright; see printSheet().
        page.textContent = '@page { margin: 0; }';
        var box = $('label-preview');
        box.innerHTML = '';
        var warning = $('label-warning');
        warning.hidden = true;
        if (!items.length) {
            $('label-total').textContent = '';
            return;
        }
        if (selected >= items.length) { selected = 0; }
        var el = makeLabel(items[selected]);
        box.appendChild(el);
        // Shown enlarged so small labels can be read; printing uses the real size.
        var zoom = Math.max(1, Math.min(3, 250 / (opts.w * 3.78)));
        box.style.zoom = zoom.toFixed(2);
        if (!fit(el)) {
            warning.textContent = (t.tooSmall || '').replace(':size', opts.w + '×' + opts.h);
            warning.hidden = false;
        }
        var total = items.reduce(function (sum, it) { return sum + (Number(it.qty) || 0); }, 0);
        $('label-total').textContent = (t.total || ':count').replace(':count', total);
        $('label-hint').textContent = (t.hint || '').replace(':size', opts.w + '×' + opts.h);
        $('label-preview-note').textContent = items[selected].name + (items[selected].label ? ' — ' + items[selected].label : '');
    }

    function renderOpts() {
        $('opt-name').checked = opts.name;
        $('opt-variant').checked = opts.variant;
        $('opt-price').checked = opts.price;
        $('opt-digits').checked = opts.digits;
        $('opt-shop').checked = opts.shop;
        var mode = $('label-mode');
        mode.hidden = !data.wholesale || !opts.price;
        document.querySelectorAll('input[name="label-mode"]').forEach(function (r) { r.checked = r.value === opts.mode; });
    }

    function render() {
        armedWholesale = false;
        $('label-print').textContent = $('label-print').dataset.text || $('label-print').textContent;
        renderItems();
        renderSizes();
        renderOpts();
        renderPreview();
    }

    // ---------- printing ----------
    function print() {
        if (!items.length) {
            return;
        }
        if (items.some(function (it) { return !it.barcode; })) {
            var w = $('label-warning');
            w.textContent = t.needCodes || '';
            w.hidden = false;
            return;
        }
        var missingWholesale = opts.price && opts.mode !== 'retail' && items.some(function (it) { return it.wholesale === null || it.wholesale === undefined; });
        if (missingWholesale && !armedWholesale) {
            // Never a retail price on a wholesale label without the cashier knowing.
            armedWholesale = true;
            var warn = $('label-warning');
            warn.textContent = t.confirmWholesale || '';
            warn.hidden = false;
            $('label-print').textContent = t.printAnyway || '';
            return;
        }
        var sheet = $('label-sheet');
        sheet.innerHTML = '';
        items.forEach(function (it) {
            for (var n = 0; n < (Number(it.qty) || 0); n++) {
                var el = makeLabel(it);
                sheet.appendChild(el);
                fit(el);
            }
        });
        printSheet(sheet.innerHTML);
        armedWholesale = false;
        $('label-print').textContent = $('label-print').dataset.text;
    }

    /**
     * Prints the labels alone, from a hidden frame: one label per page, the
     * page upright and as big as the printer's label (no landscape turn), and
     * nothing of this page behind them (its height used to add blank labels).
     */
    function printSheet(html) {
        var css = '';
        Array.prototype.forEach.call(document.styleSheets, function (sheet) {
            var rules;
            try { rules = sheet.cssRules; } catch (e) { return; }
            Array.prototype.forEach.call(rules || [], function (r) {
                if (r.selectorText && /^\.lbl/.test(r.selectorText)) {
                    css += r.cssText + ' ';
                }
            });
        });
        var old = document.getElementById('label-print-frame');
        if (old) {
            old.parentNode.removeChild(old);
        }
        var frame = document.createElement('iframe');
        frame.id = 'label-print-frame';
        frame.setAttribute('aria-hidden', 'true');
        frame.tabIndex = -1;
        frame.style.cssText = 'position:fixed;left:-10000px;top:0;border:0;height:100px;width:' + opts.w + 'mm';
        document.body.appendChild(frame);
        var doc = frame.contentDocument;
        doc.open();
        doc.write('<!doctype html><html><head><meta charset="utf-8"><style>' + css +
            '@page { margin: 0; }' +
            'html, body { margin: 0; padding: 0; background: #fff; }' +
            '.lbl { box-shadow: none; margin: 0; break-after: page; page-break-after: always; break-inside: avoid; }' +
            '.lbl:last-child { break-after: auto; page-break-after: auto; }' +
            '</style></head><body>' + html + '</body></html>');
        doc.close();
        var win = frame.contentWindow;
        win.addEventListener('afterprint', function () {
            setTimeout(function () { if (frame.parentNode) { frame.parentNode.removeChild(frame); } }, 500);
        });
        setTimeout(function () {
            win.focus();
            win.print();
        }, 50);
    }

    // ---------- events ----------
    document.addEventListener('click', function (e) {
        var b = e.target.closest('button, .label-row');
        if (!b) {
            return;
        }
        var i;
        if (b.dataset.inc !== undefined) {
            i = Number(b.dataset.inc); items[i].qty = Math.min(999, (Number(items[i].qty) || 0) + 1); render();
        } else if (b.dataset.dec !== undefined) {
            i = Number(b.dataset.dec); items[i].qty = Math.max(1, (Number(items[i].qty) || 1) - 1); render();
        } else if (b.dataset.del !== undefined) {
            items.splice(Number(b.dataset.del), 1); render();
        } else if (b.dataset.make !== undefined) {
            i = Number(b.dataset.make);
            b.disabled = true;
            post('/barcodes/assign', { product_id: items[i].product_id, variant_id: items[i].variant_id || 0 }).then(function (r) {
                if (r && r.code) {
                    items.forEach(function (it) {
                        if (it.product_id === items[i].product_id && (it.variant_id || 0) === (items[i].variant_id || 0)) { it.barcode = r.code; }
                    });
                }
                render();
            });
        } else if (b.dataset.w !== undefined) {
            opts.w = Number(b.dataset.w); opts.h = Number(b.dataset.h);
            delete $('label-sizes').dataset.customOpen;
            render();
        } else if (b.dataset.custom) {
            $('label-sizes').dataset.customOpen = '1';
            $('label-custom').hidden = false;
            $('label-w').focus();
        } else if (b.dataset.delSize !== undefined) {
            post('/labels/sizes/delete', { index: b.dataset.delSize, ajax: '1' }).then(function (r) {
                if (r && r.sizes) { customSizes = r.sizes; }
                render();
            });
        } else if (b.classList.contains('label-row')) {
            selected = Number(b.dataset.i); render();
        }
    });

    document.addEventListener('change', function (e) {
        var el = e.target;
        if (el.dataset && el.dataset.qty !== undefined) {
            var q = parseInt(el.value, 10);
            items[Number(el.dataset.qty)].qty = isNaN(q) ? 1 : Math.max(1, Math.min(999, q));
            render();
        } else if (el.id === 'label-w' || el.id === 'label-h') {
            var w = parseInt($('label-w').value, 10), h = parseInt($('label-h').value, 10);
            if (w >= 15 && w <= 200 && h >= 15 && h <= 200) {
                opts.w = w; opts.h = h; render();
            }
        } else if (el.id && el.id.indexOf('opt-') === 0) {
            opts[el.id.slice(4)] = el.checked; render();
        } else if (el.name === 'label-mode') {
            opts.mode = el.value; render();
        }
    });

    $('label-size-save').addEventListener('click', function () {
        post('/labels/sizes', { w: opts.w, h: opts.h, name: $('label-size-name').value }).then(function (r) {
            if (r && r.sizes) {
                customSizes = r.sizes;
                $('label-size-name').value = '';
                delete $('label-sizes').dataset.customOpen;
            }
            render();
        });
    });

    $('label-qty-stock').addEventListener('click', function () {
        items.forEach(function (it) { it.qty = Math.min(999, Math.max(1, Math.floor(it.stock))); }); render();
    });
    $('label-qty-one').addEventListener('click', function () {
        items.forEach(function (it) { it.qty = 1; }); render();
    });

    var saveDefault = $('label-save-default');
    if (saveDefault) {
        saveDefault.addEventListener('click', function () {
            post('/labels/settings', {
                ajax: '1', w: opts.w, h: opts.h, price_mode: opts.mode,
                name: opts.name ? '1' : '0', variant: opts.variant ? '1' : '0', price: opts.price ? '1' : '0',
                digits: opts.digits ? '1' : '0', shop: opts.shop ? '1' : '0'
            }).then(function () { $('label-default-status').textContent = t.savedDefault || ''; });
        });
    }

    // "+ Mahsulot qo'shish": search by name or barcode.
    var search = $('label-search'), results = $('label-search-results'), found = [], timer = null;
    search.addEventListener('input', function () {
        clearTimeout(timer);
        var q = search.value.trim();
        if (!q) { results.hidden = true; return; }
        timer = setTimeout(function () {
            fetch('/labels/search?q=' + encodeURIComponent(q), { credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (r) {
                found = (r && r.items) || [];
                results.innerHTML = found.map(function (it, i) {
                    return '<button type="button" class="label-result" data-add="' + i + '">' + esc(it.name) + (it.label ? ' — ' + esc(it.label) : '') +
                        ' <span class="muted">' + esc(it.barcode || '') + '</span></button>';
                }).join('') || '<p class="muted">—</p>';
                results.hidden = false;
            });
        }, 200);
    });
    results.addEventListener('click', function (e) {
        var b = e.target.closest('[data-add]');
        if (!b) { return; }
        var it = found[Number(b.dataset.add)];
        var same = items.find(function (x) { return x.product_id === it.product_id && (x.variant_id || 0) === (it.variant_id || 0); });
        if (same) { same.qty = Math.min(999, same.qty + 1); } else { items.push(Object.assign({}, it)); selected = items.length - 1; }
        search.value = '';
        results.hidden = true;
        render();
    });

    $('label-print').dataset.text = $('label-print').textContent;
    $('label-print').addEventListener('click', print);

    window.addEventListener('load', render);
})();

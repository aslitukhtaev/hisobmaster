// "Tez o'zgartirish" (products/bulk-edit): every active product and variant in
// a spreadsheet-like grid. Change cells like in Excel — Enter / arrows move up
// and down a column, a column copied from Excel pastes down at once — and
// "Saqlash" sends only the cells that changed.
(function () {
    'use strict';

    var grid = document.getElementById('bulk-grid');
    if (!grid) {
        return;
    }

    var columns = JSON.parse(grid.getAttribute('data-columns') || '[]');
    var i18n = JSON.parse(grid.getAttribute('data-i18n') || '{}');
    var items = (window.HM_BULK_ROWS || []).map(function (r, i) {
        r._i = i;
        r._orig = {};
        columns.forEach(function (c) { r._orig[c.key] = r[c.key] == null ? '' : String(r[c.key]); });
        r._readOnly = {};
        columns.forEach(function (c) { r._readOnly[c.key] = !!c.productOnly && r.type === 'variant'; });
        r._now = Object.assign({}, r._orig);
        return r;
    });
    var tbody = grid.querySelector('tbody');
    var searchEl = document.getElementById('bulk-search');
    var categoryEl = document.getElementById('bulk-category');
    var onlyChangedEl = document.getElementById('bulk-only-changed');
    var countEl = document.getElementById('bulk-count');
    var resultEl = document.getElementById('bulk-result');
    var moreEl = document.getElementById('bulk-more');
    var saveBtn = document.getElementById('bulk-save');
    var LIMIT = 400;
    var errorsByItem = {};

    function fold(text) {
        return String(text || '').toLowerCase().replace(/[‘’ʻʼ`´]/g, "'");
    }

    function changedKeys(item) {
        return columns.filter(function (c) {
            return item._now[c.key].trim() !== item._orig[c.key].trim();
        }).map(function (c) { return c.key; });
    }

    function changedItems() {
        return items.filter(function (item) { return changedKeys(item).length > 0; });
    }

    function updateCount() {
        var n = changedItems().length;
        countEl.textContent = n ? (i18n.changes || ':count').replace(':count', n) : '';
    }

    function visibleItems() {
        var q = fold(searchEl.value.trim());
        var cat = categoryEl ? categoryEl.value : '';
        var only = onlyChangedEl.checked;
        return items.filter(function (item) {
            if (cat && item.category !== cat) {
                return false;
            }
            if (only && !changedKeys(item).length && !errorsByItem[item._i]) {
                return false;
            }
            if (!q) {
                return true;
            }
            return fold(item.name).indexOf(q) !== -1 || fold(item.parent).indexOf(q) !== -1
                || String(item._now.barcode || '').indexOf(q) !== -1;
        });
    }

    function render() {
        var list = visibleItems();
        tbody.innerHTML = '';
        if (!list.length) {
            var tr = document.createElement('tr');
            var td = document.createElement('td');
            td.colSpan = columns.length + 1;
            td.className = 'muted';
            td.textContent = i18n.empty || '';
            tr.appendChild(td);
            tbody.appendChild(tr);
        }
        list.slice(0, LIMIT).forEach(function (item, n) {
            var tr = document.createElement('tr');
            tr.dataset.i = String(item._i);
            if (item.type === 'variant') {
                tr.className = 'bulk-variant';
            }
            var num = document.createElement('td');
            num.className = 'quick-num';
            num.textContent = n + 1;
            tr.appendChild(num);
            columns.forEach(function (c, col) {
                var td = document.createElement('td');
                if (c.number) {
                    td.className = 'quick-number';
                }
                if (c.key === 'name' && item.type === 'variant') {
                    var of = document.createElement('div');
                    of.className = 'bulk-parent';
                    of.textContent = (i18n.variantOf || ':name').replace(':name', item.parent || '');
                    td.appendChild(of);
                }
                var input = document.createElement('input');
                input.type = 'text';
                input.autocomplete = 'off';
                input.spellcheck = false;
                input.inputMode = c.number ? 'decimal' : 'text';
                input.setAttribute('data-col', String(col));
                input.setAttribute('aria-label', c.label + ': ' + item.name);
                input.value = item._now[c.key];
                if (c.list) {
                    input.setAttribute('list', c.list);
                }
                if (item._readOnly[c.key]) {
                    // A variant is in its product's category.
                    input.readOnly = true;
                    input.tabIndex = -1;
                    input.className = 'bulk-readonly';
                }
                td.appendChild(input);
                tr.appendChild(td);
            });
            mark(tr, item);
            tbody.appendChild(tr);
        });
        if (list.length > LIMIT) {
            moreEl.textContent = (i18n.more || '').replace(':shown', LIMIT).replace(':count', list.length);
            moreEl.hidden = false;
        } else {
            moreEl.hidden = true;
        }
        updateCount();
    }

    function itemOf(tr) {
        return items[Number(tr.dataset.i)];
    }

    function mark(tr, item) {
        var keys = changedKeys(item);
        tr.querySelectorAll('input').forEach(function (input) {
            var key = columns[Number(input.getAttribute('data-col'))].key;
            input.classList.toggle('bulk-changed', keys.indexOf(key) !== -1);
        });
        tr.classList.toggle('quick-error', !!errorsByItem[item._i]);
        var old = tr.querySelector('.quick-row-error');
        if (old) {
            old.remove();
        }
        if (errorsByItem[item._i]) {
            var msg = document.createElement('div');
            msg.className = 'quick-row-error';
            msg.textContent = errorsByItem[item._i];
            tr.querySelector('td:nth-child(2)').appendChild(msg);
        }
    }

    function rowsEls() {
        return Array.prototype.slice.call(tbody.querySelectorAll('tr[data-i]'));
    }

    function focusCell(rowIndex, col) {
        var tr = rowsEls()[rowIndex];
        var input = tr && tr.querySelectorAll('input')[col];
        if (input) {
            input.focus();
            input.select();
        }
    }

    tbody.addEventListener('input', function (e) {
        var input = e.target;
        var tr = input.closest('tr');
        if (!tr || input.tagName !== 'INPUT') {
            return;
        }
        var item = itemOf(tr);
        item._now[columns[Number(input.getAttribute('data-col'))].key] = input.value;
        delete errorsByItem[item._i];
        mark(tr, item);
        updateCount();
    });

    tbody.addEventListener('keydown', function (e) {
        var input = e.target;
        if (input.tagName !== 'INPUT') {
            return;
        }
        var tr = input.closest('tr');
        var row = rowsEls().indexOf(tr);
        var col = Number(input.getAttribute('data-col'));
        if (e.key === 'Enter' || e.key === 'ArrowDown') {
            e.preventDefault();
            focusCell(row + 1, col);
        } else if (e.key === 'ArrowUp' && row > 0) {
            e.preventDefault();
            focusCell(row - 1, col);
        } else if (e.key === 'Escape') {
            // Back to the saved value of this cell.
            var item = itemOf(tr);
            var key = columns[col].key;
            input.value = item._orig[key];
            item._now[key] = item._orig[key];
            mark(tr, item);
            updateCount();
        }
    });

    // A column (or block) copied from Excel: down the rows shown, from the cell pasted into.
    tbody.addEventListener('paste', function (e) {
        var input = e.target;
        if (input.tagName !== 'INPUT') {
            return;
        }
        var text = (e.clipboardData || window.clipboardData).getData('text');
        if (!text || (text.indexOf('\t') === -1 && text.indexOf('\n') === -1)) {
            return;
        }
        e.preventDefault();
        var lines = text.replace(/\r/g, '').split('\n');
        while (lines.length && lines[lines.length - 1].trim() === '') {
            lines.pop();
        }
        var trs = rowsEls();
        var start = trs.indexOf(input.closest('tr'));
        var col = Number(input.getAttribute('data-col'));
        lines.forEach(function (line, i) {
            var tr = trs[start + i];
            if (!tr) {
                return;
            }
            var inputs = tr.querySelectorAll('input');
            var item = itemOf(tr);
            line.split('\t').forEach(function (value, j) {
                var cell = inputs[col + j];
                if (cell && !cell.readOnly) {
                    cell.value = value.trim();
                    item._now[columns[col + j].key] = cell.value;
                }
            });
            mark(tr, item);
        });
        updateCount();
    });

    searchEl.addEventListener('input', render);
    if (categoryEl) {
        categoryEl.addEventListener('change', render);
    }
    onlyChangedEl.addEventListener('change', render);

    function showResult(message, isError) {
        resultEl.textContent = message;
        resultEl.classList.toggle('is-error', !!isError);
    }

    saveBtn.addEventListener('click', function () {
        var payload = [];
        changedItems().forEach(function (item) {
            var fields = {};
            changedKeys(item).forEach(function (key) { fields[key] = item._now[key].trim(); });
            payload.push({ _row: item._i, type: item.type, id: item.id, fields: fields });
        });
        if (!payload.length) {
            showResult(i18n.nothing || '', true);
            return;
        }
        var body = new FormData();
        body.append('_csrf', grid.getAttribute('data-csrf'));
        body.append('rows', JSON.stringify(payload));
        saveBtn.disabled = true;
        showResult(i18n.saving || '', false);

        fetch('/products/bulk-edit', { method: 'POST', body: body, credentials: 'same-origin' })
            .then(function (response) {
                return response.json().catch(function () { throw new Error(i18n.failed || ''); });
            })
            .then(function (data) {
                if (!data || !data.ok) {
                    throw new Error(data && data.message ? data.message : '');
                }
                errorsByItem = {};
                (data.errors || []).forEach(function (err) { errorsByItem[err.row] = err.message; });
                (data.saved || []).forEach(function (i) {
                    var item = items[i];
                    if (item) {
                        columns.forEach(function (c) { item._orig[c.key] = item._now[c.key].trim(); item._now[c.key] = item._orig[c.key]; });
                    }
                });
                var errorCount = (data.errors || []).length;
                var message = (i18n.saved || ':count').replace(':count', (data.saved || []).length);
                if (errorCount) {
                    message += ' · ' + (i18n.errors || ':count').replace(':count', errorCount);
                    onlyChangedEl.checked = true;
                }
                render();
                showResult(message, errorCount > 0);
            })
            .catch(function (err) {
                showResult((err && err.message) || i18n.failed || '', true);
            })
            .then(function () {
                saveBtn.disabled = false;
            });
    });

    // Changes not saved yet: ask before leaving the page.
    window.addEventListener('beforeunload', function (e) {
        if (changedItems().length) {
            e.preventDefault();
            e.returnValue = i18n.leave || '';
        }
    });

    render();
})();

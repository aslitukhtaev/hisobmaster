// Quick entry grid (products/quick, customers/quick-debts): type like in a
// spreadsheet — Enter moves to the next cell and on to a new row, arrows move
// up and down, a whole block copied from Excel pastes in at once, a barcode
// scanner "types" its code and Enter jumps on. "Save" sends the filled rows;
// the saved ones leave the grid, a row with a mistake stays, marked.
(function () {
    'use strict';

    var grid = document.getElementById('quick-grid');
    if (!grid) {
        return;
    }

    var columns = JSON.parse(grid.getAttribute('data-columns') || '[]');
    var prefill = JSON.parse(grid.getAttribute('data-prefill') || '{}') || {};
    var i18n = JSON.parse(grid.getAttribute('data-i18n') || '{}');
    var tbody = grid.querySelector('tbody');
    var countEl = document.getElementById('quick-count');
    var resultEl = document.getElementById('quick-result');
    var saveBtn = document.getElementById('quick-save');
    var START_ROWS = 10;

    function cellsOf(tr) {
        return Array.prototype.slice.call(tr.querySelectorAll('input'));
    }

    function rows() {
        return Array.prototype.slice.call(tbody.querySelectorAll('tr'));
    }

    function isFilled(tr) {
        return cellsOf(tr).some(function (input) {
            var column = columns[Number(input.getAttribute('data-col'))];
            return input.value.trim() !== '' && input.value.trim() !== String(column.default || '');
        });
    }

    function renumber() {
        rows().forEach(function (tr, i) {
            tr.querySelector('.quick-num').textContent = i + 1;
        });
        var filled = rows().filter(isFilled).length;
        countEl.textContent = filled ? (i18n.rows || ':count').replace(':count', filled) : '';
    }

    function addRow(values) {
        var tr = document.createElement('tr');
        var num = document.createElement('td');
        num.className = 'quick-num';
        tr.appendChild(num);

        columns.forEach(function (column, col) {
            var td = document.createElement('td');
            var input = document.createElement('input');
            input.type = 'text';
            input.autocomplete = 'off';
            input.spellcheck = false;
            input.setAttribute('data-col', String(col));
            input.setAttribute('aria-label', column.label);
            input.inputMode = column.type === 'number' ? 'decimal' : (column.type === 'tel' ? 'tel' : 'text');
            if (column.placeholder) {
                input.placeholder = column.placeholder;
            }
            if (column.list) {
                input.setAttribute('list', 'quick-list-' + column.key);
            }
            var value = values && values[column.key] !== undefined ? values[column.key] : (column.default || '');
            input.value = value;
            if (column.type === 'number') {
                td.className = 'quick-number';
            }
            td.appendChild(input);
            tr.appendChild(td);
        });

        var x = document.createElement('td');
        x.className = 'quick-x';
        var remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'quick-remove';
        remove.setAttribute('aria-label', i18n.remove || '');
        remove.title = i18n.remove || '';
        remove.textContent = '✕';
        x.appendChild(remove);
        tr.appendChild(x);

        tbody.appendChild(tr);
        return tr;
    }

    function addRows(n) {
        for (var i = 0; i < n; i++) {
            addRow(null);
        }
        renumber();
    }

    function focusCell(rowIndex, col) {
        var all = rows();
        while (rowIndex >= all.length) {
            addRow(null);
            all = rows();
            renumber();
        }
        var input = cellsOf(all[rowIndex])[col];
        if (input) {
            input.focus();
            input.select();
        }
    }

    function position(input) {
        var tr = input.closest('tr');
        return { row: rows().indexOf(tr), col: Number(input.getAttribute('data-col')) };
    }

    tbody.addEventListener('keydown', function (e) {
        var input = e.target;
        if (input.tagName !== 'INPUT') {
            return;
        }
        var at = position(input);
        if (e.key === 'Enter') {
            e.preventDefault();
            if (at.col < columns.length - 1) {
                focusCell(at.row, at.col + 1);
            } else {
                focusCell(at.row + 1, 0);
            }
        } else if (e.key === 'ArrowDown' && !input.getAttribute('list')) {
            e.preventDefault();
            focusCell(at.row + 1, at.col);
        } else if (e.key === 'ArrowUp' && !input.getAttribute('list') && at.row > 0) {
            e.preventDefault();
            focusCell(at.row - 1, at.col);
        }
    });

    tbody.addEventListener('input', function (e) {
        var tr = e.target.closest('tr');
        if (tr) {
            tr.classList.remove('quick-error');
            var msg = tr.querySelector('.quick-row-error');
            if (msg) {
                msg.remove();
            }
        }
        renumber();
    });

    // A block copied from Excel / Google Sheets: tab-separated cells,
    // one line per row — spread it across the grid from the cell pasted into.
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
        var at = position(input);
        lines.forEach(function (line, i) {
            var all = rows();
            while (at.row + i >= all.length) {
                addRow(null);
                all = rows();
            }
            var inputs = cellsOf(all[at.row + i]);
            line.split('\t').forEach(function (value, j) {
                if (inputs[at.col + j]) {
                    inputs[at.col + j].value = value.trim();
                }
            });
        });
        renumber();
    });

    tbody.addEventListener('click', function (e) {
        var remove = e.target.closest('.quick-remove');
        if (!remove) {
            return;
        }
        remove.closest('tr').remove();
        if (rows().length === 0) {
            addRows(1);
        }
        renumber();
    });

    document.getElementById('quick-add-rows').addEventListener('click', function () {
        addRows(START_ROWS);
        focusCell(rows().length - START_ROWS, 0);
    });

    function showResult(message, isError) {
        resultEl.textContent = message;
        resultEl.classList.toggle('is-error', !!isError);
    }

    saveBtn.addEventListener('click', function () {
        var payload = [];
        var byIndex = {};
        rows().forEach(function (tr, i) {
            tr.classList.remove('quick-error');
            var old = tr.querySelector('.quick-row-error');
            if (old) {
                old.remove();
            }
            if (!isFilled(tr)) {
                return;
            }
            var row = { _row: i };
            cellsOf(tr).forEach(function (input) {
                row[columns[Number(input.getAttribute('data-col'))].key] = input.value.trim();
            });
            payload.push(row);
            byIndex[i] = tr;
        });
        if (payload.length === 0) {
            showResult(i18n.nothing || '', true);
            focusCell(0, 0);
            return;
        }

        var body = new FormData();
        body.append('_csrf', grid.getAttribute('data-csrf'));
        body.append('rows', JSON.stringify(payload));
        // Fields above the grid that apply to the whole batch (the category).
        document.querySelectorAll('[data-quick-extra]').forEach(function (el) {
            body.append(el.name, el.value.trim());
        });
        saveBtn.disabled = true;
        showResult(i18n.saving || '', false);

        fetch(grid.getAttribute('data-endpoint'), { method: 'POST', body: body, credentials: 'same-origin' })
            .then(function (response) {
                return response.json().catch(function () { throw new Error(i18n.failed || ''); });
            })
            .then(function (data) {
                if (!data || !data.ok) {
                    throw new Error(data && data.message ? data.message : '');
                }
                (data.errors || []).forEach(function (err) {
                    var tr = byIndex[err.row];
                    if (!tr) {
                        return;
                    }
                    tr.classList.add('quick-error');
                    var msg = document.createElement('div');
                    msg.className = 'quick-row-error';
                    msg.textContent = err.message;
                    tr.querySelector('td:nth-child(2)').appendChild(msg);
                });
                (data.created || []).forEach(function (index) {
                    if (byIndex[index]) {
                        byIndex[index].remove();
                    }
                });
                var errorCount = (data.errors || []).length;
                var message = (i18n.saved || ':created').replace(':created', (data.created || []).length);
                if (errorCount) {
                    message += ' · ' + (i18n.errors || ':count').replace(':count', errorCount);
                }
                showResult(message, errorCount > 0);
                if (rows().length < START_ROWS) {
                    addRows(START_ROWS - rows().length);
                }
                renumber();
                var firstError = tbody.querySelector('tr.quick-error input');
                if (firstError) {
                    firstError.focus();
                } else {
                    focusCell(0, 0);
                }
            })
            .catch(function (e) {
                showResult((e && e.message) || i18n.failed || '', true);
            })
            .then(function () {
                saveBtn.disabled = false;
            });
    });

    addRow(prefill);
    addRows(START_ROWS - 1);
    focusCell(0, prefill && Object.keys(prefill).length ? 1 : 0);
})();

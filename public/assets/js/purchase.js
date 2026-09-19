(function () {
    'use strict';

    var products = window.HM_PURCHASE_PRODUCTS || [];
    var i18n = window.HM_PURCHASE_I18N || {};

    var bodyEl = document.getElementById('purchase-items-body');
    var addBtn = document.getElementById('add-purchase-item-btn');
    var totalEl = document.getElementById('purchase-total');
    var itemsFieldEl = document.getElementById('purchase-items-field');
    var submitBtn = document.getElementById('submit-purchase-btn');
    var updateCostCheckbox = document.getElementById('update-cost-price-checkbox');
    var updateCostField = document.getElementById('update-cost-price-field');

    if (!bodyEl) {
        return;
    }

    // Each row: { productId: number|'', qty: number, packs: number, unitCost: number }
    var rows = [];
    var nextRowId = 1;

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str == null ? '' : String(str);
        return div.innerHTML;
    }

    function formatMoney(n) {
        var rounded = Math.round(n);
        return rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ' ' + (i18n.currency || "so'm");
    }

    function productById(id) {
        return products.find(function (p) { return p.id === id; });
    }

    function addRow() {
        rows.push({ id: nextRowId++, productId: '', qty: 0, packs: 0, unitCost: 0 });
        render();
    }

    function removeRow(rowId) {
        rows = rows.filter(function (r) { return r.id !== rowId; });
        render();
    }

    function render() {
        bodyEl.innerHTML = rows.map(function (row) {
            var product = row.productId !== '' ? productById(row.productId) : null;
            var packSize = product && product.pack_size ? product.pack_size : null;

            var options = '<option value="">—</option>' + products.map(function (p) {
                return '<option value="' + p.id + '"' + (row.productId === p.id ? ' selected' : '') + '>' + escapeHtml(p.name) + '</option>';
            }).join('');

            var packsCell = '';
            if (packSize) {
                packsCell =
                    '<input type="number" class="item-packs" data-row="' + row.id + '" min="0" step="1" inputmode="numeric" ' +
                    'placeholder="' + escapeHtml(i18n.packsLabel || 'packs') + '" value="' + (row.packs || '') + '">' +
                    '<div class="purchase-pack-hint">' + escapeHtml((i18n.packHint || '1 = :n :unit').replace(':n', packSize).replace(':unit', product.unit)) + '</div>';
            }

            var subtotal = (row.qty || 0) * (row.unitCost || 0);

            return (
                '<tr data-row="' + row.id + '">' +
                    '<td><select class="item-product" data-row="' + row.id + '">' + options + '</select></td>' +
                    '<td>' +
                        '<input type="number" class="item-qty" data-row="' + row.id + '" min="0" step="0.01" inputmode="decimal" value="' + (row.qty || '') + '">' +
                        packsCell +
                    '</td>' +
                    '<td><input type="number" class="item-unit-cost" data-row="' + row.id + '" min="0" step="0.01" inputmode="decimal" value="' + (row.unitCost || '') + '"></td>' +
                    '<td class="purchase-item-subtotal">' + escapeHtml(formatMoney(subtotal)) + '</td>' +
                    '<td><button type="button" class="purchase-item-remove" data-row="' + row.id + '" aria-label="' + escapeHtml(i18n.removeRow || 'Remove') + '">✕</button></td>' +
                '</tr>'
            );
        }).join('');

        updateTotals();
    }

    function updateSubtotalCell(rowId, row) {
        var cell = bodyEl.querySelector('tr[data-row="' + rowId + '"] .purchase-item-subtotal');
        if (cell) {
            cell.textContent = formatMoney((row.qty || 0) * (row.unitCost || 0));
        }
    }

    function updateTotals() {
        var total = 0;
        var validItems = [];

        rows.forEach(function (row) {
            var subtotal = (row.qty || 0) * (row.unitCost || 0);
            total += subtotal;
            if (row.productId !== '' && row.qty > 0) {
                validItems.push({ product_id: row.productId, qty: row.qty, unit_cost: row.unitCost || 0 });
            }
        });

        totalEl.textContent = formatMoney(total);
        itemsFieldEl.value = JSON.stringify(validItems);
        submitBtn.disabled = validItems.length === 0;
    }

    addBtn.addEventListener('click', addRow);

    bodyEl.addEventListener('change', function (e) {
        var rowId = parseInt(e.target.getAttribute('data-row'), 10);
        var row = rows.find(function (r) { return r.id === rowId; });
        if (!row) {
            return;
        }

        if (e.target.classList.contains('item-product')) {
            row.productId = e.target.value !== '' ? parseInt(e.target.value, 10) : '';
            var product = row.productId !== '' ? productById(row.productId) : null;
            if (product && !row.unitCost) {
                row.unitCost = product.cost_price || 0;
            }
            render();
            return;
        }

        if (e.target.classList.contains('item-qty')) {
            // A direct qty edit overrides whatever the packs shortcut computed.
            // This deliberately does NOT call render(): a full row rebuild here
            // would run synchronously during this field's blur (e.g. the
            // cashier tabbing straight from qty into unit-cost), destroying and
            // recreating that next field out from under the browser's own
            // focus-transfer — dropping focus (and any keystroke already in
            // flight) instead of landing it in the new node. Every other field
            // in the row is updated surgically instead, same as the packs
            // branch below.
            row.qty = parseFloat(e.target.value) || 0;
            row.packs = 0;
            var packsInput = bodyEl.querySelector('tr[data-row="' + rowId + '"] .item-packs');
            if (packsInput) {
                packsInput.value = '';
            }
            updateSubtotalCell(rowId, row);
            updateTotals();
            return;
        }

        if (e.target.classList.contains('item-packs')) {
            // Same reasoning as item-qty above: update the qty input's value
            // directly rather than re-rendering the row, so a blur/tab into
            // the next field (unit cost) never races a synchronous rebuild.
            var product2 = row.productId !== '' ? productById(row.productId) : null;
            var packSize = product2 && product2.pack_size ? product2.pack_size : 1;
            row.packs = parseFloat(e.target.value) || 0;
            row.qty = Math.round(row.packs * packSize * 100) / 100;
            var qtyInput = bodyEl.querySelector('tr[data-row="' + rowId + '"] .item-qty');
            if (qtyInput) {
                qtyInput.value = row.qty || '';
            }
            updateSubtotalCell(rowId, row);
            updateTotals();
            return;
        }

        if (e.target.classList.contains('item-unit-cost')) {
            row.unitCost = parseFloat(e.target.value) || 0;
            updateTotals();
            // Only the subtotal cell needs to change — update it directly
            // rather than re-rendering the whole row, so focus isn't lost.
            updateSubtotalCell(rowId, row);
        }
    });

    // Live subtotal/total feedback while typing qty/unit-cost, without a full
    // re-render (which would rebuild the <select>/<input> elements and steal
    // focus mid-keystroke). The 'change' listener above still handles the
    // heavier product/packs cases that do need a full row re-render.
    bodyEl.addEventListener('input', function (e) {
        var isQty = e.target.classList.contains('item-qty');
        var isCost = e.target.classList.contains('item-unit-cost');
        if (!isQty && !isCost) {
            return;
        }

        var rowId = parseInt(e.target.getAttribute('data-row'), 10);
        var row = rows.find(function (r) { return r.id === rowId; });
        if (!row) {
            return;
        }

        if (isQty) {
            row.qty = parseFloat(e.target.value) || 0;
        } else {
            row.unitCost = parseFloat(e.target.value) || 0;
        }

        updateSubtotalCell(rowId, row);
        updateTotals();
    });

    bodyEl.addEventListener('click', function (e) {
        var btn = e.target.closest('.purchase-item-remove');
        if (btn) {
            removeRow(parseInt(btn.getAttribute('data-row'), 10));
        }
    });

    if (updateCostCheckbox && updateCostField) {
        updateCostCheckbox.addEventListener('change', function () {
            updateCostField.value = updateCostCheckbox.checked ? '1' : '0';
        });
    }

    addRow();
})();

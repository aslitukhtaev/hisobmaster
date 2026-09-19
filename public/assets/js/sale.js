(function () {
    'use strict';

    var products = window.HM_PRODUCTS || [];
    var customers = window.HM_CUSTOMERS || [];
    var i18n = window.HM_I18N || {};
    var oldCart = window.HM_OLD_CART || [];
    var oldSale = window.HM_OLD_SALE || {};

    // Units a product can be sold in fractional amounts of (checked
    // case-insensitively). Anything else ("dona", "pcs", ...) stays whole-unit
    // only, exactly like before.
    var FRACTIONAL_UNITS = ['kg', 'litr', 'l', 'metr', 'm'];
    var FRACTIONAL_STEP = 0.1;

    var cart = new Map(); // product_id -> qty
    var discount = 0;
    var discountMode = 'amount'; // 'amount' | 'percent'

    // naqd/karta amounts the cashier explicitly entered. A "preset" keeps the
    // corresponding field pinned to the live total as the cart changes (the
    // one-click single-payment-type flow); editing a field by hand drops into
    // free/custom mode so a genuine split sticks instead of being overwritten
    // on the next render.
    var naqdAmount = 0;
    var kartaAmount = 0;
    var activePreset = 'naqd';
    // Whichever field the cashier last typed into keeps the value they typed
    // (clamped only to the total); the other one yields to make room for it.
    // Without this, editing karta while naqd was still pinned to the old total
    // would just clamp karta straight back down to 0 instead of actually
    // making a split.
    var lastEditedField = 'naqd';

    var productListEl = document.getElementById('product-list');
    var searchEl = document.getElementById('product-search');
    var cartListEl = document.getElementById('cart-list');
    var cartEmptyMsgEl = document.getElementById('cart-empty-msg');
    var cartTotalEl = document.getElementById('cart-total');
    var discountInputEl = document.getElementById('discount-input');
    var discountComputedLabelEl = document.getElementById('discount-computed-label');
    var completeBtnEl = document.getElementById('complete-sale-btn');
    var debtFieldsEl = document.getElementById('debt-fields');
    var customerSelectEl = document.getElementById('customer-select');
    var newCustomerFieldsEl = document.getElementById('new-customer-fields');
    var newCustomerNameEl = document.getElementById('new-customer-name');
    var newCustomerPhoneEl = document.getElementById('new-customer-phone');
    var debtRemainingLabelEl = document.getElementById('debt-remaining-label');
    var saleFormEl = document.getElementById('sale-form');
    var naqdAmountInputEl = document.getElementById('naqd-amount-input');
    var kartaAmountInputEl = document.getElementById('karta-amount-input');

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str == null ? '' : String(str);
        return div.innerHTML;
    }

    function formatMoney(n) {
        var rounded = Math.round(n);
        return rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ' ' + (i18n.currency || "so'm");
    }

    function formatQty(n) {
        if (Math.floor(n) === n) {
            return n.toString();
        }
        return (Math.round(n * 100) / 100).toString();
    }

    function roundQty(n) {
        return Math.round(n * 100) / 100;
    }

    function isFractionalUnit(unit) {
        return FRACTIONAL_UNITS.indexOf(String(unit || '').toLowerCase()) !== -1;
    }

    function productById(id) {
        return products.find(function (p) { return p.id === id; });
    }

    function filterProducts(query) {
        var q = (query || '').trim().toLowerCase();
        if (!q) {
            return products;
        }
        return products.filter(function (p) {
            return p.name.toLowerCase().indexOf(q) !== -1 ||
                (p.barcode && String(p.barcode).toLowerCase().indexOf(q) !== -1);
        });
    }

    function renderProducts() {
        var list = filterProducts(searchEl.value);

        if (list.length === 0) {
            productListEl.innerHTML = '<p class="muted">' + escapeHtml(i18n.noResults || '') + '</p>';
            return;
        }

        productListEl.innerHTML = list.map(function (p) {
            return '<button type="button" class="pos-product" data-id="' + p.id + '">' +
                '<span class="pos-product-name">' + escapeHtml(p.name) + '</span>' +
                '<span class="pos-product-meta">' + escapeHtml(formatMoney(p.price)) + ' · ' + escapeHtml(formatQty(p.stock)) + ' ' + escapeHtml(p.unit) + '</span>' +
                '</button>';
        }).join('');
    }

    function addToCart(id) {
        var product = productById(id);
        if (!product) {
            return;
        }
        var currentQty = cart.get(id) || 0;
        // Tapping a fractional-unit product in the list still adds a plain "1"
        // the first time, same as a whole-unit product — the 0.1 step only
        // applies to the +/- stepper once it's in the cart, so a cashier
        // reaching for the direct qty input to type an exact amount (e.g. 1.35
        // kg) starts from a sensible whole number instead of a stray "0.1".
        var next = roundQty(currentQty + 1);
        if (next > product.stock) {
            alert(i18n.stockLimitReached || 'Stock limit reached');
            return;
        }
        cart.set(id, next);
        renderCart();
    }

    function changeQty(id, direction) {
        var product = productById(id);
        var currentQty = cart.get(id) || 0;
        var step = product && isFractionalUnit(product.unit) ? FRACTIONAL_STEP : 1;
        var next = roundQty(currentQty + direction * step);

        if (next <= 0) {
            cart.delete(id);
        } else if (product && next > product.stock) {
            alert(i18n.stockLimitReached || 'Stock limit reached');
            return;
        } else {
            cart.set(id, next);
        }
        renderCart();
    }

    function setQty(id, qty) {
        var product = productById(id);
        if (!product) {
            return;
        }
        var next = roundQty(qty);
        if (next <= 0) {
            cart.delete(id);
        } else {
            if (next > product.stock) {
                next = product.stock;
                alert(i18n.stockLimitReached || 'Stock limit reached');
            }
            cart.set(id, next);
        }
        renderCart();
    }

    function cartSubtotal() {
        var subtotal = 0;
        cart.forEach(function (qty, id) {
            var product = productById(id);
            if (product) {
                subtotal += product.price * qty;
            }
        });
        return subtotal;
    }

    function discountAmount(subtotal) {
        if (discountMode === 'percent') {
            var pct = Math.max(0, Math.min(100, parseFloat(discountInputEl.value) || 0));
            return Math.round(subtotal * pct / 100 * 100) / 100;
        }
        return Math.max(0, parseFloat(discountInputEl.value) || 0);
    }

    function renderCart() {
        if (cart.size === 0) {
            cartListEl.innerHTML = '';
            cartEmptyMsgEl.style.display = 'block';
            completeBtnEl.disabled = true;
        } else {
            cartEmptyMsgEl.style.display = 'none';
            completeBtnEl.disabled = false;

            var rows = [];
            cart.forEach(function (qty, id) {
                var product = productById(id);
                if (!product) {
                    return;
                }
                var lineTotal = product.price * qty;
                var fractional = isFractionalUnit(product.unit);
                var qtyControl = fractional
                    ? '<input type="number" class="qty-direct-input" data-id="' + id + '" min="0.01" max="' + product.stock + '" step="0.01" value="' + qty + '" inputmode="decimal" aria-label="' + escapeHtml(i18n.editQty || '') + ' — ' + escapeHtml(product.name) + '">'
                    : '<span>' + escapeHtml(formatQty(qty)) + '</span>';

                rows.push(
                    '<div class="cart-row" data-id="' + id + '">' +
                        '<div class="cart-row-main">' +
                            '<div class="cart-row-name" title="' + escapeHtml(product.name) + '">' + escapeHtml(product.name) + '</div>' +
                            '<div class="cart-row-price">' + escapeHtml(formatMoney(product.price)) + ' / ' + escapeHtml(product.unit) + '</div>' +
                        '</div>' +
                        '<div class="qty-stepper' + (fractional ? ' qty-stepper-fractional' : '') + '">' +
                            '<button type="button" data-action="dec" data-id="' + id + '" aria-label="' + escapeHtml(i18n.decreaseQty || '') + '">−</button>' +
                            qtyControl +
                            '<button type="button" data-action="inc" data-id="' + id + '" aria-label="' + escapeHtml(i18n.increaseQty || '') + '">+</button>' +
                        '</div>' +
                        '<div class="cart-row-subtotal">' + escapeHtml(formatMoney(lineTotal)) + '</div>' +
                        '<button type="button" class="cart-remove" data-action="remove" data-id="' + id + '" aria-label="' + escapeHtml(i18n.removeFromCart || '') + '">✕</button>' +
                    '</div>'
                );
            });
            cartListEl.innerHTML = rows.join('');
        }

        var subtotal = cartSubtotal();
        discount = discountAmount(subtotal);
        var total = Math.max(0, Math.round((subtotal - discount) * 100) / 100);
        cartTotalEl.textContent = formatMoney(total);
        document.getElementById('discount-field').value = discount;

        if (discountMode === 'percent') {
            discountComputedLabelEl.style.display = 'block';
            discountComputedLabelEl.textContent = (i18n.discountEqualsLabel || '= :amount').replace(':amount', formatMoney(discount));
        } else {
            discountComputedLabelEl.style.display = 'none';
        }

        document.getElementById('cart-field').value = JSON.stringify(
            Array.from(cart, function (entry) { return { product_id: entry[0], qty: entry[1] }; })
        );

        applyPayments(total);
    }

    // Keeps naqd/karta amounts consistent with the live total and with each
    // other: a preset re-syncs its field to the current total on every
    // render (so the common single-payment case just tracks the cart as it
    // changes); free/custom edits are clamped so naqd+karta never exceed the
    // total, and whatever's left over becomes the qarz (debt) remainder.
    function applyPayments(total) {
        if (activePreset === 'naqd') {
            naqdAmount = total;
            kartaAmount = 0;
        } else if (activePreset === 'karta') {
            naqdAmount = 0;
            kartaAmount = total;
        } else if (activePreset === 'qarz') {
            naqdAmount = 0;
            kartaAmount = 0;
        } else if (lastEditedField === 'karta') {
            kartaAmount = Math.max(0, Math.min(kartaAmount, total));
            naqdAmount = Math.max(0, Math.min(naqdAmount, Math.round((total - kartaAmount) * 100) / 100));
        } else {
            naqdAmount = Math.max(0, Math.min(naqdAmount, total));
            kartaAmount = Math.max(0, Math.min(kartaAmount, Math.round((total - naqdAmount) * 100) / 100));
        }

        naqdAmountInputEl.value = naqdAmount;
        kartaAmountInputEl.value = kartaAmount;
        document.getElementById('naqd-amount-field').value = naqdAmount;
        document.getElementById('karta-amount-field').value = kartaAmount;

        document.querySelectorAll('.payment-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-preset') === activePreset);
        });

        var remaining = Math.max(0, Math.round((total - naqdAmount - kartaAmount) * 100) / 100);
        debtFieldsEl.style.display = remaining > 0 ? 'block' : 'none';
        debtRemainingLabelEl.textContent = (i18n.debtRemainingLabel || 'Debt: :amount').replace(':amount', formatMoney(remaining));
    }

    function setPreset(preset) {
        activePreset = preset;
        renderCart();
    }

    function populateCustomerSelect() {
        customers.forEach(function (c) {
            var opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = c.name + (c.phone ? ' — ' + c.phone : '');
            customerSelectEl.appendChild(opt);
        });
    }

    function updateCustomerFields() {
        var selected = customerSelectEl.value;
        if (selected) {
            newCustomerFieldsEl.style.display = 'none';
            document.getElementById('customer-id-field').value = selected;
            document.getElementById('customer-name-field').value = '';
            document.getElementById('customer-phone-field').value = '';
        } else {
            newCustomerFieldsEl.style.display = 'grid';
            document.getElementById('customer-id-field').value = '';
        }
    }

    productListEl.addEventListener('click', function (e) {
        var btn = e.target.closest('.pos-product');
        if (btn) {
            addToCart(parseInt(btn.getAttribute('data-id'), 10));
        }
    });

    searchEl.addEventListener('input', renderProducts);
    searchEl.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            var matches = filterProducts(searchEl.value);
            if (matches.length === 1) {
                addToCart(matches[0].id);
                searchEl.value = '';
                renderProducts();
            }
        }
    });

    cartListEl.addEventListener('click', function (e) {
        var btn = e.target.closest('button[data-action]');
        if (!btn) {
            return;
        }
        var id = parseInt(btn.getAttribute('data-id'), 10);
        var action = btn.getAttribute('data-action');
        if (action === 'inc') {
            changeQty(id, 1);
        } else if (action === 'dec') {
            changeQty(id, -1);
        } else if (action === 'remove') {
            cart.delete(id);
            renderCart();
        }
    });

    // Direct numeric qty entry (fractional-unit products only) commits on
    // change (blur/Enter) rather than every keystroke, so a full re-render
    // doesn't steal focus mid-type.
    cartListEl.addEventListener('change', function (e) {
        if (e.target.classList.contains('qty-direct-input')) {
            var id = parseInt(e.target.getAttribute('data-id'), 10);
            setQty(id, parseFloat(e.target.value) || 0);
        }
    });

    discountInputEl.addEventListener('input', renderCart);

    document.querySelectorAll('.discount-mode-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var mode = btn.getAttribute('data-mode');
            if (mode === discountMode) {
                return;
            }
            discountMode = mode;
            discountInputEl.value = 0;
            discountInputEl.setAttribute('max', mode === 'percent' ? '100' : '');
            document.querySelectorAll('.discount-mode-btn').forEach(function (b) {
                b.classList.toggle('active', b === btn);
            });
            renderCart();
        });
    });

    document.querySelectorAll('.payment-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            setPreset(btn.getAttribute('data-preset'));
        });
    });

    naqdAmountInputEl.addEventListener('input', function () {
        activePreset = null;
        lastEditedField = 'naqd';
        naqdAmount = Math.max(0, parseFloat(naqdAmountInputEl.value) || 0);
        renderCart();
    });
    kartaAmountInputEl.addEventListener('input', function () {
        activePreset = null;
        lastEditedField = 'karta';
        kartaAmount = Math.max(0, parseFloat(kartaAmountInputEl.value) || 0);
        renderCart();
    });

    customerSelectEl.addEventListener('change', updateCustomerFields);

    newCustomerNameEl.addEventListener('input', function () {
        document.getElementById('customer-name-field').value = newCustomerNameEl.value;
    });
    newCustomerPhoneEl.addEventListener('input', function () {
        document.getElementById('customer-phone-field').value = newCustomerPhoneEl.value;
    });

    saleFormEl.addEventListener('submit', function (e) {
        if (cart.size === 0) {
            e.preventDefault();
            return;
        }
        completeBtnEl.disabled = true;
    });

    // After a validation failure the server redirects back here with the
    // submitted cart/discount/payment/customer state flashed via old() — restore
    // it so the cashier doesn't have to rebuild the cart from scratch.
    function restoreOldState() {
        if (Array.isArray(oldCart)) {
            oldCart.forEach(function (row) {
                var id = parseInt(row.product_id, 10);
                var qty = parseFloat(row.qty);
                if (!isNaN(id) && !isNaN(qty) && qty > 0 && productById(id)) {
                    cart.set(id, qty);
                }
            });
        }

        if (oldSale.discount !== undefined && oldSale.discount !== '') {
            var restoredDiscount = parseFloat(oldSale.discount);
            if (!isNaN(restoredDiscount)) {
                discount = restoredDiscount;
                discountInputEl.value = restoredDiscount;
            }
        }

        if (oldSale.customerId) {
            customerSelectEl.value = oldSale.customerId;
        }
        updateCustomerFields();

        if (!oldSale.customerId) {
            if (oldSale.customerName) {
                newCustomerNameEl.value = oldSale.customerName;
                document.getElementById('customer-name-field').value = oldSale.customerName;
            }
            if (oldSale.customerPhone) {
                newCustomerPhoneEl.value = oldSale.customerPhone;
                document.getElementById('customer-phone-field').value = oldSale.customerPhone;
            }
        }

        var hasOldNaqd = oldSale.naqdAmount !== undefined && oldSale.naqdAmount !== '';
        var hasOldKarta = oldSale.kartaAmount !== undefined && oldSale.kartaAmount !== '';
        if (hasOldNaqd || hasOldKarta) {
            activePreset = null;
            naqdAmount = hasOldNaqd ? (parseFloat(oldSale.naqdAmount) || 0) : 0;
            kartaAmount = hasOldKarta ? (parseFloat(oldSale.kartaAmount) || 0) : 0;
        }
    }

    populateCustomerSelect();
    renderProducts();
    restoreOldState();
    renderCart();
})();

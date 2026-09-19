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

    // Cart entries are keyed by a composite string ("p12" for a plain product,
    // "v34" for a specific variant) rather than the bare product id, since a
    // product with variants can have several distinct cart lines — one per
    // variant — all sharing the same product_id.
    var cart = new Map(); // key -> { productId, variantId, qty }
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

    function variantOf(product, variantId) {
        if (!product || !variantId || !product.variants) {
            return null;
        }
        return product.variants.find(function (v) { return v.id === variantId; }) || null;
    }

    function cartKey(productId, variantId) {
        return variantId ? ('v' + variantId) : ('p' + productId);
    }

    /**
     * Resolves a cart entry's display name/unit/price/stock, looking at the
     * variant (when present) with the parent product as fallback — the same
     * "variant overrides, else parent" rule Sale::create() applies.
     */
    function resolveEntry(entry) {
        var product = productById(entry.productId);
        if (!product) {
            return null;
        }
        var variant = entry.variantId ? variantOf(product, entry.variantId) : null;

        return {
            product: product,
            variant: variant,
            name: variant ? (product.name + ' — ' + variant.label) : product.name,
            unit: product.unit,
            price: variant ? variant.price : product.price,
            stock: variant ? variant.stock : product.stock
        };
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
            if (!p.variants || p.variants.length === 0) {
                return '<button type="button" class="pos-product" data-id="' + p.id + '">' +
                    '<span class="pos-product-name">' + escapeHtml(p.name) + '</span>' +
                    '<span class="pos-product-meta">' + escapeHtml(formatMoney(p.price)) + ' · ' + escapeHtml(formatQty(p.stock)) + ' ' + escapeHtml(p.unit) + '</span>' +
                    '</button>';
            }

            // A product with variants can't be added directly — the cashier
            // must pick a specific one, since stock/price are tracked per
            // variant (see Sale::create()).
            var variantButtons = p.variants.map(function (v) {
                var disabled = v.stock <= 0;
                return '<button type="button" class="pos-variant-btn" data-id="' + p.id + '" data-variant="' + v.id + '"' + (disabled ? ' disabled' : '') + '>' +
                    '<span>' + escapeHtml(v.label) + '</span>' +
                    '<span class="pos-product-meta">' + escapeHtml(formatMoney(v.price)) + ' · ' + escapeHtml(formatQty(v.stock)) + ' ' + escapeHtml(p.unit) + '</span>' +
                    '</button>';
            }).join('');

            return '<div class="pos-product-group">' +
                '<div class="pos-product" style="cursor:default;">' +
                    '<span class="pos-product-name">' + escapeHtml(p.name) + '</span>' +
                    '<span class="pos-variant-toggle-hint">' + escapeHtml(i18n.pickVariantHint || '') + '</span>' +
                '</div>' +
                '<div class="pos-variant-list">' + variantButtons + '</div>' +
            '</div>';
        }).join('');
    }

    function addToCart(productId, variantId) {
        var product = productById(productId);
        if (!product) {
            return;
        }
        var variant = variantId ? variantOf(product, variantId) : null;
        if (product.variants && product.variants.length > 0 && !variant) {
            // Shouldn't happen through the UI (variant-bearing products only
            // ever render variant buttons), but guard against it anyway.
            return;
        }

        var key = cartKey(productId, variantId);
        var currentQty = cart.has(key) ? cart.get(key).qty : 0;
        var stock = variant ? variant.stock : product.stock;

        // Tapping a fractional-unit product in the list still adds a plain "1"
        // the first time, same as a whole-unit product — the 0.1 step only
        // applies to the +/- stepper once it's in the cart, so a cashier
        // reaching for the direct qty input to type an exact amount (e.g. 1.35
        // kg) starts from a sensible whole number instead of a stray "0.1".
        var next = roundQty(currentQty + 1);
        if (next > stock) {
            alert(i18n.stockLimitReached || 'Stock limit reached');
            return;
        }
        cart.set(key, { productId: productId, variantId: variantId || null, qty: next });
        renderCart();
    }

    function changeQty(key, direction) {
        var entry = cart.get(key);
        if (!entry) {
            return;
        }
        var resolved = resolveEntry(entry);
        if (!resolved) {
            return;
        }
        var step = isFractionalUnit(resolved.unit) ? FRACTIONAL_STEP : 1;
        var next = roundQty(entry.qty + direction * step);

        if (next <= 0) {
            cart.delete(key);
        } else if (next > resolved.stock) {
            alert(i18n.stockLimitReached || 'Stock limit reached');
            return;
        } else {
            entry.qty = next;
        }
        renderCart();
    }

    function setQty(key, qty) {
        var entry = cart.get(key);
        if (!entry) {
            return;
        }
        var resolved = resolveEntry(entry);
        if (!resolved) {
            return;
        }
        var next = roundQty(qty);
        if (next <= 0) {
            cart.delete(key);
        } else {
            if (next > resolved.stock) {
                next = resolved.stock;
                alert(i18n.stockLimitReached || 'Stock limit reached');
            }
            entry.qty = next;
        }
        renderCart();
    }

    function cartSubtotal() {
        var subtotal = 0;
        cart.forEach(function (entry) {
            var resolved = resolveEntry(entry);
            if (resolved) {
                subtotal += resolved.price * entry.qty;
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
            cart.forEach(function (entry, key) {
                var resolved = resolveEntry(entry);
                if (!resolved) {
                    return;
                }
                var qty = entry.qty;
                var lineTotal = resolved.price * qty;
                var fractional = isFractionalUnit(resolved.unit);
                var qtyControl = fractional
                    ? '<input type="number" class="qty-direct-input" data-key="' + key + '" min="0.01" max="' + resolved.stock + '" step="0.01" value="' + qty + '" inputmode="decimal" aria-label="' + escapeHtml(i18n.editQty || '') + ' — ' + escapeHtml(resolved.name) + '">'
                    : '<span>' + escapeHtml(formatQty(qty)) + '</span>';

                rows.push(
                    '<div class="cart-row" data-key="' + key + '">' +
                        '<div class="cart-row-main">' +
                            '<div class="cart-row-name" title="' + escapeHtml(resolved.name) + '">' + escapeHtml(resolved.name) + '</div>' +
                            '<div class="cart-row-price">' + escapeHtml(formatMoney(resolved.price)) + ' / ' + escapeHtml(resolved.unit) + '</div>' +
                        '</div>' +
                        '<div class="qty-stepper' + (fractional ? ' qty-stepper-fractional' : '') + '">' +
                            '<button type="button" data-action="dec" data-key="' + key + '" aria-label="' + escapeHtml(i18n.decreaseQty || '') + '">−</button>' +
                            qtyControl +
                            '<button type="button" data-action="inc" data-key="' + key + '" aria-label="' + escapeHtml(i18n.increaseQty || '') + '">+</button>' +
                        '</div>' +
                        '<div class="cart-row-subtotal">' + escapeHtml(formatMoney(lineTotal)) + '</div>' +
                        '<button type="button" class="cart-remove" data-action="remove" data-key="' + key + '" aria-label="' + escapeHtml(i18n.removeFromCart || '') + '">✕</button>' +
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
            Array.from(cart.values(), function (entry) {
                return { product_id: entry.productId, variant_id: entry.variantId || null, qty: entry.qty };
            })
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
        var variantBtn = e.target.closest('.pos-variant-btn');
        if (variantBtn) {
            if (variantBtn.disabled) {
                return;
            }
            addToCart(parseInt(variantBtn.getAttribute('data-id'), 10), parseInt(variantBtn.getAttribute('data-variant'), 10));
            return;
        }

        var btn = e.target.closest('.pos-product[data-id]');
        if (btn) {
            addToCart(parseInt(btn.getAttribute('data-id'), 10), null);
        }
    });

    searchEl.addEventListener('input', renderProducts);
    searchEl.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            var matches = filterProducts(searchEl.value);
            // Enter-to-add only applies to a single unambiguous plain-product
            // match; a product with variants still needs an explicit tap on
            // the variant the cashier means.
            if (matches.length === 1 && (!matches[0].variants || matches[0].variants.length === 0)) {
                addToCart(matches[0].id, null);
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
        var key = btn.getAttribute('data-key');
        var action = btn.getAttribute('data-action');
        if (action === 'inc') {
            changeQty(key, 1);
        } else if (action === 'dec') {
            changeQty(key, -1);
        } else if (action === 'remove') {
            cart.delete(key);
            renderCart();
        }
    });

    // Direct numeric qty entry (fractional-unit products only) commits on
    // change (blur/Enter) rather than every keystroke, so a full re-render
    // doesn't steal focus mid-type.
    cartListEl.addEventListener('change', function (e) {
        if (e.target.classList.contains('qty-direct-input')) {
            var key = e.target.getAttribute('data-key');
            setQty(key, parseFloat(e.target.value) || 0);
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
                var productId = parseInt(row.product_id, 10);
                var variantId = row.variant_id ? parseInt(row.variant_id, 10) : null;
                var qty = parseFloat(row.qty);
                var product = !isNaN(productId) ? productById(productId) : null;
                if (product && !isNaN(qty) && qty > 0) {
                    cart.set(cartKey(productId, variantId), { productId: productId, variantId: variantId, qty: qty });
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

(function () {
    'use strict';

    var products = window.HM_PRODUCTS || [];
    var customers = window.HM_CUSTOMERS || [];
    var i18n = window.HM_I18N || {};
    var oldCart = window.HM_OLD_CART || [];
    var oldSale = window.HM_OLD_SALE || {};

    var canDiscount = window.HM_CAN_DISCOUNT !== false;

    // Whether a product may be sold in fractional amounts comes from the
    // server (unit_allows_fraction(), the same rule Sale::create() enforces);
    // this list is only the fallback for a product row without that flag.
    var FRACTIONAL_UNITS = ['kg', 'litr', 'l', 'metr', 'm'];
    var FRACTIONAL_STEP = 0.1;
    // The list shows at most this many products at once: a big catalogue is
    // found by typing or scanning, and redrawing thousands of rows on every
    // keystroke would make the till lag.
    var MAX_LISTED = 60;

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
    var qrAmount = 0;
    var activePreset = 'naqd';
    // Whichever field the cashier last typed into keeps the value they typed
    // (clamped only to the total); the other one yields to make room for it.
    // Without this, editing karta while naqd was still pinned to the old total
    // would just clamp karta straight back down to 0 instead of actually
    // making a split.
    var lastEditedField = 'naqd';

    // Set by applyPayments() whenever the selected existing customer has a
    // credit_limit configured and this sale's debt remainder would push
    // their balance above it. Read by the form's submit handler to require
    // an explicit confirm() before letting the sale through — the shop owner
    // can still override (e.g. for a trusted regular), this is a warning, not
    // a hard block (see Feature 2 in the customer/debt backlog).
    var creditLimitExceeded = false;

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
    var qrAmountInputEl = document.getElementById('qr-amount-input');
    var changeRowEl = document.getElementById('change-row');
    var changeAmountEl = document.getElementById('change-amount');
    var discountErrorEl = document.getElementById('discount-error');
    var formErrorEl = document.getElementById('sale-form-error');
    var cartCountEl = document.getElementById('cart-count');
    var cartClearEl = document.getElementById('cart-clear');

    // Set by renderCart() when the discount would leave nothing (or less
    // than nothing) to pay — Sale::create() rejects that, so the form does
    // too instead of letting the cashier find out after a round trip.
    var discountTooLarge = false;

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

    function isFractionalProduct(product) {
        return typeof product.fractional === 'boolean' ? product.fractional : isFractionalUnit(product.unit);
    }

    function showFormError(message) {
        if (!formErrorEl) {
            alert(message);
            return;
        }
        formErrorEl.textContent = message;
        formErrorEl.hidden = !message;
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
            fractional: isFractionalProduct(product),
            price: priceOf(product, variant),
            stock: variant ? variant.stock : product.stock
        };
    }

    // "Chakana" (retail) or "Optom" (wholesale), picked by the cashier for the
    // whole sale. The server prices the lines the same way (Sale::create()):
    // the variant's wholesale price, else the product's, else the retail one.
    var priceMode = 'retail';
    function priceOf(product, variant) {
        var retail = variant ? variant.price : product.price;
        if (priceMode !== 'wholesale') {
            return retail;
        }
        if (variant && variant.wholesale !== null && variant.wholesale !== undefined) {
            return variant.wholesale;
        }
        if (product.wholesale !== null && product.wholesale !== undefined) {
            return product.wholesale;
        }
        return retail;
    }

    // The category button picked above the list (null = all). Typing and
    // scanning still look through every product when asked to (Enter).
    var activeCategory = null;

    function filterProducts(query, everyCategory) {
        var q = (query || '').trim().toLowerCase();
        var pool = everyCategory || activeCategory === null
            ? products
            : products.filter(function (p) { return p.category === activeCategory; });
        if (!q) {
            return pool;
        }
        function has(value) {
            return value !== null && value !== undefined && String(value).toLowerCase().indexOf(q) !== -1;
        }
        return pool.filter(function (p) {
            return has(p.name) || has(p.barcode) || (p.variants || []).some(function (v) {
                return has(v.label) || has(v.barcode);
            });
        });
    }

    /**
     * The one product/variant whose barcode is exactly `code` — what a
     * hardware scanner types (followed by Enter) or the camera reads.
     * Barcodes are unique per shop (Product::barcodeTaken()), so there is at
     * most one. A variant-bearing product's own barcode resolves to its only
     * variant when it has just one; with several it can't pick for the
     * cashier and returns null (the filtered list then shows the choices).
     */
    function findByExactBarcode(code) {
        var q = String(code || '').trim();
        if (!q) {
            return null;
        }
        for (var i = 0; i < products.length; i++) {
            var p = products[i];
            var variants = p.variants || [];
            for (var j = 0; j < variants.length; j++) {
                if (variants[j].barcode && String(variants[j].barcode) === q) {
                    return { productId: p.id, variantId: variants[j].id };
                }
            }
            if (p.barcode && String(p.barcode) === q) {
                if (variants.length === 0) {
                    return { productId: p.id, variantId: null };
                }
                if (variants.length === 1) {
                    return { productId: p.id, variantId: variants[0].id };
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Enter in the search box / a camera scan: an exact barcode goes straight
     * into the cart; otherwise a single unambiguous match (a plain product,
     * or a product with exactly one variant) does. Anything else leaves the
     * filtered list on screen to pick from.
     */
    function addFromSearch() {
        var query = searchEl.value;
        var target = findByExactBarcode(query);

        if (!target) {
            var matches = filterProducts(query, true);
            if (matches.length === 1) {
                var only = matches[0];
                var vs = only.variants || [];
                if (vs.length === 0) {
                    target = { productId: only.id, variantId: null };
                } else if (vs.length === 1) {
                    target = { productId: only.id, variantId: vs[0].id };
                }
            } else if (matches.length === 0 && String(query || '').trim() !== '') {
                var code = String(query).trim();
                var emptyName = (window.HM_EMPTY_BARCODES || {})[code];
                if (emptyName) {
                    // A product we have, just sold out: say so, and offer a purchase, not a new product.
                    productListEl.innerHTML = '<p class="muted">' +
                        escapeHtml((i18n.scanOutOfStock || ':name').replace(':name', emptyName)) +
                        (window.HM_CAN_PRODUCTS ? ' <a class="scan-add-link" href="/purchases/create">' + escapeHtml(i18n.scanRecordPurchase || '') + '</a>' : '') +
                        '</p>';
                    searchEl.select();
                    return;
                }
                productListEl.innerHTML = '<p class="muted">' +
                    escapeHtml((i18n.scanNotFound || '').replace(':code', code)) +
                    (window.HM_CAN_PRODUCTS && /^[0-9A-Za-z-]{4,64}$/.test(code)
                        ? ' <a class="scan-add-link" href="/products/quick?barcode=' + encodeURIComponent(code) + '">' + escapeHtml(i18n.scanAddProduct || '+') + '</a>'
                        : '') + '</p>';
                searchEl.select();
                return;
            }
        }

        if (target) {
            addToCart(target.productId, target.variantId);
            searchEl.value = '';
            renderProducts();
            searchEl.focus();
        }
    }

    function renderProducts() {
        var list = filterProducts(searchEl.value);

        if (list.length === 0) {
            productListEl.innerHTML = '<p class="muted">' + escapeHtml(i18n.noResults || '') + '</p>';
            return;
        }

        var hiddenCount = Math.max(0, list.length - MAX_LISTED);
        var more = hiddenCount > 0
            ? '<p class="muted pos-more">' + escapeHtml((i18n.moreProducts || '+:count').replace(':count', hiddenCount)) + '</p>'
            : '';

        productListEl.innerHTML = list.slice(0, MAX_LISTED).map(function (p) {
            if (!p.variants || p.variants.length === 0) {
                return '<button type="button" class="pos-product" data-id="' + p.id + '">' +
                    '<span class="pos-product-name">' + escapeHtml(p.name) + '</span>' +
                    '<span class="pos-product-meta">' + escapeHtml(formatMoney(priceOf(p, null))) + '<span class="pos-stock"> · ' + escapeHtml(formatQty(p.stock)) + ' ' + escapeHtml(p.unit) + '</span></span>' +
                    '</button>';
            }

            // A product with variants can't be added directly — the cashier
            // must pick a specific one, since stock/price are tracked per
            // variant (see Sale::create()).
            var variantButtons = p.variants.map(function (v) {
                var disabled = v.stock <= 0;
                return '<button type="button" class="pos-variant-btn" data-id="' + p.id + '" data-variant="' + v.id + '"' + (disabled ? ' disabled' : '') + '>' +
                    '<span>' + escapeHtml(v.label) + '</span>' +
                    '<span class="pos-product-meta">' + escapeHtml(formatMoney(priceOf(p, v))) + '<span class="pos-stock"> · ' + escapeHtml(formatQty(v.stock)) + ' ' + escapeHtml(p.unit) + '</span></span>' +
                    '</button>';
            }).join('');

            return '<div class="pos-product-group">' +
                '<div class="pos-product" style="cursor:default;">' +
                    '<span class="pos-product-name">' + escapeHtml(p.name) + '</span>' +
                    '<span class="pos-variant-toggle-hint">' + escapeHtml(i18n.pickVariantHint || '') + '</span>' +
                '</div>' +
                '<div class="pos-variant-list">' + variantButtons + '</div>' +
            '</div>';
        }).join('') + more;
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
            showFormError(i18n.stockLimitReached || 'Stock limit reached');
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
        var step = resolved.fractional ? FRACTIONAL_STEP : 1;
        var next = roundQty(entry.qty + direction * step);

        if (next <= 0) {
            cart.delete(key);
        } else if (next > resolved.stock) {
            showFormError(i18n.stockLimitReached || 'Stock limit reached');
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
        var next = resolved.fractional ? roundQty(qty) : Math.round(qty);
        if (next > resolved.stock) {
            // Keep the old amount: a barcode scanned into this field by accident
            // must not turn into "all the stock". renderCart clears the form
            // error, so the message goes after it.
            renderCart();
            showFormError(i18n.stockLimitReached || 'Stock limit reached');
            return;
        }
        if (next <= 0) {
            cart.delete(key);
        } else {
            entry.qty = next;
        }
        renderCart();
    }

    // Applies what was typed into a cart line's quantity field. An empty field
    // puts the old amount back; only a typed 0 removes the line.
    var qtyCommitting = false;
    function commitQtyInput(input) {
        // renderCart removes the focused field, which fires focusout again.
        if (qtyCommitting) {
            return;
        }
        var entry = cart.get(input.getAttribute('data-key'));
        if (!entry) {
            return;
        }
        var typed = parseFloat(input.value);
        if (isNaN(typed) || typed === entry.qty) {
            input.value = formatQty(entry.qty);
            return;
        }
        qtyCommitting = true;
        setQty(input.getAttribute('data-key'), typed);
        qtyCommitting = false;
    }

    function commitFocusedQty() {
        var active = document.activeElement;
        if (active && active.classList.contains('qty-direct-input')) {
            commitQtyInput(active);
        }
    }

    // Phones: "N ta · 51 250 so'm · To'lash" above the bottom menu while the cart has items.
    var mobileBarEl = document.getElementById('pos-mobile-bar');
    function renderMobileBar(total) {
        if (!mobileBarEl) {
            return;
        }
        mobileBarEl.hidden = cart.size === 0;
        document.getElementById('pos-mobile-count').textContent = (i18n.itemsCount || ':count').replace(':count', cart.size);
        document.getElementById('pos-mobile-total').textContent = formatMoney(total);
    }
    var mobilePayEl = document.getElementById('pos-mobile-pay');
    if (mobilePayEl) {
        mobilePayEl.addEventListener('click', function () {
            var cartEl = document.getElementById('pos-cart');
            if (cartEl) {
                cartEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
    }

    // One tap for the cash the customer hands over: the exact sum and the next round notes.
    var quickCashEl = document.getElementById('quick-cash');
    function renderQuickCash(total) {
        if (!quickCashEl) {
            return;
        }
        var amounts = [];
        if (total > 0) {
            amounts.push(total);
            [1000, 5000, 10000, 20000, 50000, 100000, 200000].forEach(function (note) {
                var v = Math.ceil(total / note - 1e-9) * note;
                if (v > total + 0.005 && amounts.indexOf(v) === -1) {
                    amounts.push(v);
                }
            });
        }
        quickCashEl.innerHTML = amounts.slice(0, 4).map(function (v) {
            return '<button type="button" class="quick-cash-btn" data-cash="' + v + '">' + escapeHtml(formatMoney(v)) + '</button>';
        }).join('');
    }
    if (quickCashEl) {
        quickCashEl.addEventListener('click', function (e) {
            var btn = e.target.closest('.quick-cash-btn');
            if (!btn) {
                return;
            }
            e.preventDefault();
            naqdAmountInputEl.value = btn.getAttribute('data-cash');
            naqdAmountInputEl.dispatchEvent(new Event('input', { bubbles: true }));
        });
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
        if (!canDiscount) {
            return 0;
        }
        if (discountMode === 'percent') {
            var pct = Math.max(0, Math.min(100, parseFloat(discountInputEl.value) || 0));
            return Math.round(subtotal * pct / 100 * 100) / 100;
        }
        return Math.max(0, parseFloat(discountInputEl.value) || 0);
    }

    function renderCart() {
        // The Telegram WebApp asks before closing while the cart has items.
        document.dispatchEvent(new CustomEvent('kassiron:cart', { detail: { count: cart.size } }));
        if (cartCountEl) {
            cartCountEl.textContent = cart.size;
            cartCountEl.hidden = cart.size === 0;
        }
        if (cartClearEl) {
            cartClearEl.hidden = cart.size === 0;
        }
        if (cart.size === 0) {
            cartListEl.innerHTML = '';
            cartEmptyMsgEl.style.display = 'block';
        } else {
            cartEmptyMsgEl.style.display = 'none';
            showFormError('');

            var rows = [];
            cart.forEach(function (entry, key) {
                var resolved = resolveEntry(entry);
                if (!resolved) {
                    return;
                }
                var qty = entry.qty;
                var lineTotal = resolved.price * qty;
                var fractional = resolved.fractional;
                // Text, not type="number": phones and Uzbek/Russian keyboards type "1,5".
                var qtyControl = '<input type="text" class="qty-direct-input" data-key="' + key + '" data-fractional="' + (fractional ? '1' : '0') + '" value="' + escapeHtml(formatQty(qty)) + '" inputmode="' + (fractional ? 'decimal' : 'numeric') + '" enterkeyhint="done" autocomplete="off" aria-label="' + escapeHtml(i18n.editQty || '') + ' — ' + escapeHtml(resolved.name) + '">';

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
        discountTooLarge = discount > 0 && discount >= subtotal - 0.005;
        if (discountErrorEl) {
            discountErrorEl.hidden = !discountTooLarge;
        }
        var total = Math.max(0, Math.round((subtotal - discount) * 100) / 100);
        cartTotalEl.textContent = formatMoney(total);
        renderMobileBar(total);
        renderQuickCash(total);
        lastTotal = total;
        if (typeof renderCartTabs === 'function' && !switchingCart) {
            renderCartTabs();
            schedulePersist();
        }
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
    // changes); free/custom edits are clamped so the card amount never
    // exceeds what's left, and whatever's left over becomes the qarz (debt)
    // remainder. Cash is the exception: the customer may hand over more than
    // is owed, so a typed naqd amount above the total is kept as-is and the
    // difference is shown as change (Sale::create() applies the same rule).
    function applyPayments(total) {
        // What is left of the total after `used`, never below zero.
        function left(used) {
            return Math.max(0, Math.round((total - used) * 100) / 100);
        }

        if (activePreset === 'naqd') {
            naqdAmount = total;
            kartaAmount = 0;
            qrAmount = 0;
        } else if (activePreset === 'karta') {
            naqdAmount = 0;
            kartaAmount = total;
            qrAmount = 0;
        } else if (activePreset === 'qr') {
            naqdAmount = 0;
            kartaAmount = 0;
            qrAmount = total;
        } else if (activePreset === 'qarz') {
            naqdAmount = 0;
            kartaAmount = 0;
            qrAmount = 0;
        } else if (lastEditedField === 'karta') {
            kartaAmount = Math.max(0, Math.min(kartaAmount, total));
            qrAmount = Math.max(0, Math.min(qrAmount, left(kartaAmount)));
            naqdAmount = Math.max(0, Math.min(naqdAmount, left(kartaAmount + qrAmount)));
        } else if (lastEditedField === 'qr') {
            qrAmount = Math.max(0, Math.min(qrAmount, total));
            kartaAmount = Math.max(0, Math.min(kartaAmount, left(qrAmount)));
            naqdAmount = Math.max(0, Math.min(naqdAmount, left(kartaAmount + qrAmount)));
        } else {
            naqdAmount = Math.max(0, naqdAmount);
            kartaAmount = Math.max(0, Math.min(kartaAmount, left(Math.min(naqdAmount, total))));
            qrAmount = Math.max(0, Math.min(qrAmount, left(Math.min(naqdAmount, total) + kartaAmount)));
        }

        var naqdApplied = Math.max(0, Math.min(naqdAmount, left(kartaAmount + qrAmount)));
        var change = Math.max(0, Math.round((naqdAmount - naqdApplied) * 100) / 100);
        if (changeRowEl) {
            changeRowEl.hidden = !(change > 0 && cart.size > 0);
            changeAmountEl.textContent = formatMoney(change);
        }

        naqdAmountInputEl.value = naqdAmount;
        kartaAmountInputEl.value = kartaAmount;
        qrAmountInputEl.value = qrAmount;
        document.getElementById('naqd-amount-field').value = naqdAmount;
        document.getElementById('karta-amount-field').value = kartaAmount;
        document.getElementById('qr-amount-field').value = qrAmount;

        document.querySelectorAll('.payment-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-preset') === activePreset);
        });

        var remaining = left(naqdApplied + kartaAmount + qrAmount);
        debtFieldsEl.style.display = remaining > 0 ? 'block' : 'none';

        var labelText = (i18n.debtRemainingLabel || 'Debt: :amount').replace(':amount', formatMoney(remaining));
        creditLimitExceeded = false;

        var selectedId = customerSelectEl.value ? parseInt(customerSelectEl.value, 10) : null;
        var selectedCustomer = selectedId ? customers.find(function (c) { return c.id === selectedId; }) : null;

        if (remaining > 0 && selectedCustomer && selectedCustomer.creditLimit !== null && selectedCustomer.creditLimit !== undefined) {
            var projectedBalance = Math.round((selectedCustomer.balance + remaining) * 100) / 100;
            labelText += ' · ' + (i18n.creditLimitPosLabel || 'Credit limit: :amount').replace(':amount', formatMoney(selectedCustomer.creditLimit));

            if (projectedBalance > selectedCustomer.creditLimit + 0.005) {
                creditLimitExceeded = true;
                labelText += ' — ' + (i18n.creditLimitExceededWarning || 'Exceeds limit: new balance :amount > limit :limit')
                    .replace(':amount', formatMoney(projectedBalance))
                    .replace(':limit', formatMoney(selectedCustomer.creditLimit));
            }
        }

        debtRemainingLabelEl.textContent = labelText;
        debtRemainingLabelEl.classList.toggle('credit-limit-exceeded', creditLimitExceeded);
    }

    function setPreset(preset) {
        activePreset = preset;
        renderCart();
    }

    // ---- The debt customer: one field that searches by name or phone and
    // offers "new customer" for what it doesn't find. The chosen id lives in
    // the hidden #customer-select the rest of this file reads.
    var customerSearchEl = document.getElementById('customer-search');
    var customerResultsEl = document.getElementById('customer-results');
    var customerClearEl = document.getElementById('customer-clear');
    var newCustomerMode = false;
    var customerActive = -1;

    function digits(value) {
        return String(value || '').replace(/\D/g, '');
    }

    function customerLabel(c) {
        return c.name + (c.phone ? ' · ' + c.phone : '');
    }

    function populateCustomerSelect() {
        // (no list to fill: the search field finds customers as you type)
    }

    function updateCustomerFields() {
        var selected = customerSelectEl.value;
        if (selected) {
            newCustomerMode = false;
            newCustomerFieldsEl.style.display = 'none';
            document.getElementById('customer-id-field').value = selected;
            document.getElementById('customer-name-field').value = '';
            document.getElementById('customer-phone-field').value = '';
        } else {
            newCustomerFieldsEl.style.display = newCustomerMode ? 'grid' : 'none';
            document.getElementById('customer-id-field').value = '';
        }
        customerClearEl.hidden = !(selected || newCustomerMode || customerSearchEl.value);
        // The active tab is named after its customer.
        if (typeof renderCartTabs === 'function' && cartTabsEl) {
            renderCartTabs();
            schedulePersist();
        }
    }

    function setCustomer(id) {
        customerSelectEl.value = id ? String(id) : '';
        customerSelectEl.dispatchEvent(new Event('change'));
    }

    function customerMatches(query) {
        var q = query.trim().toLowerCase();
        if (!q) {
            return customers.slice().sort(function (a, b) { return (b.balance || 0) - (a.balance || 0); }).slice(0, 8);
        }
        var qDigits = digits(q);
        return customers.filter(function (c) {
            return String(c.name).toLowerCase().indexOf(q) !== -1
                || (qDigits.length >= 3 && digits(c.phone).indexOf(qDigits) !== -1);
        }).slice(0, 8);
    }

    function closeCustomerResults() {
        customerResultsEl.hidden = true;
        customerSearchEl.setAttribute('aria-expanded', 'false');
        customerActive = -1;
    }

    function renderCustomerResults() {
        var query = customerSearchEl.value;
        var list = customerMatches(query);
        var html = list.map(function (c, i) {
            var debt = c.balance > 0 ? '<span class="customer-debt">' + escapeHtml((i18n.customerDebt || ':amount').replace(':amount', formatMoney(c.balance))) + '</span>' : '';
            return '<li role="option" data-index="' + i + '" data-id="' + c.id + '">' +
                '<span class="customer-name">' + escapeHtml(c.name) + '</span>' +
                (c.phone ? '<span class="customer-phone">' + escapeHtml(c.phone) + '</span>' : '') + debt + '</li>';
        });
        if (query.trim() !== '') {
            html.push('<li role="option" class="customer-new" data-index="' + list.length + '" data-new="1">' +
                escapeHtml((i18n.customerNew || '+ :name').replace(':name', query.trim())) + '</li>');
        } else if (list.length === 0) {
            html.push('<li class="customer-empty">' + escapeHtml(i18n.customerNone || '') + '</li>');
        }
        customerResultsEl.innerHTML = html.join('');
        customerResultsEl.hidden = false;
        customerSearchEl.setAttribute('aria-expanded', 'true');
        customerActive = -1;
    }

    function chooseNewCustomer(text) {
        var value = String(text || '').trim();
        newCustomerMode = true;
        setCustomer(null);
        if (digits(value).length >= 7 && digits(value).length >= value.replace(/[\s+()-]/g, '').length) {
            newCustomerPhoneEl.value = value;
            newCustomerNameEl.value = '';
        } else {
            newCustomerNameEl.value = value;
        }
        document.getElementById('customer-name-field').value = newCustomerNameEl.value;
        document.getElementById('customer-phone-field').value = newCustomerPhoneEl.value;
        closeCustomerResults();
        updateCustomerFields();
        (newCustomerNameEl.value ? newCustomerPhoneEl : newCustomerNameEl).focus();
    }

    function pickCustomerItem(li) {
        if (!li) {
            return;
        }
        if (li.getAttribute('data-new')) {
            chooseNewCustomer(customerSearchEl.value);
            return;
        }
        var id = parseInt(li.getAttribute('data-id'), 10);
        var c = customers.find(function (x) { return x.id === id; });
        if (!c) {
            return;
        }
        newCustomerMode = false;
        customerSearchEl.value = customerLabel(c);
        setCustomer(c.id);
        closeCustomerResults();
    }

    customerSearchEl.addEventListener('focus', renderCustomerResults);
    customerSearchEl.addEventListener('input', function () {
        if (customerSelectEl.value || newCustomerMode) {
            newCustomerMode = false;
            setCustomer(null);
        }
        renderCustomerResults();
        updateCustomerFields();
    });
    customerSearchEl.addEventListener('keydown', function (e) {
        var items = customerResultsEl.querySelectorAll('li[role="option"]');
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
            e.preventDefault();
            if (customerResultsEl.hidden) {
                renderCustomerResults();
                items = customerResultsEl.querySelectorAll('li[role="option"]');
            }
            if (!items.length) {
                return;
            }
            customerActive = (customerActive + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
            items.forEach(function (li, i) { li.classList.toggle('active', i === customerActive); });
            items[customerActive].scrollIntoView({ block: 'nearest' });
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (!customerResultsEl.hidden && items.length) {
                pickCustomerItem(items[customerActive >= 0 ? customerActive : 0]);
            }
        } else if (e.key === 'Escape') {
            closeCustomerResults();
        }
    });
    customerResultsEl.addEventListener('mousedown', function (e) {
        // mousedown, not click: the field's blur would close the list first.
        e.preventDefault();
        pickCustomerItem(e.target.closest('li[role="option"]'));
    });
    customerSearchEl.addEventListener('blur', function () {
        setTimeout(closeCustomerResults, 120);
    });
    customerClearEl.addEventListener('click', function () {
        customerSearchEl.value = '';
        newCustomerNameEl.value = '';
        newCustomerPhoneEl.value = '';
        document.getElementById('customer-name-field').value = '';
        document.getElementById('customer-phone-field').value = '';
        newCustomerMode = false;
        setCustomer(null);
        customerSearchEl.focus();
    });

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

    var priceTypeFieldEl = document.getElementById('price-type-field');
    var wholesaleBadgeEl = document.getElementById('wholesale-badge');
    var priceModeBtns = document.querySelectorAll('.price-mode-btn');
    function setPriceMode(mode) {
        priceMode = mode === 'wholesale' && priceModeBtns.length ? 'wholesale' : 'retail';
        priceModeBtns.forEach(function (b) {
            var on = b.getAttribute('data-price-mode') === priceMode;
            b.classList.toggle('active', on);
            b.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
        if (priceTypeFieldEl) {
            priceTypeFieldEl.value = priceMode;
        }
        if (wholesaleBadgeEl) {
            wholesaleBadgeEl.hidden = priceMode !== 'wholesale';
        }
        document.documentElement.classList.toggle('till-wholesale', priceMode === 'wholesale');
        renderProducts();
        renderCart();
    }
    priceModeBtns.forEach(function (b) {
        b.addEventListener('click', function () { setPriceMode(b.getAttribute('data-price-mode')); });
    });

    var categoriesEl = document.getElementById('pos-categories');
    if (categoriesEl) {
        categoriesEl.addEventListener('click', function (e) {
            var chip = e.target.closest('.pos-category');
            if (!chip) {
                return;
            }
            var value = chip.getAttribute('data-category');
            var picked = value === '' ? null : parseInt(value, 10);
            // Pressing the chosen one again goes back to every product.
            activeCategory = picked === activeCategory ? null : picked;
            categoriesEl.querySelectorAll('.pos-category').forEach(function (b) {
                var on = (b.getAttribute('data-category') === '' && activeCategory === null)
                    || parseInt(b.getAttribute('data-category'), 10) === activeCategory;
                b.classList.toggle('active', on);
                b.setAttribute('aria-pressed', on ? 'true' : 'false');
            });
            renderProducts();
        });
    }
    searchEl.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            addFromSearch();
        }
    });
    // Camera scans (barcode-scan.js) land here the same way a hardware
    // scanner's "code + Enter" does.
    searchEl.addEventListener('barcode-scanned', addFromSearch);

    cartListEl.addEventListener('click', function (e) {
        var btn = e.target.closest('button[data-action]');
        if (!btn) {
            return;
        }
        var key = btn.getAttribute('data-key');
        var action = btn.getAttribute('data-action');
        commitFocusedQty();
        if (action === 'inc') {
            changeQty(key, 1);
        } else if (action === 'dec') {
            changeQty(key, -1);
        } else if (action === 'remove') {
            cart.delete(key);
            renderCart();
        }
    });

    // Pressing − / + must not blur the quantity field first: the commit would
    // redraw the cart and the button under the pointer would miss its click.
    // The first press on a quantity field focuses it here, because the browser's
    // own handling would put the caret down and undo the selection below.
    cartListEl.addEventListener('mousedown', function (e) {
        var active = document.activeElement;
        var editing = active && active.classList.contains('qty-direct-input');
        if (e.target.closest('button[data-action]') && editing) {
            e.preventDefault();
        } else if (e.target.classList.contains('qty-direct-input') && e.target !== active) {
            e.preventDefault();
            e.target.focus();
        }
    });

    // Typing a quantity: the old number is selected so the first key replaces
    // it, and the amount applies on Enter or on leaving the field rather than on
    // every keystroke, so a redraw doesn't steal focus mid-type.
    cartListEl.addEventListener('focusin', function (e) {
        if (e.target.classList.contains('qty-direct-input')) {
            e.target.select();
        }
    });
    cartListEl.addEventListener('input', function (e) {
        if (!e.target.classList.contains('qty-direct-input')) {
            return;
        }
        var clean = e.target.value.replace(/,/g, '.');
        if (e.target.getAttribute('data-fractional') === '1') {
            clean = clean.replace(/[^\d.]/g, '');
            var dot = clean.indexOf('.');
            if (dot !== -1) {
                clean = clean.slice(0, dot + 1) + clean.slice(dot + 1).replace(/\./g, '');
            }
        } else {
            clean = clean.replace(/\D/g, '');
        }
        if (clean !== e.target.value) {
            e.target.value = clean;
        }
    });
    cartListEl.addEventListener('focusout', function (e) {
        if (!e.target.classList.contains('qty-direct-input')) {
            return;
        }
        // Tabbing to another control in the cart: the commit redraws the cart,
        // so focus has to be put back on that control's new copy.
        var next = e.relatedTarget;
        var nextSelector = null;
        if (next && cartListEl.contains(next)) {
            var nextAction = next.getAttribute('data-action');
            nextSelector = (nextAction ? 'button[data-action="' + nextAction + '"]' : '.qty-direct-input') + '[data-key="' + next.getAttribute('data-key') + '"]';
        }
        commitQtyInput(e.target);
        if (nextSelector) {
            setTimeout(function () {
                var control = cartListEl.querySelector(nextSelector);
                if (control && control !== document.activeElement) {
                    control.focus();
                }
            }, 0);
        }
    });
    cartListEl.addEventListener('keydown', function (e) {
        if (!e.target.classList.contains('qty-direct-input')) {
            return;
        }
        if (e.key === 'Enter') {
            // Enter here must not submit the sale.
            e.preventDefault();
            commitQtyInput(e.target);
            // Back to the search box for the next scan; on a phone that would
            // only pop the keyboard up again.
            if (window.matchMedia('(pointer: fine)').matches) {
                searchEl.focus();
            } else if (document.activeElement && document.activeElement.classList.contains('qty-direct-input')) {
                document.activeElement.blur();
            }
        } else if (e.key === 'Escape') {
            var entry = cart.get(e.target.getAttribute('data-key'));
            if (entry) {
                e.target.value = formatQty(entry.qty);
            }
            e.target.blur();
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
            discountInputEl.setAttribute('max', mode === 'percent' ? '99.99' : discountInputEl.getAttribute('data-max-amount') || '');
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

    qrAmountInputEl.addEventListener('input', function () {
        activePreset = null;
        lastEditedField = 'qr';
        qrAmount = Math.max(0, parseFloat(qrAmountInputEl.value) || 0);
        renderCart();
    });

    customerSelectEl.addEventListener('change', function () {
        updateCustomerFields();
        // Re-run so the debt/credit-limit label picks up the newly selected
        // customer's balance/creditLimit (it doesn't touch the cart/payment
        // state itself, just recomputes derived display + creditLimitExceeded).
        renderCart();
    });

    newCustomerNameEl.addEventListener('input', function () {
        document.getElementById('customer-name-field').value = newCustomerNameEl.value;
    });
    newCustomerPhoneEl.addEventListener('input', function () {
        document.getElementById('customer-phone-field').value = newCustomerPhoneEl.value;
    });

    saleFormEl.addEventListener('submit', function (e) {
        // F9 submits without leaving the quantity field.
        commitFocusedQty();
        if (cart.size === 0) {
            e.preventDefault();
            showFormError(i18n.emptyCart || 'Cart is empty');
            searchEl.focus();
            return;
        }
        if (discountTooLarge) {
            e.preventDefault();
            showFormError(i18n.discountTooLarge || 'Discount is too large');
            discountInputEl.focus();
            return;
        }
        // Typed into the customer field without picking: an exact phone
        // match is that customer, anything else a new one.
        if (!customerSelectEl.value && !newCustomerMode && customerSearchEl.value.trim() !== '') {
            var typed = digits(customerSearchEl.value);
            var exact = typed.length >= 7 ? customers.find(function (c) { return digits(c.phone).slice(-9) === typed.slice(-9); }) : null;
            if (exact) {
                setCustomer(exact.id);
            } else {
                chooseNewCustomer(customerSearchEl.value);
            }
        }
        if (creditLimitExceeded && saleFormEl.getAttribute('data-limit-ok') !== '1') {
            var question = i18n.confirmExceedCreditLimit || 'This sale exceeds the customer\'s credit limit. Continue?';
            var tg = window.Telegram && window.Telegram.WebApp;
            // Inside Telegram the browser's confirm() may never show: use Telegram's own dialog.
            if (tg && tg.initData && tg.isVersionAtLeast && tg.isVersionAtLeast('6.2')) {
                e.preventDefault();
                tg.showConfirm(question, function (ok) {
                    if (ok) {
                        saleFormEl.setAttribute('data-limit-ok', '1');
                        saleFormEl.requestSubmit ? saleFormEl.requestSubmit() : saleFormEl.submit();
                    }
                });
                return;
            }
            if (!confirm(question)) {
                e.preventDefault();
                return;
            }
        }
        completeBtnEl.disabled = true;
        // Which cart this sale is: closed on the next page if the sale went through.
        persistCarts();
        try {
            localStorage.setItem(FINISH_KEY, activeCartId);
        } catch (err) { /* no storage */ }
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

        if (oldSale.priceType === 'wholesale' && priceModeBtns.length) {

            setPriceMode('wholesale');

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
        var hasOldQr = oldSale.qrAmount !== undefined && oldSale.qrAmount !== '';
        if (hasOldNaqd || hasOldKarta || hasOldQr) {
            activePreset = null;
            naqdAmount = hasOldNaqd ? (parseFloat(oldSale.naqdAmount) || 0) : 0;
            kartaAmount = hasOldKarta ? (parseFloat(oldSale.kartaAmount) || 0) : 0;
            qrAmount = hasOldQr ? (parseFloat(oldSale.qrAmount) || 0) : 0;
        }
    }

    // Keyboard: F2 jumps to the search box, F9 completes the sale, and typing
    // (or a barcode scanner "typing") while no field is focused goes to the
    // search box instead of being lost.
    document.addEventListener('keydown', function (e) {
        if (e.key === 'F2') {
            e.preventDefault();
            searchEl.focus();
            searchEl.select();
            return;
        }
        if (e.key === 'F9') {
            e.preventDefault();
            if (!completeBtnEl.disabled) {
                completeBtnEl.click();
            }
            return;
        }
        if (e.ctrlKey || e.altKey || e.metaKey || e.key.length !== 1 || e.key === ' ') {
            return;
        }
        var active = document.activeElement;
        var tag = active ? active.tagName : '';
        if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
            return;
        }
        if (document.querySelector('.mobile-drawer.open, dialog[open]')) {
            return;
        }
        searchEl.focus();
    });

    if (cartClearEl) {
        cartClearEl.addEventListener('click', function () {
            cart.clear();
            renderCart();
            searchEl.focus();
        });
    }

    // The clock in POS mode's top bar.
    var clockEl = document.getElementById('pos-clock');
    if (clockEl) {
        var tick = function () {
            var now = new Date();
            clockEl.textContent = ('0' + now.getHours()).slice(-2) + ':' + ('0' + now.getMinutes()).slice(-2);
        };
        tick();
        setInterval(tick, 15000);
    }

    // POS mode keeps the discount and the split-payment amounts out of the
    // way until asked for (or until they hold something, after a failed
    // submission brought them back).
    var extraToggleEl = document.getElementById('pos-extra-toggle');
    if (extraToggleEl) {
        extraToggleEl.addEventListener('click', function () {
            saleFormEl.classList.toggle('show-extra');
        });
    }

    // ---- Several carts at once, on this device only: a customer with a big
    // basket waits in one tab while a quick one is served in another. Parked
    // carts reserve no stock and are not sales: nothing reaches the server
    // until "Sotuvni yakunlash". Prices and stock are re-read when a cart is
    // opened again, so a parked cart never sells at an old price.
    var CARTS_KEY = window.HM_CARTS_KEY || 'kassiron.carts';
    var FINISH_KEY = CARTS_KEY + '.finishing';
    var MAX_CARTS = 5;
    var cartTabsEl = document.getElementById('cart-tabs');
    var carts = [];
    var activeCartId = null;
    var switchingCart = false;
    var saveTimer = null;
    var lastTotal = 0;

    function newCartId() {
        return 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    }
    function activeCart() {
        return carts.find(function (c) { return c.id === activeCartId; }) || null;
    }

    function captureState() {
        return {
            lines: Array.from(cart.values()).map(function (e) {
                return { productId: e.productId, variantId: e.variantId || null, qty: e.qty };
            }),
            discount: discountInputEl.value,
            discountMode: discountMode,
            priceMode: priceMode,
            customerId: customerSelectEl.value || '',
            customerText: customerSearchEl.value || '',
            newCustomer: newCustomerMode,
            newName: newCustomerNameEl.value,
            newPhone: newCustomerPhoneEl.value,
            activePreset: activePreset,
            naqd: naqdAmount,
            karta: kartaAmount,
            qr: qrAmount,
            lastEdited: lastEditedField
        };
    }

    function setDiscountModeQuietly(mode) {
        discountMode = mode === 'percent' ? 'percent' : 'amount';
        discountInputEl.setAttribute('max', discountMode === 'percent' ? '99.99' : discountInputEl.getAttribute('data-max-amount') || '');
        document.querySelectorAll('.discount-mode-btn').forEach(function (b) {
            b.classList.toggle('active', b.getAttribute('data-mode') === discountMode);
        });
    }

    function applyState(st) {
        st = st || {};
        switchingCart = true;
        cart.clear();
        var dropped = 0;
        (st.lines || []).forEach(function (l) {
            var product = productById(l.productId);
            var variant = product && l.variantId ? variantOf(product, l.variantId) : null;
            if (!product || (l.variantId && !variant)) {
                dropped++;
                return;
            }
            var stock = variant ? variant.stock : product.stock;
            var qty = Math.min(Number(l.qty) || 0, stock);
            if (qty <= 0) {
                dropped++;
                return;
            }
            if (qty < l.qty) {
                dropped++;
            }
            cart.set(cartKey(l.productId, l.variantId), { productId: l.productId, variantId: l.variantId || null, qty: qty });
        });
        setDiscountModeQuietly(st.discountMode);
        discountInputEl.value = st.discount || 0;
        if (priceModeBtns.length) {
            setPriceMode(st.priceMode === 'wholesale' ? 'wholesale' : 'retail');
        }
        newCustomerMode = !!st.newCustomer;
        newCustomerNameEl.value = st.newName || '';
        newCustomerPhoneEl.value = st.newPhone || '';
        document.getElementById('customer-name-field').value = newCustomerNameEl.value;
        document.getElementById('customer-phone-field').value = newCustomerPhoneEl.value;
        customerSelectEl.value = st.customerId || '';
        customerSearchEl.value = st.customerText || '';
        updateCustomerFields();
        activePreset = st.activePreset === undefined ? 'naqd' : st.activePreset;
        naqdAmount = Number(st.naqd) || 0;
        kartaAmount = Number(st.karta) || 0;
        qrAmount = Number(st.qr) || 0;
        lastEditedField = st.lastEdited || 'naqd';
        switchingCart = false;
        renderProducts();
        renderCart();
        if (dropped > 0) {
            showFormError((i18n.cartsDropped || ':count').replace(':count', dropped));
        }
    }

    function persistCarts() {
        var current = activeCart();
        if (current) {
            current.state = captureState();
            current.updated = Date.now();
        }
        try {
            localStorage.setItem(CARTS_KEY, JSON.stringify({ active: activeCartId, carts: carts }));
        } catch (e) { /* private window: carts simply don't survive a reload */ }
    }
    function schedulePersist() {
        if (switchingCart) {
            return;
        }
        clearTimeout(saveTimer);
        saveTimer = setTimeout(persistCarts, 150);
    }

    function stateTotals(st) {
        var count = 0, total = 0;
        (st && st.lines || []).forEach(function (l) {
            var product = productById(l.productId);
            var variant = product && l.variantId ? variantOf(product, l.variantId) : null;
            if (!product) {
                return;
            }
            var price = variant ? variant.price : product.price;
            if (st.priceMode === 'wholesale') {
                if (variant && variant.wholesale != null) {
                    price = variant.wholesale;
                } else if (product.wholesale != null) {
                    price = product.wholesale;
                }
            }
            count++;
            total += price * (Number(l.qty) || 0);
        });
        return { count: count, total: total };
    }

    function cartTitle(c, index) {
        if (c.name) {
            return c.name;
        }
        var st = c.id === activeCartId ? captureState() : (c.state || {});
        var who = st.customerId ? String(st.customerText || '').split(' · ')[0] : (st.newCustomer ? (st.newName || st.newPhone) : '');
        return who || (i18n.cartName || 'Savat :n').replace(':n', index + 1);
    }

    function renderCartTabs() {
        if (!cartTabsEl || !carts.length) {
            return;
        }
        cartTabsEl.innerHTML = carts.map(function (c, i) {
            var on = c.id === activeCartId;
            var totals = on ? { count: cart.size, total: lastTotal } : stateTotals(c.state);
            var meta = totals.count ? ' · ' + (i18n.cartItems || ':count').replace(':count', totals.count) + ' · ' + formatMoney(totals.total) : '';
            return '<div class="cart-tab' + (on ? ' active' : '') + '">' +
                '<button type="button" class="cart-tab-main" role="tab" aria-selected="' + on + '" data-cart="' + c.id + '" title="' + escapeHtml(i18n.cartRename || '') + '">' +
                    '<span class="cart-tab-name">' + escapeHtml(cartTitle(c, i)) + '</span><span class="cart-tab-meta">' + escapeHtml(meta) + '</span>' +
                '</button>' +
                (carts.length > 1 || totals.count ? '<button type="button" class="cart-tab-x" data-cart-close="' + c.id + '" aria-label="' + escapeHtml(i18n.cartClose || '') + '">×</button>' : '') +
            '</div>';
        }).join('') + (carts.length < MAX_CARTS ? '<button type="button" class="cart-tab-new" id="cart-tab-new">' + escapeHtml(i18n.cartNew || '+') + '</button>' : '');
        // On a narrow screen the open cart's tab is scrolled into sight (sideways only).
        var activeTab = cartTabsEl.querySelector('.cart-tab.active');
        if (activeTab) {
            var left = activeTab.offsetLeft - cartTabsEl.offsetLeft;
            if (left < cartTabsEl.scrollLeft || left + activeTab.offsetWidth > cartTabsEl.scrollLeft + cartTabsEl.clientWidth) {
                cartTabsEl.scrollLeft = Math.max(0, left - 8);
            }
        }
    }

    function switchToCart(id) {
        if (id === activeCartId) {
            return;
        }
        persistCarts();
        activeCartId = id;
        var target = activeCart();
        applyState(target ? target.state : {});
        persistCarts();
        renderCartTabs();
        searchEl.focus();
    }

    function askConfirm(message, onYes) {
        var tg = window.Telegram && window.Telegram.WebApp;
        if (tg && tg.initData && tg.isVersionAtLeast && tg.isVersionAtLeast('6.2')) {
            tg.showConfirm(message, function (ok) { if (ok) { onYes(); } });
        } else if (window.confirm(message)) {
            onYes();
        }
    }

    function closeCart(id) {
        var c = carts.find(function (x) { return x.id === id; });
        if (!c) {
            return;
        }
        var count = id === activeCartId ? cart.size : stateTotals(c.state).count;
        var doClose = function () {
            var index = carts.indexOf(c);
            carts.splice(index, 1);
            if (!carts.length) {
                carts.push({ id: newCartId(), name: '', state: {} });
            }
            if (id === activeCartId) {
                activeCartId = carts[Math.min(index, carts.length - 1)].id;
                applyState(activeCart().state);
            }
            persistCarts();
            renderCartTabs();
        };
        if (count > 0) {
            askConfirm((i18n.cartCloseConfirm || ':name').replace(':name', cartTitle(c, carts.indexOf(c))), doClose);
        } else {
            doClose();
        }
    }

    if (cartTabsEl) {
        cartTabsEl.addEventListener('click', function (e) {
            var close = e.target.closest('[data-cart-close]');
            if (close) {
                closeCart(close.getAttribute('data-cart-close'));
                return;
            }
            var tab = e.target.closest('[data-cart]');
            if (tab) {
                switchToCart(tab.getAttribute('data-cart'));
                return;
            }
            if (e.target.closest('#cart-tab-new') && carts.length < MAX_CARTS) {
                persistCarts();
                var c = { id: newCartId(), name: '', state: {} };
                carts.push(c);
                activeCartId = c.id;
                applyState({});
                persistCarts();
                renderCartTabs();
                searchEl.focus();
            }
        });
        // Double-click a tab's name to give the cart a name of its own ("Ko'k kurtka").
        cartTabsEl.addEventListener('dblclick', function (e) {
            var tab = e.target.closest('[data-cart]');
            if (!tab) {
                return;
            }
            var c = carts.find(function (x) { return x.id === tab.getAttribute('data-cart'); });
            var nameEl = tab.querySelector('.cart-tab-name');
            var input = document.createElement('input');
            input.className = 'cart-tab-input';
            input.maxLength = 24;
            input.value = c.name || '';
            nameEl.replaceWith(input);
            input.focus();
            var finished = false;
            var done = function () {
                if (finished) {
                    return;
                }
                finished = true;
                c.name = input.value.trim();
                persistCarts();
                renderCartTabs();
            };
            input.addEventListener('keydown', function (k) {
                if (k.key === 'Enter') { k.preventDefault(); done(); }
                if (k.key === 'Escape') { input.value = c.name || ''; done(); }
            });
            input.addEventListener('blur', done);
        });
    }

    [newCustomerNameEl, newCustomerPhoneEl].forEach(function (el) {
        el.addEventListener('input', function () {
            renderCartTabs();
            schedulePersist();
        });
    });

    function loadCarts() {
        try {
            var saved = JSON.parse(localStorage.getItem(CARTS_KEY) || 'null');
            if (saved && Array.isArray(saved.carts)) {
                carts = saved.carts.slice(0, MAX_CARTS);
                activeCartId = saved.active;
            }
        } catch (e) { carts = []; }
        var finishing = null;
        try {
            finishing = localStorage.getItem(FINISH_KEY);
            localStorage.removeItem(FINISH_KEY);
        } catch (e) { /* no storage */ }
        // The sale of that cart went through: it is done, the others stay.
        if (finishing && window.HM_SALE_DONE) {
            carts = carts.filter(function (c) { return c.id !== finishing; });
        }
        if (!carts.length) {
            carts.push({ id: newCartId(), name: '', state: {} });
        }
        if (!carts.some(function (c) { return c.id === activeCartId; })) {
            activeCartId = carts[0].id;
        }
        return finishing;
    }

    populateCustomerSelect();
    renderProducts();
    var finishingCart = loadCarts();
    if (Array.isArray(oldCart) && oldCart.length > 0) {
        // The sale was refused and came back: it belongs to the cart that was being sold.
        if (finishingCart && carts.some(function (c) { return c.id === finishingCart; })) {
            activeCartId = finishingCart;
        }
        restoreOldState();
        // Bring the customer field in line with what restoreOldState() put back.
        if (customerSelectEl.value) {
            var restored = customers.find(function (c) { return String(c.id) === customerSelectEl.value; });
            customerSearchEl.value = restored ? customerLabel(restored) : '';
        } else if (oldSale.customerName || oldSale.customerPhone) {
            newCustomerMode = true;
            customerSearchEl.value = oldSale.customerName || oldSale.customerPhone;
        }
        updateCustomerFields();
    } else {
        applyState(activeCart().state);
    }
    // Carts forgotten for a day: ask once, never drop them silently.
    var staleCarts = carts.filter(function (c) {
        return c.id !== activeCartId && c.updated && Date.now() - c.updated > 86400000 && stateTotals(c.state).count > 0;
    });
    if (staleCarts.length) {
        setTimeout(function () {
            askConfirm((i18n.cartsStale || ':count').replace(':count', staleCarts.length), function () {
                carts = carts.filter(function (c) { return staleCarts.indexOf(c) === -1; });
                persistCarts();
                renderCartTabs();
            });
        }, 400);
    }
    if (activePreset === null || (parseFloat(discountInputEl.value) || 0) > 0) {
        saleFormEl.classList.add('show-extra');
    }
    renderCart();
})();

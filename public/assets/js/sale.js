(function () {
    'use strict';

    var products = window.HM_PRODUCTS || [];
    var customers = window.HM_CUSTOMERS || [];
    var i18n = window.HM_I18N || {};
    var oldCart = window.HM_OLD_CART || [];
    var oldSale = window.HM_OLD_SALE || {};

    var cart = new Map(); // product_id -> qty
    var paymentType = 'naqd';
    var discount = 0;

    var productListEl = document.getElementById('product-list');
    var searchEl = document.getElementById('product-search');
    var cartListEl = document.getElementById('cart-list');
    var cartEmptyMsgEl = document.getElementById('cart-empty-msg');
    var cartTotalEl = document.getElementById('cart-total');
    var discountInputEl = document.getElementById('discount-input');
    var completeBtnEl = document.getElementById('complete-sale-btn');
    var debtFieldsEl = document.getElementById('debt-fields');
    var customerSelectEl = document.getElementById('customer-select');
    var newCustomerFieldsEl = document.getElementById('new-customer-fields');
    var newCustomerNameEl = document.getElementById('new-customer-name');
    var newCustomerPhoneEl = document.getElementById('new-customer-phone');
    var paidAmountInputEl = document.getElementById('paid-amount-input');
    var debtRemainingLabelEl = document.getElementById('debt-remaining-label');
    var saleFormEl = document.getElementById('sale-form');

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
        if (currentQty + 1 > product.stock) {
            alert(i18n.stockLimitReached || 'Stock limit reached');
            return;
        }
        cart.set(id, currentQty + 1);
        renderCart();
    }

    function changeQty(id, delta) {
        var product = productById(id);
        var currentQty = cart.get(id) || 0;
        var next = currentQty + delta;

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
                rows.push(
                    '<div class="cart-row" data-id="' + id + '">' +
                        '<div class="cart-row-main">' +
                            '<div class="cart-row-name" title="' + escapeHtml(product.name) + '">' + escapeHtml(product.name) + '</div>' +
                            '<div class="cart-row-price">' + escapeHtml(formatMoney(product.price)) + ' / ' + escapeHtml(product.unit) + '</div>' +
                        '</div>' +
                        '<div class="qty-stepper">' +
                            '<button type="button" data-action="dec" data-id="' + id + '" aria-label="' + escapeHtml(i18n.decreaseQty || '') + '">−</button>' +
                            '<span>' + escapeHtml(formatQty(qty)) + '</span>' +
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
        var total = Math.max(0, subtotal - discount);
        cartTotalEl.textContent = formatMoney(total);

        document.getElementById('cart-field').value = JSON.stringify(
            Array.from(cart, function (entry) { return { product_id: entry[0], qty: entry[1] }; })
        );
        document.getElementById('discount-field').value = discount;

        if (paymentType === 'qarz') {
            var paid = parseFloat(paidAmountInputEl.value) || 0;
            if (paid > total) {
                paid = total;
                paidAmountInputEl.value = total;
            }
            var remaining = Math.max(0, total - paid);
            debtRemainingLabelEl.textContent = (i18n.debtRemainingLabel || 'Debt: :amount').replace(':amount', formatMoney(remaining));
            document.getElementById('paid-amount-field').value = paid;
        } else {
            document.getElementById('paid-amount-field').value = total;
        }
    }

    function setPaymentType(type) {
        paymentType = type;
        document.getElementById('payment-type-field').value = type;

        document.querySelectorAll('.payment-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-type') === type);
        });

        debtFieldsEl.style.display = type === 'qarz' ? 'block' : 'none';
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

    discountInputEl.addEventListener('input', function () {
        discount = parseFloat(discountInputEl.value) || 0;
        renderCart();
    });

    document.querySelectorAll('.payment-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            setPaymentType(btn.getAttribute('data-type'));
        });
    });

    customerSelectEl.addEventListener('change', updateCustomerFields);
    paidAmountInputEl.addEventListener('input', renderCart);

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

        if (oldSale.paymentType) {
            setPaymentType(oldSale.paymentType);
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

        if (oldSale.paidAmount !== undefined && oldSale.paidAmount !== '') {
            var restoredPaid = parseFloat(oldSale.paidAmount);
            if (!isNaN(restoredPaid)) {
                paidAmountInputEl.value = restoredPaid;
            }
        }
    }

    populateCustomerSelect();
    renderProducts();
    restoreOldState();
    renderCart();
})();

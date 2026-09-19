(function () {
    'use strict';

    var table = document.getElementById('refund-items-table');
    var totalPreviewEl = document.getElementById('refund-total-preview');
    var submitBtnEl = document.getElementById('refund-submit-btn');

    if (!table || !totalPreviewEl || !submitBtnEl) {
        return;
    }

    function formatMoney(n) {
        var rounded = Math.round(n);
        return rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + " so'm";
    }

    function clampInput(input) {
        var max = parseFloat(input.getAttribute('max')) || 0;
        var value = parseFloat(input.value);
        if (isNaN(value) || value < 0) {
            value = 0;
        }
        if (value > max) {
            value = max;
        }
        input.value = value;
        return value;
    }

    // This is only a preview — the server computes the authoritative refund
    // total (net of the sale's discount ratio, see Refund::create()), so this
    // deliberately uses plain gross unit-price × qty for a quick estimate.
    function recalcTotal() {
        var total = 0;
        var anySelected = false;

        table.querySelectorAll('.refund-qty-input').forEach(function (input) {
            var qty = clampInput(input);
            if (qty > 0) {
                anySelected = true;
            }
            var row = input.closest('tr');
            var unitPrice = row ? parseFloat(row.getAttribute('data-unit-price')) || 0 : 0;
            total += qty * unitPrice;
        });

        totalPreviewEl.textContent = formatMoney(total);
        submitBtnEl.disabled = !anySelected;
    }

    table.addEventListener('input', function (e) {
        if (e.target.classList.contains('refund-qty-input')) {
            recalcTotal();
        }
    });

    recalcTotal();
})();

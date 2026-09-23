(function () {
    'use strict';

    // Feature detection: the native BarcodeDetector API (Chrome/Edge/Android
    // WebView as of writing — notably not Firefox or Safari) plus camera
    // access. Where either is missing, every ".barcode-scan-btn" is disabled
    // with a translated tooltip instead of trying to bundle a JS decoding
    // fallback library — this is a "nice to have" progressive enhancement,
    // not a universal requirement.
    var supported = ('BarcodeDetector' in window) &&
        !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);

    var i18n = window.HM_BARCODE_I18N || {};

    document.addEventListener('DOMContentLoaded', function () {
        var buttons = document.querySelectorAll('.barcode-scan-btn');
        if (buttons.length === 0) {
            return;
        }

        if (!supported) {
            buttons.forEach(function (btn) {
                btn.disabled = true;
                btn.title = i18n.scanUnsupported || 'Not supported in this browser';
                btn.setAttribute('aria-label', i18n.scanUnsupported || 'Not supported in this browser');
            });
            return;
        }

        var modal = buildModal();
        document.body.appendChild(modal.el);

        buttons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                var targetSelector = btn.getAttribute('data-target');
                var targetInput = targetSelector ? document.querySelector(targetSelector) : null;
                if (targetInput) {
                    modal.open(targetInput);
                }
            });
        });
    });

    function buildModal() {
        var el = document.createElement('div');
        el.className = 'scan-modal';
        el.setAttribute('role', 'dialog');
        el.setAttribute('aria-modal', 'true');
        el.innerHTML =
            '<div class="scan-modal-card">' +
                '<div class="scan-modal-header">' +
                    '<h2>' + escapeHtml(i18n.scanBarcode || 'Scan barcode') + '</h2>' +
                    '<button type="button" class="scan-modal-close" aria-label="' + escapeHtml(i18n.close || 'Close') + '">✕</button>' +
                '</div>' +
                '<video class="scan-modal-video" playsinline muted autoplay></video>' +
                '<p class="muted scan-modal-hint">' + escapeHtml(i18n.scanHint || 'Point the camera at the barcode') + '</p>' +
            '</div>';

        var videoEl = el.querySelector('.scan-modal-video');
        var closeBtn = el.querySelector('.scan-modal-close');

        var stream = null;
        var detector = null;
        var rafId = null;
        var targetInput = null;

        function stopLoop() {
            if (rafId !== null) {
                cancelAnimationFrame(rafId);
                rafId = null;
            }
        }

        function stopStream() {
            if (stream) {
                stream.getTracks().forEach(function (track) { track.stop(); });
                stream = null;
            }
            videoEl.srcObject = null;
        }

        function close() {
            stopLoop();
            stopStream();
            el.classList.remove('open');
            targetInput = null;
        }

        function detectLoop() {
            if (!detector || videoEl.readyState < 2) {
                rafId = requestAnimationFrame(detectLoop);
                return;
            }

            detector.detect(videoEl).then(function (codes) {
                if (codes && codes.length > 0 && targetInput) {
                    var value = codes[0].rawValue;
                    targetInput.value = value;
                    targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                    targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                    // Lets a page act on a completed scan the way it would on
                    // a hardware scanner's Enter (the POS adds the item).
                    targetInput.dispatchEvent(new CustomEvent('barcode-scanned', { bubbles: true, detail: { code: value } }));
                    close();
                    return;
                }
                rafId = requestAnimationFrame(detectLoop);
            }).catch(function () {
                rafId = requestAnimationFrame(detectLoop);
            });
        }

        function open(input) {
            targetInput = input;
            el.classList.add('open');

            try {
                detector = new window.BarcodeDetector();
            } catch (e) {
                detector = null;
            }

            navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
                .then(function (s) {
                    stream = s;
                    videoEl.srcObject = s;
                    videoEl.play().catch(function () {});
                    rafId = requestAnimationFrame(detectLoop);
                })
                .catch(function () {
                    // Camera permission denied or unavailable — just close; the
                    // cashier/owner can still type the barcode by hand.
                    close();
                });
        }

        closeBtn.addEventListener('click', close);
        el.addEventListener('click', function (e) {
            if (e.target === el) {
                close();
            }
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && el.classList.contains('open')) {
                close();
            }
        });

        return { el: el, open: open, close: close };
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str == null ? '' : String(str);
        return div.innerHTML;
    }
})();

// KassirON landing page — the interactive and animated parts.
// The page (layouts/landing.php, landing/index.php) is complete without this
// script; it only adds: scroll-reveal, the playable till in the hero, the
// feature tour tabs, the internet on/off demo, pointer effects and the mobile
// menu. Texts come from data-* attributes rendered by PHP, so both languages
// work without any string living here.
(function () {
    'use strict';

    window.__lpReady = true;

    var root = document.documentElement;
    var reduceMotion = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
    var finePointer = !!(window.matchMedia && window.matchMedia('(pointer: fine)').matches);
    var hasIO = 'IntersectionObserver' in window;

    function $(id) { return document.getElementById(id); }
    function fmt(n) { return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ' '); }
    function raf(fn) { return window.requestAnimationFrame ? window.requestAnimationFrame(fn) : setTimeout(fn, 16); }

    // Counts a number up/down inside an element.
    function tween(el, from, to, ms) {
        if (!el) { return; }
        if (reduceMotion || from === to) { el.textContent = fmt(to); return; }
        var t0 = null;
        function step(ts) {
            if (t0 === null) { t0 = ts; }
            var p = Math.min((ts - t0) / ms, 1);
            var eased = 1 - Math.pow(1 - p, 3);
            el.textContent = fmt(from + (to - from) * eased);
            if (p < 1) { raf(step); }
        }
        raf(step);
    }

    /* ---------- Header: shadow on scroll, reading progress, mobile menu ---------- */
    var header = $('lp-header');
    var progress = document.querySelector('.lp-progress');
    var ticking = false;
    function onScroll() {
        if (ticking) { return; }
        ticking = true;
        raf(function () {
            ticking = false;
            var y = window.pageYOffset || root.scrollTop;
            if (header) { header.classList.toggle('is-scrolled', y > 8); }
            if (progress) {
                var max = root.scrollHeight - window.innerHeight;
                progress.style.setProperty('--p', max > 0 ? Math.min(y / max, 1).toFixed(4) : 0);
            }
        });
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();

    var burger = $('lp-burger');
    var mnav = $('lp-mnav');
    if (burger && mnav) {
        var setMenu = function (open) {
            mnav.hidden = !open;
            burger.setAttribute('aria-expanded', open ? 'true' : 'false');
        };
        burger.addEventListener('click', function () { setMenu(mnav.hidden); });
        mnav.addEventListener('click', function (e) {
            if (e.target.closest && e.target.closest('a')) { setMenu(false); }
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && !mnav.hidden) { setMenu(false); burger.focus(); }
        });
        window.addEventListener('resize', function () { if (window.innerWidth > 1000) { setMenu(false); } });
    }

    // Highlight the nav link of the section being read.
    if (hasIO) {
        var links = {};
        document.querySelectorAll('.lp-nav a[href^="#"]').forEach(function (a) { links[a.getAttribute('href').slice(1)] = a; });
        var spy = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) { return; }
                Object.keys(links).forEach(function (id) { links[id].classList.toggle('is-active', id === entry.target.id); });
            });
        }, { rootMargin: '-45% 0px -50% 0px' });
        Object.keys(links).forEach(function (id) {
            var section = $(id);
            if (section) { spy.observe(section); }
        });
    }

    /* ---------- Scroll reveal ---------- */
    var revealEls = document.querySelectorAll('[data-reveal]');
    if (!hasIO) {
        revealEls.forEach(function (el) { el.classList.add('is-in'); });
    } else {
        var revealIO = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('is-in');
                    revealIO.unobserve(entry.target);
                }
            });
        }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
        revealEls.forEach(function (el) { revealIO.observe(el); });
    }

    /* ---------- Pointer effects ---------- */
    // Feature cards: a soft light following the cursor.
    document.querySelectorAll('.lp-spot').forEach(function (card) {
        card.addEventListener('pointermove', function (e) {
            var r = card.getBoundingClientRect();
            card.style.setProperty('--x', (e.clientX - r.left) + 'px');
            card.style.setProperty('--y', (e.clientY - r.top) + 'px');
        });
    });

    var hero = document.querySelector('.lp-hero');
    var stage = $('lp-stage');
    var posEl = $('lp-pos');
    if (hero && finePointer && !reduceMotion) {
        // The dot grid lights up under the pointer.
        hero.addEventListener('pointermove', function (e) {
            var r = hero.getBoundingClientRect();
            hero.style.setProperty('--sx', (e.clientX - r.left) + 'px');
            hero.style.setProperty('--sy', (e.clientY - r.top) + 'px');
        });
        hero.addEventListener('pointerleave', function () {
            hero.style.removeProperty('--sx');
            hero.style.removeProperty('--sy');
        });
        // The till tilts toward the pointer and the floating cards drift against
        // it — but the till lies flat while the pointer is over it, so its
        // buttons never move under a cursor that is about to click them.
        if (stage && posEl) {
            hero.addEventListener('pointermove', function (e) {
                var r = stage.getBoundingClientRect();
                var nx = Math.max(-1, Math.min(1, (e.clientX - (r.left + r.width / 2)) / (window.innerWidth / 2)));
                var ny = Math.max(-1, Math.min(1, (e.clientY - (r.top + r.height / 2)) / (window.innerHeight / 2)));
                var flat = posEl.contains(e.target);
                posEl.style.setProperty('--ry', flat ? '0deg' : (nx * 6).toFixed(2) + 'deg');
                posEl.style.setProperty('--rx', flat ? '0deg' : (-ny * 4).toFixed(2) + 'deg');
                stage.style.setProperty('--mx', nx.toFixed(3));
                stage.style.setProperty('--my', ny.toFixed(3));
            });
            hero.addEventListener('pointerleave', function () {
                posEl.style.setProperty('--ry', '0deg');
                posEl.style.setProperty('--rx', '0deg');
                stage.style.setProperty('--mx', 0);
                stage.style.setProperty('--my', 0);
            });
        }
    }

    /* ---------- The playable till ---------- */
    (function () {
        var pos = posEl;
        if (!pos) { return; }
        var D = pos.dataset;
        var tiles = pos.querySelectorAll('.lp-tile');
        var linesEl = $('lp-cart-lines');
        var totalEl = $('lp-total');
        var countEl = $('lp-cart-count');
        var sellBtn = $('lp-sell');
        var wrap = $('lp-receipt-wrap');
        var receipt = $('lp-receipt');
        var againBtn = $('lp-again');
        var toast = $('lp-toast');
        var revEl = $('lp-rev');
        var revCard = document.querySelector('.lp-float-rev');
        var syncCard = $('lp-sync');
        var payBtns = pos.querySelectorAll('.lp-pay button');

        var info = {};
        tiles.forEach(function (tile) {
            info[tile.dataset.id] = {
                name: tile.dataset.name,
                price: parseInt(tile.dataset.price, 10),
                emoji: tile.dataset.emoji,
                tile: tile
            };
        });

        var order = [];
        var qty = {};
        var rows = {};
        var pay = 'cash';
        var shownTotal = 0;
        var revenue = revEl ? parseInt(revEl.dataset.value, 10) : 0;
        var toastTimer = null;
        var syncTimer = null;

        function total() {
            return order.reduce(function (sum, id) { return sum + info[id].price * qty[id]; }, 0);
        }

        function makeStep(id, delta, label) {
            var b = document.createElement('button');
            b.type = 'button';
            b.className = 'lp-cart-step';
            b.dataset.id = id;
            b.dataset.d = delta;
            b.setAttribute('aria-label', info[id].name + ' ' + label);
            b.innerHTML = delta > 0
                ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" aria-hidden="true"><path d="M12 6v12M6 12h12"/></svg>'
                : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" aria-hidden="true"><path d="M6 12h12"/></svg>';
            return b;
        }

        function makeRow(id) {
            var li = document.createElement('li');
            li.className = 'lp-cart-line';
            var em = document.createElement('span');
            em.className = 'em';
            em.textContent = info[id].emoji;
            var nm = document.createElement('span');
            nm.className = 'nm';
            nm.appendChild(document.createTextNode(info[id].name));
            var small = document.createElement('small');
            nm.appendChild(small);
            var q = document.createElement('span');
            q.className = 'lp-cart-qty';
            var num = document.createElement('b');
            q.appendChild(makeStep(id, -1, '−'));
            q.appendChild(num);
            q.appendChild(makeStep(id, 1, '+'));
            li.appendChild(em);
            li.appendChild(nm);
            li.appendChild(q);
            li._small = small;
            li._num = num;
            return li;
        }

        function render() {
            // Rows: add the new ones, update the rest, drop the removed.
            Object.keys(rows).forEach(function (id) {
                if (!qty[id]) { rows[id].remove(); delete rows[id]; }
            });
            var emptyEl = linesEl.querySelector('.lp-cart-empty');
            if (!order.length) {
                if (!emptyEl) {
                    emptyEl = document.createElement('li');
                    emptyEl.className = 'lp-cart-empty';
                    emptyEl.textContent = D.tEmpty;
                    linesEl.appendChild(emptyEl);
                }
            } else if (emptyEl) {
                emptyEl.remove();
            }
            order.forEach(function (id) {
                if (!rows[id]) {
                    rows[id] = makeRow(id);
                    linesEl.appendChild(rows[id]);
                    linesEl.scrollTop = linesEl.scrollHeight;
                }
                rows[id]._num.textContent = qty[id];
                rows[id]._small.textContent = fmt(info[id].price * qty[id]) + ' ' + D.cur;
            });

            var count = 0;
            Object.keys(info).forEach(function (id) {
                var n = qty[id] || 0;
                count += n;
                var tile = info[id].tile;
                tile.classList.toggle('has-qty', n > 0);
                tile.querySelector('.lp-tile-qty').textContent = n || '';
            });
            countEl.textContent = count;
            sellBtn.disabled = !order.length;

            var to = total();
            tween(totalEl, shownTotal, to, 380);
            shownTotal = to;
        }

        function hideReceipt() {
            wrap.hidden = true;
            pos.classList.remove('is-printing');
        }

        function add(id, delta) {
            delta = delta || 1;
            if (!wrap.hidden) { hideReceipt(); }
            var next = (qty[id] || 0) + delta;
            if (next <= 0) {
                delete qty[id];
                order = order.filter(function (x) { return x !== id; });
            } else {
                if (!qty[id]) { order.push(id); }
                qty[id] = next;
            }
            render();
        }

        function say(text) {
            toast.textContent = text;
            toast.classList.add('is-on');
            clearTimeout(toastTimer);
            toastTimer = setTimeout(function () { toast.classList.remove('is-on'); }, 2400);
        }

        function payLabel() {
            return pay === 'cash' ? D.tCash : pay === 'card' ? D.tCard : D.tDebt;
        }

        function sell() {
            if (!order.length) { return; }
            var sum = total();

            // Fill the paper, then print it.
            var lines = receipt.querySelector('[data-r="lines"]');
            lines.textContent = '';
            order.forEach(function (id) {
                var li = document.createElement('li');
                var a = document.createElement('span');
                a.textContent = info[id].name + ' × ' + qty[id];
                var b = document.createElement('span');
                b.textContent = fmt(info[id].price * qty[id]);
                li.appendChild(a);
                li.appendChild(b);
                lines.appendChild(li);
            });
            receipt.querySelector('[data-r="shop"]').textContent = D.shop;
            var now = new Date();
            var locale = D.lang === 'ru' ? 'ru-RU' : 'uz-UZ';
            var stamp;
            try { stamp = now.toLocaleString(locale, { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }); } catch (e) { stamp = now.toLocaleString(); }
            receipt.querySelector('[data-r="date"]').textContent = stamp;
            receipt.querySelector('[data-r="total"]').textContent = fmt(sum) + ' ' + D.cur;
            receipt.querySelector('[data-r="pay"]').textContent = payLabel();

            pos.classList.remove('is-printing');
            wrap.hidden = false;
            void pos.offsetWidth; // restart the print animation
            pos.classList.add('is-printing');

            // Revenue (not debt) and the sync chip react.
            if (pay === 'debt') {
                say(D.tDebtnote);
            } else if (revEl) {
                tween(revEl, revenue, revenue + sum, 900);
                revenue += sum;
                if (revCard) {
                    revCard.classList.remove('is-bump');
                    void revCard.offsetWidth;
                    revCard.classList.add('is-bump');
                }
            }
            if (syncCard) {
                var label = syncCard.querySelector('[data-sync-text]');
                syncCard.classList.add('is-syncing');
                label.textContent = syncCard.dataset.tSyncing;
                clearTimeout(syncTimer);
                syncTimer = setTimeout(function () {
                    syncCard.classList.remove('is-syncing');
                    label.textContent = syncCard.dataset.tSynced;
                }, 1400);
            }

            order = [];
            qty = {};
            render();
        }

        tiles.forEach(function (tile) {
            tile.addEventListener('click', function () {
                add(tile.dataset.id, 1);
                tile.classList.remove('is-pop');
                void tile.offsetWidth;
                tile.classList.add('is-pop');
            });
        });
        linesEl.addEventListener('click', function (e) {
            var btn = e.target.closest ? e.target.closest('.lp-cart-step') : null;
            if (btn) { add(btn.dataset.id, parseInt(btn.dataset.d, 10)); }
        });
        payBtns.forEach(function (btn) {
            btn.addEventListener('click', function () {
                pay = btn.dataset.pay;
                payBtns.forEach(function (b) {
                    var on = b === btn;
                    b.classList.toggle('is-on', on);
                    b.setAttribute('aria-checked', on ? 'true' : 'false');
                });
            });
        });
        sellBtn.addEventListener('click', sell);
        againBtn.addEventListener('click', hideReceipt);

        // One self-playing round, so the till shows what it does before anyone
        // touches it. Any interaction stops it, and it never repeats.
        var autoTimers = [];
        var autoStopped = false;
        var autoStarted = false;
        function stopAuto() {
            autoStopped = true;
            autoTimers.forEach(clearTimeout);
            autoTimers = [];
        }
        ['pointerdown', 'keydown', 'focusin'].forEach(function (ev) { pos.addEventListener(ev, stopAuto); });
        function startAuto() {
            if (autoStarted || autoStopped || reduceMotion) { return; }
            autoStarted = true;
            var seq = ['bread', 'milk', 'milk', 'tea'];
            seq.forEach(function (id, i) {
                autoTimers.push(setTimeout(function () {
                    if (autoStopped) { return; }
                    add(id, 1);
                    var tile = info[id].tile;
                    tile.classList.remove('is-pop');
                    void tile.offsetWidth;
                    tile.classList.add('is-pop');
                }, 900 + i * 750));
            });
            autoTimers.push(setTimeout(function () { if (!autoStopped) { sell(); } }, 900 + seq.length * 750 + 500));
        }
        if (hasIO && stage) {
            var autoIO = new IntersectionObserver(function (entries) {
                if (entries[0].isIntersecting) { autoIO.disconnect(); startAuto(); }
            }, { threshold: 0.5 });
            autoIO.observe(stage);
        }

        render();
    })();

    /* ---------- Feature tour (tabs with a progress bar) ---------- */
    (function () {
        var ui = $('lp-tour');
        if (!ui) { return; }
        var tabs = Array.prototype.slice.call(ui.querySelectorAll('.lp-tour-tab'));
        var panels = Array.prototype.slice.call(ui.querySelectorAll('.lp-tour-panel'));
        var DURATION = 7000;
        var current = 0;
        var userTook = false;
        var inView = false;
        var hovering = false;
        var startTs = 0;
        var pausedAt = 0;
        var looping = false;

        function select(i, focus) {
            current = i;
            tabs.forEach(function (tab, n) {
                var on = n === i;
                tab.classList.toggle('is-active', on);
                tab.setAttribute('aria-selected', on ? 'true' : 'false');
                tab.tabIndex = on ? 0 : -1;
                tab.style.setProperty('--tp', 0);
                panels[n].classList.toggle('is-active', on);
            });
            if (focus) { tabs[i].focus(); }
        }

        function tick(ts) {
            if (userTook || !inView || reduceMotion || document.hidden) { looping = false; return; }
            if (hovering) { raf(tick); return; }
            if (!startTs) { startTs = ts; }
            var p = (ts - startTs) / DURATION;
            tabs[current].style.setProperty('--tp', Math.min(p, 1).toFixed(4));
            if (p >= 1) {
                select((current + 1) % tabs.length);
                startTs = ts;
            }
            raf(tick);
        }
        function run() {
            if (looping || userTook || !inView || reduceMotion) { return; }
            looping = true;
            startTs = 0;
            raf(tick);
        }

        tabs.forEach(function (tab, i) {
            tab.addEventListener('click', function () {
                userTook = true;
                select(i);
            });
            tab.addEventListener('keydown', function (e) {
                var k = e.key;
                var next = null;
                if (k === 'ArrowDown' || k === 'ArrowRight') { next = (i + 1) % tabs.length; }
                else if (k === 'ArrowUp' || k === 'ArrowLeft') { next = (i - 1 + tabs.length) % tabs.length; }
                else if (k === 'Home') { next = 0; }
                else if (k === 'End') { next = tabs.length - 1; }
                if (next !== null) {
                    e.preventDefault();
                    userTook = true;
                    select(next, true);
                }
            });
        });
        ui.addEventListener('pointerenter', function () { hovering = true; pausedAt = performance.now(); });
        ui.addEventListener('pointerleave', function () {
            hovering = false;
            if (startTs) { startTs += performance.now() - pausedAt; }
        });
        document.addEventListener('visibilitychange', function () { if (!document.hidden) { run(); } });

        if (hasIO) {
            new IntersectionObserver(function (entries) {
                inView = entries[0].isIntersecting;
                if (inView) { run(); }
            }, { threshold: 0.35 }).observe(ui);
        }
    })();

    /* ---------- Internet on/off demo ---------- */
    (function () {
        var net = $('lp-net');
        var sw = $('lp-net-switch');
        if (!net || !sw) { return; }
        var D = net.dataset;
        var titleEl = net.querySelector('[data-net="title"]');
        var subEl = net.querySelector('[data-net="sub"]');
        var toasts = $('lp-net-toasts');
        var cloud = net.querySelector('.lp-cloud');
        var hint = $('lp-net-hint');
        var amounts = [24000, 12500, 36000, 9500, 18000, 27500];
        var MAX_QUEUE = 6;
        var queue = 0;
        var state = 'online';
        var saleTimer = null;
        var syncTimer = null;
        var autoTimers = [];
        var autoOn = true;

        function setState(next) {
            state = next;
            net.dataset.state = next;
            sw.setAttribute('aria-checked', next === 'offline' ? 'false' : 'true');
            if (next === 'online') {
                titleEl.textContent = D.tOnline;
                subEl.textContent = D.tSynced;
            } else {
                titleEl.textContent = next === 'offline' ? D.tOffline : D.tSyncing;
                subEl.textContent = D.tQueue.replace('{n}', queue);
            }
        }

        function toast(text, sync) {
            if (!toasts) { return; }
            var el = document.createElement('span');
            el.className = 'lp-net-toast' + (sync ? ' is-sync' : '');
            el.textContent = text;
            var box = toasts.getBoundingClientRect();
            var left = 8 + Math.random() * 48;
            var top = 52 + Math.random() * 30;
            el.style.left = left + '%';
            el.style.top = top + '%';
            if (sync && cloud) {
                var c = cloud.getBoundingClientRect();
                el.style.setProperty('--fx', (c.left + c.width / 2 - box.left - box.width * left / 100) + 'px');
                el.style.setProperty('--fy', (c.top + c.height / 2 - box.top - box.height * top / 100) + 'px');
            }
            toasts.appendChild(el);
            setTimeout(function () { el.remove(); }, 2000);
        }

        function addSale() {
            if (queue >= MAX_QUEUE) { clearInterval(saleTimer); return; }
            var amount = amounts[queue % amounts.length];
            queue++;
            toast('+ ' + fmt(amount) + ' ' + D.cur, false);
            subEl.textContent = D.tQueue.replace('{n}', queue);
        }

        function goOffline() {
            clearInterval(saleTimer);
            clearInterval(syncTimer);
            queue = 0;
            setState('offline');
            setTimeout(addSale, 500);
            saleTimer = setInterval(addSale, 1300);
        }

        function goOnline() {
            clearInterval(saleTimer);
            clearInterval(syncTimer);
            if (!queue) { setState('online'); return; }
            setState('syncing');
            syncTimer = setInterval(function () {
                if (queue > 0) {
                    queue--;
                    toast('✓ ' + fmt(amounts[queue % amounts.length]), true);
                    if (cloud) {
                        cloud.classList.remove('is-ping');
                        void cloud.offsetWidth;
                        cloud.classList.add('is-ping');
                    }
                    subEl.textContent = D.tQueue.replace('{n}', queue);
                }
                if (queue === 0) {
                    clearInterval(syncTimer);
                    setTimeout(function () { if (state === 'syncing') { setState('online'); } }, 600);
                }
            }, 520);
        }

        function stopAuto() {
            autoOn = false;
            autoTimers.forEach(clearTimeout);
            autoTimers = [];
        }

        sw.addEventListener('click', function () {
            stopAuto();
            if (hint) { hint.classList.add('is-gone'); }
            if (state === 'offline') { goOnline(); } else { goOffline(); }
        });

        // One self-playing round when it scrolls into view.
        if (hasIO && !reduceMotion) {
            var io = new IntersectionObserver(function (entries) {
                if (!entries[0].isIntersecting) { return; }
                io.disconnect();
                if (!autoOn) { return; }
                autoTimers.push(setTimeout(function () { if (autoOn) { goOffline(); } }, 1400));
                autoTimers.push(setTimeout(function () { if (autoOn) { goOnline(); autoOn = false; } }, 5600));
            }, { threshold: 0.6 });
            io.observe(net);
        }
    })();
})();

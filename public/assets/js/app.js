if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/sw.js').catch(function () {
            // Installability is a progressive enhancement — ignore failures.
        });
    });
}

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.alert').forEach(function (alertEl) {
        setTimeout(function () {
            alertEl.style.transition = 'opacity .4s ease';
            alertEl.style.opacity = '0';
            setTimeout(function () { alertEl.remove(); }, 400);
        }, 4000);
    });

    var themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.addEventListener('click', function () {
            var root = document.documentElement;
            var current = root.getAttribute('data-theme');
            if (!current) {
                current = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
            }
            var next = current === 'dark' ? 'light' : 'dark';
            root.setAttribute('data-theme', next);
            try {
                localStorage.setItem('kassiron-theme', next);
            } catch (e) {}
        });
    }

    var moreToggle = document.getElementById('more-nav-toggle');
    var drawer = document.getElementById('mobile-drawer');
    if (moreToggle && drawer) {
        moreToggle.addEventListener('click', function () {
            drawer.classList.add('open');
        });
        drawer.addEventListener('click', function (e) {
            if (e.target === drawer) {
                drawer.classList.remove('open');
            }
        });
    }
});

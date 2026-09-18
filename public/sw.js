var CACHE_NAME = 'hisobmaster-static-v1';
var STATIC_ASSETS = [
    '/assets/css/app.css',
    '/assets/js/app.js',
    '/assets/img/icon-192.png',
    '/assets/img/icon-512.png'
];

self.addEventListener('install', function (event) {
    event.waitUntil(
        caches.open(CACHE_NAME).then(function (cache) {
            return cache.addAll(STATIC_ASSETS);
        })
    );
    self.skipWaiting();
});

self.addEventListener('activate', function (event) {
    event.waitUntil(
        caches.keys().then(function (keys) {
            return Promise.all(keys.filter(function (key) {
                return key !== CACHE_NAME;
            }).map(function (key) {
                return caches.delete(key);
            }));
        })
    );
    self.clients.claim();
});

// Only static assets are served from cache-first. Every page request (which
// carries live, per-user financial data) always goes straight to the network.
self.addEventListener('fetch', function (event) {
    var url = new URL(event.request.url);
    var isStaticAsset = url.pathname.indexOf('/assets/') === 0;

    if (!isStaticAsset || event.request.method !== 'GET') {
        return;
    }

    event.respondWith(
        caches.match(event.request).then(function (cached) {
            return cached || fetch(event.request);
        })
    );
});

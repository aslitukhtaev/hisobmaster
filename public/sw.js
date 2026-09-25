var CACHE_NAME = 'kassiron-static-v3';

// Pages link their CSS/JS/images with the file's version (asset() in
// app/helpers.php adds ?v=<modification time>), so a versioned URL never
// changes content: it is served from the cache once fetched. A new deploy
// means new URLs, so nobody is left on an old app.css / app.js — which is
// what happened with v2, whose cache-first on unversioned URLs kept serving
// the files it had installed with.
self.addEventListener('install', function () {
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

// Keeps one version per file: the new one replaces the ones before it.
function store(request, response) {
    var url = new URL(request.url);
    return caches.open(CACHE_NAME).then(function (cache) {
        return cache.keys().then(function (keys) {
            return Promise.all(keys.filter(function (key) {
                var old = new URL(key.url);
                return old.pathname === url.pathname && old.search !== url.search;
            }).map(function (key) {
                return cache.delete(key);
            }));
        }).then(function () {
            return cache.put(request, response);
        });
    });
}

// Only static assets are cached. Every page request (which carries live,
// per-user financial data) always goes straight to the network.
self.addEventListener('fetch', function (event) {
    var url = new URL(event.request.url);
    if (event.request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.indexOf('/assets/') !== 0) {
        return;
    }

    // Unversioned (the manifest's icons): network first, the cache only
    // when offline.
    if (!url.searchParams.has('v')) {
        event.respondWith(
            fetch(event.request).then(function (response) {
                if (response.ok) {
                    var copy = response.clone();
                    caches.open(CACHE_NAME).then(function (cache) { cache.put(event.request, copy); });
                }
                return response;
            }).catch(function () {
                return caches.match(event.request).then(function (cached) {
                    return cached || Response.error();
                });
            })
        );
        return;
    }

    event.respondWith(
        caches.match(event.request).then(function (cached) {
            if (cached) {
                return cached;
            }
            return fetch(event.request).then(function (response) {
                if (response.ok) {
                    var copy = response.clone();
                    event.waitUntil(store(event.request, copy));
                }
                return response;
            });
        })
    );
});

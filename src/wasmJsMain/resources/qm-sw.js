// QR Master service worker — cache-first ONLY for the heavy on-device
// model resources (background-removal model + wasm runtime, face-api),
// so a returning visitor never downloads them again, even when the
// browser's ordinary HTTP cache has been evicted. Everything else
// (pages, app bundles, ads) is left to the normal network path.
var QM_MODEL_CACHE = 'qm-models-v1';
var QM_MODEL_PREFIXES = ['/bgmodel/', '/bglib/', '/faceapi/'];

self.addEventListener('install', function (e) {
    self.skipWaiting();
});

self.addEventListener('activate', function (e) {
    e.waitUntil((async function () {
        // Drop model caches from older versions of this worker.
        var names = await caches.keys();
        await Promise.all(names.map(function (n) {
            return (n.indexOf('qm-models-') === 0 && n !== QM_MODEL_CACHE) ? caches.delete(n) : Promise.resolve();
        }));
        await self.clients.claim();
    })());
});

self.addEventListener('fetch', function (e) {
    var url;
    try { url = new URL(e.request.url); } catch (err) { return; }
    if (e.request.method !== 'GET') return;
    if (url.origin !== self.location.origin) return;
    var isModel = QM_MODEL_PREFIXES.some(function (p) { return url.pathname.indexOf(p) === 0; });
    if (!isModel) return;
    e.respondWith((async function () {
        var cache = await caches.open(QM_MODEL_CACHE);
        var hit = await cache.match(e.request);
        if (hit) return hit;
        var res = await fetch(e.request);
        if (res && res.ok) {
            try { await cache.put(e.request, res.clone()); } catch (err) {}
        }
        return res;
    })());
});

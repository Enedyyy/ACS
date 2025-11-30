const CACHE_NAME = 'fintrack-cache-v18';
const APP_SHELL = [
  '/',
  '/index.html',
  '/styles.css?v=10',
];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE_NAME);
    await cache.addAll(APP_SHELL);
  })());
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.map(k => k === CACHE_NAME ? null : caches.delete(k)));
  })());
  self.clients.claim();
});

// Stale-while-revalidate for same-origin GET requests
self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE_NAME);
    const cached = await cache.match(req, { ignoreSearch: true });
    const fetchPromise = fetch(req).then((res) => {
      if (res && res.status === 200 && (req.url.endsWith('.js') || req.url.endsWith('.css') || req.url.endsWith('.html') || req.url.endsWith('.svg') || req.url.endsWith('.ico') || req.destination === 'document' || req.destination === 'image')) {
        cache.put(req, res.clone());
      }
      return res;
    }).catch(() => cached);
    return cached || fetchPromise;
  })());
});




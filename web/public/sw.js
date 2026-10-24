/* VYBE Progressive Web App Service Worker
 * Security-First Caching Policy:
 * 1. NEVER caches Supabase Auth, PostgREST, Storage, Realtime, or authenticated requests.
 * 2. NEVER auto-reloads mid-call or mid-draft; waits for explicit SKIP_WAITING user confirmation.
 * 3. Uses Network-First for HTML navigation to prevent stale HTML / JS chunk mismatches.
 */

const BUILD_VERSION = '__VYBE_BUILD_VERSION__';
const STATIC_CACHE = `vybe-static-${BUILD_VERSION}`;
const SHELL_ASSETS = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/fonts/syne.ttf'
];

function isSensitiveOrApiRequest(request, url) {
  if (request.method !== 'GET') return true;
  if (request.headers.get('authorization') || request.headers.get('apikey')) return true;
  if (url.origin !== self.location.origin) return true;
  const p = url.pathname;
  if (
    p.startsWith('/auth/') ||
    p.startsWith('/rest/') ||
    p.startsWith('/storage/') ||
    p.startsWith('/realtime/') ||
    p.startsWith('/functions/') ||
    p === '/version.json' ||
    p === '/sw.js'
  ) {
    return true;
  }
  return false;
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(STATIC_CACHE)
      .then((cache) => cache.addAll(SHELL_ASSETS).catch(() => undefined))
  );
  // Intentionally do NOT call self.skipWaiting() unconditionally here so active calls/drafts are never interrupted.
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith('vybe-static-') && key !== STATIC_CACHE)
            .map((oldKey) => caches.delete(oldKey))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
  if (event.data && event.data.type === 'CLEAR_CACHES') {
    event.waitUntil(
      caches.keys().then((keys) => Promise.all(keys.map((k) => caches.delete(k))))
    );
  }
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (isSensitiveOrApiRequest(event.request, url)) {
    return;
  }

  // HTML / SPA Navigation: Network-first with cached index.html offline fallback
  if (event.request.mode === 'navigate' || event.request.headers.get('accept')?.includes('text/html')) {
    event.respondWith(
      fetch(event.request)
        .then((networkResp) => {
          if (networkResp && networkResp.status === 200) {
            const copy = networkResp.clone();
            caches.open(STATIC_CACHE).then((cache) => cache.put('/index.html', copy));
          }
          return networkResp;
        })
        .catch(() => caches.match('/index.html'))
    );
    return;
  }

  // Immutable Vite hashed assets, fonts & icons: Cache-first with network population
  if (
    url.pathname.startsWith('/assets/') ||
    url.pathname.startsWith('/fonts/') ||
    url.pathname.startsWith('/icons/')
  ) {
    event.respondWith(
      caches.match(event.request).then((cached) => {
        if (cached) return cached;
        return fetch(event.request).then((resp) => {
          if (resp && resp.status === 200 && resp.type === 'basic') {
            const copy = resp.clone();
            caches.open(STATIC_CACHE).then((cache) => cache.put(event.request, copy));
          }
          return resp;
        });
      })
    );
  }
});

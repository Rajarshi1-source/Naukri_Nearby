/*
 * NaukriNearby service worker (ADR 0009). Hand-written on purpose: small, auditable, and not tied to a
 * Next.js plugin's release cycle.
 *
 * Strategies
 *   - /_next/static/**, /icons/**           cache-first (content-hashed / versioned assets)
 *   - public job API (GET /api/jobs/search, GET /api/jobs/{slug}) on the API origin
 *                                            network-first, 4 s timeout, cache fallback
 *   - same-origin pages and RSC payloads     network-first, cache fallback; navigations fall back to /offline
 * Never cached: any request carrying an Authorization header, any non-GET, any other API route.
 * Bump CACHE_VERSION whenever these rules change; old caches are deleted on activate.
 */
const CACHE_VERSION = "v1";
const STATIC_CACHE = `nn-static-${CACHE_VERSION}`;
const PAGE_CACHE = `nn-pages-${CACHE_VERSION}`;
const API_CACHE = `nn-api-${CACHE_VERSION}`;
const NETWORK_TIMEOUT_MS = 4000;
const PRECACHE = ["/offline", "/", "/jobs", "/manifest.webmanifest", "/icons/icon-192.png", "/icons/icon-512.png"];

const API_ORIGIN = (() => {
  try {
    return new URL(new URL(self.location.href).searchParams.get("api") || self.location.origin).origin;
  } catch {
    return self.location.origin;
  }
})();

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(PAGE_CACHE)
      .then((cache) => Promise.allSettled(PRECACHE.map((url) => cache.add(url))))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  const keep = new Set([STATIC_CACHE, PAGE_CACHE, API_CACHE]);
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith("nn-") && !keep.has(k)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

function isPublicJobApi(url) {
  if (url.origin !== API_ORIGIN) return false;
  if (url.pathname === "/api/jobs/search") return true;
  // /api/jobs/{slug} — but not /api/jobs/recommended (personal) or deeper paths.
  const m = url.pathname.match(/^\/api\/jobs\/([^/]+)$/);
  return m !== null && m[1] !== "recommended";
}

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timeout")), ms);
    promise.then(
      (value) => { clearTimeout(timer); resolve(value); },
      (err) => { clearTimeout(timer); reject(err); },
    );
  });
}

async function networkFirst(request, cacheName, fallbackUrl) {
  const cache = await caches.open(cacheName);
  try {
    const response = await withTimeout(fetch(request), NETWORK_TIMEOUT_MS);
    if (response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  } catch (err) {
    const cached = await cache.match(request, { ignoreVary: true });
    if (cached) return cached;
    if (fallbackUrl) {
      const fallback = await caches.match(fallbackUrl);
      if (fallback) return fallback;
    }
    throw err;
  }
}

async function cacheFirst(request) {
  const cache = await caches.open(STATIC_CACHE);
  const cached = await cache.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) cache.put(request, response.clone());
  return response;
}

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET" || request.headers.has("Authorization")) return;

  const url = new URL(request.url);

  if (url.origin === self.location.origin) {
    if (url.pathname.startsWith("/_next/static/") || url.pathname.startsWith("/icons/")) {
      event.respondWith(cacheFirst(request));
      return;
    }
    if (url.pathname.startsWith("/_next/") || url.pathname === "/sw.js") return;
    event.respondWith(networkFirst(request, PAGE_CACHE, request.mode === "navigate" ? "/offline" : null));
    return;
  }

  if (isPublicJobApi(url)) {
    event.respondWith(networkFirst(request, API_CACHE, null));
  }
});

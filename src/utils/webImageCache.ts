import { vybeDiagnostics } from './performanceDiagnostics';

const IMAGE_CACHE_BUCKET = 'vybe-image-cache-v1';
const MAX_CACHED_IMAGES = 180;
const imageUrlRegistry = new Set<string>();

/**
 * Normalizes a remote image URL into a stable canonical cache key by stripping
 * ephemeral signed-URL query parameters (e.g. `?token=`, `?X-Amz-...`) while
 * preserving immutable storage object paths.
 */
export function normalizeImageCacheKey(rawUrl: string): string {
  const trimmed = (rawUrl || '').trim();
  if (!trimmed) return '';
  if (trimmed.startsWith('data:') || trimmed.startsWith('blob:')) return trimmed;
  try {
    const parsed = new URL(trimmed);
    if (parsed.pathname.includes('/storage/v1/object/')) {
      const canonicalPath = parsed.pathname
        .replace('/storage/v1/object/sign/', '/storage/v1/object/')
        .replace('/storage/v1/object/authenticated/', '/storage/v1/object/')
        .replace('/storage/v1/object/public/', '/storage/v1/object/');
      return `${parsed.origin}${canonicalPath}`;
    }
    parsed.searchParams.delete('token');
    parsed.searchParams.delete('t');
    return `${parsed.origin}${parsed.pathname}${parsed.search}`;
  } catch {
    return trimmed.split('#')[0];
  }
}

/**
 * Registers an image load in session telemetry and opportunistically persists public
 * image responses into the browser's CacheStorage API for repeat-view offline/zero-egress hits.
 */
export async function trackAndCacheImage(url: string): Promise<void> {
  const cleanUrl = (url || '').trim();
  if (!cleanUrl || cleanUrl.startsWith('data:') || cleanUrl.startsWith('blob:')) return;

  const key = normalizeImageCacheKey(cleanUrl);
  if (imageUrlRegistry.has(key)) {
    vybeDiagnostics.recordCacheHit(key, 48_000);
    return;
  }
  imageUrlRegistry.add(key);

  if (typeof window === 'undefined' || !('caches' in window)) return;
  try {
    const cache = await caches.open(IMAGE_CACHE_BUCKET);
    const cacheReq = new Request(key, { mode: 'cors' });
    const existing = await cache.match(cacheReq);
    if (existing) {
      vybeDiagnostics.recordCacheHit(key, 64_000);
      return;
    }

    // Only prefetch cacheable public URLs (avoid re-fetching private signed URLs into CORS issues)
    if (cleanUrl.includes('/storage/v1/object/public/')) {
      const res = await fetch(cleanUrl, { cache: 'force-cache', mode: 'cors' });
      if (res.ok) {
        await cache.put(cacheReq, res.clone());
        const keys = await cache.keys();
        if (keys.length > MAX_CACHED_IMAGES) {
          await cache.delete(keys[0]);
        }
      }
    }
  } catch {
    // Graceful no-op in offline / sandboxed WebView / CORS-restricted environments
  }
}

export async function clearWebImageCacheStorage(): Promise<void> {
  imageUrlRegistry.clear();
  if (typeof window !== 'undefined' && 'caches' in window) {
    try {
      await caches.delete(IMAGE_CACHE_BUCKET);
    } catch {}
  }
}

export async function getWebImageCacheStats(): Promise<{ count: number; estimatedBytes: number }> {
  if (typeof window === 'undefined' || !('caches' in window)) {
    return { count: imageUrlRegistry.size, estimatedBytes: 0 };
  }
  try {
    const has = await caches.has(IMAGE_CACHE_BUCKET);
    if (!has) return { count: imageUrlRegistry.size, estimatedBytes: 0 };
    const cache = await caches.open(IMAGE_CACHE_BUCKET);
    const keys = await cache.keys();
    let total = 0;
    for (const req of keys) {
      const resp = await cache.match(req);
      const lenHeader = resp?.headers.get('content-length');
      if (lenHeader) {
        total += Number(lenHeader) || 0;
      } else if (resp) {
        const buf = await resp.clone().arrayBuffer().catch(() => null);
        total += buf ? buf.byteLength : 0;
      }
    }
    return { count: keys.length, estimatedBytes: total };
  } catch {
    return { count: imageUrlRegistry.size, estimatedBytes: 0 };
  }
}

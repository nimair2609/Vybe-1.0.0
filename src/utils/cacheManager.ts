import { vybeCache } from './indexedDbCache';
import { clearWebImageCacheStorage, getWebImageCacheStats } from './webImageCache';
import { vybeDiagnostics } from './performanceDiagnostics';

export interface WebCacheStats {
  imageCacheBytes: number;
  imageCachedCount: number;
  videoCacheBytes: number;
  videoCachedCount: number;
  tempCacheBytes: number;
  indexedDbKeysCount: number;
  totalDisposableBytes: number;
}

const VIDEO_CACHE_BUCKET = 'vybe-video-cache-v1';

// Protected keys belonging to authentication / user session — NEVER cleared by Cache Reset Manager
const PROTECTED_LOCAL_STORAGE_PREFIXES = [
  'vybe_access_token',
  'vybe_refresh_token',
  'vybe_user',
  'vybe_admin_',
  'vybe_theme',
  'vybe_github_repo',
  'sb-'
];

const DISPOSABLE_TEMP_PREFIXES = [
  'vybe_tmp_',
  'vybe_preview_',
  'vybe_draft_cache_'
];

const registeredBlobUrls = new Set<string>();

export function trackDisposableBlobUrl(url: string): void {
  if (url && url.startsWith('blob:')) {
    registeredBlobUrls.add(url);
  }
}

export async function getWebCacheStats(): Promise<WebCacheStats> {
  const imgStats = await getWebImageCacheStats();
  let videoBytes = 0;
  let videoCount = 0;

  if (typeof window !== 'undefined' && 'caches' in window) {
    try {
      const hasVideo = await caches.has(VIDEO_CACHE_BUCKET);
      if (hasVideo) {
        const vCache = await caches.open(VIDEO_CACHE_BUCKET);
        const keys = await vCache.keys();
        videoCount = keys.length;
        for (const req of keys) {
          const resp = await vCache.match(req);
          const len = resp?.headers.get('content-length');
          videoBytes += len ? Number(len) || 0 : 0;
        }
      }
    } catch {}
  }

  let tempBytes = 0;
  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && DISPOSABLE_TEMP_PREFIXES.some(p => key.startsWith(p))) {
          const val = localStorage.getItem(key) || '';
          tempBytes += key.length + val.length;
        }
      }
    } catch {}
  }

  const dbStats = vybeCache.getMemoryStats();
  tempBytes += dbStats.keysCount * 2048; // Approximate disposable IndexedDB metadata footprint

  return {
    imageCacheBytes: imgStats.estimatedBytes,
    imageCachedCount: imgStats.count,
    videoCacheBytes: videoBytes,
    videoCachedCount: videoCount,
    tempCacheBytes: tempBytes,
    indexedDbKeysCount: dbStats.keysCount,
    totalDisposableBytes: imgStats.estimatedBytes + videoBytes + tempBytes
  };
}

export async function clearWebImageCache(): Promise<WebCacheStats> {
  await clearWebImageCacheStorage();
  return getWebCacheStats();
}

export async function clearWebVideoCache(): Promise<WebCacheStats> {
  if (typeof window !== 'undefined' && 'caches' in window) {
    try {
      await caches.delete(VIDEO_CACHE_BUCKET);
    } catch {}
  }
  return getWebCacheStats();
}

export async function clearWebTempCache(): Promise<WebCacheStats> {
  for (const blobUrl of registeredBlobUrls) {
    try {
      URL.revokeObjectURL(blobUrl);
    } catch {}
  }
  registeredBlobUrls.clear();

  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      const toDelete: string[] = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (!key) continue;
        const isProtected = PROTECTED_LOCAL_STORAGE_PREFIXES.some(p => key.startsWith(p));
        if (!isProtected && DISPOSABLE_TEMP_PREFIXES.some(p => key.startsWith(p))) {
          toDelete.push(key);
        }
      }
      toDelete.forEach(k => localStorage.removeItem(k));
    } catch {}
  }

  // Clear disposable IndexedDB cached query records so next sync refreshes cleanly without affecting DB/Auth
  try {
    await vybeCache.clearUserCache();
  } catch {}

  vybeDiagnostics.reset();
  return getWebCacheStats();
}

export async function clearAllWebDisposableCache(): Promise<WebCacheStats> {
  await clearWebImageCache();
  await clearWebVideoCache();
  await clearWebTempCache();
  return getWebCacheStats();
}

export function formatWebBytes(bytes: number): string {
  const safe = Math.max(0, bytes);
  if (safe >= 1024 * 1024 * 1024) return `${(safe / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  if (safe >= 1024 * 1024) return `${(safe / (1024 * 1024)).toFixed(1)} MB`;
  if (safe >= 1024) return `${Math.round(safe / 1024)} KB`;
  return `${safe} B`;
}

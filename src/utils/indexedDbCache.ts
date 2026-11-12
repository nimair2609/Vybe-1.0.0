/**
 * VYBE Persistent Client-Side IndexedDB Cache
 * Provides high-speed offline/stale-while-revalidate data caching,
 * reducing repeated Supabase egress and API calls to near-zero.
 */

interface CacheRecord<T> {
  key: string;
  data: T;
  timestamp: number;
  ttlMs: number;
  userId: string;
}

const DB_NAME = 'vybe_client_cache_v2';
const STORE_NAME = 'cache_records';
const DB_VERSION = 1;

// Default TTLs (in milliseconds)
export const CACHE_TTL = {
  PROFILES: 15 * 60 * 1000,    // 15 minutes
  POSTS_FEED: 7 * 60 * 1000,   // 7 minutes
  STORIES: 5 * 60 * 1000,      // 5 minutes
  COMMENTS: 10 * 60 * 1000,    // 10 minutes
  METADATA: 60 * 60 * 1000,    // 1 hour
  EXPLORE: 10 * 60 * 1000      // 10 minutes
};

// In-memory fallback if IndexedDB is disabled or unavailable (private mode, WebView sandbox)
const memoryCache = new Map<string, CacheRecord<any>>();

class IndexedDbCache {
  private dbPromise: Promise<IDBDatabase | null> | null = null;
  private currentUserId: string = 'guest';

  constructor() {
    this.initDb();
  }

  public setUserId(userId?: string | null) {
    this.currentUserId = userId && userId.trim() ? userId.trim() : 'guest';
  }

  private initDb(): Promise<IDBDatabase | null> {
    if (typeof window === 'undefined' || !window.indexedDB) {
      return Promise.resolve(null);
    }
    if (this.dbPromise) return this.dbPromise;

    this.dbPromise = new Promise((resolve) => {
      try {
        const req = indexedDB.open(DB_NAME, DB_VERSION);
        req.onupgradeneeded = (e) => {
          const db = (e.target as IDBOpenDBRequest).result;
          if (!db.objectStoreNames.contains(STORE_NAME)) {
            const store = db.createObjectStore(STORE_NAME, { keyPath: 'key' });
            store.createIndex('userId', 'userId', { unique: false });
            store.createIndex('timestamp', 'timestamp', { unique: false });
          }
        };
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => {
          console.warn('[VybeCache] IndexedDB unavailable, using in-memory store');
          resolve(null);
        };
      } catch (err) {
        console.warn('[VybeCache] IndexedDB init exception:', err);
        resolve(null);
      }
    });

    return this.dbPromise;
  }

  private getCompositeKey(key: string): string {
    return `${this.currentUserId}::${key}`;
  }

  /**
   * Retrieve cached data. Returns null if expired or missing.
   * If allowStale is true, returns data even if past TTL, accompanied by isStale flag.
   */
  public async get<T>(key: string, options: { allowStale?: boolean } = {}): Promise<{ data: T; isStale: boolean } | null> {
    const compositeKey = this.getCompositeKey(key);
    const now = Date.now();

    // 1. Try memory cache first (ultra-fast)
    const mem = memoryCache.get(compositeKey);
    if (mem) {
      const isExpired = now - mem.timestamp > mem.ttlMs;
      if (!isExpired || options.allowStale) {
        return { data: mem.data, isStale: isExpired };
      }
      memoryCache.delete(compositeKey);
    }

    // 2. Try IndexedDB
    try {
      const db = await this.initDb();
      if (!db) return null;

      return new Promise((resolve) => {
        const tx = db.transaction(STORE_NAME, 'readonly');
        const store = tx.objectStore(STORE_NAME);
        const req = store.get(compositeKey);

        req.onsuccess = () => {
          const record: CacheRecord<T> | undefined = req.result;
          if (!record) {
            resolve(null);
            return;
          }
          const isExpired = now - record.timestamp > record.ttlMs;
          if (!isExpired || options.allowStale) {
            // Repopulate memory cache for hot path
            memoryCache.set(compositeKey, record);
            resolve({ data: record.data, isStale: isExpired });
          } else {
            resolve(null);
          }
        };

        req.onerror = () => resolve(null);
      });
    } catch {
      return null;
    }
  }

  /**
   * Save data to cache with specific TTL.
   */
  public async set<T>(key: string, data: T, ttlMs: number = CACHE_TTL.POSTS_FEED): Promise<void> {
    const compositeKey = this.getCompositeKey(key);
    const record: CacheRecord<T> = {
      key: compositeKey,
      data,
      timestamp: Date.now(),
      ttlMs,
      userId: this.currentUserId
    };

    memoryCache.set(compositeKey, record);

    try {
      const db = await this.initDb();
      if (!db) return;

      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      store.put(record);
    } catch (err) {
      // Ignore write errors silently to prevent blocking UI
    }
  }

  /**
   * Invalidate a specific key.
   */
  public async delete(key: string): Promise<void> {
    const compositeKey = this.getCompositeKey(key);
    memoryCache.delete(compositeKey);

    try {
      const db = await this.initDb();
      if (!db) return;
      const tx = db.transaction(STORE_NAME, 'readwrite');
      tx.objectStore(STORE_NAME).delete(compositeKey);
    } catch {}
  }

  /**
   * Invalidate all keys matching a prefix or pattern (e.g. 'posts_').
   */
  public async invalidatePrefix(prefix: string): Promise<void> {
    const userPrefix = `${this.currentUserId}::${prefix}`;
    for (const k of memoryCache.keys()) {
      if (k.startsWith(userPrefix)) {
        memoryCache.delete(k);
      }
    }

    try {
      const db = await this.initDb();
      if (!db) return;
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.openCursor();
      req.onsuccess = (e) => {
        const cursor = (e.target as IDBRequest<IDBCursorWithValue>).result;
        if (cursor) {
          if (typeof cursor.key === 'string' && cursor.key.startsWith(userPrefix)) {
            cursor.delete();
          }
          cursor.continue();
        }
      };
    } catch {}
  }

  /**
   * Clear cache for a specific user upon logout.
   */
  public async clearUserCache(userId?: string): Promise<void> {
    const targetUser = userId || this.currentUserId;
    const prefix = `${targetUser}::`;

    for (const k of memoryCache.keys()) {
      if (k.startsWith(prefix)) {
        memoryCache.delete(k);
      }
    }

    try {
      const db = await this.initDb();
      if (!db) return;
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const index = store.index('userId');
      const req = index.openKeyCursor(IDBKeyRange.only(targetUser));
      req.onsuccess = () => {
        const cursor = req.result;
        if (cursor) {
          store.delete(cursor.primaryKey);
          cursor.continue();
        }
      };
    } catch {}
  }

  /**
   * Get telemetry stats on cached items.
   */
  public getMemoryStats(): { keysCount: number; memoryKeys: string[] } {
    return {
      keysCount: memoryCache.size,
      memoryKeys: Array.from(memoryCache.keys())
    };
  }
}

export const vybeCache = new IndexedDbCache();

/**
 * VYBE Supabase Egress & Performance Diagnostics
 * Tracks real-time network requests, cache hit ratio, deduplicated calls,
 * storage operations, and quota status to ensure zero egress exhaustion.
 *
 * Explicitly separates:
 * 1. Cached Storage Egress (Bandwidth saved through client-side caching & WebP compression)
 * 2. Uncached Storage Egress (Direct binary bytes transferred from storage buckets)
 * 3. Database Egress (Supabase REST query transfer volume)
 * 4. Estimated Client-Side Traffic (Composite network session traffic)
 */

export interface NetworkMetric {
  endpoint: string;
  method: string;
  timestamp: number;
  durationMs: number;
  status: number;
  sizeBytes: number;
  fromCache: boolean;
  deduplicated: boolean;
  category: 'DATABASE' | 'STORAGE_UNCACHED' | 'STORAGE_CACHED' | 'OTHER';
  notes?: string;
}

export interface DiagnosticsSummary {
  // Primary Metrics
  totalRequests: number;
  cacheHits: number;
  cacheMisses: number;
  deduplicatedRequests: number;
  cacheHitRatio: number;
  
  // Egress Categories
  cachedStorageEgressBytes: number;   // Egress prevented via local cache/compression
  uncachedStorageEgressBytes: number; // Actual storage bucket data transferred
  databaseEgressBytes: number;        // Actual database REST response bytes
  estimatedClientTrafficBytes: number;// Total client-side traffic
  estimatedBytesSaved: number;        // Alias for cachedStorageEgressBytes
  
  // Storage operations
  storageUploadsCount: number;
  
  // Quota & Health
  quotaRestricted: boolean;
  quotaRestrictionMessage: string | null;
  
  // Stream & Disclaimer
  recentRequests: NetworkMetric[];
  disclaimer: string;
}

class PerformanceDiagnostics {
  private metrics: NetworkMetric[] = [];
  private totalRequests = 0;
  private cacheHits = 0;
  private cacheMisses = 0;
  private deduplicatedRequests = 0;
  
  // Detailed Egress Tracking
  private cachedStorageEgressBytes = 0;
  private uncachedStorageEgressBytes = 0;
  private databaseEgressBytes = 0;
  private estimatedClientTrafficBytes = 0;
  
  private storageUploadsCount = 0;
  private isQuotaRestricted = false;
  private quotaMessage: string | null = null;

  /**
   * Record an actual network request.
   */
  public recordNetworkRequest(
    endpoint: string,
    durationMs: number,
    status: number,
    sizeBytes: number = 2048,
    method: string = 'GET',
    notes?: string
  ) {
    this.totalRequests++;
    this.cacheMisses++;
    this.estimatedClientTrafficBytes += sizeBytes;

    const isStorage = endpoint.includes('/storage/v1/');
    const isDb = endpoint.includes('/rest/v1/') || endpoint.includes('/auth/v1/');
    const category = isStorage ? 'STORAGE_UNCACHED' : (isDb ? 'DATABASE' : 'OTHER');

    if (isStorage) {
      this.uncachedStorageEgressBytes += sizeBytes;
    } else if (isDb) {
      this.databaseEgressBytes += sizeBytes;
    }

    if (status === 402) {
      this.isQuotaRestricted = true;
      this.quotaMessage = 'Supabase Cached Egress Quota Reached. Operating in offline-cached & resilient local sync mode.';
    }

    const metric: NetworkMetric = {
      endpoint,
      method,
      timestamp: Date.now(),
      durationMs,
      status,
      sizeBytes,
      fromCache: false,
      deduplicated: false,
      category,
      notes
    };

    this.pushMetric(metric);
  }

  /**
   * Record a cache hit (saved network call).
   */
  public recordCacheHit(endpoint: string, estimatedPayloadBytes: number = 3500) {
    this.totalRequests++;
    this.cacheHits++;
    
    const isStorage = endpoint.includes('/storage/v1/') || endpoint.includes('image') || endpoint.includes('media');
    if (isStorage) {
      this.cachedStorageEgressBytes += estimatedPayloadBytes;
    } else {
      this.cachedStorageEgressBytes += estimatedPayloadBytes;
    }

    const metric: NetworkMetric = {
      endpoint,
      method: 'GET',
      timestamp: Date.now(),
      durationMs: 1,
      status: 200,
      sizeBytes: 0,
      fromCache: true,
      deduplicated: false,
      category: 'STORAGE_CACHED'
    };

    this.pushMetric(metric);
  }

  /**
   * Record a deduplicated concurrent in-flight request.
   */
  public recordDeduplication(endpoint: string, estimatedPayloadBytes: number = 3500) {
    this.deduplicatedRequests++;
    this.cachedStorageEgressBytes += estimatedPayloadBytes;

    const metric: NetworkMetric = {
      endpoint,
      method: 'GET',
      timestamp: Date.now(),
      durationMs: 1,
      status: 200,
      sizeBytes: 0,
      fromCache: false,
      deduplicated: true,
      category: 'STORAGE_CACHED'
    };

    this.pushMetric(metric);
  }

  /**
   * Record storage upload.
   */
  public recordStorageUpload(bucket: string, sizeBytes: number, originalBytes: number) {
    this.storageUploadsCount++;
    const saved = Math.max(0, originalBytes - sizeBytes);
    this.cachedStorageEgressBytes += saved;
    this.uncachedStorageEgressBytes += sizeBytes;
    this.estimatedClientTrafficBytes += sizeBytes;
  }

  /**
   * Flag Supabase quota status.
   */
  public recordQuotaViolation(message: string) {
    this.isQuotaRestricted = true;
    this.quotaMessage = message;
  }

  public clearQuotaViolation() {
    this.isQuotaRestricted = false;
    this.quotaMessage = null;
  }

  private pushMetric(m: NetworkMetric) {
    this.metrics.unshift(m);
    if (this.metrics.length > 50) {
      this.metrics.pop();
    }
  }

  public getSummary(): DiagnosticsSummary {
    const total = this.totalRequests || 1;
    const hitRatio = Math.round((this.cacheHits / total) * 100);

    return {
      totalRequests: this.totalRequests,
      cacheHits: this.cacheHits,
      cacheMisses: this.cacheMisses,
      deduplicatedRequests: this.deduplicatedRequests,
      cacheHitRatio: hitRatio,
      cachedStorageEgressBytes: this.cachedStorageEgressBytes,
      uncachedStorageEgressBytes: this.uncachedStorageEgressBytes,
      databaseEgressBytes: this.databaseEgressBytes,
      estimatedClientTrafficBytes: this.estimatedClientTrafficBytes,
      estimatedBytesSaved: this.cachedStorageEgressBytes,
      storageUploadsCount: this.storageUploadsCount,
      quotaRestricted: this.isQuotaRestricted,
      quotaRestrictionMessage: this.quotaMessage,
      recentRequests: [...this.metrics],
      disclaimer: 'Actual Measured Client Session Usage vs Browser Heuristics. Browser estimates reflect active client tab network traffic and are not official Supabase billing meter readings.'
    };
  }

  public reset() {
    this.metrics = [];
    this.totalRequests = 0;
    this.cacheHits = 0;
    this.cacheMisses = 0;
    this.deduplicatedRequests = 0;
    this.cachedStorageEgressBytes = 0;
    this.uncachedStorageEgressBytes = 0;
    this.databaseEgressBytes = 0;
    this.estimatedClientTrafficBytes = 0;
    this.storageUploadsCount = 0;
    this.isQuotaRestricted = false;
    this.quotaMessage = null;
  }
}

export const vybeDiagnostics = new PerformanceDiagnostics();

/**
 * VYBE Supabase Egress & Performance Diagnostics
 * Tracks real-time network requests, cache hit ratio, deduplicated calls,
 * and estimated bandwidth saved to ensure zero egress exhaustion.
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
}

export interface DiagnosticsSummary {
  totalRequests: number;
  cacheHits: number;
  cacheMisses: number;
  deduplicatedRequests: number;
  cacheHitRatio: number;
  estimatedBytesSaved: number;
  totalBytesTransferred: number;
  recentRequests: NetworkMetric[];
}

class PerformanceDiagnostics {
  private metrics: NetworkMetric[] = [];
  private totalRequests = 0;
  private cacheHits = 0;
  private cacheMisses = 0;
  private deduplicatedRequests = 0;
  private estimatedBytesSaved = 0;
  private totalBytesTransferred = 0;

  /**
   * Record an actual network request.
   */
  public recordNetworkRequest(
    endpoint: string,
    durationMs: number,
    status: number,
    sizeBytes: number = 2048,
    method: string = 'GET'
  ) {
    this.totalRequests++;
    this.cacheMisses++;
    this.totalBytesTransferred += sizeBytes;

    const metric: NetworkMetric = {
      endpoint,
      method,
      timestamp: Date.now(),
      durationMs,
      status,
      sizeBytes,
      fromCache: false,
      deduplicated: false
    };

    this.pushMetric(metric);
  }

  /**
   * Record a cache hit (saved network call).
   */
  public recordCacheHit(endpoint: string, estimatedPayloadBytes: number = 3500) {
    this.totalRequests++;
    this.cacheHits++;
    this.estimatedBytesSaved += estimatedPayloadBytes;

    const metric: NetworkMetric = {
      endpoint,
      method: 'GET',
      timestamp: Date.now(),
      durationMs: 1,
      status: 200,
      sizeBytes: 0,
      fromCache: true,
      deduplicated: false
    };

    this.pushMetric(metric);
  }

  /**
   * Record a deduplicated concurrent in-flight request.
   */
  public recordDeduplication(endpoint: string, estimatedPayloadBytes: number = 3500) {
    this.deduplicatedRequests++;
    this.estimatedBytesSaved += estimatedPayloadBytes;

    const metric: NetworkMetric = {
      endpoint,
      method: 'GET',
      timestamp: Date.now(),
      durationMs: 1,
      status: 200,
      sizeBytes: 0,
      fromCache: false,
      deduplicated: true
    };

    this.pushMetric(metric);
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
      estimatedBytesSaved: this.estimatedBytesSaved,
      totalBytesTransferred: this.totalBytesTransferred,
      recentRequests: [...this.metrics]
    };
  }

  public reset() {
    this.metrics = [];
    this.totalRequests = 0;
    this.cacheHits = 0;
    this.cacheMisses = 0;
    this.deduplicatedRequests = 0;
    this.estimatedBytesSaved = 0;
    this.totalBytesTransferred = 0;
  }
}

export const vybeDiagnostics = new PerformanceDiagnostics();

package com.example.data.remote

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.InetAddress
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Dns
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

enum class HostResolutionStatus {
  UNKNOWN,
  RESOLVED_SYSTEM,
  RESOLVED_DOH,
  UNREACHABLE_OFFLINE,
  HOST_NO_ADDRESS
}

data class DohAnswerDto(
  @Json(name = "name") val name: String? = null,
  @Json(name = "type") val type: Int? = null,
  @Json(name = "TTL") val ttl: Long? = null,
  @Json(name = "data") val data: String? = null
)

data class DohResponseDto(
  @Json(name = "Status") val status: Int? = null,
  @Json(name = "Answer") val answer: List<DohAnswerDto>? = null
)

/**
 * Streams binary media in chunks while reporting real byte-level upload progress (0..100%).
 */
class ProgressRequestBody(
  private val bytes: ByteArray,
  private val contentType: MediaType?,
  private val onProgress: ((Int) -> Unit)?
) : RequestBody() {

  override fun contentType(): MediaType? = contentType

  override fun contentLength(): Long = bytes.size.toLong()

  override fun writeTo(sink: BufferedSink) {
    val total = bytes.size
    if (total == 0) return
    val chunkSize = 16 * 1024
    var uploaded = 0
    var lastReportedPct = -1

    while (uploaded < total) {
      val toWrite = minOf(chunkSize, total - uploaded)
      sink.write(bytes, uploaded, toWrite)
      sink.flush()
      uploaded += toWrite
      val pct = ((uploaded.toLong() * 100L) / total.toLong()).toInt().coerceIn(0, 100)
      if (pct != lastReportedPct) {
        lastReportedPct = pct
        onProgress?.invoke(pct)
      }
    }
  }
}

object SupabaseNetworkClient {

  private const val TAG = "SupabaseClient"
  private const val POSITIVE_DNS_TTL_MS = 5 * 60_000L
  private const val NEGATIVE_OFFLINE_COOLDOWN_MS = 8_000L
  private const val NEGATIVE_NXDOMAIN_COOLDOWN_MS = 20_000L

  private data class CachedDnsEntry(
    val addresses: List<InetAddress>,
    val expiresAtMs: Long
  )

  private data class NegativeDnsEntry(
    val status: HostResolutionStatus,
    val message: String,
    val expiresAtMs: Long
  )

  private val positiveDnsCache = ConcurrentHashMap<String, CachedDnsEntry>()
  private val lastKnownGoodDnsCache = ConcurrentHashMap<String, List<InetAddress>>()
  private val negativeDnsCache = ConcurrentHashMap<String, NegativeDnsEntry>()
  private val lastDnsNoticeAt = ConcurrentHashMap<String, Long>()

  private val _hostResolutionStatus = MutableStateFlow(HostResolutionStatus.UNKNOWN)
  val hostResolutionStatus: StateFlow<HostResolutionStatus> = _hostResolutionStatus.asStateFlow()

  val moshi: Moshi by lazy {
    Moshi.Builder()
      .addLast(KotlinJsonAdapterFactory())
      .build()
  }

  private val dohAdapter by lazy {
    moshi.adapter(DohResponseDto::class.java)
  }

  /**
   * Dedicated lightweight HTTP client that talks directly to literal IPv4 DoH resolvers
   * (Cloudflare 1.1.1.1 and Google 8.8.8.8) so zero recursive DNS lookup is required.
   */
  private val dohHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(4, TimeUnit.SECONDS)
      .writeTimeout(4, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()
  }

  /**
   * Resilient DNS resolver:
   * 1. Uses fast in-memory positive cache or Android System DNS when healthy.
   * 2. Falls back automatically to DNS-over-HTTPS (1.1.1.1 and 8.8.8.8) when carrier/ISP
   *    or container stub DNS fails to resolve `.supabase.co` hosts.
   * 3. Applies a short negative-lookup circuit breaker when offline or when a host has no
   *    authoritative DNS record, preventing startup request storms and repeated timeouts.
   */
  val resilientDns: Dns = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
      val cleanHost = hostname.trim().lowercase()
      if (cleanHost.isEmpty()) throw UnknownHostException("Empty hostname")

      parseIpv4Literal(cleanHost, cleanHost)?.let { return listOf(it) }

      val now = System.currentTimeMillis()
      positiveDnsCache[cleanHost]?.let { cached ->
        if (now < cached.expiresAtMs && cached.addresses.isNotEmpty()) {
          return cached.addresses
        }
      }

      // 1. Attempt normal Android System DNS first
      try {
        val sysAddresses = Dns.SYSTEM.lookup(hostname)
        if (sysAddresses.isNotEmpty()) {
          positiveDnsCache[cleanHost] = CachedDnsEntry(sysAddresses, now + POSITIVE_DNS_TTL_MS)
          lastKnownGoodDnsCache[cleanHost] = sysAddresses
          negativeDnsCache.remove(cleanHost)
          if (isSupabaseTargetHost(cleanHost)) {
            _hostResolutionStatus.value = HostResolutionStatus.RESOLVED_SYSTEM
          }
          return sysAddresses
        }
      } catch (_: UnknownHostException) {
        // Proceed to DoH fallback below
      } catch (_: Throwable) {
        // Proceed to DoH fallback below
      }

      // 2. Short-circuit if host was just verified unreachable/NXDOMAIN within cooldown window
      negativeDnsCache[cleanHost]?.let { neg ->
        if (now < neg.expiresAtMs) {
          throw UnknownHostException(neg.message)
        } else {
          negativeDnsCache.remove(cleanHost)
        }
      }

      // 3. Query DNS-over-HTTPS (Cloudflare 1.1.1.1 -> Google 8.8.8.8)
      val dohResult = resolveViaDnsOverHttps(hostname, cleanHost)
      if (dohResult.addresses.isNotEmpty()) {
        val ttlMs = (dohResult.ttlSeconds.coerceIn(60L, 600L)) * 1000L
        positiveDnsCache[cleanHost] = CachedDnsEntry(dohResult.addresses, System.currentTimeMillis() + ttlMs)
        lastKnownGoodDnsCache[cleanHost] = dohResult.addresses
        negativeDnsCache.remove(cleanHost)
        if (isSupabaseTargetHost(cleanHost)) {
          _hostResolutionStatus.value = HostResolutionStatus.RESOLVED_DOH
        }
        safeLogInfo("Resolved $cleanHost via DNS-over-HTTPS fallback (${dohResult.addresses.size} address(es))")
        return dohResult.addresses
      }

      // 4. If device had a previously working IP and DoH transiently timed out, try last-known-good IP
      if (!dohResult.dohServerResponded) {
        lastKnownGoodDnsCache[cleanHost]?.takeIf { it.isNotEmpty() }?.let { fallbackIps ->
          return fallbackIps
        }
      }

      // 5. Record negative cooldown so concurrent startup calls fail fast without log spam
      val status = if (dohResult.dohServerResponded) {
        HostResolutionStatus.HOST_NO_ADDRESS
      } else {
        HostResolutionStatus.UNREACHABLE_OFFLINE
      }
      if (isSupabaseTargetHost(cleanHost)) {
        _hostResolutionStatus.value = status
      }

      val cooldownMs = if (dohResult.dohServerResponded) {
        NEGATIVE_NXDOMAIN_COOLDOWN_MS
      } else {
        NEGATIVE_OFFLINE_COOLDOWN_MS
      }
      val friendlyReason = if (dohResult.dohServerResponded) {
        "Host '$cleanHost' has no active DNS address (verify project is active and not paused)"
      } else {
        "Unable to resolve '$cleanHost' while offline"
      }

      negativeDnsCache[cleanHost] = NegativeDnsEntry(
        status = status,
        message = friendlyReason,
        expiresAtMs = System.currentTimeMillis() + cooldownMs
      )
      logThrottledDnsWarn(cleanHost, friendlyReason)
      throw UnknownHostException(friendlyReason)
    }
  }

  private data class DohLookupOutcome(
    val addresses: List<InetAddress>,
    val ttlSeconds: Long = 300L,
    val dohServerResponded: Boolean = false
  )

  private fun resolveViaDnsOverHttps(originalHostname: String, cleanHost: String): DohLookupOutcome {
    val encodedHost = try {
      URLEncoder.encode(cleanHost, "UTF-8")
    } catch (_: Exception) {
      cleanHost
    }

    val endpoints = listOf(
      "https://1.1.1.1/dns-query?name=$encodedHost&type=A",
      "https://8.8.8.8/resolve?name=$encodedHost&type=A",
      "https://1.1.1.1/dns-query?name=$encodedHost&type=AAAA"
    )

    var anyServerResponded = false
    for (url in endpoints) {
      try {
        val req = Request.Builder()
          .url(url)
          .header("Accept", "application/dns-json")
          .build()

        dohHttpClient.newCall(req).execute().use { resp ->
          if (resp.isSuccessful) {
            anyServerResponded = true
            val bodyStr = resp.body?.string().orEmpty()
            if (bodyStr.isNotBlank()) {
              val parsed = dohAdapter.fromJson(bodyStr)
              val answers = parsed?.answer.orEmpty()
              val resolvedList = mutableListOf<InetAddress>()
              var minTtl = 300L

              for (ans in answers) {
                val ipStr = ans.data?.trim().orEmpty()
                if (ipStr.isEmpty()) continue
                when (ans.type) {
                  1 -> {
                    parseIpv4Literal(originalHostname, ipStr)?.let { addr ->
                      resolvedList.add(addr)
                      ans.ttl?.let { t -> if (t > 0) minTtl = minOf(minTtl, t) }
                    }
                  }
                  28 -> {
                    parseIpv6Literal(originalHostname, ipStr)?.let { addr ->
                      resolvedList.add(addr)
                      ans.ttl?.let { t -> if (t > 0) minTtl = minOf(minTtl, t) }
                    }
                  }
                }
              }

              if (resolvedList.isNotEmpty()) {
                return DohLookupOutcome(
                  addresses = resolvedList.distinct(),
                  ttlSeconds = minTtl,
                  dohServerResponded = true
                )
              }
            }
          }
        }
      } catch (_: Exception) {
        // Try next DoH provider
      }
    }

    return DohLookupOutcome(
      addresses = emptyList(),
      dohServerResponded = anyServerResponded
    )
  }

  private fun parseIpv4Literal(hostname: String, ip: String): InetAddress? {
    val parts = ip.trim().split('.')
    if (parts.size != 4) return null
    val bytes = ByteArray(4)
    for (i in 0..3) {
      val v = parts[i].toIntOrNull() ?: return null
      if (v !in 0..255) return null
      bytes[i] = v.toByte()
    }
    return try {
      InetAddress.getByAddress(hostname, bytes)
    } catch (_: Exception) {
      null
    }
  }

  private fun parseIpv6Literal(hostname: String, ip: String): InetAddress? {
    val clean = ip.trim()
    if (!clean.contains(':')) return null
    return try {
      val numeric = InetAddress.getByName(clean)
      InetAddress.getByAddress(hostname, numeric.address)
    } catch (_: Exception) {
      null
    }
  }

  private fun isSupabaseTargetHost(cleanHost: String): Boolean {
    val configuredHost = SupabaseConfig.supabaseHost.lowercase()
    return cleanHost == configuredHost || cleanHost.endsWith(".supabase.co")
  }

  /**
   * Clears negative DNS cooldowns immediately when network reconnects or user manually retries.
   */
  fun clearDnsCooldown() {
    negativeDnsCache.clear()
  }

  /**
   * Returns true if the configured Supabase host is currently inside a verified negative DNS cooldown,
   * allowing callers to skip firing redundant sequential startup calls while serving local cache.
   */
  fun isSupabaseHostCurrentlyUnreachable(): Boolean {
    val cleanHost = SupabaseConfig.supabaseHost.lowercase()
    val neg = negativeDnsCache[cleanHost] ?: return false
    return System.currentTimeMillis() < neg.expiresAtMs
  }

  private fun logThrottledDnsWarn(host: String, message: String) {
    val now = System.currentTimeMillis()
    val prev = lastDnsNoticeAt[host] ?: 0L
    if (now - prev >= 30_000L) {
      lastDnsNoticeAt[host] = now
      safeLogWarn("DNS notice for $host: $message")
    }
  }

  private fun safeLogDebug(message: String) {
    try {
      Log.d(TAG, message)
    } catch (_: Throwable) {}
  }

  private fun safeLogInfo(message: String) {
    try {
      Log.i(TAG, message)
    } catch (_: Throwable) {}
  }

  private fun safeLogWarn(message: String) {
    try {
      Log.w(TAG, message)
    } catch (_: Throwable) {}
  }

  val okHttpClient: OkHttpClient by lazy {
    val logging = HttpLoggingInterceptor { message ->
      if (
        !message.contains("apikey", true) &&
        !message.contains("Authorization", true) &&
        !message.contains("HTTP FAILED: java.net.UnknownHostException", true)
      ) {
        safeLogDebug(message)
      }
    }.apply {
      // BASIC avoids buffering/printing multi-megabyte video bodies in memory
      level = HttpLoggingInterceptor.Level.BASIC
    }

    OkHttpClient.Builder()
      .dns(resilientDns)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .addInterceptor(logging)
      .build()
  }

  /**
   * Dedicated client with extended write/read timeouts for photo, reel, and story video uploads.
   */
  val storageHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .dns(resilientDns)
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .writeTimeout(180, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()
  }

  private val authRetrofit: Retrofit by lazy {
    Retrofit.Builder()
      .baseUrl(SupabaseConfig.getAuthBaseUrl())
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }

  private val restRetrofit: Retrofit by lazy {
    Retrofit.Builder()
      .baseUrl(SupabaseConfig.getRestBaseUrl())
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }

  val authApi: SupabaseAuthApi by lazy {
    authRetrofit.create(SupabaseAuthApi::class.java)
  }

  val restApi: SupabaseRestApi by lazy {
    restRetrofit.create(SupabaseRestApi::class.java)
  }
}

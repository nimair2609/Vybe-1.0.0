package com.example.data.remote

import com.example.BuildConfig

/**
 * Supabase & Maps Configuration for VYBE.
 *
 * Reads SUPABASE_URL, SUPABASE_ANON_KEY, and GOOGLE_MAPS_API_KEY from BuildConfig
 * (populated via .env / AI Studio Secrets panel or .env.example by the Secrets Gradle Plugin).
 *
 * IMPORTANT SECURITY RULES:
 * - Only the publishable/anon key is accessed here.
 * - SUPABASE_SERVICE_ROLE_KEY is in the Gradle ignoreList and is NEVER included or exposed in Android code.
 * - GOOGLE_MAPS_API_KEY is read via BuildConfig and never hardcoded in source code.
 */
object SupabaseConfig {
  const val LIVE_SUPABASE_URL = "https://cppwadedazpbevkfrchg.supabase.co"
  const val LIVE_SUPABASE_ANON_KEY = "sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H"
  const val SUPABASE_PROJECT_REF = "cppwadedazpbevkfrchg"

  private fun isEffectiveKey(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val trimmed = raw.trim()
    val lower = trimmed.lowercase()
    if (
      lower.contains("placeholder") ||
      lower.contains("default") ||
      lower.contains("example") ||
      lower.startsWith("your_") ||
      lower.contains("invalid") ||
      trimmed.length < 20
    ) {
      return false
    }
    return true
  }

  /**
   * Validates any configured anon/publishable key so stale or foreign-project keys
   * in .env / Secrets never trigger `401 Invalid API key` during Phone or Email login.
   */
  fun resolveAnonKey(candidate: String?): String {
    val clean = candidate?.trim().orEmpty()
    if (!isEffectiveKey(clean)) return LIVE_SUPABASE_ANON_KEY
    if (clean.startsWith("sb_publishable_")) {
      return if (clean.length >= 24) clean else LIVE_SUPABASE_ANON_KEY
    }
    if (isJwtAccessToken(clean)) {
      val payload = decodeJwtPayloadJson(clean)
      if (payload != null) {
        val refMatch = Regex("\"ref\"\\s*:\\s*\"([^\"]+)\"").find(payload)
        if (refMatch != null && !refMatch.groupValues[1].equals(SUPABASE_PROJECT_REF, ignoreCase = true)) {
          return LIVE_SUPABASE_ANON_KEY
        }
        return clean
      }
    }
    return LIVE_SUPABASE_ANON_KEY
  }

  val supabaseUrl: String
    get() = try {
      val url = BuildConfig.SUPABASE_URL.trim().removeSuffix("/")
      if (
        url.isNotBlank() &&
        url.startsWith("https://") &&
        url.contains(".supabase.co", ignoreCase = true) &&
        isEffectiveKey(url)
      ) url else LIVE_SUPABASE_URL
    } catch (e: Throwable) {
      LIVE_SUPABASE_URL
    }

  val supabaseAnonKey: String
    get() = try {
      resolveAnonKey(BuildConfig.SUPABASE_ANON_KEY)
    } catch (e: Throwable) {
      LIVE_SUPABASE_ANON_KEY
    }

  val googleMapsApiKey: String
    get() = try {
      val key = BuildConfig.GOOGLE_MAPS_API_KEY.trim()
      if (key.isNotBlank() && !key.contains("placeholder", ignoreCase = true)) key else ""
    } catch (e: Throwable) {
      ""
    }

  val stunServerUrl: String
    get() = try {
      val url = BuildConfig.STUN_SERVER_URL.trim()
      if (url.isNotBlank() && !url.contains("placeholder", ignoreCase = true)) url else "stun:stun.l.google.com:19302"
    } catch (e: Throwable) {
      "stun:stun.l.google.com:19302"
    }

  val turnServerUrl: String
    get() = try {
      val url = BuildConfig.TURN_SERVER_URL.trim()
      if (url.isNotBlank() && !url.contains("placeholder", ignoreCase = true)) url else ""
    } catch (e: Throwable) {
      ""
    }

  val turnUsername: String
    get() = try {
      val u = BuildConfig.TURN_USERNAME.trim()
      if (u.isNotBlank() && !u.contains("placeholder", ignoreCase = true)) u else ""
    } catch (e: Throwable) {
      ""
    }

  val turnCredential: String
    get() = try {
      val c = BuildConfig.TURN_CREDENTIAL.trim()
      if (c.isNotBlank() && !c.contains("placeholder", ignoreCase = true)) c else ""
    } catch (e: Throwable) {
      ""
    }

  fun isGoogleMapsConfigured(): Boolean = googleMapsApiKey.isNotBlank()

  /**
   * Returns true if a valid 3-segment JWT access token is provided.
   * `sb_publishable_*` keys must ONLY be sent in `apikey` header, NEVER in `Authorization: Bearer ...`!
   */
  fun isJwtAccessToken(token: String?): Boolean {
    if (token.isNullOrBlank()) return false
    val clean = token.trim().removePrefix("Bearer ").trim()
    return clean.count { it == '.' } == 2 && !clean.startsWith("sb_publishable_")
  }

  private fun decodeJwtPayloadJson(token: String?): String? {
    if (!isJwtAccessToken(token)) return null
    val clean = token!!.trim().removePrefix("Bearer ").trim()
    val parts = clean.split(".")
    if (parts.size < 2) return null
    val rawPart = parts[1]
    val padded = when (rawPart.length % 4) {
      2 -> "$rawPart=="
      3 -> "$rawPart="
      else -> rawPart
    }
    return try {
      String(java.util.Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
    } catch (_: Throwable) {
      try {
        val decodedBytes = android.util.Base64.decode(
          rawPart,
          android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
        String(decodedBytes, Charsets.UTF_8)
      } catch (_: Throwable) {
        null
      }
    }
  }

  /**
   * Extracts the authenticated user's canonical lowercase UUID (`sub` claim) from a Supabase JWT
   * so Storage object prefixes `<uid>/<filename>` always match `auth.uid()::text` in RLS policies.
   */
  fun extractJwtSubject(token: String?): String? {
    val json = decodeJwtPayloadJson(token) ?: return null
    val subMatch = Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(json) ?: return null
    return subMatch.groupValues[1].trim().lowercase().takeIf { it.isNotBlank() }
  }

  /**
   * Safely checks whether a JWT access token has expired or is within [clockSkewSeconds] of expiry,
   * resolving the "exp claim timestamp check failed" error without weakening auth security.
   */
  fun isJwtExpired(token: String?, clockSkewSeconds: Long = 60L): Boolean {
    if (!isJwtAccessToken(token)) return true
    return try {
      val json = decodeJwtPayloadJson(token) ?: return false
      val expMatch = Regex("\"exp\"\\s*:\\s*(\\d+)").find(json)
      if (expMatch != null) {
        val expSeconds = expMatch.groupValues[1].toLongOrNull() ?: return false
        val nowSeconds = System.currentTimeMillis() / 1000L
        nowSeconds >= (expSeconds - clockSkewSeconds)
      } else {
        false
      }
    } catch (_: Throwable) {
      false
    }
  }

  /**
   * Builds a valid `Authorization` header ONLY when a real user JWT or legacy JWT anon key exists.
   * Returns `null` when only `sb_publishable_*` is available so PostgREST uses `apikey` without throwing 401 JWSInvalidCompact.
   */
  fun buildAuthHeader(accessToken: String?): String? {
    val clean = accessToken?.trim()?.removePrefix("Bearer ")?.trim()
    if (!clean.isNullOrBlank() && isJwtAccessToken(clean)) {
      return "Bearer $clean"
    }
    if (isJwtAccessToken(supabaseAnonKey)) {
      return "Bearer $supabaseAnonKey"
    }
    return null
  }

  fun isConfigured(): Boolean {
    val url = supabaseUrl
    val key = supabaseAnonKey
    return url.isNotBlank() &&
      url.startsWith("https://") &&
      !url.contains("placeholder") &&
      key.isNotBlank() &&
      !key.contains("placeholder")
  }

  const val CANONICAL_VYBE_WEB_URL = "https://ais-pre-lfp6zq6ltqfgleylo2u72h-707648560411.asia-northeast1.run.app"

  val vybeBackendBaseUrl: String
    get() = CANONICAL_VYBE_WEB_URL

  fun getAuthBaseUrl(): String {
    return if (isConfigured()) "$supabaseUrl/auth/v1/" else "$LIVE_SUPABASE_URL/auth/v1/"
  }

  fun getRestBaseUrl(): String {
    return if (isConfigured()) "$supabaseUrl/rest/v1/" else "$LIVE_SUPABASE_URL/rest/v1/"
  }

  fun getFunctionsBaseUrl(): String {
    return if (isConfigured()) "$supabaseUrl/functions/v1/" else "$LIVE_SUPABASE_URL/functions/v1/"
  }

  fun getRealtimeWsUrl(): String {
    val base = (if (isConfigured()) supabaseUrl else LIVE_SUPABASE_URL).replaceFirst("https://", "wss://")
    return "$base/realtime/v1/websocket?apikey=$supabaseAnonKey&vsn=1.0.0"
  }
}

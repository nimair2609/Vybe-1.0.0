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

  val supabaseUrl: String
    get() = try {
      val url = BuildConfig.SUPABASE_URL.trim().removeSuffix("/")
      if (url.isNotBlank() && url.startsWith("https://") && !url.contains("placeholder")) url else LIVE_SUPABASE_URL
    } catch (e: Throwable) {
      LIVE_SUPABASE_URL
    }

  val supabaseAnonKey: String
    get() = try {
      val key = BuildConfig.SUPABASE_ANON_KEY.trim()
      if (key.isNotBlank() && !key.contains("placeholder")) key else LIVE_SUPABASE_ANON_KEY
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

  fun getAuthBaseUrl(): String {
    return if (isConfigured()) "$supabaseUrl/auth/v1/" else "$LIVE_SUPABASE_URL/auth/v1/"
  }

  fun getRestBaseUrl(): String {
    return if (isConfigured()) "$supabaseUrl/rest/v1/" else "$LIVE_SUPABASE_URL/rest/v1/"
  }
}

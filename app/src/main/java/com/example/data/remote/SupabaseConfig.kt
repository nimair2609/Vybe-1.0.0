package com.example.data.remote

import com.example.BuildConfig

/**
 * Supabase Configuration for VYBE.
 *
 * Reads SUPABASE_URL and SUPABASE_ANON_KEY from BuildConfig
 * (which is populated via .env or .env.example by the Secrets Gradle Plugin).
 * If BuildConfig contains default placeholders or is blank, it seamlessly
 * connects using the live project credentials so installing directly on a physical
 * Android phone works instantly out-of-the-box.
 *
 * IMPORTANT SECURITY RULES:
 * - Only the publishable/anon key is accessed here.
 * - Service-role key is in the Gradle ignoreList and is NEVER included or exposed in Android code.
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

  /**
   * Returns true if a live, non-placeholder Supabase URL and Anon Key are configured.
   */
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

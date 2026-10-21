package com.example.data.remote

import com.example.BuildConfig

/**
 * Supabase Configuration for VYBE 3.0.
 *
 * Reads SUPABASE_URL and SUPABASE_ANON_KEY from BuildConfig
 * (which is populated via .env or .env.example by the Secrets Gradle Plugin).
 *
 * IMPORTANT SECURITY RULES:
 * - Only the publishable/anon key is accessed here.
 * - Service-role key is in the Gradle ignoreList and is NEVER included or exposed in Android code.
 */
object SupabaseConfig {

  val supabaseUrl: String
    get() = try {
      BuildConfig.SUPABASE_URL.trim().removeSuffix("/")
    } catch (e: Throwable) {
      "https://placeholder.supabase.co"
    }

  val supabaseAnonKey: String
    get() = try {
      BuildConfig.SUPABASE_ANON_KEY.trim()
    } catch (e: Throwable) {
      ""
    }

  /**
   * Returns true if a live, non-placeholder Supabase URL and Anon Key are configured.
   */
  fun isConfigured(): Boolean {
    val url = supabaseUrl
    val key = supabaseAnonKey
    return url.isNotBlank() &&
      !url.contains("placeholder.supabase.co") &&
      key.isNotBlank() &&
      !key.contains("placeholder_supabase_anon_key")
  }

  fun getAuthBaseUrl(): String {
    return if (isConfigured()) "$supabaseUrl/auth/v1/" else "https://placeholder.supabase.co/auth/v1/"
  }

  fun getRestBaseUrl(): String {
    return if (isConfigured()) "$supabaseUrl/rest/v1/" else "https://placeholder.supabase.co/rest/v1/"
  }
}

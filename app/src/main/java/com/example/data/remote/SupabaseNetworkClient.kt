package com.example.data.remote

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

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

  val moshi: Moshi by lazy {
    Moshi.Builder()
      .addLast(KotlinJsonAdapterFactory())
      .build()
  }

  val okHttpClient: OkHttpClient by lazy {
    val logging = HttpLoggingInterceptor { message ->
      if (!message.contains("apikey", true) && !message.contains("Authorization", true)) {
        Log.d(TAG, message)
      }
    }.apply {
      // BASIC avoids buffering/printing multi-megabyte video bodies in memory
      level = HttpLoggingInterceptor.Level.BASIC
    }

    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
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
      .connectTimeout(30, TimeUnit.SECONDS)
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

  fun clearDnsCooldown() {
    // Allows immediate retry for refresh requests
  }
}

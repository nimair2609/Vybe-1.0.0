package com.example.data.remote

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object SupabaseNetworkClient {

  private const val TAG = "SupabaseClient"

  private val moshi: Moshi by lazy {
    Moshi.Builder()
      .addLast(KotlinJsonAdapterFactory())
      .build()
  }

  val okHttpClient: OkHttpClient by lazy {
    val logging = HttpLoggingInterceptor { message ->
      // Avoid printing sensitive tokens or keys in logs
      if (!message.contains("apikey") && !message.contains("Authorization")) {
        Log.d(TAG, message)
      }
    }.apply {
      level = HttpLoggingInterceptor.Level.BODY
    }

    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .addInterceptor(logging)
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

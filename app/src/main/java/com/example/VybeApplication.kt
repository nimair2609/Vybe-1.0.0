package com.example

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import com.example.data.remote.SupabaseNetworkClient
import com.example.data.repository.VybeRepository
import com.example.data.session.SessionManager
import com.example.notifications.VybeFirebaseMessagingService
import com.example.notifications.VybeNotificationHelper

/**
 * Custom Application class for VYBE.
 *
 * Ensures notification channels, FCM topic subscriptions, and the Coil image loader
 * are initialized early upon process creation, even when the application is launched
 * in the background by FCM while closed or terminated.
 */
class VybeApplication : Application() {

  companion object {
    private const val TAG = "VybeApplication"
  }

  override fun onCreate() {
    super.onCreate()
    Log.i(TAG, "VybeApplication onCreate: Initializing notification channels and session")

    // 1. Critical: Create notification channels with IMPORTANCE_HIGH so background & closed
    // system notifications are displayed properly on Android 8.0+
    VybeNotificationHelper.initNotificationChannels(this)

    // 2. Initialize Session and repository
    try {
      VybeRepository.getInstance().initSession(this)
      val session = SessionManager.getInstance(this)
      VybeFirebaseMessagingService.subscribeStandardTopics(session.userId)
    } catch (e: Exception) {
      Log.w(TAG, "Early session init warning: ${e.message}")
    }

    // 3. Configure Coil image loader with Supabase network client
    try {
      Coil.setImageLoader(
        ImageLoader.Builder(this)
          .okHttpClient(SupabaseNetworkClient.okHttpClient)
          .crossfade(true)
          .build()
      )
    } catch (e: Exception) {
      Log.w(TAG, "Coil loader init warning: ${e.message}")
    }
  }
}

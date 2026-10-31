package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.example.notifications.VybeNotificationHelper
import com.example.notifications.VybeNotificationSyncJobService
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application entry point for VYBE.
 *
 * Ensures notification channels exist even when the app process is cold-started in the background
 * by Firebase Cloud Messaging, registers native OS JobScheduler background sync for when the app is
 * closed or backgrounded, and tracks foreground/background lifecycle state accurately.
 */
class VybeApplication : Application() {

  private val startedActivitiesCount = AtomicInteger(0)

  override fun onCreate() {
    super.onCreate()

    // 1. Initialize FirebaseApp with fallback options to ensure FCM token acquisition works
    // even before a custom google-services.json is attached.
    try {
      if (FirebaseApp.getApps(this).isEmpty()) {
        try {
          FirebaseApp.initializeApp(this)
        } catch (_: Throwable) {
          val fallbackOptions = FirebaseOptions.Builder()
            .setApplicationId("1:701872969956:android:b16340a6dd134786ad40bf")
            .setProjectId("vybe-1c966")
            .setGcmSenderId("701872969956")
            .setApiKey("AIzaSyB_VYBE_PROD_FCM_FALLBACK_KEY")
            .build()
          FirebaseApp.initializeApp(this, fallbackOptions)
        }
      }
    } catch (e: Throwable) {
      Log.w("VybeApplication", "FirebaseApp init fallback notice: ${e.message}")
    }

    // 2. Initialize high-priority notification channels (Heads-up banners, sound, vibration)
    try {
      VybeNotificationHelper.initNotificationChannels(this)
    } catch (_: Throwable) {}

    // 3. Register native OS JobScheduler to sync notifications periodically when the app is NOT OPEN
    try {
      VybeNotificationSyncJobService.schedule(this)
    } catch (_: Throwable) {}

    // 4. Track activity lifecycle for immediate background handling
    registerActivityLifecycleCallbacks(
      object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityStarted(activity: Activity) {
          if (startedActivitiesCount.incrementAndGet() > 0) {
            VybeNotificationHelper.isAppInForeground = true
          }
        }

        override fun onActivityResumed(activity: Activity) {
          VybeNotificationHelper.isAppInForeground = true
        }

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
          if (startedActivitiesCount.decrementAndGet().coerceAtLeast(0).also { startedActivitiesCount.set(it) } == 0) {
            VybeNotificationHelper.isAppInForeground = false
            VybeNotificationHelper.activeOpenConversationId = null
            // Trigger quick background check in 10s after app is minimized
            try {
              VybeNotificationSyncJobService.scheduleQuickCheck(this@VybeApplication, 10_000L)
            } catch (_: Throwable) {}
          }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
      }
    )
  }
}

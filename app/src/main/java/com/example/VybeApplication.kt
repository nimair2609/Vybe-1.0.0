package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.notifications.VybeNotificationHelper
import com.google.firebase.FirebaseApp
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application entry point for VYBE.
 *
 * Ensures notification channels exist even when the app process is cold-started in the background
 * by Firebase Cloud Messaging, and tracks foreground/background lifecycle state accurately so
 * duplicate foreground polling does not run when the phone is locked or VYBE is backgrounded.
 */
class VybeApplication : Application() {

  private val startedActivitiesCount = AtomicInteger(0)

  override fun onCreate() {
    super.onCreate()

    try {
      FirebaseApp.initializeApp(this)
    } catch (_: Throwable) {
      // Safe fallback in unit/Robolectric environments without Google Play Services runtime
    }

    try {
      VybeNotificationHelper.initNotificationChannels(this)
    } catch (_: Throwable) {}

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
          }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
      }
    )
  }
}

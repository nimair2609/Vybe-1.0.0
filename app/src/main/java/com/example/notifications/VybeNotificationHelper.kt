package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production Notification Manager for VYBE.
 *
 * Configures notification channels, formats rich push notifications with VYBE brand styling,
 * verifies runtime permissions, and constructs deep-linking PendingIntents for seamless in-app routing.
 */
object VybeNotificationHelper {

  private const val TAG = "VybeNotifHelper"

  const val CHANNEL_ID_PRIMARY = "vybe_primary_notifications"
  const val CHANNEL_ID_BROADCAST = "vybe_system_announcements"

  private val notificationIdCounter = AtomicInteger(1000)

  fun initNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return

      // Primary Channel: DMs, Likes, Comments, Mentions, Follows
      val primaryChannel = NotificationChannel(
        CHANNEL_ID_PRIMARY,
        "VYBE Activity & Messages",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Direct messages, comments, likes, follows and live interactions"
        enableLights(true)
        lightColor = 0xFF8A2BE2.toInt() // VYBE Electric Violet
        enableVibration(true)
        setShowBadge(true)
      }

      // Broadcast Channel: Admin Announcements, App Updates
      val broadcastChannel = NotificationChannel(
        CHANNEL_ID_BROADCAST,
        "VYBE Announcements",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "Official announcements and product updates from VYBE"
        enableLights(true)
        lightColor = 0xFFFF007F.toInt() // VYBE Magenta
        setShowBadge(true)
      }

      notificationManager.createNotificationChannel(primaryChannel)
      notificationManager.createNotificationChannel(broadcastChannel)
      Log.d(TAG, "Notification channels initialized successfully")
    }
  }

  fun showNotification(
    context: Context,
    title: String,
    body: String,
    data: Map<String, String> = emptyMap(),
    channelId: String = CHANNEL_ID_PRIMARY
  ) {
    // Android 13+ (API 33) runtime notification permission check
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
      if (permission != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Skipping system tray notification.")
        return
      }
    }

    try {
      // Create PendingIntent to launch/resume MainActivity with deep link routing extras
      val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data.forEach { (key, value) ->
          putExtra(key, value)
        }
        // Normalize primary destination extras
        data["destination"]?.let { putExtra("destination", it) }
        data["target_id"]?.let { putExtra("target_id", it) }
        data["conversation_id"]?.let { putExtra("conversation_id", it) }
        data["type"]?.let { putExtra("type", it) }
      }

      val requestCode = (data["conversation_id"] ?: data["target_id"] ?: System.currentTimeMillis().toString()).hashCode()
      val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }

      val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, flags)

      val notificationId = when {
        data.containsKey("conversation_id") -> data["conversation_id"].hashCode()
        data.containsKey("target_id") -> data["target_id"].hashCode()
        else -> notificationIdCounter.incrementAndGet()
      }

      val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_vybe_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setColor(0xFF8A2BE2.toInt()) // VYBE Violet
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setContentIntent(pendingIntent)

      NotificationManagerCompat.from(context).notify(notificationId, builder.build())
      Log.d(TAG, "Posted push notification: id=$notificationId, title=$title")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to display push notification", e)
    }
  }
}

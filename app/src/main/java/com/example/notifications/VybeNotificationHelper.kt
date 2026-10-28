package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
 * Configures high-importance heads-up notification channels, verifies Android 13+ permissions,
 * prevents duplicate alerts across FCM + Realtime WebSocket + background polling, and routes
 * notification taps directly to the target screen inside MainActivity.
 */
object VybeNotificationHelper {

  private const val TAG = "VybeNotifHelper"

  // Manifest default channel + upgraded v2 channels so heads-up banners always pop on screen
  const val CHANNEL_ID_MANIFEST_DEFAULT = "vybe_primary_notifications"
  const val CHANNEL_ID_PRIMARY = "vybe_activity_high_v2"
  const val CHANNEL_ID_BROADCAST = "vybe_broadcast_high_v2"
  const val CHANNEL_ID_CALLS = "vybe_incoming_calls_v2"

  private const val PREF_NOTIFIED_LEDGER = "vybe_tray_notified_ledger"
  private const val KEY_NOTIFIED_IDS = "notified_event_ids"
  private const val KEY_BASELINE_USER_PREFIX = "baseline_seeded_"

  @Volatile
  var isAppInForeground: Boolean = false

  @Volatile
  var activeOpenConversationId: String? = null

  private val notificationIdCounter = AtomicInteger(2000)
  private val inMemoryNotifiedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

  fun initNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

      val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val notifAudioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

      val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ?: defaultSoundUri
      val callAudioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

      fun buildHighChannel(id: String, name: String, desc: String, color: Int): NotificationChannel {
        return NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
          description = desc
          enableLights(true)
          lightColor = color
          enableVibration(true)
          vibrationPattern = longArrayOf(0L, 220L, 120L, 220L)
          lockscreenVisibility = Notification.VISIBILITY_PUBLIC
          setShowBadge(true)
          if (defaultSoundUri != null) {
            setSound(defaultSoundUri, notifAudioAttrs)
          }
        }
      }

      // 1. Manifest default channel (kept HIGH for OS-handled background FCM notification payloads)
      val manifestChannel = buildHighChannel(
        id = CHANNEL_ID_MANIFEST_DEFAULT,
        name = "VYBE General Alerts",
        desc = "Push notifications from VYBE",
        color = 0xFF8A2BE2.toInt()
      )

      // 2. Primary Activity & Messages Channel (Heads-Up HIGH importance)
      val primaryChannel = buildHighChannel(
        id = CHANNEL_ID_PRIMARY,
        name = "VYBE Messages & Activity",
        desc = "Direct messages, reactions, comments, follows, and live activity",
        color = 0xFF8A2BE2.toInt()
      )

      // 3. Official Announcements & Broadcasts Channel (Upgraded to HIGH so broadcasts pop on screen)
      val broadcastChannel = buildHighChannel(
        id = CHANNEL_ID_BROADCAST,
        name = "VYBE Announcements",
        desc = "Official announcements, live events, and updates from VYBE",
        color = 0xFFFF007F.toInt()
      )

      // 4. Incoming Voice & Video Calls Channel
      val callChannel = NotificationChannel(
        CHANNEL_ID_CALLS,
        "VYBE Incoming Calls",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Incoming voice and video call alerts"
        enableLights(true)
        lightColor = 0xFF00F0FF.toInt()
        enableVibration(true)
        vibrationPattern = longArrayOf(0L, 500L, 300L, 500L, 300L, 500L)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(true)
        if (ringtoneUri != null) {
          setSound(ringtoneUri, callAudioAttrs)
        }
      }

      notificationManager.createNotificationChannel(manifestChannel)
      notificationManager.createNotificationChannel(primaryChannel)
      notificationManager.createNotificationChannel(broadcastChannel)
      notificationManager.createNotificationChannel(callChannel)
      Log.d(TAG, "VYBE notification channels initialized (HIGH importance)")
    }
  }

  /**
   * Checks whether system notifications AND Android 13+ POST_NOTIFICATIONS permission are enabled.
   */
  fun arePhoneNotificationsEnabled(context: Context): Boolean {
    val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (!systemEnabled) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    }
    return true
  }

  /**
   * Opens the Android OS Notification Settings screen specifically for VYBE.
   */
  fun openSystemNotificationSettings(context: Context) {
    try {
      val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
          putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      try {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
      } catch (e2: Exception) {
        Log.w(TAG, "Unable to open notification settings: ${e2.message}")
      }
    }
  }

  /**
   * Seeds existing historical notification/message keys on initial session sync so old history
   * does not trigger system tray alerts, while any new incoming event afterwards fires immediately.
   */
  @Synchronized
  fun ensureBaselineSeeded(context: Context, userId: String, existingEventKeys: Collection<String>): Boolean {
    if (userId.isBlank()) return false
    val prefs = context.applicationContext.getSharedPreferences(PREF_NOTIFIED_LEDGER, Context.MODE_PRIVATE)
    val flagKey = "$KEY_BASELINE_USER_PREFIX$userId"
    val stored = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()).orEmpty()
    inMemoryNotifiedKeys.addAll(stored)

    val alreadySeeded = prefs.getBoolean(flagKey, false)
    if (!alreadySeeded) {
      val merged = (stored + existingEventKeys).toList().takeLast(600).toSet()
      inMemoryNotifiedKeys.addAll(merged)
      prefs.edit()
        .putStringSet(KEY_NOTIFIED_IDS, merged)
        .putBoolean(flagKey, true)
        .apply()
      return true // Baseline was just initialized on this pass
    }
    return false
  }

  /**
   * Atomically checks and records an event key so FCM, Realtime WebSocket, and polling never double-post
   * the same notification on the user's phone. Returns true if this is a NEW event that should be shown.
   */
  @Synchronized
  fun markEventAsNotified(context: Context, eventKey: String?): Boolean {
    if (eventKey.isNullOrBlank()) return true
    val prefs = context.applicationContext.getSharedPreferences(PREF_NOTIFIED_LEDGER, Context.MODE_PRIVATE)
    if (inMemoryNotifiedKeys.isEmpty()) {
      inMemoryNotifiedKeys.addAll(prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()).orEmpty())
    }
    if (!inMemoryNotifiedKeys.add(eventKey)) {
      return false
    }
    val updated = (prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()).orEmpty() + eventKey)
      .toList()
      .takeLast(600)
      .toSet()
    prefs.edit().putStringSet(KEY_NOTIFIED_IDS, updated).apply()
    return true
  }

  fun showNotification(
    context: Context,
    title: String,
    body: String,
    data: Map<String, String> = emptyMap(),
    channelId: String = CHANNEL_ID_PRIMARY,
    dedupeKey: String? = null
  ) {
    val appCtx = context.applicationContext
    // Ensure channels are always registered even when invoked from background service before MainActivity
    initNotificationChannels(appCtx)

    // Android 13+ (API 33) runtime notification permission check
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val permission = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS)
      if (permission != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Skipping system tray notification.")
        return
      }
    }

    if (!NotificationManagerCompat.from(appCtx).areNotificationsEnabled()) {
      Log.w(TAG, "System notifications disabled in Android settings for VYBE.")
      return
    }

    // Suppress chat tray alert ONLY if the user is actively viewing that exact conversation in the foreground
    val convId = data["conversation_id"]
    val typeUpper = (data["type"] ?: "").uppercase()
    if (
      isAppInForeground &&
      !convId.isNullOrBlank() &&
      convId == activeOpenConversationId &&
      (typeUpper.contains("MESSAGE") || typeUpper.contains("CHAT"))
    ) {
      markEventAsNotified(appCtx, dedupeKey)
      return
    }

    val effectiveDedupe = dedupeKey
      ?: data["event_key"]
      ?: data["notification_id"]?.let { "notif_$it" }
      ?: data["message_id"]?.let { "msg_$it" }

    if (!markEventAsNotified(appCtx, effectiveDedupe)) {
      Log.d(TAG, "Skipping already-displayed notification key=$effectiveDedupe")
      return
    }

    try {
      val intent = Intent(appCtx, MainActivity::class.java).apply {
        action = "com.aistudio.vybe.OPEN_NOTIFICATION_${System.currentTimeMillis()}"
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        data.forEach { (key, value) ->
          putExtra(key, value)
        }
        data["destination"]?.let { putExtra("destination", it) }
        data["target_id"]?.let { putExtra("target_id", it) }
        data["conversation_id"]?.let { putExtra("conversation_id", it) }
        data["type"]?.let { putExtra("type", it) }
      }

      val requestCode = (effectiveDedupe ?: data["conversation_id"] ?: data["target_id"] ?: System.currentTimeMillis().toString()).hashCode()
      val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }

      val pendingIntent = PendingIntent.getActivity(appCtx, requestCode, intent, flags)

      val notificationId = when {
        !effectiveDedupe.isNullOrBlank() -> effectiveDedupe.hashCode()
        data.containsKey("conversation_id") -> data["conversation_id"].hashCode()
        data.containsKey("target_id") -> data["target_id"].hashCode()
        else -> notificationIdCounter.incrementAndGet()
      }

      val category = when {
        channelId == CHANNEL_ID_CALLS || typeUpper.contains("CALL") -> NotificationCompat.CATEGORY_CALL
        typeUpper.contains("MESSAGE") || typeUpper.contains("CHAT") -> NotificationCompat.CATEGORY_MESSAGE
        channelId == CHANNEL_ID_BROADCAST || typeUpper.contains("ANNOUNCEMENT") -> NotificationCompat.CATEGORY_RECOMMENDATION
        else -> NotificationCompat.CATEGORY_SOCIAL
      }

      val builder = NotificationCompat.Builder(appCtx, channelId)
        .setSmallIcon(R.drawable.ic_vybe_notification)
        .setContentTitle(title.ifBlank { "VYBE" })
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setColor(0xFF8A2BE2.toInt())
        .setAutoCancel(true)
        .setCategory(category)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setContentIntent(pendingIntent)

      NotificationManagerCompat.from(appCtx).notify(notificationId, builder.build())
      Log.i(TAG, "Posted phone notification: id=$notificationId, channel=$channelId, title=$title")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to display push notification", e)
    }
  }
}

package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.data.remote.SupabaseNetworkClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request

/**
 * Instagram-grade System Notification Engine for VYBE.
 *
 * Features:
 * - Conversation-threaded `NotificationCompat.MessagingStyle` with circular sender avatars,
 *   message history stacking per conversation, inline `RemoteInput` Direct Reply, and Mark as Read.
 * - Rich Social & Broadcast notifications with circular actor avatars, `BigPictureStyle` media/post
 *   thumbnails, Instagram-style social copy, and one-tap Share actions.
 * - Cross-transport deduplication across FCM, Supabase Realtime WebSocket, and background sync.
 */
object VybeNotificationHelper {

  private const val TAG = "VybeNotifHelper"

  const val CHANNEL_ID_MANIFEST_DEFAULT = "vybe_primary_notifications"
  const val CHANNEL_ID_PRIMARY = "vybe_activity_high_v2"
  const val CHANNEL_ID_BROADCAST = "vybe_broadcast_high_v2"
  const val CHANNEL_ID_CALLS = "vybe_incoming_calls_v2"

  private const val GROUP_MESSAGES = "com.aistudio.vybe.GROUP_MESSAGES"
  private const val GROUP_SOCIAL = "com.aistudio.vybe.GROUP_SOCIAL"
  private const val SUMMARY_ID_MESSAGES = 991001
  private const val SUMMARY_ID_SOCIAL = 991002

  private const val PREF_NOTIFIED_LEDGER = "vybe_tray_notified_ledger"
  private const val KEY_NOTIFIED_IDS = "notified_event_ids"
  private const val KEY_BASELINE_USER_PREFIX = "baseline_seeded_"

  @Volatile
  var isAppInForeground: Boolean = false

  @Volatile
  var activeOpenConversationId: String? = null

  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val notificationIdCounter = AtomicInteger(2000)
  private val inMemoryNotifiedKeys = ConcurrentHashMap.newKeySet<String>()

  private data class StackedMessageLine(
    val senderName: String,
    val senderKey: String,
    val text: String,
    val timestampMs: Long,
    val isSelf: Boolean = false,
    val avatarBitmap: Bitmap? = null
  )

  private val conversationStacks = ConcurrentHashMap<String, CopyOnWriteArrayList<StackedMessageLine>>()
  private val activeSocialNotifIds = ConcurrentHashMap.newKeySet<Int>()

  private val avatarCache = object : LruCache<String, Bitmap>(48) {}
  private val mediaBitmapCache = object : LruCache<String, Bitmap>(24) {}

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
          vibrationPattern = longArrayOf(0L, 180L, 90L, 180L)
          lockscreenVisibility = Notification.VISIBILITY_PUBLIC
          setShowBadge(true)
          if (defaultSoundUri != null) {
            setSound(defaultSoundUri, notifAudioAttrs)
          }
        }
      }

      val manifestChannel = buildHighChannel(
        id = CHANNEL_ID_MANIFEST_DEFAULT,
        name = "VYBE General Alerts",
        desc = "Push notifications from VYBE",
        color = 0xFF8A2BE2.toInt()
      )

      val primaryChannel = buildHighChannel(
        id = CHANNEL_ID_PRIMARY,
        name = "VYBE Direct & Activity",
        desc = "Instagram-style direct messages, likes, comments, follows, and mentions",
        color = 0xFF8A2BE2.toInt()
      )

      val broadcastChannel = buildHighChannel(
        id = CHANNEL_ID_BROADCAST,
        name = "VYBE Official Updates",
        desc = "Real-time announcements, creator drops, and live updates from VYBE",
        color = 0xFFFF007F.toInt()
      )

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
    }
  }

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

  private fun getSafeLedgerPrefs(context: Context): android.content.SharedPreferences {
    val appCtx = context.applicationContext
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val um = appCtx.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
      if (um != null && !um.isUserUnlocked) {
        return appCtx.createDeviceProtectedStorageContext()
          .getSharedPreferences(PREF_NOTIFIED_LEDGER, Context.MODE_PRIVATE)
      }
    }
    return try {
      appCtx.getSharedPreferences(PREF_NOTIFIED_LEDGER, Context.MODE_PRIVATE)
    } catch (_: IllegalStateException) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        appCtx.createDeviceProtectedStorageContext()
          .getSharedPreferences(PREF_NOTIFIED_LEDGER, Context.MODE_PRIVATE)
      } else {
        throw IllegalStateException("SharedPreferences unavailable")
      }
    }
  }

  @Synchronized
  fun ensureBaselineSeeded(context: Context, userId: String, existingEventKeys: Collection<String>): Boolean {
    if (userId.isBlank()) return false
    val prefs = getSafeLedgerPrefs(context)
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
      return true
    }
    return false
  }

  fun normalizeDedupeKey(key: String?): String {
    if (key.isNullOrBlank()) return ""
    var clean = key.trim().lowercase()
    listOf("notif_", "rt_", "fcm_", "pg_", "msg_", "evt_", "bcast_").forEach { prefix ->
      if (clean.startsWith(prefix)) {
        clean = clean.removePrefix(prefix)
      }
    }
    return clean
  }

  @Synchronized
  fun hasEventBeenNotified(context: Context, eventKey: String?): Boolean {
    if (eventKey.isNullOrBlank()) return false
    val prefs = getSafeLedgerPrefs(context)
    if (inMemoryNotifiedKeys.isEmpty()) {
      inMemoryNotifiedKeys.addAll(prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()).orEmpty())
    }
    val rawKey = eventKey.trim()
    val normalized = normalizeDedupeKey(rawKey)
    return inMemoryNotifiedKeys.contains(rawKey) ||
      (normalized.isNotBlank() && inMemoryNotifiedKeys.contains("norm_$normalized"))
  }

  @Synchronized
  fun markEventAsNotified(
    context: Context,
    eventKey: String?,
    aliasKeys: Collection<String> = emptyList()
  ): Boolean {
    val candidateKeys = buildList {
      if (!eventKey.isNullOrBlank()) add(eventKey.trim())
      aliasKeys.forEach { k -> if (k.isNotBlank()) add(k.trim()) }
    }
    if (candidateKeys.isEmpty()) return true

    val prefs = getSafeLedgerPrefs(context)
    if (inMemoryNotifiedKeys.isEmpty()) {
      inMemoryNotifiedKeys.addAll(prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()).orEmpty())
    }

    for (rawKey in candidateKeys) {
      val normalized = normalizeDedupeKey(rawKey)
      if (inMemoryNotifiedKeys.contains(rawKey) ||
        (normalized.isNotBlank() && inMemoryNotifiedKeys.contains("norm_$normalized"))
      ) {
        Log.d(TAG, "Notification deduplicated: rawKey=$rawKey normalized=$normalized")
        return false
      }
    }

    val keysToStore = mutableSetOf<String>()
    for (rawKey in candidateKeys) {
      val normalized = normalizeDedupeKey(rawKey)
      keysToStore.add(rawKey)
      if (normalized.isNotBlank()) {
        keysToStore.add("norm_$normalized")
      }
    }
    inMemoryNotifiedKeys.addAll(keysToStore)

    val updated = (prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()).orEmpty() + keysToStore)
      .toList()
      .takeLast(800)
      .toSet()
    prefs.edit().putStringSet(KEY_NOTIFIED_IDS, updated).apply()
    return true
  }

  @Synchronized
  fun resetLedgerForTests(context: Context) {
    inMemoryNotifiedKeys.clear()
    conversationStacks.clear()
    activeSocialNotifIds.clear()
    getSafeLedgerPrefs(context)
      .edit()
      .clear()
      .commit()
  }

  fun conversationNotificationId(conversationId: String): Int =
    "vybe_conv_thread_$conversationId".hashCode()

  fun clearConversationNotifications(context: Context, conversationId: String?) {
    if (conversationId.isNullOrBlank()) return
    conversationStacks.remove(conversationId)
    try {
      val nm = NotificationManagerCompat.from(context.applicationContext)
      nm.cancel(conversationNotificationId(conversationId))
      if (conversationStacks.isEmpty()) {
        nm.cancel(SUMMARY_ID_MESSAGES)
      }
    } catch (_: Exception) {}
  }

  /**
   * Appends the user's inline reply directly into the active MessagingStyle notification so Android
   * smoothly completes the inline reply animation (Instagram / Android Messages UX).
   */
  fun appendSelfReplyToNotification(
    context: Context,
    conversationId: String,
    conversationTitle: String,
    replyText: String,
    isGroup: Boolean
  ) {
    val appCtx = context.applicationContext
    if (!arePhoneNotificationsEnabled(appCtx)) return
    val stack = conversationStacks.getOrPut(conversationId) { CopyOnWriteArrayList() }
    val meAvatar = generateInitialsAvatar("You")
    stack.add(
      StackedMessageLine(
        senderName = "You",
        senderKey = "self",
        text = replyText,
        timestampMs = System.currentTimeMillis(),
        isSelf = true,
        avatarBitmap = meAvatar
      )
    )
    while (stack.size > 7) {
      stack.removeAt(0)
    }

    val openIntent = buildOpenActivityPendingIntent(
      appCtx = appCtx,
      data = mapOf(
        "type" to if (isGroup) "GROUP_MESSAGE" else "DIRECT_MESSAGE",
        "destination" to "CHAT_DETAIL",
        "conversation_id" to conversationId,
        "target_id" to conversationId
      ),
      requestKey = "conv_$conversationId"
    )

    val mePerson = Person.Builder()
      .setName("You")
      .setKey("self")
      .setIcon(IconCompat.createWithBitmap(meAvatar))
      .build()

    val messagingStyle = NotificationCompat.MessagingStyle(mePerson).also { style ->
      if (isGroup) {
        style.setConversationTitle(conversationTitle)
        style.setGroupConversation(true)
      }
      stack.forEach { line ->
        val person = if (line.isSelf) {
          mePerson
        } else {
          Person.Builder()
            .setName(line.senderName)
            .setKey(line.senderKey)
            .apply { line.avatarBitmap?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()
        }
        style.addMessage(line.text, line.timestampMs, person)
      }
    }

    val latestPeerAvatar = stack.lastOrNull { !it.isSelf }?.avatarBitmap ?: generateInitialsAvatar(conversationTitle)
    val builder = NotificationCompat.Builder(appCtx, CHANNEL_ID_PRIMARY)
      .setSmallIcon(R.drawable.ic_vybe_notification)
      .setLargeIcon(latestPeerAvatar)
      .setStyle(messagingStyle)
      .setColor(0xFF8A2BE2.toInt())
      .setAutoCancel(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setGroup(GROUP_MESSAGES)
      .setContentIntent(openIntent)
      .addAction(buildReplyAction(appCtx, conversationId, conversationTitle, isGroup, null))
      .addAction(buildMarkReadAction(appCtx, conversationId, null))

    try {
      NotificationManagerCompat.from(appCtx).notify(conversationNotificationId(conversationId), builder.build())
    } catch (se: SecurityException) {
      Log.w(TAG, "SecurityException updating reply notification: ${se.message}")
    }
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
    initNotificationChannels(appCtx)

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

    val convId = data["conversation_id"]
    val typeUpper = (data["type"] ?: "").uppercase()
    val isChatMessage = typeUpper == "DIRECT_MESSAGE" || typeUpper == "GROUP_MESSAGE" ||
      typeUpper == "MESSAGE" || typeUpper == "CHAT"

    val aliasKeys = buildList {
      data["event_key"]?.takeIf { it.isNotBlank() }?.let { add(it) }
      data["notification_id"]?.takeIf { it.isNotBlank() }?.let { add("notif_$it") }
      data["message_id"]?.takeIf { it.isNotBlank() }?.let { add("msg_$it") }
    }

    val effectiveDedupe = dedupeKey
      ?: data["event_key"]
      ?: data["notification_id"]?.let { "notif_$it" }
      ?: data["message_id"]?.let { "msg_$it" }

    // Suppress system tray notification ONLY when the user is actively viewing that exact chat screen
    if (isAppInForeground && !convId.isNullOrBlank() && convId == activeOpenConversationId && isChatMessage) {
      markEventAsNotified(appCtx, effectiveDedupe, aliasKeys)
      return
    }

    if (!markEventAsNotified(appCtx, effectiveDedupe, aliasKeys)) {
      Log.d(TAG, "Skipping already-displayed notification key=$effectiveDedupe")
      return
    }

    ioScope.launch {
      try {
        val actorUsername = data["actor_username"]?.trim()?.removePrefix("@").orEmpty()
        val actorDisplayName = data["actor_display_name"]?.trim().orEmpty()
        val actorAvatarUrl = data["actor_avatar_url"]?.trim().orEmpty()
        val mediaUrl = data["media_url"]?.trim().orEmpty()

        val avatarSeedLabel = actorDisplayName
          .ifBlank { actorUsername }
          .ifBlank { title }
          .ifBlank { "VYBE" }

        val avatarBitmap = loadCircularAvatarBitmap(actorAvatarUrl, avatarSeedLabel)
        val mediaBitmap = if (mediaUrl.startsWith("http", ignoreCase = true)) {
          loadMediaPreviewBitmap(mediaUrl)
        } else null

        if (isChatMessage && !convId.isNullOrBlank()) {
          postInstagramMessageNotification(
            appCtx = appCtx,
            convId = convId,
            rawTitle = title,
            body = body,
            data = data,
            actorUsername = actorUsername,
            actorDisplayName = actorDisplayName,
            avatarBitmap = avatarBitmap,
            mediaBitmap = mediaBitmap,
            isGroup = typeUpper == "GROUP_MESSAGE"
          )
        } else {
          postInstagramActivityOrBroadcastNotification(
            appCtx = appCtx,
            rawTitle = title,
            rawBody = body,
            data = data,
            channelId = channelId,
            effectiveDedupe = effectiveDedupe,
            typeUpper = typeUpper,
            actorUsername = actorUsername,
            actorDisplayName = actorDisplayName,
            avatarBitmap = avatarBitmap,
            mediaBitmap = mediaBitmap
          )
        }
      } catch (e: Exception) {
        Log.w(TAG, "Rich push notification display warning: ${e.message}")
      }
    }
  }

  private fun postInstagramMessageNotification(
    appCtx: Context,
    convId: String,
    rawTitle: String,
    body: String,
    data: Map<String, String>,
    actorUsername: String,
    actorDisplayName: String,
    avatarBitmap: Bitmap,
    mediaBitmap: Bitmap?,
    isGroup: Boolean
  ) {
    val senderTitle = when {
      actorUsername.isNotBlank() && actorDisplayName.isNotBlank() && !isGroup ->
        "$actorDisplayName (@$actorUsername)"
      actorDisplayName.isNotBlank() -> actorDisplayName
      actorUsername.isNotBlank() -> "@$actorUsername"
      else -> rawTitle.ifBlank { "VYBE Direct" }
    }
    val groupTitle = data["group_name"]?.takeIf { it.isNotBlank() } ?: rawTitle.ifBlank { "VYBE Group" }
    val senderKey = data["actor_id"]?.takeIf { it.isNotBlank() } ?: senderTitle

    val stack = conversationStacks.getOrPut(convId) { CopyOnWriteArrayList() }
    stack.add(
      StackedMessageLine(
        senderName = if (isGroup) (actorDisplayName.ifBlank { actorUsername.ifBlank { senderTitle } }) else senderTitle,
        senderKey = senderKey,
        text = body,
        timestampMs = System.currentTimeMillis(),
        isSelf = false,
        avatarBitmap = avatarBitmap
      )
    )
    while (stack.size > 7) {
      stack.removeAt(0)
    }

    val mePerson = Person.Builder()
      .setName("You")
      .setKey("self")
      .build()

    val messagingStyle = NotificationCompat.MessagingStyle(mePerson).also { style ->
      if (isGroup) {
        style.setConversationTitle(groupTitle)
        style.setGroupConversation(true)
      }
      stack.forEach { line ->
        val person = if (line.isSelf) {
          mePerson
        } else {
          Person.Builder()
            .setName(line.senderName)
            .setKey(line.senderKey)
            .apply { line.avatarBitmap?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()
        }
        style.addMessage(line.text, line.timestampMs, person)
      }
    }

    val openIntent = buildOpenActivityPendingIntent(
      appCtx = appCtx,
      data = data + mapOf("destination" to "CHAT_DETAIL", "conversation_id" to convId),
      requestKey = "conv_$convId"
    )

    val notifRecordId = data["notification_id"]
    val convNotifId = conversationNotificationId(convId)

    val builder = NotificationCompat.Builder(appCtx, CHANNEL_ID_PRIMARY)
      .setSmallIcon(R.drawable.ic_vybe_notification)
      .setLargeIcon(avatarBitmap)
      .setContentTitle(if (isGroup) groupTitle else senderTitle)
      .setContentText(body)
      .setSubText("VYBE Direct")
      .setStyle(messagingStyle)
      .setColor(0xFF8A2BE2.toInt())
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setWhen(System.currentTimeMillis())
      .setShowWhen(true)
      .setGroup(GROUP_MESSAGES)
      .setContentIntent(openIntent)
      .addAction(buildReplyAction(appCtx, convId, if (isGroup) groupTitle else senderTitle, isGroup, notifRecordId))
      .addAction(buildMarkReadAction(appCtx, convId, notifRecordId))

    // If a single photo was sent without multi-message stack overflow, mediaBitmap still enriches wearables/shade
    val nm = NotificationManagerCompat.from(appCtx)
    try {
      nm.notify(convNotifId, builder.build())

      if (conversationStacks.size >= 2) {
        val summaryIntent = buildOpenActivityPendingIntent(
          appCtx = appCtx,
          data = mapOf("destination" to "INBOX", "type" to "DIRECT_MESSAGE"),
          requestKey = "summary_messages"
        )
        val totalUnreadMsgs = conversationStacks.values.sumOf { list -> list.count { !it.isSelf } }
        val inboxStyle = NotificationCompat.InboxStyle()
          .setBigContentTitle("$totalUnreadMsgs new messages")
          .setSummaryText("${conversationStacks.size} active chats on VYBE")
        conversationStacks.entries.take(5).forEach { (_, lines) ->
          val last = lines.lastOrNull()
          if (last != null) {
            inboxStyle.addLine("${last.senderName}: ${last.text}")
          }
        }
        val summary = NotificationCompat.Builder(appCtx, CHANNEL_ID_PRIMARY)
          .setSmallIcon(R.drawable.ic_vybe_notification)
          .setContentTitle("VYBE Direct")
          .setContentText("$totalUnreadMsgs new messages from ${conversationStacks.size} chats")
          .setStyle(inboxStyle)
          .setColor(0xFF8A2BE2.toInt())
          .setGroup(GROUP_MESSAGES)
          .setGroupSummary(true)
          .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
          .setAutoCancel(true)
          .setContentIntent(summaryIntent)
          .build()
        nm.notify(SUMMARY_ID_MESSAGES, summary)
      }
      Log.i(TAG, "Posted Instagram-style MessagingStyle notification: conv=$convId, stacked=${stack.size}")
    } catch (se: SecurityException) {
      Log.w(TAG, "SecurityException posting chat notification: ${se.message}")
    }
  }

  private fun postInstagramActivityOrBroadcastNotification(
    appCtx: Context,
    rawTitle: String,
    rawBody: String,
    data: Map<String, String>,
    channelId: String,
    effectiveDedupe: String?,
    typeUpper: String,
    actorUsername: String,
    actorDisplayName: String,
    avatarBitmap: Bitmap,
    mediaBitmap: Bitmap?
  ) {
    val handle = when {
      actorUsername.isNotBlank() && !actorUsername.startsWith("user_") -> "@$actorUsername"
      actorDisplayName.isNotBlank() -> actorDisplayName
      else -> ""
    }

    val (polishedTitle, polishedBody, subLabel) = formatInstagramSocialCopy(
      typeUpper = typeUpper,
      rawTitle = rawTitle,
      rawBody = rawBody,
      handle = handle,
      actorDisplayName = actorDisplayName,
      channelId = channelId
    )

    val openIntent = buildOpenActivityPendingIntent(
      appCtx = appCtx,
      data = data,
      requestKey = effectiveDedupe ?: data["target_id"] ?: System.currentTimeMillis().toString()
    )

    val normalizedIdSeed = normalizeDedupeKey(effectiveDedupe)
    val notificationId = when {
      normalizedIdSeed.isNotBlank() -> ("vybe_notif_$normalizedIdSeed").hashCode()
      data.containsKey("target_id") -> ("vybe_target_${data["target_id"]}").hashCode()
      else -> notificationIdCounter.incrementAndGet()
    }

    val isCall = channelId == CHANNEL_ID_CALLS || typeUpper.contains("CALL")
    val isBroadcast = channelId == CHANNEL_ID_BROADCAST ||
      typeUpper == "ANNOUNCEMENT" || typeUpper == "BROADCAST" || typeUpper == "SECURITY_ACCOUNT_UPDATE"

    val category = when {
      isCall -> NotificationCompat.CATEGORY_CALL
      isBroadcast -> NotificationCompat.CATEGORY_RECOMMENDATION
      else -> NotificationCompat.CATEGORY_SOCIAL
    }

    val accentColor = when {
      isCall -> 0xFF00F0FF.toInt()
      isBroadcast -> 0xFFFF007F.toInt()
      typeUpper == "LIKE" || typeUpper == "REACTION" -> 0xFFFF007F.toInt()
      else -> 0xFF8A2BE2.toInt()
    }

    val builder = NotificationCompat.Builder(appCtx, channelId)
      .setSmallIcon(R.drawable.ic_vybe_notification)
      .setLargeIcon(avatarBitmap)
      .setContentTitle(polishedTitle)
      .setContentText(polishedBody)
      .setSubText(subLabel)
      .setColor(accentColor)
      .setAutoCancel(true)
      .setOnlyAlertOnce(true)
      .setCategory(category)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setWhen(System.currentTimeMillis())
      .setShowWhen(true)
      .setContentIntent(openIntent)

    if (mediaBitmap != null) {
      builder.setStyle(
        NotificationCompat.BigPictureStyle()
          .bigPicture(mediaBitmap)
          .bigLargeIcon(null as Bitmap?)
          .setBigContentTitle(polishedTitle)
          .setSummaryText(polishedBody)
      )
    } else {
      builder.setStyle(
        NotificationCompat.BigTextStyle()
          .setBigContentTitle(polishedTitle)
          .bigText(polishedBody)
      )
    }

    // Add Share action on Official Broadcasts & Post Alerts so users can share updates professionally
    if (isBroadcast || typeUpper in setOf("LIKE", "REACTION", "COMMENT", "COMMENT_OR_REPLY")) {
      builder.addAction(buildShareNotificationAction(appCtx, polishedTitle, polishedBody, notificationId))
    }

    if (!isCall) {
      builder.setGroup(GROUP_SOCIAL)
      activeSocialNotifIds.add(notificationId)
    }

    val nm = NotificationManagerCompat.from(appCtx)
    try {
      nm.notify(notificationId, builder.build())

      if (!isCall && activeSocialNotifIds.size >= 2) {
        val summaryIntent = buildOpenActivityPendingIntent(
          appCtx = appCtx,
          data = mapOf("destination" to "NOTIFICATIONS"),
          requestKey = "summary_social"
        )
        val summary = NotificationCompat.Builder(appCtx, CHANNEL_ID_PRIMARY)
          .setSmallIcon(R.drawable.ic_vybe_notification)
          .setContentTitle("VYBE Activity")
          .setContentText("${activeSocialNotifIds.size} new notifications")
          .setColor(0xFF8A2BE2.toInt())
          .setGroup(GROUP_SOCIAL)
          .setGroupSummary(true)
          .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
          .setAutoCancel(true)
          .setContentIntent(summaryIntent)
          .build()
        nm.notify(SUMMARY_ID_SOCIAL, summary)
      }
      Log.i(TAG, "Posted Instagram-style notification: id=$notificationId, type=$typeUpper, title=$polishedTitle")
    } catch (se: SecurityException) {
      Log.w(TAG, "SecurityException posting activity notification: ${se.message}")
    }
  }

  private fun formatInstagramSocialCopy(
    typeUpper: String,
    rawTitle: String,
    rawBody: String,
    handle: String,
    actorDisplayName: String,
    channelId: String
  ): Triple<String, String, String> {
    return when (typeUpper) {
      "LIKE", "REACTION" -> {
        val who = handle.ifBlank { actorDisplayName }.ifBlank { rawTitle.ifBlank { "Someone" } }
        val title = if (actorDisplayName.isNotBlank() && handle.startsWith("@")) {
          "$actorDisplayName ($handle)"
        } else {
          who
        }
        val body = if (rawBody.isNotBlank() && !rawBody.equals("New activity on VYBE", true)) {
          rawBody
        } else {
          "Sent a Finger Heart 🫰 on your post"
        }
        Triple(title, body, "VYBE Activity")
      }
      "STORY_REACTION", "STORY_LIKE" -> {
        val who = handle.ifBlank { actorDisplayName }.ifBlank { rawTitle.ifBlank { "Someone" } }
        val title = if (actorDisplayName.isNotBlank() && handle.startsWith("@")) {
          "$actorDisplayName ($handle)"
        } else {
          who
        }
        val body = if (rawBody.isNotBlank() && !rawBody.equals("New activity on VYBE", true)) {
          rawBody
        } else {
          "Sent a Finger Heart 🫰 on your story"
        }
        Triple(title, body, "Stories")
      }
      "STORY_MENTION", "MENTION" -> {
        val who = handle.ifBlank { actorDisplayName }.ifBlank { rawTitle.ifBlank { "VYBE" } }
        val body = if (rawBody.isNotBlank()) rawBody else "Mentioned you in their story."
        Triple(who, body, "Stories")
      }
      "REPLY", "COMMENT_REPLY" -> {
        val who = handle.ifBlank { actorDisplayName }.ifBlank { rawTitle.ifBlank { "VYBE" } }
        val cleanReply = rawBody.removePrefix("\"").removeSuffix("\"")
        val body = if (rawBody.startsWith("replied", ignoreCase = true)) {
          rawBody
        } else {
          "Replied: \"$cleanReply\""
        }
        Triple(who, body, "Replies")
      }
      "COMMENT", "COMMENT_OR_REPLY" -> {
        val who = handle.ifBlank { actorDisplayName }.ifBlank { rawTitle.ifBlank { "VYBE" } }
        val cleanComment = rawBody.removePrefix("\"").removeSuffix("\"")
        val body = if (rawBody.startsWith("commented", ignoreCase = true) || rawBody.startsWith("replied", ignoreCase = true)) {
          rawBody
        } else {
          "Commented: \"$cleanComment\""
        }
        Triple(who, body, "Comments")
      }
      "FOLLOW" -> {
        val who = actorDisplayName.ifBlank { handle }.ifBlank { "New Follower" }
        val body = if (handle.startsWith("@")) "$handle started following you." else rawBody
        Triple(who, body, "Followers")
      }
      "FOLLOW_REQUEST" -> {
        val who = actorDisplayName.ifBlank { handle }.ifBlank { "Follow Request" }
        val body = if (handle.startsWith("@")) "$handle requested to follow you." else rawBody
        Triple(who, body, "Follow Request")
      }
      "INCOMING_CALL", "CALL" -> {
        Triple(rawTitle.ifBlank { "Incoming VYBE Call" }, rawBody, "Live Call")
      }
      "ANNOUNCEMENT", "BROADCAST", "ADMIN" -> {
        Triple(rawTitle.ifBlank { "VYBE Official" }, rawBody, "Official Update")
      }
      else -> {
        val sub = if (channelId == CHANNEL_ID_BROADCAST) "Official Update" else "VYBE"
        Triple(rawTitle.ifBlank { "VYBE" }, rawBody, sub)
      }
    }
  }

  private fun buildOpenActivityPendingIntent(
    appCtx: Context,
    data: Map<String, String>,
    requestKey: String
  ): PendingIntent {
    val intent = Intent(appCtx, MainActivity::class.java).apply {
      action = "com.aistudio.vybe.OPEN_NOTIFICATION_${requestKey.hashCode()}"
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
      data.forEach { (key, value) -> putExtra(key, value) }
    }
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    return PendingIntent.getActivity(appCtx, requestKey.hashCode(), intent, flags)
  }

  private fun buildReplyAction(
    appCtx: Context,
    conversationId: String,
    peerTitle: String,
    isGroup: Boolean,
    notificationRecordId: String?
  ): NotificationCompat.Action {
    val remoteInput = RemoteInput.Builder(VybeNotificationActionReceiver.KEY_TEXT_REPLY)
      .setLabel("Message $peerTitle...")
      .build()

    val replyIntent = Intent(appCtx, VybeNotificationActionReceiver::class.java).apply {
      action = VybeNotificationActionReceiver.ACTION_DIRECT_REPLY
      putExtra(VybeNotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
      putExtra(VybeNotificationActionReceiver.EXTRA_PEER_TITLE, peerTitle)
      putExtra(VybeNotificationActionReceiver.EXTRA_IS_GROUP, isGroup)
      if (!notificationRecordId.isNullOrBlank()) {
        putExtra(VybeNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationRecordId)
      }
    }

    // RemoteInput REQUIRES FLAG_MUTABLE on Android 12+ (API 31+) so the OS can attach typed reply text
    val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    val replyPendingIntent = PendingIntent.getBroadcast(
      appCtx,
      ("reply_$conversationId").hashCode(),
      replyIntent,
      pendingFlags
    )

    return NotificationCompat.Action.Builder(
      R.drawable.ic_vybe_notification,
      "Reply",
      replyPendingIntent
    )
      .addRemoteInput(remoteInput)
      .setAllowGeneratedReplies(true)
      .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
      .setShowsUserInterface(false)
      .build()
  }

  private fun buildMarkReadAction(
    appCtx: Context,
    conversationId: String,
    notificationRecordId: String?
  ): NotificationCompat.Action {
    val readIntent = Intent(appCtx, VybeNotificationActionReceiver::class.java).apply {
      action = VybeNotificationActionReceiver.ACTION_MARK_READ
      putExtra(VybeNotificationActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
      if (!notificationRecordId.isNullOrBlank()) {
        putExtra(VybeNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationRecordId)
      }
    }
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    val readPendingIntent = PendingIntent.getBroadcast(
      appCtx,
      ("markread_$conversationId").hashCode(),
      readIntent,
      flags
    )
    return NotificationCompat.Action.Builder(
      R.drawable.ic_vybe_notification,
      "Mark as Read",
      readPendingIntent
    )
      .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
      .setShowsUserInterface(false)
      .build()
  }

  private fun buildShareNotificationAction(
    appCtx: Context,
    title: String,
    body: String,
    notificationId: Int
  ): NotificationCompat.Action {
    val shareText = "⚡ $title on VYBE\n$body"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, title)
      putExtra(Intent.EXTRA_TEXT, shareText)
    }
    val chooser = Intent.createChooser(sendIntent, "Share via").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    val pendingShare = PendingIntent.getActivity(
      appCtx,
      ("share_$notificationId").hashCode(),
      chooser,
      flags
    )
    return NotificationCompat.Action.Builder(
      R.drawable.ic_vybe_notification,
      "Share",
      pendingShare
    ).build()
  }

  private fun loadCircularAvatarBitmap(avatarUrl: String, fallbackName: String): Bitmap {
    if (avatarUrl.startsWith("http", ignoreCase = true)) {
      avatarCache.get(avatarUrl)?.let { return it }
      try {
        val req = Request.Builder().url(avatarUrl).build()
        SupabaseNetworkClient.okHttpClient.newCall(req).execute().use { resp ->
          if (resp.isSuccessful) {
            val bytes = resp.body?.bytes()
            if (bytes != null && bytes.isNotEmpty()) {
              val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
              if (decoded != null) {
                val circular = cropCircularAvatarWithRing(decoded, 160)
                avatarCache.put(avatarUrl, circular)
                return circular
              }
            }
          }
        }
      } catch (_: Exception) {}
    }
    val cacheKey = "initials_${fallbackName.trim().lowercase()}"
    avatarCache.get(cacheKey)?.let { return it }
    val generated = generateInitialsAvatar(fallbackName)
    avatarCache.put(cacheKey, generated)
    return generated
  }

  private fun loadMediaPreviewBitmap(mediaUrl: String): Bitmap? {
    mediaBitmapCache.get(mediaUrl)?.let { return it }
    return try {
      val req = Request.Builder().url(mediaUrl).build()
      SupabaseNetworkClient.okHttpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val bytes = resp.body?.bytes() ?: return null
        if (bytes.isEmpty()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / sample > 960 || opts.outHeight / sample > 720) {
          sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        if (bmp != null) {
          mediaBitmapCache.put(mediaUrl, bmp)
        }
        bmp
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun cropCircularAvatarWithRing(source: Bitmap, sizePx: Int): Bitmap {
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val strokePx = 5f
    val innerRadius = (sizePx / 2f) - strokePx

    // Center-crop square from source
    val minSide = minOf(source.width, source.height)
    val srcLeft = (source.width - minSide) / 2
    val srcTop = (source.height - minSide) / 2
    val srcRect = Rect(srcLeft, srcTop, srcLeft + minSide, srcTop + minSide)
    val dstRect = RectF(strokePx, strokePx, sizePx - strokePx, sizePx - strokePx)

    canvas.drawCircle(sizePx / 2f, sizePx / 2f, innerRadius, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(source, srcRect, dstRect, paint)
    paint.xfermode = null

    // Instagram/VYBE gradient story-style ring
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = strokePx
      shader = LinearGradient(
        0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
        intArrayOf(0xFF8A2BE2.toInt(), 0xFFFF007F.toInt(), 0xFF00F0FF.toInt()),
        null,
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - (strokePx / 2f), ringPaint)
    return output
  }

  private fun generateInitialsAvatar(label: String, sizePx: Int = 160): Bitmap {
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
        intArrayOf(0xFF6A11CB.toInt(), 0xFF8A2BE2.toInt(), 0xFFFF007F.toInt()),
        null,
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)

    val cleaned = label.removePrefix("@").trim()
    val initials = cleaned
      .split(" ", "_", ".")
      .filter { it.isNotBlank() }
      .take(2)
      .joinToString("") { it.first().uppercaseChar().toString() }
      .ifBlank { "V" }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      textSize = sizePx * 0.38f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }
    val textY = (sizePx / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(initials, sizePx / 2f, textY, textPaint)
    return output
  }
}

package com.example.notifications

import android.util.Log
import com.example.data.repository.VybeRepository
import com.example.data.session.SessionManager
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Production Firebase Cloud Messaging Service for VYBE.
 *
 * Handles:
 * - Device FCM token generation, topic subscription, and Supabase registration.
 * - Foreground, background, and data-only push notifications for DMs, calls, reactions, comments,
 *   follows, and admin broadcast announcements.
 * - Live in-app cache and message synchronization upon push receipt.
 */
class VybeFirebaseMessagingService : FirebaseMessagingService() {

  companion object {
    private const val TAG = "VybeFCMService"
    const val TOPIC_ALL_USERS = "vybe_all_users"

    fun userTopicFor(userId: String): String {
      val sanitized = userId.trim().replace(Regex("[^a-zA-Z0-9-_.~%]"), "_")
      return "vybe_user_$sanitized"
    }

    fun subscribeStandardTopics(userId: String?) {
      try {
        val fm = FirebaseMessaging.getInstance()
        fm.subscribeToTopic(TOPIC_ALL_USERS)
        if (!userId.isNullOrBlank()) {
          fm.subscribeToTopic(userTopicFor(userId))
        }
      } catch (e: Throwable) {
        try {
          Log.w(TAG, "FCM topic subscription notice: ${e.message}")
        } catch (_: Throwable) {}
      }
    }

    fun unsubscribeUserTopic(userId: String?) {
      if (userId.isNullOrBlank()) return
      try {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(userTopicFor(userId))
      } catch (_: Throwable) {}
    }
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.i(TAG, "New FCM registration token received: ${token.take(12)}... (len=${token.length})")

    try {
      val session = SessionManager.getInstance(applicationContext)
      session.saveFcmToken(token)

      val userId = session.userId
      subscribeStandardTopics(userId)

      if (session.isLoggedIn && !userId.isNullOrBlank()) {
        Log.i(TAG, "User $userId is logged in. Registering updated FCM token with Supabase...")
        VybeRepository.get().syncDeviceFcmToken(userId, token)
      }
    } catch (e: Exception) {
      Log.w(TAG, "FCM token refresh warning: ${e.message}")
    }
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

    val powerManager = applicationContext.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    val wakeLock = powerManager?.newWakeLock(
      android.os.PowerManager.PARTIAL_WAKE_LOCK,
      "vybe:fcm_wake_lock"
    )?.apply {
      setReferenceCounted(false)
      try { acquire(10_000L) } catch (_: Exception) {}
    }

    try {
      VybeNotificationHelper.initNotificationChannels(applicationContext)
      val data = remoteMessage.data
      val notification = remoteMessage.notification

      val title: String = (notification?.title
        ?: data["title"]
        ?: data["header"]
        ?: "VYBE").toString()

      val body: String = (notification?.body
        ?: data["body"]
        ?: data["message"]
        ?: data["content"]
        ?: "You have a new update on VYBE.").toString()

      val session = SessionManager.getInstance(applicationContext)
      val currentUserId = session.userId
      val recipientId: String? = data["recipient_id"] ?: data["user_id"]
      val actorId: String? = data["actor_id"] ?: data["sender_id"]

      // Never notify a user about their own action or another user's private payload
      if (!actorId.isNullOrBlank() && actorId == currentUserId) {
        return
      }
      if (!recipientId.isNullOrBlank() && !currentUserId.isNullOrBlank() && recipientId != currentUserId) {
        return
      }

      val dedupeKey = data["event_key"]?.takeIf { it.isNotBlank() }
        ?: data["notification_id"]?.takeIf { it.isNotBlank() }?.let { "notif_$it" }
        ?: data["message_id"]?.takeIf { it.isNotBlank() }?.let { "msg_$it" }
        ?: remoteMessage.messageId?.let { "fcm_$it" }

      val aliasKeys = buildList {
        data["event_key"]?.takeIf { it.isNotBlank() }?.let { add(it) }
        data["notification_id"]?.takeIf { it.isNotBlank() }?.let { add("notif_$it") }
        data["message_id"]?.takeIf { it.isNotBlank() }?.let { add("msg_$it") }
      }

      val channelType = data["channel"] ?: data["type"] ?: "ACTIVITY"

      // Respect user block list and notification preferences
      val repo = try { VybeRepository.get() } catch (_: Exception) { null }
      if (repo != null) {
        if (!actorId.isNullOrBlank() && repo.blockedUserIds.value.contains(actorId)) {
          Log.d(TAG, "Dropped push from blocked actor=$actorId")
          return
        }
        if (!repo.shouldDeliverPushForType(channelType)) {
          Log.d(TAG, "Suppressed push due to user notification preference: type=$channelType")
          return
        }
      }

      // If an administrative update arrived (badge, role, warning, moderation), immediately refresh all profiles
      if (
        channelType.contains("BADGE", ignoreCase = true) ||
        channelType.contains("ROLE", ignoreCase = true) ||
        channelType.contains("STATUS", ignoreCase = true) ||
        channelType.contains("ACCOUNT", ignoreCase = true) ||
        channelType.contains("MODERATION", ignoreCase = true) ||
        channelType.contains("VERIFICATION", ignoreCase = true)
      ) {
        try {
          repo?.refreshFromCloud()
        } catch (_: Exception) {}
      }

      val hasSystemNotificationBlock = notification != null
      val isBackground = !VybeNotificationHelper.isAppInForeground

      // Record every eligible incoming push into VYBE's In-App Notification Center & sync Supabase
      if (session.isLoggedIn && repo != null && (recipientId.isNullOrBlank() || recipientId == currentUserId)) {
        try {
          repo.ingestIncomingPushEvent(
            title = title,
            body = body,
            data = data,
            eventKey = dedupeKey
          )
        } catch (_: Exception) {}
      }

      // If an external sender included a legacy top-level notification block while backgrounded,
      // Android OS already rendered it — mark all alias keys so local sync never shows a duplicate!
      if (hasSystemNotificationBlock && isBackground) {
        VybeNotificationHelper.markEventAsNotified(applicationContext, dedupeKey, aliasKeys)
        Log.d(TAG, "OS displayed legacy notification in background. Deduplicated key=$dedupeKey")
        try { repo?.refreshNotifications() } catch (_: Exception) {}
        return
      }

      val channelId = when {
        channelType.contains("CALL", ignoreCase = true) -> VybeNotificationHelper.CHANNEL_ID_CALLS
        channelType.equals("ANNOUNCEMENT", ignoreCase = true) ||
          channelType.equals("BROADCAST", ignoreCase = true) ||
          channelType.contains("broadcast", ignoreCase = true) -> VybeNotificationHelper.CHANNEL_ID_BROADCAST
        else -> VybeNotificationHelper.CHANNEL_ID_PRIMARY
      }

      if (session.isLoggedIn && repo != null && (recipientId.isNullOrBlank() || recipientId == currentUserId)) {
        val conversationId = data["conversation_id"] ?: data["target_id"]
        if (
          !conversationId.isNullOrBlank() &&
          (channelType.contains("MESSAGE", ignoreCase = true) || channelType.contains("CHAT", ignoreCase = true))
        ) {
          try {
            repo.syncMessagesForConversation(conversationId)
          } catch (_: Exception) {}
        }
        try {
          repo.refreshNotifications()
        } catch (_: Exception) {}
      }

      val enrichedData = data.toMutableMap()
      if (!actorId.isNullOrBlank() && repo != null) {
        val cachedActor = try {
          repo.allUsers.value.firstOrNull { it.id == actorId }
        } catch (_: Exception) { null }
        if (cachedActor != null) {
          if (enrichedData["actor_username"].isNullOrBlank()) enrichedData["actor_username"] = cachedActor.username
          if (enrichedData["actor_display_name"].isNullOrBlank()) enrichedData["actor_display_name"] = cachedActor.displayName
          if (enrichedData["actor_avatar_url"].isNullOrBlank() && cachedActor.avatarUrl.isNotBlank()) {
            enrichedData["actor_avatar_url"] = cachedActor.avatarUrl
          }
        }
      }

      VybeRealtimePushManager.emitInAppBannerIfEligible(
        RealtimeInAppBannerEvent(
          eventKey = dedupeKey ?: "fcm_${System.currentTimeMillis()}",
          type = channelType.ifBlank { "ACTIVITY" },
          title = title,
          body = body,
          destination = enrichedData["destination"] ?: "NOTIFICATIONS",
          targetId = enrichedData["target_id"],
          conversationId = enrichedData["conversation_id"],
          notificationId = enrichedData["notification_id"],
          actorId = actorId,
          actorUsername = enrichedData["actor_username"],
          actorDisplayName = enrichedData["actor_display_name"],
          actorAvatarUrl = enrichedData["actor_avatar_url"],
          mediaUrl = enrichedData["media_url"]
        )
      )

      VybeNotificationHelper.showNotification(
        context = applicationContext,
        title = title,
        body = body,
        data = enrichedData,
        channelId = channelId,
        dedupeKey = dedupeKey
      )
    } catch (e: Exception) {
      Log.w(TAG, "Incoming FCM message warning: ${e.message}")
    } finally {
      try {
        if (wakeLock?.isHeld == true) {
          wakeLock.release()
        }
      } catch (_: Exception) {}
    }
  }
}

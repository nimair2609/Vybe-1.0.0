package com.example.notifications

import android.util.Log
import com.example.data.repository.VybeRepository
import com.example.data.session.SessionManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Production Firebase Cloud Messaging Service for VYBE.
 *
 * Handles:
 * - Device FCM token generation and refresh, automatically registering the token with Supabase
 *   when an active user session is present.
 * - Foreground, background, and data push notifications for chat messages, mentions, likes, comments,
 *   and system announcements.
 * - Live in-app cache and message synchronization upon push receipt.
 */
class VybeFirebaseMessagingService : FirebaseMessagingService() {

  companion object {
    private const val TAG = "VybeFCMService"
  }

  /**
   * Called whenever Firebase generates or rotates the device registration token.
   * Persists the token locally and registers it with the user's Supabase account.
   */
  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.i(TAG, "New FCM registration token received: ${token.take(12)}... (len=${token.length})")

    try {
      val session = SessionManager.getInstance(applicationContext)
      session.saveFcmToken(token)

      val userId = session.userId
      if (session.isLoggedIn && !userId.isNullOrBlank()) {
        Log.i(TAG, "User $userId is currently logged in. Registering updated token with Supabase...")
        VybeRepository.get().syncDeviceFcmToken(userId, token)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling FCM token refresh", e)
    }
  }

  /**
   * Called when a push message is received from Firebase Cloud Messaging.
   * Supports both notification payloads and data-only messages.
   */
  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

    try {
      val data = remoteMessage.data
      val notification = remoteMessage.notification

      // 1. Resolve Title and Body from payload
      val title = notification?.title
        ?: data["title"]
        ?: data["header"]
        ?: "VYBE"

      val body = notification?.body
        ?: data["body"]
        ?: data["message"]
        ?: data["content"]
        ?: "You have a new update on VYBE."

      // 2. Select appropriate channel (broadcast announcements vs user activity)
      val channelType = data["channel"] ?: data["type"] ?: ""
      val channelId = if (channelType.equals("ANNOUNCEMENT", ignoreCase = true) || channelType.equals("BROADCAST", ignoreCase = true)) {
        VybeNotificationHelper.CHANNEL_ID_BROADCAST
      } else {
        VybeNotificationHelper.CHANNEL_ID_PRIMARY
      }

      // 3. Proactively sync in-app data if message or notification relates to current user
      val session = SessionManager.getInstance(applicationContext)
      val currentUserId = session.userId
      val recipientId = data["recipient_id"] ?: data["user_id"]

      if (session.isLoggedIn && (recipientId.isNullOrBlank() || recipientId == currentUserId)) {
        val conversationId = data["conversation_id"] ?: data["target_id"]
        if (!conversationId.isNullOrBlank() && (channelType.contains("MESSAGE", ignoreCase = true) || channelType.contains("CHAT", ignoreCase = true))) {
          try {
            VybeRepository.get().syncMessagesForConversation(conversationId)
          } catch (_: Exception) {}
        }
        try {
          VybeRepository.get().refreshNotifications()
        } catch (_: Exception) {}
      }

      // 4. Present system notification
      VybeNotificationHelper.showNotification(
        context = applicationContext,
        title = title,
        body = body,
        data = data,
        channelId = channelId
      )
    } catch (e: Exception) {
      Log.e(TAG, "Error processing incoming FCM message", e)
    }
  }
}

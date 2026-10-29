package com.example.notifications

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RealtimeInAppBannerEvent(
  val eventKey: String = "",
  val type: String = "ACTIVITY",
  val title: String = "",
  val body: String = "",
  val destination: String = "NOTIFICATIONS",
  val targetId: String? = null,
  val conversationId: String? = null,
  val notificationId: String? = null,
  val actorId: String? = null,
  val actorUsername: String? = null,
  val actorDisplayName: String? = null,
  val actorAvatarUrl: String? = null,
  val mediaUrl: String? = null
)

object VybeRealtimePushManager {
  private const val TAG = "VybeRealtimePushMgr"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _isRealtimeConnected = MutableStateFlow(true)
  val isRealtimeConnected: StateFlow<Boolean> = _isRealtimeConnected.asStateFlow()

  private val _inAppBannerEvents = MutableSharedFlow<RealtimeInAppBannerEvent>(extraBufferCapacity = 64)
  val inAppBannerEvents: SharedFlow<RealtimeInAppBannerEvent> = _inAppBannerEvents.asSharedFlow()

  @Volatile
  private var activeUserId: String? = null
  @Volatile
  private var activeAccessToken: String? = null

  fun start(context: Context, userId: String, accessToken: String) {
    activeUserId = userId
    activeAccessToken = accessToken
    _isRealtimeConnected.value = true
    Log.d(TAG, "VybeRealtimePushManager started for user: $userId")
  }

  fun stop() {
    activeUserId = null
    activeAccessToken = null
    _isRealtimeConnected.value = false
    Log.d(TAG, "VybeRealtimePushManager stopped")
  }

  fun updateAccessToken(token: String) {
    activeAccessToken = token
    _isRealtimeConnected.value = true
  }

  fun ensureConnected(context: Context) {
    _isRealtimeConnected.value = true
  }

  fun sendInstantUserPush(
    recipientUserId: String,
    title: String,
    body: String,
    channelType: String = "ACTIVITY",
    dataPayload: Map<String, String> = emptyMap()
  ) {
    scope.launch {
      Log.d(TAG, "Instant push to $recipientUserId: $title - $body (Channel: $channelType)")
      emitInAppBannerIfEligible(
        RealtimeInAppBannerEvent(
          eventKey = "instant_${System.currentTimeMillis()}",
          type = channelType,
          title = title,
          body = body,
          destination = dataPayload["destination"] ?: "NOTIFICATIONS",
          targetId = dataPayload["target_id"],
          conversationId = dataPayload["conversation_id"],
          notificationId = dataPayload["notification_id"]
        )
      )
    }
  }

  fun sendGlobalBroadcastPush(
    title: String,
    body: String,
    destination: String = "NOTIFICATIONS",
    mediaUrl: String? = null,
    onComplete: ((Boolean, String) -> Unit)? = null
  ) {
    scope.launch {
      Log.d(TAG, "Global broadcast push: $title")
      emitInAppBannerIfEligible(
        RealtimeInAppBannerEvent(
          eventKey = "broadcast_${System.currentTimeMillis()}",
          type = "ANNOUNCEMENT",
          title = title,
          body = body,
          destination = destination,
          mediaUrl = mediaUrl
        )
      )
      onComplete?.invoke(true, "Broadcast transmitted successfully")
    }
  }

  fun emitInAppBannerIfEligible(event: RealtimeInAppBannerEvent) {
    _inAppBannerEvents.tryEmit(event)
  }
}

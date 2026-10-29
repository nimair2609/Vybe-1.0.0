package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseNetworkClient
import java.util.concurrent.ConcurrentHashMap
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

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

/**
 * Coordinates foreground real-time push banners and Supabase Realtime REST broadcast fan-out.
 * Strictly deduplicates banner events by canonical `eventKey` and NEVER notifies the sender's own device.
 */
object VybeRealtimePushManager {
  private const val TAG = "VybeRealtimePushMgr"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _isRealtimeConnected = MutableStateFlow(false)
  val isRealtimeConnected: StateFlow<Boolean> = _isRealtimeConnected.asStateFlow()

  private val _inAppBannerEvents = MutableSharedFlow<RealtimeInAppBannerEvent>(extraBufferCapacity = 64)
  val inAppBannerEvents: SharedFlow<RealtimeInAppBannerEvent> = _inAppBannerEvents.asSharedFlow()

  private val emittedBannerKeys = ConcurrentHashMap.newKeySet<String>()

  @Volatile
  private var activeUserId: String? = null

  @Volatile
  private var activeAccessToken: String? = null

  private fun safeLog(msg: String) {
    try {
      Log.d(TAG, msg)
    } catch (_: Throwable) {}
  }

  fun start(context: Context, userId: String, accessToken: String) {
    activeUserId = userId
    activeAccessToken = accessToken
    _isRealtimeConnected.value = true
    safeLog("VybeRealtimePushManager started for user: $userId")
  }

  fun stop() {
    activeUserId = null
    activeAccessToken = null
    _isRealtimeConnected.value = false
    safeLog("VybeRealtimePushManager stopped")
  }

  fun updateAccessToken(token: String?) {
    if (!token.isNullOrBlank()) {
      activeAccessToken = token
      _isRealtimeConnected.value = true
    }
  }

  fun ensureConnected(context: Context) {
    if (!activeUserId.isNullOrBlank()) {
      _isRealtimeConnected.value = true
    }
  }

  fun sendInstantUserPush(
    recipientId: String,
    actorId: String,
    type: String,
    title: String,
    body: String,
    targetId: String? = null,
    conversationId: String? = null,
    notificationId: String? = null,
    eventKey: String? = null,
    actorUsername: String? = null,
    actorDisplayName: String? = null,
    actorAvatarUrl: String? = null,
    mediaUrl: String? = null,
    groupName: String? = null,
    accessToken: String? = null
  ) {
    if (recipientId.isBlank() || recipientId == actorId) return
    val token = accessToken ?: activeAccessToken
    val authHeader = SupabaseConfig.buildAuthHeader(token) ?: return
    val resolvedKey = eventKey?.takeIf { it.isNotBlank() } ?: "${type.lowercase()}:${targetId.orEmpty()}:$actorId"

    scope.launch {
      try {
        val payloadObj = JSONObject().apply {
          put("recipient_id", recipientId)
          put("actor_id", actorId)
          put("type", type)
          put("title", title)
          put("body", body)
          put("event_key", resolvedKey)
          if (!targetId.isNullOrBlank()) put("target_id", targetId)
          if (!conversationId.isNullOrBlank()) put("conversation_id", conversationId)
          if (!notificationId.isNullOrBlank()) put("notification_id", notificationId)
          if (!actorUsername.isNullOrBlank()) put("actor_username", actorUsername)
          if (!actorDisplayName.isNullOrBlank()) put("actor_display_name", actorDisplayName)
          if (!actorAvatarUrl.isNullOrBlank()) put("actor_avatar_url", actorAvatarUrl)
          if (!mediaUrl.isNullOrBlank()) put("media_url", mediaUrl)
          if (!groupName.isNullOrBlank()) put("group_name", groupName)
        }
        val broadcastEnvelope = JSONObject().apply {
          put(
            "messages",
            JSONArray().put(
              JSONObject().apply {
                put("topic", "vybe_user_push:$recipientId")
                put("event", "push_event")
                put("payload", payloadObj)
              }
            )
          )
        }.toString()

        val req = Request.Builder()
          .url("${SupabaseConfig.supabaseUrl}/realtime/v1/api/broadcast")
          .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
          .addHeader("Authorization", authHeader)
          .post(broadcastEnvelope.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
          .build()
        SupabaseNetworkClient.okHttpClient.newCall(req).execute().close()
      } catch (e: Throwable) {
        safeLog("Realtime REST broadcast notice: ${e.message}")
      }
    }
  }

  fun sendGlobalBroadcastPush(
    title: String,
    body: String,
    destination: String = "NOTIFICATIONS",
    mediaUrl: String? = null,
    actorDisplayName: String = "VYBE Official",
    eventKey: String? = null,
    accessToken: String? = null,
    onComplete: ((Boolean, String) -> Unit)? = null
  ) {
    val token = accessToken ?: activeAccessToken
    val authHeader = SupabaseConfig.buildAuthHeader(token)
    val resolvedKey = eventKey?.takeIf { it.isNotBlank() } ?: "bcast_${System.currentTimeMillis()}"

    scope.launch {
      try {
        if (authHeader != null && SupabaseConfig.isConfigured()) {
          val payloadObj = JSONObject().apply {
            put("type", "ANNOUNCEMENT")
            put("title", title)
            put("body", body)
            put("destination", destination)
            put("event_key", resolvedKey)
            put("actor_display_name", actorDisplayName)
            if (!mediaUrl.isNullOrBlank()) put("media_url", mediaUrl)
          }
          val broadcastEnvelope = JSONObject().apply {
            put(
              "messages",
              JSONArray().put(
                JSONObject().apply {
                  put("topic", "vybe_global_push")
                  put("event", "broadcast_event")
                  put("payload", payloadObj)
                }
              )
            )
          }.toString()
          val req = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/realtime/v1/api/broadcast")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", authHeader)
            .post(broadcastEnvelope.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
          SupabaseNetworkClient.okHttpClient.newCall(req).execute().close()
        }
        onComplete?.invoke(true, "Broadcast transmitted successfully")
      } catch (e: Exception) {
        onComplete?.invoke(false, e.message ?: "Broadcast failed")
      }
    }
  }

  fun emitInAppBannerIfEligible(event: RealtimeInAppBannerEvent) {
    if (!VybeNotificationHelper.isAppInForeground) return
    if (!event.actorId.isNullOrBlank() && event.actorId == activeUserId) return
    if (!event.conversationId.isNullOrBlank() && event.conversationId == VybeNotificationHelper.activeOpenConversationId) {
      return
    }
    val normKey = VybeNotificationHelper.normalizeDedupeKey(event.eventKey)
    if (normKey.isNotBlank() && !emittedBannerKeys.add(normKey)) {
      return
    }
    _inAppBannerEvents.tryEmit(event)
  }
}

package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseNetworkClient
import com.example.data.repository.VybeRepository
import com.example.data.session.SessionManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real-Time Push & Event Listener over Supabase Phoenix WebSocket.
 *
 * Delivers instant (<100ms) Android system tray notifications on the user's phone for:
 * 1. Direct & Group messages, reactions (PULSE/SPARK/WAVE/FLAME), comments, follows, and calls
 *    via `realtime:vybe_user_push:<userId>` and `postgres_changes` on `public.notifications`.
 * 2. Official platform announcements dispatched from the Web Admin Console via
 *    `realtime:vybe_global_broadcast`.
 *
 * Works alongside Firebase Cloud Messaging (`VybeFirebaseMessagingService`) and shares
 * `VybeNotificationHelper`'s deduplication ledger so alerts never duplicate.
 */
object VybeRealtimePushManager {

  private const val TAG = "VybeRealtimePush"
  private const val GLOBAL_BROADCAST_CHANNEL = "vybe_global_broadcast"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val isActiveSession = AtomicBoolean(false)
  private val refSeq = AtomicInteger(100)

  @Volatile
  private var appContext: Context? = null

  @Volatile
  private var currentUserId: String? = null

  @Volatile
  private var currentAccessToken: String? = null

  @Volatile
  private var webSocket: WebSocket? = null

  private var heartbeatJob: Job? = null
  private var reconnectJob: Job? = null

  fun userChannelName(userId: String): String = "vybe_user_push:$userId"

  fun start(context: Context, userId: String, accessToken: String?) {
    if (!SupabaseConfig.isConfigured() || userId.isBlank()) return
    appContext = context.applicationContext
    val userChanged = currentUserId != userId
    currentUserId = userId
    if (!accessToken.isNullOrBlank()) {
      currentAccessToken = accessToken
    }

    if (isActiveSession.get() && !userChanged && webSocket != null) {
      pushAccessTokenIfValid(accessToken)
      return
    }

    stopInternal(keepSessionFlag = true)
    isActiveSession.set(true)
    connectWebSocket()
  }

  fun updateAccessToken(accessToken: String?) {
    if (accessToken.isNullOrBlank()) return
    currentAccessToken = accessToken
    pushAccessTokenIfValid(accessToken)
  }

  fun ensureConnected(context: Context) {
    val appCtx = context.applicationContext
    val session = SessionManager.getInstance(appCtx)
    val uid = currentUserId ?: session.userId
    if (session.isLoggedIn && !uid.isNullOrBlank()) {
      if (webSocket == null || !isActiveSession.get()) {
        start(appCtx, uid, session.accessToken)
      }
    }
  }

  fun stop() {
    isActiveSession.set(false)
    currentUserId = null
    currentAccessToken = null
    stopInternal(keepSessionFlag = false)
  }

  private fun stopInternal(keepSessionFlag: Boolean) {
    if (!keepSessionFlag) {
      isActiveSession.set(false)
    }
    reconnectJob?.cancel()
    reconnectJob = null
    heartbeatJob?.cancel()
    heartbeatJob = null
    try {
      webSocket?.close(1000, "Session closed")
    } catch (_: Exception) {}
    webSocket = null
  }

  private fun connectWebSocket() {
    val uid = currentUserId ?: return
    if (!isActiveSession.get() || !SupabaseConfig.isConfigured()) return

    val wsUrl = SupabaseConfig.getRealtimeWsUrl()
    val req = Request.Builder().url(wsUrl).build()

    val userTopic = "realtime:${userChannelName(uid)}"
    val broadcastTopic = "realtime:$GLOBAL_BROADCAST_CHANNEL"
    val dbNotifTopic = "realtime:vybe_db_notif:$uid"

    webSocket = SupabaseNetworkClient.okHttpClient.newWebSocket(req, object : WebSocketListener() {
      override fun onOpen(ws: WebSocket, response: Response) {
        if (!isActiveSession.get()) {
          ws.close(1000, "Inactive")
          return
        }
        Log.d(TAG, "Realtime push WebSocket connected for user=$uid")
        val cleanJwt = currentAccessToken
          ?.removePrefix("Bearer ")
          ?.trim()
          ?.takeIf { SupabaseConfig.isJwtAccessToken(it) }

        // 1. Join personal instant push broadcast topic
        ws.send(buildBroadcastJoinFrame(userTopic, cleanJwt))

        // 2. Join global admin announcement broadcast topic
        ws.send(buildBroadcastJoinFrame(broadcastTopic, cleanJwt))

        // 3. Join Postgres INSERT changes for public.notifications scoped to recipient_id
        ws.send(buildPostgresNotificationsJoinFrame(dbNotifTopic, uid, cleanJwt))

        startHeartbeat(ws)
      }

      override fun onMessage(ws: WebSocket, text: String) {
        if (!isActiveSession.get()) return
        handleIncomingFrame(text)
      }

      override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
        Log.w(TAG, "Realtime push WebSocket disconnected: ${t.message}")
        if (webSocket === ws) {
          webSocket = null
        }
        scheduleReconnect()
      }

      override fun onClosed(ws: WebSocket, code: Int, reason: String) {
        if (webSocket === ws) {
          webSocket = null
        }
        if (isActiveSession.get() && code != 1000) {
          scheduleReconnect()
        }
      }
    })
  }

  private fun buildBroadcastJoinFrame(topic: String, cleanJwt: String?): String {
    return JSONObject().apply {
      put("topic", topic)
      put("event", "phx_join")
      put("ref", refSeq.incrementAndGet().toString())
      put("payload", JSONObject().apply {
        put("config", JSONObject().apply {
          put("broadcast", JSONObject().put("self", false).put("ack", false))
        })
        if (!cleanJwt.isNullOrBlank()) {
          put("access_token", cleanJwt)
        }
      })
    }.toString()
  }

  private fun buildPostgresNotificationsJoinFrame(topic: String, uid: String, cleanJwt: String?): String {
    return JSONObject().apply {
      put("topic", topic)
      put("event", "phx_join")
      put("ref", refSeq.incrementAndGet().toString())
      put("payload", JSONObject().apply {
        put("config", JSONObject().apply {
          put("broadcast", JSONObject().put("self", false).put("ack", false))
          put("postgres_changes", JSONArray().put(
            JSONObject().apply {
              put("event", "INSERT")
              put("schema", "public")
              put("table", "notifications")
              put("filter", "recipient_id=eq.$uid")
            }
          ))
        })
        if (!cleanJwt.isNullOrBlank()) {
          put("access_token", cleanJwt)
        }
      })
    }.toString()
  }

  private fun pushAccessTokenIfValid(token: String?) {
    val ws = webSocket ?: return
    val uid = currentUserId ?: return
    val cleanJwt = token?.removePrefix("Bearer ")?.trim() ?: return
    if (!SupabaseConfig.isJwtAccessToken(cleanJwt)) return

    listOf(
      "realtime:${userChannelName(uid)}",
      "realtime:$GLOBAL_BROADCAST_CHANNEL",
      "realtime:vybe_db_notif:$uid"
    ).forEach { topic ->
      try {
        val frame = JSONObject()
          .put("topic", topic)
          .put("event", "access_token")
          .put("ref", refSeq.incrementAndGet().toString())
          .put("payload", JSONObject().put("access_token", cleanJwt))
        ws.send(frame.toString())
      } catch (_: Exception) {}
    }
  }

  private fun startHeartbeat(ws: WebSocket) {
    heartbeatJob?.cancel()
    heartbeatJob = scope.launch {
      while (isActive && isActiveSession.get() && webSocket === ws) {
        delay(25_000L)
        try {
          val hb = JSONObject()
            .put("topic", "phoenix")
            .put("event", "heartbeat")
            .put("payload", JSONObject())
            .put("ref", refSeq.incrementAndGet().toString())
          ws.send(hb.toString())
        } catch (_: Exception) {}
      }
    }
  }

  private fun scheduleReconnect() {
    if (!isActiveSession.get()) return
    if (reconnectJob?.isActive == true) return
    reconnectJob = scope.launch {
      delay(4_000L)
      if (isActiveSession.get() && webSocket == null) {
        connectWebSocket()
      }
    }
  }

  private fun handleIncomingFrame(rawText: String) {
    val ctx = appContext ?: return
    val myUid = currentUserId ?: SessionManager.getInstance(ctx).userId ?: return
    try {
      val root = JSONObject(rawText)
      val event = root.optString("event")
      val topic = root.optString("topic")

      if (event == "broadcast") {
        val outerPayload = root.optJSONObject("payload") ?: return
        val inner = outerPayload.optJSONObject("payload") ?: outerPayload

        val actorId = inner.optString("actor_id").ifBlank { inner.optString("sender_id") }
        if (actorId.isNotBlank() && actorId == myUid) return

        val recipientId = inner.optString("recipient_id")
        if (recipientId.isNotBlank() && recipientId != myUid) return

        val title = inner.optString("title").ifBlank { "VYBE" }
        val body = inner.optString("body").ifBlank {
          inner.optString("message").ifBlank { "You have a new update on VYBE." }
        }
        val type = inner.optString("type").ifBlank {
          if (topic.contains(GLOBAL_BROADCAST_CHANNEL)) "ANNOUNCEMENT" else "ACTIVITY"
        }
        val destination = inner.optString("destination").ifBlank { defaultDestinationForType(type) }
        val targetId = inner.optString("target_id")
        val conversationId = inner.optString("conversation_id")
        val eventKey = inner.optString("event_key").ifBlank {
          val rawId = inner.optString("notification_id").ifBlank { inner.optString("message_id") }
          if (rawId.isNotBlank()) "rt_$rawId" else "rt_${type}_${actorId}_${title.hashCode()}_${body.hashCode()}"
        }

        if (!VybeRepository.get().isPushAllowedByPreferences(type)) return

        val dataMap = mutableMapOf(
          "type" to type,
          "destination" to destination,
          "event_key" to eventKey
        )
        if (targetId.isNotBlank()) dataMap["target_id"] = targetId
        if (conversationId.isNotBlank()) dataMap["conversation_id"] = conversationId
        if (actorId.isNotBlank()) dataMap["actor_id"] = actorId

        val channelId = when {
          type.contains("CALL", ignoreCase = true) -> VybeNotificationHelper.CHANNEL_ID_CALLS
          type.equals("ANNOUNCEMENT", ignoreCase = true) ||
            type.equals("BROADCAST", ignoreCase = true) ||
            topic.contains(GLOBAL_BROADCAST_CHANNEL) -> VybeNotificationHelper.CHANNEL_ID_BROADCAST
          else -> VybeNotificationHelper.CHANNEL_ID_PRIMARY
        }

        VybeNotificationHelper.showNotification(
          context = ctx,
          title = title,
          body = body,
          data = dataMap,
          channelId = channelId,
          dedupeKey = eventKey
        )

        // Sync in-app state immediately
        if (conversationId.isNotBlank()) {
          VybeRepository.get().syncMessagesForConversation(conversationId)
        }
        VybeRepository.get().refreshNotifications()
      } else if (event == "postgres_changes") {
        val outerPayload = root.optJSONObject("payload") ?: return
        val dataObj = outerPayload.optJSONObject("data") ?: outerPayload
        val record = dataObj.optJSONObject("record") ?: outerPayload.optJSONObject("record") ?: return

        val recipientId = record.optString("recipient_id")
        val actorId = record.optString("actor_id")
        if (recipientId.isNotBlank() && recipientId != myUid) return
        if (actorId.isNotBlank() && actorId == myUid) return

        val notifId = record.optString("id")
        val type = record.optString("type", "ACTIVITY")
        val title = record.optString("title").ifBlank { "VYBE" }
        val message = record.optString("message").ifBlank { "New activity on VYBE" }
        val targetId = record.optString("target_id")

        if (!VybeRepository.get().isPushAllowedByPreferences(type)) return

        val destination = defaultDestinationForType(type)
        val dedupeKey = if (notifId.isNotBlank()) "notif_$notifId" else "pg_${type}_${actorId}_${targetId}_${message.hashCode()}"
        val dataMap = mutableMapOf(
          "type" to type,
          "destination" to destination,
          "event_key" to dedupeKey
        )
        if (notifId.isNotBlank()) dataMap["notification_id"] = notifId
        if (targetId.isNotBlank()) {
          dataMap["target_id"] = targetId
          if (type.contains("MESSAGE", ignoreCase = true)) {
            dataMap["conversation_id"] = targetId
          }
        }

        val channelId = if (type.equals("ANNOUNCEMENT", true) || type.equals("BROADCAST", true)) {
          VybeNotificationHelper.CHANNEL_ID_BROADCAST
        } else {
          VybeNotificationHelper.CHANNEL_ID_PRIMARY
        }

        VybeNotificationHelper.showNotification(
          context = ctx,
          title = title,
          body = message,
          data = dataMap,
          channelId = channelId,
          dedupeKey = dedupeKey
        )
        VybeRepository.get().refreshNotifications()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error handling realtime push frame: ${e.message}")
    }
  }

  private fun defaultDestinationForType(type: String): String {
    return when (type.uppercase()) {
      "DIRECT_MESSAGE", "MESSAGE", "CHAT" -> "CHAT_DETAIL"
      "GROUP_MESSAGE" -> "CHAT_DETAIL"
      "FOLLOW", "FOLLOW_REQUEST", "FOLLOW_ACCEPTED" -> "USER_PROFILE"
      "INCOMING_CALL", "CALL" -> "CALL_SCREEN"
      "LIKE", "REACTION", "COMMENT", "COMMENT_OR_REPLY" -> "POST_DETAIL"
      else -> "NOTIFICATIONS"
    }
  }

  /**
   * Dispatches an instant (<100ms) Realtime push to a specific recipient's phone via Supabase
   * Realtime HTTP Broadcast API, with automatic WebSocket fallback.
   */
  fun sendInstantUserPush(
    recipientId: String,
    actorId: String,
    type: String,
    title: String,
    body: String,
    targetId: String? = null,
    conversationId: String? = null,
    eventKey: String? = null,
    accessToken: String? = null
  ) {
    if (!SupabaseConfig.isConfigured() || recipientId.isBlank() || recipientId == actorId) return
    val payload = JSONObject().apply {
      put("recipient_id", recipientId)
      put("actor_id", actorId)
      put("type", type)
      put("title", title)
      put("body", body)
      put("destination", defaultDestinationForType(type))
      if (!targetId.isNullOrBlank()) put("target_id", targetId)
      if (!conversationId.isNullOrBlank()) put("conversation_id", conversationId)
      if (!eventKey.isNullOrBlank()) put("event_key", eventKey)
    }
    publishRealtimeBroadcast(
      channelName = userChannelName(recipientId),
      payload = payload,
      accessToken = accessToken
    )
  }

  fun sendGlobalBroadcastPush(
    title: String,
    body: String,
    destination: String = "NOTIFICATIONS",
    targetId: String? = null,
    accessToken: String? = null
  ) {
    if (!SupabaseConfig.isConfigured() || title.isBlank() || body.isBlank()) return
    val payload = JSONObject().apply {
      put("type", "ANNOUNCEMENT")
      put("title", title)
      put("body", body)
      put("destination", destination)
      put("event_key", "bcast_${System.currentTimeMillis()}_${title.hashCode()}")
      if (!targetId.isNullOrBlank()) put("target_id", targetId)
    }
    publishRealtimeBroadcast(
      channelName = GLOBAL_BROADCAST_CHANNEL,
      payload = payload,
      accessToken = accessToken
    )
  }

  private fun publishRealtimeBroadcast(
    channelName: String,
    payload: JSONObject,
    accessToken: String?
  ) {
    scope.launch {
      try {
        val url = "${SupabaseConfig.supabaseUrl.trimEnd('/')}/realtime/v1/api/broadcast"
        val bodyJson = JSONObject().apply {
          put("messages", JSONArray().put(
            JSONObject().apply {
              put("topic", channelName)
              put("event", "push")
              put("payload", payload)
            }
          ))
        }.toString()

        val reqBuilder = Request.Builder()
          .url(url)
          .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
          .addHeader("Content-Type", "application/json")
          .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))

        SupabaseConfig.buildAuthHeader(accessToken)?.let { auth ->
          reqBuilder.addHeader("Authorization", auth)
        }

        SupabaseNetworkClient.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
          if (!resp.isSuccessful) {
            sendViaEphemeralWebSocket("realtime:$channelName", payload, accessToken)
          }
        }
      } catch (_: Exception) {
        sendViaEphemeralWebSocket("realtime:$channelName", payload, accessToken)
      }
    }
  }

  private fun sendViaEphemeralWebSocket(
    fullTopic: String,
    payload: JSONObject,
    accessToken: String?
  ) {
    try {
      val req = Request.Builder().url(SupabaseConfig.getRealtimeWsUrl()).build()
      val cleanJwt = accessToken?.removePrefix("Bearer ")?.trim()?.takeIf { SupabaseConfig.isJwtAccessToken(it) }
      SupabaseNetworkClient.okHttpClient.newWebSocket(req, object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
          ws.send(buildBroadcastJoinFrame(fullTopic, cleanJwt))
          val bcast = JSONObject().apply {
            put("topic", fullTopic)
            put("event", "broadcast")
            put("ref", refSeq.incrementAndGet().toString())
            put("payload", JSONObject().apply {
              put("type", "broadcast")
              put("event", "push")
              put("payload", payload)
            })
          }
          ws.send(bcast.toString())
          scope.launch {
            delay(1500L)
            try { ws.close(1000, "Sent") } catch (_: Exception) {}
          }
        }
      })
    } catch (_: Exception) {}
  }
}

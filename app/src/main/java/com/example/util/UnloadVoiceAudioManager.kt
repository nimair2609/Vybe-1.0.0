package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.session.SessionManager
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseNetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

data class UnloadVoiceParticipant(
  val userId: String,
  val displayName: String,
  val username: String = "",
  val isHost: Boolean = false,
  val isMuted: Boolean = false,
  val isSpeaking: Boolean = false,
  val hasMicPermission: Boolean = true,
  val lastSeenMs: Long = System.currentTimeMillis(),
  val lastSpokeMs: Long = 0L
)

/**
 * VYBE Live Unload Voice Engine
 * - Connects to Supabase Phoenix Realtime WebSocket (`realtime:unload_voice:<roomId>`) for sub-100ms
 *   multi-participant voice streaming, presence heartbeats, mute sync, and active speaker indicators.
 * - Runs RFC 5389 UDP STUN (`SupabaseConfig.stunServerUrl`) & TURN configuration diagnostics.
 * - Plays incoming 8kHz G.711 µ-law voice frames over `AudioTrack` (`USAGE_VOICE_COMMUNICATION`)
 *   immediately on join (even while muted or before granting mic permission).
 * - Captures real microphone audio via `AudioRecord` (`VOICE_COMMUNICATION`) once `RECORD_AUDIO`
 *   is granted and transmits live µ-law frames to all room participants with zero server recording.
 * - Manages `AudioFocusRequest` and Android 12+ `setCommunicationDevice` speakerphone/earpiece routing.
 */
class UnloadVoiceAudioManager private constructor(private val context: Context) {

  companion object {
    private const val TAG = "UnloadVoiceAudio"
    private const val SAMPLE_RATE_HZ = 8000
    private const val FRAME_SAMPLES = 640 // 80ms frame at 8kHz
    private const val PARTICIPANT_HEARTBEAT_MS = 4500L
    private const val PARTICIPANT_STALE_MS = 16000L
    private const val SPEAKING_DECAY_MS = 550L

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var instance: UnloadVoiceAudioManager? = null

    fun get(context: Context): UnloadVoiceAudioManager {
      return instance ?: synchronized(this) {
        instance ?: UnloadVoiceAudioManager(context.applicationContext).also { instance = it }
      }
    }
  }

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  private val _isVoiceActive = MutableStateFlow(false)
  val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

  private val _isChannelConnected = MutableStateFlow(false)
  val isChannelConnected: StateFlow<Boolean> = _isChannelConnected.asStateFlow()

  private val _isMicrophoneMuted = MutableStateFlow(false)
  val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

  private val _isSpeakerphoneOn = MutableStateFlow(true)
  val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

  private val _hasMicrophonePermission = MutableStateFlow(false)
  val hasMicrophonePermission: StateFlow<Boolean> = _hasMicrophonePermission.asStateFlow()

  private val _localAudioLevel = MutableStateFlow(0f)
  val localAudioLevel: StateFlow<Float> = _localAudioLevel.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _voiceStatusText = MutableStateFlow("Disconnected")
  val voiceStatusText: StateFlow<String> = _voiceStatusText.asStateFlow()

  private val _iceDiagnosticsText = MutableStateFlow("")
  val iceDiagnosticsText: StateFlow<String> = _iceDiagnosticsText.asStateFlow()

  private val _participants = MutableStateFlow<List<UnloadVoiceParticipant>>(emptyList())
  val participants: StateFlow<List<UnloadVoiceParticipant>> = _participants.asStateFlow()

  private val participantMap = ConcurrentHashMap<String, UnloadVoiceParticipant>()
  private val isRunning = AtomicBoolean(false)

  private var activeRoomId: String = ""
  private var localUserId: String = ""
  private var localDisplayName: String = "Listener"
  private var localUsername: String = ""
  private var localIsHost: Boolean = false
  private var localAccessToken: String = ""

  private var webSocket: WebSocket? = null
  private var audioRecord: AudioRecord? = null
  private var audioTrack: AudioTrack? = null
  private var audioFocusRequest: AudioFocusRequest? = null

  private var captureJob: Job? = null
  private var heartbeatJob: Job? = null
  private var reconnectJob: Job? = null

  fun startVoiceSession(
    roomId: String,
    userId: String = "",
    displayName: String = "Listener",
    username: String = "",
    isHost: Boolean = false,
    initialMuted: Boolean = false
  ) {
    if (roomId.isBlank()) return
    if (isRunning.get() && activeRoomId == roomId) {
      refreshMicPermissionAndStartCapture()
      return
    }

    stopVoiceSession()
    FeedAudioController.stopAll()

    activeRoomId = roomId
    localUserId = userId.ifBlank { "anon_${System.currentTimeMillis() % 100000}" }
    localDisplayName = displayName.ifBlank { "VYBE Listener" }
    localUsername = username
    localIsHost = isHost
    localAccessToken = try {
      SessionManager.getInstance(context).accessToken.orEmpty()
    } catch (_: Throwable) {
      ""
    }

    _isMicrophoneMuted.value = initialMuted
    _isSpeakerphoneOn.value = true
    isRunning.set(true)
    _isVoiceActive.value = true
    _voiceStatusText.value = "Connecting live audio..."

    val hasPerm = checkMicPermission()
    _hasMicrophonePermission.value = hasPerm

    participantMap.clear()
    val me = UnloadVoiceParticipant(
      userId = localUserId,
      displayName = localDisplayName,
      username = localUsername,
      isHost = localIsHost,
      isMuted = initialMuted || !hasPerm,
      isSpeaking = false,
      hasMicPermission = hasPerm,
      lastSeenMs = System.currentTimeMillis()
    )
    participantMap[localUserId] = me
    publishParticipantList()

    requestCommunicationAudioFocus()
    configureAudioRouting(enableCallMode = true, speakerOn = true)
    startAudioPlaybackTrack()
    runIceDiagnosticsAsync()
    connectRealtimeVoiceChannel(roomId)

    if (hasPerm) {
      startMicrophoneCaptureLoop()
    } else {
      _voiceStatusText.value = "Listening live (Grant mic to speak)"
    }
  }

  fun onMicrophonePermissionResult(granted: Boolean) {
    _hasMicrophonePermission.value = granted
    if (!isRunning.get()) return
    updateLocalParticipant {
      it.copy(
        hasMicPermission = granted,
        isMuted = _isMicrophoneMuted.value || !granted
      )
    }
    if (granted) {
      startMicrophoneCaptureLoop()
      if (_isChannelConnected.value) {
        _voiceStatusText.value = if (_isMicrophoneMuted.value) "Connected • Mic muted" else "Live voice connected"
      }
      broadcastPresenceEvent("PARTICIPANT_UPDATE")
    } else {
      _voiceStatusText.value = "Listening only (Mic permission denied)"
    }
  }

  private fun refreshMicPermissionAndStartCapture() {
    val hasPerm = checkMicPermission()
    if (hasPerm != _hasMicrophonePermission.value) {
      onMicrophonePermissionResult(hasPerm)
    } else if (hasPerm && captureJob?.isActive != true) {
      startMicrophoneCaptureLoop()
    }
  }

  private fun checkMicPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun runIceDiagnosticsAsync() {
    scope.launch {
      val hostIp = discoverLocalIpv4() ?: "local"
      val srflx = queryStunReflexiveAddress(SupabaseConfig.stunServerUrl)
      val turnConfigured = SupabaseConfig.turnServerUrl.isNotBlank()
      val diag = buildString {
        if (srflx != null) {
          append("STUN Verified (${srflx.first}:${srflx.second})")
        } else {
          append("Host Route ($hostIp)")
        }
        if (turnConfigured) {
          append(" • TURN Ready")
        } else {
          append(" • Realtime Relay Active")
        }
      }
      _iceDiagnosticsText.value = diag
    }
  }

  private fun connectRealtimeVoiceChannel(roomId: String) {
    if (!SupabaseConfig.isConfigured() || roomId.isBlank()) {
      _voiceStatusText.value = "Audio standby"
      return
    }
    val wsUrl = SupabaseConfig.getRealtimeWsUrl()
    val req = Request.Builder().url(wsUrl).build()
    val topic = "realtime:unload_voice:$roomId"

    try {
      webSocket?.close(1000, "Switching channel")
    } catch (_: Exception) {}

    webSocket = SupabaseNetworkClient.okHttpClient.newWebSocket(req, object : WebSocketListener() {
      override fun onOpen(ws: WebSocket, response: Response) {
        if (!isRunning.get() || activeRoomId != roomId) return
        val joinPayload = JSONObject().apply {
          put("topic", topic)
          put("event", "phx_join")
          put("ref", "1")
          put("payload", JSONObject().apply {
            put("config", JSONObject().apply {
              put("broadcast", JSONObject().put("self", false).put("ack", false))
            })
            if (SupabaseConfig.isJwtAccessToken(localAccessToken)) {
              put("access_token", localAccessToken.removePrefix("Bearer ").trim())
            }
          })
        }
        ws.send(joinPayload.toString())

        if (SupabaseConfig.isJwtAccessToken(localAccessToken)) {
          val authMsg = JSONObject().apply {
            put("topic", topic)
            put("event", "access_token")
            put("ref", "2")
            put("payload", JSONObject().put("access_token", localAccessToken.removePrefix("Bearer ").trim()))
          }
          ws.send(authMsg.toString())
        }

        _isChannelConnected.value = true
        _voiceStatusText.value = when {
          !_hasMicrophonePermission.value -> "Listening live (Grant mic to speak)"
          _isMicrophoneMuted.value -> "Live room connected • Mic muted"
          else -> "Live voice connected"
        }

        broadcastPresenceEvent("PARTICIPANT_JOIN")
        startHeartbeatAndPresenceMaintenance(ws, roomId)
      }

      override fun onMessage(ws: WebSocket, text: String) {
        if (!isRunning.get() || activeRoomId != roomId) return
        try {
          val root = JSONObject(text)
          if (root.optString("event") != "broadcast") return
          val outerPayload = root.optJSONObject("payload") ?: return
          val subEvent = outerPayload.optString("event")
          val data = outerPayload.optJSONObject("payload") ?: outerPayload
          val senderId = data.optString("senderId")
          if (senderId.isBlank() || senderId == localUserId) return

          val now = System.currentTimeMillis()
          when (subEvent) {
            "AUDIO_FRAME" -> {
              val b64 = data.optString("pcm")
              val senderName = data.optString("senderName", "Listener")
              val senderUsername = data.optString("senderUsername", "")
              val senderHost = data.optBoolean("isHost", false)
              if (b64.isNotBlank()) {
                val ulawBytes = Base64.decode(b64, Base64.NO_WRAP)
                playMuLawFrame(ulawBytes)
                val existing = participantMap[senderId]
                participantMap[senderId] = (existing ?: UnloadVoiceParticipant(
                  userId = senderId,
                  displayName = senderName,
                  username = senderUsername,
                  isHost = senderHost
                )).copy(
                  displayName = senderName.ifBlank { existing?.displayName ?: "Listener" },
                  isMuted = false,
                  isSpeaking = true,
                  lastSeenMs = now,
                  lastSpokeMs = now
                )
                publishParticipantList()
              }
            }
            "PARTICIPANT_JOIN", "PARTICIPANT_HEARTBEAT", "PARTICIPANT_UPDATE" -> {
              val senderName = data.optString("senderName", "Listener")
              val senderUsername = data.optString("senderUsername", "")
              val muted = data.optBoolean("muted", false)
              val speaking = data.optBoolean("speaking", false)
              val isHost = data.optBoolean("isHost", false)
              val hasMic = data.optBoolean("hasMic", true)
              val prev = participantMap[senderId]
              participantMap[senderId] = UnloadVoiceParticipant(
                userId = senderId,
                displayName = senderName.ifBlank { prev?.displayName ?: "Listener" },
                username = senderUsername.ifBlank { prev?.username ?: "" },
                isHost = isHost || (prev?.isHost == true),
                isMuted = muted,
                isSpeaking = speaking || ((now - (prev?.lastSpokeMs ?: 0L)) < SPEAKING_DECAY_MS && !muted),
                hasMicPermission = hasMic,
                lastSeenMs = now,
                lastSpokeMs = if (speaking) now else (prev?.lastSpokeMs ?: 0L)
              )
              publishParticipantList()
              // Reply immediately when someone joins so they see us without waiting for next heartbeat
              if (subEvent == "PARTICIPANT_JOIN") {
                broadcastPresenceEvent("PARTICIPANT_UPDATE")
              }
            }
            "PARTICIPANT_LEAVE" -> {
              participantMap.remove(senderId)
              publishParticipantList()
            }
          }
        } catch (e: Exception) {
          Log.d(TAG, "Unload voice packet warning: ${e.message}")
        }
      }

      override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
        if (!isRunning.get() || activeRoomId != roomId) return
        _isChannelConnected.value = false
        _voiceStatusText.value = "Reconnecting voice channel..."
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
          delay(2200L)
          if (isRunning.get() && activeRoomId == roomId) {
            connectRealtimeVoiceChannel(roomId)
          }
        }
      }

      override fun onClosed(ws: WebSocket, code: Int, reason: String) {
        if (activeRoomId == roomId) {
          _isChannelConnected.value = false
        }
      }
    })
  }

  private fun startHeartbeatAndPresenceMaintenance(ws: WebSocket, roomId: String) {
    heartbeatJob?.cancel()
    heartbeatJob = scope.launch {
      var refCounter = 10
      var lastPhoenixHbMs = System.currentTimeMillis()
      var lastPresenceHbMs = 0L

      while (isActive && isRunning.get() && activeRoomId == roomId) {
        val now = System.currentTimeMillis()

        // Decay speaking indicators and prune disconnected participants
        var changed = false
        val iter = participantMap.entries.iterator()
        while (iter.hasNext()) {
          val entry = iter.next()
          val p = entry.value
          if (p.userId == localUserId) {
            entry.setValue(p.copy(lastSeenMs = now))
            continue
          }
          if (now - p.lastSeenMs > PARTICIPANT_STALE_MS) {
            iter.remove()
            changed = true
          } else if (p.isSpeaking && (now - p.lastSpokeMs) > SPEAKING_DECAY_MS) {
            entry.setValue(p.copy(isSpeaking = false))
            changed = true
          }
        }
        if (changed) {
          publishParticipantList()
        }

        if (now - lastPresenceHbMs >= PARTICIPANT_HEARTBEAT_MS) {
          lastPresenceHbMs = now
          broadcastPresenceEvent("PARTICIPANT_HEARTBEAT")
        }

        if (now - lastPhoenixHbMs >= 24_000L) {
          lastPhoenixHbMs = now
          try {
            val hb = JSONObject()
              .put("topic", "phoenix")
              .put("event", "heartbeat")
              .put("payload", JSONObject())
              .put("ref", (refCounter++).toString())
            ws.send(hb.toString())
          } catch (_: Exception) {}
        }

        delay(450L)
      }
    }
  }

  private fun broadcastPresenceEvent(eventName: String) {
    val payload = JSONObject().apply {
      put("senderId", localUserId)
      put("senderName", localDisplayName)
      put("senderUsername", localUsername)
      put("isHost", localIsHost)
      put("muted", _isMicrophoneMuted.value || !_hasMicrophonePermission.value)
      put("speaking", _isSpeaking.value && !_isMicrophoneMuted.value)
      put("hasMic", _hasMicrophonePermission.value)
    }
    sendRealtimeBroadcast(eventName, payload)
  }

  private fun sendRealtimeBroadcast(subEvent: String, innerData: JSONObject) {
    val ws = webSocket ?: return
    val roomId = activeRoomId
    if (roomId.isBlank()) return
    try {
      innerData.put("senderId", localUserId)
      val msg = JSONObject().apply {
        put("topic", "realtime:unload_voice:$roomId")
        put("event", "broadcast")
        put("ref", System.nanoTime().toString())
        put("payload", JSONObject().apply {
          put("type", "broadcast")
          put("event", subEvent)
          put("payload", innerData)
        })
      }
      ws.send(msg.toString())
    } catch (_: Exception) {}
  }

  private fun startAudioPlaybackTrack() {
    if (audioTrack != null) return
    try {
      val minBuf = AudioTrack.getMinBufferSize(
        SAMPLE_RATE_HZ,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      )
      if (minBuf <= 0) return
      val track = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(maxOf(minBuf * 2, FRAME_SAMPLES * 4))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
      track.play()
      audioTrack = track
    } catch (e: Exception) {
      Log.w(TAG, "AudioTrack init warning: ${e.message}")
    }
  }

  @SuppressLint("MissingPermission")
  private fun startMicrophoneCaptureLoop() {
    if (!checkMicPermission()) return
    if (captureJob?.isActive == true) return

    captureJob = scope.launch {
      try {
        val minBuf = AudioRecord.getMinBufferSize(
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_SAMPLES * 2)

        val record = AudioRecord(
          MediaRecorder.AudioSource.VOICE_COMMUNICATION,
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          maxOf(minBuf * 2, FRAME_SAMPLES * 4)
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
          record.release()
          _voiceStatusText.value = "Listening live (Mic unavailable on device)"
          return@launch
        }

        record.startRecording()
        audioRecord = record

        val pcmBuffer = ShortArray(FRAME_SAMPLES)
        val ulawBuffer = ByteArray(FRAME_SAMPLES)

        while (isActive && isRunning.get()) {
          if (_isMicrophoneMuted.value) {
            if (_isSpeaking.value || _localAudioLevel.value > 0f) {
              _localAudioLevel.value = 0f
              _isSpeaking.value = false
              updateLocalParticipant { it.copy(isSpeaking = false, isMuted = true) }
            }
            delay(80L)
            continue
          }

          val readCount = record.read(pcmBuffer, 0, FRAME_SAMPLES)
          if (readCount > 0) {
            var sumSq = 0.0
            for (i in 0 until readCount) {
              val s = pcmBuffer[i].toDouble()
              sumSq += s * s
            }
            val rms = sqrt(sumSq / readCount)
            val normalized = (rms / 14000.0).toFloat().coerceIn(0f, 1f)
            val speakingNow = normalized > 0.032f

            _localAudioLevel.value = normalized
            val prevSpeaking = _isSpeaking.value
            _isSpeaking.value = speakingNow

            if (prevSpeaking != speakingNow) {
              updateLocalParticipant {
                it.copy(
                  isSpeaking = speakingNow,
                  isMuted = false,
                  lastSpokeMs = if (speakingNow) System.currentTimeMillis() else it.lastSpokeMs
                )
              }
            }

            // Stream live µ-law voice packet when speaking or trailing speech tail
            if ((speakingNow || prevSpeaking) && _isChannelConnected.value) {
              for (i in 0 until readCount) {
                ulawBuffer[i] = linearPcmToMuLaw(pcmBuffer[i].toInt())
              }
              val encoded = Base64.encodeToString(ulawBuffer, 0, readCount, Base64.NO_WRAP)
              val packet = JSONObject().apply {
                put("pcm", encoded)
                put("senderName", localDisplayName)
                put("senderUsername", localUsername)
                put("isHost", localIsHost)
              }
              sendRealtimeBroadcast("AUDIO_FRAME", packet)
            }
          } else {
            delay(35L)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Unload mic capture warning: ${e.message}")
      }
    }
  }

  private fun playMuLawFrame(ulawBytes: ByteArray) {
    val track = audioTrack ?: return
    if (!isRunning.get() || ulawBytes.isEmpty()) return
    try {
      val pcmShorts = ShortArray(ulawBytes.size)
      for (i in ulawBytes.indices) {
        pcmShorts[i] = muLawToLinearPcm(ulawBytes[i])
      }
      track.write(pcmShorts, 0, pcmShorts.size)
    } catch (_: Exception) {}
  }

  fun toggleMute(): Boolean {
    val next = !_isMicrophoneMuted.value
    setMuted(next)
    return next
  }

  fun setMuted(muted: Boolean) {
    _isMicrophoneMuted.value = muted
    if (muted) {
      _localAudioLevel.value = 0f
      _isSpeaking.value = false
    }
    updateLocalParticipant {
      it.copy(
        isMuted = muted || !_hasMicrophonePermission.value,
        isSpeaking = if (muted) false else it.isSpeaking
      )
    }
    if (isRunning.get()) {
      _voiceStatusText.value = when {
        !_hasMicrophonePermission.value -> "Listening live (Grant mic to speak)"
        muted -> "Live room connected • Mic muted"
        _isChannelConnected.value -> "Live voice connected"
        else -> "Connecting live audio..."
      }
      broadcastPresenceEvent("PARTICIPANT_UPDATE")
    }
  }

  fun toggleSpeakerphone(): Boolean {
    val next = !_isSpeakerphoneOn.value
    _isSpeakerphoneOn.value = next
    configureAudioRouting(enableCallMode = isRunning.get(), speakerOn = next)
    return next
  }

  fun stopVoiceSession() {
    if (isRunning.getAndSet(false)) {
      broadcastPresenceEvent("PARTICIPANT_LEAVE")
    }
    _isVoiceActive.value = false
    _isChannelConnected.value = false
    _isSpeaking.value = false
    _localAudioLevel.value = 0f
    _voiceStatusText.value = "Disconnected"
    _iceDiagnosticsText.value = ""
    activeRoomId = ""

    reconnectJob?.cancel()
    reconnectJob = null
    heartbeatJob?.cancel()
    heartbeatJob = null
    captureJob?.cancel()
    captureJob = null

    participantMap.clear()
    _participants.value = emptyList()

    try {
      audioRecord?.stop()
    } catch (_: Exception) {}
    try {
      audioRecord?.release()
    } catch (_: Exception) {}
    audioRecord = null

    try {
      audioTrack?.stop()
    } catch (_: Exception) {}
    try {
      audioTrack?.release()
    } catch (_: Exception) {}
    audioTrack = null

    try {
      webSocket?.close(1000, "Left voice room")
    } catch (_: Exception) {}
    webSocket = null

    configureAudioRouting(enableCallMode = false, speakerOn = false)
    abandonCommunicationAudioFocus()
  }

  private fun updateLocalParticipant(transform: (UnloadVoiceParticipant) -> UnloadVoiceParticipant) {
    val id = localUserId
    if (id.isBlank()) return
    val current = participantMap[id] ?: UnloadVoiceParticipant(
      userId = id,
      displayName = localDisplayName,
      username = localUsername,
      isHost = localIsHost
    )
    participantMap[id] = transform(current).copy(lastSeenMs = System.currentTimeMillis())
    publishParticipantList()
  }

  private fun publishParticipantList() {
    val sorted = participantMap.values.sortedWith(
      compareByDescending<UnloadVoiceParticipant> { it.isHost }
        .thenByDescending { it.userId == localUserId }
        .thenByDescending { it.isSpeaking }
        .thenBy { it.displayName.lowercase() }
    )
    _participants.value = sorted
  }

  private fun requestCommunicationAudioFocus() {
    val am = audioManager ?: return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          .setAcceptsDelayedFocusGain(true)
          .setOnAudioFocusChangeListener { }
          .build()
        audioFocusRequest = req
        am.requestAudioFocus(req)
      } else {
        @Suppress("DEPRECATION")
        am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      }
    } catch (_: Exception) {}
  }

  private fun abandonCommunicationAudioFocus() {
    val am = audioManager ?: return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
      } else {
        @Suppress("DEPRECATION")
        am.abandonAudioFocus(null)
      }
    } catch (_: Exception) {}
  }

  private fun configureAudioRouting(enableCallMode: Boolean, speakerOn: Boolean) {
    val am = audioManager ?: return
    try {
      if (enableCallMode) {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val devices = am.availableCommunicationDevices
          val externalHeadset = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
              it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
              it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
              it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
              it.type == AudioDeviceInfo.TYPE_USB_HEADSET
          }
          val targetDevice = if (!speakerOn && externalHeadset != null) {
            externalHeadset
          } else {
            val targetType = if (speakerOn) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            devices.firstOrNull { it.type == targetType } ?: externalHeadset
          }
          if (targetDevice != null) {
            am.setCommunicationDevice(targetDevice)
          }
        } else {
          @Suppress("DEPRECATION")
          am.isSpeakerphoneOn = speakerOn
        }
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          am.clearCommunicationDevice()
        } else {
          @Suppress("DEPRECATION")
          am.isSpeakerphoneOn = false
        }
        am.mode = AudioManager.MODE_NORMAL
      }
    } catch (_: Exception) {}
  }

  private fun discoverLocalIpv4(): String? {
    return try {
      NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
    } catch (_: Exception) {
      null
    }
  }

  private fun queryStunReflexiveAddress(stunUri: String): Pair<String, Int>? {
    return try {
      val cleaned = stunUri.removePrefix("stun:").removePrefix("stuns:").trim()
      val host = cleaned.substringBefore(':')
      val port = cleaned.substringAfter(':', "19302").toIntOrNull() ?: 19302
      if (host.isBlank()) return null

      val txId = ByteArray(12).also { SecureRandom().nextBytes(it) }
      val req = ByteBuffer.allocate(20).apply {
        putShort(0x0001)
        putShort(0x0000)
        putInt(0x2112A442)
        put(txId)
      }.array()

      DatagramSocket().use { socket ->
        socket.soTimeout = 1200
        val addr = InetAddress.getByName(host)
        socket.send(DatagramPacket(req, req.size, addr, port))
        val respBuf = ByteArray(128)
        val respPkt = DatagramPacket(respBuf, respBuf.size)
        socket.receive(respPkt)
        parseStunXorMappedAddress(respBuf, respPkt.length)
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun parseStunXorMappedAddress(buf: ByteArray, len: Int): Pair<String, Int>? {
    if (len < 20) return null
    val bb = ByteBuffer.wrap(buf, 0, len)
    val msgType = bb.short.toInt() and 0xFFFF
    if (msgType != 0x0101) return null
    val msgLen = bb.short.toInt() and 0xFFFF
    val cookie = bb.int
    bb.position(20)

    val end = minOf(len, 20 + msgLen)
    while (bb.position() + 4 <= end) {
      val attrType = bb.short.toInt() and 0xFFFF
      val attrLen = bb.short.toInt() and 0xFFFF
      if (bb.position() + attrLen > end) break
      if (attrType == 0x0020 && attrLen >= 8) {
        bb.get()
        val family = bb.get().toInt() and 0xFF
        val xPort = (bb.short.toInt() and 0xFFFF) xor ((cookie ushr 16) and 0xFFFF)
        if (family == 0x01) {
          val xIp = bb.int xor cookie
          val ipStr = "${(xIp ushr 24) and 0xFF}.${(xIp ushr 16) and 0xFF}.${(xIp ushr 8) and 0xFF}.${xIp and 0xFF}"
          return Pair(ipStr, xPort)
        }
      } else {
        val padded = (attrLen + 3) and -4
        bb.position((bb.position() + padded).coerceAtMost(end))
      }
    }
    return null
  }

  private fun linearPcmToMuLaw(sampleIn: Int): Byte {
    var sample = sampleIn.coerceIn(-32768, 32767)
    val sign = if (sample < 0) {
      sample = -sample
      0x80
    } else 0
    if (sample > 32635) sample = 32635
    sample += 0x84
    var exponent = 7
    var expMask = 0x4000
    while (exponent > 0 && (sample and expMask) == 0) {
      exponent--
      expMask = expMask shr 1
    }
    val mantissa = (sample shr (exponent + 3)) and 0x0F
    val ulaw = (sign or (exponent shl 4) or mantissa).inv() and 0xFF
    return ulaw.toByte()
  }

  private fun muLawToLinearPcm(ulawByte: Byte): Short {
    val u = ulawByte.toInt().inv() and 0xFF
    val sign = u and 0x80
    val exponent = (u shr 4) and 0x07
    val mantissa = u and 0x0F
    var sample = ((mantissa shl 3) + 0x84) shl exponent
    sample -= 0x84
    return (if (sign != 0) -sample else sample).coerceIn(-32768, 32767).toShort()
  }
}

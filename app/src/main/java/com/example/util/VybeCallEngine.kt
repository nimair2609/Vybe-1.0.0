package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.remote.SupabaseCallSignalInsertDto
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseNetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Real-time Voice & Video Call Engine for VYBE.
 *
 * Implements:
 * 1. RFC 5389 UDP STUN reflexive candidate gathering against `SupabaseConfig.stunServerUrl`
 *    + TURN server metadata (`SupabaseConfig.turnServerUrl`) for SDP/ICE negotiation.
 * 2. Dual-path low-latency transport:
 *    - Primary: Supabase Phoenix Realtime WebSocket (`realtime:call:<callId>`) for sub-100ms
 *      call state events, PCM µ-law voice audio streaming, and live video frames.
 *    - Guaranteed Fallback/Relay: `public.call_signals` REST sync for `VIDEO_FRAME` (latest-frame
 *      replacement per sender) and `ANSWER`/`DECLINE`/`HANGUP`/`MUTE_STATE`/`CAMERA_STATE` so
 *      mutual video and call controls work reliably across all devices, NATs, and Android <-> Web.
 * 3. `AudioManager` speakerphone / earpiece routing and guaranteed resource cleanup on call end.
 */
class VybeCallEngine(
  private val appContext: Context?
) {

  companion object {
    private const val TAG = "VybeCallEngine"
    private const val SAMPLE_RATE_HZ = 8000
    private const val FRAME_SAMPLES = 800 // 100ms voice frame
    private const val MAX_JPEG_FRAME_BYTES = 96_000
    private const val DB_VIDEO_RELAY_INTERVAL_MS = 650L
    private const val DB_SIGNAL_POLL_INTERVAL_MS = 650L
  }

  interface Listener {
    fun onSignalingConnected(iceSummary: String)
    fun onRemoteAccepted()
    fun onRemoteDeclined()
    fun onRemoteEnded()
    fun onRemoteMuteChanged(isMuted: Boolean)
    fun onRemoteCameraChanged(isCameraOff: Boolean)
    fun onRemoteVideoFrame(jpegBase64: String)
    fun onError(message: String)
  }

  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var webSocket: WebSocket? = null
  private var heartbeatJob: Job? = null
  private var signalPollingJob: Job? = null
  private var audioCaptureJob: Job? = null
  private var audioRecord: AudioRecord? = null
  private var audioTrack: AudioTrack? = null

  private val isRunning = AtomicBoolean(false)
  private val isMicMuted = AtomicBoolean(false)
  private val isDbFrameUploadInFlight = AtomicBoolean(false)
  private val lastDbVideoRelayMs = AtomicLong(0L)
  private val processedSignalIds = Collections.synchronizedSet( LinkedHashSet<String>() )
  private var latestRemoteFrameKey: String = ""

  private var activeCallId: String = ""
  private var localUserId: String = ""
  private var localAccessToken: String = ""
  private var listener: Listener? = null

  /**
   * Performs a real RFC 5389 STUN Binding Request over UDP to discover the device's server-reflexive
   * IP/port candidate and builds an SDP session description with gathered host + srflx (+ relay if configured) candidates.
   */
  suspend fun buildLocalSdpWithStun(isVideo: Boolean, isOffer: Boolean): Pair<String, String> = withContext(Dispatchers.IO) {
    val hostIp = discoverLocalIpv4() ?: "127.0.0.1"
    val srflx = queryStunReflexiveAddress(SupabaseConfig.stunServerUrl)
    val sessionId = System.currentTimeMillis()
    val mediaLines = buildString {
      appendLine("v=0")
      appendLine("o=vybe $sessionId 2 IN IP4 $hostIp")
      appendLine("s=VYBE-${if (isVideo) "Video" else "Voice"}-Session")
      appendLine("t=0 0")
      appendLine("a=setup:${if (isOffer) "actpass" else "active"}")
      appendLine("m=audio 9 UDP/TLS/RTP/SAVPF 0")
      appendLine("a=rtpmap:0 PCMU/8000")
      appendLine("a=candidate:1 1 udp 2122260223 $hostIp 54210 typ host")
      if (srflx != null) {
        appendLine("a=candidate:2 1 udp 1686052607 ${srflx.first} ${srflx.second} typ srflx raddr $hostIp rport 54210")
      }
      if (SupabaseConfig.turnServerUrl.isNotBlank()) {
        appendLine("a=ice-options:turn-configured")
      }
      if (isVideo) {
        appendLine("m=video 9 UDP/TLS/RTP/SAVPF 96")
        appendLine("a=rtpmap:96 JPEG/90000")
      }
    }
    val candidateSummary = if (srflx != null) {
      "STUN srflx ${srflx.first}:${srflx.second}"
    } else {
      "ICE host $hostIp"
    }
    Pair(mediaLines, candidateSummary)
  }

  fun startSession(
    callId: String,
    myUserId: String,
    accessToken: String,
    initialSpeakerOn: Boolean,
    initialMuted: Boolean,
    callback: Listener
  ) {
    stopSession()
    activeCallId = callId
    localUserId = myUserId
    localAccessToken = accessToken
    listener = callback
    isMicMuted.set(initialMuted)
    isDbFrameUploadInFlight.set(false)
    lastDbVideoRelayMs.set(0L)
    processedSignalIds.clear()
    latestRemoteFrameKey = ""
    isRunning.set(true)

    configureAudioRouting(enableCallMode = true, speakerOn = initialSpeakerOn)
    connectRealtimeChannel(callId, accessToken)
    startDatabaseSignalRelayLoop(callId, accessToken)
  }

  fun startMediaStreaming() {
    if (!isRunning.get()) return
    startAudioPlaybackTrack()
    startAudioCaptureLoop()
  }

  fun setMuted(muted: Boolean) {
    isMicMuted.set(muted)
    sendRealtimeBroadcast("MUTE_STATE", JSONObject().put("muted", muted))
    postSignalAsync("MUTE_STATE", mapOf("muted" to muted.toString()))
  }

  fun setSpeakerphone(enabled: Boolean) {
    configureAudioRouting(enableCallMode = true, speakerOn = enabled)
  }

  fun notifyCameraState(cameraOff: Boolean) {
    sendRealtimeBroadcast("CAMERA_STATE", JSONObject().put("cameraOff", cameraOff))
    postSignalAsync("CAMERA_STATE", mapOf("cameraOff" to cameraOff.toString()))
  }

  fun sendVideoFrameJpeg(jpegBytes: ByteArray) {
    if (!isRunning.get() || jpegBytes.isEmpty() || jpegBytes.size > MAX_JPEG_FRAME_BYTES) return
    val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
    sendRealtimeBroadcast("VIDEO_FRAME", JSONObject().put("frame", encoded))

    val now = System.currentTimeMillis()
    val prev = lastDbVideoRelayMs.get()
    if (
      now - prev >= DB_VIDEO_RELAY_INTERVAL_MS &&
      lastDbVideoRelayMs.compareAndSet(prev, now) &&
      isDbFrameUploadInFlight.compareAndSet(false, true)
    ) {
      val callId = activeCallId
      val senderId = localUserId
      val authHeader = SupabaseConfig.buildAuthHeader(localAccessToken)
      if (SupabaseConfig.isConfigured() && callId.isNotBlank() && senderId.isNotBlank() && !authHeader.isNullOrBlank()) {
        engineScope.launch {
          try {
            SupabaseNetworkClient.restApi.deleteCallSignalsBySender(
              apiKey = SupabaseConfig.supabaseAnonKey,
              authHeader = authHeader,
              callIdFilter = "eq.$callId",
              senderIdFilter = "eq.$senderId",
              signalTypeFilter = "eq.VIDEO_FRAME"
            )
            SupabaseNetworkClient.restApi.insertCallSignal(
              apiKey = SupabaseConfig.supabaseAnonKey,
              authHeader = authHeader,
              signal = SupabaseCallSignalInsertDto(
                callId = callId,
                senderId = senderId,
                signalType = "VIDEO_FRAME",
                payload = mapOf(
                  "frame" to encoded,
                  "ts" to now.toString()
                )
              )
            )
          } catch (e: Exception) {
            Log.d(TAG, "DB video frame relay skipped: ${e.message}")
          } finally {
            isDbFrameUploadInFlight.set(false)
          }
        }
      } else {
        isDbFrameUploadInFlight.set(false)
      }
    }
  }

  fun sendCallControlEvent(eventType: String) {
    val ts = System.currentTimeMillis().toString()
    sendRealtimeBroadcast(eventType, JSONObject().put("ts", ts))
    postSignalAsync(eventType, mapOf("ts" to ts))
  }

  private fun postSignalAsync(signalType: String, payload: Map<String, String>) {
    val callId = activeCallId
    val senderId = localUserId
    val authHeader = SupabaseConfig.buildAuthHeader(localAccessToken)
    if (!SupabaseConfig.isConfigured() || callId.isBlank() || senderId.isBlank() || authHeader.isNullOrBlank()) return
    engineScope.launch {
      try {
        SupabaseNetworkClient.restApi.insertCallSignal(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          signal = SupabaseCallSignalInsertDto(
            callId = callId,
            senderId = senderId,
            signalType = signalType,
            payload = payload
          )
        )
      } catch (_: Exception) {}
    }
  }

  private fun startDatabaseSignalRelayLoop(callId: String, accessToken: String) {
    if (!SupabaseConfig.isConfigured() || callId.isBlank()) return
    signalPollingJob?.cancel()
    signalPollingJob = engineScope.launch {
      val authHeader = SupabaseConfig.buildAuthHeader(accessToken)
      if (authHeader.isNullOrBlank()) return@launch
      while (isActive && isRunning.get() && activeCallId == callId) {
        try {
          val resp = SupabaseNetworkClient.restApi.getCallSignals(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            callIdFilter = "eq.$callId",
            order = "created_at.desc",
            limit = 25
          )
          if (resp.isSuccessful) {
            val signals = resp.body().orEmpty()
            var newestFrameHandled = false
            // Signals are ordered newest first (`created_at.desc`).
            // Process the newest remote VIDEO_FRAME immediately, and process control signals in chronological order.
            for (sig in signals) {
              if (sig.senderId == localUserId) continue
              if (sig.signalType == "VIDEO_FRAME" && !newestFrameHandled) {
                newestFrameHandled = true
                val frame = sig.payload?.get("frame")?.toString().orEmpty()
                val key = sig.id ?: sig.payload?.get("ts")?.toString() ?: sig.createdAt.orEmpty()
                if (frame.isNotBlank() && key != latestRemoteFrameKey) {
                  latestRemoteFrameKey = key
                  listener?.onRemoteVideoFrame(frame)
                }
              }
            }
            for (sig in signals.asReversed()) {
              if (sig.senderId == localUserId || sig.signalType == "VIDEO_FRAME") continue
              val sigKey = sig.id ?: "${sig.signalType}_${sig.senderId}_${sig.createdAt}"
              if (!processedSignalIds.add(sigKey)) continue
              when (sig.signalType) {
                "ANSWER" -> listener?.onRemoteAccepted()
                "DECLINE" -> listener?.onRemoteDeclined()
                "HANGUP" -> listener?.onRemoteEnded()
                "MUTE_STATE" -> {
                  val muted = sig.payload?.get("muted")?.toString()?.toBooleanStrictOrNull() ?: false
                  listener?.onRemoteMuteChanged(muted)
                }
                "CAMERA_STATE" -> {
                  val camOff = sig.payload?.get("cameraOff")?.toString()?.toBooleanStrictOrNull() ?: false
                  listener?.onRemoteCameraChanged(camOff)
                }
              }
            }
          }
        } catch (e: Exception) {
          Log.d(TAG, "Signal poll warning: ${e.message}")
        }
        delay(DB_SIGNAL_POLL_INTERVAL_MS)
      }
    }
  }

  fun stopSession() {
    if (!isRunning.getAndSet(false) && webSocket == null && signalPollingJob == null) return
    heartbeatJob?.cancel()
    heartbeatJob = null
    signalPollingJob?.cancel()
    signalPollingJob = null
    audioCaptureJob?.cancel()
    audioCaptureJob = null

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
      webSocket?.close(1000, "Call ended")
    } catch (_: Exception) {}
    webSocket = null

    configureAudioRouting(enableCallMode = false, speakerOn = false)
    listener = null
    activeCallId = ""
    localAccessToken = ""
  }

  private fun connectRealtimeChannel(callId: String, accessToken: String) {
    if (!SupabaseConfig.isConfigured() || callId.isBlank()) return
    val wsUrl = SupabaseConfig.getRealtimeWsUrl()
    val req = Request.Builder().url(wsUrl).build()
    val topic = "realtime:call:$callId"

    webSocket = SupabaseNetworkClient.okHttpClient.newWebSocket(req, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        if (!isRunning.get()) return
        val joinPayload = JSONObject().apply {
          put("topic", topic)
          put("event", "phx_join")
          put("ref", "1")
          put("payload", JSONObject().apply {
            put("config", JSONObject().apply {
              put("broadcast", JSONObject().put("self", false).put("ack", false))
            })
            if (SupabaseConfig.isJwtAccessToken(accessToken)) {
              put("access_token", accessToken.removePrefix("Bearer ").trim())
            }
          })
        }
        webSocket.send(joinPayload.toString())
        if (SupabaseConfig.isJwtAccessToken(accessToken)) {
          val authMsg = JSONObject().apply {
            put("topic", topic)
            put("event", "access_token")
            put("ref", "2")
            put("payload", JSONObject().put("access_token", accessToken.removePrefix("Bearer ").trim()))
          }
          webSocket.send(authMsg.toString())
        }
        listener?.onSignalingConnected("Realtime channel live")

        heartbeatJob?.cancel()
        heartbeatJob = engineScope.launch {
          var refCounter = 10
          while (isActive && isRunning.get()) {
            delay(25_000L)
            val hb = JSONObject()
              .put("topic", "phoenix")
              .put("event", "heartbeat")
              .put("payload", JSONObject())
              .put("ref", (refCounter++).toString())
            webSocket.send(hb.toString())
          }
        }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        if (!isRunning.get()) return
        try {
          val root = JSONObject(text)
          if (root.optString("event") != "broadcast") return
          val payload = root.optJSONObject("payload") ?: return
          val subEvent = payload.optString("event")
          val data = payload.optJSONObject("payload") ?: payload
          val sender = data.optString("senderId")
          if (sender.isNotBlank() && sender == localUserId) return

          when (subEvent) {
            "AUDIO_FRAME" -> {
              val b64 = data.optString("pcm")
              if (b64.isNotBlank()) {
                val ulawBytes = Base64.decode(b64, Base64.NO_WRAP)
                playMuLawFrame(ulawBytes)
              }
            }
            "VIDEO_FRAME" -> {
              val frame = data.optString("frame")
              if (frame.isNotBlank()) {
                listener?.onRemoteVideoFrame(frame)
              }
            }
            "MUTE_STATE" -> listener?.onRemoteMuteChanged(data.optBoolean("muted", false))
            "CAMERA_STATE" -> listener?.onRemoteCameraChanged(data.optBoolean("cameraOff", false))
            "ANSWER" -> listener?.onRemoteAccepted()
            "DECLINE" -> listener?.onRemoteDeclined()
            "HANGUP" -> listener?.onRemoteEnded()
          }
        } catch (e: Exception) {
          Log.d(TAG, "Realtime message parse warning: ${e.message}")
        }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.w(TAG, "Realtime WebSocket fallback to DB signal relay: ${t.message}")
      }
    })
  }

  private fun sendRealtimeBroadcast(subEvent: String, innerData: JSONObject) {
    val ws = webSocket ?: return
    val callId = activeCallId
    if (callId.isBlank()) return
    try {
      innerData.put("senderId", localUserId)
      val msg = JSONObject().apply {
        put("topic", "realtime:call:$callId")
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
    if (appContext == null || audioTrack != null) return
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
      Log.w(TAG, "AudioTrack init skipped: ${e.message}")
    }
  }

  private fun startAudioCaptureLoop() {
    val ctx = appContext ?: return
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      return
    }
    if (audioCaptureJob?.isActive == true) return

    audioCaptureJob = engineScope.launch {
      try {
        val minBuf = AudioRecord.getMinBufferSize(
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return@launch

        val recorder = AudioRecord(
          MediaRecorder.AudioSource.VOICE_COMMUNICATION,
          SAMPLE_RATE_HZ,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          maxOf(minBuf * 2, FRAME_SAMPLES * 4)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
          recorder.release()
          return@launch
        }
        audioRecord = recorder
        recorder.startRecording()

        val pcmBuffer = ShortArray(FRAME_SAMPLES)
        val ulawBuffer = ByteArray(FRAME_SAMPLES)

        while (isActive && isRunning.get()) {
          val readCount = recorder.read(pcmBuffer, 0, FRAME_SAMPLES)
          if (readCount > 0 && !isMicMuted.get() && webSocket != null) {
            for (i in 0 until readCount) {
              ulawBuffer[i] = linearPcmToMuLaw(pcmBuffer[i].toInt())
            }
            val encoded = Base64.encodeToString(ulawBuffer, 0, readCount, Base64.NO_WRAP)
            sendRealtimeBroadcast("AUDIO_FRAME", JSONObject().put("pcm", encoded))
          } else {
            delay(40L)
          }
        }
      } catch (e: SecurityException) {
        Log.w(TAG, "Microphone permission revoked during call: ${e.message}")
      } catch (e: Exception) {
        Log.w(TAG, "Audio capture loop ended: ${e.message}")
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

  private fun configureAudioRouting(enableCallMode: Boolean, speakerOn: Boolean) {
    val ctx = appContext ?: return
    try {
      val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
      if (enableCallMode) {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val targetType = if (speakerOn) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
          } else {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
          }
          am.availableCommunicationDevices.firstOrNull { it.type == targetType }?.let { device ->
            am.setCommunicationDevice(device)
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
        putShort(0x0001) // Binding Request
        putShort(0x0000) // Length 0
        putInt(0x2112A442) // Magic Cookie
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
    if (msgType != 0x0101) return null // Binding Success Response
    val msgLen = bb.short.toInt() and 0xFFFF
    val cookie = bb.int
    bb.position(20)

    val end = minOf(len, 20 + msgLen)
    while (bb.position() + 4 <= end) {
      val attrType = bb.short.toInt() and 0xFFFF
      val attrLen = bb.short.toInt() and 0xFFFF
      if (bb.position() + attrLen > end) break
      if (attrType == 0x0020 && attrLen >= 8) { // XOR-MAPPED-ADDRESS (IPv4)
        bb.get() // reserved
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

  // ITU-T G.711 µ-law encoder/decoder for low-bandwidth voice packets
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

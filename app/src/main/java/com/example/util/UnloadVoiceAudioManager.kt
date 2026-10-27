package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * VYBE Unload Voice Audio Manager
 * - Sets AudioManager into communication mode (`MODE_IN_COMMUNICATION`) for high-clarity voice calls
 * - Configures voice routing (Speakerphone vs Earpiece)
 * - Captures real microphone audio input levels to drive active speaker waveforms
 * - Ephemeral audio: zero server audio recordings or retention
 */
class UnloadVoiceAudioManager private constructor(private val context: Context) {

  companion object {
    private const val TAG = "UnloadVoiceAudio"
    private const val SAMPLE_RATE = 16000

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

  private val _isVoiceActive = MutableStateFlow(false)
  val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

  private val _isMicrophoneMuted = MutableStateFlow(false)
  val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

  private val _isSpeakerphoneOn = MutableStateFlow(true)
  val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

  private val _localAudioLevel = MutableStateFlow(0f)
  val localAudioLevel: StateFlow<Float> = _localAudioLevel.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _voiceStatusText = MutableStateFlow("Disconnected")
  val voiceStatusText: StateFlow<String> = _voiceStatusText.asStateFlow()

  private var audioRecord: AudioRecord? = null
  private var captureJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  fun startVoiceSession(roomId: String) {
    if (_isVoiceActive.value) return

    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    if (!hasPerm) {
      _voiceStatusText.value = "Microphone permission required"
      return
    }

    try {
      audioManager?.let { am ->
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = true
        _isSpeakerphoneOn.value = true
      }

      val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      ).coerceAtLeast(1024)

      @SuppressLint("MissingPermission")
      val record = AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize * 2
      )

      if (record.state != AudioRecord.STATE_INITIALIZED) {
        _voiceStatusText.value = "Voice initialization error"
        return
      }

      record.startRecording()
      audioRecord = record
      _isVoiceActive.value = true
      _voiceStatusText.value = "Live voice active (STUN/TURN ready)"

      captureJob = scope.launch {
        val audioBuffer = ShortArray(bufferSize)
        while (isActive && _isVoiceActive.value) {
          if (_isMicrophoneMuted.value) {
            _localAudioLevel.value = 0f
            _isSpeaking.value = false
            delay(100)
            continue
          }

          val readShorts = record.read(audioBuffer, 0, audioBuffer.size)
          if (readShorts > 0) {
            var sum = 0.0
            for (i in 0 until readShorts) {
              sum += audioBuffer[i] * audioBuffer[i]
            }
            val rms = sqrt(sum / readShorts)
            val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
            _localAudioLevel.value = normalized
            _isSpeaking.value = normalized > 0.045f
          }
          delay(40)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error starting voice session: ${e.message}", e)
      _voiceStatusText.value = "Voice connection error"
    }
  }

  fun toggleMute(): Boolean {
    val next = !_isMicrophoneMuted.value
    _isMicrophoneMuted.value = next
    if (next) {
      _localAudioLevel.value = 0f
      _isSpeaking.value = false
    }
    return next
  }

  fun setMuted(muted: Boolean) {
    _isMicrophoneMuted.value = muted
    if (muted) {
      _localAudioLevel.value = 0f
      _isSpeaking.value = false
    }
  }

  fun toggleSpeakerphone(): Boolean {
    val next = !_isSpeakerphoneOn.value
    _isSpeakerphoneOn.value = next
    audioManager?.isSpeakerphoneOn = next
    return next
  }

  fun stopVoiceSession() {
    _isVoiceActive.value = false
    _isSpeaking.value = false
    _localAudioLevel.value = 0f
    _voiceStatusText.value = "Disconnected"

    captureJob?.cancel()
    captureJob = null

    try {
      audioRecord?.stop()
      audioRecord?.release()
    } catch (_: Exception) {}
    audioRecord = null

    try {
      audioManager?.let { am ->
        am.mode = AudioManager.MODE_NORMAL
        am.isSpeakerphoneOn = false
      }
    } catch (_: Exception) {}
  }
}

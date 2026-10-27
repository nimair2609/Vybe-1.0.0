package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Coordinates Feed audio playback across video posts (`MediaPlayer`) and music-enabled photo/text posts (`AudioTrack`).
 * Guarantees:
 * 1. Once enabled, Feed audio remains enabled across scrolled items until switched off.
 * 2. Strictly ONE item can produce sound at any instant (zero overlapping audio).
 * 3. Uses STREAM_MUSIC / USAGE_MEDIA so physical volume buttons control volume natively.
 */
object FeedAudioController {

  val PRESET_TRACKS = listOf(
    "Midnight Pulse — VYBE Originals",
    "Golden Hour Drift — Ambient Synth",
    "Neon Horizon — Chillwave",
    "Rainy Cafe Lo-Fi — Studio Cut",
    "Velvet Nights — Soul Instrumental"
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Any()

  @Volatile
  private var activeOwnerPostId: String? = null
  private var activeMediaPlayer: MediaPlayer? = null
  private var synthJob: Job? = null
  private var currentAudioTrack: AudioTrack? = null

  /**
   * Registers a [MediaPlayer] for [postId]. Mutes any previously registered player for a different post
   * and halts any ambient music track so video + music never overlap.
   */
  fun onVideoAudioStateChanged(postId: String, player: MediaPlayer, shouldPlayAudio: Boolean) {
    synchronized(lock) {
      if (shouldPlayAudio) {
        if (activeOwnerPostId != null && activeOwnerPostId != postId) {
          stopSynthLocked()
          val prevPlayer = activeMediaPlayer
          if (prevPlayer != null && prevPlayer !== player) {
            try {
              prevPlayer.setVolume(0f, 0f)
            } catch (_: Exception) {}
          }
        }
        stopSynthLocked()
        activeOwnerPostId = postId
        activeMediaPlayer = player
        try {
          player.setVolume(1f, 1f)
        } catch (_: Exception) {}
      } else {
        try {
          player.setVolume(0f, 0f)
        } catch (_: Exception) {}
        if (activeMediaPlayer === player) {
          activeMediaPlayer = null
          if (activeOwnerPostId == postId) {
            activeOwnerPostId = null
          }
        }
      }
    }
  }

  /**
   * Starts or stops non-overlapping music playback for a non-video Feed post that has an [audioTrackTitle].
   */
  fun syncPostMusicTrack(postId: String, audioTrackTitle: String?, shouldPlayAudio: Boolean) {
    synchronized(lock) {
      if (!shouldPlayAudio || audioTrackTitle.isNullOrBlank()) {
        if (activeOwnerPostId == postId) {
          stopSynthLocked()
          activeOwnerPostId = null
        }
        return
      }

      if (activeOwnerPostId == postId && synthJob?.isActive == true) {
        return
      }

      // Mute any previously active video player & stop previous ambient track
      activeMediaPlayer?.let { prev ->
        try {
          prev.setVolume(0f, 0f)
        } catch (_: Exception) {}
      }
      activeMediaPlayer = null
      stopSynthLocked()

      activeOwnerPostId = postId
      startAmbientTrackLocked(postId, audioTrackTitle)
    }
  }

  /**
   * Immediately silences all Feed video players and music tracks (e.g. leaving Feed tab or muting).
   */
  fun stopAllFeedAudio() {
    synchronized(lock) {
      activeMediaPlayer?.let { prev ->
        try {
          prev.setVolume(0f, 0f)
        } catch (_: Exception) {}
      }
      activeMediaPlayer = null
      stopSynthLocked()
      activeOwnerPostId = null
    }
  }

  fun stopAll() = stopAllFeedAudio()

  fun playMusicTrack(ownerId: String, trackTitle: String) {
    syncPostMusicTrack(ownerId, trackTitle, shouldPlayAudio = true)
  }

  fun stopMusicTrack(ownerId: String? = null) {
    synchronized(lock) {
      if (ownerId == null || activeOwnerPostId == ownerId) {
        stopSynthLocked()
        if (ownerId == null || activeOwnerPostId == ownerId) {
          activeOwnerPostId = null
        }
      }
    }
  }

  private fun stopSynthLocked() {
    synthJob?.cancel()
    synthJob = null
    val track = currentAudioTrack
    currentAudioTrack = null
    if (track != null) {
      try {
        track.pause()
        track.flush()
        track.release()
      } catch (_: Exception) {}
    }
  }

  private fun startAmbientTrackLocked(postId: String, trackTitle: String) {
    val seed = abs(trackTitle.hashCode())
    val chordSets = listOf(
      doubleArrayOf(220.0, 277.18, 329.63, 415.30), // A maj7
      doubleArrayOf(196.0, 246.94, 293.66, 369.99), // G maj7
      doubleArrayOf(174.61, 220.0, 261.63, 329.63), // F maj7
      doubleArrayOf(261.63, 311.13, 392.00, 466.16), // C min7
      doubleArrayOf(233.08, 293.66, 349.23, 440.00)  // Bb maj7
    )
    val freqs = chordSets[seed % chordSets.size]

    synthJob = scope.launch {
      val sampleRate = 22050
      val minBuf = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      )
      if (minBuf <= 0) return@launch

      var track: AudioTrack? = null
      try {
        track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(minBuf * 2)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()

        synchronized(lock) {
          if (activeOwnerPostId != postId || !isActive) {
            track.release()
            return@launch
          }
          currentAudioTrack = track
        }

        track.play()
        val chunkSamples = sampleRate / 10 // 100ms chunks for instant responsiveness on scroll/mute
        val buffer = ShortArray(chunkSamples)
        var sampleIdx = 0L

        while (isActive && activeOwnerPostId == postId) {
          for (i in 0 until chunkSamples) {
            val t = sampleIdx.toDouble() / sampleRate.toDouble()
            val env = 0.55 + 0.35 * sin(2.0 * PI * 0.22 * t)
            val noteShift = ((t / 2.4).toInt()) % freqs.size
            val f1 = freqs[0]
            val f2 = freqs[1]
            val f3 = freqs[noteShift]
            val wave = (0.42 * sin(2.0 * PI * f1 * t) +
              0.33 * sin(2.0 * PI * f2 * t) +
              0.25 * sin(2.0 * PI * f3 * t)) * env * 0.18
            buffer[i] = (wave * Short.MAX_VALUE).toInt()
              .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
              .toShort()
            sampleIdx++
          }
          val written = track.write(buffer, 0, chunkSamples)
          if (written < 0) break
        }
      } catch (_: Exception) {
        // Expected on Robolectric or devices without audio hardware
      } finally {
        try {
          track?.stop()
          track?.release()
        } catch (_: Exception) {}
      }
    }
  }
}

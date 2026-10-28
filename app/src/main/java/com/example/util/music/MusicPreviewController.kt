package com.example.util.music

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.LicensedMusicTrack
import com.example.data.model.MusicAttachment
import com.example.util.FeedAudioController
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

sealed class MusicPreviewState {
  object Idle : MusicPreviewState()
  data class Buffering(val trackId: String) : MusicPreviewState()
  data class Playing(
    val trackId: String,
    val positionMs: Long,
    val startTimeMs: Long,
    val endTimeMs: Long
  ) : MusicPreviewState()
  data class Paused(val trackId: String, val positionMs: Long) : MusicPreviewState()
  data class Error(val trackId: String, val message: String) : MusicPreviewState()
}

interface PreviewAudioEngine {
  fun prepareAsync(
    streamUrl: String,
    initialVolume: Float,
    onPrepared: (durationMs: Long) -> Unit,
    onError: (String) -> Unit,
    onCompletion: () -> Unit
  )
  fun seekTo(ms: Long)
  fun start()
  fun pause()
  fun setVolume(volume: Float)
  fun currentPositionMs(): Long
  fun isPlaying(): Boolean
  fun release()
}

private class AndroidMediaPlayerPreviewEngine : PreviewAudioEngine {
  private var mediaPlayer: MediaPlayer? = null

  override fun prepareAsync(
    streamUrl: String,
    initialVolume: Float,
    onPrepared: (durationMs: Long) -> Unit,
    onError: (String) -> Unit,
    onCompletion: () -> Unit
  ) {
    release()
    val player = MediaPlayer()
    mediaPlayer = player
    try {
      player.setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      val vol = initialVolume.coerceIn(0f, 1f)
      player.setVolume(vol, vol)
      player.setDataSource(streamUrl)
      player.setOnPreparedListener { mp ->
        val dur = mp.duration.takeIf { it > 0 }?.toLong() ?: 30_000L
        onPrepared(dur)
      }
      player.setOnCompletionListener {
        onCompletion()
      }
      player.setOnErrorListener { _, what, extra ->
        onError("Playback error ($what/$extra). Track stream may be temporarily unavailable.")
        true
      }
      player.prepareAsync()
    } catch (e: Exception) {
      release()
      onError(e.message ?: "Unable to stream audio preview.")
    }
  }

  override fun seekTo(ms: Long) {
    try {
      mediaPlayer?.seekTo(ms.coerceAtLeast(0L).toInt())
    } catch (_: Exception) {}
  }

  override fun start() {
    try {
      mediaPlayer?.start()
    } catch (_: Exception) {}
  }

  override fun pause() {
    try {
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.pause()
      }
    } catch (_: Exception) {}
  }

  override fun setVolume(volume: Float) {
    val v = volume.coerceIn(0f, 1f)
    try {
      mediaPlayer?.setVolume(v, v)
    } catch (_: Exception) {}
  }

  override fun currentPositionMs(): Long {
    return try {
      mediaPlayer?.currentPosition?.coerceAtLeast(0)?.toLong() ?: 0L
    } catch (_: Exception) {
      0L
    }
  }

  override fun isPlaying(): Boolean {
    return try {
      mediaPlayer?.isPlaying == true
    } catch (_: Exception) {
      false
    }
  }

  override fun release() {
    val existing = mediaPlayer
    mediaPlayer = null
    if (existing != null) {
      try {
        existing.setOnPreparedListener(null)
        existing.setOnCompletionListener(null)
        existing.setOnErrorListener(null)
        if (existing.isPlaying) {
          existing.stop()
        }
      } catch (_: Exception) {
      } finally {
        try {
          existing.release()
        } catch (_: Exception) {}
      }
    }
  }
}

/**
 * Lifecycle-safe controller for licensed music track previews and non-rendered attachment sync.
 * Ensures music never overlaps with Feed audio and stops immediately when leaving creation/viewer screens.
 */
object MusicPreviewController {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val lock = Any()

  @Volatile
  private var engineFactory: () -> PreviewAudioEngine = { AndroidMediaPlayerPreviewEngine() }

  private var activeEngine: PreviewAudioEngine? = null
  private var progressJob: Job? = null
  private var activeTrackId: String? = null
  private var activeStreamUrl: String? = null
  private var activeStartMs: Long = 0L
  private var activeEndMs: Long = 15_000L
  private var currentVolume: Float = 0.85f

  private val _state = MutableStateFlow<MusicPreviewState>(MusicPreviewState.Idle)
  val state: StateFlow<MusicPreviewState> = _state.asStateFlow()

  internal fun setEngineFactoryForTesting(factory: () -> PreviewAudioEngine) {
    stop()
    engineFactory = factory
  }

  internal fun resetEngineFactory() {
    stop()
    engineFactory = { AndroidMediaPlayerPreviewEngine() }
  }

  fun playTrackPreview(
    track: LicensedMusicTrack,
    startTimeMs: Long = 0L,
    clipDurationMs: Long = minOf(15_000L, track.durationMs.coerceAtLeast(1_000L)),
    volume: Float = 0.85f
  ) {
    if (!track.isSyncLicensed) {
      stop()
      _state.value = MusicPreviewState.Error(
        trackId = track.id,
        message = "This track lacks verified sync licensing and cannot be played."
      )
      return
    }
    if (!track.isAvailable || track.previewStreamUrl.isNullOrBlank()) {
      stop()
      _state.value = MusicPreviewState.Error(
        trackId = track.id,
        message = "Audio preview is unavailable for this track."
      )
      return
    }
    playSegment(
      trackId = track.id,
      streamUrl = track.previewStreamUrl,
      startTimeMs = startTimeMs,
      clipDurationMs = clipDurationMs,
      volume = volume
    )
  }

  fun playAttachment(attachment: MusicAttachment, muted: Boolean = false) {
    val clean = attachment.sanitized()
    if (clean.streamUrl.isNullOrBlank()) {
      stop()
      return
    }
    playSegment(
      trackId = clean.trackId,
      streamUrl = clean.streamUrl,
      startTimeMs = clean.startTimeMs,
      clipDurationMs = clean.clipDurationMs,
      volume = if (muted) 0f else clean.musicVolume
    )
  }

  fun toggleTrackPreview(
    track: LicensedMusicTrack,
    startTimeMs: Long = 0L,
    clipDurationMs: Long = minOf(15_000L, track.durationMs.coerceAtLeast(1_000L)),
    volume: Float = 0.85f
  ) {
    val cur = _state.value
    if ((cur is MusicPreviewState.Playing || cur is MusicPreviewState.Buffering) && activeTrackId == track.id) {
      stop()
    } else {
      playTrackPreview(track, startTimeMs, clipDurationMs, volume)
    }
  }

  fun togglePreview(
    track: LicensedMusicTrack,
    startTimeMs: Long = 0L,
    clipDurationMs: Long = minOf(15_000L, track.durationMs.coerceAtLeast(1_000L)),
    volume: Float = 0.85f
  ) {
    toggleTrackPreview(track, startTimeMs, clipDurationMs, volume)
  }

  fun isPlayingTrack(trackId: String): Boolean {
    if (trackId.isBlank()) return false
    val cur = _state.value
    return (cur is MusicPreviewState.Playing || cur is MusicPreviewState.Buffering) && activeTrackId == trackId
  }

  fun playSegment(
    trackId: String,
    streamUrl: String,
    startTimeMs: Long = 0L,
    clipDurationMs: Long = 15_000L,
    volume: Float = 0.85f
  ) {
    if (streamUrl.isBlank()) {
      stop()
      _state.value = MusicPreviewState.Error(trackId, "Preview stream URL is unavailable.")
      return
    }

    FeedAudioController.stopAllFeedAudio()

    synchronized(lock) {
      val safeStart = startTimeMs.coerceAtLeast(0L)
      val safeDur = clipDurationMs.coerceIn(1_000L, 60_000L)
      val safeEnd = safeStart + safeDur
      currentVolume = volume.coerceIn(0f, 1f)

      // Fast re-seek if same stream is already prepared
      val existing = activeEngine
      if (existing != null && activeTrackId == trackId && activeStreamUrl == streamUrl &&
        (_state.value is MusicPreviewState.Playing || _state.value is MusicPreviewState.Paused)
      ) {
        activeStartMs = safeStart
        activeEndMs = safeEnd
        existing.setVolume(currentVolume)
        existing.seekTo(safeStart)
        existing.start()
        _state.value = MusicPreviewState.Playing(
          trackId = trackId,
          positionMs = safeStart,
          startTimeMs = safeStart,
          endTimeMs = safeEnd
        )
        startSegmentLoopMonitorLocked(trackId)
        return
      }

      releaseLocked()
      activeTrackId = trackId
      activeStreamUrl = streamUrl
      activeStartMs = safeStart
      activeEndMs = safeEnd
      _state.value = MusicPreviewState.Buffering(trackId)

      val engine = engineFactory()
      activeEngine = engine
      engine.prepareAsync(
        streamUrl = streamUrl,
        initialVolume = currentVolume,
        onPrepared = { detectedDurationMs ->
          synchronized(lock) {
            if (activeEngine !== engine || activeTrackId != trackId) return@synchronized
            val clampedStart = activeStartMs.coerceAtMost((detectedDurationMs - 1_000L).coerceAtLeast(0L))
            val clampedEnd = activeEndMs.coerceIn(clampedStart + 1_000L, detectedDurationMs.coerceAtLeast(clampedStart + 1_000L))
            activeStartMs = clampedStart
            activeEndMs = clampedEnd
            engine.setVolume(currentVolume)
            engine.seekTo(clampedStart)
            engine.start()
            _state.value = MusicPreviewState.Playing(
              trackId = trackId,
              positionMs = clampedStart,
              startTimeMs = clampedStart,
              endTimeMs = clampedEnd
            )
            startSegmentLoopMonitorLocked(trackId)
          }
        },
        onError = { message ->
          synchronized(lock) {
            if (activeEngine !== engine) return@synchronized
            releaseLocked()
            _state.value = MusicPreviewState.Error(trackId, message)
          }
        },
        onCompletion = {
          synchronized(lock) {
            if (activeEngine !== engine || activeTrackId != trackId) return@synchronized
            engine.seekTo(activeStartMs)
            engine.start()
          }
        }
      )
    }
  }

  fun setVolume(volume: Float) {
    synchronized(lock) {
      currentVolume = volume.coerceIn(0f, 1f)
      activeEngine?.setVolume(currentVolume)
    }
  }

  fun updateSegmentBounds(startTimeMs: Long, clipDurationMs: Long) {
    synchronized(lock) {
      val engine = activeEngine ?: return
      val id = activeTrackId ?: return
      val safeStart = startTimeMs.coerceAtLeast(0L)
      val safeEnd = safeStart + clipDurationMs.coerceIn(1_000L, 60_000L)
      activeStartMs = safeStart
      activeEndMs = safeEnd
      if (engine.isPlaying()) {
        engine.seekTo(safeStart)
        _state.value = MusicPreviewState.Playing(
          trackId = id,
          positionMs = safeStart,
          startTimeMs = safeStart,
          endTimeMs = safeEnd
        )
      }
    }
  }

  fun pause() {
    synchronized(lock) {
      progressJob?.cancel()
      progressJob = null
      val engine = activeEngine ?: return
      val id = activeTrackId ?: return
      val pos = engine.currentPositionMs()
      engine.pause()
      _state.value = MusicPreviewState.Paused(id, pos)
    }
  }

  fun resumeIfPaused() {
    synchronized(lock) {
      val cur = _state.value
      val engine = activeEngine ?: return
      val id = activeTrackId ?: return
      if (cur is MusicPreviewState.Paused) {
        engine.setVolume(currentVolume)
        engine.start()
        _state.value = MusicPreviewState.Playing(
          trackId = id,
          positionMs = engine.currentPositionMs(),
          startTimeMs = activeStartMs,
          endTimeMs = activeEndMs
        )
        startSegmentLoopMonitorLocked(id)
      }
    }
  }

  fun stop() {
    synchronized(lock) {
      releaseLocked()
      _state.value = MusicPreviewState.Idle
    }
  }

  private fun startSegmentLoopMonitorLocked(trackId: String) {
    progressJob?.cancel()
    progressJob = scope.launch {
      while (isActive) {
        delay(100L)
        synchronized(lock) {
          val engine = activeEngine
          if (engine == null || activeTrackId != trackId || !engine.isPlaying()) {
            return@launch
          }
          var pos = engine.currentPositionMs()
          if (pos >= activeEndMs || pos < activeStartMs - 250L) {
            engine.seekTo(activeStartMs)
            pos = activeStartMs
          }
          _state.value = MusicPreviewState.Playing(
            trackId = trackId,
            positionMs = pos,
            startTimeMs = activeStartMs,
            endTimeMs = activeEndMs
          )
        }
      }
    }
  }

  private fun releaseLocked() {
    progressJob?.cancel()
    progressJob = null
    activeTrackId = null
    activeStreamUrl = null
    val engine = activeEngine
    activeEngine = null
    engine?.release()
  }
}

/**
 * Binds [MusicPreviewController] to the current Compose host lifecycle so audio preview
 * immediately halts on backgrounding (`ON_PAUSE` / `ON_STOP`) and when leaving the screen.
 */
@Composable
fun BindMusicPreviewLifecycle(stopOnDispose: Boolean = true) {
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, stopOnDispose) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        MusicPreviewController.stop()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      if (stopOnDispose) {
        MusicPreviewController.stop()
      }
    }
  }
}

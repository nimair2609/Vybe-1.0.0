package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.util.FeedAudioController
import com.example.util.VybeCacheManager

enum class VideoFitMode {
  /** Scales proportionally inside container without cropping important content (ideal for landscape/portrait in stories & reels). */
  FIT_CONTAIN,
  /** Fills container proportionally from center without stretching (ideal when container already matches aspect ratio). */
  COVER_PROPORTIONAL
}

/**
 * Reusable, lifecycle-safe video player powered by AndroidX Media3 [ExoPlayer] + shared application-level
 * [VybeCacheManager] (`SimpleCache` + `CacheDataSource` + `LeastRecentlyUsedCacheEvictor`), rendered via `TextureView`
 * so it clips cleanly inside Compose cards, reels, stories, and post-creation previews without `SurfaceView` Z-order boxes.
 *
 * Enforces strict single-video viewport autoplay & zero duplicate downloads:
 * - Replays directly from the shared bounded 500 MB LRU disk cache with canonical cache keys.
 * - Starts automatically when [autoPlay] becomes true (muted by default in feed).
 * - Immediately pauses when [autoPlay] becomes false, [isPausedExternal] becomes true, or lifecycle pauses.
 * - Respects manual user pause ([manuallyPausedByUser]) and system reduced-motion preferences when [respectReducedMotion] is true.
 * - Unregisters and releases Media3 ExoPlayer cleanly on dispose.
 */
@OptIn(UnstableApi::class)
@Composable
fun VybeVideoPlayer(
  videoUrl: String,
  modifier: Modifier = Modifier,
  autoPlay: Boolean = true,
  isLooping: Boolean = true,
  initialMuted: Boolean = false,
  isPausedExternal: Boolean = false,
  respectReducedMotion: Boolean = false,
  fitMode: VideoFitMode = VideoFitMode.FIT_CONTAIN,
  showControls: Boolean = true,
  onAspectRatioDetected: ((Float) -> Unit)? = null,
  onDurationDetected: ((Int) -> Unit)? = null,
  onVideoCompleted: (() -> Unit)? = null,
  onReadyChanged: ((Boolean) -> Unit)? = null,
  onErrorChanged: ((String?) -> Unit)? = null,
  onProgressChanged: ((Int, Int) -> Unit)? = null,
  seekToFraction: Float? = null,
  seekNonce: Int = 0,
  onTapOverlay: (() -> Unit)? = null,
  onDoubleTapOverlay: (() -> Unit)? = null,
  onMuteToggled: ((Boolean) -> Unit)? = null,
  feedAudioOwnerId: String? = null
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val isSystemReducedMotion = remember(context, respectReducedMotion) {
    if (!respectReducedMotion) {
      false
    } else {
      try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
      } catch (_: Exception) {
        false
      }
    }
  }

  var isBuffering by remember(videoUrl) { mutableStateOf(true) }
  var isPrepared by remember(videoUrl) { mutableStateOf(false) }
  var isPlaying by remember(videoUrl) { mutableStateOf(false) }
  var manuallyPausedByUser by remember(videoUrl) { mutableStateOf(false) }
  var isMuted by remember(videoUrl) { mutableStateOf(initialMuted) }
  var playbackError by remember(videoUrl) { mutableStateOf<String?>(null) }
  var retryNonce by remember { mutableIntStateOf(0) }

  // Keep latest state references accessible inside async ExoPlayer callbacks
  val latestAutoPlay by rememberUpdatedState(autoPlay)
  val latestPausedExternal by rememberUpdatedState(isPausedExternal)
  val latestMuted by rememberUpdatedState(isMuted)

  var videoWidth by remember(videoUrl) { mutableIntStateOf(0) }
  var videoHeight by remember(videoUrl) { mutableIntStateOf(0) }
  var viewWidth by remember { mutableIntStateOf(0) }
  var viewHeight by remember { mutableIntStateOf(0) }

  val exoPlayerRef = remember { mutableStateOf<ExoPlayer?>(null) }
  val textureViewRef = remember { mutableStateOf<TextureView?>(null) }
  val activeSurfaceRef = remember { mutableStateOf<Surface?>(null) }
  val boundUrlRef = remember { mutableStateOf<String?>(null) }

  LaunchedEffect(initialMuted) {
    isMuted = initialMuted
    try {
      val player = exoPlayerRef.value
      val vol = if (initialMuted) 0f else 1f
      player?.volume = vol
      if (feedAudioOwnerId != null && player != null) {
        FeedAudioController.onVideoAudioStateChanged(feedAudioOwnerId, player, shouldPlayAudio = isPlaying && !initialMuted)
      }
    } catch (_: Exception) {}
  }

  LaunchedEffect(isPlaying, isMuted, feedAudioOwnerId) {
    val ownerId = feedAudioOwnerId ?: return@LaunchedEffect
    val player = exoPlayerRef.value ?: return@LaunchedEffect
    FeedAudioController.onVideoAudioStateChanged(ownerId, player, shouldPlayAudio = isPlaying && !isMuted)
  }

  LaunchedEffect(isPrepared, isBuffering, playbackError) {
    onReadyChanged?.invoke(isPrepared && !isBuffering && playbackError == null)
  }

  LaunchedEffect(playbackError) {
    onErrorChanged?.invoke(playbackError)
  }

  LaunchedEffect(seekNonce) {
    if (seekNonce > 0 && seekToFraction != null && isPrepared && playbackError == null) {
      val player = exoPlayerRef.value ?: return@LaunchedEffect
      try {
        val dur = player.duration
        if (dur > 0 && dur != C.TIME_UNSET) {
          val targetMs = (dur * seekToFraction.coerceIn(0f, 1f)).toLong()
          player.seekTo(targetMs)
          onProgressChanged?.invoke(targetMs.toInt(), dur.toInt())
        }
      } catch (_: Exception) {}
    }
  }

  LaunchedEffect(isPrepared, isPlaying, videoUrl, onProgressChanged != null) {
    if (onProgressChanged == null || !isPrepared || playbackError != null) return@LaunchedEffect
    while (isPrepared && playbackError == null) {
      val player = exoPlayerRef.value
      if (player != null) {
        try {
          val dur = player.duration
          val pos = player.currentPosition
          if (dur > 0 && dur != C.TIME_UNSET && pos >= 0) {
            onProgressChanged(pos.coerceAtMost(dur).toInt(), dur.toInt())
          }
        } catch (_: Exception) {}
      }
      if (!isPlaying) break
      kotlinx.coroutines.delay(80L)
    }
  }

  fun applyAspectTransform() {
    val tv = textureViewRef.value ?: return
    if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return

    val vw = viewWidth.toFloat()
    val vh = viewHeight.toFloat()
    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
    val viewRatio = vw / vh

    val scaleX: Float
    val scaleY: Float

    if (fitMode == VideoFitMode.COVER_PROPORTIONAL) {
      if (videoRatio > viewRatio) {
        scaleX = videoRatio / viewRatio
        scaleY = 1f
      } else {
        scaleX = 1f
        scaleY = viewRatio / videoRatio
      }
    } else {
      if (videoRatio > viewRatio) {
        scaleX = 1f
        scaleY = viewRatio / videoRatio
      } else {
        scaleX = videoRatio / viewRatio
        scaleY = 1f
      }
    }

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, vw / 2f, vh / 2f)
    tv.setTransform(matrix)
  }

  fun releaseCurrentPlayer() {
    exoPlayerRef.value?.let { old ->
      if (feedAudioOwnerId != null) {
        FeedAudioController.onVideoAudioStateChanged(feedAudioOwnerId, old, shouldPlayAudio = false)
      }
      VybeCacheManager.unregisterActivePlayer(old)
      try {
        old.playWhenReady = false
        old.stop()
      } catch (_: Exception) {}
      try {
        old.clearVideoSurface()
      } catch (_: Exception) {}
      try {
        old.release()
      } catch (_: Exception) {}
    }
    exoPlayerRef.value = null
    boundUrlRef.value = null
    isPrepared = false
    isPlaying = false
  }

  fun startOrResumePlayer(player: ExoPlayer) {
    if (player.playbackState == Player.STATE_ENDED && !isLooping) {
      player.seekTo(0L)
    }
    player.playWhenReady = true
    player.play()
  }

  fun configureAndStartPlayer(surface: Surface, forceReload: Boolean = false) {
    if (!forceReload && boundUrlRef.value == videoUrl && exoPlayerRef.value != null && playbackError == null) {
      return
    }
    releaseCurrentPlayer()
    isBuffering = true
    playbackError = null

    if (videoUrl.isBlank()) {
      isBuffering = false
      playbackError = "Video URL is unavailable."
      return
    }

    try {
      val cacheDataSourceFactory = VybeCacheManager.getCacheDataSourceFactory(context)
      val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)
      val audioAttrs = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .setUsage(C.USAGE_MEDIA)
        .build()

      val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(audioAttrs, false)
        .build()

      VybeCacheManager.registerActivePlayer(player)
      exoPlayerRef.value = player
      boundUrlRef.value = videoUrl

      player.setVideoSurface(surface)
      player.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
      player.volume = if (latestMuted) 0f else 1f

      player.addListener(object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
          val w = videoSize.width
          val h = videoSize.height
          if (w > 0 && h > 0) {
            val adjustedW = (w * videoSize.pixelWidthHeightRatio).toInt().coerceAtLeast(1)
            videoWidth = adjustedW
            videoHeight = h
            val ratio = (adjustedW.toFloat() / h.toFloat()).coerceIn(0.45f, 2.4f)
            onAspectRatioDetected?.invoke(ratio)
            applyAspectTransform()
          }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          when (playbackState) {
            Player.STATE_BUFFERING -> {
              isBuffering = true
            }
            Player.STATE_READY -> {
              isBuffering = false
              val firstReady = !isPrepared
              isPrepared = true
              val dur = player.duration
              if (dur > 0 && dur != C.TIME_UNSET) {
                onDurationDetected?.invoke(dur.toInt())
              }
              player.volume = if (latestMuted) 0f else 1f

              if (firstReady) {
                val isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                val shouldPlayNow = latestAutoPlay &&
                  !latestPausedExternal &&
                  !manuallyPausedByUser &&
                  !isSystemReducedMotion &&
                  isLifecycleResumed
                if (shouldPlayNow) {
                  player.playWhenReady = true
                  player.play()
                } else {
                  player.playWhenReady = false
                }
              }
            }
            Player.STATE_ENDED -> {
              isBuffering = false
              if (!isLooping) {
                isPlaying = false
                player.playWhenReady = false
                onVideoCompleted?.invoke()
              }
            }
            Player.STATE_IDLE -> {
              isPlaying = false
            }
          }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
          isPlaying = playing
          if (playing) {
            isBuffering = false
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          isPrepared = false
          isBuffering = false
          isPlaying = false
          playbackError = error.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unable to play video."
        }
      })

      val cacheKey = VybeCacheManager.stableMediaCacheKey(videoUrl)
      val mediaItem = MediaItem.Builder()
        .setUri(videoUrl)
        .setCustomCacheKey(cacheKey)
        .build()

      val isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
      val initialAutoPlay = latestAutoPlay &&
        !latestPausedExternal &&
        !manuallyPausedByUser &&
        !isSystemReducedMotion &&
        isLifecycleResumed

      player.playWhenReady = initialAutoPlay
      player.setMediaItem(mediaItem)
      player.prepare()
    } catch (_: Exception) {
      isPrepared = false
      isBuffering = false
      isPlaying = false
      playbackError = "Video could not be loaded."
    }
  }

  // React dynamically whenever viewport visibility (`autoPlay`) or external pause changes
  LaunchedEffect(autoPlay, isPausedExternal, isPrepared) {
    if (!autoPlay) {
      manuallyPausedByUser = false
    }
    val player = exoPlayerRef.value ?: return@LaunchedEffect
    if (!isPrepared || playbackError != null) return@LaunchedEffect

    try {
      val shouldPause = !autoPlay || isPausedExternal
      if (shouldPause) {
        if (player.isPlaying || player.playWhenReady) {
          player.playWhenReady = false
          player.pause()
        }
        isPlaying = false
      } else if (!manuallyPausedByUser && !isSystemReducedMotion && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        if (!player.isPlaying) {
          startOrResumePlayer(player)
        }
      }
    } catch (_: Exception) {}
  }

  // React to videoUrl or manual retry changes when surface is already available
  LaunchedEffect(videoUrl, retryNonce) {
    val surface = activeSurfaceRef.value
    if (surface != null && surface.isValid) {
      configureAndStartPlayer(surface, forceReload = retryNonce > 0)
    }
  }

  // Pause cleanly when app moves to background and release decoder on dispose
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      val player = exoPlayerRef.value
      when (event) {
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
          try {
            if (isPrepared && (player?.isPlaying == true || player?.playWhenReady == true)) {
              player.playWhenReady = false
              player.pause()
            }
            isPlaying = false
          } catch (_: Exception) {}
        }
        Lifecycle.Event.ON_RESUME -> {
          try {
            if (isPrepared && latestAutoPlay && !latestPausedExternal && !manuallyPausedByUser && !isSystemReducedMotion && player != null && playbackError == null) {
              if (!player.isPlaying) {
                startOrResumePlayer(player)
              }
            }
          } catch (_: Exception) {}
        }
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      releaseCurrentPlayer()
      activeSurfaceRef.value?.let { s ->
        try { s.release() } catch (_: Exception) {}
      }
      activeSurfaceRef.value = null
    }
  }

  Box(
    modifier = modifier
      .background(Color(0xFF0A0A0F))
      .testTag("vybe_video_player"),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      factory = { ctx ->
        TextureView(ctx).apply {
          isOpaque = false
          textureViewRef.value = this
          surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
              viewWidth = width
              viewHeight = height
              val surface = Surface(st)
              activeSurfaceRef.value = surface
              configureAndStartPlayer(surface)
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
              viewWidth = width
              viewHeight = height
              applyAspectTransform()
            }

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
              releaseCurrentPlayer()
              activeSurfaceRef.value?.let { s ->
                try { s.release() } catch (_: Exception) {}
              }
              activeSurfaceRef.value = null
              return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
          }
        }
      },
      update = { tv ->
        textureViewRef.value = tv
        if (tv.width > 0 && tv.height > 0 && (tv.width != viewWidth || tv.height != viewHeight)) {
          viewWidth = tv.width
          viewHeight = tv.height
          applyAspectTransform()
        }
      },
      modifier = Modifier
        .fillMaxSize()
        .then(
          if (onDoubleTapOverlay != null) {
            Modifier.pointerInput(videoUrl, showControls, onTapOverlay, onDoubleTapOverlay) {
              detectTapGestures(
                onDoubleTap = { onDoubleTapOverlay() },
                onTap = {
                  if (onTapOverlay != null) {
                    onTapOverlay()
                  } else if (showControls) {
                    val player = exoPlayerRef.value
                    if (player != null && isPrepared && playbackError == null) {
                      try {
                        if (player.isPlaying) {
                          player.playWhenReady = false
                          player.pause()
                          manuallyPausedByUser = true
                        } else {
                          manuallyPausedByUser = false
                          startOrResumePlayer(player)
                        }
                      } catch (_: Exception) {}
                    }
                  }
                }
              )
            }
          } else if (onTapOverlay != null) {
            Modifier.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) { onTapOverlay() }
          } else if (showControls) {
            Modifier.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) {
              val player = exoPlayerRef.value
              if (player != null && isPrepared && playbackError == null) {
                try {
                  if (player.isPlaying) {
                    player.playWhenReady = false
                    player.pause()
                    manuallyPausedByUser = true
                  } else {
                    manuallyPausedByUser = false
                    startOrResumePlayer(player)
                  }
                } catch (_: Exception) {}
              }
            }
          } else Modifier
        )
    )

    if (isBuffering && playbackError == null) {
      CircularProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 2.5.dp,
        modifier = Modifier.size(34.dp)
      )
    }

    if (playbackError != null) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .padding(16.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.Black.copy(alpha = 0.72f))
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = "Video playback error", tint = Color(0xFFFF6B6B))
        Spacer(Modifier.height(6.dp))
        Text(
          text = playbackError ?: "Unable to play video",
          color = Color.White,
          fontSize = 12.sp,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
          onClick = { retryNonce += 1 },
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
          modifier = Modifier.defaultMinSize(minHeight = 40.dp)
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Retry video", tint = Color.White, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Retry", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
      }
    } else if (showControls) {
      // Subtle center Play indicator when paused
      if (!isPlaying && !isBuffering) {
        Box(
          modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable {
              val player = exoPlayerRef.value
              if (player != null && isPrepared) {
                try {
                  manuallyPausedByUser = false
                  startOrResumePlayer(player)
                } catch (_: Exception) {}
              }
            },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play video",
            tint = Color.White,
            modifier = Modifier.size(30.dp)
          )
        }
      }

      // Bottom-end Mute / Unmute button with 48dp accessible touch target
      IconButton(
        onClick = {
          val nextMuted = !isMuted
          isMuted = nextMuted
          try {
            val vol = if (nextMuted) 0f else 1f
            exoPlayerRef.value?.volume = vol
          } catch (_: Exception) {}
          onMuteToggled?.invoke(nextMuted)
        },
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(8.dp)
          .size(48.dp)
          .testTag("video_mute_toggle")
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (isMuted) "Unmute video" else "Mute video",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

/**
 * Normalizes raw media aspect ratios into clean social feed containers (1:1, 4:5, or 16:9)
 * so media fits naturally without stretching, excessive cropping, or side black bars.
 */
fun resolveFeedContainerAspectRatio(rawRatio: Float?, isReel: Boolean = false): Float {
  if (isReel) return 9f / 16f
  if (rawRatio == null || rawRatio <= 0f) return 1f
  return when {
    rawRatio < 0.88f -> 0.8f       // Portrait 4:5
    rawRatio <= 1.22f -> 1.0f      // Square 1:1
    else -> rawRatio.coerceIn(1.25f, 1.78f) // Landscape up to 16:9
  }
}

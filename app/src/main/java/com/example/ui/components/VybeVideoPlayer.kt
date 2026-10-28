package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
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
import com.example.util.FeedAudioController

enum class VideoFitMode {
  /** Scales proportionally inside container without cropping important content (ideal for landscape/portrait in stories & reels). */
  FIT_CONTAIN,
  /** Fills container proportionally from center without stretching (ideal when container already matches aspect ratio). */
  COVER_PROPORTIONAL
}

/**
 * Reusable, lifecycle-safe video player built on `TextureView` + `MediaPlayer` so it clips cleanly
 * inside Compose cards, reels, stories, and post-creation previews without `SurfaceView` Z-order black boxes.
 *
 * Enforces strict single-video viewport autoplay:
 * - Starts automatically when [autoPlay] becomes true (muted by default in feed).
 * - Immediately pauses when [autoPlay] becomes false, [isPausedExternal] becomes true, or lifecycle pauses.
 * - Respects manual user pause ([manuallyPausedByUser]) and system reduced-motion preferences when [respectReducedMotion] is true.
 * - Prevents repeated initialization and releases native decoders cleanly on dispose.
 */
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
  onTapOverlay: (() -> Unit)? = null,
  onDoubleTapOverlay: (() -> Unit)? = null,
  onMuteToggled: ((Boolean) -> Unit)? = null,
  feedAudioOwnerId: String? = null,
  audioVolume: Float = 1f
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

  // Keep latest state references accessible inside async MediaPlayer callbacks
  val latestAutoPlay by rememberUpdatedState(autoPlay)
  val latestPausedExternal by rememberUpdatedState(isPausedExternal)
  val latestMuted by rememberUpdatedState(isMuted)
  val latestAudioVolume by rememberUpdatedState(audioVolume.coerceIn(0f, 1f))

  var videoWidth by remember(videoUrl) { mutableIntStateOf(0) }
  var videoHeight by remember(videoUrl) { mutableIntStateOf(0) }
  var viewWidth by remember { mutableIntStateOf(0) }
  var viewHeight by remember { mutableIntStateOf(0) }

  val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
  val textureViewRef = remember { mutableStateOf<TextureView?>(null) }
  val activeSurfaceRef = remember { mutableStateOf<Surface?>(null) }
  val boundUrlRef = remember { mutableStateOf<String?>(null) }

  LaunchedEffect(initialMuted, audioVolume) {
    isMuted = initialMuted
    try {
      val mp = mediaPlayerRef.value
      val vol = if (initialMuted) 0f else audioVolume.coerceIn(0f, 1f)
      mp?.setVolume(vol, vol)
      if (feedAudioOwnerId != null && mp != null) {
        FeedAudioController.onVideoAudioStateChanged(feedAudioOwnerId, mp, shouldPlayAudio = isPlaying && !initialMuted && vol > 0f)
        if (!initialMuted && vol < 1f) {
          mp.setVolume(vol, vol)
        }
      }
    } catch (_: Exception) {}
  }

  LaunchedEffect(isPlaying, isMuted, audioVolume, feedAudioOwnerId) {
    val ownerId = feedAudioOwnerId ?: return@LaunchedEffect
    val mp = mediaPlayerRef.value ?: return@LaunchedEffect
    val vol = if (isMuted) 0f else audioVolume.coerceIn(0f, 1f)
    FeedAudioController.onVideoAudioStateChanged(ownerId, mp, shouldPlayAudio = isPlaying && !isMuted && vol > 0f)
    if (isPlaying && !isMuted && vol < 1f) {
      try { mp.setVolume(vol, vol) } catch (_: Exception) {}
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
    mediaPlayerRef.value?.let { old ->
      if (feedAudioOwnerId != null) {
        FeedAudioController.onVideoAudioStateChanged(feedAudioOwnerId, old, shouldPlayAudio = false)
      }
      try {
        if (old.isPlaying) old.stop()
      } catch (_: Exception) {}
      try {
        old.reset()
      } catch (_: Exception) {}
      try {
        old.release()
      } catch (_: Exception) {}
    }
    mediaPlayerRef.value = null
    boundUrlRef.value = null
    isPrepared = false
    isPlaying = false
  }

  fun configureAndStartPlayer(surface: Surface, forceReload: Boolean = false) {
    if (!forceReload && boundUrlRef.value == videoUrl && mediaPlayerRef.value != null && playbackError == null) {
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
      val mp = MediaPlayer()
      mediaPlayerRef.value = mp
      boundUrlRef.value = videoUrl

      mp.setAudioAttributes(
        AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .build()
      )
      mp.setSurface(surface)
      mp.isLooping = isLooping
      val vol = if (latestMuted) 0f else latestAudioVolume
      mp.setVolume(vol, vol)

      mp.setOnVideoSizeChangedListener { _, w, h ->
        if (w > 0 && h > 0) {
          videoWidth = w
          videoHeight = h
          val ratio = (w.toFloat() / h.toFloat()).coerceIn(0.45f, 2.4f)
          onAspectRatioDetected?.invoke(ratio)
          applyAspectTransform()
        }
      }

      mp.setOnInfoListener { _, what, _ ->
        when (what) {
          MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
          MediaPlayer.MEDIA_INFO_BUFFERING_END,
          MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
        }
        false
      }

      mp.setOnPreparedListener { player ->
        isPrepared = true
        isBuffering = false
        val w = player.videoWidth
        val h = player.videoHeight
        if (w > 0 && h > 0) {
          videoWidth = w
          videoHeight = h
          onAspectRatioDetected?.invoke((w.toFloat() / h.toFloat()).coerceIn(0.45f, 2.4f))
          applyAspectTransform()
        }
        val dur = try { player.duration } catch (_: Exception) { 0 }
        if (dur > 0) {
          onDurationDetected?.invoke(dur)
        }
        val currentVol = if (latestMuted) 0f else latestAudioVolume
        player.setVolume(currentVol, currentVol)

        val isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val shouldPlayNow = latestAutoPlay &&
          !latestPausedExternal &&
          !manuallyPausedByUser &&
          !isSystemReducedMotion &&
          isLifecycleResumed

        if (shouldPlayNow) {
          try {
            player.start()
            isPlaying = true
          } catch (_: Exception) {
            isPlaying = false
          }
        } else {
          try {
            player.seekTo(1)
          } catch (_: Exception) {}
          isPlaying = false
        }
      }

      mp.setOnCompletionListener {
        if (!isLooping) {
          isPlaying = false
          onVideoCompleted?.invoke()
        }
      }

      mp.setOnErrorListener { _, what, extra ->
        isPrepared = false
        isBuffering = false
        isPlaying = false
        playbackError = "Unable to play video ($what/$extra)."
        true
      }

      val parsedUri = Uri.parse(videoUrl)
      if (parsedUri.scheme == "content" || parsedUri.scheme == "file") {
        mp.setDataSource(context, parsedUri)
      } else {
        mp.setDataSource(videoUrl)
      }
      mp.prepareAsync()
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
      // Leaving viewport resets manual pause so scrolling back later behaves predictably
      manuallyPausedByUser = false
    }
    val mp = mediaPlayerRef.value ?: return@LaunchedEffect
    if (!isPrepared || playbackError != null) return@LaunchedEffect

    try {
      val shouldPause = !autoPlay || isPausedExternal
      if (shouldPause) {
        if (mp.isPlaying) {
          mp.pause()
        }
        isPlaying = false
      } else if (!manuallyPausedByUser && !isSystemReducedMotion && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        if (!mp.isPlaying) {
          mp.start()
        }
        isPlaying = true
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
      val mp = mediaPlayerRef.value
      when (event) {
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
          try {
            if (isPrepared && mp?.isPlaying == true) {
              mp.pause()
            }
            isPlaying = false
          } catch (_: Exception) {}
        }
        Lifecycle.Event.ON_RESUME -> {
          try {
            if (isPrepared && latestAutoPlay && !latestPausedExternal && !manuallyPausedByUser && !isSystemReducedMotion && mp != null && playbackError == null) {
              if (!mp.isPlaying) {
                mp.start()
              }
              isPlaying = true
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
                    val mp = mediaPlayerRef.value
                    if (mp != null && isPrepared && playbackError == null) {
                      try {
                        if (mp.isPlaying) {
                          mp.pause()
                          isPlaying = false
                          manuallyPausedByUser = true
                        } else {
                          manuallyPausedByUser = false
                          mp.start()
                          isPlaying = true
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
              val mp = mediaPlayerRef.value
              if (mp != null && isPrepared && playbackError == null) {
                try {
                  if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                    manuallyPausedByUser = true
                  } else {
                    manuallyPausedByUser = false
                    mp.start()
                    isPlaying = true
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
              val mp = mediaPlayerRef.value
              if (mp != null && isPrepared) {
                try {
                  manuallyPausedByUser = false
                  mp.start()
                  isPlaying = true
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
            val vol = if (nextMuted) 0f else latestAudioVolume
            mediaPlayerRef.value?.setVolume(vol, vol)
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

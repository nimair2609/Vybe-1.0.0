package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

enum class VideoFitMode {
  /** Scales proportionally inside container without cropping important content (ideal for landscape/portrait in stories & feed). */
  FIT_CONTAIN,
  /** Fills container proportionally from center without stretching (ideal when container already matches aspect ratio). */
  COVER_PROPORTIONAL
}

/**
 * Reusable, lifecycle-safe video player built on `TextureView` + `MediaPlayer` so it clips cleanly
 * inside Compose cards, reels, stories, and post-creation previews without `SurfaceView` Z-order black boxes.
 */
@Composable
fun VybeVideoPlayer(
  videoUrl: String,
  modifier: Modifier = Modifier,
  autoPlay: Boolean = true,
  isLooping: Boolean = true,
  initialMuted: Boolean = false,
  isPausedExternal: Boolean = false,
  fitMode: VideoFitMode = VideoFitMode.FIT_CONTAIN,
  showControls: Boolean = true,
  onAspectRatioDetected: ((Float) -> Unit)? = null,
  onDurationDetected: ((Int) -> Unit)? = null,
  onVideoCompleted: (() -> Unit)? = null,
  onTapOverlay: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var isBuffering by remember(videoUrl) { mutableStateOf(true) }
  var isPlaying by remember(videoUrl) { mutableStateOf(autoPlay) }
  var isMuted by remember(videoUrl) { mutableStateOf(initialMuted) }
  var playbackError by remember(videoUrl) { mutableStateOf<String?>(null) }
  var retryNonce by remember { mutableIntStateOf(0) }

  var videoWidth by remember(videoUrl) { mutableIntStateOf(0) }
  var videoHeight by remember(videoUrl) { mutableIntStateOf(0) }
  var viewWidth by remember { mutableIntStateOf(0) }
  var viewHeight by remember { mutableIntStateOf(0) }

  val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
  val textureViewRef = remember { mutableStateOf<TextureView?>(null) }
  val activeSurfaceRef = remember { mutableStateOf<Surface?>(null) }

  LaunchedEffect(initialMuted) {
    isMuted = initialMuted
    try {
      val vol = if (initialMuted) 0f else 1f
      mediaPlayerRef.value?.setVolume(vol, vol)
    } catch (_: Exception) {}
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
      // FIT_CONTAIN: zero distortion, zero cropping of important content
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

  fun configureAndStartPlayer(surface: Surface) {
    mediaPlayerRef.value?.let { old ->
      try { old.stop() } catch (_: Exception) {}
      try { old.release() } catch (_: Exception) {}
    }
    mediaPlayerRef.value = null
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
      mp.setAudioAttributes(
        AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .build()
      )
      mp.setSurface(surface)
      mp.isLooping = isLooping
      val vol = if (isMuted) 0f else 1f
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
        val currentVol = if (isMuted) 0f else 1f
        player.setVolume(currentVol, currentVol)
        if (autoPlay && !isPausedExternal) {
          player.start()
          isPlaying = true
        } else {
          player.seekTo(1)
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
    } catch (e: Exception) {
      isBuffering = false
      isPlaying = false
      playbackError = "Video could not be loaded."
    }
  }

  // React to external pause (e.g., long-press hold on StoryViewer or dialog open)
  LaunchedEffect(isPausedExternal) {
    val mp = mediaPlayerRef.value ?: return@LaunchedEffect
    try {
      if (isPausedExternal && mp.isPlaying) {
        mp.pause()
        isPlaying = false
      } else if (!isPausedExternal && autoPlay && !mp.isPlaying && playbackError == null && !isBuffering) {
        mp.start()
        isPlaying = true
      }
    } catch (_: Exception) {}
  }

  // React to videoUrl or manual retry changes when surface is already available
  LaunchedEffect(videoUrl, retryNonce) {
    val surface = activeSurfaceRef.value
    if (surface != null && surface.isValid) {
      configureAndStartPlayer(surface)
    }
  }

  // Pause cleanly when app moves to background
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      val mp = mediaPlayerRef.value
      when (event) {
        Lifecycle.Event.ON_PAUSE -> {
          try {
            if (mp?.isPlaying == true) {
              mp.pause()
              isPlaying = false
            }
          } catch (_: Exception) {}
        }
        Lifecycle.Event.ON_RESUME -> {
          try {
            if (autoPlay && !isPausedExternal && mp != null && !isBuffering && playbackError == null) {
              mp.start()
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
      mediaPlayerRef.value?.let { player ->
        try { player.stop() } catch (_: Exception) {}
        try { player.release() } catch (_: Exception) {}
      }
      mediaPlayerRef.value = null
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
              mediaPlayerRef.value?.let { player ->
                try { player.stop() } catch (_: Exception) {}
                try { player.release() } catch (_: Exception) {}
              }
              mediaPlayerRef.value = null
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
          if (onTapOverlay != null) {
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
              if (mp != null && playbackError == null && !isBuffering) {
                try {
                  if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                  } else {
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
          modifier = Modifier.defaultMinSize(minHeight = 36.dp)
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
              try {
                mp?.start()
                isPlaying = true
              } catch (_: Exception) {}
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
            mediaPlayerRef.value?.setVolume(vol, vol)
          } catch (_: Exception) {}
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

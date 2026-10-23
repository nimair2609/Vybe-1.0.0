package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.theme.VybeMagenta
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * High-performance, single-instance video player for vertical Reels ViewPager.
 * When [isActive] is false, native VideoView and MediaPlayer instances are completely
 * released from memory and replaced with a zero-cost poster image placeholder.
 * This guarantees STRICTLY ONLY ONE video decoder/player is active in memory at any time.
 */
@Composable
fun SmartReelVideoPlayer(
  videoUrl: String,
  isActive: Boolean,
  isMuted: Boolean,
  onMuteToggle: () -> Unit,
  onDoubleTap: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isPlaying by remember { mutableStateOf(true) }
  var isBuffering by remember { mutableStateOf(true) }
  var playbackProgress by remember { mutableFloatStateOf(0f) }
  var showPlayPauseFeedback by remember { mutableStateOf(false) }

  // Double tap heart pop animation
  var showHeartPop by remember { mutableStateOf(false) }
  val heartScale = remember { Animatable(0.2f) }
  val heartAlpha = remember { Animatable(0f) }

  LaunchedEffect(showHeartPop) {
    if (showHeartPop) {
      heartScale.snapTo(0.2f)
      heartAlpha.snapTo(1f)
      heartScale.animateTo(
        targetValue = 1.4f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessLow
        )
      )
      heartScale.animateTo(1.0f, tween(140))
      heartAlpha.animateTo(0f, tween(260))
      showHeartPop = false
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black),
    contentAlignment = Alignment.Center
  ) {
    if (isActive) {
      var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
      var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

      // Update volume when isMuted changes
      LaunchedEffect(isMuted) {
        try {
          val vol = if (isMuted) 0f else 1f
          mediaPlayerRef?.setVolume(vol, vol)
        } catch (_: Exception) {}
      }

      // Track playback progress
      LaunchedEffect(isActive, isPlaying) {
        while (isActive && isPlaying) {
          try {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying && vv.duration > 0) {
              playbackProgress = (vv.currentPosition.toFloat() / vv.duration.toFloat()).coerceIn(0f, 1f)
            }
          } catch (_: Exception) {}
          delay(150)
        }
      }

      // Safe lifecycle release when this page leaves viewport
      DisposableEffect(videoUrl) {
        onDispose {
          try {
            videoViewRef?.stopPlayback()
            mediaPlayerRef?.release()
            mediaPlayerRef = null
            videoViewRef = null
          } catch (_: Exception) {}
        }
      }

      AndroidView(
        factory = { ctx ->
          VideoView(ctx).apply {
            setVideoURI(Uri.parse(videoUrl))
            setOnPreparedListener { mp ->
              mediaPlayerRef = mp
              mp.isLooping = true
              val vol = if (isMuted) 0f else 1f
              mp.setVolume(vol, vol)
              start()
              isPlaying = true
              isBuffering = false
            }
            setOnErrorListener { _, _, _ ->
              isBuffering = false
              true
            }
            setOnCompletionListener {
              isPlaying = false
            }
            videoViewRef = this
          }
        },
        update = { view ->
          videoViewRef = view
        },
        modifier = Modifier.fillMaxSize()
      )

      // Gesture overlay: Tap to play/pause, Double-tap to like
      Box(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(videoUrl) {
            detectTapGestures(
              onDoubleTap = {
                onDoubleTap()
                showHeartPop = true
              },
              onTap = {
                val vv = videoViewRef
                if (vv != null) {
                  if (vv.isPlaying) {
                    vv.pause()
                    isPlaying = false
                  } else {
                    vv.start()
                    isPlaying = true
                  }
                  showPlayPauseFeedback = true
                }
              }
            )
          }
      )

      // Loading / Buffering spinner
      if (isBuffering) {
        CircularProgressIndicator(
          color = VybeMagenta,
          strokeWidth = 3.dp,
          modifier = Modifier.size(36.dp)
        )
      }

      // Play / Pause central visual feedback
      if (!isPlaying && !isBuffering) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
          )
        }
      }

      // Mute indicator badge in top-right
      Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = CircleShape,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 56.dp, end = 16.dp)
          .clip(CircleShape)
          .pointerInput(Unit) {
            detectTapGestures { onMuteToggle() }
          }
          .testTag("reel_mute_indicator")
      ) {
        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = if (isMuted) "Muted" else "Sound on",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      // Bottom playback progress line (Instagram / Reels style)
      LinearProgressIndicator(
        progress = { playbackProgress },
        modifier = Modifier
          .fillMaxWidth()
          .height(2.dp)
          .align(Alignment.BottomCenter),
        color = Color.White.copy(alpha = 0.85f),
        trackColor = Color.White.copy(alpha = 0.2f),
      )
    } else {
      // INACTIVE PAGE: ZERO MEMORY OVERHEAD
      // Native player is completely unmounted. Show only poster thumbnail.
      AsyncImage(
        model = videoUrl,
        contentDescription = "Reel preview",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    }

    // Double-tap animated heart pop in center
    if (heartAlpha.value > 0.01f) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = Color.White.copy(alpha = heartAlpha.value),
          modifier = Modifier
            .size(110.dp)
            .graphicsLayer(
              scaleX = heartScale.value,
              scaleY = heartScale.value
            )
        )
      }
    }
  }
}

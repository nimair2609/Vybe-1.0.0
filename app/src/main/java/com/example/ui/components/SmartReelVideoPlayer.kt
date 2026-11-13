package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
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
import coil.compose.AsyncImage
import com.example.ui.theme.VybeMagenta

/**
 * High-performance, single-instance video player for vertical Reels ViewPager backed by Media3 [VybeVideoPlayer]
 * and the shared application-level [com.example.util.VybeCacheManager] (`SimpleCache`).
 *
 * When [isActive] is false, the active player instance is completely released from memory and replaced with a
 * lightweight cached poster preview image so only one decoder is alive at any time.
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
  var isPausedByUser by remember(videoUrl, isActive) { mutableStateOf(false) }
  var isReady by remember(videoUrl, isActive) { mutableStateOf(false) }
  var playbackProgress by remember(videoUrl, isActive) { mutableFloatStateOf(0f) }

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
      VybeVideoPlayer(
        videoUrl = videoUrl,
        autoPlay = true,
        isLooping = true,
        initialMuted = isMuted,
        isPausedExternal = isPausedByUser,
        fitMode = VideoFitMode.COVER_PROPORTIONAL,
        showControls = false,
        onReadyChanged = { ready -> isReady = ready },
        onProgressChanged = { posMs, durMs ->
          if (durMs > 0) {
            playbackProgress = (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
          }
        },
        onTapOverlay = { isPausedByUser = !isPausedByUser },
        onDoubleTapOverlay = {
          onDoubleTap()
          showHeartPop = true
        },
        modifier = Modifier.fillMaxSize()
      )

      // Loading / Buffering spinner
      if (!isReady) {
        CircularProgressIndicator(
          color = VybeMagenta,
          strokeWidth = 3.dp,
          modifier = Modifier.size(36.dp)
        )
      }

      // Play / Pause central visual feedback
      if (isPausedByUser && isReady) {
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
            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
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
      // INACTIVE PAGE: ZERO DECODER OVERHEAD
      AsyncImage(
        model = videoUrl,
        contentDescription = "Reel preview",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    }

    // Double-tap animated VYBE Resonance burst in center
    if (heartAlpha.value > 0.01f) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer(
            scaleX = heartScale.value,
            scaleY = heartScale.value,
            alpha = heartAlpha.value
          ),
        contentAlignment = Alignment.Center
      ) {
        VybeLikeIcon(isLiked = true, size = 104.dp, contentDescriptionText = null)
      }
    }
  }
}

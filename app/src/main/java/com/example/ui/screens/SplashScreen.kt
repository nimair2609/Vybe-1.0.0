package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

/**
 * VYBE 2.0 Official Startup Animation
 * - Initially shows only the VYBE logo and subtle "from NR Next" brand attribution.
 * - Progressive letter-reveal animation where the VYBE wordmark starts incomplete,
 *   letters attempt to appear with gentle blinking/fading, and resolves into the complete wordmark.
 */
@Composable
fun SplashScreen() {
  val transition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by transition.animateFloat(
    initialValue = 0.98f,
    targetValue = 1.04f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )
  val ambientGlow by transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.75f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "ambientGlow"
  )

  // Letter reveal progressive animation states
  var animationProgress by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(Unit) {
    val startTime = System.currentTimeMillis()
    val duration = 1200f
    while (animationProgress < 1f) {
      val elapsed = System.currentTimeMillis() - startTime
      animationProgress = (elapsed / duration).coerceIn(0f, 1f)
      kotlinx.coroutines.delay(16)
    }
  }

  // Calculate individual letter alphas with initial flicker/blink attempt
  val vAlpha = when {
    animationProgress < 0.1f -> 0f
    animationProgress < 0.2f -> 0.4f // initial faint flicker
    animationProgress < 0.25f -> 0.85f
    else -> 1f
  }
  val yAlpha = when {
    animationProgress < 0.3f -> 0f
    animationProgress < 0.4f -> 0.3f // trying to appear
    animationProgress < 0.5f -> 0.7f
    else -> 1f
  }
  val bAlpha = when {
    animationProgress < 0.55f -> 0f
    animationProgress < 0.65f -> 0.45f
    animationProgress < 0.75f -> 0.8f
    else -> 1f
  }
  val eAlpha = when {
    animationProgress < 0.78f -> 0f
    animationProgress < 0.88f -> 0.5f
    else -> 1f
  }
  val fullResolved = animationProgress >= 0.95f

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianBackground)
      .testTag("splash_screen"),
    contentAlignment = Alignment.Center
  ) {
    // Ambient brand glow
    Box(
      modifier = Modifier
        .size(260.dp)
        .scale(pulseScale * 1.2f)
        .alpha(ambientGlow)
        .clip(RoundedCornerShape(80.dp))
        .background(
          Brush.radialGradient(
            colors = listOf(
              VybeBrandViolet.copy(alpha = 0.40f),
              VybeBrandMagenta.copy(alpha = 0.20f),
              Color.Transparent
            )
          )
        )
    )

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // Official Brand Icon Squircle
      Box(
        modifier = Modifier
          .size(130.dp)
          .scale(pulseScale),
        contentAlignment = Alignment.Center
      ) {
        Image(
          painter = painterResource(id = R.drawable.vybe_app_icon_clean),
          contentDescription = "VYBE Official Logo",
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      // Letter-reveal wordmark animation: V - Y - B - E
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Text(
          text = "V",
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White.copy(alpha = vAlpha),
          modifier = Modifier.padding(horizontal = 1.dp)
        )
        Text(
          text = "Y",
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White.copy(alpha = yAlpha),
          modifier = Modifier.padding(horizontal = 1.dp)
        )
        Text(
          text = "B",
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White.copy(alpha = bAlpha),
          modifier = Modifier.padding(horizontal = 1.dp)
        )
        Text(
          text = "E",
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White.copy(alpha = eAlpha),
          modifier = Modifier.padding(horizontal = 1.dp)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Brand attribution: "from NR Next"
      Text(
        text = "from NR Next",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = if (fullResolved) 0.85f else (animationProgress * 0.7f)),
        letterSpacing = 2.5.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

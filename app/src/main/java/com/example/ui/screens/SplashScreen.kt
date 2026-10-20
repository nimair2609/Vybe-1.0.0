package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@Composable
fun SplashScreen() {
  val infiniteTransition = rememberInfiniteTransition(label = "splash")
  val scale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.8f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianBackground),
    contentAlignment = Alignment.Center
  ) {
    // Ambient radial gradient matching brand cyan/blue/fuchsia glow
    Box(
      modifier = Modifier
        .size(280.dp)
        .scale(scale * 1.25f)
        .alpha(glowAlpha)
        .clip(RoundedCornerShape(80.dp))
        .background(
          Brush.radialGradient(
            colors = listOf(
              VybeBrandViolet.copy(alpha = 0.45f),
              VybeBrandMagenta.copy(alpha = 0.25f),
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
          .size(140.dp)
          .scale(scale),
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

      Text(
        text = "vybe",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = Color.White
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Official Tagline from brand sheet: "CONNECT • SHARE • BE YOU"
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Text(text = "CONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.85f))
        Text(text = " • ", fontSize = 12.sp, color = VybeBrandCyan, fontWeight = FontWeight.ExtraBold)
        Text(text = "SHARE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.85f))
        Text(text = " • ", fontSize = 12.sp, color = VybeBrandMagenta, fontWeight = FontWeight.ExtraBold)
        Text(text = "BE YOU", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.85f))
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "More Than Just Social Media",
        style = MaterialTheme.typography.bodySmall,
        color = VybeBrandCyan,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

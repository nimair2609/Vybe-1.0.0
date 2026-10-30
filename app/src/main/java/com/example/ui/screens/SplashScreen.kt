package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.VybeBrandMagenta
import com.example.ui.theme.VybeBrandViolet

/**
 * VYBE Official Startup Screen
 * Displays the VYBE brand icon, wordmark, and "from NR Next" attribution at a
 * clean, static, fixed scale without zooming, pulsing, scaling, or bouncing effects.
 */
@Composable
fun SplashScreen() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianBackground)
      .testTag("splash_screen"),
    contentAlignment = Alignment.Center
  ) {
    // Static ambient brand glow
    Box(
      modifier = Modifier
        .size(260.dp)
        .clip(RoundedCornerShape(80.dp))
        .background(
          Brush.radialGradient(
            colors = listOf(
              VybeBrandViolet.copy(alpha = 0.28f),
              VybeBrandMagenta.copy(alpha = 0.14f),
              Color.Transparent
            )
          )
        )
    )

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // Official Brand Icon Squircle — fixed 130.dp size, zero zoom/pulse animation
      Box(
        modifier = Modifier
          .size(130.dp)
          .testTag("splash_logo_container"),
        contentAlignment = Alignment.Center
      ) {
        Image(
          painter = painterResource(id = R.drawable.vybe_app_icon_clean),
          contentDescription = "VYBE Official Logo",
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .testTag("splash_logo_image")
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Text(
          text = "VYBE",
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          letterSpacing = 2.sp,
          modifier = Modifier.testTag("splash_wordmark")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "from NR Next",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.85f),
        letterSpacing = 2.5.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

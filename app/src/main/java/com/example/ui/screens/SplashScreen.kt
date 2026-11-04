package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "CONNECT. EXPRESS. GROW.",
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF38BDF8),
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Subtle Indian tricolour accent bar
      Row(
        modifier = Modifier
          .size(width = 64.dp, height = 3.dp)
          .clip(RoundedCornerShape(2.dp))
      ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFF9933)))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFFFFF)))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF138808)))
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "AN NR NEXT PRODUCT",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.90f),
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "🇮🇳 PROUDLY MADE IN INDIA",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.70f),
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}

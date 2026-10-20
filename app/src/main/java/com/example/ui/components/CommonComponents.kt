package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LivePulseStatus
import com.example.data.model.User
import com.example.ui.theme.*

@Composable
fun VybeAvatar(
  user: User,
  size: Dp = 48.dp,
  showPulseDot: Boolean = true,
  onClick: (() -> Unit)? = null
) {
  val borderBrush = if (user.currentMood != null) {
    Brush.sweepGradient(listOf(user.currentMood.accentColor, VybeBrandViolet, user.currentMood.accentColor))
  } else {
    Brush.linearGradient(listOf(VybeBrandCyan, VybeBrandViolet, VybeBrandMagenta))
  }

  Box(
    modifier = Modifier
      .size(size)
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(CircleShape)
        .background(borderBrush)
        .padding(2.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surface)
    ) {
      if (user.avatarUrl.isNotEmpty()) {
        AsyncImage(
          model = user.avatarUrl,
          contentDescription = user.displayName,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(VybeBrandViolet, VybeBrandMagenta))),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = user.displayName.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4).sp
          )
        }
      }
    }

    // 5-second Profile Video Indicator
    if (!user.profileVideoUrl.isNullOrEmpty()) {
      Box(
        modifier = Modifier
          .size(size * 0.35f)
          .align(Alignment.TopEnd)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surface)
          .padding(1.dp)
          .clip(CircleShape)
          .background(VybeBrandMagenta),
        contentAlignment = Alignment.Center
      ) {
        Text("▶", fontSize = (size.value * 0.16).sp, color = Color.White)
      }
    }

    if (showPulseDot && user.livePulse != LivePulseStatus.NONE) {
      Box(
        modifier = Modifier
          .size(size * 0.32f)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surface)
          .padding(1.5.dp)
          .clip(CircleShape)
          .background(VybeOnlineGreen)
      )
    }
  }
}

/**
 * Official VYBE 2.0 Distinctive Circular Badges
 * Featuring the official folded ribbon "V" logo inside a precision circular ring
 */
@Composable
fun VerificationBadge(user: User) {
  // Official VIP Badge
  if (user.isVip) {
    Spacer(modifier = Modifier.width(3.dp))
    Box(
      modifier = Modifier
        .size(17.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))))
        .padding(1.2.dp)
        .clip(CircleShape)
        .background(Color(0xFF0F121E))
        .testTag("vip_badge"),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.vybe_app_icon_clean),
        contentDescription = "VYBE Authorized VIP Badge",
        modifier = Modifier.size(13.dp)
      )
    }
  }

  // Official Verified Badge
  if (user.isVerified || user.isVerifiedPublicFigure) {
    Spacer(modifier = Modifier.width(3.dp))
    Box(
      modifier = Modifier
        .size(17.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(VybeBrandCyan, VybeBrandBlue)))
        .padding(1.2.dp)
        .clip(CircleShape)
        .background(Color(0xFF060714))
        .testTag("verified_badge"),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.vybe_app_icon_clean),
        contentDescription = "VYBE Authorized Verified Badge",
        modifier = Modifier.size(13.dp)
      )
    }
  }

  // Verified Business Shop Badge
  if (user.isVerifiedBusiness) {
    Spacer(modifier = Modifier.width(3.dp))
    Icon(
      imageVector = Icons.Default.Store,
      contentDescription = "Verified Business Shop",
      tint = VybeVerifiedGold,
      modifier = Modifier
        .size(16.dp)
        .testTag("business_badge")
    )
  }
}

@Composable
fun VybeGradientButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .height(48.dp)
      .fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
    contentPadding = PaddingValues(),
    shape = RoundedCornerShape(14.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          brush = Brush.horizontalGradient(listOf(VybeBrandCyan, VybeBrandViolet, VybeBrandMagenta)),
          shape = RoundedCornerShape(14.dp)
        ),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
      )
    }
  }
}

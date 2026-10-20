package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    Brush.sweepGradient(listOf(user.currentMood.accentColor, VybeViolet, user.currentMood.accentColor))
  } else {
    Brush.linearGradient(listOf(VybeViolet, VybeMagenta))
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
            .background(Brush.linearGradient(listOf(VybeViolet, VybeMagenta))),
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

@Composable
fun VerificationBadge(user: User) {
  if (user.isVerifiedPublicFigure) {
    Icon(
      imageVector = Icons.Default.CheckCircle,
      contentDescription = "Verified Public Figure",
      tint = VybeVerifiedBlue,
      modifier = Modifier
        .size(16.dp)
        .testTag("verified_badge")
    )
  } else if (user.isVerifiedBusiness) {
    Icon(
      imageVector = Icons.Default.Store,
      contentDescription = "Verified Business Shop",
      tint = VybeVerifiedGold,
      modifier = Modifier
        .size(16.dp)
        .testTag("business_badge")
    )
  }

  if (user.vipBadgeTitle.isNotEmpty()) {
    Spacer(modifier = Modifier.width(4.dp))
    Surface(
      color = VybeVioletDark.copy(alpha = 0.35f),
      shape = RoundedCornerShape(6.dp),
      border = androidx.compose.foundation.BorderStroke(0.5.dp, VybeViolet)
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.WorkspacePremium,
          contentDescription = "VIP",
          tint = VybeVerifiedGold,
          modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
          text = user.vipBadgeTitle,
          fontSize = 9.sp,
          color = Color.White,
          fontWeight = FontWeight.Bold
        )
      }
    }
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
          brush = Brush.horizontalGradient(listOf(VybeViolet, VybeMagenta)),
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

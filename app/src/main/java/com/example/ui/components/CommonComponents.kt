package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
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

    if (showPulseDot && user.isOnline) {
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
/**
 * Official VYBE 3-Tier Authority Badges:
 * 1. VYBE Verified (Blue Tick): Public profile authenticity.
 * 2. VYBE VIP / Shop (Gold Badge): VIP Creator star / Verified Shop business icon.
 * 3. VYBE Worker (Green Tick): Verified VYBE employee/worker.
 * Honors user preference: Hidden if badgeDisplayStatus is 'HIDDEN' or 'DECLINED'.
 */
@Composable
fun VerificationBadge(user: User) {
  if (user.badgeDisplayStatus == "HIDDEN" || user.badgeDisplayStatus == "DECLINED") {
    return
  }

  // 1. Official VYBE Worker Badge (Green Tick)
  if (user.isWorker || user.role == UserRole.ADMIN || user.role == UserRole.MODERATOR) {
    Spacer(modifier = Modifier.width(3.dp))
    Box(
      modifier = Modifier
        .size(18.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00C853))))
        .testTag("worker_badge"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = "VYBE Verified Worker",
        tint = Color(0xFF060714),
        modifier = Modifier.size(12.dp)
      )
    }
  }

  // 2. Official VIP Creator (Gold Star) or Verified Shop (Store)
  if (user.isVip) {
    Spacer(modifier = Modifier.width(3.dp))
    Box(
      modifier = Modifier
        .size(18.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))))
        .testTag("vip_badge"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Star,
        contentDescription = "VYBE VIP Creator Badge",
        tint = Color(0xFF0F121E),
        modifier = Modifier.size(12.dp)
      )
    }
  } else if (user.isVerifiedBusiness) {
    Spacer(modifier = Modifier.width(3.dp))
    Box(
      modifier = Modifier
        .size(18.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF9100))))
        .testTag("business_badge"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Store,
        contentDescription = "Verified Business Shop",
        tint = Color(0xFF060714),
        modifier = Modifier.size(12.dp)
      )
    }
  }

  // 3. Official Verified Public Figure / Account (Blue Tick)
  if (user.isVerified || user.isVerifiedPublicFigure) {
    Spacer(modifier = Modifier.width(3.dp))
    Box(
      modifier = Modifier
        .size(18.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFF0091EA))))
        .testTag("verified_badge"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = "VYBE Verified Badge",
        tint = Color(0xFF060714),
        modifier = Modifier.size(12.dp)
      )
    }
  }
}

/**
 * Premium Filled Follow Button for Feed, Post Cards, and User Profiles.
 * Follows VYBE signature cyan-to-violet gradient with distinct Following and Requested states.
 */
@Composable
fun VybeFollowButton(
  isFollowing: Boolean,
  isFollowRequested: Boolean = false,
  followsMe: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false
) {
  val label = when {
    isFollowing -> "Following"
    isFollowRequested -> "Requested"
    followsMe -> "Follow Back"
    else -> "Follow"
  }

  when {
    isFollowing -> {
      Surface(
        color = Color(0xFF1E2638),
        shape = RoundedCornerShape(if (compact) 14.dp else 12.dp),
        border = BorderStroke(1.dp, Color(0xFF2E3856)),
        modifier = modifier
          .clip(RoundedCornerShape(if (compact) 14.dp else 12.dp))
          .clickable { onClick() }
          .testTag("follow_button_following")
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(
            horizontal = if (compact) 14.dp else 20.dp,
            vertical = if (compact) 6.dp else 10.dp
          )
        ) {
          Text(
            text = label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f)
          )
        }
      }
    }
    isFollowRequested -> {
      Surface(
        color = Color(0xFF241838),
        shape = RoundedCornerShape(if (compact) 14.dp else 12.dp),
        border = BorderStroke(1.dp, Color(0xFF8A2BE2).copy(alpha = 0.5f)),
        modifier = modifier
          .clip(RoundedCornerShape(if (compact) 14.dp else 12.dp))
          .clickable { onClick() }
          .testTag("follow_button_requested")
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(
            horizontal = if (compact) 14.dp else 20.dp,
            vertical = if (compact) 6.dp else 10.dp
          )
        ) {
          Text(
            text = label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFD8B4FE)
          )
        }
      }
    }
    else -> {
      // Filled Gradient Follow Button
      Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(if (compact) 14.dp else 12.dp),
        modifier = modifier
          .clip(RoundedCornerShape(if (compact) 14.dp else 12.dp))
          .background(
            Brush.linearGradient(
              listOf(Color(0xFF00F0FF), Color(0xFF8A2BE2))
            )
          )
          .clickable { onClick() }
          .testTag("follow_button_active")
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(
            horizontal = if (compact) 14.dp else 20.dp,
            vertical = if (compact) 6.dp else 10.dp
          )
        ) {
          Text(
            text = label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF060714)
          )
        }
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

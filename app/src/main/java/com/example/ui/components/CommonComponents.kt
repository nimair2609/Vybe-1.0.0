package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.model.UserRole
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
 * Official VYBE 3-Tier Authority Badges:
 * 1. VYBE Verified (Blue Tick): Public profile authenticity confirmed by admin review.
 * 2. VYBE VIP / Shop (Gold Badge): Active VIP Creator star (honors vipExpiresAtIso) or Verified Business storefront.
 * 3. VYBE Worker (Green Tick): Authorized VYBE employee/worker confirmed by admin.
 *
 * Strictly enforces:
 * - Never auto-grants badges based on ADMIN/MODERATOR role or follower count.
 * - Never displays badges while in PENDING_ACCEPTANCE, HIDDEN (unless mandatory worker policy), or DECLINED state.
 * - Never displays expired VIP badges.
 */
@Composable
fun VerificationBadge(
  user: User,
  modifier: Modifier = Modifier,
  iconSize: Dp = 18.dp,
  enableExplanationDialog: Boolean = false
) {
  val activeBadges = user.activePublicBadges
  if (activeBadges.isEmpty()) return

  var showExplanation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
  val glyphSize = (iconSize.value * 0.66f).dp

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.then(
      if (enableExplanationDialog) {
        Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable { showExplanation = true }
          .testTag("badge_explanation_trigger")
      } else {
        Modifier
      }
    )
  ) {
    activeBadges.forEach { badge ->
      Spacer(modifier = Modifier.width(3.dp))
      val testTag = when (badge) {
        com.example.data.model.OfficialVybeBadge.WORKER_GREEN -> "worker_badge"
        com.example.data.model.OfficialVybeBadge.VIP_GOLD -> "vip_badge"
        com.example.data.model.OfficialVybeBadge.SHOP_GOLD -> "business_badge"
        com.example.data.model.OfficialVybeBadge.VERIFIED_BLUE -> "verified_badge"
      }
      val drawableResId = when (badge) {
        com.example.data.model.OfficialVybeBadge.WORKER_GREEN -> R.drawable.vybe_official
        com.example.data.model.OfficialVybeBadge.VIP_GOLD -> R.drawable.vybe_vip
        com.example.data.model.OfficialVybeBadge.SHOP_GOLD -> R.drawable.vybe_vip
        com.example.data.model.OfficialVybeBadge.VERIFIED_BLUE -> R.drawable.vybe_verified
      }
      // For VIP, the royal crown sits gracefully above the circular emblem
      val badgeHeight = if (badge == com.example.data.model.OfficialVybeBadge.VIP_GOLD) iconSize * 1.14f else iconSize

      Image(
        painter = painterResource(id = drawableResId),
        contentDescription = badge.title,
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .width(iconSize)
          .height(badgeHeight)
          .testTag(testTag)
      )
    }
  }

  if (showExplanation) {
    AlertDialog(
      onDismissRequest = { showExplanation = false },
      title = {
        Text(
          text = "Official VYBE Verification",
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          activeBadges.forEach { badge ->
            val accent = when (badge) {
              com.example.data.model.OfficialVybeBadge.VERIFIED_BLUE -> Color(0xFF2563EB)
              com.example.data.model.OfficialVybeBadge.VIP_GOLD,
              com.example.data.model.OfficialVybeBadge.SHOP_GOLD -> Color(0xFFD97706)
              com.example.data.model.OfficialVybeBadge.WORKER_GREEN -> Color(0xFF059669)
            }
            Surface(
              color = accent.copy(alpha = 0.12f),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = badge.title,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = badge.description,
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 16.sp
                )
                if (badge == com.example.data.model.OfficialVybeBadge.VIP_GOLD && !user.vipExpiresAtIso.isNullOrBlank()) {
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Active through ${user.vipExpiresAtIso.take(10)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showExplanation = false }) {
          Text("Got it", fontWeight = FontWeight.Bold)
        }
      },
      modifier = Modifier.testTag("badge_explanation_dialog")
    )
  }
}

/**
 * Filled VYBE Brand Follow Button for Feed, Reels, Search, and User Profiles.
 * Readable and high-contrast in both Dark and Light themes with distinct states:
 * - Follow / Follow Back: Filled VYBE brand blue-purple gradient with crisp white text
 * - Following: Neutral secondary container surface with outline border
 * - Requested: Amber-accented pending request surface with outline border
 */
@Composable
fun VybeFollowButton(
  isFollowing: Boolean,
  isFollowRequested: Boolean = false,
  followsMe: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  testTag: String? = null
) {
  val label = when {
    isFollowing -> "Following"
    isFollowRequested -> "Requested"
    followsMe -> "Follow Back"
    else -> "Follow"
  }
  val shape = RoundedCornerShape(if (compact) 14.dp else 12.dp)

  when {
    isFollowing -> {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
          .minimumInteractiveComponentSize()
          .clip(shape)
          .clickable { onClick() }
          .testTag(testTag ?: "follow_button_following")
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(
            horizontal = if (compact) 14.dp else 20.dp,
            vertical = if (compact) 7.dp else 11.dp
          )
        ) {
          Text(
            text = label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
          )
        }
      }
    }
    isFollowRequested -> {
      Surface(
        color = Color(0xFFD97706).copy(alpha = 0.16f),
        shape = shape,
        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.8f)),
        modifier = modifier
          .minimumInteractiveComponentSize()
          .clip(shape)
          .clickable { onClick() }
          .testTag(testTag ?: "follow_button_requested")
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(
            horizontal = if (compact) 14.dp else 20.dp,
            vertical = if (compact) 7.dp else 11.dp
          )
        ) {
          Text(
            text = label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFD97706),
            maxLines = 1
          )
        }
      }
    }
    else -> {
      Surface(
        color = Color.Transparent,
        shape = shape,
        shadowElevation = if (compact) 2.dp else 4.dp,
        modifier = modifier
          .minimumInteractiveComponentSize()
          .clip(shape)
          .background(
            Brush.horizontalGradient(
              listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
            )
          )
          .clickable { onClick() }
          .testTag(testTag ?: "follow_button_active")
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(
            horizontal = if (compact) 14.dp else 20.dp,
            vertical = if (compact) 7.dp else 11.dp
          )
        ) {
          Text(
            text = label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
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

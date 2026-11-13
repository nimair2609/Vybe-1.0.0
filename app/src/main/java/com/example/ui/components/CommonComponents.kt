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
import androidx.compose.material.icons.filled.PlayArrow
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
  val palette = LocalVybePalette.current
  val borderBrush = if (user.currentMood != null) {
    Brush.sweepGradient(listOf(user.currentMood.accentColor, VybeSocialVioletBright, user.currentMood.accentColor))
  } else {
    Brush.linearGradient(listOf(VybeSocialVioletBright, VybeGrowBlueBright))
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
        .background(palette.card)
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
            .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlue))),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = user.displayName.take(1).uppercase(),
            color = Color.White,
            fontFamily = PlusJakartaSansFontFamily,
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
          .background(palette.card)
          .padding(1.dp)
          .clip(CircleShape)
          .background(palette.accent),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.PlayArrow,
          contentDescription = "Profile video",
          tint = Color.White,
          modifier = Modifier.size(size * 0.22f)
        )
      }
    }

    if (showPulseDot && user.isOnline) {
      Box(
        modifier = Modifier
          .size(size * 0.32f)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .background(palette.card)
          .padding(1.5.dp)
          .clip(CircleShape)
          .background(palette.success)
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
  val palette = LocalVybePalette.current
  val activeBadges = user.activePublicBadges
  if (activeBadges.isEmpty()) return

  var showExplanation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.then(
      if (enableExplanationDialog) {
        Modifier
          .clip(RoundedCornerShape(VybeTokens.Radius.sm))
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
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      shape = RoundedCornerShape(VybeTokens.Radius.xl),
      title = {
        Text(
          text = "Official VYBE Verification",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          activeBadges.forEach { badge ->
            val accent = when (badge) {
              com.example.data.model.OfficialVybeBadge.VERIFIED_BLUE -> VybeGrowBlue
              com.example.data.model.OfficialVybeBadge.VIP_GOLD,
              com.example.data.model.OfficialVybeBadge.SHOP_GOLD -> Color(0xFFD97706)
              com.example.data.model.OfficialVybeBadge.WORKER_GREEN -> Color(0xFF059669)
            }
            Surface(
              color = accent.copy(alpha = 0.12f),
              shape = RoundedCornerShape(VybeTokens.Radius.sm),
              border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = badge.title,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = badge.description,
                  fontSize = 12.sp,
                  color = palette.textSecondary,
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
          Text("Got it", fontWeight = FontWeight.Bold, color = palette.accent)
        }
      },
      modifier = Modifier.testTag("badge_explanation_dialog")
    )
  }
}

/**
 * Official VYBE Brand LINK UP Button for Feed, Reels, Search, and User Profiles.
 * Readable and high-contrast in both Dark and Light themes with distinct states:
 * - Not connected: Filled VYBE brand blue-purple gradient with crisp white "+ LINK UP" text
 * - Linked: Neutral container surface with crisp outline and checkmark "LINKED", offering UNLINK confirmation
 * - Requested: Amber-accented pending request surface with outline border "LINKUP Requested"
 */
@Composable
fun VybeFollowButton(
  isFollowing: Boolean,
  isFollowRequested: Boolean = false,
  followsMe: Boolean = false,
  targetUsername: String? = null,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  testTag: String? = null
) {
  val palette = LocalVybePalette.current
  var showUnlinkConfirm by remember { mutableStateOf(false) }

  val label = when {
    isFollowing -> "LINKED"
    isFollowRequested -> "LINKUP Requested"
    else -> "+ LINK UP"
  }
  val shape = RoundedCornerShape(if (compact) VybeTokens.Radius.md else VybeTokens.Radius.md)

  when {
    isFollowing -> {
      Surface(
        color = palette.elevated,
        shape = shape,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
          .minimumInteractiveComponentSize()
          .clip(shape)
          .testTag(testTag ?: "follow_button_following")
          .clickable { showUnlinkConfirm = true }
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier.padding(
            horizontal = if (compact) 12.dp else 18.dp,
            vertical = if (compact) 6.dp else 10.dp
          )
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = palette.textPrimary,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            maxLines = 1
          )
        }
      }
    }
    isFollowRequested -> {
      Surface(
        color = palette.warning.copy(alpha = 0.14f),
        shape = shape,
        border = BorderStroke(1.dp, palette.warning.copy(alpha = 0.7f)),
        modifier = modifier
          .minimumInteractiveComponentSize()
          .clip(shape)
          .testTag(testTag ?: "follow_button_requested")
          .clickable { onClick() }
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(
            horizontal = if (compact) 12.dp else 16.dp,
            vertical = if (compact) 6.dp else 10.dp
          )
        ) {
          Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.warning,
            maxLines = 1
          )
        }
      }
    }
    else -> {
      Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
          .minimumInteractiveComponentSize()
          .clip(shape)
          .background(palette.accent)
          .testTag(testTag ?: "follow_button_active")
          .clickable { onClick() }
          .padding(
            horizontal = if (compact) 14.dp else 20.dp,
            vertical = if (compact) 7.dp else 11.dp
          )
      ) {
        Text(
          text = label,
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = if (compact) 12.sp else 14.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 1
        )
      }
    }
  }

  if (showUnlinkConfirm) {
    AlertDialog(
      onDismissRequest = { showUnlinkConfirm = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      shape = RoundedCornerShape(VybeTokens.Radius.xl),
      title = {
        Text(
          "UNLINK",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        val target = if (!targetUsername.isNullOrBlank()) "@$targetUsername" else "this user"
        Text("Are you sure you want to UNLINK $target? You will no longer see their posts and stories in your LINKED feed.")
      },
      confirmButton = {
        Button(
          onClick = {
            showUnlinkConfirm = false
            onClick()
          },
          colors = ButtonDefaults.buttonColors(containerColor = palette.danger)
        ) {
          Text("UNLINK", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showUnlinkConfirm = false }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
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
  val palette = LocalVybePalette.current
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .heightIn(min = VybeTokens.Size.buttonHeight)
      .fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(
      containerColor = Color.Transparent,
      disabledContainerColor = palette.elevated
    ),
    contentPadding = PaddingValues(),
    shape = RoundedCornerShape(VybeTokens.Radius.md)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = VybeTokens.Size.buttonHeight)
        .background(
          brush = if (enabled) {
            Brush.horizontalGradient(listOf(VybeSocialViolet, VybeGrowBlue))
          } else {
            Brush.horizontalGradient(listOf(palette.elevated, palette.elevated))
          },
          shape = RoundedCornerShape(VybeTokens.Radius.md)
        ),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = text,
        color = if (enabled) Color.White else palette.textMuted,
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
      )
    }
  }
}

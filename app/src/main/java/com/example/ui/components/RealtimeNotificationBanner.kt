package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.notifications.RealtimeInAppBannerEvent
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeViolet

/**
 * Instagram-style floating top notification pill that slides down in real time (<100ms)
 * when the user receives a Direct Message, Like, Comment, Follow, or Official Broadcast
 * while actively browsing VYBE.
 */
@Composable
fun RealtimeNotificationBanner(
  event: RealtimeInAppBannerEvent?,
  onOpen: (RealtimeInAppBannerEvent) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = event != null && !event.type.contains("CALL", ignoreCase = true),
    enter = slideInVertically(
      initialOffsetY = { -it - 40 },
      animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    ) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { -it - 40 }) + fadeOut(),
    modifier = modifier
  ) {
    val active = event ?: return@AnimatedVisibility
    val typeUpper = active.type.uppercase()
    val isChat = typeUpper.contains("MESSAGE") || typeUpper.contains("CHAT")
    val isBroadcast = typeUpper == "ANNOUNCEMENT" || typeUpper == "BROADCAST" || typeUpper == "SECURITY_ACCOUNT_UPDATE"

    val (badgeIcon, badgeColor, actionLabel) = when {
      isChat -> Triple(Icons.Outlined.Forum, VybeOnlineGreen, "Reply")
      typeUpper == "LIKE" || typeUpper == "REACTION" -> Triple(Icons.Outlined.FavoriteBorder, VybeMagenta, "View")
      typeUpper.contains("COMMENT") -> Triple(Icons.Outlined.ChatBubbleOutline, VybeBrandCyan, "View")
      typeUpper.contains("FOLLOW") -> Triple(Icons.Outlined.PersonAdd, VybeViolet, "Profile")
      isBroadcast -> Triple(Icons.Outlined.Campaign, VybeMagenta, "Open")
      else -> Triple(Icons.Outlined.NotificationsActive, VybeViolet, "Open")
    }

    val headline = when {
      !active.actorUsername.isNullOrBlank() && !active.actorUsername.startsWith("user_") ->
        if (!active.actorDisplayName.isNullOrBlank()) "${active.actorDisplayName} (@${active.actorUsername})" else "@${active.actorUsername}"
      !active.actorDisplayName.isNullOrBlank() -> active.actorDisplayName
      else -> active.title
    }

    val initials = headline.removePrefix("@").trim()
      .split(" ", "_")
      .filter { it.isNotBlank() }
      .take(2)
      .joinToString("") { it.first().uppercaseChar().toString() }
      .ifBlank { "V" }

    Surface(
      shape = RoundedCornerShape(22.dp),
      color = Color(0xF214141D),
      border = BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(
          listOf(
            VybeViolet.copy(alpha = 0.65f),
            badgeColor.copy(alpha = 0.65f)
          )
        )
      ),
      shadowElevation = 14.dp,
      tonalElevation = 8.dp,
      modifier = Modifier
        .padding(horizontal = 12.dp)
        .widthIn(max = 520.dp)
        .fillMaxWidth()
        .clickable {
          onOpen(active)
          onDismiss()
        }
        .testTag("realtime_in_app_notification_banner")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Circular Avatar + Instagram-style Mini Activity Badge
        Box(
          modifier = Modifier.size(44.dp),
          contentAlignment = Alignment.BottomEnd
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(VybeViolet, VybeMagenta)
                )
              )
              .padding(1.5.dp)
              .clip(CircleShape)
              .background(Color(0xFF1F1F2E)),
            contentAlignment = Alignment.Center
          ) {
            if (!active.actorAvatarUrl.isNullOrBlank()) {
              AsyncImage(
                model = active.actorAvatarUrl,
                contentDescription = headline,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Text(
                text = initials,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Box(
            modifier = Modifier
              .size(18.dp)
              .clip(CircleShape)
              .background(Color(0xFF14141D))
              .padding(1.5.dp)
              .clip(CircleShape)
              .background(badgeColor),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = badgeIcon,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(10.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = headline,
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "• now",
              color = VybeBrandCyan,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
          Text(
            text = active.body,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 2,
            lineHeight = 15.sp,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (!active.mediaUrl.isNullOrBlank()) {
          Spacer(modifier = Modifier.width(8.dp))
          AsyncImage(
            model = active.mediaUrl,
            contentDescription = "Media preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(38.dp)
              .clip(RoundedCornerShape(8.dp))
          )
        } else {
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = badgeColor.copy(alpha = 0.2f)
          ) {
            Text(
              text = actionLabel,
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
          }
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(48.dp)
            .testTag("dismiss_realtime_banner_button")
        ) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Dismiss notification banner",
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

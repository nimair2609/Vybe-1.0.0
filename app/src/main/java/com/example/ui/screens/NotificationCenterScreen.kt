package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationType
import com.example.data.model.User
import com.example.data.model.VybeReaction
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.components.reactionColor
import com.example.ui.theme.*
import com.example.util.rememberRelativeTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val notifications by viewModel.notifications.collectAsState()
  val incomingFollowRequestUserIds by viewModel.incomingFollowRequestUserIds.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  var filterUnreadOnly by remember { mutableStateOf(false) }
  var notificationToDelete by remember { mutableStateOf<NotificationItem?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) {
    viewModel.refreshAllData()
  }

  val pendingFollowRequesters = remember(incomingFollowRequestUserIds, allUsers) {
    incomingFollowRequestUserIds.map { requesterId ->
      allUsers.firstOrNull { it.id == requesterId } ?: User(
        id = requesterId,
        username = "member_${requesterId.take(6)}",
        displayName = "VYBE Member"
      )
    }
  }

  val unreadCount = remember(notifications) { notifications.count { !it.isRead } }
  val filteredNotifications = remember(notifications, filterUnreadOnly) {
    if (filterUnreadOnly) notifications.filter { !it.isRead } else notifications
  }
  val totalBadge = unreadCount + pendingFollowRequesters.size

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      VybeSubScreenTopBar(
        title = "Notifications",
        subtitle = if (totalBadge > 0) "$totalBadge unread updates" else "All caught up",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "notifications_back_button",
        actions = {
          if (unreadCount > 0) {
            TextButton(
              onClick = { viewModel.markAllNotificationsRead() },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("mark_all_read_button")
            ) {
              Text(
                "Mark All Read",
                fontSize = 12.sp,
                color = palette.accent,
                fontWeight = FontWeight.Bold
              )
            }
          }
          IconButton(
            onClick = { viewModel.refreshAllData() },
            modifier = Modifier
              .size(48.dp)
              .testTag("notifications_refresh_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Sync Notifications", tint = palette.textPrimary)
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .navigationBarsPadding()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(
          Triple(false, "All (${notifications.size})", "filter_all_notifications"),
          Triple(true, "Unread ($unreadCount)", "filter_unread_notifications")
        ).forEach { (unreadTab, label, tag) ->
          val selected = filterUnreadOnly == unreadTab
          Surface(
            color = if (selected) palette.accent else palette.elevated,
            shape = RoundedCornerShape(VybeTokens.Radius.pill),
            border = BorderStroke(1.dp, if (selected) palette.accent else palette.border),
            modifier = Modifier
              .heightIn(min = 40.dp)
              .clip(RoundedCornerShape(VybeTokens.Radius.pill))
              .clickable { filterUnreadOnly = unreadTab }
              .testTag(tag)
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) Color.White else palette.textSecondary
              )
            }
          }
        }
      }

      if (filteredNotifications.isEmpty() && pendingFollowRequesters.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          VybeEmptyState(
            icon = Icons.Default.NotificationsNone,
            title = if (filterUnreadOnly) "No unread notifications" else "No notifications yet",
            subtitle = "Likes, comments, LINKUPS, and message alerts from real interactions will appear here."
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (pendingFollowRequesters.isNotEmpty()) {
            item {
              Text(
                text = "LINKUP REQUESTS (${pendingFollowRequesters.size})",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = palette.accent,
                modifier = Modifier.padding(vertical = 4.dp)
              )
            }
            items(pendingFollowRequesters, key = { "req_${it.id}" }) { requester ->
              VybeSurfaceCard(
                borderColor = palette.accent.copy(alpha = 0.35f),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("follow_request_item_${requester.id}"),
                contentPadding = PaddingValues(12.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  VybeAvatar(
                    user = requester,
                    size = 42.dp,
                    onClick = { viewModel.openUserProfile(requester) }
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(
                    modifier = Modifier
                      .weight(1f)
                      .clickable { viewModel.openUserProfile(requester) }
                  ) {
                    Text(requester.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                    Text(
                      "@${requester.username} sent you a LINKUP Request",
                      fontSize = 11.sp,
                      color = palette.textSecondary
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Button(
                    onClick = { viewModel.respondToFollowRequest(requester.id, approve = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.heightIn(min = 38.dp)
                  ) {
                    Text("Accept LINKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  OutlinedButton(
                    onClick = { viewModel.respondToFollowRequest(requester.id, approve = false) },
                    border = BorderStroke(1.dp, palette.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.heightIn(min = 38.dp)
                  ) {
                    Text("Decline", fontSize = 11.sp, color = palette.textSecondary)
                  }
                }
              }
            }
          }

          items(filteredNotifications, key = { it.id }) { item ->
            val resolvedPost = remember(item.targetEntityId, allUsers) {
              item.targetEntityId?.let { tid ->
                viewModel.posts.value.firstOrNull { it.id == tid }
              }
            }
            NotificationCard(
              item = item,
              resolvedPostPreviewUrl = item.contentPreviewUrl ?: resolvedPost?.mediaUrls?.firstOrNull(),
              resolvedContentSnippet = item.contentSnippet ?: resolvedPost?.caption?.takeIf { it.isNotBlank() },
              resolvedIsReel = item.isReel ||
                item.destinationScreen == "REELS_VIEWER" ||
                resolvedPost?.isVybesReel == true ||
                resolvedPost?.mediaType == com.example.data.model.MediaType.REEL,
              onActorClick = {
                item.actorUser?.let { actor ->
                  viewModel.markNotificationAsRead(item.id)
                  viewModel.openUserProfile(actor)
                }
              },
              onClick = {
                viewModel.markNotificationAsRead(item.id)
                when {
                  item.type == NotificationType.BUSINESS_VERIFICATION_UPDATE ||
                    item.destinationScreen == "VERIFICATION_CENTER" -> {
                    viewModel.navigateTo(Screen.VERIFICATION_CENTER)
                  }
                  item.type == NotificationType.DIRECT_MESSAGE || item.type == NotificationType.GROUP_MESSAGE -> {
                    viewModel.selectTab(com.example.ui.MainTab.INBOX)
                    viewModel.navigateTo(Screen.MAIN)
                  }
                  !item.targetEntityId.isNullOrBlank() -> {
                    val targetId = item.targetEntityId
                    val matchingPost = viewModel.posts.value.firstOrNull { it.id == targetId }
                    val openAsReel = item.isReel ||
                      item.destinationScreen == "REELS_VIEWER" ||
                      matchingPost?.isVybesReel == true ||
                      matchingPost?.mediaType == com.example.data.model.MediaType.REEL
                    if (openAsReel) {
                      viewModel.openReelViewer(targetId)
                    } else {
                      viewModel.openPostFromNotification(targetId)
                    }
                  }
                  item.actorUser != null -> {
                    viewModel.openUserProfile(item.actorUser)
                  }
                  else -> {
                    viewModel.selectTab(com.example.ui.MainTab.FEED)
                    viewModel.navigateTo(Screen.MAIN)
                  }
                }
              },
              onDelete = {
                notificationToDelete = item
              }
            )
          }

          item { Spacer(modifier = Modifier.height(24.dp)) }
        }
      }
    }
  }

  if (notificationToDelete != null) {
    AlertDialog(
      onDismissRequest = { notificationToDelete = null },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Text(
          "Delete Notification?",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Text(
          "This notification will be removed across all your signed-in devices.",
          color = palette.textSecondary
        )
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Delete",
          containerColor = palette.danger,
          onClick = {
            val toDel = notificationToDelete
            if (toDel != null) {
              viewModel.deleteNotification(toDel.id)
              notificationToDelete = null
            }
          }
        )
      },
      dismissButton = {
        VybeSecondaryButton(
          text = "Cancel",
          onClick = { notificationToDelete = null }
        )
      }
    )
  }
}

@Composable
fun NotificationCard(
  item: NotificationItem,
  resolvedPostPreviewUrl: String? = item.contentPreviewUrl,
  resolvedContentSnippet: String? = item.contentSnippet,
  resolvedIsReel: Boolean = item.isReel,
  onActorClick: (() -> Unit)? = null,
  onClick: () -> Unit,
  onDelete: () -> Unit
) {
  val palette = LocalVybePalette.current
  val (icon, iconTint) = when (item.type) {
    NotificationType.LIKE -> Pair(Icons.Outlined.FavoriteBorder, reactionColor(VybeReaction.LOVE))
    NotificationType.COMMENT_OR_REPLY -> Pair(Icons.Outlined.ChatBubbleOutline, palette.accent)
    NotificationType.FOLLOW -> Pair(Icons.Outlined.PersonAdd, palette.accent)
    NotificationType.DIRECT_MESSAGE, NotificationType.GROUP_MESSAGE -> Pair(Icons.Outlined.Forum, palette.success)
    NotificationType.ROOM_INVITATION -> Pair(Icons.Outlined.GraphicEq, palette.accent)
    NotificationType.TIME_CAPSULE_INVITATION, NotificationType.TIME_CAPSULE_UNLOCK -> Pair(Icons.Outlined.Schedule, palette.accentSecondary)
    NotificationType.DOUBLE_TAKE_RESPONSE -> Pair(Icons.Outlined.PhotoCamera, palette.accentSecondary)
    NotificationType.CROSSED_PATHS_INTERACTION -> Pair(Icons.Outlined.Place, palette.danger)
    NotificationType.BUSINESS_VERIFICATION_UPDATE -> Pair(Icons.Outlined.Verified, palette.accent)
    NotificationType.LIVE_STREAM_ALERT -> Pair(Icons.Outlined.Sensors, palette.danger)
    NotificationType.SECURITY_ACCOUNT_UPDATE -> Pair(Icons.Outlined.Shield, palette.success)
    NotificationType.SHOP_REVIEW -> Pair(Icons.Outlined.Storefront, palette.warning)
  }

  val actorHeadline = remember(item.actorUser, item.title) {
    val actor = item.actorUser
    if (actor != null && actor.displayName.isNotBlank()) {
      if (actor.username.isNotBlank() && !actor.username.startsWith("user_")) {
        "${actor.displayName} (@${actor.username})"
      } else {
        actor.displayName
      }
    } else {
      item.title
    }
  }

  val actionDescription = remember(item.type, item.message, resolvedIsReel) {
    when (item.type) {
      NotificationType.LIKE -> {
        val contentKind = if (resolvedIsReel) "Reel" else "post"
        if (item.message.contains("Reel", ignoreCase = true) || item.message.contains("post", ignoreCase = true) || item.message.contains("story", ignoreCase = true)) {
          item.message
        } else {
          "Reacted to your $contentKind"
        }
      }
      NotificationType.FOLLOW -> {
        if (item.message.contains("request", ignoreCase = true)) {
          "Sent you a LINKUP Request"
        } else if (item.message.contains("accept", ignoreCase = true)) {
          "Accepted your LINKUP"
        } else {
          "Sent you a new LINKUP"
        }
      }
      else -> {
        item.message
          .replace("started following you", "sent you a new LINKUP")
          .replace("requested to follow you", "sent you a LINKUP Request")
          .replace("accepted your follow request", "accepted your LINKUP")
      }
    }
  }

  VybeSurfaceCard(
    onClick = onClick,
    containerColor = if (!item.isRead) palette.accent.copy(alpha = 0.08f) else palette.card,
    borderColor = if (!item.isRead) palette.accent.copy(alpha = 0.35f) else palette.border,
    contentPadding = PaddingValues(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("notification_item_${item.id}")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (item.actorUser != null) {
        Box(
          modifier = Modifier.size(48.dp),
          contentAlignment = Alignment.BottomEnd
        ) {
          VybeAvatar(
            user = item.actorUser,
            size = 44.dp,
            onClick = onActorClick
          )
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(CircleShape)
              .background(palette.card)
              .padding(1.5.dp)
              .clip(CircleShape)
              .background(if (item.type == NotificationType.LIKE) palette.elevated else iconTint),
            contentAlignment = Alignment.Center
          ) {
            if (item.type == NotificationType.LIKE) {
              com.example.ui.components.FingerHeartIcon(
                isLiked = true,
                size = 13.dp,
                forceDarkSurface = true
              )
            } else {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
              )
            }
          }
        }
      } else {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(iconTint.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          if (item.type == NotificationType.LIKE) {
            com.example.ui.components.FingerHeartIcon(
              isLiked = true,
              size = 22.dp,
              forceDarkSurface = true
            )
          } else {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = iconTint,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = actorHeadline,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 14.sp,
            fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          if (item.actorUser != null) {
            com.example.ui.components.VerificationBadge(user = item.actorUser, iconSize = 14.dp)
          }
          if (!item.isRead) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(palette.accent)
            )
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = actionDescription,
          fontSize = 12.sp,
          color = palette.textSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        if (!resolvedContentSnippet.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = "\"$resolvedContentSnippet\"",
            fontSize = 11.sp,
            color = palette.textPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        val relativeTime = rememberRelativeTimestamp(item.createdAtIso, item.timestamp)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = relativeTime,
            fontSize = 11.sp,
            color = palette.textMuted
          )
          if (!item.targetEntityId.isNullOrBlank()) {
            Text(
              text = if (resolvedIsReel) " • Tap to view Reel" else " • Tap to view post",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = palette.accent
            )
          }
        }
      }

      if (!resolvedPostPreviewUrl.isNullOrBlank()) {
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(VybeTokens.Radius.sm))
            .background(Color.Black.copy(alpha = 0.25f)),
          contentAlignment = Alignment.Center
        ) {
          AsyncImage(
            model = resolvedPostPreviewUrl,
            contentDescription = if (resolvedIsReel) "Liked Reel preview" else "Liked post preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
          if (resolvedIsReel) {
            Box(
              modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = "Reel",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
              )
            }
          }
        }
      } else if (!resolvedContentSnippet.isNullOrBlank() && !item.targetEntityId.isNullOrBlank()) {
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(VybeTokens.Radius.sm),
          color = palette.elevated,
          border = BorderStroke(1.dp, palette.border),
          modifier = Modifier.size(48.dp)
        ) {
          Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = resolvedContentSnippet,
              fontSize = 8.sp,
              lineHeight = 10.sp,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              color = palette.textSecondary,
              textAlign = TextAlign.Center
            )
          }
        }
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier
          .size(48.dp)
          .testTag("delete_notification_${item.id}")
      ) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = "Delete notification",
          modifier = Modifier.size(16.dp),
          tint = palette.textMuted
        )
      }
    }
  }
}

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import com.example.util.rememberRelativeTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

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

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("notifications_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            val totalBadge = unreadCount + pendingFollowRequesters.size
            if (totalBadge > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                color = VybeViolet,
                shape = CircleShape,
                modifier = Modifier.padding(horizontal = 4.dp)
              ) {
                Text(
                  text = "$totalBadge",
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
              }
            }
          }
        },
        actions = {
          IconButton(
            onClick = { viewModel.refreshAllData() },
            modifier = Modifier.testTag("notifications_refresh_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Sync Notifications")
          }
          if (unreadCount > 0) {
            TextButton(
              onClick = { viewModel.markAllNotificationsRead() },
              modifier = Modifier.testTag("mark_all_read_button")
            ) {
              Text("Mark All Read", fontSize = 12.sp, color = VybeBrandCyan, fontWeight = FontWeight.SemiBold)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = !filterUnreadOnly,
          onClick = { filterUnreadOnly = false },
          label = { Text("All (${notifications.size})") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VybeViolet.copy(alpha = 0.2f),
            selectedLabelColor = VybeViolet
          ),
          modifier = Modifier.testTag("filter_all_notifications")
        )
        FilterChip(
          selected = filterUnreadOnly,
          onClick = { filterUnreadOnly = true },
          label = { Text("Unread ($unreadCount)") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VybeViolet.copy(alpha = 0.2f),
            selectedLabelColor = VybeViolet
          ),
          modifier = Modifier.testTag("filter_unread_notifications")
        )
      }

      if (filteredNotifications.isEmpty() && pendingFollowRequesters.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.size(72.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.NotificationsNone,
                  contentDescription = null,
                  modifier = Modifier.size(36.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = if (filterUnreadOnly) "No unread notifications" else "No notifications yet",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Likes, comments, follows, and message alerts from real interactions will appear here.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (pendingFollowRequesters.isNotEmpty()) {
            item {
              Text(
                text = "Follow Requests (${pendingFollowRequesters.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = VybeViolet,
                modifier = Modifier.padding(vertical = 4.dp)
              )
            }
            items(pendingFollowRequesters, key = { "req_${it.id}" }) { requester ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.3f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("follow_request_item_${requester.id}")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
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
                    Text(requester.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                      "@${requester.username} requested to follow you",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Button(
                    onClick = { viewModel.respondToFollowRequest(requester.id, approve = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.heightIn(min = 38.dp)
                  ) {
                    Text("Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  OutlinedButton(
                    onClick = { viewModel.respondToFollowRequest(requester.id, approve = false) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.heightIn(min = 38.dp)
                  ) {
                    Text("Decline", fontSize = 11.sp)
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
        }
      }
    }
  }

  if (notificationToDelete != null) {
    AlertDialog(
      onDismissRequest = { notificationToDelete = null },
      title = { Text("Delete Notification?", fontWeight = FontWeight.Bold) },
      text = { Text("This notification will be removed across all your signed-in devices.") },
      confirmButton = {
        Button(
          onClick = {
            val toDel = notificationToDelete
            if (toDel != null) {
              viewModel.deleteNotification(toDel.id)
              notificationToDelete = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Delete", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { notificationToDelete = null }) {
          Text("Cancel")
        }
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
  val (icon, iconTint) = when (item.type) {
    NotificationType.LIKE -> Pair(Icons.Outlined.FavoriteBorder, VybeMagenta)
    NotificationType.COMMENT_OR_REPLY -> Pair(Icons.Outlined.ChatBubbleOutline, VybeBrandCyan)
    NotificationType.FOLLOW -> Pair(Icons.Outlined.PersonAdd, VybeViolet)
    NotificationType.DIRECT_MESSAGE, NotificationType.GROUP_MESSAGE -> Pair(Icons.Outlined.Forum, Color(0xFF00E676))
    NotificationType.ROOM_INVITATION -> Pair(Icons.Outlined.GraphicEq, VybeBrandCyan)
    NotificationType.TIME_CAPSULE_INVITATION, NotificationType.TIME_CAPSULE_UNLOCK -> Pair(Icons.Outlined.Schedule, Color(0xFFAB47BC))
    NotificationType.DOUBLE_TAKE_RESPONSE -> Pair(Icons.Outlined.PhotoCamera, VybeMagenta)
    NotificationType.CROSSED_PATHS_INTERACTION -> Pair(Icons.Outlined.Place, Color(0xFFFF5252))
    NotificationType.BUSINESS_VERIFICATION_UPDATE -> Pair(Icons.Outlined.Verified, VybeBrandCyan)
    NotificationType.LIVE_STREAM_ALERT -> Pair(Icons.Outlined.Sensors, Color(0xFFFF1744))
    NotificationType.SECURITY_ACCOUNT_UPDATE -> Pair(Icons.Outlined.Shield, Color(0xFF26A69A))
    NotificationType.SHOP_REVIEW -> Pair(Icons.Outlined.Storefront, Color(0xFFFFB300))
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
    if (item.type == NotificationType.LIKE) {
      val contentKind = if (resolvedIsReel) "Reel" else "post"
      if (item.message.contains("Reel", ignoreCase = true) || item.message.contains("post", ignoreCase = true) || item.message.contains("story", ignoreCase = true)) {
        item.message
      } else {
        "Sent a Finger Heart 🫰 on your $contentKind"
      }
    } else {
      item.message
    }
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = if (!item.isRead) VybeViolet.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    border = if (!item.isRead) BorderStroke(1.dp, VybeViolet.copy(alpha = 0.25f)) else null,
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("notification_item_${item.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
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
              .background(MaterialTheme.colorScheme.surface)
              .padding(1.5.dp)
              .clip(CircleShape)
              .background(if (item.type == NotificationType.LIKE) Color(0xFF1F1228) else iconTint),
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
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
                .background(VybeViolet)
            )
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = actionDescription,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        if (!resolvedContentSnippet.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = "\"$resolvedContentSnippet\"",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
          )
          if (!item.targetEntityId.isNullOrBlank()) {
            Text(
              text = if (resolvedIsReel) " • Tap to view Reel" else " • Tap to view post",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = VybeBrandCyan
            )
          }
        }
      }

      // Content preview thumbnail or text-post preview tile on right
      if (!resolvedPostPreviewUrl.isNullOrBlank()) {
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
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
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
              color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

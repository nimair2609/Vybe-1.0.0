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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationType
import com.example.data.model.User
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

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
            NotificationCard(
              item = item,
              onClick = {
                viewModel.markNotificationAsRead(item.id)
                item.actorUser?.let { actor ->
                  viewModel.openUserProfile(actor)
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
  onClick: () -> Unit,
  onDelete: () -> Unit
) {
  val (icon, iconTint) = when (item.type) {
    NotificationType.LIKE -> Pair(Icons.Default.Favorite, Color(0xFFFF5252))
    NotificationType.COMMENT_OR_REPLY -> Pair(Icons.Default.Comment, VybeBrandCyan)
    NotificationType.FOLLOW -> Pair(Icons.Default.PersonAdd, VybeViolet)
    NotificationType.DIRECT_MESSAGE, NotificationType.GROUP_MESSAGE -> Pair(Icons.Default.ChatBubble, Color(0xFF00E676))
    NotificationType.ROOM_INVITATION -> Pair(Icons.Default.Hearing, VybeBrandCyan)
    NotificationType.TIME_CAPSULE_INVITATION, NotificationType.TIME_CAPSULE_UNLOCK -> Pair(Icons.Default.LockClock, Color(0xFFAB47BC))
    NotificationType.DOUBLE_TAKE_RESPONSE -> Pair(Icons.Default.CameraAlt, VybeMagenta)
    NotificationType.CROSSED_PATHS_INTERACTION -> Pair(Icons.Default.Place, Color(0xFFFF5252))
    NotificationType.BUSINESS_VERIFICATION_UPDATE -> Pair(Icons.Default.Verified, VybeBrandCyan)
    NotificationType.LIVE_STREAM_ALERT -> Pair(Icons.Default.Podcasts, Color(0xFFFF1744))
    NotificationType.SECURITY_ACCOUNT_UPDATE -> Pair(Icons.Default.Security, Color(0xFF26A69A))
    NotificationType.SHOP_REVIEW -> Pair(Icons.Default.Storefront, Color(0xFFFFB300))
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
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(iconTint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
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
          text = item.message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = item.timestamp,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(36.dp).testTag("delete_notification_${item.id}")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Delete notification",
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationType
import com.example.ui.VybeViewModel
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val notifications by viewModel.notifications.collectAsState()
  var filterUnreadOnly by remember { mutableStateOf(false) }
  var notificationToDelete by remember { mutableStateOf<NotificationItem?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }

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
            if (unreadCount > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                color = VybeViolet,
                shape = CircleShape,
                modifier = Modifier.padding(horizontal = 4.dp)
              ) {
                Text(
                  text = "$unreadCount",
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
          if (notifications.isNotEmpty()) {
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
      // Filter Tabs
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

      if (filteredNotifications.isEmpty()) {
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
              text = if (filterUnreadOnly) "No unread notifications" else "You're all caught up!",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "New vibes, comments, and connections will appear here.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredNotifications, key = { it.id }) { item ->
            NotificationCard(
              item = item,
              onClick = {
                viewModel.markNotificationAsRead(item.id)
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

  // Confirmation Dialog for deletion
  if (notificationToDelete != null) {
    AlertDialog(
      onDismissRequest = { notificationToDelete = null },
      title = { Text("Delete Notification?", fontWeight = FontWeight.Bold) },
      text = { Text("This notification will be permanently removed from your feed.") },
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
    NotificationType.COMMENT_OR_REPLY -> Pair(Icons.Default.Comment, VybeBrandCyan)
    NotificationType.POST_REACTION -> Pair(Icons.Default.ElectricBolt, Color(0xFFFFB300))
    NotificationType.NEW_FOLLOWER -> Pair(Icons.Default.PersonAdd, VybeViolet)
    NotificationType.DIRECT_MESSAGE -> Pair(Icons.Default.ChatBubble, Color(0xFF00E676))
    NotificationType.CROSSED_PATHS_ALERT -> Pair(Icons.Default.Place, Color(0xFFFF5252))
    NotificationType.TIME_CAPSULE_UNLOCKED -> Pair(Icons.Default.LockClock, Color(0xFFAB47BC))
    NotificationType.UNLOAD_ROOM_INVITE -> Pair(Icons.Default.Hearing, VybeBrandCyan)
    NotificationType.LIVE_STREAM_STARTED -> Pair(Icons.Default.Podcasts, Color(0xFFFF1744))
    NotificationType.SPIRITUAL_REMINDER -> Pair(Icons.Default.SelfImprovement, Color(0xFF26A69A))
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = if (!item.isRead) VybeViolet.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    border = if (!item.isRead) androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.25f)) else null,
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
        modifier = Modifier.size(32.dp).testTag("delete_notification_${item.id}")
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

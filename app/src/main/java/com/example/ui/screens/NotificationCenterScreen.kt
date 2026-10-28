package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.notifications.VybeNotificationHelper
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import com.example.util.TimestampUtils
import com.example.util.rememberRelativeTimestamp
import kotlinx.coroutines.launch

private enum class ActivityFilter(val label: String) {
  ALL("All"),
  UNREAD("Unread"),
  MESSAGES("Direct"),
  SOCIAL("Likes & Comments"),
  FOLLOWS("Follows")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val notifications by viewModel.notifications.collectAsState()
  val incomingFollowRequestUserIds by viewModel.incomingFollowRequestUserIds.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val isRealtimeConnected by viewModel.isRealtimeConnected.collectAsState()

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()
  var activeFilter by remember { mutableStateOf(ActivityFilter.ALL) }
  var notificationToDelete by remember { mutableStateOf<NotificationItem?>(null) }
  var showQuickBroadcastDialog by remember { mutableStateOf(false) }
  var phoneNotificationsEnabled by remember {
    mutableStateOf(VybeNotificationHelper.arePhoneNotificationsEnabled(context))
  }
  val snackbarHostState = remember { SnackbarHostState() }

  val notifPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    phoneNotificationsEnabled = VybeNotificationHelper.arePhoneNotificationsEnabled(context)
    if (!granted) {
      VybeNotificationHelper.openSystemNotificationSettings(context)
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        phoneNotificationsEnabled = VybeNotificationHelper.arePhoneNotificationsEnabled(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

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
  val filteredNotifications = remember(notifications, activeFilter) {
    when (activeFilter) {
      ActivityFilter.ALL -> notifications
      ActivityFilter.UNREAD -> notifications.filter { !it.isRead }
      ActivityFilter.MESSAGES -> notifications.filter {
        it.type == NotificationType.DIRECT_MESSAGE || it.type == NotificationType.GROUP_MESSAGE
      }
      ActivityFilter.SOCIAL -> notifications.filter {
        it.type == NotificationType.LIKE || it.type == NotificationType.COMMENT_OR_REPLY
      }
      ActivityFilter.FOLLOWS -> notifications.filter {
        it.type == NotificationType.FOLLOW
      }
    }
  }

  val nowMs = System.currentTimeMillis()
  val dayMs = 24L * 60L * 60L * 1000L
  val groupedSections = remember(filteredNotifications, nowMs) {
    val unreadNew = mutableListOf<NotificationItem>()
    val today = mutableListOf<NotificationItem>()
    val thisWeek = mutableListOf<NotificationItem>()
    val earlier = mutableListOf<NotificationItem>()

    filteredNotifications.forEach { n ->
      if (!n.isRead) {
        unreadNew.add(n)
      } else {
        val createdMs = TimestampUtils.parseIsoMillis(n.createdAtIso)
        val age = if (createdMs != null) (nowMs - createdMs) else Long.MAX_VALUE
        when {
          age <= dayMs -> today.add(n)
          age <= 7L * dayMs -> thisWeek.add(n)
          else -> earlier.add(n)
        }
      }
    }
    listOf(
      "New" to unreadNew,
      "Today" to today,
      "This Week" to thisWeek,
      "Earlier" to earlier
    ).filter { it.second.isNotEmpty() }
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
            Text("Activity", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            val totalBadge = unreadCount + pendingFollowRequesters.size
            if (totalBadge > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                color = VybeMagenta,
                shape = CircleShape
              ) {
                Text(
                  text = "$totalBadge",
                  color = Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
              color = if (isRealtimeConnected) VybeOnlineGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(
                1.dp,
                if (isRealtimeConnected) VybeOnlineGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
              )
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isRealtimeConnected) VybeOnlineGreen else Color.Gray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (isRealtimeConnected) "LIVE" else "SYNC",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = if (isRealtimeConnected) VybeOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        },
        actions = {
          if (currentUser?.role == UserRole.ADMIN) {
            IconButton(
              onClick = { showQuickBroadcastDialog = true },
              modifier = Modifier.testTag("open_quick_broadcast_button")
            ) {
              Icon(
                Icons.Outlined.Campaign,
                contentDescription = "Broadcast Official Notification",
                tint = VybeVerifiedGold
              )
            }
          }
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
              Text("Mark Read", fontSize = 12.sp, color = VybeBrandCyan, fontWeight = FontWeight.SemiBold)
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
      if (!phoneNotificationsEnabled) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("enable_phone_notifications_banner"),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = VybeViolet.copy(alpha = 0.14f)),
          border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.45f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(
              Icons.Outlined.NotificationsActive,
              contentDescription = "Enable phone notifications",
              tint = VybeViolet,
              modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Turn On Real-Time Phone Alerts",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Get Instagram-style Direct replies, likes, comments, and live calls directly on your lockscreen.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Button(
              onClick = {
                if (
                  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                  ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                  notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  VybeNotificationHelper.openSystemNotificationSettings(context)
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier.testTag("enable_phone_notifications_button")
            ) {
              Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }

      // Instagram-style Horizontal Filter Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ActivityFilter.values().forEach { filter ->
          val tag = when (filter) {
            ActivityFilter.ALL -> "filter_all_notifications"
            ActivityFilter.UNREAD -> "filter_unread_notifications"
            else -> "filter_${filter.name.lowercase()}_notifications"
          }
          val labelText = when (filter) {
            ActivityFilter.ALL -> "All (${notifications.size})"
            ActivityFilter.UNREAD -> "Unread ($unreadCount)"
            else -> filter.label
          }
          FilterChip(
            selected = activeFilter == filter,
            onClick = { activeFilter = filter },
            label = { Text(labelText, fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = VybeViolet.copy(alpha = 0.2f),
              selectedLabelColor = VybeViolet
            ),
            modifier = Modifier.testTag(tag)
          )
        }
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
              text = if (activeFilter == ActivityFilter.UNREAD) "You're all caught up" else "No activity yet",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Real-time Direct messages, likes, comments, follows, and official VYBE drops appear here instantly.",
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
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (pendingFollowRequesters.isNotEmpty()) {
            item {
              Text(
                text = "Follow Requests (${pendingFollowRequesters.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
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
                    size = 44.dp,
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
                    modifier = Modifier.heightIn(min = 40.dp)
                  ) {
                    Text("Confirm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  OutlinedButton(
                    onClick = { viewModel.respondToFollowRequest(requester.id, approve = false) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.heightIn(min = 40.dp)
                  ) {
                    Text("Delete", fontSize = 11.sp)
                  }
                }
              }
            }
          }

          groupedSections.forEach { (sectionTitle, sectionItems) ->
            item(key = "header_$sectionTitle") {
              Text(
                text = sectionTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
              )
            }

            items(sectionItems, key = { it.id }) { item ->
              val resolvedPost = remember(item.targetEntityId, posts) {
                item.targetEntityId?.let { tid ->
                  posts.firstOrNull { it.id == tid }
                }
              }
              val actorId = item.actorUser?.id
              val isFollowingActor = actorId != null && followingUserIds.contains(actorId)
              val isRequestedActor = actorId != null && requestedFollowUserIds.contains(actorId)

              NotificationCard(
                item = item,
                resolvedPostPreviewUrl = item.contentPreviewUrl ?: resolvedPost?.mediaUrls?.firstOrNull(),
                resolvedContentSnippet = item.contentSnippet ?: resolvedPost?.caption?.takeIf { it.isNotBlank() },
                resolvedIsReel = item.isReel ||
                  item.destinationScreen == "REELS_VIEWER" ||
                  resolvedPost?.isVybesReel == true ||
                  resolvedPost?.mediaType == MediaType.REEL,
                isFollowingActor = isFollowingActor,
                isFollowRequestedActor = isRequestedActor,
                onToggleFollowActor = if (
                  item.type == NotificationType.FOLLOW &&
                  actorId != null &&
                  actorId != currentUser?.id
                ) {
                  { viewModel.toggleFollowUser(actorId) }
                } else null,
                onShareNotification = {
                  shareNotificationExternally(context, item)
                },
                onActorClick = {
                  item.actorUser?.let { actor ->
                    viewModel.markNotificationAsRead(item.id)
                    viewModel.openUserProfile(actor)
                  }
                },
                onClick = {
                  viewModel.markNotificationAsRead(item.id)
                  when {
                    item.type == NotificationType.DIRECT_MESSAGE || item.type == NotificationType.GROUP_MESSAGE -> {
                      val tid = item.targetEntityId
                      val existingConv = if (!tid.isNullOrBlank()) {
                        viewModel.conversations.value.firstOrNull { it.id == tid }
                      } else null
                      if (existingConv != null) {
                        viewModel.openConversation(existingConv)
                      } else {
                        viewModel.selectTab(MainTab.INBOX)
                        viewModel.navigateTo(Screen.MAIN)
                      }
                    }
                    !item.targetEntityId.isNullOrBlank() -> {
                      val targetId = item.targetEntityId
                      val matchingPost = posts.firstOrNull { it.id == targetId }
                      val openAsReel = item.isReel ||
                        item.destinationScreen == "REELS_VIEWER" ||
                        matchingPost?.isVybesReel == true ||
                        matchingPost?.mediaType == MediaType.REEL
                      if (openAsReel) {
                        viewModel.openReelViewer(targetId)
                      } else if (item.type == NotificationType.FOLLOW && item.actorUser != null) {
                        viewModel.openUserProfile(item.actorUser)
                      } else {
                        viewModel.openPostFromNotification(targetId)
                      }
                    }
                    item.actorUser != null -> {
                      viewModel.openUserProfile(item.actorUser)
                    }
                    else -> {
                      viewModel.selectTab(MainTab.FEED)
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
  }

  if (showQuickBroadcastDialog) {
    QuickAdminBroadcastDialog(
      onDismiss = { showQuickBroadcastDialog = false },
      onSend = { title, body, mediaUrl ->
        showQuickBroadcastDialog = false
        viewModel.sendAdminBroadcastNotification(
          title = title,
          body = body,
          destination = "NOTIFICATIONS",
          mediaUrl = mediaUrl
        ) { _, msg ->
          coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
          }
        }
      }
    )
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

private fun shareNotificationExternally(context: Context, item: NotificationItem) {
  val actorHandle = item.actorUser?.username?.takeIf { it.isNotBlank() && !it.startsWith("user_") }?.let { "@$it" }
  val header = actorHandle ?: item.actorUser?.displayName ?: item.title
  val formatted = buildString {
    append("⚡ VYBE Update\n")
    append("$header — ${item.message}")
    if (!item.contentSnippet.isNullOrBlank()) {
      append("\n\"${item.contentSnippet}\"")
    }
  }
  val sendIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, item.title)
    putExtra(Intent.EXTRA_TEXT, formatted)
  }
  context.startActivity(Intent.createChooser(sendIntent, "Share notification as message"))
}

@Composable
private fun QuickAdminBroadcastDialog(
  onDismiss: () -> Unit,
  onSend: (title: String, body: String, mediaUrl: String?) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var mediaUrl by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Campaign, contentDescription = null, tint = VybeVerifiedGold)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Broadcast Real-Time Push", fontWeight = FontWeight.Bold, fontSize = 17.sp)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "Delivers an instant Instagram-style push alert to all connected VYBE phones (<100ms) and saves to Activity.",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Headline (e.g., VYBE Live Drop 🔥)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = body,
          onValueChange = { body = it },
          label = { Text("Message") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = mediaUrl,
          onValueChange = { mediaUrl = it },
          label = { Text("Banner Image URL (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSend(title, body, mediaUrl.takeIf { it.isNotBlank() }) },
        enabled = title.isNotBlank() && body.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
      ) {
        Text("Send Live Push", color = Color.White, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun NotificationCard(
  item: NotificationItem,
  resolvedPostPreviewUrl: String? = item.contentPreviewUrl,
  resolvedContentSnippet: String? = item.contentSnippet,
  resolvedIsReel: Boolean = item.isReel,
  isFollowingActor: Boolean = false,
  isFollowRequestedActor: Boolean = false,
  onToggleFollowActor: (() -> Unit)? = null,
  onShareNotification: (() -> Unit)? = null,
  onActorClick: (() -> Unit)? = null,
  onClick: () -> Unit,
  onDelete: () -> Unit
) {
  val (icon, iconTint) = when (item.type) {
    NotificationType.LIKE -> Pair(Icons.Filled.Favorite, VybeMagenta)
    NotificationType.COMMENT_OR_REPLY -> Pair(Icons.Outlined.ChatBubbleOutline, VybeBrandCyan)
    NotificationType.FOLLOW -> Pair(Icons.Outlined.PersonAdd, VybeViolet)
    NotificationType.DIRECT_MESSAGE, NotificationType.GROUP_MESSAGE -> Pair(Icons.Outlined.Forum, Color(0xFF00E676))
    NotificationType.ROOM_INVITATION -> Pair(Icons.Outlined.GraphicEq, VybeBrandCyan)
    NotificationType.TIME_CAPSULE_INVITATION, NotificationType.TIME_CAPSULE_UNLOCK -> Pair(Icons.Outlined.Schedule, Color(0xFFAB47BC))
    NotificationType.DOUBLE_TAKE_RESPONSE -> Pair(Icons.Outlined.PhotoCamera, VybeMagenta)
    NotificationType.CROSSED_PATHS_INTERACTION -> Pair(Icons.Outlined.Place, Color(0xFFFF5252))
    NotificationType.BUSINESS_VERIFICATION_UPDATE -> Pair(Icons.Outlined.Verified, VybeBrandCyan)
    NotificationType.LIVE_STREAM_ALERT -> Pair(Icons.Outlined.Sensors, Color(0xFFFF1744))
    NotificationType.SECURITY_ACCOUNT_UPDATE -> Pair(Icons.Outlined.Campaign, VybeVerifiedGold)
    NotificationType.SHOP_REVIEW -> Pair(Icons.Outlined.Storefront, Color(0xFFFFB300))
  }

  val actorHandle = remember(item.actorUser, item.title) {
    val actor = item.actorUser
    when {
      actor != null && actor.username.isNotBlank() && !actor.username.startsWith("user_") ->
        actor.username
      actor != null && actor.displayName.isNotBlank() ->
        actor.displayName
      else -> item.title
    }
  }

  val actionDescription = remember(item.type, item.message, resolvedIsReel) {
    when (item.type) {
      NotificationType.LIKE -> {
        val contentKind = if (resolvedIsReel) "Reel" else "post"
        if (item.message.contains("Reel", ignoreCase = true) || item.message.contains("post", ignoreCase = true)) {
          item.message
        } else {
          "liked your $contentKind."
        }
      }
      NotificationType.FOLLOW -> "started following you."
      else -> item.message
    }
  }

  val relativeTime = rememberRelativeTimestamp(item.createdAtIso, item.timestamp)

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = if (!item.isRead) VybeViolet.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    border = if (!item.isRead) BorderStroke(1.dp, VybeViolet.copy(alpha = 0.28f)) else null,
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("notification_item_${item.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
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
              .size(19.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surface)
              .padding(1.5.dp)
              .clip(CircleShape)
              .background(iconTint),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(11.dp)
            )
          }
        }
      } else {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(iconTint.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(21.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      // Instagram-style inline rich sentence: "@username liked your post. 2m"
      Column(modifier = Modifier.weight(1f)) {
        val richText = buildAnnotatedString {
          withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
            append(actorHandle)
          }
          append(" ")
          withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))) {
            append(actionDescription)
          }
          append("  ")
          withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), fontSize = 11.sp)) {
            append(relativeTime)
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = richText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
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

        if (!resolvedContentSnippet.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(3.dp))
          Text(
            text = "\"$resolvedContentSnippet\"",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Right-hand Action or Post Thumbnail (Instagram-style Follow Back button or Square Media Preview)
      if (onToggleFollowActor != null) {
        Spacer(modifier = Modifier.width(8.dp))
        val isActiveFollow = isFollowingActor || isFollowRequestedActor
        Button(
          onClick = onToggleFollowActor,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isActiveFollow) MaterialTheme.colorScheme.surfaceVariant else VybeViolet,
            contentColor = if (isActiveFollow) MaterialTheme.colorScheme.onSurface else Color.White
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          modifier = Modifier
            .heightIn(min = 34.dp)
            .testTag("follow_back_button_${item.id}")
        ) {
          Text(
            text = when {
              isFollowingActor -> "Following"
              isFollowRequestedActor -> "Requested"
              else -> "Follow Back"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
        }
      } else if (!resolvedPostPreviewUrl.isNullOrBlank()) {
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.25f)),
          contentAlignment = Alignment.Center
        ) {
          AsyncImage(
            model = resolvedPostPreviewUrl,
            contentDescription = if (resolvedIsReel) "Reel preview" else "Post preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
          if (resolvedIsReel) {
            Box(
              modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = "Reel",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
              )
            }
          }
        }
      }

      if (onShareNotification != null) {
        IconButton(
          onClick = onShareNotification,
          modifier = Modifier
            .size(38.dp)
            .testTag("share_notification_${item.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = "Share notification",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier
          .size(38.dp)
          .testTag("delete_notification_${item.id}")
      ) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = "Delete notification",
          modifier = Modifier.size(15.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

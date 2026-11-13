package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallAvailabilityResult
import com.example.data.model.Conversation
import com.example.data.model.User
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSearchBar
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import com.example.util.rememberMessageTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
  viewModel: VybeViewModel,
  embeddedInMainScreen: Boolean = false
) {
  val palette = LocalVybePalette.current
  val conversations by viewModel.conversations.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val allCommunityUsers by viewModel.allUsers.collectAsState()

  var currentTab by remember { mutableStateOf("Chats") }
  var searchQuery by remember { mutableStateOf("") }
  val inboxListState = rememberLazyListState()

  // New Chat / Group modal state
  var showNewChatModal by remember { mutableStateOf(false) }
  var newGroupMode by remember { mutableStateOf(false) }
  var groupNameInput by remember { mutableStateOf("") }
  var searchUserInput by remember { mutableStateOf("") }
  var newChatError by remember { mutableStateOf<String?>(null) }
  val selectedGroupMembers = remember { mutableStateListOf<User>() }

  // Hidden Chats PIN State (cryptographic hash only, no hardcoded default PIN)
  var isHiddenUnlocked by remember { mutableStateOf(false) }
  var showPinDialog by remember { mutableStateOf(false) }
  var pinInput by remember { mutableStateOf("") }
  var pinError by remember { mutableStateOf(false) }
  var showSetPinDialog by remember { mutableStateOf(false) }
  var newPinInput by remember { mutableStateOf("") }
  var convToLock by remember { mutableStateOf<Conversation?>(null) }
  var lockPinInput by remember { mutableStateOf("") }
  var lockPinError by remember { mutableStateOf<String?>(null) }

  var conversationToManage by remember { mutableStateOf<Conversation?>(null) }
  var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }

  LaunchedEffect(Unit) {
    viewModel.syncConversations()
  }

  val displayedConversations = remember(conversations, currentTab, searchQuery, isHiddenUnlocked) {
    var list = conversations
    if (searchQuery.isNotBlank()) {
      val q = searchQuery.trim().lowercase()
      list = list.filter {
        it.peerUser.displayName.lowercase().contains(q) ||
          it.peerUser.username.lowercase().contains(q) ||
          it.lastMessage.lowercase().contains(q) ||
          (it.groupName?.lowercase()?.contains(q) == true)
      }
    }

    val filtered = when (currentTab) {
      "Chats" -> list.filter { !it.isMessageRequest && !it.isHidden }
      "Message Requests" -> list.filter { it.isMessageRequest && !it.isHidden }
      "Hidden Chats" -> if (isHiddenUnlocked) list.filter { it.isHidden } else emptyList()
      else -> list
    }
    filtered.sortedWith(
      compareByDescending<Conversation> { it.lastMessageCreatedAtIso ?: "" }
        .thenByDescending { it.messages.lastOrNull()?.createdAtIso ?: "" }
        .thenBy { it.id }
    )
  }

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      Surface(color = palette.canvas) {
        Column(modifier = if (embeddedInMainScreen) Modifier else Modifier.statusBarsPadding()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = VybeTokens.Size.topBarHeight)
              .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = "Messages",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = palette.textPrimary
              )
              val activeCount = conversations.count { !it.isHidden }
              Text(
                text = "$activeCount conversations",
                fontSize = 11.sp,
                color = palette.textMuted
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { viewModel.refreshAllData() },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("inbox_refresh_button")
              ) {
                Icon(
                  Icons.Default.Refresh,
                  contentDescription = "Sync Messages",
                  tint = palette.textSecondary
                )
              }
              Spacer(modifier = Modifier.width(4.dp))
              Surface(
                color = palette.accent,
                shape = CircleShape,
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .clickable {
                    searchUserInput = ""
                    groupNameInput = ""
                    newGroupMode = false
                    newChatError = null
                    selectedGroupMembers.clear()
                    showNewChatModal = true
                  }
                  .testTag("new_chat_button")
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Default.Edit,
                    contentDescription = "New Message",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
          HorizontalDivider(thickness = 0.8.dp, color = palette.border)
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      state = inboxListState,
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .testTag("inbox_conversations_list")
    ) {
      // Search Bar
      item(key = "inbox_search_bar") {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
          VybeSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search by @username, name, or message...",
            testTag = "inbox_search_field"
          )
        }
      }

      // Sub tabs: Chats, Message Requests, Hidden Chats
      item(key = "inbox_sub_tabs") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("Chats", "Message Requests", "Hidden Chats").forEach { tab ->
            val isSelected = currentTab == tab
            Surface(
              color = if (isSelected) palette.accent else palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              border = BorderStroke(
                1.dp,
                if (isSelected) palette.accent else palette.border
              ),
              modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                .clickable {
                  if (tab == "Hidden Chats" && !isHiddenUnlocked) {
                    pinInput = ""
                    pinError = false
                    if (viewModel.hasHiddenChatPinSet()) {
                      showPinDialog = true
                    } else {
                      newPinInput = ""
                      showSetPinDialog = true
                    }
                  } else {
                    currentTab = tab
                  }
                }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (tab == "Hidden Chats") {
                  Icon(
                    imageVector = if (isHiddenUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isSelected) Color.White else palette.textSecondary
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                  text = tab,
                  fontFamily = PlusJakartaSansFontFamily,
                  color = if (isSelected) Color.White else palette.textSecondary,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                  fontSize = 12.sp,
                  maxLines = 1
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
      }

      if (currentTab == "Hidden Chats" && !isHiddenUnlocked) {
        item {
          Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
            VybeEmptyState(
              icon = Icons.Default.Lock,
              title = "Hidden Chats are Locked",
              subtitle = "Protected behind your private secret PIN. Plaintext PINs are never stored.",
              actionLabel = if (viewModel.hasHiddenChatPinSet()) "Enter PIN to Unlock" else "Create Secret PIN",
              onAction = {
                if (viewModel.hasHiddenChatPinSet()) {
                  pinInput = ""
                  pinError = false
                  showPinDialog = true
                } else {
                  newPinInput = ""
                  showSetPinDialog = true
                }
              }
            )
          }
        }
      } else if (displayedConversations.isEmpty()) {
        item {
          Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
            VybeEmptyState(
              icon = if (currentTab == "Message Requests") Icons.Default.Shield else Icons.Outlined.ChatBubbleOutline,
              title = when {
                searchQuery.isNotEmpty() -> "No conversations matching \"$searchQuery\""
                currentTab == "Message Requests" -> "No Pending Message Requests"
                currentTab == "Hidden Chats" -> "No Hidden Conversations"
                else -> "Your Inbox is Empty"
              },
              subtitle = when {
                currentTab == "Message Requests" -> "New requests and Crossed Paths handshakes appear here."
                currentTab == "Hidden Chats" -> "Lock any conversation from its options menu to move it here."
                else -> "Start a real conversation with people you connect with on VYBE."
              },
              actionLabel = if (currentTab == "Chats" && searchQuery.isEmpty()) "New Message" else null,
              onAction = if (currentTab == "Chats" && searchQuery.isEmpty()) {
                {
                  searchUserInput = ""
                  groupNameInput = ""
                  newGroupMode = false
                  newChatError = null
                  selectedGroupMembers.clear()
                  showNewChatModal = true
                }
              } else null
            )
          }
        }
      } else {
        items(
          items = displayedConversations,
          key = { conv -> if (conv.isGroup) "grp_${conv.groupName ?: conv.id}" else "dm_${conv.peerUser.id}" }
        ) { conv ->
          val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { dismissVal ->
              when (dismissVal) {
                SwipeToDismissBoxValue.EndToStart -> {
                  conversationToDelete = conv
                  false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                  viewModel.toggleConversationUnread(conv.id)
                  false
                }
                else -> false
              }
            }
          )

          val isUnread = conv.unreadCount > 0
          val startToEndLabel = if (isUnread) "Mark as Read" else "Mark as Unread"
          val startToEndIcon = if (isUnread) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread

          SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
              val direction = dismissState.dismissDirection
              val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> if (isUnread) palette.success else palette.accent
                SwipeToDismissBoxValue.EndToStart -> palette.danger
                else -> Color.Transparent
              }
              val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
              }
              val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> startToEndIcon
                else -> Icons.Default.Delete
              }

              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 16.dp, vertical = 5.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.lg))
                  .background(color)
                  .padding(horizontal = 20.dp),
                contentAlignment = alignment
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(icon, contentDescription = null, tint = Color.White)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (direction == SwipeToDismissBoxValue.StartToEnd) startToEndLabel else "Delete Chat",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                }
              }
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            VybeSurfaceCard(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .testTag("conversation_item_${conv.id}"),
              onClick = { viewModel.openConversation(conv) },
              borderColor = if (isUnread) palette.accent.copy(alpha = 0.45f) else palette.border,
              contentPadding = PaddingValues(12.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box {
                  VybeAvatar(user = conv.peerUser, size = 48.dp)
                  if (!conv.isGroup && conv.peerUser.isOnline && !conv.isBlocked) {
                    Box(
                      modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(palette.card)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(palette.success)
                    )
                  }
                  if (conv.unreadCount > 0) {
                    Surface(
                      shape = CircleShape,
                      color = palette.accent,
                      modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                    ) {
                      Text(
                        text = "${conv.unreadCount}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    Text(
                      text = conv.groupName ?: (conv.customNickname ?: conv.peerUser.displayName),
                      fontFamily = PlusJakartaSansFontFamily,
                      fontWeight = FontWeight.Bold,
                      fontSize = 15.sp,
                      color = palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!conv.isGroup) {
                      VerificationBadge(user = conv.peerUser)
                    } else {
                      Surface(
                        color = palette.accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(VybeTokens.Radius.xs)
                      ) {
                        Text(
                          "Group",
                          fontSize = 9.sp,
                          color = palette.accent,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                      }
                    }

                    if (conv.isMuted) {
                      Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Muted",
                        tint = palette.textMuted,
                        modifier = Modifier.size(14.dp)
                      )
                    }

                    if (conv.isBlocked) {
                      Surface(
                        color = palette.danger.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(VybeTokens.Radius.xs)
                      ) {
                        Text(
                          "Blocked",
                          fontSize = 9.sp,
                          color = palette.danger,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                      }
                    }

                    if (conv.isRestricted) {
                      Surface(
                        color = palette.warning.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(VybeTokens.Radius.xs)
                      ) {
                        Text(
                          "Restricted",
                          fontSize = 9.sp,
                          color = palette.warning,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(3.dp))

                  Text(
                    text = conv.lastMessage.ifBlank { "Tap to send a message" },
                    fontSize = 13.sp,
                    color = if (conv.unreadCount > 0) palette.textPrimary else palette.textSecondary,
                    fontWeight = if (conv.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(horizontalAlignment = Alignment.End) {
                  val relativeLastTime = rememberMessageTimestamp(
                    conv.lastMessageCreatedAtIso,
                    conv.lastMessageTimestamp
                  )
                  Text(
                    text = relativeLastTime,
                    fontSize = 11.sp,
                    color = if (conv.unreadCount > 0) palette.accent else palette.textMuted,
                    fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!conv.isGroup) {
                      IconButton(
                        onClick = {
                          when (val res = viewModel.initiateCall(conv.peerUser, isVideo = false)) {
                            is CallAvailabilityResult.Unavailable -> viewModel.postBannerMessage(res.reason)
                            is CallAvailabilityResult.Offline -> viewModel.postBannerMessage(res.reason)
                            is CallAvailabilityResult.Blocked -> viewModel.postBannerMessage(res.reason)
                            is CallAvailabilityResult.Error -> viewModel.postBannerMessage(res.message)
                            else -> {}
                          }
                        },
                        modifier = Modifier
                          .size(36.dp)
                          .testTag("voice_call_btn_${conv.id}")
                      ) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = palette.accent, modifier = Modifier.size(18.dp))
                      }
                      IconButton(
                        onClick = {
                          when (val res = viewModel.initiateCall(conv.peerUser, isVideo = true)) {
                            is CallAvailabilityResult.Unavailable -> viewModel.postBannerMessage(res.reason)
                            is CallAvailabilityResult.Offline -> viewModel.postBannerMessage(res.reason)
                            is CallAvailabilityResult.Blocked -> viewModel.postBannerMessage(res.reason)
                            is CallAvailabilityResult.Error -> viewModel.postBannerMessage(res.message)
                            else -> {}
                          }
                        },
                        modifier = Modifier
                          .size(36.dp)
                          .testTag("video_call_btn_${conv.id}")
                      ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = palette.accentSecondary, modifier = Modifier.size(18.dp))
                      }
                    }
                    IconButton(
                      onClick = { conversationToManage = conv },
                      modifier = Modifier.size(36.dp)
                    ) {
                      Icon(Icons.Default.MoreVert, contentDescription = "Manage", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
                    }
                  }
                }
              }
            }
          }
        }
      }

      item(key = "inbox_bottom_spacer") {
        Spacer(modifier = Modifier.height(88.dp))
      }
    }
  }

  // Conversation Manage Sheet
  if (conversationToManage != null) {
    val targetConv = conversationToManage!!
    AlertDialog(
      onDismissRequest = { conversationToManage = null },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Text(
          targetConv.groupName ?: targetConv.peerUser.displayName,
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            color = palette.elevated,
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(VybeTokens.Radius.md))
              .clickable {
                convToLock = targetConv
                lockPinInput = ""
                lockPinError = null
                conversationToManage = null
              }
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                if (targetConv.isHidden) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                tint = palette.accent
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                if (targetConv.isHidden) "Unhide Conversation" else "Hide Conversation (Lock behind PIN)",
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
          Surface(
            color = palette.elevated,
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(VybeTokens.Radius.md))
              .clickable {
                viewModel.toggleMuteChat(targetConv.id)
                conversationToManage = null
              }
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                if (targetConv.isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                contentDescription = null,
                tint = palette.textSecondary
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                if (targetConv.isMuted) "Unmute Notifications" else "Mute Notifications",
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { conversationToManage = null }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }

  // Lock / Unlock Conversation PIN Dialog
  if (convToLock != null) {
    val target = convToLock!!
    val hasExistingPin = viewModel.hasHiddenChatPinSet()
    AlertDialog(
      onDismissRequest = { convToLock = null },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = palette.accent) },
      title = {
        Text(
          text = when {
            target.isHidden -> "Enter PIN to Unhide"
            hasExistingPin -> "Confirm PIN to Lock Chat"
            else -> "Set Secret PIN to Lock Chat"
          },
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = when {
              target.isHidden -> "Enter your secret PIN to restore this conversation to your main Chats tab:"
              hasExistingPin -> "Enter your secret PIN to move this conversation to Hidden Chats:"
              else -> "Create a 4-to-6 digit secret PIN to protect your Hidden Chats:"
            },
            fontSize = 12.sp,
            color = palette.textSecondary
          )
          OutlinedTextField(
            value = lockPinInput,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) lockPinInput = it },
            label = { Text("Secret PIN (4-6 digits)") },
            singleLine = true,
            isError = lockPinError != null,
            supportingText = {
              if (lockPinError != null) Text(lockPinError ?: "", color = palette.danger)
            },
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = if (target.isHidden) "Unhide Chat" else "Lock Chat",
          onClick = {
            if (lockPinInput.length < 4) {
              lockPinError = "PIN must be at least 4 digits."
            } else if (hasExistingPin && !viewModel.verifyHiddenChatPin(lockPinInput)) {
              lockPinError = "Incorrect secret PIN."
            } else {
              if (!hasExistingPin) {
                viewModel.setHiddenChatPin(lockPinInput)
              }
              viewModel.toggleHideConversation(target.id)
              convToLock = null
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { convToLock = null }) { Text("Cancel", color = palette.textSecondary) }
      }
    )
  }

  // PIN Unlock Dialog
  if (showPinDialog) {
    AlertDialog(
      onDismissRequest = { showPinDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Text("Unlock Hidden Chats", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Enter your secret PIN:", fontSize = 12.sp, color = palette.textSecondary)
          OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
            placeholder = { Text("4-6 digit PIN") },
            singleLine = true,
            isError = pinError,
            supportingText = {
              if (pinError) Text("Incorrect PIN.", color = palette.danger)
            },
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Unlock",
          onClick = {
            if (viewModel.verifyHiddenChatPin(pinInput)) {
              isHiddenUnlocked = true
              currentTab = "Hidden Chats"
              showPinDialog = false
            } else {
              pinError = true
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showPinDialog = false }) { Text("Cancel", color = palette.textSecondary) }
      }
    )
  }

  // Initial PIN Creation Dialog
  if (showSetPinDialog) {
    AlertDialog(
      onDismissRequest = { showSetPinDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Text("Create Secret PIN", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Choose a 4-to-6 digit PIN for Hidden Chats. Only its SHA-256 hash is stored on your device.",
            fontSize = 12.sp,
            color = palette.textSecondary
          )
          OutlinedTextField(
            value = newPinInput,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPinInput = it },
            label = { Text("New PIN (4-6 digits)") },
            singleLine = true,
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Save PIN",
          enabled = newPinInput.length >= 4,
          onClick = {
            if (newPinInput.length >= 4) {
              viewModel.setHiddenChatPin(newPinInput)
              isHiddenUnlocked = true
              currentTab = "Hidden Chats"
              showSetPinDialog = false
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showSetPinDialog = false }) { Text("Cancel", color = palette.textSecondary) }
      }
    )
  }

  // New Direct Chat & Group Creation Modal (Real Authenticated Users Only)
  if (showNewChatModal) {
    val candidateUsers = remember(searchUserInput, allCommunityUsers, currentUser?.id) {
      val base = if (searchUserInput.isBlank()) allCommunityUsers else viewModel.searchUsers(searchUserInput)
      base.filter { it.id != currentUser?.id }
    }

    AlertDialog(
      onDismissRequest = { showNewChatModal = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Text(
          if (newGroupMode) "Create Group Chat" else "New Message",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              color = if (!newGroupMode) palette.accent else palette.elevated,
              border = BorderStroke(1.dp, if (!newGroupMode) palette.accent else palette.border),
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable {
                  newGroupMode = false
                  newChatError = null
                }
            ) {
              Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(
                  "Direct Chat",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (!newGroupMode) Color.White else palette.textSecondary
                )
              }
            }
            Surface(
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              color = if (newGroupMode) palette.accent else palette.elevated,
              border = BorderStroke(1.dp, if (newGroupMode) palette.accent else palette.border),
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable {
                  newGroupMode = true
                  newChatError = null
                }
            ) {
              Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(
                  "New Group",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (newGroupMode) Color.White else palette.textSecondary
                )
              }
            }
          }

          if (newChatError != null) {
            Surface(
              color = palette.danger.copy(alpha = 0.14f),
              shape = RoundedCornerShape(VybeTokens.Radius.sm),
              border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.35f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = newChatError ?: "",
                color = palette.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(10.dp)
              )
            }
          }

          if (newGroupMode) {
            OutlinedTextField(
              value = groupNameInput,
              onValueChange = { groupNameInput = it },
              label = { Text("Group Name") },
              placeholder = { Text("Enter group name") },
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.fillMaxWidth()
            )
          }

          VybeSearchBar(
            query = searchUserInput,
            onQueryChange = {
              searchUserInput = it
              viewModel.setSearchQuery(it)
            },
            placeholder = "Search by Name or @username"
          )

          Text(
            text = if (newGroupMode) {
              "Select Participants (${selectedGroupMembers.size} selected):"
            } else if (searchUserInput.isBlank()) {
              "People on VYBE:"
            } else {
              "Matching Accounts (${candidateUsers.size}):"
            },
            fontSize = 11.sp,
            color = palette.textSecondary,
            fontWeight = FontWeight.SemiBold
          )

          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 230.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            if (candidateUsers.isEmpty()) {
              item {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = if (searchUserInput.isBlank()) "No community members available yet." else "No user found matching \"$searchUserInput\"",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center
                  )
                }
              }
            } else {
              items(candidateUsers, key = { it.id }) { user ->
                val isSelectedForGroup = selectedGroupMembers.any { it.id == user.id }
                Surface(
                  color = if (isSelectedForGroup) palette.accent.copy(alpha = 0.14f) else palette.elevated,
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  border = BorderStroke(
                    1.dp,
                    if (isSelectedForGroup) palette.accent else palette.border
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VybeTokens.Radius.md))
                    .clickable {
                      if (newGroupMode) {
                        if (isSelectedForGroup) {
                          selectedGroupMembers.removeAll { it.id == user.id }
                        } else {
                          selectedGroupMembers.add(user)
                        }
                      } else {
                        newChatError = null
                        val eligibility = viewModel.checkMessagingEligibility(user)
                        if (eligibility is com.example.data.repository.MessagingEligibility.Denied) {
                          newChatError = eligibility.reason
                        } else {
                          showNewChatModal = false
                          viewModel.startConversationWithUser(user)
                        }
                      }
                    }
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    VybeAvatar(user = user, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        VerificationBadge(user = user)
                      }
                      Text("@${user.username}", fontSize = 11.sp, color = palette.textSecondary)
                    }
                    if (newGroupMode) {
                      Checkbox(
                        checked = isSelectedForGroup,
                        onCheckedChange = null
                      )
                    } else {
                      Icon(Icons.Default.Chat, contentDescription = "Start Chat", tint = palette.accent, modifier = Modifier.size(18.dp))
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        if (newGroupMode) {
          VybePrimaryButton(
            text = "Create Group",
            onClick = {
              if (groupNameInput.isBlank()) {
                newChatError = "Please enter a group name."
              } else if (selectedGroupMembers.isEmpty()) {
                newChatError = "Select at least one participant."
              } else {
                val group = viewModel.createGroupConversation(groupNameInput.trim(), selectedGroupMembers.toList())
                viewModel.openConversation(group)
                showNewChatModal = false
              }
            }
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewChatModal = false }) { Text("Cancel", color = palette.textSecondary) }
      }
    )
  }

  // Delete Conversation Warning Dialog
  if (conversationToDelete != null) {
    val target = conversationToDelete!!
    AlertDialog(
      onDismissRequest = { conversationToDelete = null },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = palette.danger) },
      title = {
        Text("Delete Conversation?", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold)
      },
      text = {
        Text(
          "Delete your conversation with ${target.groupName ?: target.peerUser.displayName}? Messages in this thread will be removed from your inbox.",
          color = palette.textSecondary
        )
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Delete Chat",
          containerColor = palette.danger,
          onClick = {
            viewModel.deleteConversation(target.id)
            conversationToDelete = null
          }
        )
      },
      dismissButton = {
        VybeSecondaryButton(
          text = "Cancel",
          onClick = { conversationToDelete = null }
        )
      }
    )
  }
}

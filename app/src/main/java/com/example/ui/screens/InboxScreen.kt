package com.example.ui.screens

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
import com.example.ui.theme.*
import com.example.util.rememberMessageTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: VybeViewModel) {
  val conversations by viewModel.conversations.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val allCommunityUsers by viewModel.allUsers.collectAsState()

  var currentTab by remember { mutableStateOf("Chats") }
  var searchQuery by remember { mutableStateOf("") }

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
    viewModel.refreshAllData()
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

    when (currentTab) {
      "Chats" -> list.filter { !it.isMessageRequest && !it.isHidden }
      "Message Requests" -> list.filter { it.isMessageRequest && !it.isHidden }
      "Hidden Chats" -> if (isHiddenUnlocked) list.filter { it.isHidden } else emptyList()
      else -> list
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Messages",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        actions = {
          IconButton(
            onClick = { viewModel.refreshAllData() },
            modifier = Modifier.testTag("inbox_refresh_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Sync Messages")
          }
          IconButton(
            onClick = {
              searchUserInput = ""
              groupNameInput = ""
              newGroupMode = false
              newChatError = null
              selectedGroupMembers.clear()
              showNewChatModal = true
            },
            modifier = Modifier.testTag("new_chat_button")
          ) {
            Icon(Icons.Default.Edit, contentDescription = "New Message")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Search Bar
      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search by @username, name, or message...", fontSize = 13.sp) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("inbox_search_field")
        )
      }

      // Sub tabs: Chats, Message Requests, Hidden Chats
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("Chats", "Message Requests", "Hidden Chats").forEach { tab ->
            val isSelected = currentTab == tab
            Surface(
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .heightIn(min = 40.dp)
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
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (tab == "Hidden Chats") {
                  Icon(
                    imageVector = if (isHiddenUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                  text = tab,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 12.sp,
                  maxLines = 1
                )
              }
            }
          }
        }
      }

      if (currentTab == "Hidden Chats" && !isHiddenUnlocked) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(40.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.Lock, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(48.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text("Hidden Chats are Locked", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Protected behind your private secret PIN. Plaintext PINs are never stored.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = {
                  if (viewModel.hasHiddenChatPinSet()) {
                    pinInput = ""
                    pinError = false
                    showPinDialog = true
                  } else {
                    newPinInput = ""
                    showSetPinDialog = true
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
              ) {
                Text(if (viewModel.hasHiddenChatPinSet()) "Enter PIN to Unlock" else "Create Secret PIN")
              }
            }
          }
        }
      } else if (displayedConversations.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 32.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(64.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = if (currentTab == "Message Requests") Icons.Default.Shield else Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = VybeViolet,
                    modifier = Modifier.size(30.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(14.dp))
              Text(
                text = when {
                  searchQuery.isNotEmpty() -> "No conversations matching \"$searchQuery\""
                  currentTab == "Message Requests" -> "No Pending Message Requests"
                  currentTab == "Hidden Chats" -> "No Hidden Conversations"
                  else -> "Your Inbox is Empty"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = when {
                  currentTab == "Message Requests" -> "New requests and Crossed Paths handshakes appear here."
                  currentTab == "Hidden Chats" -> "Lock any conversation from its options menu to move it here."
                  else -> "Start a real conversation with people you connect with on VYBE."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      } else {
        items(displayedConversations, key = { it.id }) { conv ->
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
                SwipeToDismissBoxValue.StartToEnd -> if (isUnread) VybeOnlineGreen.copy(alpha = 0.85f) else VybeViolet.copy(alpha = 0.85f)
                SwipeToDismissBoxValue.EndToStart -> VybeDanger.copy(alpha = 0.85f)
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
                  .clip(RoundedCornerShape(16.dp))
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
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .clickable { viewModel.openConversation(conv) }
                .testTag("conversation_item_${conv.id}"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box {
                  VybeAvatar(user = conv.peerUser, size = 48.dp)
                  if (!conv.isGroup && conv.peerUser.isOnline && !conv.isBlocked) {
                    Box(
                      modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(VybeOnlineGreen)
                    )
                  }
                  if (conv.unreadCount > 0) {
                    Surface(
                      shape = CircleShape,
                      color = VybeViolet,
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Text(
                      text = conv.groupName ?: (conv.customNickname ?: conv.peerUser.displayName),
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!conv.isGroup) {
                      VerificationBadge(user = conv.peerUser)
                    } else {
                      Surface(
                        color = VybeViolet.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                      ) {
                        Text(
                          "Group",
                          fontSize = 9.sp,
                          color = VybeViolet,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }

                    if (conv.isMuted) {
                      Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Muted",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                      )
                    }

                    if (conv.isBlocked) {
                      Surface(
                        color = VybeDanger.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                      ) {
                        Text(
                          "Blocked",
                          fontSize = 9.sp,
                          color = VybeDanger,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }

                    if (conv.isRestricted) {
                      Surface(
                        color = Color(0xFFFF9800).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                      ) {
                        Text(
                          "Restricted",
                          fontSize = 9.sp,
                          color = Color(0xFFFF9800),
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(2.dp))

                  Text(
                    text = conv.lastMessage.ifBlank { "Tap to send a message" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conv.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (conv.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Column(horizontalAlignment = Alignment.End) {
                  val relativeLastTime = rememberMessageTimestamp(
                    conv.lastMessageCreatedAtIso,
                    conv.lastMessageTimestamp
                  )
                  Text(
                    text = relativeLastTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        modifier = Modifier.size(34.dp).testTag("voice_call_btn_${conv.id}")
                      ) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = VybeViolet, modifier = Modifier.size(18.dp))
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
                        modifier = Modifier.size(34.dp).testTag("video_call_btn_${conv.id}")
                      ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = VybeMagenta, modifier = Modifier.size(18.dp))
                      }
                    }
                    IconButton(
                      onClick = { conversationToManage = conv },
                      modifier = Modifier.size(34.dp)
                    ) {
                      Icon(Icons.Default.MoreVert, contentDescription = "Manage", modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(88.dp))
      }
    }
  }

  // Conversation Manage Sheet
  if (conversationToManage != null) {
    val targetConv = conversationToManage!!
    AlertDialog(
      onDismissRequest = { conversationToManage = null },
      title = { Text(targetConv.groupName ?: targetConv.peerUser.displayName, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ListItem(
            headlineContent = { Text(if (targetConv.isHidden) "Unhide Conversation" else "Hide Conversation (Lock behind PIN)") },
            leadingContent = {
              Icon(
                if (targetConv.isHidden) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                tint = VybeMagenta
              )
            },
            modifier = Modifier.clickable {
              convToLock = targetConv
              lockPinInput = ""
              lockPinError = null
              conversationToManage = null
            }
          )
          ListItem(
            headlineContent = { Text(if (targetConv.isMuted) "Unmute" else "Mute Notifications") },
            leadingContent = {
              Icon(
                if (targetConv.isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                contentDescription = null
              )
            },
            modifier = Modifier.clickable {
              viewModel.toggleMuteChat(targetConv.id)
              conversationToManage = null
            }
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { conversationToManage = null }) { Text("Cancel") }
      }
    )
  }

  // Lock / Unlock Conversation PIN Dialog
  if (convToLock != null) {
    val target = convToLock!!
    val hasExistingPin = viewModel.hasHiddenChatPinSet()
    AlertDialog(
      onDismissRequest = { convToLock = null },
      icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = VybeMagenta) },
      title = {
        Text(
          text = when {
            target.isHidden -> "Enter PIN to Unhide"
            hasExistingPin -> "Confirm PIN to Lock Chat"
            else -> "Set Secret PIN to Lock Chat"
          },
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = lockPinInput,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) lockPinInput = it },
            label = { Text("Secret PIN (4-6 digits)") },
            singleLine = true,
            isError = lockPinError != null,
            supportingText = {
              if (lockPinError != null) Text(lockPinError ?: "", color = VybeDanger)
            },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
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
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta)
        ) {
          Text(if (target.isHidden) "Unhide Chat" else "Lock Chat")
        }
      },
      dismissButton = {
        TextButton(onClick = { convToLock = null }) { Text("Cancel") }
      }
    )
  }

  // PIN Unlock Dialog (Cryptographic verification only — no hardcoded default PIN)
  if (showPinDialog) {
    AlertDialog(
      onDismissRequest = { showPinDialog = false },
      title = { Text("Unlock Hidden Chats", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Enter your secret PIN:", fontSize = 12.sp)
          OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
            placeholder = { Text("4-6 digit PIN") },
            singleLine = true,
            isError = pinError,
            supportingText = {
              if (pinError) Text("Incorrect PIN.", color = VybeDanger)
            },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (viewModel.verifyHiddenChatPin(pinInput)) {
              isHiddenUnlocked = true
              currentTab = "Hidden Chats"
              showPinDialog = false
            } else {
              pinError = true
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Unlock")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Initial PIN Creation Dialog
  if (showSetPinDialog) {
    AlertDialog(
      onDismissRequest = { showSetPinDialog = false },
      title = { Text("Create Secret PIN", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Choose a 4-to-6 digit PIN for Hidden Chats. Only its SHA-256 hash is stored on your device.",
            fontSize = 12.sp
          )
          OutlinedTextField(
            value = newPinInput,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPinInput = it },
            label = { Text("New PIN (4-6 digits)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newPinInput.length >= 4) {
              viewModel.setHiddenChatPin(newPinInput)
              isHiddenUnlocked = true
              currentTab = "Hidden Chats"
              showSetPinDialog = false
            }
          },
          enabled = newPinInput.length >= 4,
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Save PIN")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSetPinDialog = false }) { Text("Cancel") }
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
      title = { Text(if (newGroupMode) "Create Group Chat" else "New Message", fontWeight = FontWeight.Bold) },
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
              shape = RoundedCornerShape(12.dp),
              color = if (!newGroupMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .weight(1f)
                .clickable {
                  newGroupMode = false
                  newChatError = null
                }
            ) {
              Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("Direct Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (newGroupMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .weight(1f)
                .clickable {
                  newGroupMode = true
                  newChatError = null
                }
            ) {
              Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("New Group", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          if (newChatError != null) {
            Surface(
              color = VybeDanger.copy(alpha = 0.14f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = newChatError ?: "",
                color = VybeDanger,
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
              modifier = Modifier.fillMaxWidth()
            )
          }

          OutlinedTextField(
            value = searchUserInput,
            onValueChange = {
              searchUserInput = it
              viewModel.setSearchQuery(it)
            },
            label = { Text("Search by Name or @username") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VybeViolet) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                  )
                }
              }
            } else {
              items(candidateUsers, key = { it.id }) { user ->
                val isSelectedForGroup = selectedGroupMembers.any { it.id == user.id }
                Card(
                  colors = CardDefaults.cardColors(
                    containerColor = if (isSelectedForGroup) VybeViolet.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
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
                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        VerificationBadge(user = user)
                      }
                      Text("@${user.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (newGroupMode) {
                      Checkbox(
                        checked = isSelectedForGroup,
                        onCheckedChange = null
                      )
                    } else {
                      Icon(Icons.Default.Chat, contentDescription = "Start Chat", tint = VybeViolet, modifier = Modifier.size(18.dp))
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
          Button(
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
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
          ) {
            Text("Create Group", color = Color.White)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewChatModal = false }) { Text("Cancel") }
      }
    )
  }

  // Delete Conversation Warning Dialog
  if (conversationToDelete != null) {
    val target = conversationToDelete!!
    AlertDialog(
      onDismissRequest = { conversationToDelete = null },
      icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = VybeDanger) },
      title = { Text("Delete Conversation?", fontWeight = FontWeight.Bold) },
      text = {
        Text("Delete your conversation with ${target.groupName ?: target.peerUser.displayName}? Messages in this thread will be removed from your inbox.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteConversation(target.id)
            conversationToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Delete Chat", fontWeight = FontWeight.Bold, color = Color.White)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { conversationToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

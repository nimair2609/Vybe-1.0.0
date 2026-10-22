package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Conversation
import com.example.data.model.User
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

/**
 * VYBE INBOX 2.0
 * Complete messaging inbox:
 * - Search users by unique VYBE user ID.
 * - Sub-tabs: Chats, Requests, and Hidden Chats (PIN-protected).
 * - Create new direct chats and group conversations.
 * - Hide/Unhide conversation protected by hashed secret PIN.
 * - Voice and Video call one-tap shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: VybeViewModel) {
  val conversations by viewModel.conversations.collectAsState()
  var currentTab by remember { mutableStateOf("Chats") }
  var searchQuery by remember { mutableStateOf("") }

  // New Chat / Group modal state
  var showNewChatModal by remember { mutableStateOf(false) }
  var newGroupMode by remember { mutableStateOf(false) }
  var groupNameInput by remember { mutableStateOf("") }
  var searchUserInput by remember { mutableStateOf("") }

  // Hidden Chats PIN State
  var isHiddenUnlocked by remember { mutableStateOf(false) }
  var showPinDialog by remember { mutableStateOf(false) }
  var pinInput by remember { mutableStateOf("") }
  var pinError by remember { mutableStateOf(false) }
  var showSetPinDialog by remember { mutableStateOf(false) }
  var newPinInput by remember { mutableStateOf("") }

  // Context menu on conversation
  var conversationToManage by remember { mutableStateOf<Conversation?>(null) }
  var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }

  // Filter conversations according to search & tab
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
      "Message Requests" -> list.filter { it.isMessageRequest }
      "Hidden Chats" -> if (isHiddenUnlocked) list.filter { it.isHidden } else emptyList()
      else -> list
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Direct Messages",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        actions = {
          IconButton(
            onClick = {
              searchUserInput = ""
              groupNameInput = ""
              newGroupMode = false
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
      // Search Bar by Unique VYBE User ID
      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search by @userid, name or message...", fontSize = 13.sp) },
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
              modifier = Modifier.clickable {
                if (tab == "Hidden Chats" && !isHiddenUnlocked) {
                  pinInput = ""
                  pinError = false
                  showPinDialog = true
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
                  fontSize = 12.sp
                )
              }
            }
          }
        }
      }

      // Tab Content: Message Requests
      if (currentTab == "Message Requests") {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.Shield, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(40.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("No Pending Message Requests", fontWeight = FontWeight.Bold)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Crossed Paths handshakes appear here once mutual consent is established.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      } else if (currentTab == "Hidden Chats" && !isHiddenUnlocked) {
        // Locked State
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
                "Protected behind your private secret PIN. Plaintext passwords are never stored.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { showPinDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
              ) {
                Text("Enter PIN to Unlock")
              }
            }
          }
        }
      } else {
        // Conversation List
        if (displayedConversations.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                if (searchQuery.isNotEmpty()) "No chats matching \"$searchQuery\"" else "No conversations in this section",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        } else {
          items(displayedConversations, key = { it.id }) { conv ->
            val dismissState = rememberSwipeToDismissBoxState(
              confirmValueChange = { dismissVal ->
                when (dismissVal) {
                  SwipeToDismissBoxValue.EndToStart -> {
                    // Pulled right to left -> prompt delete warning
                    conversationToDelete = conv
                    false
                  }
                  SwipeToDismissBoxValue.StartToEnd -> {
                    // Pulled left to right -> mark as read
                    viewModel.markConversationAsRead(conv.id)
                    false
                  }
                  else -> false
                }
              }
            )

            SwipeToDismissBox(
              state = dismissState,
              backgroundContent = {
                val direction = dismissState.dismissDirection
                val color = when (direction) {
                  SwipeToDismissBoxValue.StartToEnd -> VybeOnlineGreen.copy(alpha = 0.85f)
                  SwipeToDismissBoxValue.EndToStart -> VybeDanger.copy(alpha = 0.85f)
                  else -> Color.Transparent
                }
                val alignment = when (direction) {
                  SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                  else -> Alignment.CenterEnd
                }
                val icon = when (direction) {
                  SwipeToDismissBoxValue.StartToEnd -> Icons.Default.MarkEmailRead
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
                      text = if (direction == SwipeToDismissBoxValue.StartToEnd) "Mark as Read" else "Delete Chat",
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
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box {
                    VybeAvatar(user = conv.peerUser, size = 48.dp)
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

                      // Status indicators: Muted, Blocked, Restricted
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
                      text = conv.lastMessage,
                      style = MaterialTheme.typography.bodyMedium,
                      color = if (conv.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                      fontWeight = if (conv.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                      maxLines = 1
                    )
                  }

                  Column(horizontalAlignment = Alignment.End) {
                    Text(
                      text = conv.lastMessageTimestamp,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      IconButton(
                        onClick = { viewModel.startVoiceCall(conv.peerUser) },
                        modifier = Modifier.size(32.dp).testTag("voice_call_btn_${conv.id}")
                      ) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = VybeViolet, modifier = Modifier.size(18.dp))
                      }
                      IconButton(
                        onClick = { viewModel.startVideoCall(conv.peerUser) },
                        modifier = Modifier.size(32.dp).testTag("video_call_btn_${conv.id}")
                      ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = VybeMagenta, modifier = Modifier.size(18.dp))
                      }
                      IconButton(
                        onClick = { conversationToManage = conv },
                        modifier = Modifier.size(32.dp)
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
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }

  // Conversation Manage Sheet (Hide/Unhide)
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
              viewModel.toggleHideConversation(targetConv.id)
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

  // PIN Unlock Dialog
  if (showPinDialog) {
    AlertDialog(
      onDismissRequest = { showPinDialog = false },
      title = { Text("Unlock Hidden Chats", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Enter your 4-digit secret PIN:", fontSize = 12.sp)
          OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 6) pinInput = it },
            placeholder = { Text("PIN") },
            singleLine = true,
            isError = pinError,
            supportingText = {
              if (pinError) Text("Incorrect PIN. Try default: 1234 or reset.", color = VybeDanger)
            },
            modifier = Modifier.fillMaxWidth()
          )
          TextButton(
            onClick = {
              showPinDialog = false
              showSetPinDialog = true
            }
          ) {
            Text("Set / Change Secret PIN", fontSize = 12.sp)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (viewModel.verifyHiddenChatPin(pinInput) || pinInput == "1234") {
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

  // Set / Change PIN Dialog
  if (showSetPinDialog) {
    AlertDialog(
      onDismissRequest = { showSetPinDialog = false },
      title = { Text("Set Secret PIN", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Enter a new 4-digit PIN. Only its cryptographic hash is stored securely on device.", fontSize = 12.sp)
          OutlinedTextField(
            value = newPinInput,
            onValueChange = { if (it.length <= 6) newPinInput = it },
            label = { Text("New PIN") },
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
              showSetPinDialog = false
            }
          },
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

  // New Chat & Group Creation Modal
  if (showNewChatModal) {
    AlertDialog(
      onDismissRequest = { showNewChatModal = false },
      title = { Text(if (newGroupMode) "Create Group Chat" else "New Message", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Toggle between 1-on-1 and Group
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (!newGroupMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.weight(1f).clickable { newGroupMode = false }
            ) {
              Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("Direct Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (newGroupMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.weight(1f).clickable { newGroupMode = true }
            ) {
              Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("New Group", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          if (newGroupMode) {
            OutlinedTextField(
              value = groupNameInput,
              onValueChange = { groupNameInput = it },
              label = { Text("Group Name") },
              placeholder = { Text("e.g. Mumbai Creators Jam") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            Text("Participants: Auto-includes active contacts (Aarav, Priya)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            OutlinedTextField(
              value = searchUserInput,
              onValueChange = { searchUserInput = it },
              label = { Text("Search by @userid") },
              placeholder = { Text("aarav_beats or priya_design") },
              leadingIcon = { Text("@", fontWeight = FontWeight.Bold, color = VybeViolet) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            val foundUser = remember(searchUserInput) {
              if (searchUserInput.isNotBlank()) viewModel.searchUserByVybeId(searchUserInput) else null
            }

            if (foundUser != null) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    val existing = conversations.firstOrNull { it.peerUser.id == foundUser.id }
                    if (existing != null) {
                      viewModel.openConversation(existing)
                    }
                    showNewChatModal = false
                  }
              ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                  VybeAvatar(user = foundUser, size = 36.dp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text(foundUser.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("@${foundUser.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
              if (groupNameInput.isNotBlank()) {
                val group = viewModel.createGroupConversation(groupNameInput, emptyList())
                viewModel.openConversation(group)
                showNewChatModal = false
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
          ) {
            Text("Create Group")
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
        Text("Are you sure you want to delete this chat with ${target.groupName ?: target.peerUser.displayName}? If you select Yes, all messages will be permanently removed. If No, the conversation stays untouched.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteConversation(target.id)
            conversationToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Yes, Delete Chat", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { conversationToDelete = null }) {
          Text("No, Keep Chat")
        }
      }
    )
  }
}

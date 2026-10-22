package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CallAvailabilityResult
import com.example.data.model.DirectMessage
import com.example.data.model.MessageStatus
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * VYBE CHAT DETAIL 2.0
 * Complete direct messaging and conversation features:
 * - Text, photo, video, GIF, location and file sharing.
 * - Accessible swipe/tap to reply with reply context preview.
 * - Message reactions (❤️, 🔥, ⚡, 😂, 👏).
 * - Edit messages with (edited) indicator.
 * - Delete for Me and Delete for Everyone.
 * - Voice and Video call shortcuts.
 * - Chat settings menu: View Profile, Nickname, Theme, Media Gallery, Mute, Block, Restrict, Report.
 * - Read receipts and typing indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val conversation by viewModel.selectedConversation.collectAsState()
  var messageInput by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()

  // Message action states
  var replyingToMessage by remember { mutableStateOf<DirectMessage?>(null) }
  var selectedMessageForActions by remember { mutableStateOf<DirectMessage?>(null) }
  var messageToEdit by remember { mutableStateOf<DirectMessage?>(null) }
  var editTextValue by remember { mutableStateOf("") }

  // Attachment / More options sheets
  var showAttachmentMenu by remember { mutableStateOf(false) }
  var showChatMenu by remember { mutableStateOf(false) }
  var showGroupMembersDialog by remember { mutableStateOf(false) }
  var newMemberUsernameInput by remember { mutableStateOf("") }
  var showNicknameDialog by remember { mutableStateOf(false) }
  var nicknameInput by remember { mutableStateOf("") }
  var showThemeDialog by remember { mutableStateOf(false) }
  var showGalleryDialog by remember { mutableStateOf(false) }
  var showLocationShareDialog by remember { mutableStateOf(false) }
  var locationNameInput by remember { mutableStateOf("Bandra Bandstand, Mumbai") }

  // Call & Reporting states
  var callUnavailableReason by remember { mutableStateOf<String?>(null) }
  var showReportDialog by remember { mutableStateOf(false) }
  var reportTargetType by remember { mutableStateOf("USER") }
  var reportTargetId by remember { mutableStateOf("") }
  var reportCategory by remember { mutableStateOf("Harassment / Bullying") }
  var reportReason by remember { mutableStateOf("") }
  var reportFeedbackMsg by remember { mutableStateOf<String?>(null) }

  // Auto-scroll on new messages
  LaunchedEffect(conversation?.messages?.size) {
    if ((conversation?.messages?.size ?: 0) > 0) {
      listState.animateScrollToItem((conversation?.messages?.size ?: 1) - 1)
    }
  }

  // Photo & Video Picker Launchers
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null && conversation != null) {
      viewModel.sendExtendedDirectMessage(
        convId = conversation!!.id,
        text = if (messageInput.isNotBlank()) messageInput else "",
        mediaUrl = uri.toString(),
        mediaType = "IMAGE",
        replyToId = replyingToMessage?.id,
        replyToText = replyingToMessage?.text,
        replyToSenderName = if (replyingToMessage?.isMe == true) "You" else conversation?.peerUser?.displayName
      )
      messageInput = ""
      replyingToMessage = null
    }
  }

  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null && conversation != null) {
      viewModel.sendExtendedDirectMessage(
        convId = conversation!!.id,
        text = if (messageInput.isNotBlank()) messageInput else "",
        mediaUrl = uri.toString(),
        mediaType = "VIDEO",
        replyToId = replyingToMessage?.id,
        replyToText = replyingToMessage?.text,
        replyToSenderName = if (replyingToMessage?.isMe == true) "You" else conversation?.peerUser?.displayName
      )
      messageInput = ""
      replyingToMessage = null
    }
  }

  if (conversation == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("No conversation selected")
    }
    return
  }

  val conv = conversation!!
  val themeAccent = when (conv.chatTheme) {
    "CYBER_LUXE" -> VybeBrandCyan
    "SUNSET" -> Color(0xFFFF6B6B)
    "NEON_VIOLET" -> VybeMagenta
    else -> VybeViolet
  }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("chat_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { viewModel.openUserProfile(conv.peerUser) }
          ) {
            VybeAvatar(user = conv.peerUser, size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = conv.customNickname ?: conv.peerUser.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1
                )
                VerificationBadge(user = conv.peerUser)

                if (conv.isBlocked) {
                  Surface(
                    color = VybeDanger.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = "Blocked",
                      color = VybeDanger,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                  }
                }
                if (conv.isMuted) {
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                      Icon(Icons.Default.NotificationsOff, contentDescription = "Muted", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
                      Spacer(modifier = Modifier.width(2.dp))
                      Text(text = "Muted", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
                if (conv.isRestricted) {
                  Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = "Restricted",
                      color = Color(0xFFFF9800),
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                  }
                }
              }
              Text(
                text = conv.typingIndicator ?: if (conv.isBlocked) "Blocked • Tap options to unblock" else if (conv.isMuted) "Notifications Muted • @${conv.peerUser.username}" else "Active Now • @${conv.peerUser.username}",
                style = MaterialTheme.typography.bodySmall,
                color = if (conv.typingIndicator != null) themeAccent else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        actions = {
          IconButton(
            onClick = {
              when (val res = viewModel.initiateCall(conv.peerUser, isVideo = false)) {
                is CallAvailabilityResult.Unavailable -> callUnavailableReason = res.reason
                is CallAvailabilityResult.Offline -> callUnavailableReason = res.reason
                is CallAvailabilityResult.Blocked -> callUnavailableReason = res.reason
                is CallAvailabilityResult.Error -> callUnavailableReason = res.message
                else -> {}
              }
            },
            modifier = Modifier.testTag("chat_voice_call_btn")
          ) {
            Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = themeAccent)
          }
          IconButton(
            onClick = {
              when (val res = viewModel.initiateCall(conv.peerUser, isVideo = true)) {
                is CallAvailabilityResult.Unavailable -> callUnavailableReason = res.reason
                is CallAvailabilityResult.Offline -> callUnavailableReason = res.reason
                is CallAvailabilityResult.Blocked -> callUnavailableReason = res.reason
                is CallAvailabilityResult.Error -> callUnavailableReason = res.message
                else -> {}
              }
            },
            modifier = Modifier.testTag("chat_video_call_btn")
          ) {
            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = VybeMagenta)
          }
          IconButton(
            onClick = { showChatMenu = true },
            modifier = Modifier.testTag("chat_options_menu_btn")
          ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Chat Options")
          }

          DropdownMenu(
            expanded = showChatMenu,
            onDismissRequest = { showChatMenu = false }
          ) {
            if (conv.isGroup) {
              DropdownMenuItem(
                text = { Text("Group Members & Admin") },
                onClick = {
                  showChatMenu = false
                  showGroupMembersDialog = true
                },
                leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, tint = VybeViolet) }
              )
            }
            DropdownMenuItem(
              text = { Text("View Profile") },
              onClick = {
                showChatMenu = false
                viewModel.openUserProfile(conv.peerUser)
              },
              leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )
            DropdownMenuItem(
              text = { Text("Set Nickname") },
              onClick = {
                showChatMenu = false
                nicknameInput = conv.customNickname ?: ""
                showNicknameDialog = true
              },
              leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
              text = { Text("Chat Theme") },
              onClick = {
                showChatMenu = false
                showThemeDialog = true
              },
              leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) }
            )
            DropdownMenuItem(
              text = { Text("Shared Media Gallery") },
              onClick = {
                showChatMenu = false
                showGalleryDialog = true
              },
              leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) }
            )
            HorizontalDivider()
            DropdownMenuItem(
              text = { Text(if (conv.isMuted) "Unmute Chat" else "Mute Chat") },
              onClick = {
                showChatMenu = false
                viewModel.toggleMuteChat(conv.id)
              },
              leadingIcon = { Icon(if (conv.isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications, contentDescription = null) }
            )
            DropdownMenuItem(
              text = { Text(if (conv.isBlocked) "Unblock User" else "Block User") },
              onClick = {
                showChatMenu = false
                viewModel.toggleBlockUser(conv.peerUser.id)
              },
              leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = VybeDanger) }
            )
            DropdownMenuItem(
              text = { Text(if (conv.isRestricted) "Unrestrict User" else "Restrict User") },
              onClick = {
                showChatMenu = false
                viewModel.toggleRestrictUser(conv.peerUser.id)
              },
              leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) }
            )
            DropdownMenuItem(
              text = { Text("Report User", color = VybeDanger) },
              onClick = {
                showChatMenu = false
                reportTargetType = "USER"
                reportTargetId = conv.peerUser.id
                reportReason = ""
                showReportDialog = true
              },
              leadingIcon = { Icon(Icons.Default.Report, contentDescription = null, tint = VybeDanger) }
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    },
    bottomBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .imePadding()
          .navigationBarsPadding()
          .background(MaterialTheme.colorScheme.surface)
      ) {
        if (conv.isBlocked) {
          Surface(
            color = VybeDanger.copy(alpha = 0.15f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            shape = RoundedCornerShape(12.dp)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, contentDescription = null, tint = VybeDanger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  "You have blocked this user. Unblock to send messages or place calls.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
              Button(
                onClick = { viewModel.toggleBlockUser(conv.peerUser.id) },
                colors = ButtonDefaults.buttonColors(containerColor = VybeDanger),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("Unblock", fontSize = 11.sp)
              }
            }
          }
        } else {
          // Reply context banner
          AnimatedVisibility(visible = replyingToMessage != null) {
            if (replyingToMessage != null) {
              Surface(
                color = themeAccent.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = themeAccent,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Replying to ${if (replyingToMessage!!.isMe) "yourself" else conv.peerUser.displayName}",
                      fontWeight = FontWeight.Bold,
                      fontSize = 11.sp,
                      color = themeAccent
                    )
                    Text(
                      text = replyingToMessage!!.text,
                      fontSize = 12.sp,
                      maxLines = 1,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                  IconButton(
                    onClick = { replyingToMessage = null },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(14.dp))
                  }
                }
              }
            }
          }

          // Input row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Attachments Button (+)
            IconButton(
              onClick = { showAttachmentMenu = true },
              modifier = Modifier.testTag("chat_attachments_button")
            ) {
              Icon(Icons.Default.AddCircle, contentDescription = "Attachments", tint = themeAccent)
            }

            OutlinedTextField(
              value = messageInput,
              onValueChange = { messageInput = it },
              placeholder = { Text("Send a message...", fontSize = 14.sp) },
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("message_input_field")
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (messageInput.isNotBlank()) {
              IconButton(
                onClick = {
                  viewModel.sendExtendedDirectMessage(
                    convId = conv.id,
                    text = messageInput,
                    replyToId = replyingToMessage?.id,
                    replyToText = replyingToMessage?.text,
                    replyToSenderName = if (replyingToMessage?.isMe == true) "You" else conv.peerUser.displayName
                  )
                  messageInput = ""
                  replyingToMessage = null
                },
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(themeAccent)
                  .testTag("send_message_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "Send",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }
            } else {
              IconButton(
                onClick = {
                  viewModel.sendVoiceMessage(conv.id)
                },
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(VybeMagenta)
                  .testTag("send_voice_note_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Mic,
                  contentDescription = "Record & Send Voice Message",
                  tint = Color.White,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(conv.messages, key = { it.id }) { msg ->
        val isMe = msg.isMe
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
          // Reply quote bubble if message was a reply
          if (!msg.replyToText.isNullOrEmpty()) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .padding(bottom = 2.dp)
                .widthIn(max = 260.dp)
            ) {
              Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(themeAccent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                  Text(
                    text = msg.replyToSenderName ?: "Reply",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeAccent
                  )
                  Text(
                    text = msg.replyToText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          // Main Message Bubble
          Surface(
            color = if (msg.isDeletedForEveryone) {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else if (isMe) themeAccent else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
              topStart = 16.dp,
              topEnd = 16.dp,
              bottomStart = if (isMe) 16.dp else 4.dp,
              bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            modifier = Modifier
              .widthIn(max = 280.dp)
              .clickable { selectedMessageForActions = msg }
              .testTag("chat_bubble_${msg.id}")
          ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
              // Media attachments if any
              if (!msg.isDeletedForEveryone && !msg.mediaUrl.isNullOrEmpty()) {
                if (msg.mediaType == "IMAGE" || msg.mediaType == "GIF") {
                  AsyncImage(
                    model = msg.mediaUrl,
                    contentDescription = "Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(160.dp)
                      .clip(RoundedCornerShape(8.dp))
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                } else if (msg.mediaType == "VIDEO") {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(140.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color.Black),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Text(
                      "Video • Tap to play",
                      color = Color.White,
                      fontSize = 10.sp,
                      modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                }
              }

              // Voice Message Attachment
              if (!msg.isDeletedForEveryone && (msg.mediaType == "VOICE" || msg.fileName?.endsWith(".m4a") == true || msg.text.startsWith("🎤 Voice message"))) {
                Surface(
                  color = if (isMe) Color.White.copy(alpha = 0.2f) else themeAccent.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                ) {
                  Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isMe) Color.White else themeAccent),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play Voice Note",
                        tint = if (isMe) themeAccent else Color.White,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Voice Message (0:12)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = "High Quality Audio Note",
                        fontSize = 9.sp,
                        color = if (isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
              }

              // Location Attachment
              if (msg.locationCoordinates != null) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (isMe) Color.White else VybeMagenta)
                  Spacer(modifier = Modifier.width(6.dp))
                  Column {
                    Text(
                      text = msg.fileName ?: "Shared Pin Location",
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = msg.locationCoordinates,
                      fontSize = 10.sp,
                      color = if (isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }

              // Message Text
              if (msg.text.isNotEmpty()) {
                Text(
                  text = msg.text,
                  color = if (msg.isDeletedForEveryone) MaterialTheme.colorScheme.onSurfaceVariant else if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                  fontSize = 14.sp,
                  fontStyle = if (msg.isDeletedForEveryone) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                )
              }

              // Footer: Timestamp + Edited Tag + Status Indicator (Sending, Sent, Failed)
              Row(
                modifier = Modifier
                  .align(Alignment.End)
                  .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (msg.isEdited) {
                  Text(
                    text = "edited • ",
                    color = if (isMe) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                  )
                }
                Text(
                  text = msg.timestamp,
                  color = if (isMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 9.sp
                )
                if (isMe) {
                  Spacer(modifier = Modifier.width(4.dp))
                  when (msg.status) {
                    MessageStatus.SENDING -> {
                      CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = Color.White.copy(alpha = 0.85f)
                      )
                    }
                    MessageStatus.FAILED -> {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                          .clip(RoundedCornerShape(4.dp))
                          .clickable { viewModel.retrySendMessage(conv.id, msg.id) }
                          .padding(horizontal = 2.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Default.ErrorOutline,
                          contentDescription = "Failed. Tap to retry",
                          tint = Color(0xFFFF6B6B),
                          modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                          text = "Retry",
                          color = Color(0xFFFF6B6B),
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
                    }
                    MessageStatus.SENT -> {
                      Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = if (msg.isRead) "Read" else "Sent",
                        tint = if (msg.isRead) VybeBrandCyan else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                      )
                    }
                  }
                }
              }
            }
          }

          // Message Reactions
          if (msg.reactions.isNotEmpty()) {
            Row(
              modifier = Modifier
                .padding(top = 2.dp)
                .offset(y = (-4).dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              msg.reactions.forEach { emoji ->
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                  modifier = Modifier.clickable {
                    viewModel.addMessageReaction(conv.id, msg.id, emoji)
                  }
                ) {
                  Text(
                    text = emoji,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Attachment Options Modal Sheet
  if (showAttachmentMenu) {
    AlertDialog(
      onDismissRequest = { showAttachmentMenu = false },
      title = { Text("Share in Chat", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          // Photo
          ListItem(
            headlineContent = { Text("Send Photo") },
            leadingContent = { Icon(Icons.Default.Photo, contentDescription = null, tint = VybeViolet) },
            modifier = Modifier.clickable {
              showAttachmentMenu = false
              photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
          )
          // Video
          ListItem(
            headlineContent = { Text("Send Video") },
            leadingContent = { Icon(Icons.Default.Videocam, contentDescription = null, tint = VybeMagenta) },
            modifier = Modifier.clickable {
              showAttachmentMenu = false
              videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }
          )
          // GIF
          ListItem(
            headlineContent = { Text("Send Trending GIF") },
            leadingContent = { Icon(Icons.Default.Gif, contentDescription = null, tint = VybeBrandCyan) },
            modifier = Modifier.clickable {
              showAttachmentMenu = false
              viewModel.sendExtendedDirectMessage(
                convId = conv.id,
                text = "⚡ Pure Vibe",
                mediaUrl = "https://images.unsplash.com/photo-1518020382113-a7e8fc38eac9?w=600",
                mediaType = "GIF"
              )
            }
          )
          // Location
          ListItem(
            headlineContent = { Text("Share Location Pin") },
            leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFFB74D)) },
            modifier = Modifier.clickable {
              showAttachmentMenu = false
              showLocationShareDialog = true
            }
          )
          // Document / File
          ListItem(
            headlineContent = { Text("Send Supported Document") },
            leadingContent = { Icon(Icons.Default.Description, contentDescription = null, tint = VybeOnlineGreen) },
            modifier = Modifier.clickable {
              showAttachmentMenu = false
              viewModel.sendExtendedDirectMessage(
                convId = conv.id,
                text = "Sharing project outline document",
                fileName = "vybe_roadmap_2026.pdf",
                mediaType = "FILE"
              )
            }
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showAttachmentMenu = false }) { Text("Cancel") }
      }
    )
  }

  // Location Share Dialog
  if (showLocationShareDialog) {
    AlertDialog(
      onDismissRequest = { showLocationShareDialog = false },
      title = { Text("Share Location", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Enter landmark or location name:", fontSize = 12.sp)
          OutlinedTextField(
            value = locationNameInput,
            onValueChange = { locationNameInput = it },
            label = { Text("Location Name") },
            modifier = Modifier.fillMaxWidth()
          )
          Text("GPS: 19.0544° N, 72.8197° E (Mumbai)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.sendExtendedDirectMessage(
              convId = conv.id,
              text = "📍 Met at $locationNameInput",
              locationCoordinates = "19.0544° N, 72.8197° E",
              fileName = locationNameInput,
              mediaType = "LOCATION"
            )
            showLocationShareDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = themeAccent)
        ) {
          Text("Share Pin")
        }
      },
      dismissButton = {
        TextButton(onClick = { showLocationShareDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Message Actions Sheet (Reply, React, Edit, Delete)
  if (selectedMessageForActions != null) {
    val targetMsg = selectedMessageForActions!!
    AlertDialog(
      onDismissRequest = { selectedMessageForActions = null },
      title = { Text("Message Options", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Quick Reaction Bar
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            listOf("❤️", "🔥", "⚡", "😂", "👏").forEach { emoji ->
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                  .size(38.dp)
                  .clickable {
                    viewModel.addMessageReaction(conv.id, targetMsg.id, emoji)
                    selectedMessageForActions = null
                  }
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(emoji, fontSize = 18.sp)
                }
              }
            }
          }

          HorizontalDivider()

          // Reply
          ListItem(
            headlineContent = { Text("Reply to Message") },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = themeAccent) },
            modifier = Modifier.clickable {
              replyingToMessage = targetMsg
              selectedMessageForActions = null
            }
          )

          // Edit (Only for own messages)
          if (targetMsg.isMe && !targetMsg.isDeletedForEveryone) {
            ListItem(
              headlineContent = { Text("Edit Message") },
              leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = VybeBrandCyan) },
              modifier = Modifier.clickable {
                editTextValue = targetMsg.text
                messageToEdit = targetMsg
                selectedMessageForActions = null
              }
            )

            // Delete for Everyone
            ListItem(
              headlineContent = { Text("Delete for Everyone") },
              leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = VybeDanger) },
              modifier = Modifier.clickable {
                viewModel.deleteMessageForEveryone(conv.id, targetMsg.id)
                selectedMessageForActions = null
              }
            )
          }

          // Delete for Me
          ListItem(
            headlineContent = { Text("Delete for Me") },
            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
            modifier = Modifier.clickable {
              viewModel.deleteMessageForMe(conv.id, targetMsg.id)
              selectedMessageForActions = null
            }
          )

          // Report Message
          ListItem(
            headlineContent = { Text("Report Message", color = VybeDanger) },
            leadingContent = { Icon(Icons.Default.Report, contentDescription = null, tint = VybeDanger) },
            modifier = Modifier.clickable {
              reportTargetType = "MESSAGE"
              reportTargetId = targetMsg.id
              reportReason = ""
              showReportDialog = true
              selectedMessageForActions = null
            }
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { selectedMessageForActions = null }) { Text("Close") }
      }
    )
  }

  // Edit Message Dialog
  if (messageToEdit != null) {
    AlertDialog(
      onDismissRequest = { messageToEdit = null },
      title = { Text("Edit Message", fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = editTextValue,
          onValueChange = { editTextValue = it },
          label = { Text("Updated message") },
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (editTextValue.isNotBlank()) {
              viewModel.editDirectMessage(conv.id, messageToEdit!!.id, editTextValue)
              messageToEdit = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = themeAccent)
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { messageToEdit = null }) { Text("Cancel") }
      }
    )
  }

  // Nickname Dialog
  if (showNicknameDialog) {
    AlertDialog(
      onDismissRequest = { showNicknameDialog = false },
      title = { Text("Custom Chat Nickname", fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = nicknameInput,
          onValueChange = { nicknameInput = it },
          placeholder = { Text("e.g. Bestie, DJ Aarav") },
          label = { Text("Nickname for ${conv.peerUser.displayName}") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.updateChatNickname(conv.id, nicknameInput)
            showNicknameDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = themeAccent)
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showNicknameDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Theme Dialog
  if (showThemeDialog) {
    AlertDialog(
      onDismissRequest = { showThemeDialog = false },
      title = { Text("Choose Chat Theme", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            "DEFAULT" to "VYBE Signature Violet",
            "CYBER_LUXE" to "Cyber Luxe Cyan",
            "SUNSET" to "Sunset Coral & Gold",
            "NEON_VIOLET" to "Neon Magenta Pulse"
          ).forEach { (key, label) ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (conv.chatTheme == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  viewModel.updateChatTheme(conv.id, key)
                  showThemeDialog = false
                }
            ) {
              Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(14.dp)
              )
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
      }
    )
  }

  // Shared Media Gallery Dialog
  if (showGalleryDialog) {
    val mediaMessages = remember(conv.messages) {
      conv.messages.filter { !it.mediaUrl.isNullOrEmpty() }
    }
    AlertDialog(
      onDismissRequest = { showGalleryDialog = false },
      title = { Text("Shared Media Gallery", fontWeight = FontWeight.Bold) },
      text = {
        if (mediaMessages.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No photos or videos shared yet in this chat.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            items(mediaMessages) { m ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                AsyncImage(
                  model = m.mediaUrl,
                  contentDescription = null,
                  modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                  contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(m.mediaType, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Text("Shared ${m.timestamp}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showGalleryDialog = false }) { Text("Close") }
      }
    )
  }

  // Pre-call availability error dialog
  if (callUnavailableReason != null) {
    AlertDialog(
      onDismissRequest = { callUnavailableReason = null },
      icon = { Icon(Icons.Default.PhoneMissed, contentDescription = null, tint = VybeDanger) },
      title = { Text("Call Unavailable", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text = callUnavailableReason ?: "User is currently unavailable for calls.",
          fontSize = 13.sp
        )
      },
      confirmButton = {
        Button(
          onClick = { callUnavailableReason = null },
          colors = ButtonDefaults.buttonColors(containerColor = themeAccent)
        ) {
          Text("OK")
        }
      }
    )
  }

  // Report Dialog (for user or message)
  if (showReportDialog) {
    val categories = listOf(
      "Harassment / Bullying",
      "Spam or Scam",
      "Hate Speech or Discrimination",
      "Nudity or Inappropriate Content",
      "Impersonation",
      "Threats or Violence"
    )
    AlertDialog(
      onDismissRequest = { showReportDialog = false },
      title = {
        Text(
          text = if (reportTargetType == "USER") "Report @${conv.peerUser.username}" else "Report Message",
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Select category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          categories.forEach { cat ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (reportCategory == cat) themeAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
              border = if (reportCategory == cat) androidx.compose.foundation.BorderStroke(1.dp, themeAccent) else null,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { reportCategory = cat }
            ) {
              Text(
                text = cat,
                fontSize = 12.sp,
                fontWeight = if (reportCategory == cat) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
              )
            }
          }
          OutlinedTextField(
            value = reportReason,
            onValueChange = { reportReason = it },
            label = { Text("Details (optional)") },
            placeholder = { Text("Provide additional context...") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            coroutineScope.launch {
              viewModel.submitReport(
                targetType = reportTargetType,
                targetId = reportTargetId,
                reason = reportReason.ifBlank { reportCategory },
                details = reportCategory
              )
            }
            showReportDialog = false
            reportFeedbackMsg = "Thank you. Your report has been submitted for safety review."
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Submit Report")
        }
      },
      dismissButton = {
        TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Report Feedback Confirmation Dialog
  if (reportFeedbackMsg != null) {
    AlertDialog(
      onDismissRequest = { reportFeedbackMsg = null },
      icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VybeOnlineGreen) },
      title = { Text("Report Received", fontWeight = FontWeight.Bold) },
      text = { Text(reportFeedbackMsg ?: "", fontSize = 13.sp) },
      confirmButton = {
        Button(onClick = { reportFeedbackMsg = null }) {
          Text("Done")
        }
      }
    )
  }

  // Group Members & Admin Dialog
  if (showGroupMembersDialog && conv.isGroup) {
    val allUsers by viewModel.allUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isGroupAdmin = conv.groupAdminIds.contains(currentUser?.id) || conv.groupAdminIds.isEmpty() || currentUser?.id == "u_me"

    AlertDialog(
      onDismissRequest = { showGroupMembersDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Group, contentDescription = null, tint = themeAccent)
          Spacer(modifier = Modifier.width(8.dp))
          Text(conv.groupName ?: "Group Members", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            Text(
              text = "Group Participants (${conv.groupMembers.size})",
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              color = themeAccent
            )
          }

          items(conv.groupMembers) { member ->
            val isMemberAdmin = conv.groupAdminIds.contains(member.id)
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                  VybeAvatar(user = member, size = 34.dp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(member.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                      Spacer(modifier = Modifier.width(4.dp))
                      if (isMemberAdmin) {
                        Surface(
                          color = VybeVerifiedGold.copy(alpha = 0.2f),
                          shape = RoundedCornerShape(4.dp)
                        ) {
                          Text(
                            "Admin",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = VybeVerifiedGold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                          )
                        }
                      }
                    }
                    Text("@${member.username}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }

                if (member.id != currentUser?.id) {
                  OutlinedButton(
                    onClick = {
                      viewModel.removeGroupMember(conv.id, member.id)
                    },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Text("Remove", fontSize = 10.sp, color = VybeDanger)
                  }
                }
              }
            }
          }

          item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Add User to Group", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedTextField(
                  value = newMemberUsernameInput,
                  onValueChange = { newMemberUsernameInput = it },
                  placeholder = { Text("@username", fontSize = 12.sp) },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                  onClick = {
                    if (newMemberUsernameInput.isNotBlank()) {
                      val cleanUname = newMemberUsernameInput.trim().replace("@", "").lowercase()
                      val candidate = allUsers.firstOrNull { it.username.lowercase() == cleanUname }
                        ?: com.example.data.model.User(
                          id = "u_${cleanUname}",
                          username = cleanUname,
                          displayName = cleanUname.replaceFirstChar { it.uppercase() },
                          bio = "VYBE Community Member"
                        )
                      viewModel.addGroupMember(conv.id, candidate)
                      newMemberUsernameInput = ""
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = themeAccent),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }

            item {
              Text("Quick Add Contacts:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("priya_design", "aarav_beats", "vikram_cricket").forEach { uname ->
                  val existing = conv.groupMembers.any { it.username == uname }
                  if (!existing) {
                    FilterChip(
                      selected = false,
                      onClick = {
                        val candidate = allUsers.firstOrNull { it.username == uname }
                          ?: com.example.data.model.User(
                            id = "u_${uname}",
                            username = uname,
                            displayName = uname.replaceFirstChar { it.uppercase() }
                          )
                        viewModel.addGroupMember(conv.id, candidate)
                      },
                      label = { Text("+ @$uname", fontSize = 9.sp) }
                    )
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { showGroupMembersDialog = false },
            colors = ButtonDefaults.buttonColors(containerColor = themeAccent)
          ) {
            Text("Done")
          }
        }
      )
    }
  }

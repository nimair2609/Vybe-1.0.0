package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CallAvailabilityResult
import com.example.data.model.DirectMessage
import com.example.data.model.MessageStatus
import com.example.ui.VybeViewModel
import com.example.ui.components.LocationPickerDialog
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VideoFitMode
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeVideoPlayer
import com.example.ui.theme.*
import com.example.util.MediaUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val context = LocalContext.current
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
  var groupAddError by remember { mutableStateOf<String?>(null) }
  var showNicknameDialog by remember { mutableStateOf(false) }
  var nicknameInput by remember { mutableStateOf("") }
  var showThemeDialog by remember { mutableStateOf(false) }
  var showGalleryDialog by remember { mutableStateOf(false) }
  var showLocationShareDialog by remember { mutableStateOf(false) }

  // Call & Reporting states
  var callUnavailableReason by remember { mutableStateOf<String?>(null) }
  var showReportDialog by remember { mutableStateOf(false) }
  var reportTargetType by remember { mutableStateOf("USER") }
  var reportTargetId by remember { mutableStateOf("") }
  var reportCategory by remember { mutableStateOf("Harassment / Bullying") }
  var reportReason by remember { mutableStateOf("") }
  var reportFeedbackMsg by remember { mutableStateOf<String?>(null) }

  // Keep conversation synced with Supabase while open to prevent delivery delays
  LaunchedEffect(conversation?.id) {
    val activeId = conversation?.id ?: return@LaunchedEffect
    viewModel.markConversationAsRead(activeId)
    while (true) {
      viewModel.syncMessagesForConversation(activeId)
      delay(8000L)
    }
  }

  LaunchedEffect(conversation?.messages?.size) {
    if ((conversation?.messages?.size ?: 0) > 0) {
      listState.animateScrollToItem((conversation?.messages?.size ?: 1) - 1)
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null && conversation != null) {
      val localPath = MediaUtils.copyUriToLocalCache(context, uri, "chat_img")
      viewModel.sendExtendedDirectMessage(
        convId = conversation!!.id,
        text = if (messageInput.isNotBlank()) messageInput else "",
        mediaUrl = localPath,
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
      val localPath = MediaUtils.copyUriToLocalCache(context, uri, "chat_vid")
      viewModel.sendExtendedDirectMessage(
        convId = conversation!!.id,
        text = if (messageInput.isNotBlank()) messageInput else "",
        mediaUrl = localPath,
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
            modifier = Modifier.clickable(enabled = !conv.isGroup) { viewModel.openUserProfile(conv.peerUser) }
          ) {
            VybeAvatar(user = conv.peerUser, size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = conv.groupName ?: (conv.customNickname ?: conv.peerUser.displayName),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (!conv.isGroup) {
                  VerificationBadge(user = conv.peerUser)
                }
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
                text = conv.typingIndicator
                  ?: if (conv.isBlocked) "Blocked • Unblock in options"
                  else if (conv.isGroup) "${conv.groupMembers.size} participants"
                  else "@${conv.peerUser.username}",
                style = MaterialTheme.typography.bodySmall,
                color = if (conv.typingIndicator != null) themeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        },
        actions = {
          if (!conv.isGroup) {
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
                text = { Text("Group Members") },
                onClick = {
                  showChatMenu = false
                  groupAddError = null
                  showGroupMembersDialog = true
                },
                leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, tint = VybeViolet) }
              )
            } else {
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
            }
            DropdownMenuItem(
              text = { Text("Chat Theme") },
              onClick = {
                showChatMenu = false
                showThemeDialog = true
              },
              leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) }
            )
            DropdownMenuItem(
              text = { Text("Shared Media") },
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
            if (!conv.isGroup) {
              DropdownMenuItem(
                text = { Text(if (conv.isRestricted) "Unrestrict User" else "Restrict User") },
                onClick = {
                  showChatMenu = false
                  viewModel.toggleRestrictUser(conv.peerUser.id)
                },
                leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) }
              )
              DropdownMenuItem(
                text = { Text(if (conv.isBlocked) "Unblock User" else "Block User", color = VybeDanger) },
                onClick = {
                  showChatMenu = false
                  viewModel.toggleBlockUser(conv.peerUser.id)
                },
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = VybeDanger) }
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
                  "You have blocked this account. Unblock to send messages.",
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
                Text("Unblock", fontSize = 11.sp, color = Color.White)
              }
            }
          }
        } else {
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
                      text = replyingToMessage!!.text.ifBlank { replyingToMessage!!.mediaType },
                      fontSize = 12.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                  IconButton(
                    onClick = { replyingToMessage = null },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(14.dp))
                  }
                }
              }
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = { showAttachmentMenu = true },
              modifier = Modifier.testTag("chat_attachments_button")
            ) {
              Icon(Icons.Default.AddCircle, contentDescription = "Attachments", tint = themeAccent)
            }

            OutlinedTextField(
              value = messageInput,
              onValueChange = { messageInput = it },
              placeholder = { Text("Write a message...", fontSize = 14.sp) },
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("message_input_field")
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
              onClick = {
                val trimmed = messageInput.trim()
                if (trimmed.isNotEmpty()) {
                  viewModel.sendExtendedDirectMessage(
                    convId = conv.id,
                    text = trimmed,
                    replyToId = replyingToMessage?.id,
                    replyToText = replyingToMessage?.text,
                    replyToSenderName = if (replyingToMessage?.isMe == true) "You" else conv.peerUser.displayName
                  )
                  messageInput = ""
                  replyingToMessage = null
                }
              },
              enabled = messageInput.isNotBlank(),
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (messageInput.isNotBlank()) themeAccent else MaterialTheme.colorScheme.surfaceVariant)
                .testTag("send_message_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (messageInput.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
  ) { paddingValues ->
    if (conv.messages.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "No messages yet. Say hello to start the conversation.",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
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
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }

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
                .widthIn(max = 290.dp)
                .clickable { selectedMessageForActions = msg }
                .testTag("chat_bubble_${msg.id}")
            ) {
              Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!msg.isDeletedForEveryone && !msg.mediaUrl.isNullOrEmpty()) {
                  if (msg.mediaType == "IMAGE" || msg.mediaType == "GIF") {
                    AsyncImage(
                      model = msg.mediaUrl,
                      contentDescription = "Shared photo",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                  } else if (msg.mediaType == "VIDEO") {
                    VybeVideoPlayer(
                      videoUrl = msg.mediaUrl,
                      autoPlay = false,
                      isLooping = false,
                      fitMode = VideoFitMode.FIT_CONTAIN,
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                  }
                }

                if (!msg.isDeletedForEveryone && (msg.locationCoordinates != null || msg.mediaType == "LOCATION")) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      Icons.Default.LocationOn,
                      contentDescription = null,
                      tint = if (isMe) Color.White else VybeMagenta
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                      Text(
                        text = msg.fileName ?: "Shared Location",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                      )
                      if (!msg.locationCoordinates.isNullOrBlank()) {
                        Text(
                          text = msg.locationCoordinates,
                          fontSize = 10.sp,
                          color = if (isMe) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }
                  }
                }

                if (msg.text.isNotEmpty()) {
                  Text(
                    text = msg.text,
                    color = if (msg.isDeletedForEveryone) MaterialTheme.colorScheme.onSurfaceVariant else if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontStyle = if (msg.isDeletedForEveryone) FontStyle.Italic else FontStyle.Normal
                  )
                }

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
                    color = if (isMe) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                          tint = if (msg.isRead) VybeBrandCyan else Color.White.copy(alpha = 0.75f),
                          modifier = Modifier.size(12.dp)
                        )
                      }
                    }
                  }
                }
              }
            }

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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
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
  }

  // Real Attachment Options Sheet (Photo, Video, Google Maps Location Pin)
  if (showAttachmentMenu) {
    AlertDialog(
      onDismissRequest = { showAttachmentMenu = false },
      title = { Text("Share in Chat", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ListItem(
            headlineContent = { Text("Send Photo") },
            leadingContent = { Icon(Icons.Default.Photo, contentDescription = null, tint = VybeViolet) },
            modifier = Modifier.clickable {
              showAttachmentMenu = false
              photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
          )
          ListItem(
            headlineContent = { Text("Send Video") },
            leadingContent = { Icon(Icons.Default.Videocam, contentDescription = null, tint = VybeMagenta) },
            modifier = Modifier.clickable {
              showAttachmentMenu = false
              videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }
          )
          ListItem(
            headlineContent = { Text("Share Location") },
            leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFFB74D)) },
            modifier = Modifier.clickable {
              showAttachmentMenu = false
              showLocationShareDialog = true
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

  // Real Google Maps + GPS / Manual Location Picker
  if (showLocationShareDialog) {
    LocationPickerDialog(
      initialLocationName = null,
      onDismiss = { showLocationShareDialog = false },
      onLocationSelected = { res ->
        showLocationShareDialog = false
        if (res != null) {
          val coordsString = if (res.isCoordinatesVerified && res.latitude != null && res.longitude != null) {
            String.format(Locale.US, "%.4f, %.4f", res.latitude, res.longitude)
          } else {
            null
          }
          viewModel.sendExtendedDirectMessage(
            convId = conv.id,
            text = "Shared location: ${res.displayName}",
            locationCoordinates = coordsString,
            fileName = res.displayName,
            mediaType = "LOCATION"
          )
        }
      }
    )
  }

  // Message Actions Sheet (Reply, React, Edit, Delete, Report)
  if (selectedMessageForActions != null) {
    val targetMsg = selectedMessageForActions!!
    AlertDialog(
      onDismissRequest = { selectedMessageForActions = null },
      title = { Text("Message Options", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

          ListItem(
            headlineContent = { Text("Reply to Message") },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = themeAccent) },
            modifier = Modifier.clickable {
              replyingToMessage = targetMsg
              selectedMessageForActions = null
            }
          )

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

            ListItem(
              headlineContent = { Text("Delete for Everyone") },
              leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = VybeDanger) },
              modifier = Modifier.clickable {
                viewModel.deleteMessageForEveryone(conv.id, targetMsg.id)
                selectedMessageForActions = null
              }
            )
          }

          ListItem(
            headlineContent = { Text("Delete for Me") },
            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
            modifier = Modifier.clickable {
              viewModel.deleteMessageForMe(conv.id, targetMsg.id)
              selectedMessageForActions = null
            }
          )

          if (!targetMsg.isMe) {
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
          Text("Save", color = Color.White)
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
          Text("Save", color = Color.White)
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
      conv.messages.filter { !it.mediaUrl.isNullOrEmpty() && !it.isDeletedForEveryone }
    }
    AlertDialog(
      onDismissRequest = { showGalleryDialog = false },
      title = { Text("Shared Media", fontWeight = FontWeight.Bold) },
      text = {
        if (mediaMessages.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No photos or videos shared yet in this chat.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            items(mediaMessages, key = { it.id }) { m ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (m.mediaType == "VIDEO") {
                  Box(
                    modifier = Modifier
                      .size(60.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color.Black),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                  }
                } else {
                  AsyncImage(
                    model = m.mediaUrl,
                    contentDescription = null,
                    modifier = Modifier
                      .size(60.dp)
                      .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                  )
                }
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

  // Call Unavailable Dialog
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
          Text("OK", color = Color.White)
        }
      }
    )
  }

  // Report Dialog
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Select category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          categories.forEach { cat ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (reportCategory == cat) themeAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
              border = if (reportCategory == cat) BorderStroke(1.dp, themeAccent) else null,
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
          Text("Submit Report", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
      }
    )
  }

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

  // Group Members Dialog (Real Community Users Only)
  if (showGroupMembersDialog && conv.isGroup) {
    val allUsers by viewModel.allUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isGroupAdmin = conv.groupAdminIds.contains(currentUser?.id) || conv.groupAdminIds.isEmpty()

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
            .heightIn(max = 400.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            Text(
              text = "Participants (${conv.groupMembers.size})",
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              color = themeAccent
            )
          }

          items(conv.groupMembers, key = { it.id }) { member ->
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
                      if (isMemberAdmin) {
                        Spacer(modifier = Modifier.width(4.dp))
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

                if (isGroupAdmin && member.id != currentUser?.id) {
                  OutlinedButton(
                    onClick = { viewModel.removeGroupMember(conv.id, member.id) },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Text("Remove", fontSize = 10.sp, color = VybeDanger)
                  }
                }
              }
            }
          }

          if (isGroupAdmin) {
            item {
              HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
              Text("Add Registered User", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedTextField(
                  value = newMemberUsernameInput,
                  onValueChange = {
                    newMemberUsernameInput = it
                    groupAddError = null
                  },
                  placeholder = { Text("@username", fontSize = 12.sp) },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                  onClick = {
                    val cleanUname = newMemberUsernameInput.trim().removePrefix("@").lowercase()
                    val candidate = allUsers.firstOrNull { it.username.lowercase() == cleanUname }
                    if (candidate == null) {
                      groupAddError = "No registered user found with @$cleanUname."
                    } else {
                      viewModel.addGroupMember(conv.id, candidate)
                      newMemberUsernameInput = ""
                      groupAddError = null
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = themeAccent),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                  Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
              }
              if (groupAddError != null) {
                Text(
                  text = groupAddError ?: "",
                  color = VybeDanger,
                  fontSize = 11.sp,
                  modifier = Modifier.padding(top = 4.dp)
                )
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
          Text("Done", color = Color.White)
        }
      }
    )
  }
}

package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.Job
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeVideoPlayer
import com.example.ui.theme.*
import com.example.util.MediaUtils
import com.example.util.QrCodeGenerator
import com.example.util.TimestampUtils
import com.example.util.rememberMessageTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
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
      delay(2500L)
    }
  }

  var typingJob by remember { mutableStateOf<Job?>(null) }
  LaunchedEffect(messageInput, conversation?.id) {
    val activeConv = conversation ?: return@LaunchedEffect
    if (activeConv.id.isNotBlank() && !activeConv.isBlocked) {
      if (messageInput.isNotEmpty()) {
        viewModel.sendTypingIndicator(activeConv.id, activeConv.peerUser.id, true)
        typingJob?.cancel()
        typingJob = coroutineScope.launch {
          delay(2500L)
          viewModel.sendTypingIndicator(activeConv.id, activeConv.peerUser.id, false)
        }
      } else {
        typingJob?.cancel()
        viewModel.sendTypingIndicator(activeConv.id, activeConv.peerUser.id, false)
      }
    }
  }

  DisposableEffect(conversation?.id) {
    onDispose {
      typingJob?.cancel()
      val activeConv = conversation
      if (activeConv != null && activeConv.id.isNotBlank()) {
        viewModel.sendTypingIndicator(activeConv.id, activeConv.peerUser.id, false)
      }
    }
  }

  val visibleMessages = remember(convMessagesKey(conversation)) {
    conversation?.messages?.filter { !it.isDeletedForEveryone }.orEmpty()
  }
  val density = LocalDensity.current
  val isImeVisible = WindowInsets.ime.getBottom(density) > 0
  val hasTypingIndicator = conversation?.typingIndicator != null

  val threadIdentity = remember(
    conversation?.isGroup,
    conversation?.groupName,
    conversation?.peerUser?.id,
    conversation?.id
  ) {
    val c = conversation
    when {
      c == null -> ""
      c.isGroup -> "grp_${c.groupName ?: c.id}"
      else -> "dm_${c.peerUser.id}"
    }
  }

  val isNearBottom by remember {
    derivedStateOf {
      val layoutInfo = listState.layoutInfo
      val total = layoutInfo.totalItemsCount
      if (total <= 1) {
        true
      } else {
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        lastVisible >= total - 3
      }
    }
  }

  var unreadBelowCount by remember(threadIdentity) { mutableIntStateOf(0) }
  var hasAnchoredInitialOpen by remember(threadIdentity) { mutableStateOf(false) }
  var lastTrackedMessageKey by remember(threadIdentity) { mutableStateOf<String?>(null) }
  var prevImeVisible by remember(threadIdentity) { mutableStateOf(false) }

  LaunchedEffect(isNearBottom) {
    if (isNearBottom && unreadBelowCount > 0) {
      unreadBelowCount = 0
    }
  }

  val lastMessageIdentity = visibleMessages.lastOrNull()?.let { it.clientId ?: it.id }

  LaunchedEffect(threadIdentity, lastMessageIdentity, visibleMessages.size) {
    if (threadIdentity.isBlank()) return@LaunchedEffect
    val targetIndex = visibleMessages.size + (if (hasTypingIndicator) 1 else 0) - 1
    if (!hasAnchoredInitialOpen) {
      hasAnchoredInitialOpen = true
      lastTrackedMessageKey = lastMessageIdentity
      if (targetIndex >= 0) {
        listState.scrollToItem(targetIndex)
      }
      return@LaunchedEffect
    }

    if (lastMessageIdentity != null && lastMessageIdentity != lastTrackedMessageKey) {
      val newest = visibleMessages.lastOrNull()
      lastTrackedMessageKey = lastMessageIdentity
      if (targetIndex >= 0) {
        if (newest?.isMe == true) {
          unreadBelowCount = 0
          listState.animateScrollToItem(targetIndex)
        } else if (isNearBottom) {
          listState.animateScrollToItem(targetIndex)
        } else {
          unreadBelowCount += 1
        }
      }
    }
  }

  LaunchedEffect(isImeVisible) {
    val openedNow = isImeVisible && !prevImeVisible
    prevImeVisible = isImeVisible
    if (openedNow && isNearBottom) {
      delay(100L)
      val targetIndex = visibleMessages.size + (if (hasTypingIndicator) 1 else 0) - 1
      if (targetIndex >= 0) {
        listState.animateScrollToItem(targetIndex)
      }
    }
  }

  LaunchedEffect(hasTypingIndicator) {
    if (hasTypingIndicator && isNearBottom) {
      val targetIndex = visibleMessages.size
      if (targetIndex >= 0) {
        listState.animateScrollToItem(targetIndex)
      }
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
    "CYBER_LUXE" -> VybeGrowBlueBright
    "SUNSET" -> Color(0xFFFF6B6B)
    "NEON_VIOLET" -> VybeSocialVioletBright
    else -> palette.accent
  }

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      Surface(color = palette.canvas) {
        Column {
          TopAppBar(
            navigationIcon = {
              IconButton(
                onClick = { viewModel.navigateBack() },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("chat_back_button")
              ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
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
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (!conv.isGroup) {
                    VerificationBadge(user = conv.peerUser)
                  }
                  if (conv.isBlocked) {
                    Surface(
                      color = palette.danger.copy(alpha = 0.16f),
                      shape = RoundedCornerShape(VybeTokens.Radius.xs)
                    ) {
                      Text(
                        text = "Blocked",
                        color = palette.danger,
                        fontSize = 9.sp,
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
                        text = "Restricted",
                        color = palette.warning,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                      )
                    }
                  }
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                  val showOnlineDot = conv.typingIndicator == null && !conv.isBlocked && !conv.isGroup && conv.peerUser.isOnline
                  if (showOnlineDot) {
                    Box(
                      modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(palette.success)
                    )
                  }
                  Text(
                    text = conv.typingIndicator
                      ?: if (conv.isBlocked) "Blocked • Unblock in options"
                      else if (conv.isGroup) "${conv.groupMembers.size} participants"
                      else if (conv.peerUser.isOnline) "Active now • @${conv.peerUser.username}"
                      else {
                        val lastSeen = TimestampUtils.formatLastSeen(conv.peerUser.lastSeenAtIso)
                        if (lastSeen.isNotBlank()) "$lastSeen • @${conv.peerUser.username}"
                        else "@${conv.peerUser.username}"
                      },
                    fontSize = 12.sp,
                    color = when {
                      conv.typingIndicator != null -> themeAccent
                      showOnlineDot -> palette.success
                      else -> palette.textSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
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
                modifier = Modifier.size(48.dp).testTag("chat_voice_call_btn")
              ) {
                Icon(Icons.Outlined.Call, contentDescription = "Voice Call", tint = palette.textPrimary)
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
                modifier = Modifier.size(48.dp).testTag("chat_video_call_btn")
              ) {
                Icon(Icons.Outlined.Videocam, contentDescription = "Video Call", tint = palette.textPrimary)
              }
            }
            IconButton(
              onClick = { showChatMenu = true },
              modifier = Modifier.size(48.dp).testTag("chat_options_menu_btn")
            ) {
              Icon(Icons.Outlined.MoreVert, contentDescription = "Chat Options", tint = palette.textPrimary)
            }

            DropdownMenu(
              expanded = showChatMenu,
              onDismissRequest = { showChatMenu = false },
              containerColor = palette.elevated
            ) {
              if (conv.isGroup) {
                DropdownMenuItem(
                  text = { Text("Group Members", color = palette.textPrimary) },
                  onClick = {
                    showChatMenu = false
                    groupAddError = null
                    showGroupMembersDialog = true
                  },
                  leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, tint = palette.accent) }
                )
              } else {
                DropdownMenuItem(
                  text = { Text("View Profile", color = palette.textPrimary) },
                  onClick = {
                    showChatMenu = false
                    viewModel.openUserProfile(conv.peerUser)
                  },
                  leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = palette.textSecondary) }
                )
                DropdownMenuItem(
                  text = { Text("Set Nickname", color = palette.textPrimary) },
                  onClick = {
                    showChatMenu = false
                    nicknameInput = conv.customNickname ?: ""
                    showNicknameDialog = true
                  },
                  leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = palette.textSecondary) }
                )
              }
              DropdownMenuItem(
                text = { Text("Chat Theme", color = palette.textPrimary) },
                onClick = {
                  showChatMenu = false
                  showThemeDialog = true
                },
                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = palette.accent) }
              )
              DropdownMenuItem(
                text = { Text("Shared Media", color = palette.textPrimary) },
                onClick = {
                  showChatMenu = false
                  showGalleryDialog = true
                },
                leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null, tint = palette.textSecondary) }
              )
              HorizontalDivider(color = palette.border)
              DropdownMenuItem(
                text = { Text(if (conv.isMuted) "Unmute Chat" else "Mute Chat", color = palette.textPrimary) },
                onClick = {
                  showChatMenu = false
                  viewModel.toggleMuteChat(conv.id)
                },
                leadingIcon = {
                  Icon(
                    if (conv.isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = palette.textSecondary
                  )
                }
              )
              if (!conv.isGroup) {
                DropdownMenuItem(
                  text = { Text(if (conv.isRestricted) "Unrestrict User" else "Restrict User", color = palette.textPrimary) },
                  onClick = {
                    showChatMenu = false
                    viewModel.toggleRestrictUser(conv.peerUser.id)
                  },
                  leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = palette.textSecondary) }
                )
                DropdownMenuItem(
                  text = { Text(if (conv.isBlocked) "Unblock User" else "Block User", color = palette.danger) },
                  onClick = {
                    showChatMenu = false
                    viewModel.toggleBlockUser(conv.peerUser.id)
                  },
                  leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = palette.danger) }
                )
                DropdownMenuItem(
                  text = { Text("Report User", color = palette.danger) },
                  onClick = {
                    showChatMenu = false
                    reportTargetType = "USER"
                    reportTargetId = conv.peerUser.id
                    reportReason = ""
                    showReportDialog = true
                  },
                  leadingIcon = { Icon(Icons.Default.Report, contentDescription = null, tint = palette.danger) }
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.canvas)
        )
          HorizontalDivider(thickness = 0.8.dp, color = palette.border)
        }
      }
    },
    bottomBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.card)
          .navigationBarsPadding()
          .imePadding()
      ) {
        HorizontalDivider(thickness = 0.8.dp, color = palette.border)
        if (conv.isBlocked) {
          Surface(
            color = palette.danger.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.35f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            shape = RoundedCornerShape(VybeTokens.Radius.md)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, contentDescription = null, tint = palette.danger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  "You have blocked this account. Unblock to send messages.",
                  fontSize = 12.sp,
                  fontFamily = PlusJakartaSansFontFamily,
                  color = palette.textPrimary
                )
              }
              Button(
                onClick = { viewModel.toggleBlockUser(conv.peerUser.id) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.danger, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(VybeTokens.Radius.sm)
              ) {
                Text("Unblock", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
              }
            }
          }
        } else {
          AnimatedVisibility(visible = replyingToMessage != null) {
            if (replyingToMessage != null) {
              Surface(
                color = palette.elevated,
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .width(3.dp)
                      .height(28.dp)
                      .clip(RoundedCornerShape(2.dp))
                      .background(themeAccent)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Replying to ${if (replyingToMessage!!.isMe) "yourself" else conv.peerUser.displayName}",
                      fontWeight = FontWeight.Bold,
                      fontFamily = PlusJakartaSansFontFamily,
                      fontSize = 11.sp,
                      color = themeAccent
                    )
                    Text(
                      text = replyingToMessage!!.text.ifBlank { replyingToMessage!!.mediaType },
                      fontSize = 12.sp,
                      fontFamily = PlusJakartaSansFontFamily,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = palette.textSecondary
                    )
                  }
                  IconButton(
                    onClick = { replyingToMessage = null },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }
          }

          val dispatchSendTextMessage = {
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
              Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Attachments", tint = themeAccent)
            }

            OutlinedTextField(
              value = messageInput,
              onValueChange = { messageInput = it },
              placeholder = { Text("Write a message...", fontSize = 14.sp, color = palette.textMuted) },
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.elevated,
                unfocusedContainerColor = palette.elevated,
                focusedBorderColor = themeAccent,
                unfocusedBorderColor = palette.border,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary
              ),
              maxLines = 4,
              keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
              ),
              keyboardActions = KeyboardActions(
                onSend = { dispatchSendTextMessage() }
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("message_input_field")
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
              onClick = { dispatchSendTextMessage() },
              enabled = messageInput.isNotBlank(),
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (messageInput.isNotBlank()) themeAccent else palette.elevated)
                .testTag("send_message_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Send",
                tint = if (messageInput.isNotBlank()) Color.White else palette.textMuted,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
  ) { paddingValues ->
    if (visibleMessages.isEmpty() && conv.typingIndicator == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        VybeEmptyState(
          icon = Icons.Outlined.ChatBubbleOutline,
          title = "Start the Conversation",
          subtitle = "Say hello to ${conv.groupName ?: conv.peerUser.displayName}. Messages are delivered cleanly in real time."
        )
      }
    } else {
      val lastSentByMeId = remember(visibleMessages) {
        visibleMessages.lastOrNull { it.isMe && it.mediaType != "CALL_LOG" }?.id
      }
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("chat_messages_list"),
          contentPadding = PaddingValues(vertical = 10.dp)
        ) {
          itemsIndexed(visibleMessages, key = { _, msg -> msg.clientId ?: msg.id }) { index, msg ->
          val isMe = msg.isMe
          val prevMsg = visibleMessages.getOrNull(index - 1)
          val nextMsg = visibleMessages.getOrNull(index + 1)
          val prevSame = prevMsg != null && prevMsg.isMe == isMe && prevMsg.mediaType != "CALL_LOG"
          val nextSame = nextMsg != null && nextMsg.isMe == isMe && nextMsg.mediaType != "CALL_LOG"

          if (msg.mediaType == "CALL_LOG" && !msg.isDeletedForEveryone) {
            val rawMeta = msg.mediaUrl.orEmpty()
            val isVideoCallLog = rawMeta.contains("type=video", ignoreCase = true) ||
              msg.text.contains("video call", ignoreCase = true)
            val statusParam = Regex("status=([^&]+)").find(rawMeta)?.groupValues?.getOrNull(1)?.uppercase().orEmpty()
            val durationSec = Regex("duration=(\\d+)").find(rawMeta)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val formattedDuration = if (durationSec > 0) {
              String.format("%02d:%02d", durationSec / 60, durationSec % 60)
            } else null
            val isMissedOrDeclined = statusParam == "MISSED" || statusParam == "DECLINED" ||
              msg.text.contains("Missed", ignoreCase = true) || msg.text.contains("Declined", ignoreCase = true)
            val dynamicCallTimestamp = rememberMessageTimestamp(msg.createdAtIso, msg.timestamp)

            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (prevSame) 3.dp else 10.dp),
              horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
            ) {
              Surface(
                color = palette.card,
                shape = RoundedCornerShape(VybeTokens.Radius.lg),
                border = BorderStroke(
                  1.dp,
                  if (isMissedOrDeclined) palette.danger.copy(alpha = 0.45f) else palette.accent.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                  .widthIn(min = 220.dp, max = 300.dp)
                  .testTag("chat_call_log_${msg.id}")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(38.dp)
                      .clip(CircleShape)
                      .background(
                        if (isMissedOrDeclined) palette.danger.copy(alpha = 0.16f)
                        else palette.accent.copy(alpha = 0.16f)
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = if (isVideoCallLog) Icons.Default.Videocam else Icons.Default.Call,
                      contentDescription = if (isVideoCallLog) "Video call" else "Voice call",
                      tint = if (isMissedOrDeclined) palette.danger else palette.accent,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = msg.text.ifBlank { if (isVideoCallLog) "Video call" else "Voice call" },
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = buildString {
                        if (formattedDuration != null) {
                          append("Duration $formattedDuration • ")
                        }
                        append(dynamicCallTimestamp)
                      },
                      fontSize = 11.sp,
                      color = if (isMissedOrDeclined) palette.danger else palette.textSecondary
                    )
                  }
                  if (!conv.isGroup) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                      onClick = { viewModel.initiateCall(conv.peerUser, isVideo = isVideoCallLog) },
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                      modifier = Modifier.testTag("call_back_${msg.id}")
                    ) {
                      Text(
                        text = "Call",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                      )
                    }
                  }
                }
              }
            }
          } else {
            InstagramMessageBubbleItem(
              msg = msg,
              isMe = isMe,
              prevSame = prevSame,
              nextSame = nextSame,
              isLastSentByMe = msg.id == lastSentByMeId,
              peerUser = conv.peerUser,
              themeAccent = themeAccent,
              onReply = { replyingToMessage = it },
              onSelectActions = { selectedMessageForActions = it },
              onDoubleTapHeart = { viewModel.addMessageReaction(conv.id, it.id, "❤️") },
              onOpenSharedPost = { viewModel.openSharedPostById(it) },
              onRetry = { viewModel.retrySendMessage(conv.id, msg.id) }
            )
          }
        }

        if (conv.typingIndicator != null && !conv.isBlocked) {
          item(key = "typing_bubble") {
            InstagramTypingBubble(
              peerUser = conv.peerUser,
              themeAccent = themeAccent,
              modifier = Modifier.padding(top = 6.dp)
            )
          }
        }
      }

        if (unreadBelowCount > 0 && !isNearBottom) {
          Surface(
            onClick = {
              coroutineScope.launch {
                val targetIndex = visibleMessages.size + (if (hasTypingIndicator) 1 else 0) - 1
                if (targetIndex >= 0) {
                  unreadBelowCount = 0
                  listState.animateScrollToItem(targetIndex)
                }
              }
            },
            shape = RoundedCornerShape(20.dp),
            color = themeAccent,
            shadowElevation = 6.dp,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 12.dp)
              .testTag("jump_to_new_messages_pill")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (unreadBelowCount == 1) "1 new message" else "$unreadBelowCount new messages",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }

  // Real Attachment Options Sheet
  if (showAttachmentMenu) {
    AlertDialog(
      onDismissRequest = { showAttachmentMenu = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = { Text("Share in Chat", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            Triple("Send Photo", Icons.Default.Photo, palette.accent) to {
              showAttachmentMenu = false
              photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            Triple("Send Video", Icons.Default.Videocam, palette.accentSecondary) to {
              showAttachmentMenu = false
              videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            Triple("Share Location pin", Icons.Default.LocationOn, palette.warning) to {
              showAttachmentMenu = false
              showLocationShareDialog = true
            }
          ).forEach { (meta, onTap) ->
            Surface(
              color = palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.border),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable { onTap() }
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(meta.second, contentDescription = null, tint = meta.third)
                Spacer(modifier = Modifier.width(12.dp))
                Text(meta.first, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showAttachmentMenu = false }) { Text("Cancel", color = palette.textSecondary) }
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
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = { Text("Message Options", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            listOf("❤️", "🔥", "⚡", "😂", "👏").forEach { emoji ->
              Surface(
                shape = CircleShape,
                color = palette.elevated,
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier
                  .size(40.dp)
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

          HorizontalDivider(color = palette.border)

          ListItem(
            headlineContent = { Text("Reply to Message", color = palette.textPrimary) },
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = themeAccent) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
              .clickable {
                replyingToMessage = targetMsg
                selectedMessageForActions = null
              }
              .testTag("reply_message_option")
          )

          if (targetMsg.isMe && !targetMsg.isDeletedForEveryone) {
            ListItem(
              headlineContent = { Text("Edit Message", color = palette.textPrimary) },
              leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = palette.accent) },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              modifier = Modifier
                .clickable {
                  editTextValue = targetMsg.text
                  messageToEdit = targetMsg
                  selectedMessageForActions = null
                }
                .testTag("edit_message_option")
            )

            ListItem(
              headlineContent = { Text("Delete for Everyone", color = palette.danger, fontWeight = FontWeight.SemiBold) },
              supportingContent = { Text("Permanently removes this message for all participants", fontSize = 11.sp, color = palette.textSecondary) },
              leadingContent = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = palette.danger) },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              modifier = Modifier
                .clickable {
                  viewModel.deleteMessageForEveryone(conv.id, targetMsg.id)
                  selectedMessageForActions = null
                }
                .testTag("delete_for_everyone_option")
            )
          }

          ListItem(
            headlineContent = { Text("Delete for Me", color = palette.textPrimary) },
            leadingContent = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = palette.textSecondary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
              .clickable {
                viewModel.deleteMessageForMe(conv.id, targetMsg.id)
                selectedMessageForActions = null
              }
              .testTag("delete_for_me_option")
          )

          if (!targetMsg.isMe) {
            ListItem(
              headlineContent = { Text("Report Message", color = palette.danger) },
              leadingContent = { Icon(Icons.Outlined.Report, contentDescription = null, tint = palette.danger) },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
        TextButton(onClick = { selectedMessageForActions = null }) { Text("Close", color = palette.textSecondary) }
      }
    )
  }

  // Edit Message Dialog
  if (messageToEdit != null) {
    AlertDialog(
      onDismissRequest = { messageToEdit = null },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = { Text("Edit Message", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = editTextValue,
          onValueChange = { editTextValue = it },
          label = { Text("Updated message") },
          shape = RoundedCornerShape(VybeTokens.Radius.md),
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Save",
          containerColor = themeAccent,
          onClick = {
            if (editTextValue.isNotBlank()) {
              viewModel.editDirectMessage(conv.id, messageToEdit!!.id, editTextValue)
              messageToEdit = null
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { messageToEdit = null }) { Text("Cancel", color = palette.textSecondary) }
      }
    )
  }

  // Nickname Dialog
  if (showNicknameDialog) {
    AlertDialog(
      onDismissRequest = { showNicknameDialog = false },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          "Custom Chat Nickname",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        OutlinedTextField(
          value = nicknameInput,
          onValueChange = { nicknameInput = it },
          label = { Text("Nickname for ${conv.peerUser.displayName}", fontFamily = PlusJakartaSansFontFamily) },
          singleLine = true,
          shape = RoundedCornerShape(VybeTokens.Radius.sm),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = themeAccent,
            unfocusedBorderColor = palette.border,
            focusedContainerColor = palette.elevated,
            unfocusedContainerColor = palette.elevated,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary
          ),
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.updateChatNickname(conv.id, nicknameInput)
            showNicknameDialog = false
          },
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.White)
        ) {
          Text("Save", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
        }
      },
      dismissButton = {
        TextButton(onClick = { showNicknameDialog = false }) {
          Text("Cancel", color = palette.textSecondary, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }

  // Theme Dialog
  if (showThemeDialog) {
    AlertDialog(
      onDismissRequest = { showThemeDialog = false },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          "Choose Chat Theme",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            "DEFAULT" to "VYBE Signature Violet",
            "CYBER_LUXE" to "Cyber Luxe Blue-Cyan",
            "SUNSET" to "Sunset Coral & Gold",
            "NEON_VIOLET" to "Neon Magenta Pulse"
          ).forEach { (key, label) ->
            val selected = conv.chatTheme == key
            Surface(
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              color = if (selected) themeAccent.copy(alpha = 0.16f) else palette.elevated,
              border = BorderStroke(1.dp, if (selected) themeAccent else palette.border),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  viewModel.updateChatTheme(conv.id, key)
                  showThemeDialog = false
                }
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = label,
                  fontWeight = FontWeight.SemiBold,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 13.sp,
                  color = if (selected) themeAccent else palette.textPrimary
                )
                if (selected) {
                  Icon(Icons.Default.Check, contentDescription = null, tint = themeAccent, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showThemeDialog = false }) {
          Text("Close", color = palette.textSecondary, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }

  // Shared Media Gallery Dialog
  if (showGalleryDialog) {
    val mediaMessages = remember(conv.messages) {
      conv.messages.filter { !it.mediaUrl.isNullOrEmpty() && it.mediaType != "CALL_LOG" && !it.isDeletedForEveryone }
    }
    AlertDialog(
      onDismissRequest = { showGalleryDialog = false },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          "Shared Media",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        if (mediaMessages.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
              "No photos or videos shared yet in this chat.",
              fontSize = 12.sp,
              fontFamily = PlusJakartaSansFontFamily,
              color = palette.textSecondary
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(mediaMessages, key = { it.id }) { m ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                  .background(palette.elevated)
                  .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (m.mediaType == "VIDEO") {
                  Box(
                    modifier = Modifier
                      .size(56.dp)
                      .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                      .background(ObsidianCanvas),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                  }
                } else {
                  AsyncImage(
                    model = m.mediaUrl,
                    contentDescription = null,
                    modifier = Modifier
                      .size(56.dp)
                      .clip(RoundedCornerShape(VybeTokens.Radius.sm)),
                    contentScale = ContentScale.Crop
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    m.mediaType,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = palette.textPrimary
                  )
                  Text(
                    "Shared ${TimestampUtils.formatMessageTimestamp(m.createdAtIso, m.timestamp)}",
                    fontSize = 10.sp,
                    fontFamily = PlusJakartaSansFontFamily,
                    color = palette.textSecondary
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showGalleryDialog = false }) {
          Text("Close", color = themeAccent, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }

  // Call Unavailable Dialog
  if (callUnavailableReason != null) {
    AlertDialog(
      onDismissRequest = { callUnavailableReason = null },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      icon = { Icon(Icons.Default.PhoneMissed, contentDescription = null, tint = palette.danger) },
      title = {
        Text(
          "Call Unavailable",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        Text(
          text = callUnavailableReason ?: "User is currently unavailable for calls.",
          fontSize = 13.sp,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = { callUnavailableReason = null },
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.White)
        ) {
          Text("OK", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
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
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          text = if (reportTargetType == "USER") "Report @${conv.peerUser.username}" else "Report Message",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Select category:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PlusJakartaSansFontFamily,
            color = palette.textSecondary
          )
          categories.forEach { cat ->
            val selected = reportCategory == cat
            Surface(
              shape = RoundedCornerShape(VybeTokens.Radius.sm),
              color = if (selected) themeAccent.copy(alpha = 0.16f) else palette.elevated,
              border = BorderStroke(1.dp, if (selected) themeAccent else palette.border),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { reportCategory = cat }
            ) {
              Text(
                text = cat,
                fontSize = 12.sp,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) themeAccent else palette.textPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
              )
            }
          }
          OutlinedTextField(
            value = reportReason,
            onValueChange = { reportReason = it },
            label = { Text("Details (optional)", fontFamily = PlusJakartaSansFontFamily) },
            shape = RoundedCornerShape(VybeTokens.Radius.sm),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = themeAccent,
              unfocusedBorderColor = palette.border,
              focusedContainerColor = palette.elevated,
              unfocusedContainerColor = palette.elevated,
              focusedTextColor = palette.textPrimary,
              unfocusedTextColor = palette.textPrimary
            ),
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
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = palette.danger, contentColor = Color.White)
        ) {
          Text("Submit Report", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReportDialog = false }) {
          Text("Cancel", color = palette.textSecondary, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }

  if (reportFeedbackMsg != null) {
    AlertDialog(
      onDismissRequest = { reportFeedbackMsg = null },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = palette.success) },
      title = {
        Text(
          "Report Received",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        Text(
          reportFeedbackMsg ?: "",
          fontSize = 13.sp,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = { reportFeedbackMsg = null },
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.White)
        ) {
          Text("Done", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
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
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Group, contentDescription = null, tint = themeAccent)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            conv.groupName ?: "Group Members",
            fontWeight = FontWeight.Bold,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 16.sp,
            color = palette.textPrimary
          )
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
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 12.sp,
              color = themeAccent
            )
          }

          items(conv.groupMembers, key = { it.id }) { member ->
            val isMemberAdmin = conv.groupAdminIds.contains(member.id)
            Surface(
              color = palette.elevated,
              border = BorderStroke(1.dp, palette.border),
              shape = RoundedCornerShape(VybeTokens.Radius.sm),
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
                      Text(
                        member.displayName,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        color = palette.textPrimary
                      )
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
                    Text("@${member.username}", fontSize = 10.sp, color = palette.textSecondary)
                  }
                }

                if (isGroupAdmin && member.id != currentUser?.id) {
                  OutlinedButton(
                    onClick = { viewModel.removeGroupMember(conv.id, member.id) },
                    shape = RoundedCornerShape(VybeTokens.Radius.xs),
                    border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Text("Remove", fontSize = 10.sp, color = palette.danger, fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }

          if (isGroupAdmin) {
            item {
              HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = palette.border)
              Text(
                "Add Registered User",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFontFamily,
                color = palette.textPrimary
              )
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
                  placeholder = { Text("@username", fontSize = 12.sp, color = palette.textMuted) },
                  singleLine = true,
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = themeAccent,
                    unfocusedBorderColor = palette.border,
                    focusedContainerColor = palette.elevated,
                    unfocusedContainerColor = palette.elevated,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                  ),
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(VybeTokens.Radius.sm)
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
                  colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.White),
                  shape = RoundedCornerShape(VybeTokens.Radius.sm),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                  Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
                }
              }
              if (groupAddError != null) {
                Text(
                  text = groupAddError ?: "",
                  color = palette.danger,
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
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = themeAccent, contentColor = Color.White)
        ) {
          Text("Done", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }
}

private fun convMessagesKey(conv: com.example.data.model.Conversation?): String {
  if (conv == null) return ""
  return buildString {
    append(conv.id)
    append(':')
    append(conv.messages.size)
    append(':')
    conv.messages.forEach { m ->
      append(m.id)
      append('_')
      append(m.clientId.orEmpty())
      append('_')
      append(m.status.name)
      append('_')
      append(m.isRead)
      append('_')
      append(m.isEdited)
      append('_')
      append(m.isDeletedForEveryone)
      append('_')
      append(m.text.hashCode())
      append('_')
      append(m.mediaUrl.orEmpty().hashCode())
      append('_')
      append(m.reactions.size)
      append(',')
    }
  }
}

@Composable
private fun InstagramMessageBubbleItem(
  msg: DirectMessage,
  isMe: Boolean,
  prevSame: Boolean,
  nextSame: Boolean,
  isLastSentByMe: Boolean,
  peerUser: com.example.data.model.User,
  themeAccent: Color,
  onReply: (DirectMessage) -> Unit,
  onSelectActions: (DirectMessage) -> Unit,
  onDoubleTapHeart: (DirectMessage) -> Unit,
  onOpenSharedPost: (String) -> Unit,
  onRetry: () -> Unit
) {
  val haptic = LocalHapticFeedback.current
  val coroutineScope = rememberCoroutineScope()
  val dragOffsetX = remember { Animatable(0f) }
  var hasTriggeredReplyHaptic by remember { mutableStateOf(false) }

  var showHeartAnim by remember { mutableStateOf(false) }
  val heartScale = remember { Animatable(0f) }
  val heartAlpha = remember { Animatable(0f) }

  LaunchedEffect(showHeartAnim) {
    if (showHeartAnim) {
      heartScale.snapTo(0.25f)
      heartAlpha.snapTo(1f)
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      onDoubleTapHeart(msg)
      heartScale.animateTo(
        1.3f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
      )
      heartScale.animateTo(1.0f, animationSpec = tween(100))
      delay(400L)
      heartAlpha.animateTo(0f, animationSpec = tween(250))
      showHeartAnim = false
    }
  }

  val bubbleShape = remember(isMe, prevSame, nextSame) {
    if (isMe) {
      RoundedCornerShape(
        topStart = 18.dp,
        topEnd = if (prevSame) 4.dp else 18.dp,
        bottomEnd = if (nextSame) 4.dp else 18.dp,
        bottomStart = 18.dp
      )
    } else {
      RoundedCornerShape(
        topStart = if (prevSame) 4.dp else 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = if (nextSame) 4.dp else 18.dp
      )
    }
  }

  val bubbleGradient = remember(isMe, themeAccent) {
    if (isMe) {
      Brush.linearGradient(
        colors = listOf(
          themeAccent,
          themeAccent.copy(alpha = 0.88f)
        )
      )
    } else {
      null
    }
  }

  val replyDragThreshold = 90f

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = if (prevSame) 3.dp else 10.dp)
  ) {
    val currentDrag = dragOffsetX.value
    if (currentDrag > 10f) {
      val progress = (currentDrag / replyDragThreshold).coerceIn(0f, 1f)
      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = 8.dp)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Reply,
          contentDescription = "Reply",
          tint = themeAccent.copy(alpha = progress),
          modifier = Modifier
            .size(22.dp)
            .graphicsLayer {
              scaleX = 0.6f + (0.4f * progress)
              scaleY = 0.6f + (0.4f * progress)
              alpha = progress
            }
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
        .pointerInput(msg.id) {
          detectHorizontalDragGestures(
            onDragEnd = {
              coroutineScope.launch {
                if (dragOffsetX.value >= replyDragThreshold) {
                  onReply(msg)
                }
                dragOffsetX.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                hasTriggeredReplyHaptic = false
              }
            },
            onDragCancel = {
              coroutineScope.launch {
                dragOffsetX.animateTo(0f)
                hasTriggeredReplyHaptic = false
              }
            },
            onHorizontalDrag = { _, dragAmount ->
              coroutineScope.launch {
                val nextOffset = (dragOffsetX.value + dragAmount).coerceIn(0f, 150f)
                if (nextOffset >= replyDragThreshold && !hasTriggeredReplyHaptic) {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  hasTriggeredReplyHaptic = true
                }
                dragOffsetX.snapTo(nextOffset)
              }
            }
          )
        },
      horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
      verticalAlignment = Alignment.Bottom
    ) {
      if (!isMe) {
        if (!nextSame) {
          VybeAvatar(user = peerUser, size = 26.dp)
        } else {
          Spacer(modifier = Modifier.width(26.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
      }

      Column(
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
        modifier = Modifier.widthIn(max = 290.dp)
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

        Box(
          modifier = Modifier
            .clip(bubbleShape)
            .then(
              if (bubbleGradient != null && !msg.isDeletedForEveryone) {
                Modifier.background(bubbleGradient)
              } else {
                Modifier.background(
                  if (msg.isDeletedForEveryone) {
                    LocalVybePalette.current.elevated.copy(alpha = 0.65f)
                  } else {
                    LocalVybePalette.current.elevated
                  }
                )
              }
            )
            .pointerInput(msg.id) {
              detectTapGestures(
                onTap = { onSelectActions(msg) },
                onLongPress = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  onSelectActions(msg)
                },
                onDoubleTap = { showHeartAnim = true }
              )
            }
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
                color = if (msg.isDeletedForEveryone) MaterialTheme.colorScheme.onSurfaceVariant
                  else if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontStyle = if (msg.isDeletedForEveryone) FontStyle.Italic else FontStyle.Normal
              )

              val sharedPostId = if (!msg.isDeletedForEveryone) {
                QrCodeGenerator.extractPostIdFromText(msg.text)
              } else null

              if (!sharedPostId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                val palette = LocalVybePalette.current
                Surface(
                  color = if (isMe) Color.White.copy(alpha = 0.16f) else palette.accent.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(
                    1.dp,
                    if (isMe) Color.White.copy(alpha = 0.3f) else palette.accent.copy(alpha = 0.4f)
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenSharedPost(sharedPostId) }
                    .testTag("open_shared_post_chip_${msg.id}")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "Open shared post",
                        tint = if (isMe) Color.White else VybeViolet,
                        modifier = Modifier.size(15.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "Open Shared Post",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMe) Color.White else VybeViolet
                      )
                    }
                    Icon(
                      imageVector = Icons.Default.ChevronRight,
                      contentDescription = null,
                      tint = if (isMe) Color.White else VybeViolet,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
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
              val dynamicMsgTimestamp = rememberMessageTimestamp(msg.createdAtIso, msg.timestamp)
              Text(
                text = dynamicMsgTimestamp,
                color = if (isMe) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
              )
            }
          }

          if (showHeartAnim || heartAlpha.value > 0f) {
            Box(
              modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                  scaleX = heartScale.value
                  scaleY = heartScale.value
                  alpha = heartAlpha.value
                },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Heart reaction",
                tint = Color(0xFFFF2B56),
                modifier = Modifier.size(46.dp)
              )
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
                  onDoubleTapHeart(msg)
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

        if (isLastSentByMe) {
          InstagramMessageDeliveryFooter(
            status = msg.status,
            isRead = msg.isRead,
            peerUser = peerUser,
            themeAccent = themeAccent,
            onRetry = onRetry
          )
        } else if (isMe && msg.status == MessageStatus.FAILED) {
          InstagramMessageDeliveryFooter(
            status = msg.status,
            isRead = msg.isRead,
            peerUser = peerUser,
            themeAccent = themeAccent,
            onRetry = onRetry
          )
        }
      }
    }
  }
}

@Composable
fun InstagramMessageDeliveryFooter(
  status: MessageStatus,
  isRead: Boolean,
  peerUser: com.example.data.model.User,
  themeAccent: Color,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .padding(top = 3.dp, end = 4.dp)
      .testTag("chat_delivery_status_footer"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.End
  ) {
    when (status) {
      MessageStatus.SENDING -> {
        CircularProgressIndicator(
          modifier = Modifier.size(10.dp),
          strokeWidth = 1.5.dp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Sending...",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      MessageStatus.FAILED -> {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onRetry() }
            .padding(horizontal = 2.dp)
        ) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Failed. Tap to retry",
            tint = Color(0xFFFF6B6B),
            modifier = Modifier.size(13.dp)
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "Not delivered • Tap to retry",
            color = Color(0xFFFF6B6B),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
      MessageStatus.SENT -> {
        if (isRead) {
          Text(
            text = "Seen",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
          )
          Spacer(modifier = Modifier.width(4.dp))
          VybeAvatar(
            user = peerUser,
            size = 14.dp
          )
        } else {
          Icon(
            imageVector = Icons.Default.Done,
            contentDescription = "Delivered",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.size(13.dp)
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "Delivered",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal
          )
        }
      }
    }
  }
}

@Composable
fun InstagramTypingBubble(
  peerUser: com.example.data.model.User,
  themeAccent: Color,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

  val dot1Alpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1200
        0.3f at 0
        1f at 300
        0.3f at 600
        0.3f at 1200
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "dot1_alpha"
  )
  val dot1Offset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = -5f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1200
        0f at 0
        -5f at 300
        0f at 600
        0f at 1200
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "dot1_offset"
  )

  val dot2Alpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1200
        0.3f at 0
        0.3f at 200
        1f at 500
        0.3f at 800
        0.3f at 1200
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "dot2_alpha"
  )
  val dot2Offset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = -5f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1200
        0f at 0
        0f at 200
        -5f at 500
        0f at 800
        0f at 1200
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "dot2_offset"
  )

  val dot3Alpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1200
        0.3f at 0
        0.3f at 400
        1f at 700
        0.3f at 1000
        0.3f at 1200
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "dot3_alpha"
  )
  val dot3Offset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = -5f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 1200
        0f at 0
        0f at 400
        -5f at 700
        0f at 1000
        0f at 1200
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "dot3_offset"
  )

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .testTag("chat_typing_indicator_bubble"),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.Start
  ) {
    VybeAvatar(user = peerUser, size = 26.dp)
    Spacer(modifier = Modifier.width(8.dp))
    val palette = LocalVybePalette.current
    Surface(
      shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp),
      color = palette.elevated,
      border = BorderStroke(1.dp, palette.border)
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .offset(y = dot1Offset.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(themeAccent.copy(alpha = dot1Alpha))
        )
        Box(
          modifier = Modifier
            .offset(y = dot2Offset.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(themeAccent.copy(alpha = dot2Alpha))
        )
        Box(
          modifier = Modifier
            .offset(y = dot3Offset.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(themeAccent.copy(alpha = dot3Alpha))
        )
      }
    }
  }
}

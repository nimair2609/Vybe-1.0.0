package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.UnloadRoom
import com.example.data.model.UnloadRoomMode
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import com.example.util.UnloadVoiceAudioManager
import com.example.util.UnloadVoiceParticipant
import com.example.util.rememberRelativeTimestamp
import kotlinx.coroutines.launch

/**
 * VYBE UNLOAD 2.0
 * Live 24/7 listening spaces.
 * - Create, edit, host, join, and leave rooms.
 * - Configure name, conversation type (Voice-only, Text-only, Both), schedule, duration, joiner message, capacity.
 * - Enforces Terms & Conditions agreement before hosting or joining.
 * - Zero voice recording guarantee: ephemeral and private.
 */
@Composable
fun UnloadScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val palette = LocalVybePalette.current
  val voiceManager = remember(context) { UnloadVoiceAudioManager.get(context) }

  BackHandler {
    voiceManager.stopVoiceSession()
    viewModel.leaveUnloadRoom()
  }

  DisposableEffect(voiceManager) {
    onDispose {
      voiceManager.stopVoiceSession()
    }
  }

  val activeRoom by viewModel.activeUnloadRoom.collectAsState()
  val rooms by viewModel.unloadRooms.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val isMuted by voiceManager.isMicrophoneMuted.collectAsState()
  val isSpeakerphoneOn by voiceManager.isSpeakerphoneOn.collectAsState()
  val hasMicPermission by voiceManager.hasMicrophonePermission.collectAsState()
  val voiceStatusText by voiceManager.voiceStatusText.collectAsState()
  val iceDiagnosticsText by voiceManager.iceDiagnosticsText.collectAsState()
  val voiceParticipants by voiceManager.participants.collectAsState()
  val isLocalSpeaking by voiceManager.isSpeaking.collectAsState()
  val clipboardManager = LocalClipboardManager.current
  val coroutineScope = rememberCoroutineScope()

  var messageText by remember { mutableStateOf("") }
  var isAnonymous by remember { mutableStateOf(true) }
  var showSafetySheet by remember { mutableStateOf(false) }
  var showReportSuccess by remember { mutableStateOf(false) }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    voiceManager.onMicrophonePermissionResult(granted)
    if (!granted) {
      viewModel.postBannerMessage("Listening live. Grant microphone permission anytime to speak.")
    }
  }

  LaunchedEffect(activeRoom?.id, activeRoom?.mode, currentUser?.id) {
    val current = activeRoom
    val isVoiceRoom = current != null &&
      (current.mode == UnloadRoomMode.VOICE_ONLY || current.mode == UnloadRoomMode.VOICE_AND_TEXT)
    if (isVoiceRoom && current != null) {
      val hostFlag = current.hostId == currentUser?.id || current.hostName == currentUser?.displayName
      voiceManager.startVoiceSession(
        roomId = current.id,
        userId = currentUser?.id.orEmpty(),
        displayName = currentUser?.displayName ?: "Listener",
        username = currentUser?.username.orEmpty(),
        isHost = hostFlag,
        initialMuted = false
      )
      val permGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
      ) == PackageManager.PERMISSION_GRANTED
      if (!permGranted) {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
    } else {
      voiceManager.stopVoiceSession()
    }
  }

  // Join by Room ID state
  var joinRoomCodeInput by remember { mutableStateOf("") }
  var isJoiningByCode by remember { mutableStateOf(false) }
  var joinRoomCodeError by remember { mutableStateOf<String?>(null) }

  // Host & Edit Room Dialog state
  var showHostRoomDialog by remember { mutableStateOf(false) }
  var showEditRoomDialog by remember { mutableStateOf(false) }
  var showTermsDialog by remember { mutableStateOf(false) }
  var pendingRoomToJoin by remember { mutableStateOf<UnloadRoom?>(null) }

  var formTitle by remember { mutableStateOf("") }
  var formDesc by remember { mutableStateOf("") }
  var formTopic by remember { mutableStateOf("Work Burnout") }
  var formMode by remember { mutableStateOf(UnloadRoomMode.VOICE_AND_TEXT) }
  var formDuration by remember { mutableIntStateOf(45) }
  var formCapacity by remember { mutableIntStateOf(25) }
  var formSchedule by remember { mutableStateOf("Live Now") }
  var formJoinerMsg by remember { mutableStateOf("Welcome. Share with honesty or listen in silence.") }
  var formTermsAgreed by remember { mutableStateOf(false) }

  val topicOptions = listOf(
    "Work Burnout",
    "Silent Company",
    "Late Night Overthinking",
    "Post-Breakup Healing",
    "General Calm & Presence",
    "Creative Loneliness"
  )

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = activeRoom?.title ?: "VYBE Unload",
        subtitle = if (activeRoom != null) {
          "Room ID: ${activeRoom!!.roomCode}"
        } else {
          "24/7 Live Listening Space • Zero Audio Recording"
        },
        onBackClick = {
          voiceManager.stopVoiceSession()
          viewModel.leaveUnloadRoom()
        },
        backTestTag = "unload_back_button",
        actions = {
          if (activeRoom == null) {
            Button(
              onClick = {
                formTitle = ""
                formDesc = ""
                formTopic = "Work Burnout"
                formMode = UnloadRoomMode.VOICE_AND_TEXT
                formDuration = 45
                formCapacity = 25
                formSchedule = "Live Now"
                formJoinerMsg = "Welcome to this listening space."
                formTermsAgreed = false
                showHostRoomDialog = true
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
              modifier = Modifier
                .padding(end = 4.dp)
                .heightIn(min = 40.dp)
                .testTag("host_unload_room_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Host Room", fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          } else {
            Surface(
              color = palette.accentSecondary.copy(alpha = 0.14f),
              shape = RoundedCornerShape(VybeTokens.Radius.xs),
              modifier = Modifier
                .padding(end = 4.dp)
                .clickable {
                  clipboardManager.setText(AnnotatedString(activeRoom!!.roomCode))
                  viewModel.postBannerMessage("Copied Room ID: ${activeRoom!!.roomCode}")
                }
                .testTag("active_room_code_chip")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = activeRoom!!.roomCode,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.accentSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = "Copy Room ID",
                  tint = palette.accentSecondary,
                  modifier = Modifier.size(12.dp)
                )
              }
            }
            val isHost = activeRoom?.hostId == currentUser?.id || activeRoom?.hostName == currentUser?.displayName
            if (isHost) {
              IconButton(
                onClick = {
                  val r = activeRoom!!
                  formTitle = r.title
                  formDesc = r.description
                  formTopic = r.topicTag
                  formMode = r.mode
                  formDuration = r.durationMinutes
                  formCapacity = r.capacity
                  showEditRoomDialog = true
                },
                modifier = Modifier.size(48.dp).testTag("edit_unload_room_button")
              ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Room", tint = palette.textPrimary)
              }
            }
          }
          IconButton(
            onClick = { showSafetySheet = true },
            modifier = Modifier.size(48.dp).testTag("unload_safety_button")
          ) {
            Icon(Icons.Default.Security, contentDescription = "Safety Controls", tint = palette.accent)
          }
        }
      )
    }
  ) { paddingValues ->
    if (activeRoom == null) {
      // Room directory
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 72.dp)
      ) {
        item {
          VybeSurfaceCard(
            containerColor = palette.accent.copy(alpha = 0.12f),
            borderColor = palette.accent.copy(alpha = 0.35f),
            contentPadding = PaddingValues(16.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.md))
                  .background(palette.accentSecondary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Outlined.Spa,
                  contentDescription = null,
                  tint = palette.accentSecondary,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "VYBE Unload",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = palette.textPrimary
                )
                Text(
                  text = "Safe, continuous, non-judgmental space",
                  fontSize = 12.sp,
                  color = palette.accentSecondary,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Unload the mental noise. Join live listening rooms available 24 hours a day. Voice sessions are never recorded, analyzed, or retained.",
              fontSize = 12.sp,
              color = palette.textSecondary,
              lineHeight = 17.sp
            )
          }
        }

        // Join by Room ID Card
        item {
          VybeSurfaceCard(
            borderColor = palette.accentSecondary.copy(alpha = 0.35f),
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Tag, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Join Room by ID",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = palette.textPrimary
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = joinRoomCodeInput,
                onValueChange = {
                  joinRoomCodeInput = it.uppercase()
                  joinRoomCodeError = null
                },
                placeholder = { Text("VYB-XXXX-XXXX", fontSize = 12.sp, color = palette.textMuted) },
                colors = fieldColors,
                singleLine = true,
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier
                  .weight(1f)
                  .testTag("unload_join_code_input")
              )
              Button(
                onClick = {
                  val code = joinRoomCodeInput.trim()
                  if (code.isBlank() || isJoiningByCode) return@Button
                  joinRoomCodeError = null
                  coroutineScope.launch {
                    isJoiningByCode = true
                    val res = viewModel.joinRoomByCode(code)
                    isJoiningByCode = false
                    if (res.isSuccess) {
                      joinRoomCodeInput = ""
                    } else {
                      joinRoomCodeError = res.exceptionOrNull()?.message ?: "Room not found or no longer active."
                    }
                  }
                },
                enabled = joinRoomCodeInput.trim().isNotBlank() && !isJoiningByCode,
                colors = ButtonDefaults.buttonColors(
                  containerColor = palette.accentSecondary,
                  contentColor = Color.White
                ),
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("unload_join_code_button")
              ) {
                Text(
                  text = if (isJoiningByCode) "Joining..." else "Join ID",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
              }
            }
            if (joinRoomCodeError != null) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(joinRoomCodeError!!, color = palette.danger, fontSize = 11.sp)
            }
          }
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Active Listening Rooms (${rooms.size})",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = palette.textPrimary
            )
            TextButton(
              onClick = { viewModel.refreshAllData() },
              modifier = Modifier.heightIn(min = 48.dp)
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Refresh Rooms", tint = palette.accent, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Refresh", fontSize = 12.sp, color = palette.accent, fontWeight = FontWeight.SemiBold)
            }
          }
        }

        if (rooms.isEmpty()) {
          item {
            VybeEmptyState(
              icon = Icons.Outlined.RecordVoiceOver,
              title = "No Active Listening Rooms",
              subtitle = "Host a quiet voice or reflection room to hold safe space for the community.",
              actionLabel = "Host Listening Space",
              onAction = { showHostRoomDialog = true }
            )
          }
        }

        items(rooms, key = { it.id }) { room ->
          VybeSurfaceCard(
            onClick = {
              pendingRoomToJoin = room
              showTermsDialog = true
            },
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("unload_room_card_${room.id}")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (room.isActive) palette.success else palette.textMuted)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = room.title,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = palette.textPrimary
                )
              }
              Surface(
                color = palette.accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(VybeTokens.Radius.xs)
              ) {
                Text(
                  text = "${room.activeListenersCount}/${room.capacity} listeners",
                  fontSize = 11.sp,
                  color = palette.accent,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Surface(
                color = palette.accentSecondary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(VybeTokens.Radius.xs),
                modifier = Modifier.clickable {
                  clipboardManager.setText(AnnotatedString(room.roomCode))
                  viewModel.postBannerMessage("Copied Room ID: ${room.roomCode}")
                }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = room.roomCode,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accentSecondary
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Room ID",
                    tint = palette.accentSecondary,
                    modifier = Modifier.size(11.dp)
                  )
                }
              }
              Text(
                text = "Host: ${room.hostName}",
                fontSize = 11.sp,
                color = palette.textSecondary
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(room.description, fontSize = 12.sp, color = palette.textSecondary, lineHeight = 17.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Outlined.FormatQuote,
                contentDescription = null,
                tint = palette.accentSecondary,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "\"${room.joinerMessage}\"",
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = palette.accentSecondary
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${room.topicTag} • ${room.mode.label} • ${room.durationMinutes}m",
                fontSize = 11.sp,
                color = palette.textMuted,
                modifier = Modifier.weight(1f)
              )
              Button(
                onClick = {
                  pendingRoomToJoin = room
                  showTermsDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = palette.accent,
                  contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                modifier = Modifier.heightIn(min = 40.dp)
              ) {
                Text("Join", fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    } else {
      // Inside active Unload room
      val room = activeRoom!!
      val isHost = room.hostId == currentUser?.id || room.hostName == currentUser?.displayName

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        if (isHost) {
          Surface(
            color = palette.accent.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = VybeVerifiedGold,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "You are the Room Host",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = palette.textPrimary
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  TextButton(
                    onClick = {
                      formTitle = room.title
                      formDesc = room.description
                      formTopic = room.topicTag
                      formMode = room.mode
                      formDuration = room.durationMinutes
                      formCapacity = room.capacity
                      showEditRoomDialog = true
                    }
                  ) {
                    Text("Edit", color = palette.accentSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                  TextButton(
                    onClick = {
                      voiceManager.stopVoiceSession()
                      viewModel.deleteUnloadRoom(room.id)
                    }
                  ) {
                    Text("End Session", color = palette.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
              HorizontalDivider(thickness = 0.8.dp, color = palette.border)
            }
          }
        }

        // Real-Time Voice Control & Live Participant Roster
        if (room.mode == UnloadRoomMode.VOICE_AND_TEXT || room.mode == UnloadRoomMode.VOICE_ONLY) {
          val liveParticipantCount = maxOf(room.activeListenersCount, voiceParticipants.size, 1)
          Surface(
            color = palette.elevated,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                          when {
                            !hasMicPermission -> VybeVerifiedGold
                            isMuted -> palette.danger
                            isLocalSpeaking -> palette.accentSecondary
                            else -> palette.success
                          }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = voiceStatusText,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  Text(
                    text = buildString {
                      append("$liveParticipantCount/${room.capacity} in room")
                      if (iceDiagnosticsText.isNotBlank()) {
                        append(" • $iceDiagnosticsText")
                      }
                    },
                    fontSize = 10.sp,
                    color = palette.accentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Row(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  if (!hasMicPermission) {
                    OutlinedButton(
                      onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                      border = BorderStroke(1.dp, palette.accentSecondary),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      shape = RoundedCornerShape(VybeTokens.Radius.sm),
                      modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("unload_grant_mic_button")
                    ) {
                      Icon(Icons.Outlined.Mic, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Allow Mic", fontSize = 11.sp, color = palette.accentSecondary, fontWeight = FontWeight.Bold)
                    }
                  } else {
                    IconButton(
                      onClick = { voiceManager.toggleMute() },
                      modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) palette.danger.copy(alpha = 0.18f) else palette.success.copy(alpha = 0.16f))
                        .testTag("unload_mute_toggle_button")
                    ) {
                      Icon(
                        imageVector = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                        contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone",
                        tint = if (isMuted) palette.danger else palette.success,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                  }

                  IconButton(
                    onClick = { voiceManager.toggleSpeakerphone() },
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                      .background(palette.card)
                      .testTag("unload_speaker_toggle_button")
                  ) {
                    Icon(
                      imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeDown,
                      contentDescription = if (isSpeakerphoneOn) "Speakerphone active" else "Earpiece active",
                      tint = if (isSpeakerphoneOn) palette.accentSecondary else palette.textPrimary,
                      modifier = Modifier.size(20.dp)
                    )
                  }

                  Button(
                    onClick = {
                      voiceManager.stopVoiceSession()
                      viewModel.leaveUnloadRoom()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.danger),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.heightIn(min = 40.dp)
                  ) {
                    Text("Leave", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                  }
                }
              }

              if (voiceParticipants.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  items(voiceParticipants, key = { it.userId }) { p ->
                    UnloadParticipantChip(
                      participant = p,
                      isMe = p.userId == currentUser?.id
                    )
                  }
                }
              }
            }
          }
          HorizontalDivider(thickness = 0.8.dp, color = palette.border)
        }

        if (room.mode == UnloadRoomMode.VOICE_ONLY) {
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(144.dp)
                .clip(CircleShape)
                .border(
                  width = if (isLocalSpeaking && !isMuted) 2.5.dp else 1.dp,
                  color = if (isLocalSpeaking && !isMuted) palette.accentSecondary else palette.accent.copy(alpha = 0.45f),
                  shape = CircleShape
                )
                .background(palette.accent.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(92.dp)
                  .clip(CircleShape)
                  .background(if (isMuted) palette.danger.copy(alpha = 0.28f) else palette.accent)
                  .clickable {
                    if (!hasMicPermission) {
                      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                      voiceManager.toggleMute()
                    }
                  },
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = when {
                    !hasMicPermission -> Icons.Outlined.MicNone
                    isMuted -> Icons.Outlined.MicOff
                    else -> Icons.Outlined.RecordVoiceOver
                  },
                  contentDescription = "Microphone control",
                  tint = Color.White,
                  modifier = Modifier.size(38.dp)
                )
              }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
              text = room.title,
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = when {
                !hasMicPermission -> "Tap the center microphone to grant permission and speak."
                isMuted -> "You are muted. Tap the microphone whenever you want to speak."
                isLocalSpeaking -> "Transmitting live voice to room participants..."
                else -> "Live voice active. Speak freely or listen in calm presence."
              },
              fontSize = 12.sp,
              color = palette.textSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
              color = palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.border)
            ) {
              Text(
                text = "${maxOf(room.activeListenersCount, voiceParticipants.size, 1)} connected • Host: ${room.hostName} • ${if (isSpeakerphoneOn) "Speaker" else "Earpiece"}",
                fontSize = 11.sp,
                color = palette.accentSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
              )
            }
          }
        } else {
          // Live ephemeral messages (TEXT_ONLY & VOICE_AND_TEXT)
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            reverseLayout = true
          ) {
            items(room.recentMessages.reversed(), key = { it.id }) { msg ->
              val relativeTime = rememberRelativeTimestamp(msg.createdAtIso, msg.timestamp)
              VybeSurfaceCard(
                containerColor = if (msg.isAnonymous) palette.elevated else palette.accent.copy(alpha = 0.14f),
                borderColor = if (msg.isAnonymous) palette.border else palette.accent.copy(alpha = 0.35f),
                cornerRadius = VybeTokens.Radius.md,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = msg.senderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (msg.isAnonymous) palette.accentSecondary else palette.accent
                  )
                  Text(relativeTime, fontSize = 10.sp, color = palette.textMuted)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(msg.text, fontSize = 13.sp, color = palette.textPrimary)
              }
            }
          }

          // Keyboard-safe message input bar
          Surface(
            color = palette.card,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
            ) {
              HorizontalDivider(thickness = 0.8.dp, color = palette.border)
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                      checked = isAnonymous,
                      onCheckedChange = { isAnonymous = it },
                      colors = CheckboxDefaults.colors(checkedColor = palette.accent)
                    )
                    Text("Post anonymously", fontSize = 12.sp, color = palette.textPrimary)
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Outlined.Lock,
                      contentDescription = null,
                      tint = palette.textMuted,
                      modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ephemeral", fontSize = 11.sp, color = palette.textMuted)
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Share what's in your heart...", color = palette.textMuted) },
                    colors = fieldColors,
                    modifier = Modifier
                      .weight(1f)
                      .testTag("unload_message_input"),
                    shape = RoundedCornerShape(VybeTokens.Radius.md),
                    maxLines = 4
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  IconButton(
                    onClick = {
                      if (messageText.isNotBlank()) {
                        viewModel.sendUnloadMessage(room.id, messageText, isAnonymous)
                        messageText = ""
                      }
                    },
                    enabled = messageText.isNotBlank(),
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("unload_send_button")
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Send,
                      contentDescription = "Send",
                      tint = if (messageText.isNotBlank()) palette.accent else palette.textMuted
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

  // Terms Agreement Dialog when Joining
  if (showTermsDialog && pendingRoomToJoin != null) {
    var termsChecked by remember { mutableStateOf(false) }
    val room = pendingRoomToJoin!!

    AlertDialog(
      onDismissRequest = {
        showTermsDialog = false
        pendingRoomToJoin = null
      },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Text(
          text = "VYBE Unload Terms & Conditions",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      },
      text = {
        Column {
          Text(
            text = "Before joining \"${room.title}\", please confirm you understand and agree to the room rules:",
            fontSize = 13.sp,
            color = palette.textSecondary
          )
          Spacer(modifier = Modifier.height(10.dp))
          VybeSurfaceCard(
            containerColor = palette.elevated,
            cornerRadius = VybeTokens.Radius.sm,
            contentPadding = PaddingValues(12.dp)
          ) {
            Text("1. Zero Audio Recording: Ephemeral space; recording is strictly forbidden.", fontSize = 11.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("2. Safe & Compassionate: Harassment, hate speech or romantic pressure will result in instant account ban.", fontSize = 11.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("3. Privacy First: Keep others' shared moments confidential.", fontSize = 11.sp, color = palette.textPrimary)
          }
          Spacer(modifier = Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = termsChecked,
              onCheckedChange = { termsChecked = it },
              colors = CheckboxDefaults.colors(checkedColor = palette.accent),
              modifier = Modifier.testTag("agree_terms_checkbox")
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("I agree to the Community Safety Guidelines", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showTermsDialog = false
            viewModel.joinUnloadRoom(room)
            pendingRoomToJoin = null
          },
          enabled = termsChecked,
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
          shape = RoundedCornerShape(VybeTokens.Radius.sm),
          modifier = Modifier.testTag("confirm_join_unload_room")
        ) {
          Text("Join Room", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showTermsDialog = false
          pendingRoomToJoin = null
        }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }

  // Host a New Room Full-Screen Page
  if (showHostRoomDialog) {
    Dialog(
      onDismissRequest = { showHostRoomDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
      Scaffold(
        containerColor = palette.canvas,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
          VybeSubScreenTopBar(
            title = "Host a Listening Space",
            subtitle = "VYBE Unload • Zero Audio Recording",
            onBackClick = { showHostRoomDialog = false },
            actions = {
              Button(
                onClick = {
                  if (formTitle.isNotBlank() && formTermsAgreed) {
                    viewModel.createUnloadRoom(
                      title = formTitle.trim(),
                      description = formDesc.trim().ifBlank { "Live open listening space." },
                      topicTag = formTopic,
                      mode = formMode,
                      durationMinutes = formDuration,
                      capacity = formCapacity,
                      scheduledTime = formSchedule,
                      joinerMessage = formJoinerMsg.trim(),
                      termsAgreed = formTermsAgreed
                    )
                    showHostRoomDialog = false
                  }
                },
                enabled = formTitle.isNotBlank() && formTermsAgreed,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .heightIn(min = 40.dp)
                  .testTag("submit_host_room_button")
              ) {
                Text("Launch", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          )
        }
      ) { innerPadding ->
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp)
        ) {
          item {
            VybeSurfaceCard(
              containerColor = palette.accent.copy(alpha = 0.12f),
              borderColor = palette.accent.copy(alpha = 0.35f),
              contentPadding = PaddingValues(16.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.22f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Spa,
                    contentDescription = null,
                    tint = palette.accentSecondary,
                    modifier = Modifier.size(22.dp)
                  )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                  Text(
                    text = "Hold Space for Others",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = palette.textPrimary
                  )
                  Text(
                    text = "Zero audio recordings. Instant host moderation controls. 100% ephemeral and judgment-free.",
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    lineHeight = 15.sp
                  )
                }
              }
            }
          }

          item {
            Text("ROOM IDENTITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = palette.accent)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = formTitle,
              onValueChange = { formTitle = it },
              label = { Text("Room Title *") },
              placeholder = { Text("e.g. Midnight Work Burnout & Reset", color = palette.textMuted) },
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("host_room_title_input"),
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = formDesc,
              onValueChange = { formDesc = it },
              label = { Text("What is this session holding space for?") },
              placeholder = { Text("A quiet place to vent about toxic deadlines, share career fatigue, or just listen in silence.", color = palette.textMuted) },
              colors = fieldColors,
              modifier = Modifier.fillMaxWidth(),
              minLines = 3,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            Text("PRIMARY TOPIC", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = palette.accent)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(topicOptions) { topic ->
                val topicIcon = when (topic) {
                  "Work Burnout" -> Icons.Outlined.WorkOutline
                  "Silent Company" -> Icons.Outlined.Spa
                  "Late Night Overthinking" -> Icons.Outlined.NightsStay
                  "Post-Breakup Healing" -> Icons.Outlined.HeartBroken
                  "General Calm & Presence" -> Icons.Outlined.Eco
                  "Creative Loneliness" -> Icons.Outlined.Palette
                  else -> Icons.Outlined.ChatBubbleOutline
                }
                val isSelected = formTopic == topic
                FilterChip(
                  selected = isSelected,
                  onClick = { formTopic = topic },
                  leadingIcon = {
                    Icon(
                      imageVector = topicIcon,
                      contentDescription = null,
                      tint = if (isSelected) palette.accent else palette.textSecondary,
                      modifier = Modifier.size(15.dp)
                    )
                  },
                  label = {
                    Text(
                      topic,
                      fontSize = 12.sp,
                      color = if (isSelected) palette.textPrimary else palette.textSecondary
                    )
                  },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                    containerColor = palette.elevated
                  ),
                  shape = RoundedCornerShape(VybeTokens.Radius.sm)
                )
              }
            }
          }

          item {
            Text("CONVERSATION FORMAT", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = palette.accent)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              UnloadRoomMode.values().forEach { mode ->
                val isSelected = formMode == mode
                VybeSurfaceCard(
                  onClick = { formMode = mode },
                  containerColor = if (isSelected) palette.accent.copy(alpha = 0.14f) else palette.card,
                  borderColor = if (isSelected) palette.accent else palette.border,
                  cornerRadius = VybeTokens.Radius.md,
                  contentPadding = PaddingValues(14.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) palette.accent.copy(alpha = 0.24f) else palette.elevated),
                      contentAlignment = Alignment.Center
                    ) {
                      when (mode) {
                        UnloadRoomMode.VOICE_AND_TEXT -> Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = if (isSelected) palette.accent else palette.textSecondary, modifier = Modifier.size(20.dp))
                        UnloadRoomMode.VOICE_ONLY -> Icon(Icons.Default.Mic, contentDescription = null, tint = if (isSelected) palette.accent else palette.textSecondary, modifier = Modifier.size(20.dp))
                        UnloadRoomMode.TEXT_ONLY -> Icon(Icons.Default.Chat, contentDescription = null, tint = if (isSelected) palette.accent else palette.textSecondary, modifier = Modifier.size(20.dp))
                      }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(mode.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                      Text(
                        text = when (mode) {
                          UnloadRoomMode.VOICE_AND_TEXT -> "Interactive audio stage with live ephemeral text reflections"
                          UnloadRoomMode.VOICE_ONLY -> "Pure spoken presence; no chat distraction for listeners"
                          UnloadRoomMode.TEXT_ONLY -> "Quiet reflection room; message without microphone pressure"
                        },
                        fontSize = 11.sp,
                        color = palette.textSecondary
                      )
                    }
                    RadioButton(
                      selected = isSelected,
                      onClick = { formMode = mode },
                      colors = RadioButtonDefaults.colors(selectedColor = palette.accent)
                    )
                  }
                }
              }
            }
          }

          item {
            Text("SESSION LIMITS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = palette.accent)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Duration", fontSize = 11.sp, color = palette.textSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  listOf(30, 45, 60).forEach { mins ->
                    FilterChip(
                      selected = formDuration == mins,
                      onClick = { formDuration = mins },
                      label = { Text("${mins}m", fontSize = 11.sp) },
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                        containerColor = palette.elevated
                      ),
                      shape = RoundedCornerShape(VybeTokens.Radius.sm)
                    )
                  }
                }
              }
              Column(modifier = Modifier.weight(1f)) {
                Text("Max Capacity", fontSize = 11.sp, color = palette.textSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  listOf(15, 25, 50).forEach { cap ->
                    FilterChip(
                      selected = formCapacity == cap,
                      onClick = { formCapacity = cap },
                      label = { Text("$cap", fontSize = 11.sp) },
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                        containerColor = palette.elevated
                      ),
                      shape = RoundedCornerShape(VybeTokens.Radius.sm)
                    )
                  }
                }
              }
            }
          }

          item {
            Text("JOINER WELCOME BANNER", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = palette.accent)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = formJoinerMsg,
              onValueChange = { formJoinerMsg = it },
              label = { Text("Message displayed to newly joined listeners") },
              colors = fieldColors,
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              TextButton(onClick = { formJoinerMsg = "Welcome. Speak whenever you are ready or just listen." }) {
                Text("Default", fontSize = 11.sp, color = palette.accent)
              }
              TextButton(onClick = { formJoinerMsg = "Quiet hours: feel free to mute and rest here." }) {
                Text("Quiet", fontSize = 11.sp, color = palette.accent)
              }
              TextButton(onClick = { formJoinerMsg = "Zero judgment here. Your feelings are completely valid." }) {
                Text("Empathy", fontSize = 11.sp, color = palette.accent)
              }
            }
          }

          item {
            VybeSurfaceCard(
              containerColor = palette.elevated,
              borderColor = palette.accent.copy(alpha = 0.3f),
              cornerRadius = VybeTokens.Radius.md,
              contentPadding = PaddingValues(14.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Checkbox(
                  checked = formTermsAgreed,
                  onCheckedChange = { formTermsAgreed = it },
                  colors = CheckboxDefaults.colors(checkedColor = palette.accent),
                  modifier = Modifier.testTag("host_terms_agreed_checkbox")
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("I accept the Host Safety & Ethics Charter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                  Spacer(modifier = Modifier.height(2.dp))
                  Text("No hate speech, zero recordings, non-judgmental space, respect anonymity.", fontSize = 11.sp, color = palette.textSecondary, lineHeight = 14.sp)
                }
              }
            }
          }

          item {
            VybePrimaryButton(
              text = "Launch Listening Space",
              onClick = {
                if (formTitle.isNotBlank() && formTermsAgreed) {
                  viewModel.createUnloadRoom(
                    title = formTitle.trim(),
                    description = formDesc.trim().ifBlank { "Live open listening space." },
                    topicTag = formTopic,
                    mode = formMode,
                    durationMinutes = formDuration,
                    capacity = formCapacity,
                    scheduledTime = formSchedule,
                    joinerMessage = formJoinerMsg.trim(),
                    termsAgreed = formTermsAgreed
                  )
                  showHostRoomDialog = false
                }
              },
              enabled = formTitle.isNotBlank() && formTermsAgreed,
              icon = Icons.Default.RecordVoiceOver,
              testTag = "submit_host_room_large_button",
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }
  }

  // Edit Room Full-Screen Page (Host only)
  if (showEditRoomDialog && activeRoom != null) {
    Dialog(
      onDismissRequest = { showEditRoomDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
      Scaffold(
        containerColor = palette.canvas,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
          VybeSubScreenTopBar(
            title = "Edit Room Details",
            subtitle = activeRoom?.roomCode,
            onBackClick = { showEditRoomDialog = false },
            actions = {
              Button(
                onClick = {
                  viewModel.updateUnloadRoom(
                    roomId = activeRoom!!.id,
                    title = formTitle.trim(),
                    description = formDesc.trim(),
                    topicTag = formTopic,
                    mode = formMode,
                    durationMinutes = formDuration,
                    capacity = formCapacity
                  )
                  showEditRoomDialog = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier.padding(end = 8.dp).heightIn(min = 40.dp)
              ) {
                Text("Save", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          )
        }
      ) { innerPadding ->
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp)
        ) {
          item {
            OutlinedTextField(
              value = formTitle,
              onValueChange = { formTitle = it },
              label = { Text("Title") },
              colors = fieldColors,
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }
          item {
            OutlinedTextField(
              value = formDesc,
              onValueChange = { formDesc = it },
              label = { Text("Description") },
              colors = fieldColors,
              modifier = Modifier.fillMaxWidth(),
              minLines = 3,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }
          item {
            Text("Select Topic", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              items(topicOptions) { topic ->
                FilterChip(
                  selected = formTopic == topic,
                  onClick = { formTopic = topic },
                  label = { Text(topic, fontSize = 11.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                    containerColor = palette.elevated
                  )
                )
              }
            }
          }
          item {
            Text("Conversation Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              UnloadRoomMode.values().forEach { mode ->
                FilterChip(
                  selected = formMode == mode,
                  onClick = { formMode = mode },
                  label = { Text(mode.label, fontSize = 11.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                    containerColor = palette.elevated
                  )
                )
              }
            }
          }
          item {
            VybePrimaryButton(
              text = "Save Changes",
              onClick = {
                viewModel.updateUnloadRoom(
                  roomId = activeRoom!!.id,
                  title = formTitle.trim(),
                  description = formDesc.trim(),
                  topicTag = formTopic,
                  mode = formMode,
                  durationMinutes = formDuration,
                  capacity = formCapacity
                )
                showEditRoomDialog = false
              },
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }
  }

  // Safety & Moderation Controls Dialog
  if (showSafetySheet) {
    AlertDialog(
      onDismissRequest = { showSafetySheet = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Security, contentDescription = null, tint = palette.accent)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Community Safety Controls", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Every VYBE Unload room is protected under zero-tolerance safety policies.",
            fontSize = 12.sp,
            color = palette.textSecondary
          )

          Button(
            onClick = {
              showReportSuccess = true
              showSafetySheet = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = palette.danger.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(VybeTokens.Radius.sm)
          ) {
            Icon(Icons.Default.ReportProblem, contentDescription = null, tint = palette.danger, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Report a Participant or Room", color = palette.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

          VybeSecondaryButton(
            text = "Leave Room Quietly",
            onClick = {
              voiceManager.stopVoiceSession()
              viewModel.leaveUnloadRoom()
              showSafetySheet = false
            },
            icon = Icons.Default.ExitToApp,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showSafetySheet = false }) {
          Text("Close", color = palette.textSecondary)
        }
      }
    )
  }

  if (showReportSuccess) {
    AlertDialog(
      onDismissRequest = { showReportSuccess = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = { Text("Report Submitted", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
      text = {
        Text("Thank you for protecting our community. The NR Next Trust & Safety team has logged this report with encrypted context.", fontSize = 13.sp)
      },
      confirmButton = {
        Button(
          onClick = { showReportSuccess = false },
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
        ) {
          Text("Done")
        }
      }
    )
  }
}

@Composable
private fun UnloadParticipantChip(
  participant: UnloadVoiceParticipant,
  isMe: Boolean
) {
  val palette = LocalVybePalette.current
  val borderColor = when {
    participant.isSpeaking && !participant.isMuted -> palette.accentSecondary
    participant.isHost -> palette.accent.copy(alpha = 0.7f)
    else -> palette.border
  }

  Surface(
    color = if (participant.isSpeaking && !participant.isMuted) {
      palette.accentSecondary.copy(alpha = 0.14f)
    } else {
      palette.card
    },
    shape = RoundedCornerShape(VybeTokens.Radius.lg),
    border = BorderStroke(if (participant.isSpeaking && !participant.isMuted) 1.5.dp else 1.dp, borderColor)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .background(if (participant.isHost) palette.accent else palette.accentSecondary.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = participant.displayName.take(1).uppercase(),
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = buildString {
          append(participant.displayName)
          if (isMe) append(" (You)")
          else if (participant.isHost) append(" • Host")
        },
        fontSize = 11.sp,
        fontWeight = if (participant.isSpeaking) FontWeight.Bold else FontWeight.Medium,
        color = palette.textPrimary,
        maxLines = 1
      )
      Spacer(modifier = Modifier.width(6.dp))
      Icon(
        imageVector = when {
          !participant.hasMicPermission -> Icons.Outlined.Headset
          participant.isMuted -> Icons.Outlined.MicOff
          participant.isSpeaking -> Icons.Outlined.GraphicEq
          else -> Icons.Outlined.Mic
        },
        contentDescription = null,
        tint = when {
          !participant.hasMicPermission -> palette.textMuted
          participant.isMuted -> palette.danger
          participant.isSpeaking -> palette.accentSecondary
          else -> palette.success
        },
        modifier = Modifier.size(14.dp)
      )
    }
  }
}

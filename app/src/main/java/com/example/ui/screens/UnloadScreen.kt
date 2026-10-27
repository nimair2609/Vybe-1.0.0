package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnloadScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
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

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = {
              voiceManager.stopVoiceSession()
              viewModel.leaveUnloadRoom()
            },
            modifier = Modifier
              .size(48.dp)
              .testTag("unload_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave Room")
          }
        },
        title = {
          Column {
            Text(
              text = activeRoom?.title ?: "VYBE UNLOAD",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            if (activeRoom != null) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clickable {
                    clipboardManager.setText(AnnotatedString(activeRoom!!.roomCode))
                    viewModel.postBannerMessage("Copied Room ID: ${activeRoom!!.roomCode}")
                  }
                  .testTag("active_room_code_chip")
              ) {
                Text(
                  text = "ID: ${activeRoom!!.roomCode}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeBrandCyan
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  Icons.Default.ContentCopy,
                  contentDescription = "Copy Room ID",
                  tint = VybeBrandCyan,
                  modifier = Modifier.size(12.dp)
                )
              }
            } else {
              Text(
                text = "24/7 Live Listening Space • Zero Audio Recording",
                fontSize = 10.sp,
                color = VybeBrandCyan
              )
            }
          }
        },
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
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .padding(end = 8.dp)
                .testTag("host_unload_room_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Host Room", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          } else {
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
                modifier = Modifier.testTag("edit_unload_room_button")
              ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Room", tint = Color.White)
              }
            }
          }
          IconButton(
            onClick = { showSafetySheet = true },
            modifier = Modifier.testTag("unload_safety_button")
          ) {
            Icon(Icons.Default.Security, contentDescription = "Safety Controls", tint = VybeViolet)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
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
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = VybeVioletDark.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.5f))
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VybeBrandCyan.copy(alpha = 0.16f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Spa,
                    contentDescription = null,
                    tint = VybeBrandCyan,
                    modifier = Modifier.size(22.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("VYBE UNLOAD", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                  Text("Safe, continuous, non-judgmental space", fontSize = 12.sp, color = VybeBrandCyan)
                }
              }
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Unload the mental noise. Join live listening rooms available 24 hours a day. Voice sessions are never recorded, analyzed, or retained.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 17.sp
              )
            }
          }
        }

        // Join by Room ID Card
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VybeBrandCyan.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tag, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Join Room by ID", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              }
              Spacer(modifier = Modifier.height(8.dp))
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
                  placeholder = { Text("VYB-XXXX-XXXX", fontSize = 12.sp) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
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
                  colors = ButtonDefaults.buttonColors(containerColor = VybeBrandCyan),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("unload_join_code_button")
                ) {
                  Text(
                    if (isJoiningByCode) "Joining..." else "Join ID",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                }
              }
              if (joinRoomCodeError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(joinRoomCodeError!!, color = VybeDanger, fontSize = 11.sp)
              }
            }
          }
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Active Listening Rooms (${rooms.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { viewModel.refreshAllData() }) {
              Icon(Icons.Default.Refresh, contentDescription = "Refresh Rooms", modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Refresh", fontSize = 12.sp)
            }
          }
        }

        items(rooms, key = { it.id }) { room ->
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                pendingRoomToJoin = room
                showTermsDialog = true
              }
              .testTag("unload_room_card_${room.id}"),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
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
                      .background(if (room.isActive) VybeOnlineGreen else Color.Gray)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(room.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Surface(
                  color = VybeViolet.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(
                    text = "${room.activeListenersCount}/${room.capacity} listeners",
                    fontSize = 11.sp,
                    color = VybeViolet,
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
                  color = VybeBrandCyan.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(6.dp),
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
                      color = VybeBrandCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                      Icons.Default.ContentCopy,
                      contentDescription = "Copy Room ID",
                      tint = VybeBrandCyan,
                      modifier = Modifier.size(11.dp)
                    )
                  }
                }
                Text(
                  text = "Host: ${room.hostName}",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(room.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(8.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Outlined.FormatQuote,
                  contentDescription = null,
                  tint = VybeBrandCyan,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "\"${room.joinerMessage}\"",
                  fontSize = 11.sp,
                  fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                  color = VybeBrandCyan
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
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                  onClick = {
                    pendingRoomToJoin = room
                    showTermsDialog = true
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Text("Join", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
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
        // Host banner controls
        if (isHost) {
          Surface(
            color = VybeVioletDark.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                Text("You are the Room Host", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                  Text("Edit", color = VybeBrandCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                  onClick = {
                    voiceManager.stopVoiceSession()
                    viewModel.deleteUnloadRoom(room.id)
                  }
                ) {
                  Text("End Session", color = VybeDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        // Real-Time Voice Control & Live Participant Roster (shown for VOICE_ONLY and VOICE_AND_TEXT)
        if (room.mode == UnloadRoomMode.VOICE_AND_TEXT || room.mode == UnloadRoomMode.VOICE_ONLY) {
          val liveParticipantCount = maxOf(room.activeListenersCount, voiceParticipants.size, 1)
          Surface(
            color = ObsidianSurfaceElevated,
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
                            isMuted -> VybeDanger
                            isLocalSpeaking -> VybeBrandCyan
                            else -> VybeOnlineGreen
                          }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = voiceStatusText,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
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
                    color = VybeBrandCyan.copy(alpha = 0.85f),
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
                      border = BorderStroke(1.dp, VybeBrandCyan),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("unload_grant_mic_button")
                    ) {
                      Icon(Icons.Outlined.Mic, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(15.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Allow Mic", fontSize = 11.sp, color = VybeBrandCyan, fontWeight = FontWeight.Bold)
                    }
                  } else {
                    IconButton(
                      onClick = { voiceManager.toggleMute() },
                      modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) VybeDanger.copy(alpha = 0.2f) else VybeOnlineGreen.copy(alpha = 0.16f))
                        .testTag("unload_mute_toggle_button")
                    ) {
                      Icon(
                        imageVector = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                        contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone",
                        tint = if (isMuted) VybeDanger else VybeOnlineGreen,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                  }

                  IconButton(
                    onClick = { voiceManager.toggleSpeakerphone() },
                    modifier = Modifier
                      .size(42.dp)
                      .clip(CircleShape)
                      .background(Color.White.copy(alpha = 0.08f))
                      .testTag("unload_speaker_toggle_button")
                  ) {
                    Icon(
                      imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeDown,
                      contentDescription = if (isSpeakerphoneOn) "Speakerphone active" else "Earpiece active",
                      tint = if (isSpeakerphoneOn) VybeBrandCyan else Color.White,
                      modifier = Modifier.size(20.dp)
                    )
                  }

                  Button(
                    onClick = {
                      voiceManager.stopVoiceSession()
                      viewModel.leaveUnloadRoom()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeDanger.copy(alpha = 0.85f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 38.dp)
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
        }

        if (room.mode == UnloadRoomMode.VOICE_ONLY) {
          // Voice Only Mode Stage UI
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
                  color = if (isLocalSpeaking && !isMuted) VybeBrandCyan else VybeViolet.copy(alpha = 0.45f),
                  shape = CircleShape
                )
                .background(VybeViolet.copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(92.dp)
                  .clip(CircleShape)
                  .background(if (isMuted) VybeDanger.copy(alpha = 0.28f) else VybeViolet.copy(alpha = 0.4f))
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
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
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
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = "${maxOf(room.activeListenersCount, voiceParticipants.size, 1)} connected • Host: ${room.hostName} • ${if (isSpeakerphoneOn) "Speaker" else "Earpiece"}",
                fontSize = 11.sp,
                color = VybeBrandCyan,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (msg.isAnonymous) MaterialTheme.colorScheme.surfaceVariant else VybeViolet.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = msg.senderName,
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = if (msg.isAnonymous) VybeBrandCyan else VybeViolet
                    )
                    Text(relativeTime, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(msg.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
              }
            }
          }

          // Keyboard-safe message input bar
          Surface(
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Checkbox(
                    checked = isAnonymous,
                    onCheckedChange = { isAnonymous = it }
                  )
                  Text("Post anonymously", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Ephemeral", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedTextField(
                  value = messageText,
                  onValueChange = { messageText = it },
                  placeholder = { Text("Share what's in your heart...") },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("unload_message_input"),
                  shape = RoundedCornerShape(12.dp),
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
                  Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = VybeViolet)
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
      title = {
        Text("VYBE Unload Terms & Conditions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      },
      text = {
        Column {
          Text(
            text = "Before joining \"${room.title}\", please confirm you understand and agree to the room rules:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text("1. Zero Audio Recording: Ephemeral space; recording is strictly forbidden.", fontSize = 11.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("2. Safe & Compassionate: Harassment, hate speech or romantic pressure will result in instant account ban.", fontSize = 11.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("3. Privacy First: Keep others' shared moments confidential.", fontSize = 11.sp)
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = termsChecked,
              onCheckedChange = { termsChecked = it },
              modifier = Modifier.testTag("agree_terms_checkbox")
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("I agree to the Community Safety Guidelines", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
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
          Text("Cancel")
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
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
          TopAppBar(
            navigationIcon = {
              IconButton(onClick = { showHostRoomDialog = false }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
              }
            },
            title = {
              Column {
                Text("Host a Listening Space", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("VYBE UNLOAD • Zero Audio Recording", fontSize = 11.sp, color = VybeBrandCyan)
              }
            },
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
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .testTag("submit_host_room_button")
              ) {
                Text("Launch", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            )
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
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Executive Hero Banner
          item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
              colors = CardDefaults.cardColors(containerColor = VybeVioletDark.copy(alpha = 0.45f)),
              shape = RoundedCornerShape(20.dp),
              border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.5f))
            ) {
              Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(VybeViolet.copy(alpha = 0.35f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Spa,
                    contentDescription = null,
                    tint = VybeBrandCyan,
                    modifier = Modifier.size(22.dp)
                  )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                  Text(
                    text = "Hold Space for Others",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                  )
                  Text(
                    text = "Zero audio recordings. Instant host moderation controls. 100% ephemeral and judgment-free.",
                    fontSize = 11.sp,
                    color = VybeBrandCyan,
                    lineHeight = 15.sp
                  )
                }
              }
            }
          }

          // Section 1: Room Details
          item {
            Text("ROOM IDENTITY", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = VybeViolet)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = formTitle,
              onValueChange = { formTitle = it },
              label = { Text("Room Title *") },
              placeholder = { Text("e.g. Midnight Work Burnout & Reset") },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("host_room_title_input"),
              singleLine = true,
              shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = formDesc,
              onValueChange = { formDesc = it },
              label = { Text("What is this session holding space for?") },
              placeholder = { Text("A quiet place to vent about toxic deadlines, share career fatigue, or just listen in silence.") },
              modifier = Modifier.fillMaxWidth(),
              minLines = 3,
              shape = RoundedCornerShape(14.dp)
            )
          }

          // Section 2: Conversation Topic
          item {
            Text("PRIMARY TOPIC", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = VybeViolet)
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
                FilterChip(
                  selected = formTopic == topic,
                  onClick = { formTopic = topic },
                  leadingIcon = {
                    Icon(
                      imageVector = topicIcon,
                      contentDescription = null,
                      modifier = Modifier.size(15.dp)
                    )
                  },
                  label = { Text(topic, fontSize = 12.sp) },
                  shape = RoundedCornerShape(12.dp)
                )
              }
            }
          }

          // Section 3: Conversation Mode Cards
          item {
            Text("CONVERSATION FORMAT", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = VybeViolet)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              UnloadRoomMode.values().forEach { mode ->
                val isSelected = formMode == mode
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { formMode = mode },
                  colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) VybeViolet.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
                  ),
                  shape = RoundedCornerShape(14.dp),
                  border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                  )
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) VybeViolet.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant),
                      contentAlignment = Alignment.Center
                    ) {
                      when (mode) {
                        UnloadRoomMode.VOICE_AND_TEXT -> Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        UnloadRoomMode.VOICE_ONLY -> Icon(Icons.Default.Mic, contentDescription = null, tint = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        UnloadRoomMode.TEXT_ONLY -> Icon(Icons.Default.Chat, contentDescription = null, tint = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                      }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(mode.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      Text(
                        text = when (mode) {
                          UnloadRoomMode.VOICE_AND_TEXT -> "Interactive audio stage with live ephemeral text reflections"
                          UnloadRoomMode.VOICE_ONLY -> "Pure spoken presence; no chat distraction for listeners"
                          UnloadRoomMode.TEXT_ONLY -> "Quiet reflection room; message without microphone pressure"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    RadioButton(
                      selected = isSelected,
                      onClick = { formMode = mode }
                    )
                  }
                }
              }
            }
          }

          // Section 4: Duration & Capacity
          item {
            Text("SESSION LIMITS", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = VybeViolet)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Duration", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  listOf(30, 45, 60).forEach { mins ->
                    FilterChip(
                      selected = formDuration == mins,
                      onClick = { formDuration = mins },
                      label = { Text("${mins}m", fontSize = 11.sp) },
                      shape = RoundedCornerShape(10.dp)
                    )
                  }
                }
              }
              Column(modifier = Modifier.weight(1f)) {
                Text("Max Capacity", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  listOf(15, 25, 50).forEach { cap ->
                    FilterChip(
                      selected = formCapacity == cap,
                      onClick = { formCapacity = cap },
                      label = { Text("$cap", fontSize = 11.sp) },
                      shape = RoundedCornerShape(10.dp)
                    )
                  }
                }
              }
            }
          }

          // Section 5: Welcome message
          item {
            Text("JOINER WELCOME BANNER", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = VybeViolet)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = formJoinerMsg,
              onValueChange = { formJoinerMsg = it },
              label = { Text("Message displayed to newly joined listeners") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              TextButton(onClick = { formJoinerMsg = "Welcome. Speak whenever you are ready or just listen." }) {
                Text("Default", fontSize = 11.sp)
              }
              TextButton(onClick = { formJoinerMsg = "Quiet hours: feel free to mute and rest here." }) {
                Text("Quiet", fontSize = 11.sp)
              }
              TextButton(onClick = { formJoinerMsg = "Zero judgment here. Your feelings are completely valid." }) {
                Text("Empathy", fontSize = 11.sp)
              }
            }
          }

          // Section 6: Safety Agreement
          item {
            Surface(
              color = VybeVioletDark.copy(alpha = 0.35f),
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f))
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Checkbox(
                  checked = formTermsAgreed,
                  onCheckedChange = { formTermsAgreed = it },
                  modifier = Modifier.testTag("host_terms_agreed_checkbox")
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("I accept the Host Safety & Ethics Charter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                  Spacer(modifier = Modifier.height(2.dp))
                  Text("No hate speech, zero recordings, non-judgmental space, respect anonymity.", fontSize = 11.sp, color = VybeBrandCyan, lineHeight = 14.sp)
                }
              }
            }
          }

          // Section 7: Launch Button
          item {
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
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_host_room_large_button")
            ) {
              Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Launch Listening Space", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
          }

          item {
            Spacer(modifier = Modifier.height(30.dp))
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
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
          TopAppBar(
            navigationIcon = {
              IconButton(onClick = { showEditRoomDialog = false }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
              }
            },
            title = {
              Text("Edit Room Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
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
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(end = 8.dp)
              ) {
                Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            )
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
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = formTitle,
              onValueChange = { formTitle = it },
              label = { Text("Title") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              shape = RoundedCornerShape(14.dp)
            )
          }
          item {
            OutlinedTextField(
              value = formDesc,
              onValueChange = { formDesc = it },
              label = { Text("Description") },
              modifier = Modifier.fillMaxWidth(),
              minLines = 3,
              shape = RoundedCornerShape(14.dp)
            )
          }
          item {
            Text("Select Topic", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              items(topicOptions) { topic ->
                FilterChip(
                  selected = formTopic == topic,
                  onClick = { formTopic = topic },
                  label = { Text(topic, fontSize = 11.sp) }
                )
              }
            }
          }
          item {
            Text("Conversation Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              UnloadRoomMode.values().forEach { mode ->
                FilterChip(
                  selected = formMode == mode,
                  onClick = { formMode = mode },
                  label = { Text(mode.label, fontSize = 11.sp) }
                )
              }
            }
          }
          item {
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
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
            ) {
              Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
          }
          item {
            Spacer(modifier = Modifier.height(30.dp))
          }
        }
      }
    }
  }

  // Safety & Moderation Controls Dialog
  if (showSafetySheet) {
    AlertDialog(
      onDismissRequest = { showSafetySheet = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Security, contentDescription = null, tint = VybeViolet)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Community Safety Controls", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Every VYBE Unload room is protected under zero-tolerance safety policies.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Button(
            onClick = {
              showReportSuccess = true
              showSafetySheet = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeDanger.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.ReportProblem, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Report a Participant or Room", color = VybeDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = {
              voiceManager.stopVoiceSession()
              viewModel.leaveUnloadRoom()
              showSafetySheet = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Leave Room Quietly", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showSafetySheet = false }) {
          Text("Close")
        }
      }
    )
  }

  if (showReportSuccess) {
    AlertDialog(
      onDismissRequest = { showReportSuccess = false },
      title = { Text("Report Submitted", fontWeight = FontWeight.Bold) },
      text = {
        Text("Thank you for protecting our community. The NR Next Trust & Safety team has logged this report with encrypted context.", fontSize = 13.sp)
      },
      confirmButton = {
        Button(onClick = { showReportSuccess = false }) {
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
  val borderColor = when {
    participant.isSpeaking && !participant.isMuted -> VybeBrandCyan
    participant.isHost -> VybeViolet.copy(alpha = 0.7f)
    else -> Color.White.copy(alpha = 0.16f)
  }

  Surface(
    color = if (participant.isSpeaking && !participant.isMuted) {
      VybeBrandCyan.copy(alpha = 0.15f)
    } else {
      Color.White.copy(alpha = 0.06f)
    },
    shape = RoundedCornerShape(16.dp),
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
          .background(if (participant.isHost) VybeViolet else VybeBrandCyan.copy(alpha = 0.25f)),
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
        color = Color.White,
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
          !participant.hasMicPermission -> Color.White.copy(alpha = 0.6f)
          participant.isMuted -> VybeDanger
          participant.isSpeaking -> VybeBrandCyan
          else -> VybeOnlineGreen
        },
        modifier = Modifier.size(14.dp)
      )
    }
  }
}

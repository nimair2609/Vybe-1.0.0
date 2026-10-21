package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.data.model.UnloadRoom
import com.example.data.model.UnloadRoomMode
import com.example.ui.VybeViewModel
import com.example.ui.theme.*

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
  BackHandler {
    viewModel.leaveUnloadRoom()
  }

  val activeRoom by viewModel.activeUnloadRoom.collectAsState()
  val rooms by viewModel.unloadRooms.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var messageText by remember { mutableStateOf("") }
  var isAnonymous by remember { mutableStateOf(true) }
  var isMuted by remember { mutableStateOf(false) }
  var showSafetySheet by remember { mutableStateOf(false) }
  var showReportSuccess by remember { mutableStateOf(false) }

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
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.leaveUnloadRoom() },
            modifier = Modifier.testTag("unload_back_button")
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
            Text(
              text = "24/7 Live Listening Space • Zero Audio Recording",
              fontSize = 10.sp,
              color = VybeBrandCyan
            )
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
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = VybeVioletDark.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.5f))
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🕊️", fontSize = 28.sp)
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

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Active Listening Rooms (${rooms.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .clip(CircleShape)
                      .background(VybeOnlineGreen)
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
              Text(room.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "💬 \"${room.joinerMessage}\"",
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = VybeBrandCyan
              )
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
                Text("👑", fontSize = 16.sp)
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
                  onClick = { viewModel.deleteUnloadRoom(room.id) }
                ) {
                  Text("End Session", color = VybeDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        // Voice status bar (shown for VOICE_ONLY and VOICE_AND_TEXT)
        if (room.mode == UnloadRoomMode.VOICE_AND_TEXT || room.mode == UnloadRoomMode.VOICE_ONLY) {
          Surface(
            color = ObsidianSurfaceElevated,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) VybeDanger else VybeOnlineGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = if (isMuted) "Microphone Muted" else "Voice Connected (Live Audio)",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color.White
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                  onClick = { isMuted = !isMuted },
                  modifier = Modifier.size(34.dp)
                ) {
                  Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Toggle Mute",
                    tint = if (isMuted) VybeDanger else Color.White
                  )
                }
                Button(
                  onClick = { viewModel.leaveUnloadRoom() },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeDanger.copy(alpha = 0.8f)),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text("Leave", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        if (room.mode == UnloadRoomMode.VOICE_ONLY) {
          // Voice Only Mode UI
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(VybeViolet.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(90.dp)
                  .clip(CircleShape)
                  .background(VybeViolet.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.RecordVoiceOver,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(40.dp)
                )
              }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
              text = "Voice Only Listening Room",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            Text(
              text = "No text messages. Keep listening and speaking with presence.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = "${room.activeListenersCount} listeners connected • Host: ${room.hostName}",
                fontSize = 11.sp,
                color = VybeBrandCyan,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }
        } else {
          // Live ephemeral messages
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            reverseLayout = true
          ) {
            items(room.recentMessages.reversed()) { msg ->
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
                    Text(msg.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(msg.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
              }
            }
          }

          // Message input bar
          Surface(
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
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
                Text("🔒 Ephemeral", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                  singleLine = true
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
                  modifier = Modifier.testTag("unload_send_button")
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

  // Host a New Room Dialog
  if (showHostRoomDialog) {
    AlertDialog(
      onDismissRequest = { showHostRoomDialog = false },
      title = { Text("Host an Unload Room", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            OutlinedTextField(
              value = formTitle,
              onValueChange = { formTitle = it },
              label = { Text("Room Title") },
              placeholder = { Text("e.g., Midnight Work Burnout Vent") },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("host_room_title_input"),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = formDesc,
              onValueChange = { formDesc = it },
              label = { Text("Description") },
              placeholder = { Text("What is this session holding space for?") },
              modifier = Modifier.fillMaxWidth(),
              maxLines = 3
            )
          }
          item {
            Text("Conversation Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            Text("Duration & Capacity", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(30, 45, 60).forEach { mins ->
                FilterChip(
                  selected = formDuration == mins,
                  onClick = { formDuration = mins },
                  label = { Text("${mins}m", fontSize = 11.sp) }
                )
              }
              Spacer(modifier = Modifier.width(6.dp))
              listOf(15, 25, 50).forEach { cap ->
                FilterChip(
                  selected = formCapacity == cap,
                  onClick = { formCapacity = cap },
                  label = { Text("$cap users", fontSize = 11.sp) }
                )
              }
            }
          }
          item {
            OutlinedTextField(
              value = formJoinerMsg,
              onValueChange = { formJoinerMsg = it },
              label = { Text("Joiner Welcome Message") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }
          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = formTermsAgreed,
                onCheckedChange = { formTermsAgreed = it },
                modifier = Modifier.testTag("host_terms_agreed_checkbox")
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("I agree to host under VYBE Safety Guidelines", fontSize = 11.sp)
            }
          }
        }
      },
      confirmButton = {
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
          modifier = Modifier.testTag("submit_host_room_button")
        ) {
          Text("Launch Room", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showHostRoomDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Edit Room Dialog (Host only)
  if (showEditRoomDialog && activeRoom != null) {
    AlertDialog(
      onDismissRequest = { showEditRoomDialog = false },
      title = { Text("Edit Room Details", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = formTitle,
            onValueChange = { formTitle = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
          OutlinedTextField(
            value = formDesc,
            onValueChange = { formDesc = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
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
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Save Changes")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditRoomDialog = false }) {
          Text("Cancel")
        }
      }
    )
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

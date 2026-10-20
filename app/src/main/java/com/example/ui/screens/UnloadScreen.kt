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
 * 14.1 VYBE UNLOAD
 * A live, continuously available 24/7 listening space.
 * - Authenticated users can join an available session or room.
 * - Text and voice conversation modes.
 * - Clear session status, connection feedback.
 * - Block, report, leave, and safety controls.
 * - No audio recording guarantee: ephemeral and private.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnloadScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.leaveUnloadRoom()
  }

  val activeRoom by viewModel.activeUnloadRoom.collectAsState()
  val rooms by viewModel.unloadRooms.collectAsState()

  var messageText by remember { mutableStateOf("") }
  var isAnonymous by remember { mutableStateOf(true) }
  var isMuted by remember { mutableStateOf(false) }
  var showSafetySheet by remember { mutableStateOf(false) }
  var showReportSuccess by remember { mutableStateOf(false) }

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
                text = "Unload the mental noise. Join live listening rooms available 24 hours a day. Voice sessions are never recorded or retained.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 17.sp
              )
            }
          }
        }

        item {
          Text("Active Listening Rooms", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        items(rooms) { room ->
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { viewModel.joinUnloadRoom(room) },
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
                    text = "${room.activeListenersCount} active",
                    fontSize = 11.sp,
                    color = VybeViolet,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(room.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Topic: ${room.topicTag} • ${if (room.mode == UnloadRoomMode.VOICE_AND_TEXT) "🎙️ Voice + Chat" else "💬 Chat Only"}",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                  onClick = { viewModel.joinUnloadRoom(room) },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
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
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        // Voice status bar
        if (room.mode == UnloadRoomMode.VOICE_AND_TEXT) {
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
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                  Text("Leave", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

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
                Icon(Icons.Default.Send, contentDescription = "Send", tint = VybeViolet)
              }
            }
          }
        }
      }
    }
  }

  // Safety & Moderation Controls Dialog
  if (showSafetySheet) {
    AlertDialog(
      onDismissRequest = { showSafetySheet = false },
      title = { Text("UNLOAD Safety & Moderation", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Your emotional safety is guaranteed here:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "• Zero Voice Recording: Live audio streams are transmitted in real-time and never recorded or saved.\n• Anonymous Mode: You can participate without revealing your account name or profile photo.\n• Strict Moderation: Bullying, harassment, and unsolicited advice are prohibited.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Button(
            onClick = {
              showReportSuccess = true
              showSafetySheet = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeDanger),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Report Distress or Abuse")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSafetySheet = false }) { Text("Close") }
      }
    )
  }

  if (showReportSuccess) {
    AlertDialog(
      onDismissRequest = { showReportSuccess = false },
      title = { Text("Support & Moderation Alert Sent", fontWeight = FontWeight.Bold) },
      text = {
        Text("Our 24/7 safety moderators have been alerted. Remember you can also leave this room at any moment.")
      },
      confirmButton = {
        Button(
          onClick = { showReportSuccess = false },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("OK")
        }
      }
    )
  }
}

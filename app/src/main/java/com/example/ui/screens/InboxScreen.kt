package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: VybeViewModel) {
  val conversations by viewModel.conversations.collectAsState()
  var currentTab by remember { mutableStateOf("Chats") }

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
            onClick = { /* New conversation */ },
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
      // Sub tabs: Chats vs Message Requests
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          listOf("Chats", "Message Requests").forEach { tab ->
            val isSelected = currentTab == tab
            Surface(
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.clickable { currentTab = tab }
            ) {
              Text(
                text = tab,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
              )
            }
          }
        }
      }

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
      } else {
        items(conversations, key = { it.id }) { conv ->
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
              VybeAvatar(user = conv.peerUser, size = 48.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = conv.peerUser.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  VerificationBadge(user = conv.peerUser)
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
                Row {
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
}

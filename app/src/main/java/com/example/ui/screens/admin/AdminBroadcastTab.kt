package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Campaign
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
import com.example.ui.theme.*

@Composable
fun AdminBroadcastTab(
  onSendBroadcast: (String, String, String, String?, (Boolean, String) -> Unit) -> Unit,
  onShowSnackbar: (String) -> Unit
) {
  var pushTitle by remember { mutableStateOf("⚡ VYBE Special Announcement") }
  var pushBody by remember { mutableStateOf("Explore our new Community Zones and connect with verified creators!") }
  var pushMediaUrl by remember { mutableStateOf("") }
  var pushDestination by remember { mutableStateOf("COMMUNITY_ZONE") }
  var selectedAudience by remember { mutableStateOf("ALL_USERS") }
  var isDispatching by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_broadcast_tab"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Info Callout
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(VybeBrandBlue.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = VybeBrandBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text("Dual-Engine Push Broadcast Engine", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Text("Dispatches over Supabase Realtime (<100ms in-app) and Firebase Cloud Messaging HTTP v1 to wake backgrounded phones.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Composer Form
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Notification Composer", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        OutlinedTextField(
          value = pushTitle,
          onValueChange = { pushTitle = it },
          label = { Text("Notification Title") },
          modifier = Modifier.fillMaxWidth().testTag("admin_broadcast_title_input")
        )

        OutlinedTextField(
          value = pushBody,
          onValueChange = { pushBody = it },
          label = { Text("Message Body") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("admin_broadcast_body_input")
        )

        OutlinedTextField(
          value = pushMediaUrl,
          onValueChange = { pushMediaUrl = it },
          label = { Text("Optional Banner Media URL (JPEG/PNG)") },
          placeholder = { Text("https://...") },
          modifier = Modifier.fillMaxWidth().testTag("admin_broadcast_media_input")
        )

        // Destination Screen Selector
        Text("Tap Destination Screen:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("FEED", "NOTIFICATIONS", "COMMUNITY_ZONE", "REELS").forEach { dest ->
            FilterChip(
              selected = pushDestination == dest,
              onClick = { pushDestination = dest },
              label = { Text(dest.replace("_", " "), fontSize = 10.sp) },
              modifier = Modifier.weight(1f)
            )
          }
        }

        // Target Audience Selector
        Text("Target Audience Segment:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("ALL_USERS", "CREATORS_ONLY", "VERIFIED_SHOPS", "ACTIVE_PAST_7_DAYS").forEach { aud ->
            FilterChip(
              selected = selectedAudience == aud,
              onClick = { selectedAudience = aud },
              label = { Text(aud.replace("_", " "), fontSize = 9.sp) },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }

    // Real-Time Android Notification Tray Mockup Preview
    Text("Live Android Notification Shade Preview", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
      modifier = Modifier.fillMaxWidth().testTag("admin_broadcast_preview_card")
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
                .size(20.dp)
                .clip(CircleShape)
                .background(VybeBrandBlue),
              contentAlignment = Alignment.Center
            ) {
              Text("V", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("VYBE Official", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(" • now", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = pushTitle.ifBlank { "Notification Title" },
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = pushBody.ifBlank { "Message body preview appears here..." },
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 16.sp
            )
          }

          if (pushMediaUrl.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
              model = pushMediaUrl,
              contentDescription = "Media Preview",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
            )
          }
        }
      }
    }

    // Send Button
    Button(
      onClick = {
        if (pushTitle.isBlank() || pushBody.isBlank()) {
          onShowSnackbar("Please fill in both title and message.")
          return@Button
        }
        isDispatching = true
        onSendBroadcast(
          pushTitle.trim(),
          pushBody.trim(),
          pushDestination,
          pushMediaUrl.trim().takeIf { it.isNotBlank() }
        ) { success, msg ->
          isDispatching = false
          onShowSnackbar(if (success) "✓ $msg" else "Broadcast status: $msg")
        }
      },
      colors = ButtonDefaults.buttonColors(containerColor = VybeBrandBlue),
      shape = RoundedCornerShape(12.dp),
      enabled = !isDispatching,
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("admin_dispatch_broadcast_button")
    ) {
      if (isDispatching) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Dispatching Real-time Broadcast...", fontSize = 13.sp)
      } else {
        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Send Real-time Push Broadcast", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      }
    }
  }
}

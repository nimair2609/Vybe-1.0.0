package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionRequestStatus
import com.example.data.model.CrossedPathEncounter
import com.example.data.model.CrossedPathType
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeGradientButton
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossedPathsScreen(viewModel: VybeViewModel) {
  val encounters by viewModel.crossedPaths.collectAsState()
  var selectedTab by remember { mutableStateOf(CrossedPathType.MISSED_CONNECTION) }

  var showCreateDialog by remember { mutableStateOf(false) }
  var newLocation by remember { mutableStateOf("") }
  var newVenue by remember { mutableStateOf("") }
  var newDescription by remember { mutableStateOf("") }

  val filteredEncounters = remember(encounters, selectedTab) {
    encounters.filter { it.type == selectedTab }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Crossed Paths",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Consent-first real-world discovery",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          Button(
            onClick = { showCreateDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(end = 12.dp).testTag("noticed_someone_button")
          ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("I Noticed Someone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
      // Consent & Anti-Stalking Safety Banner
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(18.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = VybeCyan,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Consent-First Architecture",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Zero continuous GPS tracking. No exact coordinates. Encounters only share broad neighborhoods. Direct chat opens ONLY after the recipient explicitly accepts your handshake request.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 15.sp
            )
          }
        }
      }

      // Three Sub-tabs
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          CrossedPathType.values().forEach { type ->
            val label = when (type) {
              CrossedPathType.MISSED_CONNECTION -> "Missed Connections"
              CrossedPathType.VENUE_EVENT -> "Venues & Events"
              CrossedPathType.BROAD_NEARBY -> "Nearby Opt-In"
            }
            val isSelected = selectedTab == type
            Surface(
              color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .weight(1f)
                .clickable { selectedTab = type }
            ) {
              Box(
                modifier = Modifier.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 11.sp,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1
                )
              }
            }
          }
        }
      }

      // Encounter items
      items(filteredEncounters, key = { it.id }) { encounter ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("encounter_card_${encounter.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              VybeAvatar(user = encounter.poster, size = 40.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = encounter.title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "📍 ${encounter.broadLocation} • ${encounter.encounterDate}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Surface(
                color = VybeViolet.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = "⏳ ${encounter.expiresHoursRemaining}h left",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeViolet,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = encounter.description,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                encounter.tags.forEach { tag ->
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = "#$tag",
                      fontSize = 10.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
              }

              Button(
                onClick = { viewModel.sendCrossedPathRequest(encounter.id) },
                enabled = !encounter.hasSentRequest,
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (encounter.hasSentRequest) MaterialTheme.colorScheme.surfaceVariant else VybeViolet
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("handshake_button_${encounter.id}")
              ) {
                Icon(
                  imageVector = if (encounter.hasSentRequest) Icons.Default.Check else Icons.Default.WavingHand,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (encounter.hasSentRequest) "Handshake Sent" else "That Was Me",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
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

  // Create Missed Connection Dialog
  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("I Noticed Someone", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Post a respectful, non-identifying description. Expires in 72h.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = newLocation,
            onValueChange = { newLocation = it },
            label = { Text("Broad Location (e.g. Bandra West, Mumbai)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = newVenue,
            onValueChange = { newVenue = it },
            label = { Text("Venue / Event (e.g. Blue Tokai Coffee)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = newDescription,
            onValueChange = { newDescription = it },
            label = { Text("Respectful Description") },
            placeholder = { Text("e.g. You wore a green denim jacket...") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newDescription.isNotBlank()) {
              showCreateDialog = false
              newDescription = ""
              newVenue = ""
              newLocation = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta)
        ) {
          Text("Post Encounter")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
      }
    )
  }
}

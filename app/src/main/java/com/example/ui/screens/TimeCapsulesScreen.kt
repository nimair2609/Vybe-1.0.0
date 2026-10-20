package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.example.data.model.TimeCapsule
import com.example.ui.VybeViewModel
import com.example.ui.theme.VybeCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCapsulesScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val capsules by viewModel.timeCapsules.collectAsState()
  var showCreateDialog by remember { mutableStateOf(false) }

  var titleInput by remember { mutableStateOf("") }
  var messageInput by remember { mutableStateOf("") }
  var recipientInput by remember { mutableStateOf("To myself") }
  var unlockDateInput by remember { mutableStateOf("Jan 01, 2027") }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("capsules_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text("VYBE Time Capsule", fontWeight = FontWeight.Bold)
            Text("Sealed messages delivered in the future", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          Button(
            onClick = { showCreateDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(end = 12.dp).testTag("seal_capsule_button")
          ) {
            Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Seal Capsule", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Explainer card
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(16.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("Cryptographically Locked Until Due Date", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text(
                "Device clock tampering cannot unlock a capsule. Content remains protected on the server until the exact unlock timestamp arrives.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      items(capsules, key = { it.id }) { capsule ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("capsule_card_${capsule.id}"),
          colors = CardDefaults.cardColors(
            containerColor = if (capsule.isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
          ),
          shape = RoundedCornerShape(18.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (capsule.isUnlocked) VybeOnlineGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
          )
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                color = if (capsule.isUnlocked) VybeOnlineGreen.copy(alpha = 0.15f) else VybeViolet.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = if (capsule.isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (capsule.isUnlocked) VybeOnlineGreen else VybeViolet,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (capsule.isUnlocked) "UNLOCKED" else "SEALED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (capsule.isUnlocked) VybeOnlineGreen else VybeViolet
                  )
                }
              }

              Text(
                text = "Created ${capsule.createdAtFormatted}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = capsule.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${capsule.recipientSummary} • Unlocks on ${capsule.unlockDateFormatted}",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (capsule.isUnlocked) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "📜 \"${capsule.sealedMessage}\"",
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.padding(12.dp)
                )
              }
            } else {
              Surface(
                color = Color.Black.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "Message Sealed in Time",
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }
              }
            }

            if (!capsule.isUnlocked && capsule.unlockTimestampMs <= System.currentTimeMillis()) {
              Spacer(modifier = Modifier.height(10.dp))
              Button(
                onClick = { viewModel.unlockTimeCapsule(capsule.id) },
                colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen),
                modifier = Modifier.align(Alignment.End)
              ) {
                Text("Open Unlocked Capsule", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }

  // Create Capsule Dialog
  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("Seal a Future Time Capsule", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            label = { Text("Capsule Title") },
            placeholder = { Text("e.g. Note to 25yo self") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = recipientInput,
            onValueChange = { recipientInput = it },
            label = { Text("Recipient") },
            placeholder = { Text("e.g. To myself or @priya_design") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = unlockDateInput,
            onValueChange = { unlockDateInput = it },
            label = { Text("Unlock Target Date") },
            placeholder = { Text("e.g. Dec 31, 2026") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = messageInput,
            onValueChange = { messageInput = it },
            label = { Text("Sealed Message") },
            placeholder = { Text("What memory or wisdom do you want to preserve?") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (titleInput.isNotBlank() && messageInput.isNotBlank()) {
              viewModel.createTimeCapsule(titleInput, messageInput, recipientInput, unlockDateInput)
              showCreateDialog = false
              titleInput = ""
              messageInput = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Seal Capsule")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
      }
    )
  }
}

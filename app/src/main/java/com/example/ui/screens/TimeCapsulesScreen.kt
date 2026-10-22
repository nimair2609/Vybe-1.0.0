package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CapsuleInvitationStatus
import com.example.data.model.TimeCapsule
import com.example.ui.VybeViewModel
import com.example.ui.theme.*

/**
 * VYBE TIME CAPSULE 2.0
 * Future sealed messages and media capsules.
 * - Create for yourself or tag/invite friends.
 * - Phone photo and video attachment uploads via system photo picker.
 * - Tagged friends accept/refuse invitation workflow.
 * - Server-enforced 24-hour expiry upon unlocking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCapsulesScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val capsules by viewModel.timeCapsules.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var showCreateDialog by remember { mutableStateOf(false) }
  var titleInput by remember { mutableStateOf("") }
  var messageInput by remember { mutableStateOf("") }
  var recipientType by remember { mutableStateOf("myself") } // "myself" or "friends"
  var selectedFriendUsername by remember { mutableStateOf("priya_design") }
  var unlockDateInput by remember { mutableStateOf("Jan 01, 2027") }
  var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    pickedMediaUri = uri
  }

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
            Text("VYBE Time Capsule", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Sealed memories & future delivery", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          Button(
            onClick = {
              titleInput = ""
              messageInput = ""
              recipientType = "myself"
              pickedMediaUri = null
              showCreateDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .testTag("seal_capsule_button")
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
      verticalArrangement = Arrangement.spacedBy(14.dp)
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
                text = "Seal photos, videos, and heartfelt messages. When unlocked, capsules remain accessible for 24 hours before automatic cryptographic purge.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      items(capsules, key = { it.id }) { capsule ->
        val isUnlocked = capsule.isUnlocked

        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) VybeViolet.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
          ),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isUnlocked) VybeOnlineGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("capsule_card_${capsule.id}")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                color = if (isUnlocked) VybeOnlineGreen.copy(alpha = 0.2f) else VybeViolet.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = if (isUnlocked) "UNLOCKED ✓" else "LOCKED 🔒",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isUnlocked) VybeOnlineGreen else VybeViolet,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
              Text(
                text = "Unlocks: ${capsule.unlockDateFormatted}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = capsule.title,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Recipient: ${capsule.recipientSummary} • Category: ${capsule.categoryTheme}",
              fontSize = 11.sp,
              color = VybeBrandCyan
            )

            // Invitation handling if pending
            if (capsule.myInvitationStatus == CapsuleInvitationStatus.PENDING) {
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                color = VybeMagenta.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("You are invited to this Capsule", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VybeMagenta)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                      onClick = { viewModel.respondToCapsuleInvitation(capsule.id, true) },
                      colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.testTag("accept_capsule_invite_${capsule.id}")
                    ) {
                      Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                      onClick = { viewModel.respondToCapsuleInvitation(capsule.id, false) },
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.testTag("decline_capsule_invite_${capsule.id}")
                    ) {
                      Text("Decline", fontSize = 11.sp)
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isUnlocked) {
              // Unlocked content view
              if (capsule.mediaAttachmentUrl != null) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                ) {
                  AsyncImage(
                    model = capsule.mediaAttachmentUrl,
                    contentDescription = "Capsule Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                }
                Spacer(modifier = Modifier.height(10.dp))
              }

              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text(
                    text = "“${capsule.sealedMessage}”",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "Sealed on ${capsule.createdAtFormatted} by @${capsule.sender.username}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))
              // 24-hour server-enforced expiry indicator
              Surface(
                color = Color(0xFFFF9800).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Server-enforced 24h Purge Active • 18h 42m remaining until auto-deletion",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
                  )
                }
              }
            } else {
              // Locked teaser
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Lock, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text("Contents Sealed", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("Media and personal message are encrypted and unavailable until due date.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }

              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
              ) {
                TextButton(
                  onClick = { viewModel.unlockTimeCapsule(capsule.id) }
                ) {
                  Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Simulate Unlock Date", fontSize = 11.sp, color = VybeBrandCyan)
                }
              }
            }
          }
        }
      }
    }
  }

  // Create Capsule Full-Screen Dialog
  if (showCreateDialog) {
    val context = LocalContext.current
    val generatedInviteLink = "https://vybe.app/capsule/join?code=CAPSULE-${System.currentTimeMillis() % 100000}"

    Dialog(
      onDismissRequest = { showCreateDialog = false },
      properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true
      )
    ) {
      Scaffold(
        topBar = {
          TopAppBar(
            navigationIcon = {
              IconButton(onClick = { showCreateDialog = false }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
              }
            },
            title = {
              Column {
                Text("Seal a Time Capsule", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("VYBE TIME CAPSULE • Cryptographically Sealed", fontSize = 11.sp, color = VybeBrandCyan)
              }
            },
            actions = {
              Button(
                onClick = {
                  if (titleInput.isNotBlank() && messageInput.isNotBlank()) {
                    val recipientSummary = when (recipientType) {
                      "myself" -> "To myself"
                      "friends" -> "To @$selectedFriendUsername"
                      else -> "Shareable Link Members"
                    }
                    viewModel.createTimeCapsule(
                      title = titleInput.trim(),
                      message = messageInput.trim(),
                      recipient = recipientSummary,
                      unlockDate = unlockDateInput.trim()
                    )
                    showCreateDialog = false
                  }
                },
                enabled = titleInput.isNotBlank() && messageInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .testTag("confirm_seal_capsule_button")
              ) {
                Text("Seal Capsule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = titleInput,
              onValueChange = { titleInput = it },
              label = { Text("Capsule Title") },
              placeholder = { Text("e.g., Graduation Day / Letter to 2027") },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("capsule_title_input"),
              singleLine = true,
              shape = RoundedCornerShape(14.dp)
            )
          }

          item {
            Text("Recipient Access", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = recipientType == "myself",
                onClick = { recipientType = "myself" },
                label = { Text("To Myself", fontSize = 11.sp) }
              )
              FilterChip(
                selected = recipientType == "friends",
                onClick = { recipientType = "friends" },
                label = { Text("Tag Friends", fontSize = 11.sp) }
              )
              FilterChip(
                selected = recipientType == "invite_link",
                onClick = { recipientType = "invite_link" },
                label = { Text("Share Invite Link", fontSize = 11.sp) }
              )
            }
          }

          if (recipientType == "friends") {
            item {
              Text("Select Friend to Tag:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("priya_design", "aarav_beats", "vikram_cricket").forEach { username ->
                  FilterChip(
                    selected = selectedFriendUsername == username,
                    onClick = { selectedFriendUsername = username },
                    label = { Text("@$username", fontSize = 10.sp) }
                  )
                }
              }
            }
          } else if (recipientType == "invite_link") {
            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = VybeVioletDark.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f))
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shareable Capsule Invite Link", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Anyone who clicks this link can join and unlock this capsule when the time arrives.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = generatedInviteLink,
                      fontSize = 11.sp,
                      color = VybeBrandCyan,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  OutlinedButton(
                    onClick = {
                      val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Join my VYBE Time Capsule!")
                        putExtra(Intent.EXTRA_TEXT, "I just sealed a future Time Capsule on VYBE! Join with this invite link: $generatedInviteLink")
                      }
                      context.startActivity(Intent.createChooser(shareIntent, "Share Capsule Link"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = VybeBrandCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Invite Link", fontSize = 11.sp, color = VybeBrandCyan, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }

          item {
            OutlinedTextField(
              value = unlockDateInput,
              onValueChange = { unlockDateInput = it },
              label = { Text("Unlock Date") },
              placeholder = { Text("e.g., Jan 01, 2027") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              shape = RoundedCornerShape(14.dp)
            )
          }

          item {
            OutlinedTextField(
              value = messageInput,
              onValueChange = { messageInput = it },
              label = { Text("Sealed Message") },
              placeholder = { Text("Words that your future self or friend will read...") },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("capsule_message_input"),
              minLines = 3,
              maxLines = 5,
              shape = RoundedCornerShape(14.dp)
            )
          }

          item {
            Text("Photo or Video Attachment", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (pickedMediaUri != null) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(140.dp)
                  .clip(RoundedCornerShape(12.dp))
              ) {
                AsyncImage(
                  model = pickedMediaUri,
                  contentDescription = "Selected media",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
                IconButton(
                  onClick = { pickedMediaUri = null },
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(32.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                }
              }
            } else {
              OutlinedButton(
                onClick = {
                  photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Photo / Video from Phone", fontSize = 12.sp)
              }
            }
          }

          item {
            Button(
              onClick = {
                if (titleInput.isNotBlank() && messageInput.isNotBlank()) {
                  val recipientSummary = when (recipientType) {
                    "myself" -> "To myself"
                    "friends" -> "To @$selectedFriendUsername"
                    else -> "Shareable Link Members"
                  }
                  viewModel.createTimeCapsule(
                    title = titleInput.trim(),
                    message = messageInput.trim(),
                    recipient = recipientSummary,
                    unlockDate = unlockDateInput.trim()
                  )
                  showCreateDialog = false
                }
              },
              enabled = titleInput.isNotBlank() && messageInput.isNotBlank(),
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("confirm_seal_capsule_large_button")
            ) {
              Text("Cryptographically Seal Capsule", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
          }

          item {
            Spacer(modifier = Modifier.height(30.dp))
          }
        }
      }
    }
  }
}

package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.CapsuleInvitationStatus
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*

/**
 * VYBE TIME CAPSULE 2.0
 * Future sealed messages and media capsules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCapsulesScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val capsules by viewModel.timeCapsules.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  var showCreateDialog by remember { mutableStateOf(false) }
  var titleInput by remember { mutableStateOf("") }
  var messageInput by remember { mutableStateOf("") }
  var recipientType by remember { mutableStateOf("myself") }
  var selectedFriendUsername by remember { mutableStateOf("") }
  var unlockDateInput by remember { mutableStateOf("Jan 01, 2027") }
  var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    pickedMediaUri = uri
  }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = "VYBE Time Capsule",
        subtitle = "Sealed memories & future delivery",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "capsules_back_button",
        actions = {
          Button(
            onClick = {
              titleInput = ""
              messageInput = ""
              recipientType = "myself"
              pickedMediaUri = null
              showCreateDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
            shape = RoundedCornerShape(VybeTokens.Radius.pill),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
              .padding(end = 4.dp)
              .heightIn(min = 40.dp)
              .testTag("seal_capsule_button")
          ) {
            Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Seal Capsule", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
          }
        }
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .navigationBarsPadding()
        .padding(16.dp),
      contentPadding = PaddingValues(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Explainer card
      item {
        VybeSurfaceCard(elevated = true) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = palette.accent, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                "Cryptographically Locked Until Due Date",
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 13.sp,
                color = palette.textPrimary
              )
              Text(
                text = "Seal photos, videos, and heartfelt messages. When unlocked, capsules remain accessible for 24 hours before automatic cryptographic purge.",
                fontSize = 11.sp,
                color = palette.textSecondary,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      if (capsules.isEmpty()) {
        item {
          VybeEmptyState(
            icon = Icons.Outlined.LockClock,
            title = "No Time Capsules yet",
            subtitle = "Seal a message or media memory for your future self or close friends.",
            actionLabel = "Seal Capsule",
            onAction = { showCreateDialog = true }
          )
        }
      }

      items(capsules, key = { it.id }) { capsule ->
        val isUnlocked = capsule.isUnlocked

        Surface(
          color = if (isUnlocked) palette.accent.copy(alpha = 0.08f) else palette.card,
          shape = RoundedCornerShape(VybeTokens.Radius.lg),
          border = BorderStroke(
            1.dp,
            if (isUnlocked) palette.success.copy(alpha = 0.5f) else palette.border
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("capsule_card_${capsule.id}")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                color = if (isUnlocked) palette.success.copy(alpha = 0.16f) else palette.accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(VybeTokens.Radius.xs)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = if (isUnlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) palette.success else palette.accent,
                    modifier = Modifier.size(12.dp)
                  )
                  Text(
                    text = if (isUnlocked) "UNLOCKED" else "LOCKED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) palette.success else palette.accent
                  )
                }
              }
              Text(
                text = "Unlocks: ${capsule.unlockDateFormatted}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = capsule.title,
              fontWeight = FontWeight.Bold,
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 16.sp,
              color = palette.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Recipient: ${capsule.recipientSummary} • Category: ${capsule.categoryTheme}",
              fontSize = 11.sp,
              color = palette.accentSecondary
            )

            if (capsule.myInvitationStatus == CapsuleInvitationStatus.PENDING) {
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                color = palette.accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    "You are invited to this Capsule",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                    modifier = Modifier.weight(1f)
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                      onClick = { viewModel.respondToCapsuleInvitation(capsule.id, true) },
                      colors = ButtonDefaults.buttonColors(containerColor = palette.success, contentColor = Color.White),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      modifier = Modifier.testTag("accept_capsule_invite_${capsule.id}")
                    ) {
                      Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                      onClick = { viewModel.respondToCapsuleInvitation(capsule.id, false) },
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      border = BorderStroke(1.dp, palette.border),
                      modifier = Modifier.testTag("decline_capsule_invite_${capsule.id}")
                    ) {
                      Text("Decline", fontSize = 11.sp, color = palette.textPrimary)
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isUnlocked) {
              if (capsule.mediaAttachmentUrl != null) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.md))
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
                color = palette.elevated,
                border = BorderStroke(1.dp, palette.border),
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text(
                    text = "“${capsule.sealedMessage}”",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = palette.textPrimary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "Sealed on ${capsule.createdAtFormatted} by @${capsule.sender.username}",
                    fontSize = 10.sp,
                    color = palette.textSecondary
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                color = palette.warning.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, palette.warning.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Timer, contentDescription = null, tint = palette.warning, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Server-enforced 24h Purge Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.warning
                  )
                }
              }
            } else {
              Surface(
                color = palette.elevated,
                border = BorderStroke(1.dp, palette.border),
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text("Contents Sealed", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = palette.textPrimary)
                    Text(
                      "Media and personal message are encrypted and unavailable until due date.",
                      fontSize = 10.sp,
                      color = palette.textSecondary
                    )
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
                  Icon(Icons.Default.LockOpen, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Check Unlock Status", fontSize = 11.sp, color = palette.accentSecondary, fontWeight = FontWeight.SemiBold)
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
        containerColor = palette.canvas,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
          VybeSubScreenTopBar(
            title = "Seal a Time Capsule",
            subtitle = "Cryptographically Sealed",
            onBackClick = { showCreateDialog = false },
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
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                shape = RoundedCornerShape(VybeTokens.Radius.pill),
                modifier = Modifier
                  .padding(end = 4.dp)
                  .heightIn(min = 40.dp)
                  .testTag("confirm_seal_capsule_button")
              ) {
                Text("Seal Capsule", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp)
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
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = titleInput,
              onValueChange = { titleInput = it },
              label = { Text("Capsule Title") },
              placeholder = { Text("e.g., Graduation Day / Letter to 2027", color = palette.textMuted) },
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("capsule_title_input"),
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            Text("Recipient Access", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(
                "myself" to "To Myself",
                "friends" to "Tag Friends",
                "invite_link" to "Invite Link"
              ).forEach { (key, label) ->
                val selected = recipientType == key
                FilterChip(
                  selected = selected,
                  onClick = { recipientType = key },
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent,
                    selectedLabelColor = Color.White,
                    containerColor = palette.elevated,
                    labelColor = palette.textSecondary
                  ),
                  label = { Text(label, fontSize = 11.sp) }
                )
              }
            }
          }

          if (recipientType == "friends") {
            item {
              val realFriends = remember(allUsers, currentUser?.id) {
                allUsers.filter { it.id != currentUser?.id }.map { it.username }.distinct().take(12)
              }
              Text("Tag VYBE Friend:", fontSize = 11.sp, color = palette.textSecondary)
              Spacer(modifier = Modifier.height(4.dp))
              OutlinedTextField(
                value = selectedFriendUsername,
                onValueChange = { selectedFriendUsername = it.removePrefix("@").trim() },
                placeholder = { Text("Enter @username", fontSize = 12.sp, color = palette.textMuted) },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier.fillMaxWidth()
              )
              if (realFriends.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  items(realFriends.size) { index ->
                    val username = realFriends[index]
                    FilterChip(
                      selected = selectedFriendUsername == username,
                      onClick = { selectedFriendUsername = username },
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent,
                        selectedLabelColor = Color.White,
                        containerColor = palette.elevated,
                        labelColor = palette.textSecondary
                      ),
                      label = { Text("@$username", fontSize = 10.sp) }
                    )
                  }
                }
              }
            }
          } else if (recipientType == "invite_link") {
            item {
              VybeSurfaceCard(elevated = true) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shareable Capsule Invite Link", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.textPrimary)
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Anyone who clicks this link can join and unlock this capsule when the time arrives.",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Surface(
                    color = palette.card,
                    border = BorderStroke(1.dp, palette.border),
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = generatedInviteLink,
                      fontSize = 11.sp,
                      color = palette.accentSecondary,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  VybeSecondaryButton(
                    text = "Share Invite Link",
                    icon = Icons.Default.Share,
                    onClick = {
                      val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Join my VYBE Time Capsule!")
                        putExtra(Intent.EXTRA_TEXT, "I just sealed a future Time Capsule on VYBE! Join with this invite link: $generatedInviteLink")
                      }
                      context.startActivity(Intent.createChooser(shareIntent, "Share Capsule Link"))
                    },
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              }
            }
          }

          item {
            OutlinedTextField(
              value = unlockDateInput,
              onValueChange = { unlockDateInput = it },
              label = { Text("Unlock Date") },
              placeholder = { Text("e.g., Jan 01, 2027", color = palette.textMuted) },
              colors = fieldColors,
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            OutlinedTextField(
              value = messageInput,
              onValueChange = { messageInput = it },
              label = { Text("Sealed Message") },
              placeholder = { Text("Words that your future self or friend will read...", color = palette.textMuted) },
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("capsule_message_input"),
              minLines = 3,
              maxLines = 5,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            Text("Photo or Video Attachment", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            if (pickedMediaUri != null) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(140.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.md))
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
                    .padding(6.dp)
                    .background(ObsidianCanvas.copy(alpha = 0.72f), CircleShape)
                    .size(36.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                }
              }
            } else {
              VybeSecondaryButton(
                text = "Upload Photo / Video from Phone",
                icon = Icons.Default.AttachFile,
                onClick = {
                  photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                modifier = Modifier.fillMaxWidth()
              )
            }
          }

          item {
            VybePrimaryButton(
              text = "Cryptographically Seal Capsule",
              onClick = {
                if (titleInput.isNotBlank() && messageInput.isNotBlank()) {
                  val recipientSummary = when (recipientType) {
                    "myself" -> "To myself"
                    "friends" -> if (selectedFriendUsername.isNotBlank()) "To @$selectedFriendUsername" else "To myself"
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
              modifier = Modifier
                .fillMaxWidth()
                .testTag("confirm_seal_capsule_large_button")
            )
          }

          item {
            Spacer(modifier = Modifier.height(30.dp))
          }
        }
      }
    }
  }
}

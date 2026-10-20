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
import com.example.data.model.LivePulseStatus
import com.example.data.model.MoodType
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.VybeGradientButton
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodZonesScreen(viewModel: VybeViewModel) {
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val selectedMood by viewModel.selectedMoodFilter.collectAsState()

  var showCheckInDialog by remember { mutableStateOf(false) }
  var chosenMood by remember { mutableStateOf(currentUser?.currentMood ?: MoodType.HAPPY) }
  var chosenPulse by remember { mutableStateOf(currentUser?.livePulse ?: LivePulseStatus.OPEN_TO_CHAT) }

  val zonePosts = remember(posts, selectedMood) {
    if (selectedMood != null) {
      posts.filter { it.moodZone == selectedMood }
    } else {
      posts
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Mood Zones",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Emotional solidarity without judgment",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          Button(
            onClick = { showCheckInDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(end = 12.dp).testTag("check_in_mood_button")
          ) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Vibe Check", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
      // Current User Status Card
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(18.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                  currentUser?.currentMood?.accentColor?.copy(alpha = 0.25f)
                    ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = currentUser?.currentMood?.emoji ?: "💫",
                fontSize = 24.sp
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Your Current Vibe",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "${currentUser?.currentMood?.title ?: "No Zone Set"} • ${currentUser?.livePulse?.label ?: "Offline"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            TextButton(onClick = { showCheckInDialog = true }) {
              Text("Switch", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Zone Switcher Grid
      item {
        Text(
          text = "Explore 5 Mood Portals",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      items(MoodType.values()) { mood ->
        val isSelected = selectedMood == mood
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable {
              viewModel.selectMoodFilter(if (isSelected) null else mood)
            }
            .testTag("mood_card_${mood.name}"),
          colors = CardDefaults.cardColors(
            containerColor = if (isSelected) mood.accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
          ),
          border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) mood.accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
          ),
          shape = RoundedCornerShape(16.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(mood.accentColor.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Text(text = mood.emoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = mood.title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Surface(
                    color = mood.accentColor,
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = "ACTIVE",
                      color = Color.Black,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                  }
                }
              }
              Text(
                text = mood.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = mood.vibeTagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp
              )
            }

            Icon(
              imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
              contentDescription = null,
              tint = if (isSelected) mood.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Zone Posts Header
      item {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = if (selectedMood != null) "${selectedMood!!.emoji} ${selectedMood!!.title} Feed" else "All Mood Zone Posts",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      items(zonePosts, key = { it.id }) { post ->
        PostCard(
          post = post,
          onLikeClick = { viewModel.toggleLike(post.id) },
          onSaveClick = { viewModel.toggleSave(post.id) },
          onCommentClick = { viewModel.addComment(post.id, "Sending solidarity!") },
          onShareClick = {},
          onUserClick = { u -> viewModel.openUserProfile(u) }
        )
      }

      // Safety & Protection Banner at Bottom
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "🛡️ VYBE Emotional Safety Guarantee",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Mood selections are strictly temporary and user-controlled. VYBE never permanently labels users, never exposes vulnerable moods to advertising algorithms, and enforces zero-tolerance for unsolicited advances.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 15.sp
            )
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }

  // Vibe Check Dialog
  if (showCheckInDialog) {
    AlertDialog(
      onDismissRequest = { showCheckInDialog = false },
      title = { Text("Check into a Mood Zone", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Select your current energy:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(8.dp))

          MoodType.values().forEach { m ->
            Surface(
              color = if (chosenMood == m) m.accentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable { chosenMood = m }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(m.emoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = m.title,
                  fontWeight = if (chosenMood == m) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 13.sp
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "Live Pulse Status (Temporary):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(6.dp))

          LivePulseStatus.values().filter { it != LivePulseStatus.NONE }.forEach { pulse ->
            Surface(
              color = if (chosenPulse == pulse) VybeViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable { chosenPulse = pulse }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(pulse.icon, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = pulse.label,
                  fontWeight = if (chosenPulse == pulse) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 13.sp
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.updateUserMoodAndPulse(chosenMood, chosenPulse)
            viewModel.selectMoodFilter(chosenMood)
            showCheckInDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Save Vibe")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCheckInDialog = false }) { Text("Cancel") }
      }
    )
  }
}

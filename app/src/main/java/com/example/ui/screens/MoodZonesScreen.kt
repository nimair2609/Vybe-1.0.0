package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LivePulseStatus
import com.example.data.model.MoodType
import com.example.data.model.matchesMoodFilter
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.VybeMoodOutlineIcon
import com.example.ui.theme.VybeViolet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodZonesScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val selectedMood by viewModel.selectedMoodFilter.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()

  val zonePosts = remember(posts, selectedMood) {
    posts.filter { !it.isArchived && it.matchesMoodFilter(selectedMood) }
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
              text = "Select a mood to update your vibe and filter the community stream",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          if (selectedMood != null) {
            TextButton(
              onClick = { viewModel.selectMoodFilter(null) },
              modifier = Modifier
                .padding(end = 8.dp)
                .heightIn(min = 48.dp)
                .testTag("clear_mood_filter_button")
            ) {
              Text("All Vibes", fontWeight = FontWeight.Bold, color = VybeViolet)
            }
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
      // Live Availability Pulse (No duplicate mood selector modal, and NO Light/Dark theme controls here)
      if (currentUser != null) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(18.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "Current Mood: ${selectedMood?.title ?: currentUser?.currentMood?.title ?: "All Vibes"}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Availability Status",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(8.dp))
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LivePulseStatus.values().filter { it != LivePulseStatus.NONE }) { pulse ->
                  val isActivePulse = currentUser?.livePulse == pulse
                  FilterChip(
                    selected = isActivePulse,
                    onClick = {
                      val nextPulse = if (isActivePulse) LivePulseStatus.NONE else pulse
                      viewModel.updateUserMoodAndPulse(selectedMood ?: currentUser?.currentMood, nextPulse)
                    },
                    label = { Text(pulse.label, fontSize = 11.sp) }
                  )
                }
              }
            }
          }
        }
      }

      // Single Unified Mood Selector List
      item {
        Text(
          text = "Choose Mood Zone",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      items(MoodType.values(), key = { it.name }) { mood ->
        val isSelected = selectedMood == mood
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
              val nextMood = if (isSelected) null else mood
              viewModel.selectMoodFilter(nextMood)
              viewModel.updateUserMoodAndPulse(
                mood = nextMood,
                pulse = currentUser?.livePulse ?: LivePulseStatus.OPEN_TO_CHAT
              )
            }
            .testTag("mood_card_${mood.name}"),
          colors = CardDefaults.cardColors(
            containerColor = if (isSelected) mood.accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
          ),
          border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) mood.accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
          ),
          shape = RoundedCornerShape(16.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(mood.accentColor.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              VybeMoodOutlineIcon(
                mood = mood,
                tint = mood.accentColor,
                size = 22.dp
              )
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
                      text = "SELECTED",
                      color = Color.Black,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
              }
              Text(
                text = mood.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Icon(
              imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight,
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
          text = when (selectedMood) {
            null -> "All Community Posts"
            MoodType.GENERAL -> "General Feed (Includes Untagged Posts)"
            else -> "${selectedMood!!.title} Zone Posts"
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      if (zonePosts.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "No posts in this zone yet",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Share the first post in ${selectedMood?.title ?: "this"} zone.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      } else {
        items(zonePosts, key = { it.id }) { post ->
          PostCard(
            post = post,
            onLikeClick = { viewModel.toggleLike(post.id) },
            onReact = { reaction -> viewModel.reactToPost(post.id, reaction) },
            onSaveClick = { viewModel.toggleSave(post.id) },
            onCommentClick = { viewModel.loadCommentsForPost(post.id) },
            onAddComment = { text -> viewModel.addComment(post.id, text) },
            onLoadComments = { viewModel.loadCommentsForPost(post.id) },
            onShareClick = { viewModel.openPostShareSheet(post) },
            onUserClick = { u -> viewModel.openUserProfile(u) },
            onRemixClick = { viewModel.openReelViewer(post.id) },
            onDeleteClick = { viewModel.deletePost(post.id) },
            onReportPost = { reason ->
              scope.launch {
                viewModel.submitReport("POST", post.id, reason)
                viewModel.postBannerMessage("Report submitted for review.")
              }
            },
            onPostViewed = { viewModel.recordPostView(it) },
            currentUserId = currentUser?.id,
            isFollowing = followingUserIds.contains(post.author.id),
            isFollowRequested = requestedFollowUserIds.contains(post.author.id) || post.author.isFollowRequestedByMe,
            followsMe = post.author.followsMe,
            onFollowClick = if (post.author.id != currentUser?.id) {
              { viewModel.toggleFollowUser(post.author.id) }
            } else {
              null
            }
          )
        }
      }

      // Emotional Safety Guarantee
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
          ) {
            Icon(
              Icons.Outlined.Shield,
              contentDescription = null,
              tint = VybeViolet,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "VYBE Emotional Safety Guarantee",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Mood selections are temporary and user-controlled. VYBE never uses mood states for ad targeting. Appearance and theme controls remain in Settings.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
              )
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(84.dp))
      }
    }
  }
}

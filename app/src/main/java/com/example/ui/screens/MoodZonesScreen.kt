package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Spa
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
import com.example.data.model.LivePulseStatus
import com.example.data.model.MoodType
import com.example.data.model.matchesMoodFilter
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeMoodOutlineIcon
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodZonesScreen(viewModel: VybeViewModel) {
  val scope = rememberCoroutineScope()
  val palette = LocalVybePalette.current
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val selectedMood by viewModel.selectedMoodFilter.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()

  val zonePosts = remember(posts, selectedMood) {
    posts.filter { !it.isArchived && it.matchesMoodFilter(selectedMood) }
  }

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      Column(modifier = Modifier.fillMaxWidth().background(palette.canvas)) {
        TopAppBar(
          windowInsets = WindowInsets.statusBars,
          title = {
            Column {
              Text(
                text = "Mood Zones",
                fontFamily = PlusJakartaSansFontFamily,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
              )
              Text(
                text = "Select a mood to update your vibe and filter the stream",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
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
                Text(
                  "All Vibes",
                  fontWeight = FontWeight.Bold,
                  fontFamily = PlusJakartaSansFontFamily,
                  color = palette.accent
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.canvas)
        )
        HorizontalDivider(thickness = 0.8.dp, color = palette.border)
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Live Availability Pulse
      if (currentUser != null) {
        item {
          VybeSurfaceCard(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            elevated = true
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "Current Mood: ${selectedMood?.title ?: currentUser?.currentMood?.title ?: "All Vibes"}",
                fontFamily = PlusJakartaSansFontFamily,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Availability Status",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
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
                    shape = RoundedCornerShape(VybeTokens.Radius.pill),
                    colors = FilterChipDefaults.filterChipColors(
                      selectedContainerColor = palette.accent,
                      selectedLabelColor = Color.White,
                      containerColor = palette.card,
                      labelColor = palette.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                      enabled = true,
                      selected = isActivePulse,
                      borderColor = palette.border,
                      selectedBorderColor = palette.accent
                    ),
                    label = {
                      Text(
                        pulse.label,
                        fontSize = 11.sp,
                        fontWeight = if (isActivePulse) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = PlusJakartaSansFontFamily
                      )
                    }
                  )
                }
              }
            }
          }
        }
      }

      // Spiritual Sanctuary & Zen Breathwork Card
      item {
        val sanctuaryGreen = Color(0xFF10B981)
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(VybeTokens.Radius.lg))
            .clickable { viewModel.navigateTo(Screen.SPIRITUAL_MODE) }
            .testTag("enter_spiritual_mode_card"),
          color = sanctuaryGreen.copy(alpha = 0.12f),
          shape = RoundedCornerShape(VybeTokens.Radius.lg),
          border = BorderStroke(1.dp, sanctuaryGreen.copy(alpha = 0.35f))
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(sanctuaryGreen.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.Spa,
                contentDescription = null,
                tint = sanctuaryGreen,
                modifier = Modifier.size(26.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Spiritual Sanctuary & Breathwork",
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 15.sp,
                color = palette.textPrimary
              )
              Text(
                text = "Deep breathing, gratitude logs & sacred reflections",
                fontSize = 11.sp,
                color = sanctuaryGreen
              )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = sanctuaryGreen)
          }
        }
      }

      // Single Unified Mood Selector List
      item {
        Text(
          text = "Choose Mood Zone",
          fontFamily = PlusJakartaSansFontFamily,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
      }

      items(MoodType.values(), key = { it.name }) { mood ->
        val isSelected = selectedMood == mood
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(VybeTokens.Radius.lg))
            .clickable {
              val nextMood = if (isSelected) null else mood
              viewModel.selectMoodFilter(nextMood)
              viewModel.updateUserMoodAndPulse(
                mood = nextMood,
                pulse = currentUser?.livePulse ?: LivePulseStatus.OPEN_TO_CHAT
              )
            }
            .testTag("mood_card_${mood.name}"),
          color = if (isSelected) mood.accentColor.copy(alpha = 0.14f) else palette.card,
          border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) mood.accentColor else palette.border
          ),
          shape = RoundedCornerShape(VybeTokens.Radius.lg)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(mood.accentColor.copy(alpha = 0.18f)),
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
                  fontFamily = PlusJakartaSansFontFamily,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = palette.textPrimary
                )
                if (isSelected) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Surface(
                    color = mood.accentColor,
                    shape = RoundedCornerShape(VybeTokens.Radius.xs)
                  ) {
                    Text(
                      text = "SELECTED",
                      color = Color.Black,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      fontFamily = PlusJakartaSansFontFamily,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
              }
              Text(
                text = mood.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
              )
            }

            Icon(
              imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight,
              contentDescription = null,
              tint = if (isSelected) mood.accentColor else palette.textSecondary
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
          fontFamily = PlusJakartaSansFontFamily,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      if (zonePosts.isEmpty()) {
        item {
          Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            VybeEmptyState(
              icon = Icons.Outlined.Mood,
              title = "No posts in this zone yet",
              subtitle = "Share the first post in ${selectedMood?.title ?: "this"} zone."
            )
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
            onReactComment = { commentId, rx -> viewModel.reactToComment(post.id, commentId, rx) },
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
        VybeSurfaceCard(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          elevated = true
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
          ) {
            Icon(
              Icons.Outlined.Shield,
              contentDescription = null,
              tint = palette.accent,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "VYBE Emotional Safety Guarantee",
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 13.sp,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Mood selections are temporary and user-controlled. VYBE never uses mood states for ad targeting. Appearance and theme controls remain in Settings.",
                fontSize = 11.sp,
                color = palette.textSecondary,
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

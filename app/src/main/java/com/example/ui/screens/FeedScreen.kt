package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.StoryCarousel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: VybeViewModel) {
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val stories by viewModel.stories.collectAsState()
  val selectedMood by viewModel.selectedMoodFilter.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  var feedTab by remember { mutableStateOf("For You") }
  val feedTabs = listOf("For You", "Following", "Discover")

  var isRefreshing by remember { mutableStateOf(false) }
  var showStoryCreateSheet by remember { mutableStateOf(false) }
  var storyTypeSelected by remember { mutableStateOf<String?>(null) }
  var storyCreatedFeedback by remember { mutableStateOf<String?>(null) }

  val filteredPosts = remember(posts, selectedMood, feedTab) {
    if (selectedMood != null) {
      posts.filter { it.moodZone == selectedMood }
    } else {
      posts
    }
  }

  fun triggerRefresh() {
    if (isRefreshing) return
    isRefreshing = true
    coroutineScope.launch {
      delay(700)
      isRefreshing = false
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets.statusBars,
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
            ) {
              androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.vybe_app_icon_clean),
                contentDescription = "VYBE",
                modifier = Modifier.fillMaxSize()
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "vybe",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 1.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
            if (currentUser?.currentMood != null) {
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                color = currentUser!!.currentMood!!.accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = "${currentUser!!.currentMood!!.emoji} ${currentUser!!.currentMood!!.title}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = currentUser!!.currentMood!!.accentColor,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }
        },
        actions = {
          // Theme toggle removed from header per Section 4; placed in Settings
          IconButton(
            onClick = { viewModel.navigateTo(Screen.SETTINGS) },
            modifier = Modifier.testTag("settings_top_button")
          ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
          }
          if (currentUser != null) {
            IconButton(
              onClick = { viewModel.openUserProfile(currentUser!!) },
              modifier = Modifier.testTag("profile_avatar_top_button")
            ) {
              VybeAvatar(user = currentUser!!, size = 32.dp)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { viewModel.navigateTo(Screen.CREATE_POST) },
        containerColor = VybeViolet,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier.testTag("create_post_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Create Post")
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .testTag("feed_list")
      ) {
        // Double-Tap to Refresh Feed Header gesture area
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .pointerInput(Unit) {
                detectTapGestures(
                  onDoubleTap = {
                    triggerRefresh()
                  }
                )
              }
              .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Double-tap here anytime to refresh feed",
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              fontWeight = FontWeight.Medium
            )
          }
        }

        // Stories Carousel
        item {
          StoryCarousel(
            stories = stories,
            currentUser = currentUser,
            onStoryClick = { viewModel.navigateTo(Screen.REELS_VIEWER) },
            onAddStoryClick = { showStoryCreateSheet = true }
          )
        }

        // Active Mood Zone Filter Banner
        if (selectedMood != null) {
          item {
            Surface(
              color = selectedMood!!.accentColor.copy(alpha = 0.15f),
              shape = RoundedCornerShape(12.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, selectedMood!!.accentColor.copy(alpha = 0.4f)),
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(text = selectedMood!!.emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Filtered by ${selectedMood!!.title} Zone",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                  )
                  Text(
                    text = selectedMood!!.vibeTagline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                  )
                }
                TextButton(onClick = { viewModel.selectMoodFilter(null) }) {
                  Text("Clear", color = selectedMood!!.accentColor, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        // Tab switcher ("For You", "Following", "Discover")
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            feedTabs.forEach { tab ->
              val isSelected = feedTab == tab
              Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.clickable { feedTab = tab }
              ) {
                Text(
                  text = tab,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 13.sp,
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
              }
            }
          }
        }

        // Feed Posts
        if (filteredPosts.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  imageVector = Icons.Default.FilterVintage,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "No posts in this vibe yet.",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Be the first to share your energy with the community!",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        } else {
          items(filteredPosts, key = { it.id }) { post ->
            PostCard(
              post = post,
              onLikeClick = { viewModel.toggleLike(post.id) },
              onSaveClick = { viewModel.toggleSave(post.id) },
              onCommentClick = { viewModel.addComment(post.id, "Appreciating this vibe!") },
              onShareClick = { /* Share dialog */ },
              onUserClick = { user -> viewModel.openUserProfile(user) },
              onRemixClick = { viewModel.navigateTo(Screen.REELS_VIEWER) }
            )
          }
        }

        item {
          Spacer(modifier = Modifier.height(80.dp))
        }
      }

      // Visual feedback banner during double-tap feed refresh
      AnimatedVisibility(
        visible = isRefreshing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 10.dp)
      ) {
        Surface(
          color = VybeVioletDark,
          shape = RoundedCornerShape(20.dp),
          shadowElevation = 8.dp
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Refreshed with latest VYBES!",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // Feedback for created story
      if (storyCreatedFeedback != null) {
        Snackbar(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
          action = {
            TextButton(onClick = { storyCreatedFeedback = null }) {
              Text("OK", color = Color.White)
            }
          }
        ) {
          Text(storyCreatedFeedback ?: "")
        }
      }
    }
  }

  // Story Creation Modal with separate options for Photo, Video, Text, Music (Section 8)
  if (showStoryCreateSheet) {
    AlertDialog(
      onDismissRequest = { showStoryCreateSheet = false },
      title = {
        Text("Create New Story", fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Share an authentic moment that disappears in 24 hours:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          // 1. Photo Story
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                storyCreatedFeedback = "Photo Story published to your vibe ring!"
                showStoryCreateSheet = false
              }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = VybeViolet)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Photo Story", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Select from gallery or capture camera snapshot", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          // 2. Video Story
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                storyCreatedFeedback = "Video Story published to your vibe ring!"
                showStoryCreateSheet = false
              }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Videocam, contentDescription = null, tint = VybeMagenta)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Video Story", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Short video clip with responsive playback", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          // 3. Text Story
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                storyCreatedFeedback = "Text thought published to your vibe ring!"
                showStoryCreateSheet = false
              }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.TextFields, contentDescription = null, tint = VybeCyan)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Text Thought", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Express in words with animated mood gradients", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          // 4. Music Story
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                storyCreatedFeedback = "Music Vibe track added to your story!"
                showStoryCreateSheet = false
              }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFFFB74D))
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Music & Audio Vibe", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Attach licensed Indian indie/lo-fi soundscapes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showStoryCreateSheet = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

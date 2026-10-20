package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MoodType
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.StoryCarousel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: VybeViewModel) {
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val stories by viewModel.stories.collectAsState()
  val selectedMood by viewModel.selectedMoodFilter.collectAsState()

  var feedTab by remember { mutableStateOf("For You") }
  val feedTabs = listOf("For You", "Following", "Discover")

  val filteredPosts = remember(posts, selectedMood, feedTab) {
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
          IconButton(
            onClick = { viewModel.toggleDarkMode() },
            modifier = Modifier.testTag("theme_toggle_button")
          ) {
            Icon(Icons.Default.DarkMode, contentDescription = "Toggle Theme")
          }
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
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .testTag("feed_list")
    ) {
      // Stories Carousel
      item {
        StoryCarousel(
          stories = stories,
          currentUser = currentUser,
          onStoryClick = { viewModel.navigateTo(Screen.REELS_VIEWER) },
          onAddStoryClick = { viewModel.navigateTo(Screen.CREATE_POST) }
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
  }
}

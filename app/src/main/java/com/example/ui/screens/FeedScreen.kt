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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.example.ui.components.StoryCreateDialog
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
  val notifications by viewModel.notifications.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  var feedTab by remember { mutableStateOf("For You") }
  val feedTabs = listOf("For You", "Following", "Discover")

  var isRefreshing by remember { mutableStateOf(false) }
  var showStoryCreateSheet by remember { mutableStateOf(false) }
  var storyTypeSelected by remember { mutableStateOf<String?>(null) }
  var storyCreatedFeedback by remember { mutableStateOf<String?>(null) }

  val context = androidx.compose.ui.platform.LocalContext.current
  var availableRelease by remember { mutableStateOf<com.example.util.GitHubReleaseInfo?>(null) }
  var isUpdateBannerDismissed by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    try {
      val repo = com.example.util.GitHubUpdater.getSavedRepo(context)
      if (repo.isNotBlank()) {
        val res = com.example.util.GitHubUpdater.checkForUpdates(repo, currentVersion = "2.0.0")
        res.onSuccess { release ->
          if (release.isUpdateAvailable) {
            availableRelease = release
          }
        }
      }
    } catch (_: Exception) {}
  }

  val filteredPosts = remember(posts, selectedMood, feedTab, followingUserIds) {
    var list = posts
    if (selectedMood != null) {
      list = list.filter { it.moodZone == selectedMood }
    }
    if (feedTab == "Following") {
      list = list.filter { followingUserIds.contains(it.author.id) }
    }
    list
  }

  fun triggerRefresh() {
    if (isRefreshing) return
    isRefreshing = true
    coroutineScope.launch {
      delay(700)
      isRefreshing = false
    }
  }

  val listState = androidx.compose.foundation.lazy.rememberLazyListState()

  LaunchedEffect(Unit) {
    viewModel.feedRefreshTrigger.collect {
      listState.animateScrollToItem(0)
      triggerRefresh()
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
          val unreadNotifsCount = remember(notifications) { notifications.count { !it.isRead } }
          IconButton(
            onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
            modifier = Modifier.testTag("notification_bell_button")
          ) {
            BadgedBox(
              badge = {
                if (unreadNotifsCount > 0) {
                  Badge(containerColor = VybeMagenta) {
                    Text("$unreadNotifsCount")
                  }
                }
              }
            ) {
              Icon(Icons.Default.Notifications, contentDescription = "Notification Center")
            }
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
    PullToRefreshBox(
      isRefreshing = isRefreshing,
      onRefresh = { triggerRefresh() },
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .testTag("feed_list")
      ) {
        // In-App Git Update Banner
        if (availableRelease != null && !isUpdateBannerDismissed) {
          item {
            com.example.ui.components.InAppUpdateBanner(
              release = availableRelease!!,
              onUpdateClick = { viewModel.navigateTo(Screen.SETTINGS) },
              onDismiss = { isUpdateBannerDismissed = true }
            )
          }
        }

        // Stories Carousel Tray
        item {
          StoryCarousel(
            stories = stories,
            currentUser = currentUser,
            onStoryClick = { story -> viewModel.openStory(story) },
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
                .padding(horizontal = 20.dp, vertical = 28.dp),
              contentAlignment = Alignment.Center
            ) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Box(
                    modifier = Modifier
                      .size(60.dp)
                      .clip(CircleShape)
                      .background(VybeViolet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.AutoAwesome,
                      contentDescription = null,
                      tint = VybeViolet,
                      modifier = Modifier.size(30.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(14.dp))
                  Text(
                    text = if (selectedMood != null) "No posts in this vibe yet" else "Welcome to VYBE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = if (selectedMood != null)
                      "Be the first to share an authentic moment in the ${selectedMood?.title} zone!"
                    else
                      "Your feed is clean and ready. Share your first moment, photo, or story with the community!",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                  )
                  Spacer(modifier = Modifier.height(16.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                      onClick = { viewModel.navigateTo(Screen.CREATE_POST) },
                      colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                      shape = RoundedCornerShape(12.dp)
                    ) {
                      Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Create Post", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                      onClick = { showStoryCreateSheet = true },
                      shape = RoundedCornerShape(12.dp)
                    ) {
                      Text("Add Story", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                  }
                }
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
              onAddComment = { text -> viewModel.addComment(post.id, text) },
              onShareClick = { /* Share dialog */ },
              onUserClick = { user -> viewModel.openUserProfile(user) },
              onRemixClick = { viewModel.navigateTo(Screen.REELS_VIEWER) },
              onDeleteClick = { viewModel.deletePost(post.id) },
              onPostViewed = { viewModel.recordPostView(it) },
              currentUserId = currentUser?.id
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

  // Instagram-style Story Creation Dialog with separate fields (Photo, Type/Text, Music, Video, Audience, Location)
  if (showStoryCreateSheet) {
    StoryCreateDialog(
      viewModel = viewModel,
      onDismiss = { showStoryCreateSheet = false },
      onStoryPublished = { feedback ->
        storyCreatedFeedback = feedback
        showStoryCreateSheet = false
      }
    )
  }
}


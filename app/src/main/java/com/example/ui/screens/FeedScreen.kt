package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MediaType
import com.example.data.model.MoodType
import com.example.data.model.matchesMoodFilter
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.StoryCarousel
import com.example.ui.components.StoryCreateDialog
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeMatchesManagerDialog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val stories by viewModel.stories.collectAsState()
  val selectedMood by viewModel.selectedMoodFilter.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()
  val hasMoreFeedPosts by viewModel.hasMoreFeedPosts.collectAsState()
  val isLoadingMorePosts by viewModel.isLoadingMorePosts.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  var feedTab by remember { mutableStateOf("For You") }
  val feedTabs = listOf("For You", "Following", "Discover")

  var isRefreshing by remember { mutableStateOf(false) }
  var showStoryCreateSheet by remember { mutableStateOf(false) }
  var showVybeMatchesDialog by remember { mutableStateOf(false) }
  var storyCreatedFeedback by remember { mutableStateOf<String?>(null) }

  val filteredPosts = remember(posts, selectedMood, feedTab, followingUserIds, currentUser?.id) {
    var list = posts.filter { !it.isArchived && it.matchesMoodFilter(selectedMood) }
    when (feedTab) {
      "Following" -> list = list.filter { followingUserIds.contains(it.author.id) || it.author.id == currentUser?.id }
      "Discover" -> list = list.sortedByDescending { it.likesCount + it.commentsCount + it.viewsCount }
    }
    list
  }

  val videoPostIds = remember(filteredPosts) {
    filteredPosts.filter { post ->
      val firstUrl = post.mediaUrls.firstOrNull().orEmpty()
      post.mediaUrls.isNotEmpty() && (
        post.mediaType == MediaType.VIDEO ||
          post.mediaType == MediaType.REEL ||
          post.isVybesReel ||
          firstUrl.contains(".mp4", ignoreCase = true) ||
          firstUrl.contains(".mov", ignoreCase = true) ||
          firstUrl.contains(".webm", ignoreCase = true)
        )
    }.map { it.id }.toSet()
  }

  fun triggerRefresh() {
    if (isRefreshing) return
    isRefreshing = true
    viewModel.refreshAllData()
    coroutineScope.launch {
      delay(650)
      isRefreshing = false
    }
  }

  val listState = rememberLazyListState()

  // Determine the single most-visible video post (>= 55% visible in viewport) so only ONE video autoplays
  val activeAutoPlayPostId by remember(listState, videoPostIds) {
    derivedStateOf {
      if (videoPostIds.isEmpty()) return@derivedStateOf null
      val layoutInfo = listState.layoutInfo
      val viewportStart = layoutInfo.viewportStartOffset
      val viewportEnd = layoutInfo.viewportEndOffset
      if (viewportEnd <= viewportStart) return@derivedStateOf null

      layoutInfo.visibleItemsInfo
        .mapNotNull { itemInfo ->
          val keyStr = itemInfo.key as? String ?: return@mapNotNull null
          if (!videoPostIds.contains(keyStr) || itemInfo.size <= 0) return@mapNotNull null
          val visibleStart = maxOf(itemInfo.offset, viewportStart)
          val visibleEnd = minOf(itemInfo.offset + itemInfo.size, viewportEnd)
          val visiblePx = (visibleEnd - visibleStart).coerceAtLeast(0)
          val fraction = visiblePx.toFloat() / itemInfo.size.toFloat()
          if (fraction >= 0.55f) keyStr to fraction else null
        }
        .maxByOrNull { it.second }
        ?.first
    }
  }

  // Trigger smooth pagination when scrolling within 3 items of the end of the feed
  val shouldLoadMore by remember(listState, filteredPosts.size, hasMoreFeedPosts, isLoadingMorePosts) {
    derivedStateOf {
      if (!hasMoreFeedPosts || isLoadingMorePosts || filteredPosts.isEmpty()) {
        false
      } else {
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = listState.layoutInfo.totalItemsCount
        totalItems > 4 && lastVisibleIndex >= totalItems - 3
      }
    }
  }

  LaunchedEffect(shouldLoadMore) {
    if (shouldLoadMore) {
      viewModel.loadMoreFeedPosts()
    }
  }

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
                .clip(RoundedCornerShape(9.dp))
            ) {
              Image(
                painter = painterResource(id = R.drawable.vybe_app_icon_clean),
                contentDescription = "VYBE",
                modifier = Modifier.fillMaxSize()
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "VYBE",
              fontFamily = SyneFontFamily,
              fontSize = 21.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 1.6.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
        },
        actions = {
          val unreadNotifsCount = remember(notifications) { notifications.count { !it.isRead } }
          IconButton(
            onClick = { showVybeMatchesDialog = true },
            modifier = Modifier
              .size(48.dp)
              .testTag("vybe_matches_top_button")
          ) {
            BadgedBox(
              badge = {
                if (vybeMatches.isNotEmpty()) {
                  Badge(containerColor = Color(0xFF10B981)) {
                    Text("${vybeMatches.size}")
                  }
                }
              }
            ) {
              Icon(Icons.Default.Group, contentDescription = "VYBE Matches", tint = Color(0xFF10B981))
            }
          }
          IconButton(
            onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
            modifier = Modifier
              .size(48.dp)
              .testTag("notification_bell_button")
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
            modifier = Modifier
              .size(48.dp)
              .testTag("settings_top_button")
          ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
          }
          if (currentUser != null) {
            IconButton(
              onClick = { viewModel.openUserProfile(currentUser!!) },
              modifier = Modifier
                .size(48.dp)
                .testTag("profile_avatar_top_button")
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
        // Stories Tray + VYBE Matches Quick Access
        item(key = "stories_tray") {
          StoryCarousel(
            stories = stories,
            currentUser = currentUser,
            onStoryClick = { story -> viewModel.openStory(story) },
            onAddStoryClick = { showStoryCreateSheet = true },
            onManageVybeMatchesClick = { showVybeMatchesDialog = true },
            vybeMatchesCount = vybeMatches.size
          )
        }

        // Single, Unified Mood Selector Bar (No duplicate "All Vibes" + "General" clutter; tapping selected chip resets to unfiltered feed)
        item(key = "mood_selector") {
          LazyRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp, bottom = 4.dp)
              .testTag("feed_mood_selector_row"),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            items(MoodType.values(), key = { it.name }) { mood ->
              val isSelected = selectedMood == mood
              Surface(
                color = if (isSelected) mood.accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = if (isSelected) BorderStroke(1.5.dp, mood.accentColor) else null,
                modifier = Modifier
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    viewModel.selectMoodFilter(if (isSelected) null else mood)
                  }
                  .testTag("feed_mood_chip_${mood.name}")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(text = mood.emoji, fontSize = 13.sp)
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = mood.title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) mood.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }

        // Feed Stream Tabs ("For You", "Following", "Discover")
        item(key = "feed_stream_tabs") {
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
                modifier = Modifier
                  .clip(RoundedCornerShape(18.dp))
                  .clickable { feedTab = tab }
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

        // Feed Posts or Clean Empty State
        if (filteredPosts.isEmpty()) {
          item(key = "feed_empty_state") {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
              contentAlignment = Alignment.Center
            ) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                  .widthIn(max = 560.dp)
                  .fillMaxWidth()
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
                    text = when {
                      feedTab == "Following" -> "No posts from followed accounts yet"
                      selectedMood != null -> "No posts in ${selectedMood?.title} yet"
                      else -> "Welcome to VYBE"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = when {
                      feedTab == "Following" -> "Follow creators in Explore to see their latest posts and stories here."
                      selectedMood != null -> "Be the first to share a moment in the ${selectedMood?.title} zone."
                      else -> "Your feed is ready. Share a post, video, or story with the community."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                  )
                  Spacer(modifier = Modifier.height(16.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                      onClick = { viewModel.navigateTo(Screen.CREATE_POST) },
                      colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                      Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Create Post", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                      onClick = { showStoryCreateSheet = true },
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier.heightIn(min = 48.dp)
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
            Box(
              modifier = Modifier.fillMaxWidth(),
              contentAlignment = Alignment.Center
            ) {
              PostCard(
                post = post,
                onLikeClick = { viewModel.toggleLike(post.id) },
                onSaveClick = { viewModel.toggleSave(post.id) },
                onCommentClick = { viewModel.loadCommentsForPost(post.id) },
                onAddComment = { text -> viewModel.addComment(post.id, text) },
                onLoadComments = { viewModel.loadCommentsForPost(post.id) },
                onShareClick = {
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                      Intent.EXTRA_TEXT,
                      "${post.author.displayName} (@${post.author.username}) on VYBE:\n${post.caption}"
                    )
                  }
                  context.startActivity(Intent.createChooser(shareIntent, "Share VYBE Post"))
                },
                onUserClick = { user -> viewModel.openUserProfile(user) },
                onRemixClick = { viewModel.navigateTo(Screen.REELS_VIEWER) },
                onDeleteClick = { viewModel.deletePost(post.id) },
                onReportPost = { reason ->
                  coroutineScope.launch {
                    viewModel.submitReport("POST", post.id, reason)
                    viewModel.postBannerMessage("Report submitted for review.")
                  }
                },
                onPostViewed = { viewModel.recordPostView(it) },
                currentUserId = currentUser?.id,
                shouldAutoPlay = (post.id == activeAutoPlayPostId),
                modifier = Modifier.widthIn(max = 600.dp)
              )
            }
          }
        }

        if (isLoadingMorePosts) {
          item(key = "feed_pagination_loader") {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
              contentAlignment = Alignment.Center
            ) {
              CircularProgressIndicator(
                color = VybeViolet,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
              )
            }
          }
        }

        item(key = "feed_bottom_spacer") {
          Spacer(modifier = Modifier.height(84.dp))
        }
      }

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
              text = "Syncing latest VYBE feed...",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

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

  if (showVybeMatchesDialog && currentUser != null) {
    VybeMatchesManagerDialog(
      currentUserId = currentUser!!.id,
      allUsers = allUsers,
      selectedMatches = vybeMatches,
      onAddMatch = { viewModel.addVybeMatch(it) },
      onRemoveMatch = { viewModel.removeVybeMatch(it) },
      onDismiss = { showVybeMatchesDialog = false }
    )
  }
}

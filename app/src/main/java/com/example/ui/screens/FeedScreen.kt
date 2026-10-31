package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.data.model.belongsToMainFeed
import com.example.data.model.matchesMoodFilter
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import com.example.ui.components.PostCard
import com.example.ui.components.StoryCarousel
import com.example.ui.components.StoryCreateDialog
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeMatchesManagerDialog
import com.example.ui.components.VybeMoodOutlineIcon
import com.example.ui.theme.*
import com.example.util.FeedAudioController
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
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()
  val unloadRooms by viewModel.unloadRooms.collectAsState()
  val liveStreams by viewModel.liveStreams.collectAsState()
  val hasMoreFeedPosts by viewModel.hasMoreFeedPosts.collectAsState()
  val isLoadingMorePosts by viewModel.isLoadingMorePosts.collectAsState()
  val isFeedAudioEnabled by viewModel.isFeedAudioEnabled.collectAsState()
  val targetFeedPostId by viewModel.targetFeedPostId.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  var feedTab by remember { mutableStateOf("For You") }
  val feedTabs = listOf("For You", "Following", "Discover")

  var isRefreshing by remember { mutableStateOf(false) }
  var showStoryCreateSheet by remember { mutableStateOf(false) }
  var showVybeMatchesDialog by remember { mutableStateOf(false) }
  var storyCreatedFeedback by remember { mutableStateOf<String?>(null) }
  var highlightedPostId by remember { mutableStateOf<String?>(null) }

  val filteredPosts = remember(posts, selectedMood, feedTab, followingUserIds, currentUser?.id) {
    var list = posts.filter { it.belongsToMainFeed() && it.matchesMoodFilter(selectedMood) }
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

  val audioCapablePostIds = remember(filteredPosts, videoPostIds) {
    val musicPostIds = filteredPosts
      .filter { !it.audioTrackTitle.isNullOrBlank() }
      .map { it.id }
      .toSet()
    videoPostIds + musicPostIds
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

  // Scroll directly to the target post when opened from Notification Center
  LaunchedEffect(targetFeedPostId, filteredPosts.size) {
    val targetId = targetFeedPostId ?: return@LaunchedEffect
    if (feedTab != "For You") feedTab = "For You"
    if (selectedMood != null) viewModel.selectMoodFilter(null)
    val postIdx = filteredPosts.indexOfFirst { it.id == targetId }
    if (postIdx >= 0) {
      val headerItemsCount = 3 + if (liveStreams.any { it.isLive }) 1 else 0
      listState.animateScrollToItem((headerItemsCount + postIdx).coerceAtLeast(0))
      highlightedPostId = targetId
      viewModel.clearTargetFeedPostId()
      delay(3500L)
      if (highlightedPostId == targetId) {
        highlightedPostId = null
      }
    }
  }

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

  // Determine the single most-visible audio-capable post (video or music track) so audio stays enabled
  // across feed items while scrolling without EVER overlapping two items!
  val activeAudioPostId by remember(listState, audioCapablePostIds) {
    derivedStateOf {
      if (audioCapablePostIds.isEmpty()) return@derivedStateOf null
      val layoutInfo = listState.layoutInfo
      val viewportStart = layoutInfo.viewportStartOffset
      val viewportEnd = layoutInfo.viewportEndOffset
      if (viewportEnd <= viewportStart) return@derivedStateOf null

      layoutInfo.visibleItemsInfo
        .mapNotNull { itemInfo ->
          val keyStr = itemInfo.key as? String ?: return@mapNotNull null
          if (!audioCapablePostIds.contains(keyStr) || itemInfo.size <= 0) return@mapNotNull null
          val visibleStart = maxOf(itemInfo.offset, viewportStart)
          val visibleEnd = minOf(itemInfo.offset + itemInfo.size, viewportEnd)
          val visiblePx = (visibleEnd - visibleStart).coerceAtLeast(0)
          val fraction = visiblePx.toFloat() / itemInfo.size.toFloat()
          if (fraction >= 0.50f) keyStr to fraction else null
        }
        .maxByOrNull { it.second }
        ?.first
    }
  }

  LaunchedEffect(isFeedAudioEnabled, activeAudioPostId) {
    if (!isFeedAudioEnabled || activeAudioPostId == null) {
      FeedAudioController.stopAllFeedAudio()
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
    viewModel.refreshAllData()
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
          // 0. Spiritual Mode Sanctuary shortcut
          IconButton(
            onClick = { viewModel.navigateTo(Screen.SPIRITUAL_MODE) },
            modifier = Modifier
              .size(44.dp)
              .testTag("header_spiritual_mode_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.Spa,
              contentDescription = "Spiritual Sanctuary & Breathwork",
              tint = Color(0xFF6B8E78),
              modifier = Modifier.size(24.dp)
            )
          }

          // 1. Search control
          IconButton(
            onClick = { viewModel.selectTab(MainTab.EXPLORE) },
            modifier = Modifier
              .size(44.dp)
              .testTag("header_search_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = "Search",
              tint = MaterialTheme.colorScheme.onBackground,
              modifier = Modifier.size(24.dp)
            )
          }

          // 2. Notification Center with unread indicator dot
          val unreadNotifsCount = remember(notifications) { notifications.count { !it.isRead } }
          IconButton(
            onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
            modifier = Modifier
              .size(44.dp)
              .testTag("notification_bell_button")
          ) {
            BadgedBox(
              badge = {
                if (unreadNotifsCount > 0) {
                  Box(
                    modifier = Modifier
                      .size(8.dp)
                      .clip(CircleShape)
                      .background(VybeBrandCyan)
                  )
                }
              }
            ) {
              Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
              )
            }
          }

          // 3. Functional Inbox / Messages
          val conversations by viewModel.conversations.collectAsState()
          val unreadMessagesTotal = remember(conversations) { conversations.sumOf { it.unreadCount } }
          IconButton(
            onClick = { viewModel.selectTab(MainTab.INBOX) },
            modifier = Modifier
              .size(44.dp)
              .testTag("header_inbox_button")
          ) {
            BadgedBox(
              badge = {
                if (unreadMessagesTotal > 0) {
                  Badge(containerColor = VybeMagenta) {
                    Text("$unreadMessagesTotal", fontSize = 10.sp)
                  }
                }
              }
            ) {
              Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Inbox Messages",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(23.dp)
              )
            }
          }

          // 4. User profile avatar (single clean border via VybeAvatar, 48dp touch target)
          if (currentUser != null) {
            Box(
              modifier = Modifier
                .padding(end = 6.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable { viewModel.selectTab(MainTab.PROFILE) }
                .testTag("profile_avatar_top_button"),
              contentAlignment = Alignment.Center
            ) {
              VybeAvatar(user = currentUser!!, size = 34.dp, showPulseDot = false)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
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
        // Stories Tray (Pure genuine registered user stories)
        item(key = "stories_tray") {
          StoryCarousel(
            stories = stories,
            currentUser = currentUser,
            onStoryClick = { story -> viewModel.openStory(story) },
            onAddStoryClick = { showStoryCreateSheet = true },
            onManageVybeMatchesClick = { showVybeMatchesDialog = true },
            vybeMatchesCount = vybeMatches.size,
            activeLiveRooms = unloadRooms.filter { it.isActive },
            onJoinLiveRoom = { room -> viewModel.joinUnloadRoom(room) },
            activeLiveStreams = liveStreams.filter { it.isLive },
            onJoinLiveStream = { stream -> viewModel.joinLiveBroadcast(stream) }
          )
        }

        val activeStreams = liveStreams.filter { it.isLive }
        if (activeStreams.isNotEmpty()) {
          item(key = "feed_live_zone_strip") {
            LazyRow(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp)
                .testTag("feed_live_streams_row"),
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              items(activeStreams, key = { "feed_live_${it.id}" }) { stream ->
                Surface(
                  color = VybeDanger.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(16.dp),
                  border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.6f)),
                  modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.joinLiveBroadcast(stream) }
                    .testTag("feed_live_pill_${stream.id}")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    VybeAvatar(user = stream.host, size = 24.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "${stream.host.displayName}: ${stream.title}",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(color = VybeDanger, shape = RoundedCornerShape(6.dp)) {
                      Text(
                        text = "LIVE • ${stream.viewerCount}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                      )
                    }
                  }
                }
              }
            }
          }
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
                  VybeMoodOutlineIcon(
                    mood = mood,
                    size = 14.dp,
                    tint = if (isSelected) mood.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.width(6.dp))
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
                onDoubleTapLike = { viewModel.ensurePostLoved(post.id) },
                onReact = { rx -> viewModel.reactToPost(post.id, rx) },
                onSaveClick = { viewModel.toggleSave(post.id) },
                onCommentClick = { viewModel.loadCommentsForPost(post.id) },
                onAddComment = { text -> viewModel.addComment(post.id, text) },
                onLoadComments = { viewModel.loadCommentsForPost(post.id) },
                onShareClick = { viewModel.openPostShareSheet(post) },
                onUserClick = { user -> viewModel.openUserProfile(user) },
                onRemixClick = { viewModel.openReelViewer(post.id) },
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
                isAudioEnabled = isFeedAudioEnabled,
                shouldPlayAudio = (isFeedAudioEnabled && post.id == activeAudioPostId),
                isHighlighted = (post.id == highlightedPostId),
                isFollowing = followingUserIds.contains(post.author.id),
                isFollowRequested = requestedFollowUserIds.contains(post.author.id) || post.author.isFollowRequestedByMe,
                followsMe = post.author.followsMe,
                onFollowClick = if (post.author.id != currentUser?.id) {
                  { viewModel.toggleFollowUser(post.author.id) }
                } else {
                  null
                },
                onToggleAudio = { enabled -> viewModel.setFeedAudioEnabled(enabled) },
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

package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeBrandHeaderLogo
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeNotificationBellButton
import com.example.ui.components.VybeSearchBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
  viewModel: VybeViewModel,
  showTopBrandRow: Boolean = true
) {
  val palette = LocalVybePalette.current
  val searchQuery by viewModel.searchQuery.collectAsState()
  val searchHistory by viewModel.searchHistory.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val unreadNotificationsCount = remember(notifications) { notifications.count { !it.isRead } }

  val filterPills = listOf("For You", "Trending", "People", "Nearby", "Interests")
  var selectedPill by remember { mutableStateOf("For You") }

  val searchTabs = listOf("All", "People", "Hashtags", "Posts", "Reels")
  var selectedSearchTab by remember { mutableStateOf("All") }

  var nearbyLocationGranted by remember { mutableStateOf(false) }
  var selectedCityFilter by remember { mutableStateOf("All India") }
  val indianCities = listOf("All India", "Mumbai", "Bengaluru", "Delhi NCR", "Goa", "Hyderabad", "Kolkata", "Pune")

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    nearbyLocationGranted = perms.values.any { it }
  }

  val interestCategories = listOf("Travel", "Tech", "Music", "Fitness", "Lifestyle", "Design", "Food")
  var selectedInterest by remember { mutableStateOf("Travel") }

  val nearbyPosts = remember(posts, selectedCityFilter) {
    if (selectedCityFilter == "All India") {
      posts.filter { !it.isArchived && !it.locationName.isNullOrBlank() }
    } else {
      posts.filter { !it.isArchived && it.locationName?.contains(selectedCityFilter, ignoreCase = true) == true }
    }
  }

  val interestPosts = remember(posts, selectedInterest) {
    posts.filter { p ->
      !p.isArchived && (
        p.caption.contains(selectedInterest, ignoreCase = true) ||
          p.tags.any { it.contains(selectedInterest, ignoreCase = true) }
        )
    }
  }

  val trendingHashtags = remember(posts) {
    val regex = Regex("#([A-Za-z0-9_]{2,30})")
    val counts = posts
      .asSequence()
      .filter { !it.isArchived }
      .flatMap { post ->
        val fromCaption = regex.findAll(post.caption).map { it.groupValues[1] }.toList()
        val fromList = post.tags.map { it.removePrefix("#").trim() }.filter { it.isNotBlank() }
        (fromCaption + fromList).asSequence()
      }
      .groupingBy { it }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .toList()

    if (counts.isNotEmpty()) {
      counts.take(8).map { entry ->
        val samplePost = posts.firstOrNull { it.caption.contains("#${entry.key}", ignoreCase = true) || it.tags.any { t -> t.contains(entry.key, ignoreCase = true) } }
        Triple(entry.key, entry.value, samplePost?.mediaUrls?.firstOrNull().orEmpty())
      }
    } else {
      emptyList()
    }
  }

  val featuredCreators = remember(allUsers, currentUser?.id) {
    allUsers
      .filter { it.id != currentUser?.id && !it.isBanned && !it.isDeactivated }
      .sortedByDescending { it.followersCount }
  }

  val recommendedPosts = remember(posts, selectedPill) {
    val activePosts = posts.filter { !it.isArchived }
    when (selectedPill) {
      "Trending" -> activePosts.sortedByDescending { it.likesCount + it.commentsCount }
      else -> activePosts.shuffled().take(12)
    }
  }

  val isSearching = searchQuery.isNotBlank()
  val cleanQuery = remember(searchQuery) {
    searchQuery.trim().lowercase().removePrefix("@").removePrefix("#")
  }

  val matchedUsers = remember(cleanQuery, allUsers) {
    if (cleanQuery.isBlank()) emptyList() else {
      allUsers.filter {
        it.username.lowercase().contains(cleanQuery) ||
          it.displayName.lowercase().contains(cleanQuery) ||
          it.publicVybeId.lowercase().contains(cleanQuery)
      }
    }
  }

  val matchedPosts = remember(cleanQuery, posts) {
    if (cleanQuery.isBlank()) emptyList() else {
      posts.filter { p ->
        !p.isArchived && (
          p.caption.lowercase().contains(cleanQuery) ||
            p.tags.any { it.lowercase().contains(cleanQuery) } ||
            p.locationName?.lowercase()?.contains(cleanQuery) == true
          )
      }
    }
  }

  val matchedReels = remember(matchedPosts) {
    matchedPosts.filter { it.mediaType == MediaType.REEL || it.mediaType == MediaType.VIDEO || it.isVybesReel }
  }

  val matchedHashtags = remember(cleanQuery, trendingHashtags) {
    if (cleanQuery.isBlank()) emptyList() else {
      trendingHashtags.filter { it.first.lowercase().contains(cleanQuery) }
    }
  }

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      Surface(
        color = palette.canvas,
        modifier = Modifier
          .fillMaxWidth()
          .let { if (showTopBrandRow) it.statusBarsPadding() else it }
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              if (showTopBrandRow) {
                VybeBrandHeaderLogo()
                Spacer(modifier = Modifier.height(2.dp))
              }
              Text(
                text = "Explore Zone",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
              )
              Text(
                text = "Discover creators, moments & spaces",
                fontSize = 11.sp,
                color = palette.textSecondary
              )
            }

            if (showTopBrandRow) {
              VybeNotificationBellButton(
                unreadCount = unreadNotificationsCount,
                onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
                testTag = "explore_notification_bell"
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // 6 Signature Experiences Quick Access Grid
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("explore_six_features_header_grid"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              ExploreSignatureChip(
                icon = Icons.Outlined.GraphicEq,
                label = "UNLOAD",
                palette = palette,
                testTag = "explore_header_feature_unload",
                onClick = { viewModel.navigateTo(Screen.UNLOAD_VIEW) },
                modifier = Modifier.weight(1f)
              )
              ExploreSignatureChip(
                icon = Icons.Outlined.AutoAwesome,
                label = "UNFILTERED",
                palette = palette,
                testTag = "explore_header_feature_unfiltered",
                onClick = { viewModel.navigateTo(Screen.UNFILTERED_VIEW) },
                modifier = Modifier.weight(1f)
              )
              ExploreSignatureChip(
                icon = Icons.Outlined.Psychology,
                label = "WHAT NEXT",
                palette = palette,
                testTag = "explore_header_feature_what_next",
                onClick = { viewModel.navigateTo(Screen.WHAT_NEXT_VIEW) },
                modifier = Modifier.weight(1f)
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              ExploreSignatureChip(
                icon = Icons.Outlined.HourglassEmpty,
                label = "TIME CAPSULE",
                palette = palette,
                testTag = "explore_header_feature_time_capsule",
                onClick = { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) },
                modifier = Modifier.weight(1f)
              )
              ExploreSignatureChip(
                icon = Icons.Outlined.CameraAlt,
                label = "DOUBLE TAKE",
                palette = palette,
                testTag = "explore_header_feature_double_take",
                onClick = { viewModel.navigateTo(Screen.DOUBLE_TAKE_VIEW) },
                modifier = Modifier.weight(1f)
              )
              ExploreSignatureChip(
                icon = Icons.Outlined.Explore,
                label = "CROSSED PATHS",
                palette = palette,
                testTag = "explore_header_feature_crossed_paths",
                onClick = { viewModel.selectTab(MainTab.CROSSED_PATHS) },
                modifier = Modifier.weight(1f)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Filter Pills Row
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(filterPills) { pill ->
              val isSelected = selectedPill == pill
              Surface(
                color = if (isSelected) palette.accent else palette.elevated,
                shape = RoundedCornerShape(VybeTokens.Radius.pill),
                border = if (isSelected) null else BorderStroke(1.dp, palette.border),
                modifier = Modifier
                  .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                  .clickable {
                    selectedPill = pill
                    if (isSearching) viewModel.setSearchQuery("")
                  }
                  .testTag("explore_pill_$pill")
              ) {
                Text(
                  text = pill,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Smart Search Bar
          VybeSearchBar(
            query = searchQuery,
            onQueryChange = {
              viewModel.setSearchQuery(it)
              if (it.isNotBlank()) viewModel.addSearchQueryToHistory(it)
            },
            placeholder = "Search users, hashtags, or posts...",
            testTag = "explore_smart_search_input"
          )
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

      if (isSearching) {
        item(key = "search_category_tabs") {
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(searchTabs) { tab ->
              val isSelected = selectedSearchTab == tab
              Surface(
                color = if (isSelected) palette.accent else palette.elevated,
                shape = RoundedCornerShape(VybeTokens.Radius.pill),
                border = if (isSelected) null else BorderStroke(1.dp, palette.border),
                modifier = Modifier
                  .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                  .clickable { selectedSearchTab = tab }
              ) {
                Text(
                  text = tab,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
            }
          }
        }

        if (searchHistory.isNotEmpty()) {
          item(key = "search_history_bar") {
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Recent Searches", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Text(
                  text = "Clear All",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.accent,
                  modifier = Modifier.clickable { viewModel.clearSearchHistory() }
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(searchHistory) { historyItem ->
                  Surface(
                    color = palette.elevated,
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.clickable { viewModel.setSearchQuery(historyItem) }
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(text = historyItem, fontSize = 11.sp, color = palette.textPrimary)
                      Spacer(modifier = Modifier.width(6.dp))
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = palette.textMuted,
                        modifier = Modifier
                          .size(12.dp)
                          .clickable { viewModel.removeSearchQueryFromHistory(historyItem) }
                      )
                    }
                  }
                }
              }
            }
          }
        }

        if (selectedSearchTab == "All" || selectedSearchTab == "People") {
          if (matchedUsers.isNotEmpty()) {
            item(key = "matched_users_header") {
              Text("People", fontFamily = PlusJakartaSansFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
            items(matchedUsers.take(6), key = { "search_u_${it.id}" }) { user ->
              val isFollowing = followingUserIds.contains(user.id)
              val isRequested = requestedFollowUserIds.contains(user.id)
              VybeSurfaceCard(
                onClick = { viewModel.openUserProfile(user) },
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    VybeAvatar(user = user, size = 42.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (user.isVerified) {
                          Spacer(modifier = Modifier.width(4.dp))
                          VerificationBadge(user = user, iconSize = 14.dp)
                        }
                      }
                      Text("@${user.username} • ${user.followersCount} followers", fontSize = 11.sp, color = palette.textSecondary)
                    }
                  }
                  if (user.id != currentUser?.id) {
                    Surface(
                      color = if (isFollowing) palette.elevated else palette.accent,
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      border = if (isFollowing) BorderStroke(1.dp, palette.border) else null,
                      modifier = Modifier
                        .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                        .clickable { viewModel.toggleFollowUser(user.id) }
                    ) {
                      Text(
                        text = if (isFollowing) "LINKED" else if (isRequested) "Requested" else "Follow",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFollowing) palette.textSecondary else Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        if (selectedSearchTab == "All" || selectedSearchTab == "Hashtags") {
          if (matchedHashtags.isNotEmpty()) {
            item(key = "matched_tags_header") {
              Text("Hashtags", fontFamily = PlusJakartaSansFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
            items(matchedHashtags, key = { "tag_${it.first}" }) { (tag, count, _) ->
              VybeSurfaceCard(
                onClick = { viewModel.setSearchQuery("#$tag") },
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(palette.accentSecondary.copy(alpha = 0.14f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Text("#", fontWeight = FontWeight.Bold, color = palette.accentSecondary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text("#$tag", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                      Text("$count posts", fontSize = 11.sp, color = palette.textSecondary)
                    }
                  }
                  Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(14.dp))
                }
              }
            }
          }
        }

        if (selectedSearchTab == "All" || selectedSearchTab == "Posts" || selectedSearchTab == "Reels") {
          val postList = if (selectedSearchTab == "Reels") matchedReels else matchedPosts
          if (postList.isNotEmpty()) {
            item(key = "matched_posts_header") {
              Text(if (selectedSearchTab == "Reels") "Reels" else "Posts", fontFamily = PlusJakartaSansFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
            items(postList, key = { "search_p_${it.id}" }) { post ->
              VybeSurfaceCard(
                onClick = {
                  if (post.mediaType == MediaType.REEL || post.isVybesReel) {
                    viewModel.openReelViewer(post.id)
                  } else {
                    viewModel.openPostFromNotification(post.id)
                  }
                },
                contentPadding = PaddingValues(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                  val mediaUrl = post.mediaUrls.firstOrNull().orEmpty()
                  if (mediaUrl.isNotBlank()) {
                    AsyncImage(
                      model = mediaUrl,
                      contentDescription = post.caption,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = post.caption.ifBlank { "Post by @${post.author.username}" },
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                      color = palette.textPrimary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("@${post.author.username} • ${post.likesCount} likes", fontSize = 10.sp, color = palette.textSecondary)
                  }
                }
              }
            }
          }
        }

        if (matchedUsers.isEmpty() && matchedPosts.isEmpty() && matchedHashtags.isEmpty()) {
          item(key = "search_empty_state") {
            VybeEmptyState(
              icon = Icons.Default.SearchOff,
              title = "No results found for \"$searchQuery\"",
              subtitle = "Try searching for a different username, display name, or #hashtag."
            )
          }
        }
      } else {
        when (selectedPill) {
          "For You", "Trending" -> {
            item(key = "landing_trending_topics") {
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Trending Topics",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectedPill = "Trending" }
                  ) {
                    Text(
                      text = "See All",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = palette.accent
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                      imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                      contentDescription = null,
                      tint = palette.accent,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (trendingHashtags.isNotEmpty()) {
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(trendingHashtags) { (tag, count, thumbnail) ->
                      Surface(
                        color = palette.elevated,
                        shape = RoundedCornerShape(VybeTokens.Radius.md),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                          .width(138.dp)
                          .height(86.dp)
                          .clip(RoundedCornerShape(VybeTokens.Radius.md))
                          .clickable { viewModel.setSearchQuery("#$tag") }
                      ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                          if (thumbnail.isNotBlank()) {
                            AsyncImage(
                              model = thumbnail,
                              contentDescription = tag,
                              contentScale = ContentScale.Crop,
                              modifier = Modifier.fillMaxSize()
                            )
                          } else {
                            Box(
                              modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlue)))
                            )
                          }
                          Box(
                            modifier = Modifier
                              .fillMaxSize()
                              .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                          )
                          Column(
                            modifier = Modifier
                              .align(Alignment.BottomStart)
                              .padding(8.dp)
                          ) {
                            Text("#$tag", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count posts", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                          }
                        }
                      }
                    }
                  }
                } else {
                  VybeSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                      text = "No trending topics yet. Start a conversation and make something trend.",
                      fontSize = 12.sp,
                      color = palette.textSecondary
                    )
                  }
                }
              }
            }

            item(key = "landing_featured_creators") {
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Featured Creators",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectedPill = "People" }
                  ) {
                    Text(
                      text = "See All",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = palette.accent
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                      imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                      contentDescription = null,
                      tint = palette.accent,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  items(featuredCreators.take(8), key = { "feat_c_${it.id}" }) { creator ->
                    val isFollowing = followingUserIds.contains(creator.id)
                    val isRequested = requestedFollowUserIds.contains(creator.id)
                    Surface(
                      color = palette.elevated,
                      shape = RoundedCornerShape(VybeTokens.Radius.lg),
                      border = BorderStroke(1.dp, palette.border),
                      modifier = Modifier
                        .width(134.dp)
                        .height(192.dp)
                        .clip(RoundedCornerShape(VybeTokens.Radius.lg))
                        .clickable { viewModel.openUserProfile(creator) }
                    ) {
                      Box(modifier = Modifier.fillMaxSize()) {
                        if (creator.avatarUrl.isNotBlank()) {
                          AsyncImage(
                            model = creator.avatarUrl,
                            contentDescription = creator.username,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                          )
                        } else {
                          Box(
                            modifier = Modifier
                              .fillMaxSize()
                              .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlue)))
                          )
                        }
                        Box(
                          modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                        )
                        Column(
                          modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                          horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("@${creator.username}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (creator.isVerified) VerificationBadge(user = creator, iconSize = 12.dp)
                          }
                          Text("${creator.followersCount} Followers", color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp)
                          Spacer(modifier = Modifier.height(8.dp))
                          Button(
                            onClick = { viewModel.toggleFollowUser(creator.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isFollowing) Color.White.copy(alpha = 0.2f) else palette.accent),
                            shape = RoundedCornerShape(VybeTokens.Radius.pill),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                              .fillMaxWidth()
                              .height(32.dp)
                          ) {
                            Text(
                              text = if (isFollowing) "LINKED" else if (isRequested) "Requested" else "Follow",
                              fontSize = 10.sp,
                              fontWeight = FontWeight.Bold,
                              color = Color.White
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

            item(key = "landing_recommended_header") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Recommended for You",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.textPrimary
                )
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.clickable { viewModel.refreshAllData() }
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Refresh",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                  )
                }
              }
            }

            items(recommendedPosts.chunked(2), key = { chunk -> "rec_row_${chunk.first().id}" }) { pair ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                pair.forEach { post ->
                  Surface(
                    color = palette.elevated,
                    shape = RoundedCornerShape(VybeTokens.Radius.md),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                      .weight(1f)
                      .height(180.dp)
                      .clip(RoundedCornerShape(VybeTokens.Radius.md))
                      .clickable {
                        if (post.mediaType == MediaType.REEL || post.isVybesReel) {
                          viewModel.openReelViewer(post.id)
                        } else {
                          viewModel.openPostFromNotification(post.id)
                        }
                      }
                  ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                      val mediaUrl = post.mediaUrls.firstOrNull().orEmpty()
                      if (mediaUrl.isNotBlank()) {
                        AsyncImage(
                          model = mediaUrl,
                          contentDescription = post.caption,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.fillMaxSize()
                        )
                      }
                      Box(
                        modifier = Modifier
                          .fillMaxSize()
                          .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                      )
                      IconButton(
                        onClick = { viewModel.toggleSave(post.id) },
                        modifier = Modifier
                          .align(Alignment.TopEnd)
                          .size(36.dp)
                      ) {
                        Icon(
                          imageVector = if (post.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                          contentDescription = "Save",
                          tint = Color.White,
                          modifier = Modifier.size(16.dp)
                        )
                      }
                      Row(
                        modifier = Modifier
                          .align(Alignment.BottomStart)
                          .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                      ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(Icons.Default.Favorite, contentDescription = "Likes", tint = Color.White, modifier = Modifier.size(12.dp))
                          Spacer(modifier = Modifier.width(3.dp))
                          Text("${post.likesCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comments", tint = Color.White, modifier = Modifier.size(12.dp))
                          Spacer(modifier = Modifier.width(3.dp))
                          Text("${post.commentsCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                    }
                  }
                }
                if (pair.size == 1) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }

            item(key = "landing_spiritual_gateway") {
              VybeSurfaceCard(
                onClick = { viewModel.navigateTo(Screen.SPIRITUAL_MODE) },
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(palette.success.copy(alpha = 0.14f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.SelfImprovement, contentDescription = null, tint = palette.success, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Text("Spiritual Mode", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                      Text("Enter a peaceful mindful zone without metrics", fontSize = 11.sp, color = palette.textSecondary)
                    }
                  }
                  Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = palette.success, modifier = Modifier.size(18.dp))
                }
              }
            }
          }

          "People" -> {
            item(key = "people_header") {
              Text("Discover Creators & People", fontFamily = PlusJakartaSansFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
            items(featuredCreators, key = { "p_creator_${it.id}" }) { user ->
              val isFollowing = followingUserIds.contains(user.id)
              val isRequested = requestedFollowUserIds.contains(user.id)
              VybeSurfaceCard(
                onClick = { viewModel.openUserProfile(user) },
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    VybeAvatar(user = user, size = 48.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                        if (user.isVerified) {
                          Spacer(modifier = Modifier.width(4.dp))
                          VerificationBadge(user = user, iconSize = 14.dp)
                        }
                      }
                      Text("@${user.username}", fontSize = 11.sp, color = palette.accent)
                      if (user.followsMe) {
                        Text("Linked with you", fontSize = 10.sp, color = palette.accentSecondary, fontWeight = FontWeight.SemiBold)
                      }
                    }
                  }
                  Button(
                    onClick = { viewModel.toggleFollowUser(user.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isFollowing) palette.elevated else palette.accent),
                    border = if (isFollowing) BorderStroke(1.dp, palette.border) else null,
                    shape = RoundedCornerShape(VybeTokens.Radius.md)
                  ) {
                    Text(
                      text = if (isFollowing) "LINKED" else if (isRequested) "Requested" else "Follow",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (isFollowing) palette.textSecondary else Color.White
                    )
                  }
                }
                if (user.bio.isNotBlank()) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(user.bio, fontSize = 12.sp, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (user.interestTags.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    user.interestTags.take(3).forEach { tag ->
                      Surface(color = palette.elevated, shape = RoundedCornerShape(VybeTokens.Radius.xs)) {
                        Text("#$tag", fontSize = 10.sp, color = palette.textSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                      }
                    }
                  }
                }
              }
            }
          }

          "Nearby" -> {
            item(key = "nearby_header") {
              Column(modifier = Modifier.fillMaxWidth()) {
                VybeSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(palette.accentSecondary.copy(alpha = 0.14f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.LocationOn, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text("Nearby Safe Discovery", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                      Text("Explore public creators and moments around you. Exact GPS is never shared.", fontSize = 11.sp, color = palette.textSecondary)
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Select Location Area", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                  if (!nearbyLocationGranted) {
                    Text(
                      text = "Enable GPS",
                      fontSize = 11.sp,
                      color = palette.accentSecondary,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.clickable {
                        locationPermissionLauncher.launch(
                          arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                      }
                    )
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(indianCities) { city ->
                    val isCitySelected = selectedCityFilter == city
                    Surface(
                      color = if (isCitySelected) palette.accentSecondary else palette.elevated,
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      border = if (isCitySelected) null else BorderStroke(1.dp, palette.border),
                      modifier = Modifier
                        .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                        .clickable { selectedCityFilter = city }
                    ) {
                      Text(
                        text = city,
                        fontSize = 11.sp,
                        fontWeight = if (isCitySelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCitySelected) Color.White else palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                      )
                    }
                  }
                }
              }
            }

            if (nearbyPosts.isEmpty()) {
              item(key = "nearby_empty") {
                VybeEmptyState(
                  icon = Icons.Default.Place,
                  title = "No public moments in $selectedCityFilter yet",
                  subtitle = "Be the first to share a moment with a location tag in $selectedCityFilter."
                )
              }
            } else {
              items(nearbyPosts, key = { "nearby_p_${it.id}" }) { post ->
                VybeSurfaceCard(
                  onClick = { viewModel.openPostFromNotification(post.id) },
                  contentPadding = PaddingValues(12.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val mediaUrl = post.mediaUrls.firstOrNull().orEmpty()
                    if (mediaUrl.isNotBlank()) {
                      AsyncImage(
                        model = mediaUrl,
                        contentDescription = post.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(54.dp)
                          .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(post.locationName ?: "India", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.accentSecondary)
                      }
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(post.caption, fontSize = 12.sp, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("Shared by @${post.author.username}", fontSize = 10.sp, color = palette.textSecondary)
                    }
                  }
                }
              }
            }
          }

          "Interests" -> {
            item(key = "interests_header") {
              Column(modifier = Modifier.fillMaxWidth()) {
                Text("Browse by Interest", fontFamily = PlusJakartaSansFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(interestCategories) { cat ->
                    val isCatSelected = selectedInterest == cat
                    Surface(
                      color = if (isCatSelected) palette.accent else palette.elevated,
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      border = if (isCatSelected) null else BorderStroke(1.dp, palette.border),
                      modifier = Modifier
                        .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                        .clickable { selectedInterest = cat }
                    ) {
                      Text(
                        text = cat,
                        fontSize = 11.sp,
                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCatSelected) Color.White else palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                      )
                    }
                  }
                }
              }
            }

            if (interestPosts.isEmpty()) {
              item(key = "interest_empty") {
                VybeEmptyState(
                  icon = Icons.Default.Tag,
                  title = "No moments in #$selectedInterest yet",
                  subtitle = "Tag your next post with #$selectedInterest to appear here."
                )
              }
            } else {
              items(interestPosts, key = { "int_p_${it.id}" }) { post ->
                VybeSurfaceCard(
                  onClick = { viewModel.openPostFromNotification(post.id) },
                  contentPadding = PaddingValues(12.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val mediaUrl = post.mediaUrls.firstOrNull().orEmpty()
                    if (mediaUrl.isNotBlank()) {
                      AsyncImage(
                        model = mediaUrl,
                        contentDescription = post.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(54.dp)
                          .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(post.caption, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Spacer(modifier = Modifier.height(2.dp))
                      Text("@${post.author.username} • ${post.likesCount} likes", fontSize = 10.sp, color = palette.textSecondary)
                    }
                  }
                }
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun ExploreSignatureChip(
  icon: ImageVector,
  label: String,
  palette: VybePalette,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .clip(RoundedCornerShape(VybeTokens.Radius.md))
      .clickable(onClick = onClick)
      .testTag(testTag),
    color = palette.elevated,
    shape = RoundedCornerShape(VybeTokens.Radius.md),
    border = BorderStroke(1.dp, palette.border)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = palette.accent,
        modifier = Modifier.size(14.dp)
      )
      Text(
        text = label,
        fontFamily = PlusJakartaSansFontFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

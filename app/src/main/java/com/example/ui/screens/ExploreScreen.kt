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
import com.example.util.ProfileInputValidator

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
  val communities by viewModel.communities.collectAsState()
  val dismissedSuggestionIds by viewModel.dismissedSuggestionUserIds.collectAsState()
  val blockedUserIds by viewModel.blockedUserIds.collectAsState()
  val isSyncingFeed by viewModel.isSyncingFeed.collectAsState()
  val isSearchingExplore by viewModel.isSearchingExplore.collectAsState()
  val exploreSearchError by viewModel.exploreSearchError.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val unreadNotificationsCount = remember(notifications) { notifications.count { !it.isRead } }

  val filterPills = listOf("For You", "Trending", "People", "Nearby", "Circles", "Interests")
  var selectedPill by remember { mutableStateOf("For You") }

  val searchTabs = listOf("All", "People", "Hashtags", "Posts", "Circles")
  var selectedSearchTab by remember { mutableStateOf("All") }

  var nearbyLocationGranted by remember { mutableStateOf(false) }
  var showNearbyRationaleDialog by remember { mutableStateOf(false) }
  var selectedCityFilter by remember { mutableStateOf("All India") }
  val indianCities = listOf("All India", "Mumbai", "Bengaluru", "Delhi NCR", "Goa", "Hyderabad", "Kolkata", "Pune")

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    nearbyLocationGranted = perms.values.any { it }
  }

  LaunchedEffect(selectedPill, searchQuery) {
    if ((selectedPill == "Circles" || searchQuery.isNotBlank()) && communities.isEmpty()) {
      viewModel.fetchCommunities()
    }
  }

  val interestCategories = listOf("Travel", "Tech", "Music", "Fitness", "Lifestyle", "Design", "Food")
  var selectedInterest by remember { mutableStateOf("Travel") }

  val nearbyPosts = remember(posts, selectedCityFilter, blockedUserIds) {
    val active = posts.filter { !it.isArchived && !blockedUserIds.contains(it.author.id) }
    if (selectedCityFilter == "All India") {
      active.filter { !it.locationName.isNullOrBlank() }
    } else {
      active.filter { it.locationName?.contains(selectedCityFilter, ignoreCase = true) == true }
    }
  }

  val interestPosts = remember(posts, selectedInterest, blockedUserIds) {
    posts.filter { p ->
      !p.isArchived && !blockedUserIds.contains(p.author.id) && (
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

  val featuredCreators = remember(allUsers, currentUser?.id, dismissedSuggestionIds, blockedUserIds) {
    allUsers
      .filter {
        it.id != currentUser?.id &&
          !it.isBanned &&
          !it.isDeactivated &&
          !dismissedSuggestionIds.contains(it.id) &&
          !blockedUserIds.contains(it.id)
      }
      .sortedByDescending { it.followersCount }
  }

  val recommendedPosts = remember(posts, selectedPill, blockedUserIds) {
    val activePosts = posts.filter { !it.isArchived && !blockedUserIds.contains(it.author.id) }
    when (selectedPill) {
      "Trending" -> activePosts.sortedWith(
        compareByDescending<com.example.data.model.Post> { it.likesCount + it.commentsCount }
          .thenByDescending { com.example.util.TimestampUtils.parseIsoMillis(it.createdAtIso) ?: 0L }
          .thenBy { it.id }
      )
      else -> activePosts.sortedWith(
        compareByDescending<com.example.data.model.Post> {
          (com.example.util.TimestampUtils.parseIsoMillis(it.createdAtIso) ?: 0L) / 3_600_000L +
            it.likesCount * 2L + it.commentsCount * 3L
        }.thenBy { it.id }
      ).take(12)
    }
  }

  val isSearching = searchQuery.isNotBlank()
  val cleanQuery = remember(searchQuery) {
    searchQuery.trim().lowercase().removePrefix("@").removePrefix("#")
  }

  val matchedUsers = remember(cleanQuery, allUsers, blockedUserIds) {
    if (cleanQuery.isBlank()) emptyList() else {
      allUsers.filter {
        !it.isBanned &&
          !it.isDeactivated &&
          !blockedUserIds.contains(it.id) && (
          it.username.lowercase().contains(cleanQuery) ||
            it.displayName.lowercase().contains(cleanQuery) ||
            it.publicVybeId.lowercase().contains(cleanQuery)
          )
      }
    }
  }

  val matchedPosts = remember(cleanQuery, posts, blockedUserIds) {
    if (cleanQuery.isBlank()) emptyList() else {
      posts.filter { p ->
        !p.isArchived &&
          !blockedUserIds.contains(p.author.id) && (
          p.caption.lowercase().contains(cleanQuery) ||
            p.tags.any { it.lowercase().contains(cleanQuery) } ||
            p.locationName?.lowercase()?.contains(cleanQuery) == true ||
            p.author.username.lowercase().contains(cleanQuery)
          )
      }
    }
  }

  val matchedHashtags = remember(cleanQuery, trendingHashtags) {
    if (cleanQuery.isBlank()) emptyList() else {
      trendingHashtags.filter { it.first.lowercase().contains(cleanQuery) }
    }
  }

  val matchedCommunities = remember(cleanQuery, communities) {
    if (cleanQuery.isBlank()) emptyList() else {
      ProfileInputValidator.filterExploreCommunities(communities, cleanQuery)
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
                  .defaultMinSize(minHeight = 40.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                  .clickable {
                    selectedPill = pill
                    if (isSearching) viewModel.setSearchQuery("")
                  }
                  .testTag("explore_pill_$pill")
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = pill,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Smart Search Bar
          VybeSearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.setSearchQuery(it) },
            onSearch = { submitted ->
              if (submitted.trim().length >= 2) viewModel.addSearchQueryToHistory(submitted)
            },
            placeholder = "Search people, #tags, posts, or circles...",
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

      if (searchHistory.isNotEmpty()) {
        item(key = "search_history_bar") {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Recent Searches", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Text("Saved locally on this device", fontSize = 10.sp, color = palette.textMuted)
              }
              TextButton(
                onClick = { viewModel.clearSearchHistory() },
                modifier = Modifier
                  .defaultMinSize(minHeight = 48.dp)
                  .testTag("explore_clear_search_history")
              ) {
                Text(
                  text = "Clear All",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.accent
                )
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(searchHistory) { historyItem ->
                Surface(
                  color = palette.elevated,
                  shape = RoundedCornerShape(VybeTokens.Radius.sm),
                  border = BorderStroke(1.dp, palette.border),
                  modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                    .clickable {
                      viewModel.setSearchQuery(historyItem)
                      viewModel.addSearchQueryToHistory(historyItem)
                    }
                ) {
                  Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(text = historyItem, fontSize = 12.sp, color = palette.textPrimary)
                    IconButton(
                      onClick = { viewModel.removeSearchQueryFromHistory(historyItem) },
                      modifier = Modifier.size(36.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove $historyItem from search history",
                        tint = palette.textMuted,
                        modifier = Modifier.size(14.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

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
                  .defaultMinSize(minHeight = 40.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                  .clickable { selectedSearchTab = tab }
                  .testTag("explore_search_tab_$tab")
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = tab,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                  )
                }
              }
            }
          }
        }

        if (isSearchingExplore) {
          item(key = "search_loading_indicator") {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("explore_search_loading"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = palette.accent
              )
              Text(
                text = "Searching VYBE...",
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }
          }
        }

        if (!exploreSearchError.isNullOrBlank()) {
          item(key = "search_error_banner") {
            VybeSurfaceCard(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("explore_search_error")
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = exploreSearchError.orEmpty(),
                  fontSize = 12.sp,
                  color = palette.danger,
                  modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                  onClick = { viewModel.retryExploreSearch() },
                  modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("explore_search_retry_btn")
                ) {
                  Text("Retry", fontWeight = FontWeight.Bold, color = palette.accent)
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
            items(matchedUsers.take(8), key = { "search_u_${it.id}" }) { user ->
              val isFollowing = followingUserIds.contains(user.id)
              val isRequested = requestedFollowUserIds.contains(user.id)
              VybeSurfaceCard(
                onClick = {
                  viewModel.addSearchQueryToHistory("@${user.username}")
                  viewModel.openUserProfile(user)
                },
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
                        Spacer(modifier = Modifier.width(4.dp))
                        VerificationBadge(user = user, iconSize = 14.dp)
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
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                        .clickable { viewModel.toggleFollowUser(user.id) }
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(
                          text = if (isFollowing) "LINKED" else if (isRequested) "Requested" else "Follow",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = if (isFollowing) palette.textSecondary else Color.White,
                          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                      }
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
                onClick = {
                  viewModel.addSearchQueryToHistory("#$tag")
                  viewModel.setSearchQuery("#$tag")
                },
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

        if (selectedSearchTab == "All" || selectedSearchTab == "Posts") {
          val postList = matchedPosts
          if (postList.isNotEmpty()) {
            item(key = "matched_posts_header") {
              Text("Posts", fontFamily = PlusJakartaSansFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
            items(postList, key = { "search_p_${it.id}" }) { post ->
              VybeSurfaceCard(
                onClick = {
                  if (searchQuery.trim().length >= 2) {
                    viewModel.addSearchQueryToHistory(searchQuery.trim())
                  }
                  viewModel.openPostFromNotification(post.id)
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

        if (selectedSearchTab == "All" || selectedSearchTab == "Circles") {
          if (matchedCommunities.isNotEmpty()) {
            item(key = "matched_communities_header") {
              Text("Circles", fontFamily = PlusJakartaSansFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
            items(matchedCommunities, key = { "search_comm_${it.id}" }) { comm ->
              VybeSurfaceCard(
                onClick = {
                  if (searchQuery.trim().length >= 2) viewModel.addSearchQueryToHistory(searchQuery.trim())
                  viewModel.openCommunity(comm)
                },
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${comm.category} • ${comm.membersCount} members", fontSize = 11.sp, color = palette.textSecondary)
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Button(
                    onClick = { viewModel.toggleJoinCommunity(comm.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (comm.isJoined) palette.elevated else palette.accent),
                    border = if (comm.isJoined) BorderStroke(1.dp, palette.border) else null,
                    shape = RoundedCornerShape(VybeTokens.Radius.pill),
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                  ) {
                    Text(
                      text = if (comm.isJoined) "Joined" else "Join",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (comm.isJoined) palette.textSecondary else Color.White
                    )
                  }
                }
              }
            }
          }
        }

        val noResultsForTab = when (selectedSearchTab) {
          "People" -> matchedUsers.isEmpty()
          "Hashtags" -> matchedHashtags.isEmpty()
          "Posts" -> matchedPosts.isEmpty()
          "Circles" -> matchedCommunities.isEmpty()
          else -> matchedUsers.isEmpty() && matchedPosts.isEmpty() && matchedHashtags.isEmpty() && matchedCommunities.isEmpty()
        }

        if (noResultsForTab && !isSearchingExplore && exploreSearchError == null) {
          item(key = "search_empty_state") {
            VybeEmptyState(
              icon = Icons.Default.SearchOff,
              title = "No results found for \"$searchQuery\"",
              subtitle = "Try searching for a different username, #hashtag, post, or circle."
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
                    modifier = Modifier
                      .defaultMinSize(minHeight = 48.dp)
                      .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                      .clickable { selectedPill = "Trending" }
                      .padding(horizontal = 6.dp)
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
                    modifier = Modifier
                      .defaultMinSize(minHeight = 48.dp)
                      .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                      .clickable { selectedPill = "People" }
                      .padding(horizontal = 6.dp)
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

                if (featuredCreators.isNotEmpty()) {
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(featuredCreators.take(8), key = { "feat_c_${it.id}" }) { creator ->
                      val isFollowing = followingUserIds.contains(creator.id)
                      val isRequested = requestedFollowUserIds.contains(creator.id)
                      Surface(
                        color = palette.elevated,
                        shape = RoundedCornerShape(VybeTokens.Radius.lg),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                          .width(140.dp)
                          .height(198.dp)
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
                          IconButton(
                            onClick = { viewModel.dismissSuggestedUser(creator.id) },
                            modifier = Modifier
                              .align(Alignment.TopEnd)
                              .size(40.dp)
                          ) {
                            Box(
                              modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                              contentAlignment = Alignment.Center
                            ) {
                              Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss suggestion",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                              )
                            }
                          }
                          Column(
                            modifier = Modifier
                              .align(Alignment.BottomCenter)
                              .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                          ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                              Text("@${creator.username}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                              VerificationBadge(user = creator, iconSize = 12.dp)
                            }
                            Text("${creator.followersCount} Followers", color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                              onClick = { viewModel.toggleFollowUser(creator.id) },
                              colors = ButtonDefaults.buttonColors(containerColor = if (isFollowing) Color.White.copy(alpha = 0.2f) else palette.accent),
                              shape = RoundedCornerShape(VybeTokens.Radius.pill),
                              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                              modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 36.dp)
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
                } else if (isSyncingFeed) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                  }
                } else {
                  VybeSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                      text = "No creators discovered yet. Pull down or tap Refresh to sync.",
                      fontSize = 12.sp,
                      color = palette.textSecondary
                    )
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
                  modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                    .clickable { viewModel.refreshAllData() }
                    .padding(horizontal = 6.dp)
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

            if (recommendedPosts.isEmpty()) {
              item(key = "recommended_empty_or_loading") {
                if (isSyncingFeed) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.5.dp)
                  }
                } else {
                  VybeEmptyState(
                    icon = Icons.Outlined.Explore,
                    title = "No posts to explore yet",
                    subtitle = "Tap Refresh to fetch recent public moments from VYBE.",
                    actionLabel = "Refresh",
                    onAction = { viewModel.refreshAllData() }
                  )
                }
              }
            } else {
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
                        viewModel.openPostFromNotification(post.id)
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
                          .size(48.dp)
                      ) {
                        Icon(
                          imageVector = if (post.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                          contentDescription = "Save",
                          tint = Color.White,
                          modifier = Modifier.size(18.dp)
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
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Discover Creators & People", fontFamily = PlusJakartaSansFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                if (dismissedSuggestionIds.isNotEmpty()) {
                  TextButton(
                    onClick = { viewModel.restoreDismissedSuggestions() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                  ) {
                    Text("Show Hidden", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                  }
                }
              }
            }
            if (featuredCreators.isEmpty()) {
              item(key = "people_empty") {
                VybeEmptyState(
                  icon = Icons.Outlined.PeopleOutline,
                  title = "No people to show right now",
                  subtitle = if (dismissedSuggestionIds.isNotEmpty()) {
                    "You dismissed some suggestions. Restore them or refresh to see more creators."
                  } else {
                    "Tap Refresh to discover active creators on VYBE."
                  },
                  actionLabel = if (dismissedSuggestionIds.isNotEmpty()) "Restore Suggestions" else "Refresh",
                  onAction = {
                    if (dismissedSuggestionIds.isNotEmpty()) {
                      viewModel.restoreDismissedSuggestions()
                    } else {
                      viewModel.refreshAllData()
                    }
                  }
                )
              }
            } else {
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
                          Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          Spacer(modifier = Modifier.width(4.dp))
                          VerificationBadge(user = user, iconSize = 14.dp)
                        }
                        Text("@${user.username}", fontSize = 11.sp, color = palette.accent)
                        if (user.followsMe) {
                          Text("Linked with you", fontSize = 10.sp, color = palette.accentSecondary, fontWeight = FontWeight.SemiBold)
                        }
                      }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                      IconButton(
                        onClick = { viewModel.dismissSuggestedUser(user.id) },
                        modifier = Modifier.size(48.dp)
                      ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss creator", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                      }
                      Button(
                        onClick = { viewModel.toggleFollowUser(user.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isFollowing) palette.elevated else palette.accent),
                        border = if (isFollowing) BorderStroke(1.dp, palette.border) else null,
                        shape = RoundedCornerShape(VybeTokens.Radius.md),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                      ) {
                        Text(
                          text = if (isFollowing) "LINKED" else if (isRequested) "Requested" else "Follow",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = if (isFollowing) palette.textSecondary else Color.White
                        )
                      }
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
          }

          "Circles" -> {
            item(key = "circles_header") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Discover Circles", fontFamily = PlusJakartaSansFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                  Text("Join communities shaped around your passions", fontSize = 11.sp, color = palette.textSecondary)
                }
                TextButton(
                  onClick = { viewModel.openCommunityZone() },
                  modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                  Text("Community Hub", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                }
              }
            }
            if (communities.isEmpty()) {
              item(key = "circles_empty") {
                VybeEmptyState(
                  icon = Icons.Outlined.Groups,
                  title = "No Circles discovered yet",
                  subtitle = "Open Community Hub to browse or start a VYBE Circle.",
                  actionLabel = "Open Community Hub",
                  onAction = { viewModel.openCommunityZone() }
                )
              }
            } else {
              items(communities, key = { "explore_comm_${it.id}" }) { comm ->
                VybeSurfaceCard(
                  onClick = { viewModel.openCommunity(comm) },
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("${comm.category} • ${comm.membersCount} members", fontSize = 11.sp, color = palette.accent)
                      if (comm.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(comm.description, fontSize = 12.sp, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                      }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                      onClick = { viewModel.toggleJoinCommunity(comm.id) },
                      colors = ButtonDefaults.buttonColors(containerColor = if (comm.isJoined) palette.elevated else palette.accent),
                      border = if (comm.isJoined) BorderStroke(1.dp, palette.border) else null,
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    ) {
                      Text(
                        text = if (comm.isJoined) "Joined" else "Join",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (comm.isJoined) palette.textSecondary else Color.White
                      )
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
                    TextButton(
                      onClick = { showNearbyRationaleDialog = true },
                      modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("explore_nearby_enable_gps")
                    ) {
                      Text(
                        text = "Enable GPS",
                        fontSize = 12.sp,
                        color = palette.accentSecondary,
                        fontWeight = FontWeight.Bold
                      )
                    }
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
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                        .clickable { selectedCityFilter = city }
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(
                          text = city,
                          fontSize = 11.sp,
                          fontWeight = if (isCitySelected) FontWeight.Bold else FontWeight.Medium,
                          color = if (isCitySelected) Color.White else palette.textSecondary,
                          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                      }
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

  if (showNearbyRationaleDialog) {
    AlertDialog(
      onDismissRequest = { showNearbyRationaleDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Text("Privacy-First Nearby Discovery", fontWeight = FontWeight.Bold, color = palette.textPrimary)
      },
      text = {
        Text(
          "VYBE uses approximate city-level region matching to surface local public posts and creators. Your exact GPS coordinates are never published or shared with other users.",
          fontSize = 13.sp,
          color = palette.textSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showNearbyRationaleDialog = false
            locationPermissionLauncher.launch(
              arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
          },
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
        ) {
          Text("Allow Location", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showNearbyRationaleDialog = false }) {
          Text("Choose City Manually", color = palette.textSecondary)
        }
      }
    )
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

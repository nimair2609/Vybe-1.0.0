package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaType
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.model.VybeCircle
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * VYBE 2.1 – EXPLORE ZONE
 * Redesigned Discovery Experience based on the official VYBE 2.1 visual system.
 * Features:
 * - Brand wordmark & "Explore Zone ✦" header with unread notification badge
 * - Filter Pills: For You, Trending, People, Nearby, Circles, Interests
 * - Smart Search with recent history, instant suggestions, and categorized live results
 * - Trending Topics with real post-backed hashtags
 * - Featured Creators horizontal discovery cards with live follower counts and follow action
 * - Recommended for You rich media cards with likes and comments
 * - Nearby Discovery with privacy protection and city fallback
 * - Public Circles and Interest channels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(viewModel: VybeViewModel) {
  val searchQuery by viewModel.searchQuery.collectAsState()
  val searchHistory by viewModel.searchHistory.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val circles by viewModel.circles.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val unreadNotificationsCount = remember(notifications) { notifications.count { !it.isRead } }

  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current

  // Filter Pills: "For You", "Trending", "People", "Nearby", "Circles", "Interests"
  val filterPills = listOf("For You", "Trending", "People", "Nearby", "Circles", "Interests")
  var selectedPill by remember { mutableStateOf("For You") }

  // Search result category tabs
  val searchTabs = listOf("All", "People", "Hashtags", "Posts", "Reels", "Circles")
  var selectedSearchTab by remember { mutableStateOf("All") }

  // Nearby Discovery state
  var nearbyLocationGranted by remember { mutableStateOf(false) }
  var selectedCityFilter by remember { mutableStateOf("All India") }
  val indianCities = listOf("All India", "Mumbai", "Bengaluru", "Delhi NCR", "Goa", "Hyderabad", "Kolkata", "Pune")

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    nearbyLocationGranted = perms.values.any { it }
  }

  // Selected Interest Tag state
  val interestCategories = listOf("Travel", "Tech", "Music", "Fitness", "Lifestyle", "Design", "Food")
  var selectedInterest by remember { mutableStateOf("Travel") }

  // Filtered nearby posts
  val nearbyPosts = remember(posts, selectedCityFilter) {
    if (selectedCityFilter == "All India") {
      posts.filter { !it.isArchived && !it.locationName.isNullOrBlank() }
    } else {
      posts.filter { !it.isArchived && it.locationName?.contains(selectedCityFilter, ignoreCase = true) == true }
    }
  }

  // Filtered interest posts
  val interestPosts = remember(posts, selectedInterest) {
    posts.filter { p ->
      !p.isArchived && (
        p.caption.contains(selectedInterest, ignoreCase = true) ||
          p.tags.any { it.contains(selectedInterest, ignoreCase = true) }
        )
    }
  }

  // Real Trending Hashtags calculated from published posts
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

  // Real Featured Creators from published user records
  val featuredCreators = remember(allUsers, currentUser?.id) {
    allUsers
      .filter { it.id != currentUser?.id && !it.isBanned && !it.isDeactivated }
      .sortedByDescending { it.followersCount }
  }

  // Recommended Posts
  val recommendedPosts = remember(posts, selectedPill) {
    val activePosts = posts.filter { !it.isArchived }
    when (selectedPill) {
      "Trending" -> activePosts.sortedByDescending { it.likesCount + it.commentsCount }
      else -> activePosts.shuffled().take(12)
    }
  }

  // Smart Search matching
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

  val matchedCircles = remember(cleanQuery, circles) {
    if (cleanQuery.isBlank()) emptyList() else {
      circles.filter {
        it.name.lowercase().contains(cleanQuery) ||
          it.description.lowercase().contains(cleanQuery) ||
          it.category.lowercase().contains(cleanQuery)
      }
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
          // Brand Wordmark + Explore Zone Title + Notification Bell
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "VYBE",
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Black,
                  letterSpacing = 2.sp,
                  color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  color = VybeBrandViolet.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = "2.1",
                    color = VybeBrandCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(2.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Explore Zone",
                  fontSize = 18.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "✦",
                  fontSize = 16.sp,
                  color = VybeBrandViolet
                )
              }
              Text(
                text = "Discover. Connect. Vibe.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            // Top Right: Search Icon + Notification Bell with Badge
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(modifier = Modifier.size(42.dp)) {
                IconButton(
                  onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
                  modifier = Modifier.fillMaxSize()
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface
                  )
                }
                if (unreadNotificationsCount > 0) {
                  Box(
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .offset(x = (-2).dp, y = 4.dp)
                      .size(16.dp)
                      .clip(CircleShape)
                      .background(VybeDanger),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = if (unreadNotificationsCount > 9) "9+" else unreadNotificationsCount.toString(),
                      color = Color.White,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }
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
              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { viewModel.navigateTo(Screen.UNLOAD_VIEW) }
                  .testTag("explore_header_feature_unload"),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("🎙️", fontSize = 13.sp)
                  Text("UNLOAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
              }

              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { viewModel.navigateTo(Screen.UNFILTERED_VIEW) }
                  .testTag("explore_header_feature_unfiltered"),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("✨", fontSize = 13.sp)
                  Text("UNFILTERED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
              }

              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { viewModel.navigateTo(Screen.WHAT_NEXT_VIEW) }
                  .testTag("explore_header_feature_what_next"),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("🔮", fontSize = 13.sp)
                  Text("WHAT NEXT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) }
                  .testTag("explore_header_feature_time_capsule"),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("⏳", fontSize = 13.sp)
                  Text("TIME CAPSULE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
              }

              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { viewModel.navigateTo(Screen.DOUBLE_TAKE_VIEW) }
                  .testTag("explore_header_feature_double_take"),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("📸", fontSize = 13.sp)
                  Text("DOUBLE TAKE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
              }

              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { viewModel.selectTab(MainTab.CROSSED_PATHS) }
                  .testTag("explore_header_feature_crossed_paths"),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("🧭", fontSize = 13.sp)
                  Text("CROSSED PATHS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Filter Pills Row (For You, Trending, People, Nearby, Circles, Interests)
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(filterPills) { pill ->
              val isSelected = selectedPill == pill
              Surface(
                color = if (isSelected) VybeBrandViolet else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                border = if (isSelected) null else BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .clip(RoundedCornerShape(20.dp))
                  .clickable {
                    selectedPill = pill
                    if (isSearching) viewModel.setSearchQuery("")
                  }
                  .testTag("explore_pill_$pill")
              ) {
                Text(
                  text = pill,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Smart Search Bar
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              TextField(
                value = searchQuery,
                onValueChange = {
                  viewModel.setSearchQuery(it)
                  if (it.isNotBlank()) {
                    viewModel.addSearchQueryToHistory(it)
                  }
                },
                placeholder = {
                  Text(
                    text = "Search users, hashtags, or posts...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                  focusedContainerColor = Color.Transparent,
                  unfocusedContainerColor = Color.Transparent,
                  focusedIndicatorColor = Color.Transparent,
                  unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("explore_smart_search_input")
              )
              if (searchQuery.isNotEmpty()) {
                IconButton(
                  onClick = { viewModel.setSearchQuery("") },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                  )
                }
              } else {
                Icon(
                  imageVector = Icons.Default.FilterList,
                  contentDescription = "Filter",
                  tint = VybeBrandViolet,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

      // ==========================================
      // STATE 1: SEARCH RESULTS VIEW
      // ==========================================
      if (isSearching) {
        // Search Result Category Tabs
        item(key = "search_category_tabs") {
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(searchTabs) { tab ->
              val isSelected = selectedSearchTab == tab
              Surface(
                color = if (isSelected) VybeBrandViolet else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .clip(RoundedCornerShape(16.dp))
                  .clickable { selectedSearchTab = tab }
              ) {
                Text(
                  text = tab,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
            }
          }
        }

        // Recent Searches Bar
        if (searchHistory.isNotEmpty()) {
          item(key = "search_history_bar") {
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Recent Searches",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = "Clear All",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = VybeBrandViolet,
                  modifier = Modifier.clickable { viewModel.clearSearchHistory() }
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(searchHistory) { historyItem ->
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { viewModel.setSearchQuery(historyItem) }
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = historyItem,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // Matched Users section
        if (selectedSearchTab == "All" || selectedSearchTab == "People") {
          if (matchedUsers.isNotEmpty()) {
            item(key = "matched_users_header") {
              Text("People", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            items(matchedUsers.take(6), key = { "search_u_${it.id}" }) { user ->
              val isFollowing = followingUserIds.contains(user.id)
              val isRequested = requestedFollowUserIds.contains(user.id)
              Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { viewModel.openUserProfile(user) }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    VybeAvatar(user = user, size = 42.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          text = user.displayName,
                          fontWeight = FontWeight.Bold,
                          fontSize = 13.sp,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        if (user.isVerified) {
                          Spacer(modifier = Modifier.width(4.dp))
                          VerificationBadge(user = user, iconSize = 14.dp)
                        }
                      }
                      Text(
                        text = "@${user.username} • ${user.followersCount} followers",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  if (user.id != currentUser?.id) {
                    Surface(
                      color = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else VybeBrandViolet,
                      shape = RoundedCornerShape(16.dp),
                      modifier = Modifier
                        .clickable { viewModel.toggleFollowUser(user.id) }
                        .padding(start = 8.dp)
                    ) {
                      Text(
                        text = if (isFollowing) "LINKED" else if (isRequested) "Requested" else "Follow",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Matched Hashtags section
        if (selectedSearchTab == "All" || selectedSearchTab == "Hashtags") {
          if (matchedHashtags.isNotEmpty()) {
            item(key = "matched_tags_header") {
              Text("Hashtags", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            items(matchedHashtags, key = { "tag_${it.first}" }) { (tag, count, _) ->
              Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { viewModel.setSearchQuery("#$tag") }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(VybeBrandCyan.copy(alpha = 0.15f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Text("#", fontWeight = FontWeight.Bold, color = VybeBrandCyan)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text("#$tag", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      Text("$count posts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }
          }
        }

        // Matched Posts & Reels
        if (selectedSearchTab == "All" || selectedSearchTab == "Posts" || selectedSearchTab == "Reels") {
          val postList = if (selectedSearchTab == "Reels") matchedReels else matchedPosts
          if (postList.isNotEmpty()) {
            item(key = "matched_posts_header") {
              Text(
                if (selectedSearchTab == "Reels") "Reels" else "Posts",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
              )
            }
            items(postList, key = { "search_p_${it.id}" }) { post ->
              Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    if (post.mediaType == MediaType.REEL || post.isVybesReel) {
                      viewModel.openReelViewer(post.id)
                    } else {
                      viewModel.openPostFromNotification(post.id)
                    }
                  }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  val mediaUrl = post.mediaUrls.firstOrNull().orEmpty()
                  if (mediaUrl.isNotBlank()) {
                    AsyncImage(
                      model = mediaUrl,
                      contentDescription = post.caption,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = post.caption.ifBlank { "Post by @${post.author.username}" },
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "@${post.author.username} • ${post.likesCount} likes",
                      fontSize = 10.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
        }

        // Empty state when search yields nothing
        if (matchedUsers.isEmpty() && matchedPosts.isEmpty() && matchedHashtags.isEmpty() && matchedCircles.isEmpty()) {
          item(key = "search_empty_state") {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(
                  imageVector = Icons.Default.SearchOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = "No results found for \"$searchQuery\"",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Try searching for a different username, display name, #hashtag, or circle.",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        }
      }

      // ==========================================
      // STATE 2: LANDING DISCOVERY VIEW (BY PILL)
      // ==========================================
      else {
        when (selectedPill) {
          // --- FOR YOU & TRENDING ---
          "For You", "Trending" -> {
            // SECTION 1: TRENDING TOPICS 🔥
            item(key = "landing_trending_topics") {
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Text(
                      text = "Trending Topics",
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("🔥", fontSize = 16.sp)
                  }
                  Text(
                    text = "See All →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeBrandViolet,
                    modifier = Modifier.clickable { selectedPill = "Trending" }
                  )
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (trendingHashtags.isNotEmpty()) {
                  LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    items(trendingHashtags) { (tag, count, thumbnail) ->
                      Surface(
                        color = Color(0xFF131B2E),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                          .width(135.dp)
                          .height(84.dp)
                          .clip(RoundedCornerShape(14.dp))
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
                                .background(
                                  Brush.linearGradient(listOf(VybeVioletDark, Color(0xFF1E1B4B)))
                                )
                            )
                          }
                          Box(
                            modifier = Modifier
                              .fillMaxSize()
                              .background(
                                Brush.verticalGradient(
                                  listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                              )
                          )
                          Column(
                            modifier = Modifier
                              .align(Alignment.BottomStart)
                              .padding(8.dp)
                          ) {
                            Text(
                              text = "#$tag",
                              color = Color.White,
                              fontSize = 11.sp,
                              fontWeight = FontWeight.Bold,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                            )
                            Text(
                              text = "$count posts",
                              color = VybeBrandCyan,
                              fontSize = 10.sp
                            )
                          }
                        }
                      }
                    }
                  }
                } else {
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = "No trending topics yet. Start a conversation and make something trend.",
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                  }
                }
              }
            }

            // SECTION 2: FEATURED CREATORS
            item(key = "landing_featured_creators") {
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Featured Creators",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "See All →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeBrandViolet,
                    modifier = Modifier.clickable { selectedPill = "People" }
                  )
                }
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  items(featuredCreators.take(8), key = { "feat_c_${it.id}" }) { creator ->
                    val isFollowing = followingUserIds.contains(creator.id)
                    val isRequested = requestedFollowUserIds.contains(creator.id)
                    Surface(
                      color = Color(0xFF131B2E),
                      shape = RoundedCornerShape(16.dp),
                      border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                      modifier = Modifier
                        .width(130.dp)
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
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
                              .background(
                                Brush.linearGradient(listOf(VybeVioletDark, Color(0xFF312E81)))
                              )
                          )
                        }
                        Box(
                          modifier = Modifier
                            .fillMaxSize()
                            .background(
                              Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                              )
                            )
                        )
                        Column(
                          modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                          horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                          ) {
                            Text(
                              text = "@${creator.username}",
                              color = Color.White,
                              fontSize = 11.sp,
                              fontWeight = FontWeight.Bold,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                            )
                            if (creator.isVerified) {
                              VerificationBadge(user = creator, iconSize = 12.dp)
                            }
                          }
                          Text(
                            text = "${creator.followersCount} Followers",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 9.sp
                          )
                          Spacer(modifier = Modifier.height(8.dp))
                          Button(
                            onClick = { viewModel.toggleFollowUser(creator.id) },
                            colors = ButtonDefaults.buttonColors(
                              containerColor = if (isFollowing) Color.White.copy(alpha = 0.2f) else VybeBrandViolet
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier
                              .fillMaxWidth()
                              .height(30.dp)
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

            // SECTION 3: RECOMMENDED FOR YOU (Rich Media Cards)
            item(key = "landing_recommended_header") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Recommended for You",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "See All →",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeBrandViolet,
                  modifier = Modifier.clickable { viewModel.refreshAllData() }
                )
              }
            }

            // 2-Column Recommended Posts
            items(recommendedPosts.chunked(2), key = { chunk -> "rec_row_${chunk.first().id}" }) { pair ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                pair.forEach { post ->
                  Surface(
                    color = Color(0xFF131B2E),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                      .weight(1f)
                      .height(180.dp)
                      .clip(RoundedCornerShape(14.dp))
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
                          .background(
                            Brush.verticalGradient(
                              listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                          )
                      )
                      // Top bookmark icon
                      IconButton(
                        onClick = { viewModel.toggleSave(post.id) },
                        modifier = Modifier
                          .align(Alignment.TopEnd)
                          .size(32.dp)
                      ) {
                        Icon(
                          imageVector = if (post.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                          contentDescription = "Save",
                          tint = Color.White,
                          modifier = Modifier.size(16.dp)
                        )
                      }
                      // Bottom stats: Like count & comment count
                      Row(
                        modifier = Modifier
                          .align(Alignment.BottomStart)
                          .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                      ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Likes",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                          )
                          Spacer(modifier = Modifier.width(3.dp))
                          Text(
                            text = "${post.likesCount}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                          )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Comments",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                          )
                          Spacer(modifier = Modifier.width(3.dp))
                          Text(
                            text = "${post.commentsCount}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                          )
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

            // Spiritual Mode Gateway Card
            item(key = "landing_spiritual_gateway") {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { viewModel.navigateTo(Screen.SPIRITUAL_MODE) }
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.SelfImprovement,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text("Spiritual Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                      Text("Enter a peaceful mindful zone without metrics", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }

          // --- PEOPLE DISCOVERY ---
          "People" -> {
            item(key = "people_header") {
              Text("Discover Creators & People", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(featuredCreators, key = { "p_creator_${it.id}" }) { user ->
              val isFollowing = followingUserIds.contains(user.id)
              val isRequested = requestedFollowUserIds.contains(user.id)
              Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { viewModel.openUserProfile(user) }
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      modifier = Modifier.weight(1f),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      VybeAvatar(user = user, size = 48.dp)
                      Spacer(modifier = Modifier.width(10.dp))
                      Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text(
                            text = user.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                          )
                          if (user.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerificationBadge(user = user, iconSize = 14.dp)
                          }
                        }
                        Text(
                          text = "@${user.username}",
                          fontSize = 11.sp,
                          color = VybeBrandViolet
                        )
                        if (user.followsMe) {
                          Text(
                            text = "Linked with you",
                            fontSize = 10.sp,
                            color = VybeBrandCyan,
                            fontWeight = FontWeight.SemiBold
                          )
                        }
                      }
                    }
                    Button(
                      onClick = { viewModel.toggleFollowUser(user.id) },
                      colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else VybeBrandViolet
                      ),
                      shape = RoundedCornerShape(12.dp)
                    ) {
                      Text(
                        text = if (isFollowing) "LINKED" else if (isRequested) "Requested" else "Follow",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                      )
                    }
                  }

                  if (user.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = user.bio,
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  if (user.interestTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      user.interestTags.take(3).forEach { tag ->
                        Surface(
                          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                          shape = RoundedCornerShape(8.dp)
                        ) {
                          Text(
                            text = "#$tag",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          // --- NEARBY DISCOVERY ---
          "Nearby" -> {
            item(key = "nearby_header") {
              Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                  color = VybeBrandCyan.copy(alpha = 0.12f),
                  shape = RoundedCornerShape(16.dp),
                  border = BorderStroke(1.dp, VybeBrandCyan.copy(alpha = 0.35f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(VybeBrandCyan.copy(alpha = 0.2f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = VybeBrandCyan,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Nearby Safe Discovery",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                      )
                      Text(
                        text = "Explore public creators and moments around you. Exact GPS is never shared.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Location Permission & City Filter Bar
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Select Location Area", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  if (!nearbyLocationGranted) {
                    Text(
                      text = "Enable GPS",
                      fontSize = 11.sp,
                      color = VybeBrandCyan,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.clickable {
                        locationPermissionLauncher.launch(
                          arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                          )
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
                      color = if (isCitySelected) VybeBrandCyan else MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedCityFilter = city }
                    ) {
                      Text(
                        text = city,
                        fontSize = 11.sp,
                        fontWeight = if (isCitySelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCitySelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                      )
                    }
                  }
                }
              }
            }

            if (nearbyPosts.isEmpty()) {
              item(key = "nearby_empty") {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Icon(
                      imageVector = Icons.Default.Place,
                      contentDescription = null,
                      tint = VybeBrandCyan,
                      modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                      text = "No public moments in $selectedCityFilter yet",
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "Be the first to share a moment with location tag in $selectedCityFilter!",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            } else {
              items(nearbyPosts, key = { "nearby_p_${it.id}" }) { post ->
                Surface(
                  color = MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(14.dp),
                  border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.openPostFromNotification(post.id) }
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    val mediaUrl = post.mediaUrls.firstOrNull().orEmpty()
                    if (mediaUrl.isNotBlank()) {
                      AsyncImage(
                        model = mediaUrl,
                        contentDescription = post.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(54.dp)
                          .clip(RoundedCornerShape(8.dp))
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                          imageVector = Icons.Default.LocationOn,
                          contentDescription = null,
                          tint = VybeBrandCyan,
                          modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                          text = post.locationName ?: "India",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = VybeBrandCyan
                        )
                      }
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = post.caption,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        text = "Shared by @${post.author.username}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }
              }
            }
          }

          // --- CIRCLES ---
          "Circles" -> {
            item(key = "circles_header") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Public & Interest Circles", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                  text = "Create Circle →",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeBrandViolet,
                  modifier = Modifier.clickable { viewModel.navigateTo(Screen.VYBE_CIRCLE) }
                )
              }
            }
            items(circles, key = { "circle_${it.id}" }) { circle ->
              Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { viewModel.navigateTo(Screen.VYBE_CIRCLE) }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(VybeBrandViolet.copy(alpha = 0.2f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = VybeBrandViolet,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = circle.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                      )
                      Text(
                        text = "#${circle.category} • ${circle.membersCount} members",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  Button(
                    onClick = {
                      if (circle.isMember) viewModel.leaveCircle(circle.id) else viewModel.joinCircle(circle.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = if (circle.isMember) MaterialTheme.colorScheme.surfaceVariant else VybeBrandViolet
                    ),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Text(
                      text = if (circle.isMember) "Joined" else "Join",
                      fontSize = 11.sp,
                      color = if (circle.isMember) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    )
                  }
                }
              }
            }
          }

          // --- INTERESTS ---
          "Interests" -> {
            item(key = "interests_header") {
              Column(modifier = Modifier.fillMaxWidth()) {
                Text("Browse by Interest", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(interestCategories) { cat ->
                    val isCatSelected = selectedInterest == cat
                    Surface(
                      color = if (isCatSelected) VybeBrandViolet else MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedInterest = cat }
                    ) {
                      Text(
                        text = cat,
                        fontSize = 11.sp,
                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCatSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                      )
                    }
                  }
                }
              }
            }

            if (interestPosts.isEmpty()) {
              item(key = "interest_empty") {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      text = "No moments in #$selectedInterest yet",
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "Tag your next post with #$selectedInterest to appear here!",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            } else {
              items(interestPosts, key = { "int_p_${it.id}" }) { post ->
                Surface(
                  color = MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(14.dp),
                  border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.openPostFromNotification(post.id) }
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    val mediaUrl = post.mediaUrls.firstOrNull().orEmpty()
                    if (mediaUrl.isNotBlank()) {
                      AsyncImage(
                        model = mediaUrl,
                        contentDescription = post.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(54.dp)
                          .clip(RoundedCornerShape(8.dp))
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = post.caption,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = "@${post.author.username} • ${post.likesCount} likes",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
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

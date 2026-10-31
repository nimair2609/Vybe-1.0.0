package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ReportStatus
import com.example.data.model.UserRole
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(viewModel: VybeViewModel) {
  val searchQuery by viewModel.searchQuery.collectAsState()
  val predictions by viewModel.predictions.collectAsState()
  val shops by viewModel.shops.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val unloadRooms by viewModel.unloadRooms.collectAsState()
  val liveStreams by viewModel.liveStreams.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val adminReports by viewModel.adminReports.collectAsState()
  val communities by viewModel.communities.collectAsState()
  val unfilteredPosts by viewModel.unfilteredPosts.collectAsState()
  val timeCapsules by viewModel.timeCapsules.collectAsState()
  val doubleTakePrompts by viewModel.doubleTakePrompts.collectAsState()
  val crossedPaths by viewModel.crossedPaths.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    viewModel.refreshAllData()
    viewModel.fetchCommunities()
  }

  var roomCodeInput by remember { mutableStateOf("") }
  var roomCodeJoining by remember { mutableStateOf(false) }
  var roomCodeError by remember { mutableStateOf<String?>(null) }

  val trendingTags = remember(posts) {
    val regex = Regex("#([A-Za-z0-9_]{2,30})")
    posts
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
      .map { it.key }
      .take(10)
  }

  val isSearching = searchQuery.isNotBlank()
  val cleanQuery = remember(searchQuery) {
    searchQuery.trim().lowercase().removePrefix("@").removePrefix("#")
  }

  val matchedUsers = remember(cleanQuery, allUsers) {
    if (cleanQuery.isBlank()) emptyList() else {
      allUsers.filter {
        it.publicVybeId.lowercase().contains(cleanQuery) ||
          it.username.lowercase().contains(cleanQuery) ||
          it.displayName.lowercase().contains(cleanQuery)
      }
    }
  }

  val matchedShops = remember(cleanQuery, shops) {
    if (cleanQuery.isBlank()) emptyList() else {
      shops.filter {
        it.businessName.lowercase().contains(cleanQuery) ||
          it.category.lowercase().contains(cleanQuery)
      }
    }
  }

  val matchedPosts = remember(cleanQuery, posts) {
    if (cleanQuery.isBlank()) emptyList() else {
      posts.filter { post ->
        !post.isArchived && (
          post.caption.lowercase().contains(cleanQuery) ||
            post.tags.any { tag -> tag.lowercase().contains(cleanQuery) } ||
            post.locationName?.lowercase()?.contains(cleanQuery) == true
          )
      }
    }
  }

  val matchedRooms = remember(cleanQuery, unloadRooms) {
    if (cleanQuery.isBlank()) emptyList() else {
      unloadRooms.filter { room ->
        room.isActive && (
          room.roomCode.lowercase().contains(cleanQuery) ||
            room.title.lowercase().contains(cleanQuery) ||
            room.topicTag.lowercase().contains(cleanQuery)
          )
      }
    }
  }

  val matchedLiveStreams = remember(cleanQuery, liveStreams) {
    if (cleanQuery.isBlank()) emptyList() else {
      liveStreams.filter { stream ->
        stream.isLive && (
          stream.title.lowercase().contains(cleanQuery) ||
            stream.category.lowercase().contains(cleanQuery) ||
            stream.host.username.lowercase().contains(cleanQuery) ||
            stream.host.displayName.lowercase().contains(cleanQuery)
          )
      }
    }
  }

  val matchedCommunities = remember(cleanQuery, communities) {
    if (cleanQuery.isBlank()) emptyList() else {
      communities.filter { c ->
        c.name.lowercase().contains(cleanQuery) ||
          c.description.lowercase().contains(cleanQuery) ||
          c.category.lowercase().contains(cleanQuery)
      }
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { viewModel.setSearchQuery(it) },
          placeholder = { Text("Search people, @username, #tags, or places...", fontSize = 13.sp) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { viewModel.setSearchQuery("") },
                modifier = Modifier.size(44.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .heightIn(min = 52.dp)
            .testTag("explore_search_bar")
        )
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      if (isSearching) {
        item {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Search Results for \"$searchQuery\"",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
        }

        if (matchedUsers.isEmpty() && matchedShops.isEmpty() && matchedPosts.isEmpty() && matchedRooms.isEmpty() && matchedLiveStreams.isEmpty() && matchedCommunities.isEmpty()) {
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(
                  Icons.Default.SearchOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("No matching results", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Search by name, @username, public VYBE ID, #hashtag, or location.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        } else {
          if (matchedLiveStreams.isNotEmpty()) {
            item {
              Text(
                text = "Live Zone Broadcasts (${matchedLiveStreams.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = VybeDanger,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              )
            }
            items(matchedLiveStreams, key = { "search_live_${it.id}" }) { stream ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 5.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { viewModel.joinLiveBroadcast(stream) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.45f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  VybeAvatar(user = stream.host, size = 38.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(stream.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("@${stream.host.username} • ${stream.viewerCount} watching live", fontSize = 11.sp, color = VybeDanger)
                  }
                  Text("Watch", color = VybeDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }
            }
          }
          if (matchedRooms.isNotEmpty()) {
            item {
              Text(
                text = "Live Listening Rooms (${matchedRooms.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = VybeBrandCyan,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              )
            }
            items(matchedRooms, key = { "search_room_${it.id}" }) { room ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 5.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { viewModel.joinUnloadRoom(room) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.GraphicEq, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(28.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(room.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${room.roomCode} • ${room.topicTag} • ${room.activeListenersCount} online", fontSize = 11.sp, color = VybeBrandCyan)
                  }
                  Text("Join", color = VybeViolet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }
            }
          }
          if (matchedCommunities.isNotEmpty()) {
            item {
              Text(
                text = "Communities (${matchedCommunities.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = VybeViolet,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              )
            }
            items(matchedCommunities, key = { "search_comm_${it.id}" }) { comm ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 5.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    viewModel.openCommunityZone()
                    viewModel.openCommunity(comm)
                  },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Outlined.Groups, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(28.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${comm.membersCount} members • ${comm.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Text("Open", color = VybeViolet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }
            }
          }
          if (matchedUsers.isNotEmpty()) {
            item {
              Text(
                text = "People & Creators (${matchedUsers.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = VybeViolet,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              )
            }

            items(matchedUsers, key = { it.id }) { matchedUser ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 5.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { viewModel.openUserProfile(matchedUser) }
                  .testTag("search_user_card_${matchedUser.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  VybeAvatar(user = matchedUser, size = 44.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(matchedUser.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                      Spacer(modifier = Modifier.width(4.dp))
                      VerificationBadge(user = matchedUser)
                    }
                    Text("@${matchedUser.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (matchedUser.publicVybeId.isNotBlank()) {
                      Spacer(modifier = Modifier.height(2.dp))
                      Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                      ) {
                        Text(
                          text = "ID: ${matchedUser.publicVybeId}",
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          color = VybeBrandCyan,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                  if (matchedUser.id != currentUser?.id) {
                    Spacer(modifier = Modifier.width(8.dp))
                    com.example.ui.components.VybeFollowButton(
                      isFollowing = followingUserIds.contains(matchedUser.id),
                      isFollowRequested = requestedFollowUserIds.contains(matchedUser.id) || matchedUser.isFollowRequestedByMe,
                      followsMe = matchedUser.followsMe,
                      onClick = { viewModel.toggleFollowUser(matchedUser.id) },
                      compact = true,
                      modifier = Modifier.testTag("explore_follow_user_${matchedUser.id}")
                    )
                  } else {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }

          if (matchedShops.isNotEmpty()) {
            item {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Verified Shops (${matchedShops.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = VybeVerifiedGold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              )
            }

            items(matchedShops, key = { it.id }) { matchedShop ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 5.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { viewModel.openShopDetail(matchedShop) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                      .background(VybeVerifiedGold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.Store, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(24.dp))
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(matchedShop.businessName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${matchedShop.category} • ${matchedShop.locationAddress}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          if (matchedPosts.isNotEmpty()) {
            item {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Posts (${matchedPosts.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = VybeBrandCyan,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              )
            }

            items(matchedPosts, key = { "search_post_${it.id}" }) { post ->
              PostCard(
                post = post,
                onLikeClick = { viewModel.toggleLike(post.id) },
                onReact = { rx -> viewModel.reactToPost(post.id, rx) },
                onSaveClick = { viewModel.toggleSave(post.id) },
                onCommentClick = { viewModel.loadCommentsForPost(post.id) },
                onAddComment = { text -> viewModel.addComment(post.id, text) },
                onLoadComments = { viewModel.loadCommentsForPost(post.id) },
                onShareClick = { viewModel.openPostShareSheet(post) },
                onUserClick = { u -> viewModel.openUserProfile(u) },
                onRemixClick = { viewModel.openReelViewer(post.id) },
                onDeleteClick = { viewModel.deletePost(post.id) },
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
        }
      } else {
        // EXPLORE VYBES Mobile Discovery Feed Header
        item {
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f))
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  modifier = Modifier.weight(1f, fill = false),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(28.dp)
                      .clip(CircleShape)
                      .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                          listOf(VybeBrandCyan, VybeViolet)
                        )
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      Icons.Default.Explore,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = "EXPLORE VYBES",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.8.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                  color = VybeViolet.copy(alpha = 0.22f),
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.45f))
                ) {
                  Text(
                    text = "6 SIGNATURE ZONES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = VybeBrandCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Find your vibe. Enter your zone.",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VybeBrandCyan
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Six intentional ways to connect beyond algorithms, filters, and vanity metrics.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Zone 1: UNLOAD
        item {
          val activeRooms = unloadRooms.filter { it.isActive }
          ExploreExperienceFeedCard(
            title = "UNLOAD",
            tagline = "24/7 Ephemeral Voice & Listening Sanctuary",
            description = "Zero audio recordings. Ephemeral voice and chat sanctuary to share what's heavy on your mind in a judgment-free space.",
            badgeText = "LIVE SANCTUARY",
            activityText = if (activeRooms.isNotEmpty()) "${activeRooms.size} voice lounges active" else "Sanctuary standing by",
            accent = VybeViolet,
            icon = Icons.Default.GraphicEq,
            ctaText = "Enter Sanctuary",
            testTag = "explore_unload_feed_card",
            onEnter = { viewModel.navigateTo(Screen.UNLOAD_VIEW) }
          ) {
            val featuredRoom = activeRooms.firstOrNull()
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.3f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (featuredRoom != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Box(
                        modifier = Modifier
                          .size(8.dp)
                          .clip(CircleShape)
                          .background(VybeOnlineGreen)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "LIVE VOICE LOUNGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VybeOnlineGreen
                      )
                    }
                    Surface(
                      color = VybeViolet.copy(alpha = 0.2f),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = "#${featuredRoom.topicTag}",
                        fontSize = 9.sp,
                        color = VybeViolet,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = featuredRoom.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "${featuredRoom.activeListenersCount} listeners • Hosted by ${featuredRoom.hostName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                } else {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.Shield,
                      contentDescription = null,
                      tint = VybeViolet,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Zero audio stored • 100% ephemeral • Open 24/7",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Zone 2: UNFILTERED
        item {
          ExploreExperienceFeedCard(
            title = "UNFILTERED",
            tagline = "Raw & Real Moments, Zero Filters",
            description = "Spontaneous snapshots captured directly without vanity metrics, beauty filters, or curated perfection.",
            badgeText = "RAW & REAL",
            activityText = "${unfilteredPosts.size} genuine snapshots",
            accent = VybeOnlineGreen,
            icon = Icons.Default.CameraAlt,
            ctaText = "Enter Unfiltered",
            testTag = "explore_unfiltered_feed_card",
            onEnter = { viewModel.navigateTo(Screen.UNFILTERED_VIEW) }
          ) {
            val latestUnfiltered = unfilteredPosts.firstOrNull()
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, VybeOnlineGreen.copy(alpha = 0.3f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (latestUnfiltered != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                      VybeAvatar(user = latestUnfiltered.author, size = 32.dp)
                      Spacer(modifier = Modifier.width(8.dp))
                      Column {
                        Text(
                          text = latestUnfiltered.author.displayName,
                          fontWeight = FontWeight.Bold,
                          fontSize = 12.sp,
                          color = Color.White
                        )
                        Text(
                          text = "@${latestUnfiltered.author.username} • ${latestUnfiltered.timestamp}",
                          fontSize = 10.sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }
                    if (latestUnfiltered.mediaUrls.isNotEmpty()) {
                      AsyncImage(
                        model = latestUnfiltered.mediaUrls.first(),
                        contentDescription = "Unfiltered preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(38.dp)
                          .clip(RoundedCornerShape(8.dp))
                          .border(1.dp, VybeOnlineGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                      )
                    }
                  }
                  if (latestUnfiltered.caption.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = latestUnfiltered.caption,
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2
                    )
                  }
                } else {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.CameraEnhance,
                      contentDescription = null,
                      tint = VybeOnlineGreen,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Dual-cam snapshot • No drafts, no retakes • 100% genuine",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Zone 3: WHAT NEXT
        item {
          ExploreExperienceFeedCard(
            title = "WHAT NEXT",
            tagline = "Culture Pulse & Decision Dilemmas",
            description = "Community dilemmas and predictions. Vote on cultural questions and see live consensus before time runs out.",
            badgeText = "CULTURE PULSE",
            activityText = "${predictions.size} active dilemma polls",
            accent = VybeBrandCyan,
            icon = Icons.Default.Poll,
            ctaText = "Vote Now",
            testTag = "explore_what_next_feed_card",
            onEnter = { viewModel.navigateTo(Screen.WHAT_NEXT_VIEW) }
          ) {
            val topPrediction = predictions.firstOrNull()
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, VybeBrandCyan.copy(alpha = 0.3f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (topPrediction != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Surface(
                      color = VybeBrandCyan.copy(alpha = 0.18f),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = topPrediction.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = VybeBrandCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                    Text(
                      text = "Closes in ${topPrediction.closingTimeText}",
                      fontSize = 10.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = topPrediction.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                  )
                  if (topPrediction.options.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      topPrediction.options.take(2).forEach { opt ->
                        Surface(
                          color = MaterialTheme.colorScheme.surfaceVariant,
                          shape = RoundedCornerShape(8.dp),
                          border = BorderStroke(1.dp, VybeBrandCyan.copy(alpha = 0.25f)),
                          modifier = Modifier.weight(1f)
                        ) {
                          Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Text(
                              text = opt.text,
                              fontSize = 11.sp,
                              fontWeight = FontWeight.SemiBold,
                              maxLines = 1,
                              modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                              text = "${opt.percentage}%",
                              fontSize = 10.sp,
                              fontWeight = FontWeight.Bold,
                              color = VybeBrandCyan
                            )
                          }
                        }
                      }
                    }
                  }
                } else {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.HowToVote,
                      contentDescription = null,
                      tint = VybeBrandCyan,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Vote on active polls • Launch predictions • Live consensus",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Zone 4: TIME CAPSULE
        item {
          ExploreExperienceFeedCard(
            title = "TIME CAPSULE",
            tagline = "Cryptographically Sealed Memory Vaults",
            description = "Lock messages, photos, and letters for future dates. Sealed cryptographically until target unlock moment.",
            badgeText = "FUTURE VAULT",
            activityText = "${timeCapsules.size} sealed memory capsules",
            accent = VybeVerifiedGold,
            icon = Icons.Default.Schedule,
            ctaText = "Enter Vaults",
            testTag = "explore_time_capsule_feed_card",
            onEnter = { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) }
          ) {
            val latestCapsule = timeCapsules.firstOrNull()
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, VybeVerifiedGold.copy(alpha = 0.3f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (latestCapsule != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = VybeVerifiedGold,
                        modifier = Modifier.size(14.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = "SEALED VAULT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VybeVerifiedGold
                      )
                    }
                    Text(
                      text = "Unlocks ${latestCapsule.unlockDateFormatted}",
                      fontSize = 10.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = latestCapsule.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Recipient: ${latestCapsule.recipientSummary} • Theme: ${latestCapsule.categoryTheme}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                } else {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.LockClock,
                      contentDescription = null,
                      tint = VybeVerifiedGold,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Lock memories today • Choose future date • Unlocks automatically",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Zone 5: DOUBLE TAKE
        item {
          ExploreExperienceFeedCard(
            title = "DOUBLE TAKE",
            tagline = "Two Sides, Opposing Perspectives",
            description = "Thought-provoking daily prompts. Cast your stance and unlock chats with peers who think like you or opposite.",
            badgeText = "DUAL TAKE",
            activityText = "${doubleTakePrompts.size} active dilemmas",
            accent = VybeHeartbreakRed,
            icon = Icons.Default.CompareArrows,
            ctaText = "Sync Perspectives",
            testTag = "explore_double_take_feed_card",
            onEnter = { viewModel.navigateTo(Screen.DOUBLE_TAKE_VIEW) }
          ) {
            val topPrompt = doubleTakePrompts.firstOrNull()
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, VybeHeartbreakRed.copy(alpha = 0.3f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (topPrompt != null) {
                  Surface(
                    color = VybeHeartbreakRed.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = topPrompt.category.uppercase(),
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = VybeHeartbreakRed,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = topPrompt.promptQuestion,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Surface(
                      color = MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(8.dp),
                      border = BorderStroke(1.dp, VybeHeartbreakRed.copy(alpha = 0.25f)),
                      modifier = Modifier.weight(1f)
                    ) {
                      Text(
                        text = "A: ${topPrompt.optionA}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                      )
                    }
                    Surface(
                      color = MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(8.dp),
                      border = BorderStroke(1.dp, VybeHeartbreakRed.copy(alpha = 0.25f)),
                      modifier = Modifier.weight(1f)
                    ) {
                      Text(
                        text = "B: ${topPrompt.optionB}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                      )
                    }
                  }
                } else {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.QuestionAnswer,
                      contentDescription = null,
                      tint = VybeHeartbreakRed,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Two sides • Daily prompt • Discover who matches your perspective",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        // Zone 6: CROSSED PATHS
        item {
          ExploreExperienceFeedCard(
            title = "CROSSED PATHS",
            tagline = "Hyperlocal Proximity Radar & Encounters",
            description = "Consent-first real-world missed connections and venue encounters powered by privacy-safe proximity radar.",
            badgeText = "PROXIMITY RADAR",
            activityText = "${crossedPaths.size} proximity signals",
            accent = VybeBrandBlue,
            icon = Icons.Default.NearMe,
            ctaText = "Open Crossed Paths",
            testTag = "explore_crossed_feed_card",
            onEnter = { viewModel.navigateTo(Screen.CREATE_CROSSED_PATH) }
          ) {
            val topEncounter = crossedPaths.firstOrNull()
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, VybeBrandBlue.copy(alpha = 0.3f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (topEncounter != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = VybeBrandBlue,
                        modifier = Modifier.size(14.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = topEncounter.venueOrEventName ?: "Proximity Encounter",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VybeBrandBlue
                      )
                    }
                    Text(
                      text = topEncounter.encounterDate,
                      fontSize = 10.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = topEncounter.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Area: ${topEncounter.broadLocation} • ${topEncounter.expiresHoursRemaining}h remaining",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                } else {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.Explore,
                      contentDescription = null,
                      tint = VybeBrandBlue,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Radar active • Safe venue connections • Zero exact coordinates shared",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Community Zone (Synchronized via Supabase with RLS permissions)
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, VybeViolet.copy(alpha = 0.5f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp)
              .clip(RoundedCornerShape(18.dp))
              .clickable { viewModel.openCommunityZone() }
              .testTag("explore_community_zone_card")
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(VybeViolet.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Outlined.Groups, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(22.dp))
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "COMMUNITY ZONE",
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 14.sp
                    )
                    Text(
                      text = "${communities.size} active communities • Create, post & moderate",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                Button(
                  onClick = { viewModel.openCommunityZone() },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("explore_open_community_zone_button")
                ) {
                  Text("Explore", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }

              if (communities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  items(communities.take(6), key = { "explore_comm_chip_${it.id}" }) { comm ->
                    Surface(
                      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                      shape = RoundedCornerShape(12.dp),
                      border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.3f)),
                      modifier = Modifier
                        .width(195.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                          viewModel.openCommunityZone()
                          viewModel.openCommunity(comm)
                        }
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                          "${comm.membersCount} members • ${comm.category}",
                          fontSize = 10.sp,
                          color = VybeBrandCyan,
                          maxLines = 1
                        )
                      }
                    }
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Live Zone Video Broadcasts (Synced across all users via Supabase)
        item {
          val activeStreams = liveStreams.filter { it.isLive }
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.45f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp)
              .testTag("explore_live_zone_card")
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(9.dp)
                      .clip(CircleShape)
                      .background(VybeDanger)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Live Zone (${activeStreams.size} on air)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                  )
                }
                Button(
                  onClick = { viewModel.openGoLiveStudio() },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeDanger),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("explore_go_live_button")
                ) {
                  Icon(Icons.Default.Podcasts, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Go Live", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }

              if (activeStreams.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "No creators are broadcasting right now. Tap Go Live to stream in real time to everyone on VYBE.",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              } else {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  items(activeStreams, key = { "explore_live_stream_${it.id}" }) { stream ->
                    Surface(
                      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                      shape = RoundedCornerShape(14.dp),
                      border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.4f)),
                      modifier = Modifier
                        .width(215.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.joinLiveBroadcast(stream) }
                        .testTag("explore_live_stream_${stream.id}")
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          VybeAvatar(user = stream.host, size = 28.dp)
                          Spacer(modifier = Modifier.width(8.dp))
                          Column(modifier = Modifier.weight(1f)) {
                            Text(stream.host.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            Text("@${stream.host.username}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                          }
                          Surface(color = VybeDanger, shape = RoundedCornerShape(6.dp)) {
                            Text(
                              text = "LIVE • ${stream.viewerCount}",
                              color = Color.White,
                              fontWeight = FontWeight.ExtraBold,
                              fontSize = 9.sp,
                              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                          }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stream.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                        Text(stream.category, fontSize = 10.sp, color = VybeBrandCyan, maxLines = 1)
                      }
                    }
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Live Persistent Rooms Discovery & Join by Room ID
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(9.dp)
                      .clip(CircleShape)
                      .background(VybeOnlineGreen)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Live Rooms (${unloadRooms.count { it.isActive }})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = { viewModel.navigateTo(Screen.UNLOAD_VIEW) }) {
                  Text("Host / All Rooms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VybeBrandCyan)
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedTextField(
                  value = roomCodeInput,
                  onValueChange = {
                    roomCodeInput = it.uppercase()
                    roomCodeError = null
                  },
                  placeholder = { Text("Enter Room ID (VYB-XXXX-XXXX)", fontSize = 12.sp) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("explore_join_room_code_input")
                )
                Button(
                  onClick = {
                    val code = roomCodeInput.trim()
                    if (code.isBlank() || roomCodeJoining) return@Button
                    roomCodeError = null
                    coroutineScope.launch {
                      roomCodeJoining = true
                      val res = viewModel.joinRoomByCode(code)
                      roomCodeJoining = false
                      if (res.isSuccess) {
                        roomCodeInput = ""
                      } else {
                        roomCodeError = res.exceptionOrNull()?.message ?: "Room not found."
                      }
                    }
                  },
                  enabled = roomCodeInput.trim().isNotBlank() && !roomCodeJoining,
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("explore_join_room_code_button")
                ) {
                  Text(if (roomCodeJoining) "..." else "Join", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }

              if (roomCodeError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(roomCodeError!!, color = VybeDanger, fontSize = 11.sp)
              }

              val activeRooms = unloadRooms.filter { it.isActive }
              if (activeRooms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  items(activeRooms, key = { "explore_live_room_${it.id}" }) { room ->
                    Surface(
                      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                      shape = RoundedCornerShape(12.dp),
                      border = BorderStroke(1.dp, VybeBrandCyan.copy(alpha = 0.3f)),
                      modifier = Modifier
                        .width(210.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.joinUnloadRoom(room) }
                        .testTag("explore_live_room_${room.id}")
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Text(room.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                          room.roomCode,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.ExtraBold,
                          color = VybeBrandCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                          "${room.topicTag} • ${room.activeListenersCount}/${room.capacity} live",
                          fontSize = 10.sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1
                        )
                      }
                    }
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Admin & Moderation Quick Bar (Isolated to Admin accounts only)
        if (currentUser?.role == UserRole.ADMIN) {
          val pendingCount = adminReports.count { it.status == ReportStatus.PENDING }
          item {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.navigateTo(Screen.ADMIN_CONSOLE) }
                .testTag("explore_admin_card")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Moderation Queue & Safety Review",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                  text = "$pendingCount Pending",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeVerifiedGold
                )
              }
            }
          }
        }

        // Predictions Section
        if (predictions.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("What Happens Next?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                text = "Community Polls",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          items(predictions, key = { it.id }) { prediction ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("prediction_card_${prediction.id}"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    color = VybeViolet.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = prediction.category,
                      fontSize = 11.sp,
                      color = VybeViolet,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                  Text(
                    text = "${prediction.totalVotes} votes • ${prediction.closingTimeText}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = prediction.title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                prediction.options.forEach { option ->
                  val isSelected = prediction.userSelectedOptionId == option.id
                  Surface(
                    color = if (isSelected) VybeViolet.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = if (isSelected) BorderStroke(1.5.dp, VybeViolet) else null,
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .clickable { viewModel.votePrediction(prediction.id, option.id) }
                      .testTag("option_${prediction.id}_${option.id}")
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = option.text,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                      )
                      if (prediction.userSelectedOptionId != null) {
                        Text(
                          text = "${option.percentage}%",
                          fontWeight = FontWeight.Bold,
                          fontSize = 13.sp,
                          color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // Verified Businesses & VYBE Shops
        if (shops.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Verified Businesses & Shops", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                text = "Verified Badge",
                style = MaterialTheme.typography.labelSmall,
                color = VybeVerifiedGold,
                fontWeight = FontWeight.Bold
              )
            }
          }

          items(shops, key = { it.id }) { shop ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { viewModel.openShopDetail(shop) }
                .testTag("shop_card_${shop.id}"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
              Column {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                  AsyncImage(
                    model = shop.coverUrl,
                    contentDescription = shop.businessName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                  Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .padding(8.dp)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.Star, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(12.dp))
                      Spacer(modifier = Modifier.width(3.dp))
                      Text(
                        text = "${shop.rating} (${shop.reviewsCount})",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }

                Column(modifier = Modifier.padding(14.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = shop.businessName,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                      imageVector = Icons.Default.Store,
                      contentDescription = "Verified Shop",
                      tint = VybeVerifiedGold,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  Text(
                    text = "${shop.category} • ${shop.locationAddress}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = shop.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                  )
                }
              }
            }
          }
        }

        // Trending Topics derived strictly from real published posts (never mock tags)
        if (trendingTags.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
              text = "Trending Topics",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(trendingTags) { tag ->
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.setSearchQuery("#$tag") }
                ) {
                  Text(
                    text = "#$tag",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = VybeViolet,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                  )
                }
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
}

@Composable
private fun ExploreExperienceCard(
  title: String,
  subtitle: String,
  description: String,
  cta: String,
  accent: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  showLiveDot: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .clip(RoundedCornerShape(18.dp))
      .clickable { onClick() },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.2.dp, accent.copy(alpha = 0.55f))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(accent.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        if (showLiveDot) {
          Spacer(modifier = Modifier.width(5.dp))
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(VybeOnlineGreen)
          )
        }
      }
      Text(subtitle, fontSize = 11.sp, color = accent, fontWeight = FontWeight.SemiBold)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = description,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 14.sp
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "$cta →",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = accent
      )
    }
  }
}

@Composable
private fun ExploreExperienceFeedCard(
  title: String,
  tagline: String,
  description: String,
  badgeText: String,
  activityText: String,
  accent: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  ctaText: String,
  testTag: String,
  onEnter: () -> Unit,
  previewContent: @Composable () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .clip(RoundedCornerShape(20.dp))
      .clickable { onEnter() }
      .testTag(testTag),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.2.dp, accent.copy(alpha = 0.45f))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header row: Icon + Title & Tagline + Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f, fill = false),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
              text = title,
              fontWeight = FontWeight.Black,
              fontSize = 15.sp,
              letterSpacing = 0.6.sp,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = tagline,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = accent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          color = accent.copy(alpha = 0.15f),
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
        ) {
          Text(
            text = badgeText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = description,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 16.sp
      )

      Spacer(modifier = Modifier.height(12.dp))
      // Real preview slot
      previewContent()

      Spacer(modifier = Modifier.height(12.dp))
      // Bottom Action Bar: Activity indicator on left, Enter Zone Button on right
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(accent)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = activityText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Button(
          onClick = onEnter,
          colors = ButtonDefaults.buttonColors(containerColor = accent),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier.testTag("${testTag}_cta")
        ) {
          Text(
            text = ctaText,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
          )
        }
      }
    }
  }
}

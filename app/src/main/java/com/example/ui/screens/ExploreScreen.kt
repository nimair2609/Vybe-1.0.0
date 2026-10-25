package com.example.ui.screens

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
import androidx.compose.material.icons.filled.*
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
  val allUsers by viewModel.allUsers.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val adminReports by viewModel.adminReports.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  var roomCodeInput by remember { mutableStateOf("") }
  var roomCodeJoining by remember { mutableStateOf(false) }
  var roomCodeError by remember { mutableStateOf<String?>(null) }

  val trendingTags = listOf(
    "MumbaiLoFi",
    "CricketIndia",
    "DesignEthics",
    "MonsoonCoffee",
    "BangaloreTech",
    "IndieCinema"
  )

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

  Scaffold(
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

        if (matchedUsers.isEmpty() && matchedShops.isEmpty() && matchedPosts.isEmpty() && matchedRooms.isEmpty()) {
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
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onSaveClick = { viewModel.toggleSave(post.id) },
                onCommentClick = { viewModel.loadCommentsForPost(post.id) },
                onAddComment = { text -> viewModel.addComment(post.id, text) },
                onLoadComments = { viewModel.loadCommentsForPost(post.id) },
                onShareClick = {},
                onUserClick = { u -> viewModel.openUserProfile(u) },
                onRemixClick = { viewModel.openReelViewer(post.id) },
                onDeleteClick = { viewModel.deletePost(post.id) },
                onPostViewed = { viewModel.recordPostView(it) },
                currentUserId = currentUser?.id
              )
            }
          }
        }
      } else {
        // Core VYBE Experiences Header
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f))
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "CORE VYBE EXPERIENCES",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.8.sp,
                    color = VybeBrandCyan
                  )
                }
                Surface(
                  color = VybeViolet.copy(alpha = 0.22f),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(
                    text = "SIGNATURE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeViolet,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Six intentional ways to connect beyond algorithms and vanity metrics.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        // Pair 1: UNLOAD & UNFILTERED
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            ExploreExperienceCard(
              title = "UNLOAD",
              subtitle = "24/7 Live Listening",
              description = "Zero audio recordings. Ephemeral voice & chat sanctuary.",
              cta = "Enter Sanctuary",
              accent = VybeViolet,
              icon = Icons.Default.GraphicEq,
              showLiveDot = true,
              onClick = { viewModel.navigateTo(Screen.UNLOAD_VIEW) },
              modifier = Modifier
                .weight(1f)
                .testTag("explore_unload_card")
            )

            ExploreExperienceCard(
              title = "UNFILTERED",
              subtitle = "Raw & Real Moments",
              description = "Authentic snapshots without heavy filters or edits.",
              cta = "Post Unfiltered",
              accent = VybeOnlineGreen,
              icon = Icons.Default.CameraEnhance,
              onClick = { viewModel.navigateTo(Screen.UNFILTERED_VIEW) },
              modifier = Modifier
                .weight(1f)
                .testTag("explore_unfiltered_card")
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Pair 2: WHAT NEXT & TIME CAPSULE
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            ExploreExperienceCard(
              title = "WHAT NEXT",
              subtitle = "Culture Pulse Polls",
              description = "Vote on community dilemmas and predictions.",
              cta = "Vote Now",
              accent = VybeBrandCyan,
              icon = Icons.Default.Poll,
              onClick = { viewModel.navigateTo(Screen.WHAT_NEXT_VIEW) },
              modifier = Modifier
                .weight(1f)
                .testTag("explore_what_next_card")
            )

            ExploreExperienceCard(
              title = "TIME CAPSULE",
              subtitle = "Sealed Vaults",
              description = "Lock memories for future dates with trusted friends.",
              cta = "Seal Memory",
              accent = VybeVerifiedGold,
              icon = Icons.Default.Schedule,
              onClick = { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) },
              modifier = Modifier
                .weight(1f)
                .testTag("explore_time_capsule_card")
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Pair 3: DOUBLE TAKE & CROSSED PATHS
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            ExploreExperienceCard(
              title = "DOUBLE TAKE",
              subtitle = "Two-Sided Prompts",
              description = "Connect over thoughtful public and peer perspectives.",
              cta = "Sync Perspectives",
              accent = VybeHeartbreakRed,
              icon = Icons.Default.CompareArrows,
              onClick = { viewModel.navigateTo(Screen.DOUBLE_TAKE_VIEW) },
              modifier = Modifier
                .weight(1f)
                .testTag("explore_double_take_card")
            )

            ExploreExperienceCard(
              title = "CROSSED PATHS",
              subtitle = "Real-World Spark",
              description = "Consent-first encounters by venue and Google Maps.",
              cta = "Open Crossed Paths",
              accent = VybeBrandCyan,
              icon = Icons.Default.Place,
              onClick = { viewModel.navigateTo(Screen.CREATE_CROSSED_PATH) },
              modifier = Modifier
                .weight(1f)
                .testTag("explore_crossed_card")
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
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

        // Trending Topics (Clickable to search)
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

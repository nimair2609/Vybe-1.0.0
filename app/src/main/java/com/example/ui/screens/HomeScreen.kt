package com.example.ui.screens

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
import com.example.data.model.LivePulseStatus
import com.example.data.model.MoodType
import com.example.data.model.TimeCapsule
import com.example.data.model.UnloadRoom
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.FindYourVybeSection
import com.example.ui.components.StoryCarousel
import com.example.ui.components.StoryCreateDialog
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeMatchesManagerDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * VYBE Original Experience Home Screen.
 * Showcases VYBE's unique social DNA:
 * 1. Vybe Unload (Live Voice/Text Rooms)
 * 2. Time Capsule (Scheduled Messages & Memories)
 * 3. Crossed Paths (Location-Based Discovery)
 * 4. Nearby & Local Communities
 * 5. Explore Zone & Mood Zones
 * 6. Vybe Matches (Mutual Connections)
 * 7. Communities Hub
 * 8. Trending Pulse & Direct Feed Gateway
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: VybeViewModel) {
  val currentUser by viewModel.currentUser.collectAsState()
  val unloadRooms by viewModel.unloadRooms.collectAsState()
  val timeCapsules by viewModel.timeCapsules.collectAsState()
  val crossedPaths by viewModel.crossedPaths.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()
  val communities by viewModel.communities.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val stories by viewModel.stories.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val selectedMood by viewModel.selectedMoodFilter.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  var showMatchesDialog by remember { mutableStateOf(false) }
  var showStoryCreateSheet by remember { mutableStateOf(false) }

  val unreadNotificationsCount = remember(notifications) {
    notifications.count { !it.isRead }
  }

  // Calculate trending tags from real posts
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
      .take(8)
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .testTag("home_top_bar")
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // Brand Logo & Parent Identity
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { viewModel.selectTab(MainTab.FEED) }
            ) {
              Image(
                painter = painterResource(id = R.drawable.vybe_main_logo),
                contentDescription = "VYBE Logo",
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "VYBE",
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Black,
                  letterSpacing = 1.5.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "NR NEXT",
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp,
                  color = VybeBrandViolet
                )
              }
            }

            // Right Actions: Live Pulse, Matches Badge, Notifications
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Current User Live Pulse Badge
              val pulse = currentUser?.livePulse ?: LivePulseStatus.OPEN_TO_CHAT
              Surface(
                color = VybeVioletDark.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, VybeBrandViolet.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.clickable { viewModel.navigateTo(Screen.SETTINGS) }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(7.dp)
                      .clip(CircleShape)
                      .background(VybeOnlineGreen)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = pulse.name.take(6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VybeBrandCyan
                  )
                }
              }

              // Create / Unfiltered quick trigger
              IconButton(
                onClick = { viewModel.navigateTo(Screen.CREATE_POST) },
                modifier = Modifier
                  .size(40.dp)
                  .testTag("home_create_action")
              ) {
                Icon(
                  imageVector = Icons.Default.AddCircleOutline,
                  contentDescription = "Create",
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }

              // Notifications Bell with Unread Badge
              Box(modifier = Modifier.size(40.dp)) {
                IconButton(
                  onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
                  modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_notification_bell")
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
                      .padding(top = 4.dp, end = 4.dp)
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

          // Stories Row
          StoryCarousel(
            stories = stories,
            currentUser = currentUser,
            onAddStoryClick = { showStoryCreateSheet = true },
            onStoryClick = { story -> viewModel.openStory(story) },
            modifier = Modifier.padding(bottom = 6.dp)
          )
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

      // --- SECTION 1: VYBE UNLOAD HERO CARD ---
      item {
        val activeRooms = unloadRooms.filter { it.isActive }
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 6.dp,
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, VybeBrandViolet.copy(alpha = 0.45f)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.navigateTo(Screen.UNLOAD_VIEW) }
            .testTag("home_unload_hero_card")
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.verticalGradient(
                  listOf(
                    VybeVioletDark.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surface
                  )
                )
              )
              .padding(18.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VybeBrandViolet.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = VybeBrandCyan,
                    modifier = Modifier.size(24.dp)
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = "VYBE Unload",
                      fontSize = 17.sp,
                      fontWeight = FontWeight.Black,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(VybeDanger)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                      Text(
                        text = "LIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                      )
                    }
                  }
                  Text(
                    text = "Real-time audio & text safe-space rooms",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open Unload",
                tint = VybeBrandViolet,
                modifier = Modifier.size(16.dp)
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (activeRooms.isNotEmpty()) {
              val firstRoom = activeRooms.first()
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.6.dp, VybeBrandCyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = firstRoom.title,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "#${firstRoom.topicTag} • Host: ${firstRoom.hostName}",
                      fontSize = 11.sp,
                      color = VybeBrandCyan
                    )
                  }
                  Surface(
                    color = VybeBrandViolet,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { viewModel.joinUnloadRoom(firstRoom) }
                  ) {
                    Text(
                      text = "Join (${firstRoom.activeListenersCount})",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                  }
                }
              }
            } else {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "No active rooms right now. Host one!",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                  onClick = { viewModel.navigateTo(Screen.UNLOAD_VIEW) },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeBrandViolet),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text("Start Room", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }

      // --- FIND YOUR VYBE: LINK UP & SUGGEST ---
      item(key = "home_find_your_vybe") {
        FindYourVybeSection(viewModel = viewModel)
      }

      // --- SECTION 2: DUAL CARDS (TIME CAPSULE & CROSSED PATHS) ---
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Time Capsule Card
          val pendingCapsules = timeCapsules.filter { !it.isUnlocked }
          Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, VybeBrandMagenta.copy(alpha = 0.35f)),
            modifier = Modifier
              .weight(1f)
              .clickable { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) }
              .testTag("home_time_capsule_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .background(
                  Brush.verticalGradient(
                    listOf(
                      Color(0xFF831843).copy(alpha = 0.25f),
                      MaterialTheme.colorScheme.surface
                    )
                  )
                )
                .padding(14.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(VybeBrandMagenta.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.HourglassTop,
                  contentDescription = null,
                  tint = VybeBrandMagenta,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Time Capsule",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (pendingCapsules.isNotEmpty()) "${pendingCapsules.size} locked memories" else "Lock a future memory",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "Open Capsules",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeBrandMagenta
                )
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = VybeBrandMagenta,
                  modifier = Modifier.size(12.dp)
                )
              }
            }
          }

          // Crossed Paths Card
          Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, VybeBrandCyan.copy(alpha = 0.35f)),
            modifier = Modifier
              .weight(1f)
              .clickable { viewModel.navigateTo(Screen.CREATE_CROSSED_PATH) }
              .testTag("home_crossed_paths_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .background(
                  Brush.verticalGradient(
                    listOf(
                      Color(0xFF0369A1).copy(alpha = 0.25f),
                      MaterialTheme.colorScheme.surface
                    )
                  )
                )
                .padding(14.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(VybeBrandCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.NearMe,
                  contentDescription = null,
                  tint = VybeBrandCyan,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Crossed Paths",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "${crossedPaths.size} encounters near you",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "Scan Radar",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeBrandCyan
                )
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = VybeBrandCyan,
                  modifier = Modifier.size(12.dp)
                )
              }
            }
          }
        }
      }

      // --- SECTION 3: VYBE MATCHES DRAWER BANNER ---
      item {
        val pendingMatches = vybeMatches.size
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 4.dp,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, VybeBrandViolet.copy(alpha = 0.3f)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showMatchesDialog = true }
            .testTag("home_vybe_matches_banner")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.sweepGradient(listOf(VybeBrandViolet, VybeBrandMagenta, VybeBrandCyan))
                  ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Favorite,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Vybe Matches",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
                Text(
                  text = "$pendingMatches mutual connections active",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Surface(
              color = VybeBrandViolet.copy(alpha = 0.15f),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(0.6.dp, VybeBrandViolet.copy(alpha = 0.5f))
            ) {
              Text(
                text = "Manage",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VybeBrandViolet,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
              )
            }
          }
        }
      }

      // --- SECTION 4: NEARBY COMMUNITIES ---
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Communities & Zones",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "See All",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = VybeBrandViolet,
              modifier = Modifier.clickable { viewModel.navigateTo(Screen.COMMUNITY_ZONE) }
            )
          }
          Spacer(modifier = Modifier.height(10.dp))

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
          ) {
            items(communities.take(6), key = { "home_comm_${it.id}" }) { community ->
              Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .width(150.dp)
                  .clickable { viewModel.openCommunity(community) }
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  AsyncImage(
                    model = community.avatarUrl,
                    contentDescription = community.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceVariant)
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = community.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${community.membersCount} members",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      // --- SECTION 5: WHAT'S HAPPENING / TRENDING PULSE ---
      if (trendingTags.isNotEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.TrendingUp,
                  contentDescription = null,
                  tint = VybeBrandCyan,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "What's Happening on VYBE",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                trendingTags.forEach { tag ->
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.5.dp, VybeBrandCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                      viewModel.setSearchQuery("#$tag")
                      viewModel.selectTab(MainTab.EXPLORE)
                    }
                  ) {
                    Text(
                      text = "#$tag",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = VybeBrandCyan,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      // --- SECTION 6: DIRECT FEED EXPLORATION BANNER ---
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, VybeBrandViolet.copy(alpha = 0.3f)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.selectTab(MainTab.EXPLORE) }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Looking for Traditional Feed?",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Text(
                text = "Browse photo carousels, videos & creator mood zones in Explore.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Button(
              onClick = { viewModel.selectTab(MainTab.EXPLORE) },
              colors = ButtonDefaults.buttonColors(containerColor = VybeBrandViolet),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Go to Feed", fontSize = 12.sp)
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(30.dp))
      }
    }
  }

  if (showStoryCreateSheet) {
    StoryCreateDialog(
      viewModel = viewModel,
      onDismiss = { showStoryCreateSheet = false },
      onStoryPublished = { showStoryCreateSheet = false }
    )
  }

  if (showMatchesDialog && currentUser != null) {
    VybeMatchesManagerDialog(
      currentUserId = currentUser!!.id,
      allUsers = allUsers,
      selectedMatches = vybeMatches,
      onAddMatch = { viewModel.addVybeMatch(it) },
      onRemoveMatch = { viewModel.removeVybeMatch(it) },
      onDismiss = { showMatchesDialog = false }
    )
  }
}

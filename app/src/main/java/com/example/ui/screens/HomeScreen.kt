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
import com.example.ui.components.PostCard
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
  val feedMode by viewModel.feedMode.collectAsState()
  val circles by viewModel.circles.collectAsState()
  val growJourneys by viewModel.growJourneys.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val hiddenPostIds by viewModel.hiddenPostIds.collectAsState()
  val mutedCreatorIds by viewModel.mutedCreatorIds.collectAsState()
  val isFeedAudioEnabled by viewModel.isFeedAudioEnabled.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  var showMatchesDialog by remember { mutableStateOf(false) }
  var showStoryCreateSheet by remember { mutableStateOf(false) }

  val unreadNotificationsCount = remember(notifications) {
    notifications.count { !it.isRead }
  }

  val videoReels = remember(posts) {
    posts.filter { it.mediaType == com.example.data.model.MediaType.REEL || it.mediaType == com.example.data.model.MediaType.VIDEO || it.isVybesReel }
  }

  val modePosts = remember(posts, feedMode, followingUserIds, hiddenPostIds, mutedCreatorIds) {
    val nonHidden = posts.filter { !it.isArchived && !hiddenPostIds.contains(it.id) && !mutedCreatorIds.contains(it.author.id) }
    when (feedMode) {
      "FOLLOWING" -> nonHidden.filter { followingUserIds.contains(it.author.id) }
      "TRENDING" -> nonHidden.sortedByDescending { it.likesCount + it.commentsCount }
      else -> nonHidden // FOR_YOU: personalized hybrid stream
    }
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
            modifier = Modifier.padding(bottom = 2.dp)
          )

          // VYBE Home Feed 2.0 Mode Switcher (FOR YOU, FOLLOWING, TRENDING)
          val feedModes = listOf(
            "FOR_YOU" to "For You",
            "FOLLOWING" to "Following",
            "TRENDING" to "Trending"
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp)
              .testTag("home_feed_mode_selector"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            feedModes.forEach { (modeKey, label) ->
              val isSelected = (feedMode == modeKey) || (feedMode.isBlank() && modeKey == "FOR_YOU")
              Surface(
                color = if (isSelected) VybeBrandViolet else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp),
                border = if (isSelected) null else BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .clip(RoundedCornerShape(20.dp))
                  .clickable { viewModel.setFeedMode(modeKey) }
                  .testTag("feed_mode_$modeKey")
              ) {
                Text(
                  text = label,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
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
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

      // --- QUICK DISCOVERY GATEWAYS ---
      item(key = "home_quick_gateways") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // 1. VYBE Circle
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, VybeBrandViolet.copy(alpha = 0.5f)),
            modifier = Modifier
              .clickable { viewModel.navigateTo(Screen.VYBE_CIRCLE) }
              .testTag("gateway_vybe_circle")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(CircleShape)
                  .background(VybeBrandViolet.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Group,
                  contentDescription = null,
                  tint = VybeBrandViolet,
                  modifier = Modifier.size(14.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("VYBE Circle", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${circles.size} communities", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          // 2. VYBE Grow
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, VybeBrandCyan.copy(alpha = 0.5f)),
            modifier = Modifier
              .clickable { viewModel.navigateTo(Screen.VYBE_GROW) }
              .testTag("gateway_vybe_grow")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(CircleShape)
                  .background(VybeBrandCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.TrendingUp,
                  contentDescription = null,
                  tint = VybeBrandCyan,
                  modifier = Modifier.size(14.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("VYBE Grow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${growJourneys.size} journeys", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          // 3. Spiritual Mode
          val isSpiritual by viewModel.isSpiritualMode.collectAsState()
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
            modifier = Modifier
              .clickable { viewModel.navigateTo(Screen.SPIRITUAL_MODE) }
              .testTag("gateway_spiritual_mode")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.SelfImprovement,
                  contentDescription = null,
                  tint = Color(0xFF10B981),
                  modifier = Modifier.size(14.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("Spiritual Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(if (isSpiritual) "Active" else "Mindful Space", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          // 4. VYBE Unload
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.5f)),
            modifier = Modifier
              .clickable { viewModel.navigateTo(Screen.UNLOAD_VIEW) }
              .testTag("gateway_vybe_unload")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(24.dp)
                  .clip(CircleShape)
                  .background(VybeDanger.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.GraphicEq,
                  contentDescription = null,
                  tint = VybeDanger,
                  modifier = Modifier.size(14.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("Live Audio", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${unloadRooms.count { it.isActive }} rooms", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }

      // --- SOMETHING NEW FOR YOU (Personalized Hybrid Discovery) ---
      item(key = "home_something_new") {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 3.dp,
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.dp, VybeBrandViolet.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.linearGradient(
                  listOf(
                    VybeBrandViolet.copy(alpha = 0.12f),
                    MaterialTheme.colorScheme.surface
                  )
                )
              )
              .padding(14.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = VybeBrandViolet,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "Something New For You",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
              Text(
                text = "Discovery",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = VybeBrandCyan
              )
            }
            Spacer(modifier = Modifier.height(10.dp))

            val recommendedCircle = circles.firstOrNull()
            val recommendedJourney = growJourneys.firstOrNull()
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              if (recommendedCircle != null) {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.navigateTo(Screen.VYBE_CIRCLE) }
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                      text = "Circle to Join",
                      fontSize = 10.sp,
                      color = VybeBrandViolet,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = recommendedCircle.name,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "${recommendedCircle.membersCount} members",
                      fontSize = 10.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
              if (recommendedJourney != null) {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.navigateTo(Screen.VYBE_GROW) }
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                      text = "Habit Journey",
                      fontSize = 10.sp,
                      color = VybeBrandCyan,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = recommendedJourney.title,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "${recommendedJourney.durationDays}-day streak",
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

      // --- VYBE REELS PREVIEW RAIL ---
      if (videoReels.isNotEmpty()) {
        item(key = "home_reels_preview") {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                  imageVector = Icons.Default.PlayArrow,
                  contentDescription = null,
                  tint = VybeBrandMagenta,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "Trending Vybe Reels",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
              Text(
                text = "Watch All",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VybeBrandMagenta,
                modifier = Modifier.clickable { viewModel.selectTab(MainTab.VYBES) }
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(videoReels.take(8), key = { "reel_rail_${it.id}" }) { reel ->
                Surface(
                  color = Color(0xFF111827),
                  shape = RoundedCornerShape(14.dp),
                  border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { viewModel.openReelViewer(reel.id) }
                ) {
                  Box(modifier = Modifier.fillMaxSize()) {
                    val mediaUrl = reel.mediaUrls.firstOrNull().orEmpty()
                    if (mediaUrl.isNotBlank()) {
                      AsyncImage(
                        model = mediaUrl,
                        contentDescription = reel.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                      )
                    }
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .background(
                          Brush.verticalGradient(
                            listOf(
                              Color.Transparent,
                              Color.Black.copy(alpha = 0.75f)
                            )
                          )
                        )
                    )
                    Box(
                      modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Reel",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Column(
                      modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                      ) {
                        VybeAvatar(user = reel.author, size = 18.dp)
                        Text(
                          text = reel.author.username,
                          color = Color.White,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                      if (reel.viewsCount > 0) {
                        Text(
                          text = "${reel.viewsCount} views",
                          color = Color.White.copy(alpha = 0.75f),
                          fontSize = 9.sp
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

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

      // --- SECTION 6: INTELLIGENT HOME FEED 2.0 POST STREAM ---
      item(key = "home_feed_header") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.FilterList,
              contentDescription = null,
              tint = VybeBrandViolet,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = when (feedMode) {
                "FOLLOWING" -> "From Followed Creators"
                "TRENDING" -> "Trending Across VYBE"
                else -> "Personal Discovery Feed"
              },
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Text(
            text = "${modePosts.size} posts",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (modePosts.isEmpty()) {
        item(key = "home_feed_empty") {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                modifier = Modifier
                  .size(54.dp)
                  .clip(CircleShape)
                  .background(VybeBrandViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Group,
                  contentDescription = null,
                  tint = VybeBrandViolet,
                  modifier = Modifier.size(28.dp)
                )
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = if (feedMode == "FOLLOWING") "No posts from followed creators yet" else "No posts to show right now",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (feedMode == "FOLLOWING") {
                  "LINK UP with creators in Explore or VYBE Grow to see their moments here."
                } else {
                  "Share your first story or post to inspire the community!"
                },
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { viewModel.setFeedMode("FOR_YOU") },
                colors = ButtonDefaults.buttonColors(containerColor = VybeBrandViolet),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text("Explore For You Feed", fontSize = 12.sp)
              }
            }
          }
        }
      } else {
        items(modePosts, key = { "home_post_${it.id}" }) { post ->
          PostCard(
            post = post,
            onLikeClick = { viewModel.toggleLike(post.id) },
            onDoubleTapLike = { viewModel.ensurePostLoved(post.id) },
            onReact = { rx -> viewModel.reactToPost(post.id, rx) },
            onSaveClick = { viewModel.toggleSave(post.id) },
            onCommentClick = { viewModel.loadCommentsForPost(post.id) },
            onAddComment = { text -> viewModel.addComment(post.id, text) },
            onLoadComments = { viewModel.loadCommentsForPost(post.id) },
            onReactComment = { commentId, rx -> viewModel.reactToComment(post.id, commentId, rx) },
            onShareClick = { viewModel.openPostShareSheet(post) },
            onUserClick = { user -> viewModel.openUserProfile(user) },
            onRemixClick = { viewModel.openReelViewer(post.id) },
            onDeleteClick = { viewModel.deletePost(post.id) },
            onHidePost = { viewModel.hidePost(post.id) },
            onMuteCreator = { viewModel.muteCreator(post.author.id) },
            onReportPost = { reason ->
              coroutineScope.launch {
                viewModel.submitReport("POST", post.id, reason)
                viewModel.postBannerMessage("Report submitted for review.")
              }
            },
            onPostViewed = { viewModel.recordPostView(it) },
            currentUserId = currentUser?.id,
            isFollowing = followingUserIds.contains(post.author.id),
            isFollowRequested = requestedFollowUserIds.contains(post.author.id) || post.author.isFollowRequestedByMe,
            followsMe = post.author.followsMe,
            onFollowClick = if (post.author.id != currentUser?.id) { { viewModel.toggleFollowUser(post.author.id) } } else null
          )
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

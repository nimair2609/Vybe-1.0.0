package com.example.ui.screens

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
import com.example.data.model.MediaType
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.FindYourVybeSection
import com.example.ui.components.PostCard
import com.example.ui.components.StoryCarousel
import com.example.ui.components.StoryCreateDialog
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeBrandHeaderLogo
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeMatchesManagerDialog
import com.example.ui.components.VybeModeSwitch
import com.example.ui.components.VybeNotificationBellButton
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: VybeViewModel,
  showTopHeader: Boolean = true
) {
  val palette = LocalVybePalette.current
  val currentUser by viewModel.currentUser.collectAsState()
  val unloadRooms by viewModel.unloadRooms.collectAsState()
  val timeCapsules by viewModel.timeCapsules.collectAsState()
  val crossedPaths by viewModel.crossedPaths.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()
  val communities by viewModel.communities.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val stories by viewModel.stories.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val feedMode by viewModel.feedMode.collectAsState()
  val unfilteredPosts by viewModel.unfilteredPosts.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val hiddenPostIds by viewModel.hiddenPostIds.collectAsState()
  val mutedCreatorIds by viewModel.mutedCreatorIds.collectAsState()
  val isSpiritual by viewModel.isSpiritualMode.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  var showMatchesDialog by remember { mutableStateOf(false) }
  var showStoryCreateSheet by remember { mutableStateOf(false) }
  var showLivePulseDialog by remember { mutableStateOf(false) }

  val unreadNotificationsCount = remember(notifications) {
    notifications.count { !it.isRead }
  }

  val videoReels = remember(posts) {
    posts.filter { it.mediaType == MediaType.REEL || it.mediaType == MediaType.VIDEO || it.isVybesReel }
  }

  val modePosts = remember(posts, feedMode, followingUserIds, hiddenPostIds, mutedCreatorIds) {
    val nonHidden = posts.filter { !it.isArchived && !hiddenPostIds.contains(it.id) && !mutedCreatorIds.contains(it.author.id) }
    when (feedMode) {
      "FOLLOWING" -> nonHidden.filter { followingUserIds.contains(it.author.id) }
      "TRENDING" -> nonHidden.sortedByDescending { it.likesCount + it.commentsCount }
      else -> nonHidden
    }
  }

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
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      Surface(
        color = palette.canvas,
        modifier = Modifier
          .fillMaxWidth()
          .let { if (showTopHeader) it.statusBarsPadding() else it }
          .testTag("home_top_bar")
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          if (showTopHeader) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              VybeBrandHeaderLogo(
                onClick = { viewModel.selectTab(MainTab.FEED) }
              )

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                VybeModeSwitch(
                  currentMode = VybeExperienceMode.SOCIAL,
                  onModeChange = { viewModel.setExperienceMode(it) }
                )
                VybeNotificationBellButton(
                  unreadCount = unreadNotificationsCount,
                  onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
                  testTag = "home_notification_bell"
                )
                if (currentUser != null) {
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .clickable { viewModel.selectTab(MainTab.PROFILE) },
                    contentAlignment = Alignment.Center
                  ) {
                    VybeAvatar(user = currentUser!!, size = 34.dp, showPulseDot = false)
                  }
                }
              }
            }
          } else {
            Spacer(modifier = Modifier.height(6.dp))
          }

          // Compact status strip: Live Pulse + Quick Create
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val pulse = currentUser?.livePulse ?: LivePulseStatus.OPEN_TO_CHAT
            Surface(
              color = palette.elevated,
              border = BorderStroke(1.dp, palette.border),
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              modifier = Modifier
                .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                .clickable { showLivePulseDialog = true }
                .testTag("home_live_pulse_badge")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                      if (pulse != LivePulseStatus.NONE && pulse != LivePulseStatus.QUIET_TIME) palette.success else palette.textMuted
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = livePulseOutlineIcon(pulse),
                  contentDescription = null,
                  tint = palette.textSecondary,
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = pulse.label,
                  fontFamily = InterFontFamily,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }

            Surface(
              color = palette.accent.copy(alpha = 0.14f),
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
              modifier = Modifier
                .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                .clickable { viewModel.navigateTo(Screen.CREATE_POST) }
                .testTag("home_create_action")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.AddCircleOutline,
                  contentDescription = "Create",
                  tint = palette.accent,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = "New Post",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.accent
                )
              }
            }
          }

          // Stories Row
          StoryCarousel(
            stories = stories,
            currentUser = currentUser,
            onAddStoryClick = { showStoryCreateSheet = true },
            onStoryClick = { story -> viewModel.openStory(story) },
            modifier = Modifier.padding(bottom = 4.dp)
          )

          // VYBE Home Feed 2.0 Mode Switcher (FOR_YOU, FOLLOWING, TRENDING)
          val feedModes = listOf(
            "FOR_YOU" to "For You",
            "FOLLOWING" to "Following",
            "TRENDING" to "Trending"
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .testTag("home_feed_mode_selector"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            feedModes.forEach { (modeKey, label) ->
              val isSelected = (feedMode == modeKey) || (feedMode.isBlank() && modeKey == "FOR_YOU")
              Surface(
                color = if (isSelected) palette.accent else palette.elevated,
                shape = RoundedCornerShape(VybeTokens.Radius.pill),
                border = if (isSelected) null else BorderStroke(1.dp, palette.border),
                modifier = Modifier
                  .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                  .clickable { viewModel.setFeedMode(modeKey) }
                  .testTag("feed_mode_$modeKey")
              ) {
                Text(
                  text = label,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
              }
            }
          }
          HorizontalDivider(thickness = 0.8.dp, color = palette.border)
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

      // --- SOCIAL DISCOVERY GATEWAYS ---
      item(key = "home_quick_gateways") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          HomeGatewayPill(
            icon = Icons.Outlined.SelfImprovement,
            tint = palette.success,
            title = "Spiritual Mode",
            subtitle = if (isSpiritual) "Active" else "Mindful Space",
            palette = palette,
            testTag = "gateway_spiritual_mode",
            onClick = { viewModel.navigateTo(Screen.SPIRITUAL_MODE) }
          )
          HomeGatewayPill(
            icon = Icons.Outlined.GraphicEq,
            tint = palette.danger,
            title = "Live Audio",
            subtitle = "${unloadRooms.count { it.isActive }} rooms",
            palette = palette,
            testTag = "gateway_vybe_unload",
            onClick = { viewModel.navigateTo(Screen.UNLOAD_VIEW) }
          )
          HomeGatewayPill(
            icon = Icons.Outlined.Forum,
            tint = palette.accent,
            title = "Mood Zones",
            subtitle = "Expressive feeds",
            palette = palette,
            testTag = "gateway_mood_zones",
            onClick = { viewModel.selectTab(MainTab.MOOD_ZONES) }
          )
          HomeGatewayPill(
            icon = Icons.Outlined.HourglassEmpty,
            tint = palette.accentSecondary,
            title = "Time Capsules",
            subtitle = "${timeCapsules.size} sealed",
            palette = palette,
            testTag = "gateway_time_capsules",
            onClick = { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) }
          )
        }
      }

      // --- SOMETHING NEW FOR YOU (Social Discovery Only) ---
      item(key = "home_something_new") {
        VybeSurfaceCard(modifier = Modifier.fillMaxWidth()) {
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
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "Something New For You",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = palette.textPrimary
              )
            }
            Text(
              text = "Social Spaces",
              fontFamily = InterFontFamily,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.accentSecondary
            )
          }
          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              color = palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.border),
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable { viewModel.navigateTo(Screen.UNFILTERED_VIEW) }
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "Candid Feed",
                  fontSize = 10.sp,
                  color = palette.accent,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "Unfiltered",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${unfilteredPosts.size} raw moments",
                  fontSize = 11.sp,
                  color = palette.textSecondary
                )
              }
            }
            Surface(
              color = palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.border),
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable { viewModel.selectTab(MainTab.CROSSED_PATHS) }
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "Serendipity",
                  fontSize = 10.sp,
                  color = palette.accentSecondary,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "Crossed Paths",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${crossedPaths.size} shared spots",
                  fontSize = 11.sp,
                  color = palette.textSecondary
                )
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
                  imageVector = Icons.Default.PlayCircleOutline,
                  contentDescription = null,
                  tint = palette.accent,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "Trending Vybe Reels",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.textPrimary
                )
              }
              Text(
                text = "Watch All",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                modifier = Modifier.clickable { viewModel.selectTab(MainTab.VYBES) }
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(videoReels.take(8), key = { "reel_rail_${it.id}" }) { reel ->
                Surface(
                  color = palette.elevated,
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  border = BorderStroke(1.dp, palette.border),
                  modifier = Modifier
                    .width(124.dp)
                    .height(184.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.md))
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
                              Color.Black.copy(alpha = 0.78f)
                            )
                          )
                        )
                    )
                    Box(
                      modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
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
                          color = Color.White.copy(alpha = 0.78f),
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

      // --- VYBE UNLOAD HERO CARD ---
      item {
        val activeRooms = unloadRooms.filter { it.isActive }
        VybeSurfaceCard(
          onClick = { viewModel.navigateTo(Screen.UNLOAD_VIEW) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_unload_hero_card")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.md))
                  .background(palette.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.GraphicEq,
                  contentDescription = null,
                  tint = palette.accent,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "VYBE Unload",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Surface(
                    color = palette.danger,
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(
                      text = "LIVE",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
                Text(
                  text = "Real-time audio & text safe-space rooms",
                  fontSize = 12.sp,
                  color = palette.textSecondary
                )
              }
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
              contentDescription = "Open Unload",
              tint = palette.textMuted,
              modifier = Modifier.size(16.dp)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          if (activeRooms.isNotEmpty()) {
            val firstRoom = activeRooms.first()
            Surface(
              color = palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.border),
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
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "#${firstRoom.topicTag} • Host: ${firstRoom.hostName}",
                    fontSize = 11.sp,
                    color = palette.accentSecondary
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                  onClick = { viewModel.joinUnloadRoom(firstRoom) },
                  colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                  shape = RoundedCornerShape(VybeTokens.Radius.sm),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text(
                    text = "Join (${firstRoom.activeListenersCount})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
                text = "No active rooms right now.",
                fontSize = 12.sp,
                color = palette.textSecondary,
                modifier = Modifier.weight(1f)
              )
              Button(
                onClick = { viewModel.navigateTo(Screen.UNLOAD_VIEW) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(VybeTokens.Radius.sm)
              ) {
                Text("Start Room", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
              }
            }
          }
        }
      }

      // --- FIND YOUR VYBE: LINK UP & SUGGEST ---
      item(key = "home_find_your_vybe") {
        FindYourVybeSection(viewModel = viewModel)
      }

      // --- DUAL CARDS (TIME CAPSULE & CROSSED PATHS) ---
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          val pendingCapsules = timeCapsules.filter { !it.isUnlocked }
          VybeSurfaceCard(
            onClick = { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) },
            modifier = Modifier
              .weight(1f)
              .testTag("home_time_capsule_card")
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(palette.accent.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Time Capsule",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
            Text(
              text = if (pendingCapsules.isNotEmpty()) "${pendingCapsules.size} locked memories" else "Lock a future memory",
              fontSize = 11.sp,
              color = palette.textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "Open",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent
              )
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(13.dp)
              )
            }
          }

          VybeSurfaceCard(
            onClick = { viewModel.navigateTo(Screen.CREATE_CROSSED_PATH) },
            modifier = Modifier
              .weight(1f)
              .testTag("home_crossed_paths_card")
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(palette.accentSecondary.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.NearMe,
                contentDescription = null,
                tint = palette.accentSecondary,
                modifier = Modifier.size(18.dp)
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Crossed Paths",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
            Text(
              text = "${crossedPaths.size} encounters near you",
              fontSize = 11.sp,
              color = palette.textSecondary,
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentSecondary
              )
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = palette.accentSecondary,
                modifier = Modifier.size(13.dp)
              )
            }
          }
        }
      }

      // --- VYBE MATCHES DRAWER BANNER ---
      item {
        val pendingMatches = vybeMatches.size
        VybeSurfaceCard(
          onClick = { showMatchesDialog = true },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_vybe_matches_banner")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.md))
                  .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlue))),
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
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = palette.textPrimary
                )
                Text(
                  text = "$pendingMatches mutual connections active",
                  fontSize = 11.sp,
                  color = palette.textSecondary
                )
              }
            }
            Surface(
              color = palette.accent.copy(alpha = 0.14f),
              shape = RoundedCornerShape(VybeTokens.Radius.sm),
              border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f))
            ) {
              Text(
                text = "Manage",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }
        }
      }

      // --- NEARBY COMMUNITIES ---
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Communities & Zones",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
            Text(
              text = "See All",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = palette.accent,
              modifier = Modifier.clickable { viewModel.navigateTo(Screen.COMMUNITY_ZONE) }
            )
          }
          Spacer(modifier = Modifier.height(10.dp))

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(communities.take(6), key = { "home_comm_${it.id}" }) { community ->
              VybeSurfaceCard(
                onClick = { viewModel.openCommunity(community) },
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.width(156.dp)
              ) {
                AsyncImage(
                  model = community.avatarUrl,
                  contentDescription = community.name,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(palette.elevated)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = community.name,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${community.membersCount} members",
                  fontSize = 11.sp,
                  color = palette.textSecondary
                )
              }
            }
          }
        }
      }

      // --- WHAT'S HAPPENING / TRENDING PULSE ---
      item {
        VybeSurfaceCard(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_trending_pulse_section")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.TrendingUp,
              contentDescription = null,
              tint = palette.accentSecondary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "What's Happening on VYBE",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = palette.textPrimary
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          if (trendingTags.isNotEmpty()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              trendingTags.forEach { tag ->
                Surface(
                  color = palette.elevated,
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  border = BorderStroke(1.dp, palette.border),
                  modifier = Modifier
                    .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                    .clickable {
                      viewModel.setSearchQuery("#$tag")
                      viewModel.selectTab(MainTab.EXPLORE)
                    }
                ) {
                  Text(
                    text = "#$tag",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accentSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                  )
                }
              }
            }
          } else {
            Text(
              text = "No trending topics yet. Start a conversation and make something trend.",
              fontSize = 12.sp,
              color = palette.textSecondary,
              modifier = Modifier.padding(vertical = 4.dp)
            )
          }
        }
      }

      // --- INTELLIGENT HOME FEED 2.0 POST STREAM ---
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
              tint = palette.accent,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = when (feedMode) {
                "FOLLOWING" -> "From Followed Creators"
                "TRENDING" -> "Trending Across VYBE"
                else -> "Personal Discovery Feed"
              },
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
          }
          Text(
            text = "${modePosts.size} posts",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textSecondary
          )
        }
      }

      if (modePosts.isEmpty()) {
        item(key = "home_feed_empty") {
          VybeEmptyState(
            icon = Icons.Default.Group,
            title = if (feedMode == "FOLLOWING") "No posts from followed creators yet" else "No posts to show right now",
            subtitle = if (feedMode == "FOLLOWING") {
              "Link up with creators in Explore or Find Your Vybe to see their moments here."
            } else {
              "Share your first story or post to inspire the community."
            },
            actionLabel = "Explore For You Feed",
            onAction = { viewModel.setFeedMode("FOR_YOU") }
          )
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
        Spacer(modifier = Modifier.height(24.dp))
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

  if (showLivePulseDialog) {
    AlertDialog(
      onDismissRequest = { showLivePulseDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(palette.success)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Set Your Live Pulse",
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = palette.textPrimary
          )
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Broadcast your real-time presence to friends and the VYBE network.",
            fontSize = 12.sp,
            color = palette.textSecondary
          )
          Spacer(modifier = Modifier.height(4.dp))
          LivePulseStatus.values().forEach { status ->
            val isCurrent = (currentUser?.livePulse ?: LivePulseStatus.OPEN_TO_CHAT) == status
            Surface(
              color = if (isCurrent) palette.accent.copy(alpha = 0.16f) else palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, if (isCurrent) palette.accent else palette.border),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable {
                  viewModel.updateUserMoodAndPulse(currentUser?.currentMood, status)
                  showLivePulseDialog = false
                }
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = livePulseOutlineIcon(status),
                  contentDescription = null,
                  tint = if (isCurrent) palette.accent else palette.textSecondary,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = status.label,
                  fontSize = 13.sp,
                  fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                  color = if (isCurrent) palette.accent else palette.textPrimary,
                  modifier = Modifier.weight(1f)
                )
                if (isCurrent) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showLivePulseDialog = false }) {
          Text("Done", color = palette.accent, fontWeight = FontWeight.Bold)
        }
      }
    )
  }
}

@Composable
private fun HomeGatewayPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  title: String,
  subtitle: String,
  palette: VybePalette,
  testTag: String,
  onClick: () -> Unit
) {
  Surface(
    color = palette.card,
    shape = RoundedCornerShape(VybeTokens.Radius.md),
    border = BorderStroke(1.dp, palette.border),
    modifier = Modifier
      .clip(RoundedCornerShape(VybeTokens.Radius.md))
      .clickable(onClick = onClick)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(15.dp)
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(
          text = title,
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          color = palette.textPrimary
        )
        Text(
          text = subtitle,
          fontSize = 10.sp,
          color = palette.textSecondary
        )
      }
    }
  }
}

private fun livePulseOutlineIcon(status: LivePulseStatus): androidx.compose.ui.graphics.vector.ImageVector {
  return when (status) {
    LivePulseStatus.OPEN_TO_CHAT -> Icons.Outlined.ChatBubbleOutline
    LivePulseStatus.FEELING_CREATIVE -> Icons.Outlined.AutoAwesome
    LivePulseStatus.LISTENING_MUSIC -> Icons.Outlined.Headphones
    LivePulseStatus.GAMING_PARTNER -> Icons.Outlined.SportsEsports
    LivePulseStatus.QUIET_TIME -> Icons.Outlined.DarkMode
    LivePulseStatus.NONE -> Icons.Outlined.RadioButtonUnchecked
  }
}

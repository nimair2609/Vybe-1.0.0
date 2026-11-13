package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.theme.*

@Composable
fun MainScreen(viewModel: VybeViewModel) {
  val selectedTab by viewModel.selectedTab.collectAsState()
  val experienceMode by viewModel.experienceMode.collectAsState()
  val growTab by viewModel.growTab.collectAsState()
  val conversations by viewModel.conversations.collectAsState()
  val notifications by viewModel.notifications.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val palette = LocalVybePalette.current

  val unreadMessagesCount = remember(conversations) {
    conversations.sumOf { conv ->
      conv.messages.count { !it.isMe && !it.isRead && !it.isDeletedForEveryone }
    }
  }
  val unreadNotificationsCount = remember(notifications) {
    notifications.count { !it.isRead }
  }
  val showWorkspaceTopBar = experienceMode == VybeExperienceMode.GROW || selectedTab != MainTab.VYBES

  // Handle back press cleanly across both Social and Grow modes
  androidx.activity.compose.BackHandler(
    enabled = (experienceMode == VybeExperienceMode.SOCIAL && selectedTab != MainTab.FEED) ||
      (experienceMode == VybeExperienceMode.GROW && growTab != "HOME")
  ) {
    if (experienceMode == VybeExperienceMode.GROW) {
      viewModel.selectGrowTab("HOME")
    } else {
      viewModel.selectTab(MainTab.FEED)
    }
  }

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      if (showWorkspaceTopBar) {
        com.example.ui.components.VybeWorkspaceTopBar(
          experienceMode = experienceMode,
          currentUser = currentUser,
          unreadNotificationsCount = unreadNotificationsCount,
          onBrandClick = {
            if (experienceMode == VybeExperienceMode.GROW) {
              viewModel.selectGrowTab("HOME")
            } else {
              viewModel.selectTab(MainTab.FEED)
            }
          },
          onModeChange = { viewModel.setExperienceMode(it) },
          onNotificationsClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
          onProfileClick = {
            if (experienceMode == VybeExperienceMode.GROW) {
              viewModel.selectGrowTab("PROFILE")
            } else {
              viewModel.selectTab(MainTab.PROFILE)
            }
          }
        )
      }
    },
    bottomBar = {
      Surface(
        color = palette.card.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("main_bottom_nav")
      ) {
        Column {
          HorizontalDivider(thickness = 0.6.dp, color = palette.border)
          if (experienceMode == VybeExperienceMode.SOCIAL) {
            SocialBottomBar(
              selectedTab = selectedTab,
              unreadMessagesCount = unreadMessagesCount,
              palette = palette,
              onSelectTab = { viewModel.selectTab(it) },
              onCreateClick = { viewModel.navigateTo(Screen.CREATE_POST) }
            )
          } else {
            GrowBottomBar(
              activeGrowTab = growTab,
              palette = palette,
              onSelectGrowTab = { viewModel.selectGrowTab(it) }
            )
          }
        }
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      if (experienceMode == VybeExperienceMode.GROW) {
        when (growTab) {
          "HOME" -> GrowHomeWorkspace(viewModel)
          "CIRCLE" -> GrowCircleWorkspace(viewModel)
          "PROJECTS" -> GrowProjectsWorkspace(viewModel)
          "OPPORTUNITIES" -> GrowOpportunitiesWorkspace(viewModel)
          "PROFILE" -> GrowProfileWorkspace(viewModel)
          else -> GrowHomeWorkspace(viewModel)
        }
      } else {
        when (selectedTab) {
          MainTab.FEED -> HomeScreen(viewModel, showTopHeader = false)
          MainTab.EXPLORE -> ExploreScreen(viewModel, showTopBrandRow = false)
          MainTab.CREATE -> CreatePostScreen(viewModel)
          MainTab.VYBES -> ReelsViewerScreen(viewModel)
          MainTab.PROFILE -> UserProfileScreen(viewModel, forceOwnProfile = true, showTopOverlay = false)
          MainTab.INBOX -> InboxScreen(viewModel, embeddedInMainScreen = true)
          MainTab.MOOD_ZONES -> MoodZonesScreen(viewModel)
          MainTab.CROSSED_PATHS -> CrossedPathsScreen(viewModel)
        }
      }
    }
  }
}

@Composable
private fun SocialBottomBar(
  selectedTab: MainTab,
  unreadMessagesCount: Int,
  palette: VybePalette,
  onSelectTab: (MainTab) -> Unit,
  onCreateClick: () -> Unit
) {
  val activeTint = palette.accent
  val inactiveTint = palette.textMuted

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically
  ) {
    NavTabButton(
      label = "Feed",
      selected = selectedTab == MainTab.FEED,
      selectedIcon = Icons.Filled.Home,
      unselectedIcon = Icons.Outlined.Home,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      testTag = "nav_tab_feed",
      onClick = { onSelectTab(MainTab.FEED) },
      modifier = Modifier.weight(1f)
    )

    NavTabButton(
      label = "Explore",
      selected = selectedTab == MainTab.EXPLORE,
      selectedIcon = Icons.Filled.Search,
      unselectedIcon = Icons.Outlined.Search,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      testTag = "nav_tab_explore",
      onClick = { onSelectTab(MainTab.EXPLORE) },
      modifier = Modifier.weight(1f)
    )

    // Center elevated + Create circular FAB
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.weight(1f)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlue)))
          .clickable { onCreateClick() }
          .testTag("nav_tab_create")
      ) {
        Icon(
          imageVector = Icons.Filled.Add,
          contentDescription = "Create Post, Reel or Story",
          tint = Color.White,
          modifier = Modifier.size(26.dp)
        )
      }
    }

    NavTabButton(
      label = "Inbox",
      selected = selectedTab == MainTab.INBOX,
      selectedIcon = Icons.Filled.ChatBubble,
      unselectedIcon = Icons.Outlined.ChatBubbleOutline,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      badgeCount = unreadMessagesCount,
      testTag = "nav_tab_inbox",
      onClick = { onSelectTab(MainTab.INBOX) },
      modifier = Modifier.weight(1f)
    )

    NavTabButton(
      label = "Profile",
      selected = selectedTab == MainTab.PROFILE,
      selectedIcon = Icons.Filled.Person,
      unselectedIcon = Icons.Outlined.PersonOutline,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      testTag = "nav_tab_profile",
      onClick = { onSelectTab(MainTab.PROFILE) },
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun GrowBottomBar(
  activeGrowTab: String,
  palette: VybePalette,
  onSelectGrowTab: (String) -> Unit
) {
  val activeTint = palette.accent
  val inactiveTint = palette.textMuted

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically
  ) {
    NavTabButton(
      label = "Home",
      selected = activeGrowTab == "HOME",
      selectedIcon = Icons.Filled.Home,
      unselectedIcon = Icons.Outlined.Home,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      testTag = "grow_nav_home",
      onClick = { onSelectGrowTab("HOME") },
      modifier = Modifier.weight(1f)
    )
    NavTabButton(
      label = "Circle",
      selected = activeGrowTab == "CIRCLE",
      selectedIcon = Icons.Filled.Groups,
      unselectedIcon = Icons.Outlined.Groups,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      testTag = "grow_nav_circle",
      onClick = { onSelectGrowTab("CIRCLE") },
      modifier = Modifier.weight(1f)
    )
    NavTabButton(
      label = "Projects",
      selected = activeGrowTab == "PROJECTS",
      selectedIcon = Icons.Filled.Folder,
      unselectedIcon = Icons.Outlined.Folder,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      testTag = "grow_nav_projects",
      onClick = { onSelectGrowTab("PROJECTS") },
      modifier = Modifier.weight(1f)
    )
    NavTabButton(
      label = "Opportunities",
      selected = activeGrowTab == "OPPORTUNITIES",
      selectedIcon = Icons.Filled.WorkspacePremium,
      unselectedIcon = Icons.Outlined.WorkspacePremium,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      testTag = "grow_nav_opportunities",
      onClick = { onSelectGrowTab("OPPORTUNITIES") },
      modifier = Modifier.weight(1f)
    )
    NavTabButton(
      label = "Profile",
      selected = activeGrowTab == "PROFILE",
      selectedIcon = Icons.Filled.Person,
      unselectedIcon = Icons.Outlined.PersonOutline,
      activeTint = activeTint,
      inactiveTint = inactiveTint,
      testTag = "grow_nav_profile",
      onClick = { onSelectGrowTab("PROFILE") },
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun NavTabButton(
  label: String,
  selected: Boolean,
  selectedIcon: ImageVector,
  unselectedIcon: ImageVector,
  activeTint: Color,
  inactiveTint: Color,
  badgeCount: Int = 0,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .clip(RoundedCornerShape(VybeTokens.Radius.sm))
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp)
      .testTag(testTag)
  ) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      Icon(
        imageVector = if (selected) selectedIcon else unselectedIcon,
        contentDescription = label,
        tint = if (selected) activeTint else inactiveTint,
        modifier = Modifier.size(22.dp)
      )
      if (badgeCount > 0) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 5.dp, y = (-3).dp)
            .sizeIn(minWidth = 15.dp, minHeight = 15.dp)
            .clip(CircleShape)
            .background(VybeDanger)
            .padding(horizontal = 3.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (badgeCount > 9) "9+" else badgeCount.toString(),
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label,
      fontFamily = InterFontFamily,
      fontSize = 10.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) activeTint else inactiveTint,
      maxLines = 1
    )
  }
}

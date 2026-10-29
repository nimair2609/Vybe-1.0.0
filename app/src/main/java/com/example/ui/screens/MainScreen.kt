package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet

@Composable
fun MainScreen(viewModel: VybeViewModel) {
  val selectedTab by viewModel.selectedTab.collectAsState()
  val conversations by viewModel.conversations.collectAsState()
  val unreadTotal = remember(conversations) { conversations.sumOf { it.unreadCount } }

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    bottomBar = {
      Surface(
        color = Color(0xFF070A13),
        tonalElevation = 8.dp,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("main_bottom_nav")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(58.dp)
            .padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          // Tab 1: Home
          val isHome = selectedTab == MainTab.FEED
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .clip(CircleShape)
              .clickable { viewModel.selectTab(MainTab.FEED) }
              .testTag("nav_tab_feed")
          ) {
            Icon(
              imageVector = if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
              contentDescription = "Home",
              tint = if (isHome) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.65f),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Home",
              fontSize = 11.sp,
              fontWeight = if (isHome) FontWeight.Bold else FontWeight.Normal,
              color = if (isHome) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.65f)
            )
          }

          // Tab 2: Explore
          val isExplore = selectedTab == MainTab.EXPLORE
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .clip(CircleShape)
              .clickable { viewModel.selectTab(MainTab.EXPLORE) }
              .testTag("nav_tab_explore")
          ) {
            Icon(
              imageVector = if (isExplore) Icons.Filled.Explore else Icons.Outlined.Explore,
              contentDescription = "Explore",
              tint = if (isExplore) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.65f),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Explore",
              fontSize = 11.sp,
              fontWeight = if (isExplore) FontWeight.Bold else FontWeight.Normal,
              color = if (isExplore) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.65f)
            )
          }

          // Tab 3: Center Create Button (+) with Glowing Gradient Ring Border
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(
                  width = 2.dp,
                  brush = Brush.sweepGradient(
                    listOf(
                      Color(0xFF3B82F6),
                      Color(0xFF8B5CF6),
                      Color(0xFFEC4899),
                      Color(0xFF3B82F6)
                    )
                  ),
                  shape = CircleShape
                )
                .background(Color(0xFF070A13))
                .clickable { viewModel.navigateTo(Screen.CREATE_POST) }
                .testTag("nav_tab_create"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
              )
            }
          }

          // Tab 4: Reels (Short-Videos)
          val isReels = selectedTab == MainTab.VYBES
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .clip(CircleShape)
              .clickable { viewModel.selectTab(MainTab.VYBES) }
              .testTag("nav_tab_vybes")
          ) {
            Icon(
              imageVector = if (isReels) Icons.Filled.Movie else Icons.Outlined.Movie,
              contentDescription = "Reels",
              tint = if (isReels) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.65f),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Reels",
              fontSize = 11.sp,
              fontWeight = if (isReels) FontWeight.Bold else FontWeight.Normal,
              color = if (isReels) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.65f)
            )
          }

          // Tab 5: Profile
          val isProfile = selectedTab == MainTab.PROFILE
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .clip(CircleShape)
              .clickable { viewModel.selectTab(MainTab.PROFILE) }
              .testTag("nav_tab_profile")
          ) {
            Icon(
              imageVector = if (isProfile) Icons.Filled.Person else Icons.Outlined.Person,
              contentDescription = "Profile",
              tint = if (isProfile) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.65f),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Profile",
              fontSize = 11.sp,
              fontWeight = if (isProfile) FontWeight.Bold else FontWeight.Normal,
              color = if (isProfile) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.65f)
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
      when (selectedTab) {
        MainTab.FEED -> FeedScreen(viewModel)
        MainTab.VYBES -> ReelsViewerScreen(viewModel)
        MainTab.MOOD_ZONES -> MoodZonesScreen(viewModel)
        MainTab.EXPLORE -> ExploreScreen(viewModel)
        MainTab.INBOX -> InboxScreen(viewModel)
        MainTab.CROSSED_PATHS -> CrossedPathsScreen(viewModel)
        MainTab.CREATE -> CreatePostScreen(viewModel)
        MainTab.PROFILE -> UserProfileScreen(viewModel)
      }
    }
  }
}

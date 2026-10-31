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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeBrandMagenta
import com.example.ui.theme.VybeBrandViolet
import com.example.ui.theme.VybeVioletDark

@Composable
fun MainScreen(viewModel: VybeViewModel) {
  val selectedTab by viewModel.selectedTab.collectAsState()
  val isDark = LocalIsDarkTheme.current

  val navSurfaceColor = if (isDark) Color(0xFF070B14).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.97f)
  val navBorderColor = if (isDark) Color(0xFF161F33) else Color(0xFFE2E8F0)
  val activeTint = if (isDark) VybeBrandCyan else VybeVioletDark
  val inactiveTint = if (isDark) Color(0xFF8E9BAE) else Color(0xFF64748B)
  val createCenterBg = if (isDark) Color(0xFF070B14) else Color.White
  val createIconTint = if (isDark) Color.White else VybeVioletDark

  // Gracefully handle back press to return to Feed tab from any secondary tab
  androidx.activity.compose.BackHandler(enabled = selectedTab != MainTab.FEED) {
    viewModel.selectTab(MainTab.FEED)
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0.dp),
    bottomBar = {
      Surface(
        color = navSurfaceColor,
        tonalElevation = if (isDark) 8.dp else 3.dp,
        border = BorderStroke(0.6.dp, navBorderColor),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("main_bottom_nav")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // 1. Home
          val isHomeSelected = selectedTab == MainTab.FEED
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.selectTab(MainTab.FEED) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_feed")
          ) {
            Icon(
              imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
              contentDescription = "Home",
              tint = if (isHomeSelected) activeTint else inactiveTint,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Home",
              fontSize = 11.sp,
              fontWeight = if (isHomeSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isHomeSelected) activeTint else inactiveTint
            )
          }

          // 2. Explore
          val isExploreSelected = selectedTab == MainTab.EXPLORE
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.selectTab(MainTab.EXPLORE) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_explore")
          ) {
            Icon(
              imageVector = if (isExploreSelected) Icons.Filled.Explore else Icons.Outlined.Explore,
              contentDescription = "Explore",
              tint = if (isExploreSelected) activeTint else inactiveTint,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Explore",
              fontSize = 11.sp,
              fontWeight = if (isExploreSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isExploreSelected) activeTint else inactiveTint
            )
          }

          // 3. Create (+) - Centered elevated action with signature gradient border
          Box(
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                  Brush.sweepGradient(
                    listOf(VybeBrandCyan, VybeBrandViolet, VybeBrandMagenta, VybeBrandCyan)
                  )
                )
                .padding(2.dp)
                .clip(CircleShape)
                .background(createCenterBg)
                .clickable { viewModel.navigateTo(Screen.CREATE_POST) }
                .testTag("nav_tab_create"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Post",
                tint = createIconTint,
                modifier = Modifier.size(26.dp)
              )
            }
          }

          // 4. Vybe
          val isVybeSelected = selectedTab == MainTab.VYBES
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.selectTab(MainTab.VYBES) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_vybes")
          ) {
            Icon(
              imageVector = if (isVybeSelected) Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay,
              contentDescription = "Vybe",
              tint = if (isVybeSelected) activeTint else inactiveTint,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Vybe",
              fontSize = 11.sp,
              fontWeight = if (isVybeSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isVybeSelected) activeTint else inactiveTint
            )
          }

          // 5. Profile
          val isProfileSelected = selectedTab == MainTab.PROFILE
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.selectTab(MainTab.PROFILE) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_profile")
          ) {
            Icon(
              imageVector = if (isProfileSelected) Icons.Filled.Person else Icons.Outlined.Person,
              contentDescription = "Profile",
              tint = if (isProfileSelected) activeTint else inactiveTint,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Profile",
              fontSize = 11.sp,
              fontWeight = if (isProfileSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isProfileSelected) activeTint else inactiveTint
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
        MainTab.EXPLORE -> ExploreScreen(viewModel)
        MainTab.CREATE -> CreatePostScreen(viewModel)
        MainTab.VYBES -> ReelsViewerScreen(viewModel)
        MainTab.PROFILE -> UserProfileScreen(viewModel, forceOwnProfile = true)
        MainTab.INBOX -> InboxScreen(viewModel)
        MainTab.MOOD_ZONES -> MoodZonesScreen(viewModel)
        MainTab.CROSSED_PATHS -> CrossedPathsScreen(viewModel)
      }
    }
  }
}

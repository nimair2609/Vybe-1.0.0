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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainTab
import com.example.ui.VybeViewModel
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeBrandMagenta
import com.example.ui.theme.VybeBrandViolet
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet

@Composable
fun MainScreen(viewModel: VybeViewModel) {
  val selectedTab by viewModel.selectedTab.collectAsState()
  val conversations by viewModel.conversations.collectAsState()
  val unreadTotal = remember(conversations) { conversations.sumOf { it.unreadCount } }

  // Gracefully handle back press to return to Feed tab from any secondary tab
  androidx.activity.compose.BackHandler(enabled = selectedTab != MainTab.FEED) {
    viewModel.selectTab(MainTab.FEED)
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    bottomBar = {
      // Fixed translucent dark panel respecting system navigation insets
      Surface(
        color = Color(0xFF070B14).copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        border = BorderStroke(0.6.dp, Color(0xFF161F33)),
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
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.selectTab(MainTab.FEED) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_feed")
          ) {
            Icon(
              imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
              contentDescription = "Home",
              tint = if (isHomeSelected) VybeBrandCyan else Color(0xFF8E9BAE),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Home",
              fontSize = 11.sp,
              fontWeight = if (isHomeSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isHomeSelected) VybeBrandCyan else Color(0xFF8E9BAE)
            )
          }

          // 2. Explore
          val isExploreSelected = selectedTab == MainTab.EXPLORE
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.selectTab(MainTab.EXPLORE) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_explore")
          ) {
            Icon(
              imageVector = if (isExploreSelected) Icons.Filled.Explore else Icons.Outlined.Explore,
              contentDescription = "Explore",
              tint = if (isExploreSelected) VybeBrandCyan else Color(0xFF8E9BAE),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Explore",
              fontSize = 11.sp,
              fontWeight = if (isExploreSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isExploreSelected) VybeBrandCyan else Color(0xFF8E9BAE)
            )
          }

          // 3. Create (+) - Centered elevated action with signature gradient border
          Box(
            modifier = Modifier
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                  androidx.compose.ui.graphics.Brush.sweepGradient(
                    listOf(VybeBrandCyan, VybeBrandViolet, VybeBrandMagenta, VybeBrandCyan)
                  )
                )
                .padding(2.dp)
                .clip(CircleShape)
                .background(Color(0xFF070B14))
                .clickable { viewModel.navigateTo(com.example.ui.Screen.CREATE_POST) }
                .testTag("nav_tab_create"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Post",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
              )
            }
          }

          // 4. Vybe (renamed from Reels per Section 10)
          val isVybeSelected = selectedTab == MainTab.VYBES
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.selectTab(MainTab.VYBES) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_vybes")
          ) {
            Icon(
              imageVector = if (isVybeSelected) Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay,
              contentDescription = "Vybe",
              tint = if (isVybeSelected) VybeBrandCyan else Color(0xFF8E9BAE),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Vybe",
              fontSize = 11.sp,
              fontWeight = if (isVybeSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isVybeSelected) VybeBrandCyan else Color(0xFF8E9BAE)
            )
          }

          // 5. Profile
          val isProfileSelected = selectedTab == MainTab.PROFILE
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.selectTab(MainTab.PROFILE) }
              .padding(vertical = 4.dp)
              .testTag("nav_tab_profile")
          ) {
            Icon(
              imageVector = if (isProfileSelected) Icons.Filled.Person else Icons.Outlined.Person,
              contentDescription = "Profile",
              tint = if (isProfileSelected) VybeBrandCyan else Color(0xFF8E9BAE),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Profile",
              fontSize = 11.sp,
              fontWeight = if (isProfileSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isProfileSelected) VybeBrandCyan else Color(0xFF8E9BAE)
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
        MainTab.PROFILE -> UserProfileScreen(viewModel)
        MainTab.INBOX -> InboxScreen(viewModel)
        MainTab.MOOD_ZONES -> MoodZonesScreen(viewModel)
        MainTab.CROSSED_PATHS -> CrossedPathsScreen(viewModel)
      }
    }
  }
}

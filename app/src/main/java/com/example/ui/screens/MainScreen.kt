package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainTab
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
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier.testTag("main_bottom_nav")
      ) {
        // Tab 1: Feed
        NavigationBarItem(
          selected = selectedTab == MainTab.FEED,
          onClick = { viewModel.selectTab(MainTab.FEED) },
          icon = {
            Icon(
              imageVector = if (selectedTab == MainTab.FEED) Icons.Default.Home else Icons.Outlined.Home,
              contentDescription = "Feed"
            )
          },
          label = { Text("Feed", fontSize = 11.sp, fontWeight = if (selectedTab == MainTab.FEED) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = VybeViolet.copy(alpha = 0.2f),
            selectedIconColor = VybeViolet,
            selectedTextColor = VybeViolet
          ),
          modifier = Modifier.testTag("nav_tab_feed")
        )

        // Tab 2: VYBES (Short-video destination per Section 10)
        NavigationBarItem(
          selected = selectedTab == MainTab.VYBES,
          onClick = { viewModel.selectTab(MainTab.VYBES) },
          icon = {
            Icon(
              imageVector = if (selectedTab == MainTab.VYBES) Icons.Default.PlayCircle else Icons.Outlined.PlayCircleOutline,
              contentDescription = "VYBES Short-Videos"
            )
          },
          label = { Text("VYBES", fontSize = 11.sp, fontWeight = if (selectedTab == MainTab.VYBES) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = VybeMagenta.copy(alpha = 0.2f),
            selectedIconColor = VybeMagenta,
            selectedTextColor = VybeMagenta
          ),
          modifier = Modifier.testTag("nav_tab_vybes")
        )

        // Tab 3: Mood Zones
        NavigationBarItem(
          selected = selectedTab == MainTab.MOOD_ZONES,
          onClick = { viewModel.selectTab(MainTab.MOOD_ZONES) },
          icon = {
            Icon(
              imageVector = if (selectedTab == MainTab.MOOD_ZONES) Icons.Default.Mood else Icons.Outlined.Mood,
              contentDescription = "Mood Zones"
            )
          },
          label = { Text("Moods", fontSize = 11.sp, fontWeight = if (selectedTab == MainTab.MOOD_ZONES) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = VybeViolet.copy(alpha = 0.2f),
            selectedIconColor = VybeViolet,
            selectedTextColor = VybeViolet
          ),
          modifier = Modifier.testTag("nav_tab_moods")
        )

        // Tab 4: Explore
        NavigationBarItem(
          selected = selectedTab == MainTab.EXPLORE,
          onClick = { viewModel.selectTab(MainTab.EXPLORE) },
          icon = {
            Icon(
              imageVector = if (selectedTab == MainTab.EXPLORE) Icons.Default.Explore else Icons.Outlined.Explore,
              contentDescription = "Explore"
            )
          },
          label = { Text("Explore", fontSize = 11.sp, fontWeight = if (selectedTab == MainTab.EXPLORE) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = VybeViolet.copy(alpha = 0.2f),
            selectedIconColor = VybeViolet,
            selectedTextColor = VybeViolet
          ),
          modifier = Modifier.testTag("nav_tab_explore")
        )

        // Tab 5: Inbox
        NavigationBarItem(
          selected = selectedTab == MainTab.INBOX,
          onClick = { viewModel.selectTab(MainTab.INBOX) },
          icon = {
            BadgedBox(
              badge = {
                if (unreadTotal > 0) {
                  Badge { Text("$unreadTotal") }
                }
              }
            ) {
              Icon(
                imageVector = if (selectedTab == MainTab.INBOX) Icons.Default.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Inbox"
              )
            }
          },
          label = { Text("Inbox", fontSize = 11.sp, fontWeight = if (selectedTab == MainTab.INBOX) FontWeight.Bold else FontWeight.Normal) },
          colors = NavigationBarItemDefaults.colors(
            indicatorColor = VybeViolet.copy(alpha = 0.2f),
            selectedIconColor = VybeViolet,
            selectedTextColor = VybeViolet
          ),
          modifier = Modifier.testTag("nav_tab_inbox")
        )
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
      }
    }
  }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.screens.*
import com.example.ui.theme.VybeTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: VybeViewModel = viewModel()
      val isDarkMode by viewModel.isDarkMode.collectAsState()
      val currentScreen by viewModel.currentScreen.collectAsState()

      VybeTheme(darkTheme = isDarkMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
          when (currentScreen) {
            Screen.SPLASH -> SplashScreen()
            Screen.AUTH -> AuthScreen(viewModel)
            Screen.MAIN -> MainScreen(viewModel)
            Screen.CHAT_DETAIL -> ChatDetailScreen(viewModel)
            Screen.CALL_SCREEN -> CallScreen(viewModel)
            Screen.CREATE_POST -> CreatePostScreen(viewModel)
            Screen.CREATE_CAPSULE -> TimeCapsulesScreen(viewModel)
            Screen.TIME_CAPSULES_VIEW -> TimeCapsulesScreen(viewModel)
            Screen.DOUBLE_TAKE_VIEW -> DoubleTakeScreen(viewModel)
            Screen.SHOP_DETAIL -> ShopDetailScreen(viewModel)
            Screen.USER_PROFILE -> UserProfileScreen(viewModel)
            Screen.CREATOR_STUDIO -> CreatorStudioScreen(viewModel)
            Screen.ADMIN_CONSOLE -> AdminConsoleScreen(viewModel)
            Screen.SETTINGS -> SettingsScreen(viewModel)
            Screen.REELS_VIEWER -> ReelsViewerScreen(viewModel)
            Screen.CREATE_CROSSED_PATH -> CrossedPathsScreen(viewModel)
            Screen.UNLOAD_VIEW -> UnloadScreen(viewModel)
            Screen.UNFILTERED_VIEW -> UnfilteredScreen(viewModel)
            Screen.WHAT_NEXT_VIEW -> WhatNextScreen(viewModel)
            Screen.SPIRITUAL_MODE -> SpiritualModeScreen(viewModel)
            Screen.STORY_VIEWER -> StoryViewerScreen(viewModel)
          }
        }
      }
    }
  }
}

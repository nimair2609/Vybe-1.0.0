package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
    com.example.data.repository.VybeRepository.getInstance().initSession(applicationContext)
    handleAuthDeepLink(intent)
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
            Screen.NOTIFICATIONS -> NotificationCenterScreen(viewModel)
            Screen.LIVE_STUDIO -> LiveStudioScreen(viewModel)
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleAuthDeepLink(intent)
  }

  private fun handleAuthDeepLink(intent: Intent?) {
    val data = intent?.data ?: return
    Log.i("MainActivity", "Captured auth deep link: $data")
    try {
      // 1. Handle fragment (#access_token=...&refresh_token=...)
      val fragment = data.fragment
      val queryParamToken = data.getQueryParameter("access_token") ?: data.getQueryParameter("token")

      var accessToken: String? = queryParamToken
      var refreshToken: String? = data.getQueryParameter("refresh_token")
      var type: String? = data.getQueryParameter("type")

      if (!fragment.isNullOrBlank()) {
        val pairs = fragment.split("&")
        for (pair in pairs) {
          val parts = pair.split("=", limit = 2)
          if (parts.size == 2) {
            when (parts[0]) {
              "access_token" -> accessToken = parts[1]
              "refresh_token" -> refreshToken = parts[1]
              "type" -> type = parts[1]
            }
          }
        }
      }

      if (!accessToken.isNullOrBlank()) {
        Log.i("MainActivity", "Successfully parsed auth session tokens from deep link ($type)")
        val repo = com.example.data.repository.VybeRepository.getInstance()
        val session = com.example.data.session.SessionManager.getInstance(applicationContext)
        // If current user already loaded, preserve user profile with new tokens
        val user = repo.currentUser.value ?: session.loadCurrentUser()
        if (user != null) {
          session.saveSession(user, accessToken, refreshToken ?: "")
        }
      }
    } catch (e: Exception) {
      Log.e("MainActivity", "Failed to parse deep link: ${e.message}")
    }
  }
}

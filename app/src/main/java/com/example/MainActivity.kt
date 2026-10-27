package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AppThemeMode
import com.example.data.repository.VybeRepository
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.IncomingCallBanner
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.UploadStatusBanner
import com.example.ui.screens.*
import com.example.ui.theme.VybeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

  private val pendingDeepLinkUri = MutableStateFlow<Uri?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    VybeRepository.getInstance().initSession(applicationContext)
    intent?.data?.let { uri ->
      Log.i("MainActivity", "Initial deep link URI: $uri")
      pendingDeepLinkUri.value = uri
    }

    enableEdgeToEdge()
    setContent {
      val viewModel: VybeViewModel = viewModel()
      val incomingUri by pendingDeepLinkUri.collectAsState()

      LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        viewModel.handleIncomingAuthUri(uri)
        pendingDeepLinkUri.value = null
      }

      val themeMode by viewModel.themeMode.collectAsState()
      val systemInDark = isSystemInDarkTheme()
      val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemInDark
      }

      val currentScreen by viewModel.currentScreen.collectAsState()
      val isOnline by viewModel.isOnline.collectAsState()
      val uploadStatus by viewModel.uploadStatus.collectAsState()
      val activeCall by viewModel.activeCall.collectAsState()
      val bannerMsg by viewModel.uiBannerMessage.collectAsState()

      LaunchedEffect(bannerMsg) {
        if (!bannerMsg.isNullOrBlank()) {
          delay(4500)
          viewModel.consumeBannerMessage()
        }
      }

      LaunchedEffect(currentScreen) {
        val isMediaPlaybackScreen = currentScreen == Screen.MAIN ||
          currentScreen == Screen.REELS_VIEWER ||
          currentScreen == Screen.STORY_VIEWER ||
          currentScreen == Screen.LIVE_STUDIO ||
          currentScreen == Screen.UNLOAD_VIEW
        volumeControlStream = if (isMediaPlaybackScreen) {
          android.media.AudioManager.STREAM_MUSIC
        } else {
          android.media.AudioManager.USE_DEFAULT_STREAM_TYPE
        }
      }

      VybeTheme(darkTheme = isDark) {
        Surface(modifier = Modifier.fillMaxSize()) {
          Column(modifier = Modifier.fillMaxSize()) {
            OfflineStatusBanner(isOnline = isOnline)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
              when (currentScreen) {
                Screen.SPLASH -> SplashScreen()
                Screen.AUTH -> AuthScreen(viewModel)
                Screen.PASSWORD_RECOVERY -> PasswordRecoveryScreen(viewModel)
                Screen.MAIN -> MainScreen(viewModel)
                Screen.CHAT_DETAIL -> ChatDetailScreen(viewModel)
                Screen.CALL_SCREEN -> CallScreen(viewModel)
                Screen.CREATE_POST -> CreatePostScreen(viewModel)
                Screen.CREATE_CAPSULE -> TimeCapsulesScreen(viewModel)
                Screen.CREATE_CROSSED_PATH -> CrossedPathsScreen(viewModel)
                Screen.SHOP_DETAIL -> ShopDetailScreen(viewModel)
                Screen.USER_PROFILE -> UserProfileScreen(viewModel)
                Screen.CREATOR_STUDIO -> CreatorStudioScreen(viewModel)
                Screen.ADMIN_CONSOLE -> AdminConsoleScreen(viewModel)
                Screen.SETTINGS -> SettingsScreen(viewModel)
                Screen.REELS_VIEWER -> ReelsViewerScreen(viewModel)
                Screen.TIME_CAPSULES_VIEW -> TimeCapsulesScreen(viewModel)
                Screen.DOUBLE_TAKE_VIEW -> DoubleTakeScreen(viewModel)
                Screen.UNLOAD_VIEW -> UnloadScreen(viewModel)
                Screen.UNFILTERED_VIEW -> UnfilteredScreen(viewModel)
                Screen.WHAT_NEXT_VIEW -> WhatNextScreen(viewModel)
                Screen.SPIRITUAL_MODE -> SpiritualModeScreen(viewModel)
                Screen.STORY_VIEWER -> StoryViewerScreen(viewModel)
                Screen.NOTIFICATIONS -> NotificationCenterScreen(viewModel)
                Screen.LIVE_STUDIO -> LiveStudioScreen(viewModel)
                Screen.COMMUNITY_ZONE -> CommunityZoneScreen(viewModel)
              }

              // Floating Upload Status Banner positioned cleanly below the top header
              if (currentScreen != Screen.SPLASH && currentScreen != Screen.AUTH && currentScreen != Screen.PASSWORD_RECOVERY) {
                UploadStatusBanner(
                  uploadStatus = uploadStatus,
                  onRetry = { viewModel.retryLastUpload() },
                  onDismiss = { viewModel.clearUploadStatus() },
                  modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                )
              }

              // Real-time Incoming Voice / Video Call Banner
              if (currentScreen != Screen.CALL_SCREEN && currentScreen != Screen.SPLASH && currentScreen != Screen.AUTH) {
                IncomingCallBanner(
                  callSession = activeCall,
                  onAccept = { viewModel.acceptCall() },
                  onDecline = { viewModel.declineCall() },
                  onOpenCallScreen = { viewModel.openIncomingCallScreen() },
                  modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                )
              }

              if (!bannerMsg.isNullOrBlank()) {
                Surface(
                  color = MaterialTheme.colorScheme.inverseSurface,
                  contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                  shape = RoundedCornerShape(14.dp),
                  tonalElevation = 6.dp,
                  modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 84.dp)
                ) {
                  androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = bannerMsg!!,
                      style = MaterialTheme.typography.bodyMedium,
                      modifier = Modifier.weight(1f, fill = false)
                    )
                    TextButton(onClick = { viewModel.consumeBannerMessage() }) {
                      Text("OK")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.data?.let { uri ->
      Log.i("MainActivity", "NewIntent deep link URI: $uri")
      pendingDeepLinkUri.value = uri
    }
  }
}

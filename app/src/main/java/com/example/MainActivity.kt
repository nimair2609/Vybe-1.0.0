package com.example

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.notifications.VybeFirebaseMessagingService
import com.example.notifications.VybeNotificationHelper
import com.example.data.session.SessionManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AppThemeMode
import com.example.data.repository.VybeRepository
import com.example.notifications.RealtimeInAppBannerEvent
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.IncomingCallBanner
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.PostShareBottomSheet
import com.example.ui.components.RealtimeNotificationBanner
import com.example.ui.components.SharedPostDetailDialog
import com.example.ui.components.UploadStatusBanner
import com.example.ui.screens.*
import com.example.ui.theme.VybeTheme
import com.example.util.FeedAudioController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

  private val pendingDeepLinkUri = MutableStateFlow<Uri?>(null)
  private val pendingNotificationPayload = MutableStateFlow<Bundle?>(null)
  private var activeViewModel: VybeViewModel? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      coil.Coil.setImageLoader(
        coil.ImageLoader.Builder(applicationContext)
          .okHttpClient(com.example.data.remote.SupabaseNetworkClient.okHttpClient)
          .crossfade(true)
          .build()
      )
    } catch (e: Throwable) {
      Log.w("MainActivity", "Coil initialization notice: ${e.message}")
    }
    try {
      VybeRepository.getInstance().initSession(applicationContext)
    } catch (e: Throwable) {
      Log.e("MainActivity", "Session initialization recovery: ${e.message}", e)
    }
    try {
      VybeNotificationHelper.initNotificationChannels(this)
    } catch (e: Throwable) {
      Log.w("MainActivity", "Notification channel initialization notice: ${e.message}")
    }

    intent?.data?.let { uri ->
      Log.i("MainActivity", "Initial deep link URI: $uri")
      pendingDeepLinkUri.value = uri
    }
    intent?.extras?.let { extras ->
      if (extras.containsKey("destination") || extras.containsKey("target_id") || extras.containsKey("conversation_id")) {
        pendingNotificationPayload.value = extras
      }
    }

    try {
      val initialSession = SessionManager.getInstance(applicationContext)
      VybeFirebaseMessagingService.subscribeStandardTopics(initialSession.userId)
      com.google.firebase.messaging.FirebaseMessaging.getInstance().token
        .addOnCompleteListener { task ->
          if (task.isSuccessful) {
            val token = task.result
            if (!token.isNullOrBlank()) {
              Log.d("MainActivity", "Firebase Messaging Token obtained: ${token.take(12)}...")
              val session = SessionManager.getInstance(applicationContext)
              session.saveFcmToken(token)
              val userId = session.userId
              if (session.isLoggedIn && !userId.isNullOrBlank()) {
                VybeRepository.get().syncDeviceFcmToken(userId, token)
              }
            }
          } else {
            Log.w("MainActivity", "FCM token fetch warning: ${task.exception?.message}")
          }
        }
    } catch (e: Exception) {
      Log.w("MainActivity", "FirebaseMessaging initialization notice: ${e.message}")
    }

    enableEdgeToEdge()
    setContent {
      val viewModel: VybeViewModel = viewModel()
      activeViewModel = viewModel
      val incomingUri by pendingDeepLinkUri.collectAsState()
      val incomingNotification by pendingNotificationPayload.collectAsState()
      val currentScreenForPerm by viewModel.currentScreen.collectAsState()
      val selectedConversation by viewModel.selectedConversation.collectAsState()

      LaunchedEffect(currentScreenForPerm, selectedConversation?.id) {
        val openConvId = if (currentScreenForPerm == Screen.CHAT_DETAIL) selectedConversation?.id else null
        VybeNotificationHelper.activeOpenConversationId = openConvId
        if (!openConvId.isNullOrBlank()) {
          VybeNotificationHelper.clearConversationNotifications(this@MainActivity, openConvId)
        }
      }

      // Prompt for Android 13+ runtime notification permission once splash completes
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
          Log.d("MainActivity", "POST_NOTIFICATIONS granted: $isGranted")
        }
        LaunchedEffect(currentScreenForPerm != Screen.SPLASH) {
          if (
            currentScreenForPerm != Screen.SPLASH &&
            androidx.core.content.ContextCompat.checkSelfPermission(
              this@MainActivity,
              android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
          ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
          }
        }
      }

      LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        viewModel.handleIncomingAuthUri(uri)
        pendingDeepLinkUri.value = null
      }

      LaunchedEffect(incomingNotification) {
        val extras = incomingNotification ?: return@LaunchedEffect
        viewModel.handleNotificationPayload(extras)
        pendingNotificationPayload.value = null
      }

      val themeMode by viewModel.themeMode.collectAsState()
      val systemInDark = isSystemInDarkTheme()
      val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemInDark
      }

      val currentScreen by viewModel.currentScreen.collectAsState()
      val selectedTab by viewModel.selectedTab.collectAsState()
      val isOnline by viewModel.isOnline.collectAsState()
      val uploadStatus by viewModel.uploadStatus.collectAsState()
      val activeCall by viewModel.activeCall.collectAsState()
      val bannerMsg by viewModel.uiBannerMessage.collectAsState()
      val availableUpdate by viewModel.availableAppUpdate.collectAsState()
      val sharingPost by viewModel.sharingPost.collectAsState()
      var activeRealtimeBanner by remember { mutableStateOf<RealtimeInAppBannerEvent?>(null) }

      LaunchedEffect(Unit) {
        viewModel.checkForAppUpdateInBackground(applicationContext)
      }

      LaunchedEffect(Unit) {
        viewModel.inAppRealtimeBannerEvents.collect { incomingEvent ->
          activeRealtimeBanner = incomingEvent
        }
      }

      LaunchedEffect(activeRealtimeBanner?.eventKey) {
        if (activeRealtimeBanner != null) {
          delay(4500)
          activeRealtimeBanner = null
        }
      }

      LaunchedEffect(bannerMsg) {
        if (!bannerMsg.isNullOrBlank()) {
          delay(4500)
          viewModel.consumeBannerMessage()
        }
      }

      LaunchedEffect(currentScreen, selectedTab) {
        val isFeedOrReelsMain = currentScreen == Screen.MAIN &&
          (selectedTab == MainTab.FEED || selectedTab == MainTab.VYBES)
        val isMediaPlaybackScreen = isFeedOrReelsMain ||
          currentScreen == Screen.REELS_VIEWER ||
          currentScreen == Screen.STORY_VIEWER ||
          currentScreen == Screen.MUSIC_TRACK_DETAIL ||
          currentScreen == Screen.LIVE_STUDIO

        volumeControlStream = when {
          isMediaPlaybackScreen -> AudioManager.STREAM_MUSIC
          currentScreen == Screen.CALL_SCREEN || currentScreen == Screen.UNLOAD_VIEW -> AudioManager.STREAM_VOICE_CALL
          else -> AudioManager.USE_DEFAULT_STREAM_TYPE
        }

        // Prevent Feed music/video audio from overlapping into other tabs or screens
        if (currentScreen != Screen.MAIN || selectedTab != MainTab.FEED) {
          FeedAudioController.stopAllFeedAudio()
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
                Screen.MUSIC_TRACK_DETAIL -> MusicTrackScreen(viewModel)
                Screen.VERIFICATION_CENTER -> VerificationCenterScreen(viewModel)
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

              // In-App Update Notification Banner
              if (
                availableUpdate != null &&
                activeCall == null &&
                currentScreen != Screen.SPLASH &&
                currentScreen != Screen.AUTH &&
                currentScreen != Screen.PASSWORD_RECOVERY &&
                currentScreen != Screen.SETTINGS
              ) {
                com.example.ui.components.InAppUpdateBanner(
                  release = availableUpdate!!,
                  onUpdateClick = { viewModel.navigateTo(Screen.SETTINGS) },
                  onDismiss = { viewModel.dismissAppUpdateBanner() },
                  modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
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

              // Instagram-Style Real-Time Notification Pill Banner (DMs, Likes, Comments, Follows, Broadcasts)
              if (
                activeCall == null &&
                currentScreen != Screen.SPLASH &&
                currentScreen != Screen.AUTH &&
                currentScreen != Screen.PASSWORD_RECOVERY
              ) {
                RealtimeNotificationBanner(
                  event = activeRealtimeBanner,
                  onOpen = { evt -> viewModel.openRealtimeBannerEvent(evt) },
                  onDismiss = { activeRealtimeBanner = null },
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

              // Global Shared Post Deep-Link / DM Preview Modal
              SharedPostDetailDialog(viewModel = viewModel)

              // Global Post Share Bottom Sheet (DM + Copy Link + External Chooser)
              if (sharingPost != null) {
                PostShareBottomSheet(
                  post = sharingPost!!,
                  viewModel = viewModel,
                  onDismiss = { viewModel.dismissPostShareSheet() }
                )
              }
            }
          }
        }
      }
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      val vm = activeViewModel
      if (vm != null) {
        val screen = vm.currentScreen.value
        val tab = vm.selectedTab.value
        val inFeedOrReels = screen == Screen.REELS_VIEWER ||
          (screen == Screen.MAIN && (tab == MainTab.FEED || tab == MainTab.VYBES))
        if (inFeedOrReels) {
          volumeControlStream = AudioManager.STREAM_MUSIC
          if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            vm.onPhysicalVolumeUpInPlayback()
          }
        }
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onResume() {
    super.onResume()
    VybeNotificationHelper.isAppInForeground = true
    com.example.notifications.VybeRealtimePushManager.ensureConnected(applicationContext)
  }

  override fun onPause() {
    super.onPause()
    VybeNotificationHelper.isAppInForeground = false
    FeedAudioController.stopAllFeedAudio()
    com.example.util.music.MusicPreviewController.stop()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.data?.let { uri ->
      Log.i("MainActivity", "NewIntent deep link URI: $uri")
      pendingDeepLinkUri.value = uri
    }
    intent.extras?.let { extras ->
      if (extras.containsKey("destination") || extras.containsKey("target_id") || extras.containsKey("conversation_id")) {
        pendingNotificationPayload.value = extras
      }
    }
  }
}

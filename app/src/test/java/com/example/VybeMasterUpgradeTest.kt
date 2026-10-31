package com.example

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VybeLocalStore
import com.example.data.model.*
import com.example.data.remote.SupabaseConfig
import com.example.data.repository.VybeRepository
import com.example.data.session.SessionManager
import com.example.notifications.VybeNotificationHelper
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.FingerHeartReaction
import com.example.ui.components.StoryCreateDialog
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.ReelsViewerScreen
import com.example.ui.screens.StoryViewerScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.theme.VybeTheme
import com.example.util.TimestampUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VybeMasterUpgradeTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var repository: VybeRepository
  private lateinit var localStore: VybeLocalStore
  private lateinit var sessionManager: SessionManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    VybeNotificationHelper.resetLedgerForTests(context)
    localStore = VybeLocalStore.getInstance(context)
    sessionManager = SessionManager.getInstance(context)
    repository = VybeRepository.getInstance()
  }

  private fun seedAuthenticatedState(
    user: User,
    posts: List<Post> = emptyList(),
    stories: List<Story> = emptyList()
  ) {
    localStore.savePosts(posts)
    localStore.saveStories(stories)
    sessionManager.saveSession(user = user, accessToken = "", refreshToken = "")
    repository.initSession(context)
  }

  @Test
  fun `FingerHeartReaction renders in Light and Dark themes animates smoothly and ensurePostReacted never unlikes on double tap`() {
    var darkTheme by mutableStateOf(true)
    var isLiked by mutableStateOf(false)
    var count by mutableIntStateOf(24)

    composeTestRule.setContent {
      VybeTheme(darkTheme = darkTheme) {
        FingerHeartReaction(
          isLiked = isLiked,
          count = count,
          onClick = {
            isLiked = !isLiked
            count = if (isLiked) count + 1 else count - 1
          },
          testTag = "global_finger_heart_btn"
        )
      }
    }

    composeTestRule.onNodeWithTag("global_finger_heart_btn").assertIsDisplayed()
    composeTestRule.onNodeWithText("24").assertIsDisplayed()

    // Tap once -> activates Finger Heart + increments count
    composeTestRule.onNodeWithTag("global_finger_heart_btn").performClick()
    composeTestRule.waitForIdle()
    assertTrue("Finger Heart must become active after tap", isLiked)
    composeTestRule.onNodeWithText("25").assertIsDisplayed()

    // Switch to Light Mode -> stays displayed & readable
    darkTheme = false
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("global_finger_heart_btn").assertIsDisplayed()

    // Tap again -> smooth reverse unlike transition
    composeTestRule.onNodeWithTag("global_finger_heart_btn").performClick()
    composeTestRule.waitForIdle()
    assertFalse("Finger Heart must unlike cleanly on second single tap", isLiked)
    composeTestRule.onNodeWithText("24").assertIsDisplayed()

    // Verify double-tap ensurePostReacted adds LOVE once and NEVER unlikes on subsequent double-taps
    val me = User(id = "u_dt_1", username = "aria", displayName = "Aria")
    val seededPost = Post(
      id = "post_dt_101",
      author = me,
      caption = "Double tap safety test",
      timestamp = "Just now",
      likesCount = 0,
      isLiked = false
    )
    seedAuthenticatedState(user = me, posts = listOf(seededPost))

    repository.ensurePostReacted(seededPost.id, VybeReaction.LOVE)
    val afterFirstDoubleTap = repository.posts.value.first { it.id == seededPost.id }
    assertTrue("First double-tap must like the post with Finger Heart", afterFirstDoubleTap.isLiked)
    assertEquals(VybeReaction.LOVE, afterFirstDoubleTap.userReaction)
    assertEquals(1, afterFirstDoubleTap.likesCount)

    // Second double-tap on already-liked post must NOT unlike or duplicate count
    repository.ensurePostReacted(seededPost.id, VybeReaction.LOVE)
    val afterSecondDoubleTap = repository.posts.value.first { it.id == seededPost.id }
    assertTrue("Repeated double-tap must NEVER unlike an already-liked post", afterSecondDoubleTap.isLiked)
    assertEquals(1, afterSecondDoubleTap.likesCount)
  }

  @Test
  fun `cross user Story visibility handles mixed case UUIDs and StoryViewerScreen does not auto close on mount`() {
    val viewer = User(id = "u_viewer_1", username = "nimai", displayName = "Nimai")
    val peerAuthorUpper = User(
      id = "AA11BB22-CC33-44DD-EE55-FF6677889900",
      username = "priya_vibes",
      displayName = "Priya Sharma"
    )
    val nowMs = System.currentTimeMillis()
    val crossUserStory = Story(
      id = "story_cross_user_1",
      author = peerAuthorUpper.copy(id = peerAuthorUpper.id.lowercase()),
      mediaUrl = "",
      caption = "Cross-device story stays open!",
      mediaType = StoryMediaType.TEXT,
      audience = StoryAudience.PUBLIC,
      textBackgroundColor = "NEON_PURPLE",
      createdAtIso = TimestampUtils.formatIsoUtc(nowMs - 60_000L),
      expiresAtIso = TimestampUtils.formatIsoUtc(nowMs + 23L * 3_600_000L)
    )

    seedAuthenticatedState(user = viewer, stories = listOf(crossUserStory))
    repository.registerDiscoveredUser(peerAuthorUpper)

    val vm = VybeViewModel()
    assertTrue(
      "Cross-user PUBLIC story with lowercase/uppercase UUID difference must be visible",
      vm.isStoryVisibleToViewer(crossUserStory, nowMs)
    )

    // Open peer story using uppercase author ID while story author has lowercase UUID
    vm.openUserStorySequence(peerAuthorUpper.id, crossUserStory.id)
    assertEquals(Screen.STORY_VIEWER, vm.currentScreen.value)

    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.setContent {
      VybeTheme(darkTheme = true) {
        StoryViewerScreen(viewModel = vm)
      }
    }

    composeTestRule.mainClock.advanceTimeBy(800L)
    composeTestRule.waitForIdle()

    assertEquals(
      "StoryViewerScreen must NOT auto-close within milliseconds of opening from another user",
      Screen.STORY_VIEWER,
      vm.currentScreen.value
    )
    composeTestRule.onNodeWithTag("story_viewer_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("story_like_button").assertIsDisplayed()
  }

  @Test
  fun `Direct Boot notification channels use IMPORTANCE_HIGH VISIBILITY_PUBLIC and route Story deep links`() {
    VybeNotificationHelper.initNotificationChannels(context)
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val activityChannel = nm.getNotificationChannel(VybeNotificationHelper.CHANNEL_ID_PRIMARY)
    val broadcastChannel = nm.getNotificationChannel(VybeNotificationHelper.CHANNEL_ID_BROADCAST)
    assertNotNull(activityChannel)
    assertNotNull(broadcastChannel)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, activityChannel!!.importance)
    assertEquals(Notification.VISIBILITY_PUBLIC, activityChannel.lockscreenVisibility)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, broadcastChannel!!.importance)
    assertEquals(Notification.VISIBILITY_PUBLIC, broadcastChannel.lockscreenVisibility)

    // Cross-prefix deduplication across FCM, Realtime, and DB notification IDs
    assertTrue(VybeNotificationHelper.markEventAsNotified(context, "fcm_story_like_771"))
    assertFalse(
      "Realtime duplicate of same story reaction event must be suppressed",
      VybeNotificationHelper.markEventAsNotified(context, "rt_story_like_771")
    )

    // Deep-link routing for STORY_REACTION opens STORY_VIEWER when story exists
    val owner = User(id = "u_story_owner", username = "owner", displayName = "Story Owner")
    val nowMs = System.currentTimeMillis()
    val targetStory = Story(
      id = "story_deeplink_99",
      author = owner,
      mediaUrl = "",
      caption = "Deep link target story",
      mediaType = StoryMediaType.TEXT,
      audience = StoryAudience.EVERYONE,
      textBackgroundColor = "CYBER_TEAL",
      createdAtIso = TimestampUtils.formatIsoUtc(nowMs - 30_000L),
      expiresAtIso = TimestampUtils.formatIsoUtc(nowMs + 22L * 3_600_000L)
    )
    seedAuthenticatedState(user = owner, stories = listOf(targetStory))

    val vm = VybeViewModel()
    val extras = Bundle().apply {
      putString("type", "STORY_REACTION")
      putString("destination", "STORY_VIEWER")
      putString("target_id", targetStory.id)
    }
    vm.handleNotificationPayload(extras)
    composeTestRule.waitForIdle()
    assertEquals(Screen.STORY_VIEWER, vm.currentScreen.value)
  }

  @Test
  fun `ReelsViewerScreen renders Instagram style top horizontal progress bar`() {
    val creator = User(id = "u_reeler_1", username = "reeler", displayName = "Reeler")
    val reelPost = Post(
      id = "reel_progress_1",
      author = creator,
      caption = "Midnight synthwave reel",
      timestamp = "Just now",
      mediaUrls = listOf("https://cppwadedazpbevkfrchg.supabase.co/storage/v1/object/public/vybe-posts/sample_reel.mp4"),
      mediaType = MediaType.REEL,
      isVybesReel = true
    )
    seedAuthenticatedState(user = creator, posts = listOf(reelPost))

    val vm = VybeViewModel()
    composeTestRule.setContent {
      VybeTheme(darkTheme = true) {
        ReelsViewerScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("reel_top_progress_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("reel_top_progress_fill", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `StoryCreateDialog bottom action bar keeps Matches and Share Story buttons visible and comfortable`() {
    val creator = User(id = "u_creator_story", username = "creator", displayName = "Creator")
    seedAuthenticatedState(user = creator)
    val vm = VybeViewModel()

    composeTestRule.setContent {
      VybeTheme(darkTheme = true) {
        StoryCreateDialog(
          viewModel = vm,
          onDismiss = {},
          onStoryPublished = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("create_story_bottom_bar").assertIsDisplayed()
    val matchesBounds = composeTestRule.onNodeWithTag("publish_vybe_matches_button")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()
    val shareBounds = composeTestRule.onNodeWithTag("publish_your_story_button")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()

    assertTrue(
      "Matches button height must meet >= 48dp accessibility touch target",
      (matchesBounds.bottom - matchesBounds.top) >= 48.dp
    )
    assertTrue(
      "Share Story button height must meet >= 48dp accessibility touch target",
      (shareBounds.bottom - shareBounds.top) >= 48.dp
    )
  }

  @Test
  fun `ExploreScreen displays all 6 Signature Features in header grid in both Dark and Light modes`() {
    val explorer = User(id = "u_explorer_1", username = "explorer", displayName = "Explorer")
    seedAuthenticatedState(user = explorer)
    val vm = VybeViewModel()
    var darkTheme by mutableStateOf(true)

    composeTestRule.setContent {
      VybeTheme(darkTheme = darkTheme) {
        ExploreScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    val featureTags = listOf(
      "explore_header_feature_unload",
      "explore_header_feature_unfiltered",
      "explore_header_feature_what_next",
      "explore_header_feature_time_capsule",
      "explore_header_feature_double_take",
      "explore_header_feature_crossed_paths"
    )

    composeTestRule.onNodeWithTag("explore_six_features_header_grid").assertIsDisplayed()
    featureTags.forEach { tag ->
      composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    // Switch to Light Mode and verify all 6 features remain visible
    darkTheme = false
    composeTestRule.waitForIdle()
    featureTags.forEach { tag ->
      composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    // Tap UNLOAD tile -> navigates to UNLOAD_VIEW
    composeTestRule.onNodeWithTag("explore_header_feature_unload").performClick()
    composeTestRule.waitForIdle()
    assertEquals(Screen.UNLOAD_VIEW, vm.currentScreen.value)
  }

  @Test
  fun `SupabaseConfig resolveAnonKey rejects invalid or foreign keys and Profile navigation isolates own vs peer profiles`() {
    // 1. Phone login "Invalid API key" prevention
    assertEquals(SupabaseConfig.LIVE_SUPABASE_ANON_KEY, SupabaseConfig.resolveAnonKey(""))
    assertEquals(SupabaseConfig.LIVE_SUPABASE_ANON_KEY, SupabaseConfig.resolveAnonKey("your_supabase_anon_key_placeholder"))
    assertEquals(SupabaseConfig.LIVE_SUPABASE_ANON_KEY, SupabaseConfig.resolveAnonKey("short_bad_key"))

    val header = Base64.encodeToString(
      """{"alg":"HS256","typ":"JWT"}""".toByteArray(),
      Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )
    val foreignPayload = Base64.encodeToString(
      """{"ref":"wrongprojectref123","role":"anon"}""".toByteArray(),
      Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )
    val foreignJwt = "$header.$foreignPayload.fakesig"
    assertEquals(
      "Foreign project JWT anon key must fall back to LIVE_SUPABASE_ANON_KEY",
      SupabaseConfig.LIVE_SUPABASE_ANON_KEY,
      SupabaseConfig.resolveAnonKey(foreignJwt)
    )

    val validProjectPayload = Base64.encodeToString(
      """{"ref":"${SupabaseConfig.SUPABASE_PROJECT_REF}","role":"anon"}""".toByteArray(),
      Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )
    val validProjectJwt = "$header.$validProjectPayload.validsig"
    assertEquals(validProjectJwt, SupabaseConfig.resolveAnonKey(validProjectJwt))

    // 2. Visiting peer profile + Back button + Own Profile tab isolation
    val me = User(id = "u_me_900", username = "nimai_owner", displayName = "Nimai Owner")
    seedAuthenticatedState(user = me)
    val vm = VybeViewModel()
    val peer = User(
      id = "peer_user_8842",
      username = "rohan_music",
      displayName = "Rohan Music"
    )

    vm.selectTab(MainTab.FEED)
    vm.openUserProfile(peer)
    assertEquals(Screen.USER_PROFILE, vm.currentScreen.value)
    assertEquals("peer_user_8842", vm.selectedUserProfile.value?.id)

    // Render peer profile and click TopAppBar Back button
    composeTestRule.setContent {
      VybeTheme(darkTheme = true) {
        if (vm.currentScreen.collectAsState().value == Screen.USER_PROFILE) {
          UserProfileScreen(viewModel = vm, forceOwnProfile = false)
        } else {
          UserProfileScreen(viewModel = vm, forceOwnProfile = true)
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Rohan Music").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_back_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertEquals("Back button on peer profile must return to MAIN", Screen.MAIN, vm.currentScreen.value)
    assertNull("Selected peer profile must clear after popping back to MAIN", vm.selectedUserProfile.value)

    // Even if peer profile was open, tapping MainTab.PROFILE must strictly show OWN profile
    vm.openUserProfile(peer)
    assertEquals("peer_user_8842", vm.selectedUserProfile.value?.id)
    vm.selectTab(MainTab.PROFILE)
    composeTestRule.waitForIdle()

    assertNull("MainTab.PROFILE must clear selectedUserProfile", vm.selectedUserProfile.value)
    composeTestRule.onNodeWithText(me.displayName).assertIsDisplayed()
  }
}

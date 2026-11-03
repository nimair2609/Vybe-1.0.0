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
import androidx.compose.ui.geometry.Offset
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
import com.example.ui.components.FindYourVybeSection
import com.example.ui.components.FingerHeartReaction
import com.example.ui.components.StoryCreateDialog
import com.example.ui.components.VybeUniversalReaction
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.ReelsViewerScreen
import com.example.ui.screens.StoryViewerScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.theme.VybeTheme
import com.example.util.GitHubUpdater
import com.example.util.TimestampUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h915dp")
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

    // Tap once -> activates VYBE Universal Reaction + increments count
    composeTestRule.onNodeWithTag("global_finger_heart_btn").performClick()
    composeTestRule.waitForIdle()
    assertTrue("VYBE reaction must become active after tap", isLiked)
    composeTestRule.onNodeWithText("25").assertIsDisplayed()

    // Switch to Light Mode -> stays displayed & readable
    darkTheme = false
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("global_finger_heart_btn").assertIsDisplayed()

    // Tap again -> smooth reverse unlike transition
    composeTestRule.onNodeWithTag("global_finger_heart_btn").performClick()
    composeTestRule.waitForIdle()
    assertFalse("VYBE reaction must unlike cleanly on second single tap", isLiked)
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
    assertTrue("First double-tap must activate VYBE reaction", afterFirstDoubleTap.isLiked)
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

  @Test
  fun `VybeUniversalReaction supports scroll cancellation press hold drag release selector and universal deduplicated reactions`() {
    assertEquals("VYBE", VybeReaction.LOVE.label)
    assertEquals(VybeReaction.LOVE, VybeReaction.fromWire("VYBE_PULSE"))

    var currentReaction by mutableStateOf<VybeReaction?>(null)
    var count by mutableIntStateOf(10)

    composeTestRule.setContent {
      VybeTheme(darkTheme = true) {
        VybeUniversalReaction(
          isLiked = currentReaction != null,
          userReaction = currentReaction,
          count = count,
          onClick = {
            if (currentReaction == null) {
              currentReaction = VybeReaction.LOVE
              count += 1
            } else {
              currentReaction = null
              count -= 1
            }
          },
          onReact = { selected ->
            if (currentReaction == selected) {
              currentReaction = null
              count -= 1
            } else {
              if (currentReaction == null) count += 1
              currentReaction = selected
            }
          },
          testTag = "vybe_universal_rx_btn"
        )
      }
    }

    // 1. Scroll gesture beyond touchSlop must cancel cleanly without applying a reaction
    composeTestRule.onNodeWithTag("vybe_universal_rx_btn").performTouchInput {
      down(center)
      moveBy(Offset(0f, 95f))
      up()
    }
    composeTestRule.waitForIdle()
    assertNull("Vertical scroll gesture must NOT trigger accidental reaction", currentReaction)
    assertEquals(10, count)

    // 2. Press-and-hold opens floating 5-reaction selector; drag horizontally & release selects FLAME
    composeTestRule.onNodeWithTag("vybe_universal_rx_btn").performTouchInput {
      down(center)
    }
    composeTestRule.mainClock.advanceTimeBy(650L)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("vybe_universal_rx_btn_selector").assertIsDisplayed()
    composeTestRule.onNodeWithTag("universal_reaction_option_FLAME").assertIsDisplayed()

    composeTestRule.onNodeWithTag("vybe_universal_rx_btn").performTouchInput {
      moveTo(Offset(64.dp.toPx(), center.y))
      up()
    }
    composeTestRule.waitForIdle()

    assertEquals("Press-hold-drag-release onto second slot must select FLAME", VybeReaction.FLAME, currentReaction)
    assertEquals(11, count)

    // 3. Backend deduplication & switching across Posts, Comments, and Stories
    val me = User(id = "u_rx_tester", username = "rx_tester", displayName = "RX Tester")
    val peer = User(id = "u_rx_peer", username = "rx_peer", displayName = "RX Peer")
    val comment = PostComment(
      id = "comment_rx_1",
      postId = "post_rx_1",
      author = peer,
      content = "Electric energy pulse!",
      timestamp = "1m",
      likesCount = 2,
      isLiked = false
    )
    val post = Post(
      id = "post_rx_1",
      author = peer,
      caption = "Testing universal reactions",
      timestamp = "1m",
      likesCount = 5,
      comments = listOf(comment),
      commentsCount = 1
    )
    val nowMs = System.currentTimeMillis()
    val story = Story(
      id = "story_rx_1",
      author = peer,
      mediaUrl = "",
      caption = "Story universal reaction test",
      mediaType = StoryMediaType.TEXT,
      createdAtIso = TimestampUtils.formatIsoUtc(nowMs - 10_000L),
      expiresAtIso = TimestampUtils.formatIsoUtc(nowMs + 3_600_000L)
    )
    seedAuthenticatedState(user = me, posts = listOf(post), stories = listOf(story))

    // Switch post reaction LOVE -> FLAME -> FLAME (toggle off) without duplicate counts
    repository.reactToPost(post.id, VybeReaction.LOVE)
    assertEquals(6, repository.posts.value.first { it.id == post.id }.likesCount)
    repository.reactToPost(post.id, VybeReaction.FLAME)
    val switchedPost = repository.posts.value.first { it.id == post.id }
    assertEquals("Switching reaction on same post must NOT increment count twice", 6, switchedPost.likesCount)
    assertEquals(VybeReaction.FLAME, switchedPost.userReaction)
    repository.reactToPost(post.id, VybeReaction.FLAME)
    assertEquals(5, repository.posts.value.first { it.id == post.id }.likesCount)

    // Comment reaction apply, switch, and remove
    repository.reactToComment(post.id, comment.id, VybeReaction.LOVE)
    var updatedComment = repository.posts.value.first { it.id == post.id }.comments.first()
    assertEquals(3, updatedComment.likesCount)
    assertEquals(VybeReaction.LOVE, updatedComment.userReaction)
    repository.reactToComment(post.id, comment.id, VybeReaction.WOW)
    updatedComment = repository.posts.value.first { it.id == post.id }.comments.first()
    assertEquals("Switching comment reaction must keep single user count", 3, updatedComment.likesCount)
    assertEquals(VybeReaction.WOW, updatedComment.userReaction)

    // Story reaction apply, switch, and toggle off
    repository.reactToStory(story.id, VybeReaction.LOVE)
    assertEquals(VybeReaction.LOVE, repository.stories.value.first { it.id == story.id }.myReaction)
    repository.reactToStory(story.id, VybeReaction.FLAME)
    assertEquals(VybeReaction.FLAME, repository.stories.value.first { it.id == story.id }.myReaction)
    repository.reactToStory(story.id, VybeReaction.FLAME)
    assertNull(repository.stories.value.first { it.id == story.id }.myReaction)
  }

  @Test
  fun `FindYourVybeSection upgrades Link Up and Suggest with private follow requests persistent dismissal block filtering and See All`() {
    val me = User(
      id = "u_fyv_me",
      username = "nimai_vybe",
      displayName = "Nimai",
      bio = "Building #synthwave & #design",
      currentMood = MoodType.FUN
    )
    val mutualFriend = User(
      id = "u_fyv_mutual",
      username = "kavya_beats",
      displayName = "Kavya Beats",
      bio = "Producer"
    )
    val privateCreator = User(
      id = "u_fyv_private",
      username = "zara_private",
      displayName = "Zara Khan",
      bio = "Curating #synthwave nights",
      isPrivate = true,
      currentMood = MoodType.FUN
    )
    val suggestedCreator = User(
      id = "u_fyv_suggest",
      username = "dev_arjun",
      displayName = "Arjun Rao",
      bio = "Visual artist #design",
      currentMood = MoodType.FUN
    )
    val blockedAccount = User(
      id = "u_fyv_blocked",
      username = "blocked_spam",
      displayName = "Blocked Account",
      bio = "Hidden user"
    )

    // Co-interaction post so Zara & Arjun have genuine mutual connection with Kavya (whom viewer follows)
    val sharedPost = Post(
      id = "post_fyv_mutual_1",
      author = privateCreator,
      caption = "Late studio session #synthwave",
      timestamp = "5m",
      comments = listOf(
        PostComment(
          id = "c_fyv_1",
          postId = "post_fyv_mutual_1",
          author = mutualFriend,
          content = "Fire track!",
          timestamp = "2m"
        )
      ),
      commentsCount = 1
    )

    seedAuthenticatedState(user = me, posts = listOf(sharedPost))
    repository.restoreDismissedSuggestions()
    repository.registerDiscoveredUser(mutualFriend)
    repository.registerDiscoveredUser(privateCreator)
    repository.registerDiscoveredUser(suggestedCreator)
    repository.registerDiscoveredUser(blockedAccount)

    // Follow mutualFriend & block blockedAccount
    repository.toggleFollowUser(mutualFriend.id)
    if (!repository.blockedUserIds.value.contains(blockedAccount.id)) {
      repository.toggleBlockUser(blockedAccount.id)
    }

    val vm = VybeViewModel()
    var darkTheme by mutableStateOf(true)

    composeTestRule.setContent {
      VybeTheme(darkTheme = darkTheme) {
        FindYourVybeSection(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    // Verify section & separate Link Up / Suggest tabs render without duplicating features
    composeTestRule.onNodeWithTag("find_your_vybe_section").assertIsDisplayed()
    composeTestRule.onNodeWithTag("find_your_vybe_tab_link_up").assertIsDisplayed()
    composeTestRule.onNodeWithTag("find_your_vybe_tab_suggest").assertIsDisplayed()
    composeTestRule.onNodeWithTag("find_your_vybe_card_${blockedAccount.id}").assertDoesNotExist()

    // Link Up tab displays Zara (private) and sends a follow request ("LINKUP Requested")
    composeTestRule.onNodeWithTag("find_your_vybe_card_${privateCreator.id}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("find_your_vybe_follow_${privateCreator.id}").performClick()
    composeTestRule.waitForIdle()
    assertTrue(
      "Following a private profile from Link Up must create a pending follow request",
      repository.requestedFollowUserIds.value.contains(privateCreator.id)
    )

    // Switch to Suggest tab -> verify Arjun is recommended and Dismiss hides him persistently
    composeTestRule.onNodeWithTag("find_your_vybe_tab_suggest").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("find_your_vybe_card_${suggestedCreator.id}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("find_your_vybe_dismiss_${suggestedCreator.id}").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("find_your_vybe_card_${suggestedCreator.id}").assertDoesNotExist()
    assertTrue(
      "Dismissed suggestion must be persisted in VybeLocalStore to prevent repeated suggestions",
      localStore.loadDismissedSuggestions(me.id).contains(suggestedCreator.id)
    )

    // Switch to Light Mode & open See All dialog
    darkTheme = false
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("find_your_vybe_see_all_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("find_your_vybe_see_all_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("find_your_vybe_see_all_back").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("find_your_vybe_see_all_dialog").assertDoesNotExist()
  }

  @Test
  fun `preview version matches 1_3_0 and does not emit false Update Available banner on launch`() {
    assertEquals("1.3.0", GitHubUpdater.INSTALLED_VERSION)
    assertEquals(165L, GitHubUpdater.INSTALLED_VERSION_CODE)

    val vm = VybeViewModel()
    assertNull(
      "Live preview build must NOT auto-populate unsolicited Update Available banner on startup",
      vm.availableAppUpdate.value
    )
  }

  @Test
  fun `Inbox ChatDetailScreen sends new messages cleanly without stuck Sending state and prevents unwanted autoscroll when reading history`() {
    val me = User(id = "u_inbox_me", username = "aria_chat", displayName = "Aria")
    val peer = User(id = "u_inbox_peer", username = "rohan_chat", displayName = "Rohan")
    val nowMs = System.currentTimeMillis()

    val historyMessages = (0 until 32).map { idx ->
      DirectMessage(
        id = "msg_hist_$idx",
        clientId = "msg_hist_$idx",
        senderId = if (idx % 2 == 0) peer.id else me.id,
        text = "History message #$idx",
        timestamp = "10:${String.format("%02d", idx)} AM",
        createdAtIso = TimestampUtils.formatIsoUtc(nowMs - (40 - idx) * 60_000L),
        isMe = idx % 2 == 1,
        status = MessageStatus.SENT
      )
    }

    val seededConv = Conversation(
      id = "conv_local_rohan_thread",
      peerUser = peer,
      lastMessage = historyMessages.last().text,
      lastMessageTimestamp = "Just now",
      lastMessageCreatedAtIso = historyMessages.last().createdAtIso,
      unreadCount = 0,
      messages = historyMessages
    )

    localStore.saveConversations(listOf(seededConv))
    seedAuthenticatedState(user = me)
    repository.registerDiscoveredUser(peer)

    val vm = VybeViewModel()
    vm.startConversationWithUser(peer)
    assertEquals(Screen.CHAT_DETAIL, vm.currentScreen.value)

    composeTestRule.setContent {
      VybeTheme(darkTheme = true) {
        ChatDetailScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    // 1. Scroll up to index 0 to read older history
    composeTestRule.onNodeWithTag("chat_messages_list").performScrollToIndex(0)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("History message #0").assertIsDisplayed()

    // 2. Simulate an incoming peer message arriving while scrolled up -> must NOT yank user away from index 0
    val incomingMsg = DirectMessage(
      id = "msg_incoming_peer_33",
      clientId = "msg_incoming_peer_33",
      senderId = peer.id,
      text = "Incoming message while reading history",
      timestamp = "Just now",
      createdAtIso = TimestampUtils.formatIsoUtc(nowMs),
      isMe = false,
      status = MessageStatus.SENT
    )
    val currentConvs = repository.conversations.value.map { c ->
      if (c.peerUser.id == peer.id) {
        c.copy(
          lastMessage = incomingMsg.text,
          messages = c.messages + incomingMsg
        )
      } else c
    }
    localStore.saveConversations(currentConvs)
    repository.initSession(context)
    composeTestRule.waitForIdle()

    // Older message #0 remains visible (no unwanted autoscroll!) and "New message" pill appears
    composeTestRule.onNodeWithText("History message #0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("jump_to_new_messages_pill").assertIsDisplayed()

    // 3. Send a new message from composer -> appends, transitions to SENT ("Delivered"), and scrolls to bottom
    composeTestRule.onNodeWithTag("message_input_field").performTextInput("Hello from fixed Inbox!")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("send_message_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      val conv = repository.conversations.value.firstOrNull { it.peerUser.id == peer.id }
      val sent = conv?.messages?.lastOrNull { it.text == "Hello from fixed Inbox!" }
      sent != null && sent.status == MessageStatus.SENT
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Hello from fixed Inbox!").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sending...").assertDoesNotExist()
    composeTestRule.onNodeWithText("Delivered").assertIsDisplayed()
  }
}


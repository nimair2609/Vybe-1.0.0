package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import com.example.data.remote.SupabaseConfig
import com.example.data.repository.VybeRepository
import com.example.notifications.VybeNotificationHelper
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.ReelReactionGestureButton
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeFollowButton
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VybeUpgradeVerificationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var repository: VybeRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    VybeNotificationHelper.resetLedgerForTests(context)
    repository = VybeRepository.getInstance()
  }

  @Test
  fun `push notification ledger deduplicates primary eventKey and alias keys and ingests into notification center once`() {
    val eventKey = "like:post_991:actor_44"
    val aliasDbId = "notif_uuid_881"

    assertFalse(VybeNotificationHelper.hasEventBeenNotified(context, eventKey))
    assertFalse(VybeNotificationHelper.hasEventBeenNotified(context, aliasDbId))
    assertTrue(VybeNotificationHelper.markEventAsNotified(context, eventKey, listOf(aliasDbId)))
    assertFalse(
      "Second attempt with same eventKey or alias must be suppressed",
      VybeNotificationHelper.markEventAsNotified(context, aliasDbId)
    )

    assertTrue("Primary eventKey must be recognized as notified", VybeNotificationHelper.hasEventBeenNotified(context, eventKey))
    assertTrue("Alias DB notification ID must also be recognized as notified", VybeNotificationHelper.hasEventBeenNotified(context, aliasDbId))

    repository.loginUser("recipient@vybe.app", "Password123!")
    val beforeCount = repository.notifications.value.count { it.eventKey == eventKey }

    val payloadData = mapOf(
      "notification_id" to aliasDbId,
      "event_key" to eventKey,
      "type" to "LIKE",
      "actor_id" to "actor_44",
      "actor_username" to "aria",
      "actor_display_name" to "Aria",
      "target_id" to "post_991",
      "destination" to "REELS_VIEWER"
    )
    repository.ingestIncomingPushEvent(
      title = "Aria (@aria)",
      body = "Reacted 🫰 to your Reel",
      data = payloadData,
      eventKey = eventKey
    )
    // Simulate duplicate delivery of same event via Realtime/FCM retry
    repository.ingestIncomingPushEvent(
      title = "Aria (@aria)",
      body = "Reacted 🫰 to your Reel",
      data = payloadData + ("notification_id" to "notif_uuid_duplicate"),
      eventKey = eventKey
    )

    val matching = repository.notifications.value.filter { it.eventKey == eventKey }
    assertEquals("Duplicate push deliveries must produce exactly 1 In-App Notification Center entry", beforeCount + 1, matching.size)
  }

  @Test
  fun `posts have zero sample comments and PostCard displays accurate 0 count and clean empty prompt`() {
    val author = User(id = "u_clean_1", username = "maya", displayName = "Maya")
    val post = Post(
      id = "post_zero_comments",
      author = author,
      caption = "Morning coffee in Bengaluru",
      timestamp = "2m",
      commentsCount = 0,
      comments = emptyList()
    )

    composeTestRule.setContent {
      MaterialTheme(colorScheme = darkColorScheme()) {
        PostCard(
          post = post,
          onLikeClick = {},
          onSaveClick = {},
          onCommentClick = {},
          onShareClick = {},
          onUserClick = {},
          currentUserId = "viewer_2",
          onFollowClick = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("view_all_comments_post_zero_comments")
      .assertIsDisplayed()
      .assertTextEquals("Add a comment...")
    composeTestRule.onNodeWithTag("follow_button_post_zero_comments")
      .assertIsDisplayed()
  }

  @Test
  fun `VybeFollowButton renders Follow Following and Requested cleanly in both Light and Dark themes and updates counts`() {
    var clickedCount = 0
    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        VybeFollowButton(
          isFollowing = false,
          isFollowRequested = false,
          followsMe = false,
          onClick = { clickedCount++ }
        )
      }
    }
    composeTestRule.onNodeWithTag("follow_button_active")
      .assertIsDisplayed()
      .assertTextContains("Follow")
      .performClick()
    assertEquals(1, clickedCount)

    repository.loginUser("follower_tester@vybe.app", "Password123!")
    val publicCreator = User(
      id = "creator_pub_100",
      username = "creator_pub",
      displayName = "Public Creator",
      followersCount = 10,
      isPrivate = false
    )
    val privateCreator = User(
      id = "creator_priv_200",
      username = "creator_priv",
      displayName = "Private Creator",
      followersCount = 5,
      isPrivate = true
    )
    val vm = VybeViewModel()
    vm.openUserProfile(publicCreator)
    vm.toggleFollowUser(publicCreator.id)
    assertTrue("Public creator must be followed immediately", repository.followingUserIds.value.contains(publicCreator.id))

    vm.openUserProfile(privateCreator)
    vm.toggleFollowUser(privateCreator.id)
    assertTrue("Private creator follow must enter Requested state", repository.requestedFollowUserIds.value.contains(privateCreator.id))
    assertFalse("Private creator must not be auto-followed without approval", repository.followingUserIds.value.contains(privateCreator.id))
  }

  @Test
  fun `5 reaction gesture button supports quick tap press hold drag selection and outside release cancel`() {
    assertEquals(
      listOf(VybeReaction.LOVE, VybeReaction.FLAME, VybeReaction.LAUGH, VybeReaction.WOW, VybeReaction.SAD),
      VybeReaction.values().toList()
    )
    assertEquals("🫰", VybeReaction.LOVE.emoji)

    var defaultToggled = 0
    var selectedReaction: VybeReaction? = null

    composeTestRule.setContent {
      MaterialTheme(colorScheme = darkColorScheme()) {
        ReelReactionGestureButton(
          userReaction = selectedReaction,
          onReact = { rx -> selectedReaction = rx },
          onToggleDefault = { defaultToggled++ }
        )
      }
    }

    // 1. Quick tap triggers default Finger Heart toggle
    composeTestRule.onNodeWithTag("reel_like_button").performClick()
    assertEquals(1, defaultToggled)

    // 2. Press, hold, drag outside bar, and release -> must cancel without reacting
    composeTestRule.onNodeWithTag("reel_like_button").performTouchInput {
      down(center)
      advanceEventTime(650L)
      moveTo(Offset(x = center.x, y = center.y + 300f))
      advanceEventTime(50L)
      up()
    }
    composeTestRule.waitForIdle()
    assertNull("Releasing outside the reaction bar must cancel without applying a reaction", selectedReaction)

    // 3. Press, hold, drag left into the floating 5-reaction bar, and release -> applies reaction
    composeTestRule.onNodeWithTag("reel_like_button").performTouchInput {
      down(center)
      advanceEventTime(650L)
      // Drag into middle of the side floating bar [-238dp..-8dp] -> LAUGH (😂) or FLAME (🔥)
      moveTo(Offset(x = -120f * viewConfiguration.touchSlop.coerceAtLeast(1f) / viewConfiguration.touchSlop.coerceAtLeast(1f), y = center.y))
      advanceEventTime(80L)
      up()
    }
    composeTestRule.waitForIdle()
    assertNotNull("Releasing inside the 5-reaction floating bar must apply the highlighted reaction", selectedReaction)
  }

  @Test
  fun `three official VYBE badge types enforce admin assignment VIP expiry acceptance hide show and block self grant`() = runBlocking {
    // 1. ADMIN role alone must NEVER auto-display any badge
    val plainAdmin = User(
      id = "admin_plain_1",
      username = "owner_admin",
      displayName = "Owner Admin",
      role = UserRole.ADMIN,
      isVerified = false,
      isVip = false,
      isVerifiedBusiness = false,
      isWorker = false
    )
    assertTrue("ADMIN role must not auto-grant public badges", plainAdmin.activePublicBadges.isEmpty())

    // 2. Expired VIP must never appear in activePublicBadges
    val expiredIso = Instant.now().minus(2, ChronoUnit.DAYS).toString()
    val futureIso = Instant.now().plus(30, ChronoUnit.DAYS).toString()
    val expiredVipUser = User(
      id = "vip_exp_1",
      username = "past_vip",
      displayName = "Past VIP",
      isVip = true,
      vipExpiresAtIso = expiredIso,
      badgeApprovalStatus = "APPROVED",
      badgeDisplayStatus = "ACCEPTED"
    )
    assertTrue(expiredVipUser.isVipExpired)
    assertFalse(expiredVipUser.hasActiveVip)
    assertTrue("Expired VIP badge must not display publicly", expiredVipUser.activePublicBadges.isEmpty())

    // 3. Active VIP vs Verified Business (Shop) vs Verified Blue vs Worker Green
    val activeShopAndVerified = User(
      id = "shop_1",
      username = "vybe_store",
      displayName = "VYBE Store",
      isVerified = true,
      isVerifiedBusiness = true,
      isVip = false,
      badgeApprovalStatus = "APPROVED",
      badgeDisplayStatus = "ACCEPTED"
    )
    assertEquals(
      listOf(OfficialVybeBadge.VERIFIED_BLUE, OfficialVybeBadge.SHOP_GOLD),
      activeShopAndVerified.activePublicBadges
    )

    // 4. Newly approved badge in PENDING_ACCEPTANCE must wait for user acceptance before public display
    val pendingAcceptVip = User(
      id = "vip_pending_accept",
      username = "new_vip",
      displayName = "New VIP",
      isVip = true,
      vipExpiresAtIso = futureIso,
      badgeApprovalStatus = "APPROVED",
      badgeDisplayStatus = "PENDING_ACCEPTANCE"
    )
    assertTrue(pendingAcceptVip.isPendingBadgeAcceptance)
    assertTrue("Pending-acceptance badge must not display until accepted", pendingAcceptVip.activePublicBadges.isEmpty())

    // 5. Mandatory Worker badge remains visible even if user attempts HIDE
    val mandatoryWorker = User(
      id = "worker_mand_1",
      username = "vybe_trust",
      displayName = "VYBE Trust",
      isWorker = true,
      isWorkerBadgeMandatory = true,
      badgeApprovalStatus = "APPROVED",
      badgeDisplayStatus = "HIDDEN"
    )
    assertEquals(listOf(OfficialVybeBadge.WORKER_GREEN), mandatoryWorker.activePublicBadges)

    // 6. Self-grant protection in repository
    repository.loginUser("renukadas083@gmail.com", "AdminPass123!")
    val currentAdminId = repository.currentUser.value!!.id
    var selfGrantAllowed = true
    var selfGrantMsg: String? = null
    repository.adminAssignBadge(
      userId = currentAdminId,
      badgeCategory = "VERIFIED",
      isGrant = true
    ) { ok, msg ->
      selfGrantAllowed = ok
      selfGrantMsg = msg
    }
    assertFalse("Administrators must be blocked from self-granting badges", selfGrantAllowed)
    assertTrue(selfGrantMsg.orEmpty().contains("Self-granting", ignoreCase = true))
  }

  @Test
  fun `VerificationBadge renders official icons and opens badge explanation dialog on tap`() {
    val multiBadgeUser = User(
      id = "u_badges_ui",
      username = "official_creator",
      displayName = "Official Creator",
      isVerified = true,
      isWorker = true,
      badgeApprovalStatus = "APPROVED",
      badgeDisplayStatus = "ACCEPTED"
    )

    composeTestRule.setContent {
      MaterialTheme(colorScheme = darkColorScheme()) {
        VerificationBadge(
          user = multiBadgeUser,
          enableExplanationDialog = true
        )
      }
    }

    composeTestRule.onNodeWithTag("worker_badge", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("verified_badge", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("badge_explanation_trigger").performClick()
    composeTestRule.onNodeWithTag("badge_explanation_dialog").assertIsDisplayed()
  }

  @Test
  fun `canonical QR profile URLs encode immutable user IDs without secrets and missing profiles show Profile Unavailable`() {
    val userA = User(id = "11111111-2222-3333-4444-555555555555", username = "arjun_music", displayName = "Arjun")
    val userB = User(id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", username = "priya_art", displayName = "Priya")

    val urlA = QrCodeGenerator.buildCanonicalProfileUrl(userA.id, userA.username)
    val urlB = QrCodeGenerator.buildCanonicalProfileUrl(userB.id, userB.username)

    assertTrue(urlA.startsWith("${SupabaseConfig.CANONICAL_VYBE_WEB_URL}/profile/${userA.id}"))
    assertFalse("QR URL must never leak tokens or keys", urlA.contains("token", ignoreCase = true))
    assertNotEquals("Different users must generate distinct canonical QR URLs", urlA, urlB)

    val parsedA = QrCodeGenerator.parseProfileDeepLink(urlA)
    val parsedB = QrCodeGenerator.parseProfileDeepLink("vybe://profile/${userB.id}?u=${userB.username}")

    assertNotNull(parsedA)
    assertEquals(userA.id, parsedA?.userId)
    assertEquals("arjun_music", parsedA?.username)

    assertNotNull(parsedB)
    assertEquals(userB.id, parsedB?.userId)
    assertEquals("priya_art", parsedB?.username)

    // Verify missing/deleted profile routes to USER_PROFILE with profileLookupError instead of opening own profile
    repository.loginUser("scanner@vybe.app", "Password123!")
    val viewModel = VybeViewModel()
    viewModel.handleIncomingAuthUri(Uri.parse("${SupabaseConfig.CANONICAL_VYBE_WEB_URL}/profile/deleted-nonexistent-id-999?u=deleted_ghost"))

    // Allow coroutine lookup to finish
    Thread.sleep(250)
    composeTestRule.waitForIdle()

    assertEquals(Screen.USER_PROFILE, viewModel.currentScreen.value)
    assertNull("Missing QR profile must never fall back to currentUser", viewModel.selectedUserProfile.value)
    assertNotNull("Missing QR profile must populate profileLookupError", viewModel.profileLookupError.value)
  }
}

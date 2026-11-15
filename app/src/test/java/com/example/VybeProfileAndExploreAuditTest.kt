package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VybeLocalStore
import com.example.data.model.Community
import com.example.data.model.MediaType
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.repository.VybeRepository
import com.example.data.session.SessionManager
import com.example.util.ProfileInputValidator
import com.example.util.QrCodeGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VybeProfileAndExploreAuditTest {

  private lateinit var context: Context
  private lateinit var repo: VybeRepository
  private lateinit var store: VybeLocalStore
  private lateinit var session: SessionManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("vybe_persistent_data", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("vybe_auth_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    session = SessionManager.getInstance(context)
    store = VybeLocalStore.getInstance(context)
    repo = VybeRepository.getInstance()
    repo.initSession(context)
    repo.logout()
  }

  @After
  fun tearDown() {
    repo.logout()
    context.getSharedPreferences("vybe_persistent_data", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("vybe_auth_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `profile view tracking enforces authentication owner check 24h dedupe and block deactivated guards`() {
    val targetId = "user_target_profile_001"
    val blockedId = "user_blocked_profile_002"
    val deactivatedId = "user_deactivated_profile_003"
    val targetUser = User(
      id = targetId,
      username = "target_creator",
      displayName = "Target Creator",
      profileViewsCount = 5L
    )
    val blockedUser = User(
      id = blockedId,
      username = "blocked_creator",
      displayName = "Blocked Creator",
      profileViewsCount = 2L
    )
    val deactivatedUser = User(
      id = deactivatedId,
      username = "deactivated_creator",
      displayName = "Deactivated Creator",
      isDeactivated = true,
      profileViewsCount = 4L
    )
    listOf(targetUser, blockedUser, deactivatedUser).forEach { store.saveUser(it) }
    repo.initSession(context)

    val baseTime = 1_760_000_000_000L

    // 1. Unauthenticated visitor must not record profile views
    repo.recordProfileView(targetId, nowMs = baseTime)
    assertEquals(5L, store.loadUsers().first { it.id == targetId }.profileViewsCount)

    // 2. Log in as authenticated viewer
    assertTrue(repo.loginUser("viewer_alpha@example.com", "Password123!"))
    val viewerId = repo.currentUser.value!!.id

    // 3. Owner self-view must never increment
    repo.recordProfileView(viewerId, nowMs = baseTime)
    assertEquals(0L, store.getProfileViewLastRecordedMs(viewerId, viewerId))
    assertEquals(0L, repo.currentUser.value?.profileViewsCount)

    // 4. First valid visit by viewer increments count and persists ledger in VybeLocalStore
    repo.recordProfileView(targetId, nowMs = baseTime)
    assertEquals(6L, repo.allUsers.value.first { it.id == targetId }.profileViewsCount)
    assertEquals(6L, store.loadUsers().first { it.id == targetId }.profileViewsCount)
    assertEquals(baseTime, store.getProfileViewLastRecordedMs(viewerId, targetId))

    // 5. Repeat visit within 24 hours is deduplicated (1 hour later)
    repo.recordProfileView(targetId, nowMs = baseTime + 3_600_000L)
    assertEquals(6L, repo.allUsers.value.first { it.id == targetId }.profileViewsCount)
    assertEquals(6L, store.loadUsers().first { it.id == targetId }.profileViewsCount)

    // 6. Visit after > 24 hours increments again
    repo.recordProfileView(targetId, nowMs = baseTime + 86_400_001L)
    assertEquals(7L, repo.allUsers.value.first { it.id == targetId }.profileViewsCount)
    assertEquals(7L, store.loadUsers().first { it.id == targetId }.profileViewsCount)

    // 7. Blocked target must not record profile views
    assertTrue(repo.toggleBlockUser(blockedId))
    repo.recordProfileView(blockedId, nowMs = baseTime)
    assertEquals(2L, store.loadUsers().first { it.id == blockedId }.profileViewsCount)
    assertEquals(0L, store.getProfileViewLastRecordedMs(viewerId, blockedId))

    // 8. Deactivated target must not record profile views
    repo.recordProfileView(deactivatedId, nowMs = baseTime)
    assertEquals(4L, store.loadUsers().first { it.id == deactivatedId }.profileViewsCount)
    assertEquals(0L, store.getProfileViewLastRecordedMs(viewerId, deactivatedId))
  }

  @Test
  fun `extended profile edits and pinned posts persist to session and local store`() {
    assertTrue(repo.loginUser("editor_member@example.com", "Password123!"))
    val userId = repo.currentUser.value!!.id

    repo.updateExtendedProfile(
      displayName = "Aria Chen",
      bio = "Building mindful communities",
      location = "Bengaluru, India",
      website = "https://vybeonline.netlify.app/u/aria_chen",
      interests = listOf("Design", "Photography", "Design", "Indie Music"),
      avatarUrl = null,
      coverUrl = null,
      isPrivate = true
    )

    val sessionUser = session.loadCurrentUser()
    assertNotNull(sessionUser)
    assertEquals("Aria Chen", sessionUser?.displayName)
    assertEquals("Building mindful communities", sessionUser?.bio)
    assertEquals("Bengaluru, India", sessionUser?.location)
    assertEquals("https://vybeonline.netlify.app/u/aria_chen", sessionUser?.website)
    assertEquals(listOf("Design", "Photography", "Indie Music"), sessionUser?.interestTags)
    assertTrue(sessionUser?.isPrivate == true)

    val storedUser = store.loadUsers().firstOrNull { it.id == userId }
    assertNotNull(storedUser)
    assertEquals("Bengaluru, India", storedUser?.location)
    assertEquals("https://vybeonline.netlify.app/u/aria_chen", storedUser?.website)
    assertEquals(listOf("Design", "Photography", "Indie Music"), storedUser?.interestTags)

    // Pin 4 posts sequentially -> keeps max 3 most recent pinned IDs, then unpin works cleanly
    repo.togglePinPost("post_1")
    repo.togglePinPost("post_2")
    repo.togglePinPost("post_3")
    repo.togglePinPost("post_4")
    assertEquals(listOf("post_4", "post_3", "post_2"), repo.currentUser.value?.pinnedPostIds)
    assertEquals(listOf("post_4", "post_3", "post_2"), session.loadCurrentUser()?.pinnedPostIds)
    assertEquals(listOf("post_4", "post_3", "post_2"), store.loadUsers().first { it.id == userId }.pinnedPostIds)

    repo.togglePinPost("post_3")
    assertEquals(listOf("post_4", "post_2"), repo.currentUser.value?.pinnedPostIds)
    assertEquals(listOf("post_4", "post_2"), session.loadCurrentUser()?.pinnedPostIds)
  }

  @Test
  fun `explore search history persists submitted terms per user without leaking across accounts`() {
    assertTrue(repo.loginUser("searcher_one@example.com", "Password123!"))
    val userOneId = repo.currentUser.value!!.id

    // Blank or single-char keystrokes are never stored
    repo.addSearchQueryToHistory("")
    repo.addSearchQueryToHistory("a")
    assertTrue(repo.searchHistory.value.isEmpty())

    // Submitted terms deduplicate case-insensitively and move newest to front
    repo.addSearchQueryToHistory("Mindfulness")
    repo.addSearchQueryToHistory("#sunsetvibes")
    repo.addSearchQueryToHistory("mindfulness")
    assertEquals(listOf("mindfulness", "#sunsetvibes"), repo.searchHistory.value)
    assertEquals(listOf("mindfulness", "#sunsetvibes"), store.loadSearchHistory(userOneId))

    // Remove single term persists
    repo.removeSearchQueryFromHistory("#sunsetvibes")
    assertEquals(listOf("mindfulness"), repo.searchHistory.value)
    assertEquals(listOf("mindfulness"), store.loadSearchHistory(userOneId))

    // Logout clears in-memory StateFlow while retaining User 1 persisted history for re-login
    repo.logout()
    assertTrue(repo.searchHistory.value.isEmpty())

    // Logging in as User 2 starts isolated and must not see User 1 history
    Thread.sleep(5)
    assertTrue(repo.loginUser("searcher_two@example.com", "Password123!"))
    val userTwoId = repo.currentUser.value!!.id
    assertNotEqualsOrNull(userOneId, userTwoId)
    assertTrue("User 2 must not inherit User 1 search history", repo.searchHistory.value.isEmpty())

    repo.addSearchQueryToHistory("Creator Economy")
    assertEquals(listOf("Creator Economy"), repo.searchHistory.value)
    assertEquals(listOf("Creator Economy"), store.loadSearchHistory(userTwoId))
    assertEquals(listOf("mindfulness"), store.loadSearchHistory(userOneId))

    // Clear history empties both StateFlow and User 2 store
    repo.clearSearchHistory()
    assertTrue(repo.searchHistory.value.isEmpty())
    assertTrue(store.loadSearchHistory(userTwoId).isEmpty())
  }

  @Test
  fun `canonical profile url uses vybeonline netlify app and never vybe app`() {
    val withUsername = QrCodeGenerator.buildCanonicalProfileUrl("u_999", "@aria.vybe")
    assertEquals("https://vybeonline.netlify.app/u/aria.vybe", withUsername)
    assertFalse(withUsername.contains("vybe.app"))

    val withoutUsername = QrCodeGenerator.buildCanonicalProfileUrl("u_999", "")
    assertEquals("https://vybeonline.netlify.app/profile/u_999", withoutUsername)
    assertFalse(withoutUsername.contains("vybe.app"))

    val parsedUser = QrCodeGenerator.parseProfileDeepLink(withUsername)
    assertNotNull(parsedUser)
    assertEquals("aria.vybe", parsedUser?.username)

    val parsedId = QrCodeGenerator.parseProfileDeepLink(withoutUsername)
    assertNotNull(parsedId)
    assertEquals("u_999", parsedId?.userId)
  }

  @Test
  fun `deactivate account marks local profile deactivated and clears active session`() {
    assertTrue(repo.loginUser("deactivating_user@example.com", "Password123!"))
    val uid = repo.currentUser.value!!.id

    var callbackOk = false
    repo.deactivateAccount { ok, err ->
      callbackOk = ok && err == null
    }

    assertTrue(callbackOk)
    assertFalse(repo.isLoggedIn.value)
    assertNull(repo.currentUser.value)
    assertFalse(session.isLoggedIn)
    val stored = store.loadUsers().firstOrNull { it.id == uid }
    assertNotNull(stored)
    assertTrue(stored?.isDeactivated == true)
  }

  @Test
  fun `safeExternalUrl blocks dangerous schemes and normalizes bare domains`() {
    assertEquals("https://vybeonline.netlify.app/u/aria", ProfileInputValidator.safeExternalUrl("https://vybeonline.netlify.app/u/aria"))
    assertEquals("http://example.org/work", ProfileInputValidator.safeExternalUrl("http://example.org/work"))
    assertEquals("https://aria.studio/portfolio", ProfileInputValidator.safeExternalUrl("aria.studio/portfolio"))

    assertNull(ProfileInputValidator.safeExternalUrl("javascript:alert(1)"))
    assertNull(ProfileInputValidator.safeExternalUrl("data:text/html,<script>alert(1)</script>"))
    assertNull(ProfileInputValidator.safeExternalUrl("vbscript:msgbox(1)"))
    assertNull(ProfileInputValidator.safeExternalUrl("file:///etc/passwd"))
    assertNull(ProfileInputValidator.safeExternalUrl("intent://scan/#Intent"))
    assertNull(ProfileInputValidator.safeExternalUrl("content://media/external"))
    assertNull(ProfileInputValidator.safeExternalUrl("not-a-domain"))
    assertNull(ProfileInputValidator.safeExternalUrl("   "))
  }

  @Test
  fun `validateProfileEdit enforces field limits deduplicates interests and rejects unsafe URLs`() {
    val ok = ProfileInputValidator.validateProfileEdit(
      displayName = "  Aria   Chen ",
      bio = "Calm creative vibes",
      location = "Bengaluru,   India",
      website = "aria.studio/work",
      interestsRaw = listOf("#Design", "Music", "design", "  Photography ")
    )
    assertTrue(ok.isValid)
    assertEquals("Aria Chen", ok.displayName)
    assertEquals("Bengaluru, India", ok.location)
    assertEquals("https://aria.studio/work", ok.website)
    assertEquals(listOf("Design", "Music", "Photography"), ok.interests)

    assertFalse(
      ProfileInputValidator.validateProfileEdit("   ", "", "", "", emptyList()).isValid
    )
    assertFalse(
      ProfileInputValidator.validateProfileEdit("Aria", "a".repeat(241), "", "", emptyList()).isValid
    )
    assertFalse(
      ProfileInputValidator.validateProfileEdit("Aria", "", "", "javascript:alert(1)", emptyList()).isValid
    )
    assertFalse(
      ProfileInputValidator.validateProfileEdit(
        displayName = "Aria",
        bio = "",
        location = "",
        website = "",
        interestsRaw = (1..11).map { "tag$it" }
      ).isValid
    )
  }

  @Test
  fun `validateProfileMediaUpload enforces 10MB limit and supported image mimes`() {
    assertTrue(ProfileInputValidator.validateProfileMediaUpload("image/jpeg", 1024L).isValid)
    assertTrue(ProfileInputValidator.validateProfileMediaUpload("image/png", ProfileInputValidator.MAX_PROFILE_MEDIA_BYTES).isValid)
    assertTrue(ProfileInputValidator.validateProfileMediaUpload("image/webp; charset=utf-8", 2048L).isValid)
    assertTrue(ProfileInputValidator.validateProfileMediaUpload("image/heic", 4096L).isValid)

    assertFalse(ProfileInputValidator.validateProfileMediaUpload("image/jpeg", 0L).isValid)
    assertFalse(ProfileInputValidator.validateProfileMediaUpload("image/png", ProfileInputValidator.MAX_PROFILE_MEDIA_BYTES + 1L).isValid)
    assertFalse(ProfileInputValidator.validateProfileMediaUpload("video/mp4", 1024L).isValid)
    assertFalse(ProfileInputValidator.validateProfileMediaUpload("image/svg+xml", 1024L).isValid)
  }

  @Test
  fun `sanitizePostgrestSearchTerm strips prefixes and filter metacharacters`() {
    assertEquals("aria_vybe", ProfileInputValidator.sanitizePostgrestSearchTerm("  @aria_vybe "))
    assertEquals("MindfulLiving", ProfileInputValidator.sanitizePostgrestSearchTerm("#MindfulLiving"))
    assertEquals("foo bar", ProfileInputValidator.sanitizePostgrestSearchTerm("foo%(),*\"';bar"))
    assertEquals(64, ProfileInputValidator.sanitizePostgrestSearchTerm("x".repeat(100)).length)
  }

  @Test
  fun `filterProfileTabPosts includes video and legacy reel posts in Posts while keeping Unfiltered Tagged and Highlights separate`() {
    val owner = User(id = "u_owner", username = "aria_vybe", displayName = "Aria")
    val peer = User(id = "u_peer", username = "rohan_k", displayName = "Rohan")

    val photo = Post(id = "p_photo", author = owner, caption = "Sunset", mediaType = MediaType.IMAGE, timestamp = "1h")
    val reel = Post(id = "p_reel", author = owner, caption = "Clip", mediaType = MediaType.REEL, isVybesReel = true, timestamp = "1h")
    val video = Post(id = "p_video", author = owner, caption = "Vid", mediaType = MediaType.VIDEO, timestamp = "1h")
    val raw = Post(id = "p_raw", author = owner, caption = "Raw", isUnfiltered = true, timestamp = "1h")
    val selfMention = Post(id = "p_self", author = owner, caption = "Self @aria_vybe", timestamp = "1h")
    val archived = Post(id = "p_arch", author = owner, caption = "Old", isArchived = true, timestamp = "1h")
    val peerTag = Post(id = "p_peer_tag", author = peer, caption = "Collab @aria_vybe", timestamp = "1h")
    val saved = Post(id = "p_saved", author = peer, caption = "Bookmark", isSaved = true, timestamp = "1h")

    val userPosts = listOf(photo, reel, video, raw, selfMention, archived)
    val allPosts = userPosts + listOf(peerTag, saved)

    assertEquals(
      listOf("p_photo", "p_reel", "p_video", "p_self"),
      ProfileInputValidator.filterProfileTabPosts("Posts", userPosts, listOf(saved), allPosts, listOf("p_raw", "p_photo"), "aria_vybe", owner.id).map { it.id }
    )
    assertEquals(
      listOf("p_raw"),
      ProfileInputValidator.filterProfileTabPosts("Unfiltered", userPosts, listOf(saved), allPosts, listOf("p_raw"), "aria_vybe", owner.id).map { it.id }
    )
    assertEquals(
      listOf("p_peer_tag"),
      ProfileInputValidator.filterProfileTabPosts("Tagged", userPosts, listOf(saved), allPosts, listOf("p_raw"), "aria_vybe", owner.id).map { it.id }
    )
    assertEquals(
      listOf("p_raw", "p_photo"),
      ProfileInputValidator.filterProfileTabPosts("Highlights", userPosts, listOf(saved), allPosts, listOf("p_raw", "p_photo"), "aria_vybe", owner.id).map { it.id }
    )
  }

  @Test
  fun `filterExploreCommunities matches query and resolveTargetProfileOrNull avoids synthetic fallback`() {
    val c1 = Community(id = "c1", name = "Indie Sound Lab", description = "Vocalists & producers", category = "Music")
    val c2 = Community(id = "c2", name = "Run Club", description = "Morning 5k", category = "Fitness")

    assertEquals(2, ProfileInputValidator.filterExploreCommunities(listOf(c1, c2), "").size)
    assertEquals(listOf("c1"), ProfileInputValidator.filterExploreCommunities(listOf(c1, c2), "music").map { it.id })
    assertEquals(listOf("c2"), ProfileInputValidator.filterExploreCommunities(listOf(c1, c2), "5k").map { it.id })

    val me = User(id = "u_me", username = "me", displayName = "Me")
    val other = User(id = "u_other", username = "other", displayName = "Other")
    assertNull(ProfileInputValidator.resolveTargetProfileOrNull(true, null, null, listOf(other)))
    assertNull(ProfileInputValidator.resolveTargetProfileOrNull(false, me, null, listOf(other)))
    assertEquals("Me", ProfileInputValidator.resolveTargetProfileOrNull(true, me, other, listOf(other))?.displayName)
    assertEquals("Other", ProfileInputValidator.resolveTargetProfileOrNull(false, me, other, listOf(other))?.displayName)
  }

  private fun assertNotEqualsOrNull(first: String, second: String) {
    assertFalse("Expected distinct user IDs ($first vs $second)", first == second)
  }
}

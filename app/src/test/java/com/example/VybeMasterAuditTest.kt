package com.example

import android.net.Uri
import com.example.data.model.MoodType
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.model.matchesMoodFilter
import com.example.data.remote.SupabaseConfig
import com.example.data.repository.MessagingEligibility
import com.example.data.repository.VybeRepository
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.util.GitHubUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VybeMasterAuditTest {

  @Test
  fun `supabase config never exposes service role key and builds valid JWT auth header`() {
    val key = SupabaseConfig.supabaseAnonKey
    assertFalse("Client key must never contain service_role", key.contains("service_role", ignoreCase = true))
    assertEquals(
      "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature",
      SupabaseConfig.buildAuthHeader("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature")
    )
    assertNull(
      "Publishable non-JWT keys must not be sent as Bearer tokens",
      SupabaseConfig.buildAuthHeader("sb_publishable_example")
    )
  }

  @Test
  fun `password recovery deep link routes strictly to PASSWORD_RECOVERY screen`() {
    val viewModel = VybeViewModel()
    val recoveryUri = Uri.parse("vybe://reset-password?type=recovery&token_hash=pkce_test_hash&email=member@vybe.app")
    viewModel.handleIncomingAuthUri(recoveryUri)

    assertEquals(Screen.PASSWORD_RECOVERY, viewModel.currentScreen.value)

    viewModel.exitPasswordRecoveryToLogin()
    assertEquals(Screen.AUTH, viewModel.currentScreen.value)
  }

  @Test
  fun `mood filter includes untagged posts in General feed`() {
    val author = User(id = "u_author", username = "author", displayName = "Author")
    val untaggedPost = Post(
      id = "p_untagged",
      author = author,
      caption = "Everyday update without explicit mood",
      timestamp = "Just now",
      moodZone = null
    )
    val funPost = Post(
      id = "p_fun",
      author = author,
      caption = "High energy session",
      timestamp = "Just now",
      moodZone = MoodType.FUN
    )

    assertTrue("Untagged posts must appear in General feed", untaggedPost.matchesMoodFilter(MoodType.GENERAL))
    assertTrue("Untagged posts must appear when no mood filter is active", untaggedPost.matchesMoodFilter(null))
    assertFalse("Untagged posts must not appear in specific FUN filter", untaggedPost.matchesMoodFilter(MoodType.FUN))
    assertTrue("Fun post must appear in FUN filter", funPost.matchesMoodFilter(MoodType.FUN))
  }

  @Test
  fun `private profile content is gated from non-followers while public profile is visible`() {
    val repo = VybeRepository.getInstance()
    repo.loginUser("viewer@vybe.app", "Password123!")

    val privatePeer = User(
      id = "private_peer_001",
      username = "private_creator",
      displayName = "Private Creator",
      isPrivate = true
    )
    val publicPeer = User(
      id = "public_peer_001",
      username = "public_creator",
      displayName = "Public Creator",
      isPrivate = false
    )

    assertFalse("Non-follower must not view private profile content", repo.canViewUserContent(privatePeer))
    assertTrue("Viewer can view public profile content", repo.canViewUserContent(publicPeer))
  }

  @Test
  fun `mutual follow messaging rule and message privacy settings are enforced`() {
    val repo = VybeRepository.getInstance()
    repo.loginUser("sender@vybe.app", "Password123!")

    val closedDmUser = User(
      id = "dm_closed_user",
      username = "closed_dm",
      displayName = "Closed DMs",
      messagePrivacy = "NONE"
    )
    val mutualOnlyStranger = User(
      id = "dm_mutual_stranger",
      username = "mutual_only",
      displayName = "Mutual Only",
      messagePrivacy = "MUTUAL_FOLLOWS",
      followsMe = false,
      isFollowedByMe = false
    )
    val openDmUser = User(
      id = "dm_open_user",
      username = "open_dm",
      displayName = "Open DMs",
      messagePrivacy = "EVERYONE"
    )

    assertTrue(repo.checkMessagingEligibility(closedDmUser) is MessagingEligibility.Denied)
    assertTrue(repo.checkMessagingEligibility(mutualOnlyStranger) is MessagingEligibility.Denied)
    assertTrue(repo.checkMessagingEligibility(openDmUser) is MessagingEligibility.Allowed)
  }

  @Test
  fun `vybe matches manual add and remove controls update audience accurately`() {
    val repo = VybeRepository.getInstance()
    repo.loginUser("creator@vybe.app", "Password123!")

    val friend = User(
      id = "match_friend_42",
      username = "trusted_friend",
      displayName = "Trusted Friend"
    )

    repo.addVybeMatch(friend)
    assertTrue("Friend should be in VYBE Matches after adding", repo.isVybeMatch(friend.id))

    repo.removeVybeMatch(friend.id)
    assertFalse("Friend should be removed from VYBE Matches", repo.isVybeMatch(friend.id))
  }

  @Test
  fun `hidden chat pin has no default backdoor and verifies cryptographic hash`() {
    val repo = VybeRepository.getInstance()
    if (!repo.hasHiddenChatPinSet()) {
      assertFalse("Default 1234 must never unlock when no PIN is configured", repo.verifyHiddenChatPin("1234"))
    }
    repo.setHiddenChatPin("8426")
    assertTrue(repo.hasHiddenChatPinSet())
    assertFalse("Wrong PIN must fail verification", repo.verifyHiddenChatPin("1234"))
    assertTrue("Configured PIN must pass verification", repo.verifyHiddenChatPin("8426"))
  }

  @Test
  fun `github updater rejects placeholder or malformed repository input`() = runBlocking {
    val placeholderRes = GitHubUpdater.checkForUpdates("my-github-username/vybe")
    assertTrue(placeholderRes.isFailure)

    val malformedRes = GitHubUpdater.checkForUpdates("invalid_repo_without_slash")
    assertTrue(malformedRes.isFailure)
  }

  @Test
  fun `moshi serializes and deserializes supabase DTOs with json field names and defaults`() {
    val adapter = com.example.data.remote.SupabaseNetworkClient.moshi
      .adapter(com.example.data.remote.SupabasePostDto::class.java)
    val rawJson = """
      {
        "id": "p_101",
        "author_id": "u_202",
        "caption": "Hello VYBE",
        "mood_zone": "CHILL",
        "is_vybes_reel": true,
        "likes_count": 7
      }
    """.trimIndent()
    val parsed = adapter.fromJson(rawJson)
    assertNotNull(parsed)
    assertEquals("p_101", parsed?.id)
    assertEquals("u_202", parsed?.authorId)
    assertEquals("CHILL", parsed?.moodZone)
    assertEquals(true, parsed?.isVybesReel)
    assertEquals(7, parsed?.likesCount)
    assertEquals(0, parsed?.viewsCount)
  }

  @Test
  fun `relative timestamps and joined month year format authoritative ISO dates without substituting current time`() {
    val baseNow = com.example.util.TimestampUtils.parseIsoMillis("2026-10-24T12:00:00Z")!!

    val utc = java.util.TimeZone.getTimeZone("UTC")
    assertEquals("Just now", com.example.util.TimestampUtils.formatRelative("2026-10-24T11:59:35Z", baseNow, timeZone = utc))
    assertEquals("2 minutes ago", com.example.util.TimestampUtils.formatRelative("2026-10-24T11:58:00Z", baseNow, timeZone = utc))
    assertEquals("45 minutes ago", com.example.util.TimestampUtils.formatRelative("2026-10-24T11:15:00Z", baseNow, timeZone = utc))
    assertEquals("3 hours ago", com.example.util.TimestampUtils.formatRelative("2026-10-24T09:00:00Z", baseNow, timeZone = utc))
    assertEquals("2 days ago", com.example.util.TimestampUtils.formatRelative("2026-10-22T12:00:00Z", baseNow, timeZone = utc))
    assertEquals("Oct 10", com.example.util.TimestampUtils.formatRelative("2026-10-10T12:00:00Z", baseNow, timeZone = utc))
    assertEquals("", com.example.util.TimestampUtils.formatRelative(null, baseNow, timeZone = utc))

    val joined = com.example.util.TimestampUtils.formatJoinedMonthYear("2026-10-15T08:30:00Z")
    assertTrue("Joined date must start with Joined and include year 2026", joined.startsWith("Joined ") && joined.contains("2026"))
    assertEquals("VYBE Member", com.example.util.TimestampUtils.formatJoinedMonthYear(null, ""))
  }

  @Test
  fun `unload room generates shareable VYB-XXXX-XXXX room code and supports join by code`() {
    val room = com.example.data.model.UnloadRoom(
      id = "room_9a8b7c6d",
      title = "Late Night Reflection",
      description = "Open supportive circle",
      topicTag = "Silent Company"
    )
    assertTrue(
      "Room code must match VYB-XXXX-XXXX format: ${room.roomCode}",
      Regex("^VYB-[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(room.roomCode)
    )
    assertEquals("VYB-9A8B-7C6D", room.roomCode)
  }

  @Test
  fun `profile completeness detects null or placeholder usernames and requires dual verification`() {
    val nullUsernameUser = User(
      id = "u_legacy_null",
      username = "null",
      displayName = "Alex Rivera",
      isEmailVerified = true,
      isPhoneVerified = false
    )
    assertTrue("Literal 'null' username must be flagged as incomplete profile", nullUsernameUser.hasIncompleteProfile)
    assertFalse("Single-channel verification must not mark account fully verified", nullUsernameUser.isFullyVerifiedAccount)

    val completeVerifiedUser = User(
      id = "u_complete",
      username = "alex_rivera",
      displayName = "Alex Rivera",
      isEmailVerified = true,
      isPhoneVerified = true,
      postsCount = 0
    )
    assertFalse("Valid username and displayName must pass completeness check", completeVerifiedUser.hasIncompleteProfile)
    assertTrue("Both email and phone OTP verified must mark account fully verified", completeVerifiedUser.isFullyVerifiedAccount)
    assertEquals(0, completeVerifiedUser.postsCount)
  }
}



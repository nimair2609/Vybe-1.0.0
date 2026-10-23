package com.example

import com.example.data.model.MoodType
import com.example.data.repository.VybeRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VybeCoreUnitTest {

  private lateinit var repository: VybeRepository

  @Before
  fun setUp() {
    repository = VybeRepository.getInstance()
  }

  @Test
  fun testAgeGateEnforcementUnder16() {
    // Attempt signup with birth date corresponding to age < 16 (e.g. born in 2015)
    val result = repository.registerAccount("young_user", "young@vybe.in", "2015-05-10")
    assertFalse("Users under 16 must be rejected", result.first)
    assertTrue(result.second.contains("16 and above"))
  }

  @Test
  fun testAgeGateEnforcementOver16() {
    // Attempt signup with birth date corresponding to age 20
    val result = repository.registerAccount("eligible_user", "eligible@vybe.in", "2006-01-15")
    assertTrue("Users aged 16+ must be accepted", result.first)
  }

  @Test
  fun testPostLikeToggle() {
    if (repository.posts.value.isEmpty()) {
      repository.createPost(caption = "Initial test post", mood = MoodType.HEALING)
    }
    val initialPosts = repository.posts.value
    assertTrue(initialPosts.isNotEmpty())
    val targetPost = initialPosts.first()
    val initialLikes = targetPost.likesCount
    val wasLiked = targetPost.isLiked

    repository.toggleLikePost(targetPost.id)
    val updatedPost = repository.posts.value.first { it.id == targetPost.id }

    assertEquals(!wasLiked, updatedPost.isLiked)
    if (!wasLiked) {
      assertEquals(initialLikes + 1, updatedPost.likesCount)
    } else {
      assertEquals(maxOf(0, initialLikes - 1), updatedPost.likesCount)
    }
  }

  @Test
  fun testCreatePostWithMoodZone() {
    val initialCount = repository.posts.value.size
    repository.createPost(
      caption = "Unit test post: Colaba morning reflection",
      mood = MoodType.HEALING
    )

    val updatedPosts = repository.posts.value
    assertEquals(initialCount + 1, updatedPosts.size)
    assertEquals(MoodType.HEALING, updatedPosts.first().moodZone)
    assertEquals("Unit test post: Colaba morning reflection", updatedPosts.first().caption)
  }

  @Test
  fun testVotePrediction() {
    val predictions = repository.predictions.value
    assertTrue(predictions.isNotEmpty())
    val pred = predictions.first()
    val optId = pred.options.first().id

    repository.votePrediction(pred.id, optId)
    val updated = repository.predictions.value.first { it.id == pred.id }
    assertEquals(optId, updated.userSelectedOptionId)
  }

  @Test
  fun testTermsAcceptanceRequired() = kotlinx.coroutines.runBlocking {
    val result = repository.registerWithSupabase(
      username = "test_terms_user",
      email = "terms@vybe.in",
      password = "Password123!",
      firstName = "Terms",
      lastName = "Tester",
      phone = "+91 98765 11111",
      birthDateStr = "2000-01-01",
      termsAccepted = false
    )
    assertFalse("Registration must be rejected if terms not accepted", result.first)
    assertTrue(result.second.contains("Terms of Service"))
  }

  @Test
  fun testLoginAndLogoutSessionFlow() {
    val loginSuccess = repository.loginUser("eligible@vybe.in", "Password123!")
    assertTrue(loginSuccess)
    assertTrue("User should be logged in after loginUser", repository.isLoggedIn.value)
    assertNotNull("Current user must not be null after login", repository.currentUser.value)

    repository.logout()
    assertFalse("User should be logged out after logout", repository.isLoggedIn.value)
    assertNull("Current user must be null after logout", repository.currentUser.value)
  }

  @Test
  fun testOptimisticDirectMessageSendingAndDeduplication() {
    val convs = repository.conversations.value
    assertTrue(convs.isNotEmpty())
    val targetConv = convs.first()
    val initialMsgCount = targetConv.messages.size

    repository.loginUser("eligible@vybe.in", "Password123!")
    repository.sendDirectMessage(targetConv.id, "Realtime test message for optimistic send")

    val updatedConv = repository.conversations.value.first { it.id == targetConv.id }
    assertEquals(initialMsgCount + 1, updatedConv.messages.size)
    val latest = updatedConv.messages.last()
    assertEquals("Realtime test message for optimistic send", latest.text)
    assertTrue("Optimistic message must have a clientId or ID assigned", latest.id.isNotBlank())
  }

  @Test
  fun testNotificationReadAndDelete() {
    val notifs = repository.notifications.value
    assertTrue(notifs.isNotEmpty())
    val firstNotif = notifs.first()

    repository.markNotificationAsRead(firstNotif.id)
    val readNotif = repository.notifications.value.first { it.id == firstNotif.id }
    assertTrue(readNotif.isRead)

    val countBefore = repository.notifications.value.size
    repository.deleteNotification(firstNotif.id)
    val countAfter = repository.notifications.value.size
    assertEquals(countBefore - 1, countAfter)
    assertNull(repository.notifications.value.firstOrNull { it.id == firstNotif.id })
  }

  @Test
  fun testToggleFollowUser() {
    val allUsers = repository.allUsers.value
    assertTrue(allUsers.size >= 2)
    val target = allUsers.last()
    val targetId = target.id
    val initialFollowers = target.followersCount

    val isNowFollowing = repository.toggleFollowUser(targetId)
    val updatedTarget = repository.allUsers.value.first { it.id == targetId }
    if (isNowFollowing) {
      assertEquals(initialFollowers + 1, updatedTarget.followersCount)
      assertTrue(repository.followingUserIds.value.contains(targetId))
    }

    // Toggle back
    val toggledAgain = repository.toggleFollowUser(targetId)
    assertNotEquals(isNowFollowing, toggledAgain)
  }

  @Test
  fun testCallAvailabilityCheck() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val offlineUser = com.example.data.model.User(
      id = "offline_user_99",
      username = "offline_user",
      displayName = "Offline Friend",
      livePulse = com.example.data.model.LivePulseStatus.NONE
    )
    val result = repository.initiateCall(offlineUser, isVideo = false)
    assertTrue("Offline user must return Offline result", result is com.example.data.model.CallAvailabilityResult.Offline)
  }

  @Test
  fun testRecordPostViewIncrementsCount() {
    if (repository.posts.value.isEmpty()) {
      repository.createPost(caption = "View tracking test post", mood = MoodType.CHILL)
    }
    val targetPost = repository.posts.value.first()
    val initialViews = targetPost.viewsCount

    repository.recordPostView(targetPost.id)
    val updatedPost = repository.posts.value.first { it.id == targetPost.id }
    assertEquals(initialViews + 1, updatedPost.viewsCount)
  }

  @Test
  fun testDeleteOwnPostRemovesFromFeed() {
    repository.createPost(caption = "Temporary post to delete", mood = MoodType.HEALING)
    val createdPost = repository.posts.value.first { it.caption == "Temporary post to delete" }
    val initialCount = repository.posts.value.size

    repository.deletePost(createdPost.id)
    val updatedPosts = repository.posts.value
    assertEquals(initialCount - 1, updatedPosts.size)
    assertNull(updatedPosts.firstOrNull { it.id == createdPost.id })
  }

  @Test
  fun testStartConversationAndToggleUnread() {
    val peerUser = repository.allUsers.value.first { it.id != repository.currentUser.value?.id }
    val conversation = repository.getOrCreateConversation(peerUser)
    assertNotNull(conversation)
    assertEquals(peerUser.id, conversation.peerUser.id)

    // Verify toggling unread
    val initialUnread = conversation.unreadCount
    repository.toggleConversationUnread(conversation.id)
    val updated = repository.conversations.value.first { it.id == conversation.id }
    val expected = if (initialUnread > 0) 0 else 1
    assertEquals(expected, updated.unreadCount)
  }

  @Test
  fun testGitHubUpdaterVersionComparison() {
    assertTrue(com.example.util.GitHubUpdater.isNewerVersion("2.0.0", "v2.1.0"))
    assertTrue(com.example.util.GitHubUpdater.isNewerVersion("v1.0.0", "v1.0.1"))
    assertTrue(com.example.util.GitHubUpdater.isNewerVersion("2.0.0", "2.0.1"))
    assertFalse(com.example.util.GitHubUpdater.isNewerVersion("2.1.0", "v2.0.9"))
    assertFalse(com.example.util.GitHubUpdater.isNewerVersion("2.0.0", "v2.0.0"))
  }

  @Test
  fun testGitHubUpdaterSlugCleaning() {
    assertEquals("nrnext/vybe", com.example.util.GitHubUpdater.cleanRepoSlug("https://github.com/nrnext/vybe/"))
    assertEquals("nrnext/vybe", com.example.util.GitHubUpdater.cleanRepoSlug("github.com/nrnext/vybe"))
    assertEquals("owner/app", com.example.util.GitHubUpdater.cleanRepoSlug("owner/app"))
  }
}


package com.example

import com.example.data.model.CallAvailabilityResult
import com.example.data.model.LivePulseStatus
import com.example.data.model.MoodType
import com.example.data.model.NotificationItem
import com.example.data.model.NotificationType
import com.example.data.model.User
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
    val result = repository.registerAccount("young_user", "young@vybe.in", "2015-05-10")
    assertFalse("Users under 16 must be rejected", result.first)
    assertTrue(result.second.contains("16 and above"))
  }

  @Test
  fun testAgeGateEnforcementOver16() {
    val result = repository.registerAccount("eligible_user", "eligible@vybe.in", "2006-01-15")
    assertTrue("Users aged 16+ must be accepted", result.first)
  }

  @Test
  fun testPostLikeToggle() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val author = repository.currentUser.value!!
    val seededPost = com.example.data.model.Post(
      id = "post_like_test_1",
      author = author,
      caption = "Verified test post",
      timestamp = "Just now",
      moodZone = MoodType.HEALING,
      clientUploadId = "upload_like_test_1"
    )
    repository.recordVerifiedPostForState(seededPost)

    val targetPost = repository.posts.value.first { it.id == seededPost.id }
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
    repository.loginUser("eligible@vybe.in", "Password123!")
    val author = repository.currentUser.value!!
    val initialCount = repository.posts.value.filterNot { it.clientUploadId == "upload_mood_test_1" }.size

    // Verify online-first rule: unauthenticated upload attempt without live backend confirmation must NOT falsely publish
    repository.createPost(
      caption = "Unverified offline upload",
      mood = MoodType.HEALING
    )
    Thread.sleep(150)
    assertFalse(
      "Failed/unauthenticated uploads must never be falsely added to published posts",
      repository.posts.value.any { it.caption == "Unverified offline upload" }
    )

    // Verify verified post persistence with moodZone and retry deduplication by clientUploadId
    val verifiedPost = com.example.data.model.Post(
      id = "post_mood_test_1",
      author = author,
      caption = "Unit test post: morning reflection",
      timestamp = "Just now",
      moodZone = MoodType.HEALING,
      clientUploadId = "upload_mood_test_1"
    )
    repository.recordVerifiedPostForState(verifiedPost)
    repository.recordVerifiedPostForState(verifiedPost.copy(id = "post_mood_test_1_retry"))

    val updatedPosts = repository.posts.value
    assertEquals(initialCount + 1, updatedPosts.size)
    assertEquals(MoodType.HEALING, updatedPosts.first().moodZone)
    assertEquals("Unit test post: morning reflection", updatedPosts.first().caption)
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
  fun testOptimisticDirectMessageSending() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val peer = User(
      id = "peer_real_1",
      username = "peer_one",
      displayName = "Peer One"
    )
    val targetConv = repository.getOrCreateConversation(peer)
    val initialMsgCount = targetConv.messages.size

    repository.sendDirectMessage(targetConv.id, "Realtime test message for optimistic send")

    val updatedConv = repository.conversations.value.first { it.id == targetConv.id }
    assertEquals(initialMsgCount + 1, updatedConv.messages.size)
    val latest = updatedConv.messages.last()
    assertEquals("Realtime test message for optimistic send", latest.text)
    assertTrue("Optimistic message must have an ID assigned", latest.id.isNotBlank())
  }

  @Test
  fun testNotificationReadAndDelete() {
    val notifId = "notif_test_${System.currentTimeMillis()}"
    repository.addNotification(
      NotificationItem(
        id = notifId,
        type = NotificationType.LIKE,
        title = "Post Liked",
        message = "Someone liked your post",
        timestamp = "Just now",
        isRead = false
      )
    )

    repository.markNotificationAsRead(notifId)
    val readNotif = repository.notifications.value.first { it.id == notifId }
    assertTrue(readNotif.isRead)

    val countBefore = repository.notifications.value.size
    repository.deleteNotification(notifId)
    val countAfter = repository.notifications.value.size
    assertEquals(countBefore - 1, countAfter)
    assertNull(repository.notifications.value.firstOrNull { it.id == notifId })
  }

  @Test
  fun testCallAvailabilityCheck() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val offlineUser = User(
      id = "offline_user_99",
      username = "offline_user",
      displayName = "Offline Friend",
      livePulse = LivePulseStatus.NONE
    )
    val result = repository.initiateCall(offlineUser, isVideo = false)
    assertTrue("Offline user must return Offline result", result is CallAvailabilityResult.Offline)
  }

  @Test
  fun testRecordPostViewIncrementsCount() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val author = repository.currentUser.value!!
    val postId = "post_view_test_${System.currentTimeMillis()}"
    val seededPost = com.example.data.model.Post(
      id = postId,
      author = author,
      caption = "View tracking test post",
      timestamp = "Just now",
      moodZone = MoodType.CHILL,
      clientUploadId = "upload_$postId"
    )
    repository.recordVerifiedPostForState(seededPost)

    val targetPost = repository.posts.value.first { it.id == postId }
    val initialViews = targetPost.viewsCount

    repository.recordPostView(targetPost.id)
    val updatedPost = repository.posts.value.first { it.id == targetPost.id }
    assertEquals(initialViews + 1, updatedPost.viewsCount)
  }

  @Test
  fun testDeleteOwnPostRemovesFromFeed() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val author = repository.currentUser.value!!
    val postId = "post_delete_test_${System.currentTimeMillis()}"
    val createdPost = com.example.data.model.Post(
      id = postId,
      author = author,
      caption = "Temporary post to delete",
      timestamp = "Just now",
      moodZone = MoodType.HEALING,
      clientUploadId = "upload_$postId"
    )
    repository.recordVerifiedPostForState(createdPost)
    val initialCount = repository.posts.value.size

    repository.deletePost(createdPost.id)
    val updatedPosts = repository.posts.value
    assertEquals(initialCount - 1, updatedPosts.size)
    assertNull(updatedPosts.firstOrNull { it.id == createdPost.id })
  }

  @Test
  fun testStartConversationAndToggleUnread() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val peerUser = User(
      id = "peer_unread_test_1",
      username = "peer_unread",
      displayName = "Peer Unread"
    )
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

  @Test
  fun testGitHubUpdaterFileSizeFormatting() {
    val bytes15_5 = (15.5 * 1024 * 1024).toLong()
    assertEquals("15.5 MB", com.example.util.GitHubUpdater.formatFileSize(bytes15_5))
    assertEquals("24.0 MB", com.example.util.GitHubUpdater.formatFileSize(24L * 1024L * 1024L))
    assertEquals("", com.example.util.GitHubUpdater.formatFileSize(0L))
  }
}

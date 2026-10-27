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

  @Test
  fun testNoSampleUnloadRoomsSeededByDefault() {
    assertFalse(
      "Sample Unload rooms (ul_1, ul_2) must not be seeded",
      repository.unloadRooms.value.any { it.id == "ul_1" || it.id == "ul_2" }
    )
  }

  @Test
  fun testCallHistoryRecordedInChatWithDuration() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val onlinePeer = User(
      id = "peer_call_history_1",
      username = "online_peer",
      displayName = "Online Peer",
      isOnline = true,
      livePulse = LivePulseStatus.OPEN_TO_CHAT
    )
    val startRes = repository.initiateCall(onlinePeer, isVideo = true)
    assertTrue("Online peer call must start", startRes is CallAvailabilityResult.Available)
    assertNotNull("Active call session must exist", repository.activeCall.value)

    repository.endCall()
    assertNull("Active call must clear after endCall", repository.activeCall.value)

    val conv = repository.conversations.value.firstOrNull { it.peerUser.id == onlinePeer.id }
    assertNotNull("Conversation with peer must exist after call ends", conv)
    val callLogMsg = conv!!.messages.lastOrNull { it.mediaType == "CALL_LOG" }
    assertNotNull("Call history entry must be added to chat messages", callLogMsg)
    assertTrue(
      "Call history metadata must record call type",
      callLogMsg!!.mediaUrl.orEmpty().contains("type=video", ignoreCase = true)
    )
    assertEquals(
      "Video call • 01:25",
      repository.formatCallLogSummary(isVideo = true, status = "COMPLETED", durationSeconds = 85)
    )
    assertEquals(
      "Voice call • 00:42",
      repository.formatCallLogSummary(isVideo = false, status = "COMPLETED", durationSeconds = 42)
    )
  }

  @Test
  fun testDeleteForEveryonePermanentlyRemovesMessage() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val peer = User(
      id = "peer_del_everyone_1",
      username = "peer_del",
      displayName = "Peer Delete"
    )
    val conv = repository.getOrCreateConversation(peer)
    repository.sendDirectMessage(conv.id, "Secret message to delete for everyone")

    val afterSend = repository.conversations.value.first { it.id == conv.id }
    val sentMsg = afterSend.messages.last { it.text == "Secret message to delete for everyone" }

    repository.deleteMessageForEveryone(conv.id, sentMsg.id)

    val afterDelete = repository.conversations.value.first { it.id == conv.id }
    assertFalse(
      "Message deleted for everyone must completely disappear from conversation",
      afterDelete.messages.any { it.id == sentMsg.id }
    )
  }

  @Test
  fun testStorySequenceAuthorIsolationAndPrivacy() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val me = repository.currentUser.value!!
    val peerA = User(id = "story_peer_a", username = "peer_a", displayName = "Peer A")
    val peerB = User(id = "story_peer_b", username = "peer_b", displayName = "Peer B")

    val viewModel = com.example.ui.VybeViewModel()
    val futureIso = java.time.Instant.now().plusSeconds(3600).toString()
    val expiredIso = java.time.Instant.now().minusSeconds(3600).toString()

    val myStory = com.example.data.model.Story(
      id = "story_me_1",
      author = me,
      mediaUrl = "https://example.com/me1.jpg",
      audience = com.example.data.model.StoryAudience.EVERYONE,
      expiresAtIso = futureIso,
      expiresHoursRemaining = 24
    )
    val peerAPublicStory = com.example.data.model.Story(
      id = "story_peer_a_pub",
      author = peerA,
      mediaUrl = "https://example.com/a1.jpg",
      audience = com.example.data.model.StoryAudience.EVERYONE,
      expiresAtIso = futureIso,
      expiresHoursRemaining = 24
    )
    val peerAOnlyMeStory = com.example.data.model.Story(
      id = "story_peer_a_priv",
      author = peerA,
      mediaUrl = "https://example.com/a2.jpg",
      audience = com.example.data.model.StoryAudience.ONLY_ME,
      expiresAtIso = futureIso,
      expiresHoursRemaining = 24
    )
    val peerAFollowersStory = com.example.data.model.Story(
      id = "story_peer_a_fol",
      author = peerA,
      mediaUrl = "https://example.com/a3.jpg",
      audience = com.example.data.model.StoryAudience.FOLLOWERS,
      expiresAtIso = futureIso,
      expiresHoursRemaining = 24
    )
    val peerAExpiredStory = com.example.data.model.Story(
      id = "story_peer_a_exp",
      author = peerA,
      mediaUrl = "https://example.com/a4.jpg",
      audience = com.example.data.model.StoryAudience.EVERYONE,
      expiresAtIso = expiredIso,
      expiresHoursRemaining = 0
    )
    val peerBPublicStory = com.example.data.model.Story(
      id = "story_peer_b_pub",
      author = peerB,
      mediaUrl = "https://example.com/b1.jpg",
      audience = com.example.data.model.StoryAudience.EVERYONE,
      expiresAtIso = futureIso,
      expiresHoursRemaining = 24
    )

    assertTrue("My own story must be visible to me", viewModel.isStoryVisibleToViewer(myStory))
    assertTrue("Peer public story must be visible", viewModel.isStoryVisibleToViewer(peerAPublicStory))
    assertFalse("Peer ONLY_ME story must NOT be visible to other users", viewModel.isStoryVisibleToViewer(peerAOnlyMeStory))
    assertFalse("Peer FOLLOWERS story must NOT be visible to non-followers", viewModel.isStoryVisibleToViewer(peerAFollowersStory))
    assertFalse("Expired story must NOT be visible", viewModel.isStoryVisibleToViewer(peerAExpiredStory))
    assertTrue("Peer B public story must be visible", viewModel.isStoryVisibleToViewer(peerBPublicStory))
  }

  @Test
  fun testFeedMusicPersistenceAndPhysicalVolumeUnmute() {
    val viewModel = com.example.ui.VybeViewModel()
    viewModel.setFeedAudioEnabled(false)
    viewModel.setReelsAudioEnabled(false)
    assertFalse(viewModel.isFeedAudioEnabled.value)
    assertFalse(viewModel.isReelsAudioEnabled.value)

    viewModel.selectTab(com.example.ui.MainTab.FEED)
    viewModel.onPhysicalVolumeUpInPlayback()
    assertTrue("Physical volume up must unmute Feed audio", viewModel.isFeedAudioEnabled.value)

    viewModel.openReelViewer()
    viewModel.onPhysicalVolumeUpInPlayback()
    assertTrue("Physical volume up must unmute Reels audio", viewModel.isReelsAudioEnabled.value)

    viewModel.setFeedAudioEnabled(false)
    assertFalse("Feed audio must turn off when user switches it off", viewModel.isFeedAudioEnabled.value)
  }

  @Test
  fun testNotificationTargetRoutingToPostOrReel() {
    repository.loginUser("eligible@vybe.in", "Password123!")
    val author = repository.currentUser.value!!
    val feedPost = com.example.data.model.Post(
      id = "notif_feed_post_1",
      author = author,
      caption = "Feed target post",
      mediaType = com.example.data.model.MediaType.IMAGE,
      timestamp = "Just now",
      clientUploadId = "upload_notif_feed_1"
    )
    val reelPost = com.example.data.model.Post(
      id = "notif_reel_post_1",
      author = author,
      caption = "Reel target post",
      mediaType = com.example.data.model.MediaType.REEL,
      isVybesReel = true,
      timestamp = "Just now",
      clientUploadId = "upload_notif_reel_1"
    )
    repository.recordVerifiedPostForState(feedPost)
    repository.recordVerifiedPostForState(reelPost)

    val viewModel = com.example.ui.VybeViewModel()
    viewModel.openPostFromNotification(feedPost.id)
    assertEquals(com.example.ui.Screen.MAIN, viewModel.currentScreen.value)
    assertEquals(com.example.ui.MainTab.FEED, viewModel.selectedTab.value)
    assertEquals(feedPost.id, viewModel.targetFeedPostId.value)

    viewModel.openPostFromNotification(reelPost.id)
    assertEquals(com.example.ui.Screen.REELS_VIEWER, viewModel.currentScreen.value)
    assertEquals(reelPost.id, viewModel.targetReelId.value)
  }

  @Test
  fun testNoSampleCommunitiesSeededByDefault() {
    assertTrue(
      "Community Zone must not contain hardcoded mock communities",
      repository.communities.value.isEmpty()
    )
  }
}

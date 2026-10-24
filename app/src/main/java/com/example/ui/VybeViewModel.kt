package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.MessagingEligibility
import com.example.data.repository.PasswordRecoveryState
import com.example.data.repository.UploadStatus
import com.example.data.repository.VybeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MainTab {
  FEED, EXPLORE, CREATE, INBOX, PROFILE, VYBES, MOOD_ZONES, CROSSED_PATHS
}

enum class Screen {
  SPLASH,
  AUTH,
  PASSWORD_RECOVERY,
  MAIN,
  CHAT_DETAIL,
  CALL_SCREEN,
  CREATE_POST,
  CREATE_CAPSULE,
  CREATE_CROSSED_PATH,
  SHOP_DETAIL,
  USER_PROFILE,
  CREATOR_STUDIO,
  ADMIN_CONSOLE,
  SETTINGS,
  REELS_VIEWER,
  TIME_CAPSULES_VIEW,
  DOUBLE_TAKE_VIEW,
  UNLOAD_VIEW,
  UNFILTERED_VIEW,
  WHAT_NEXT_VIEW,
  SPIRITUAL_MODE,
  STORY_VIEWER,
  NOTIFICATIONS,
  LIVE_STUDIO
}

class VybeViewModel : ViewModel() {
  private val repository = VybeRepository.getInstance()

  private val _currentScreen = MutableStateFlow(Screen.SPLASH)
  val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

  private val _currentStoryIndex = MutableStateFlow(0)
  val currentStoryIndex: StateFlow<Int> = _currentStoryIndex.asStateFlow()

  private val screenBackStack = mutableListOf<Screen>()
  private var isHandlingRecoveryDeepLink = false

  private val _selectedTab = MutableStateFlow(MainTab.FEED)
  val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

  private val _selectedConversation = MutableStateFlow<Conversation?>(null)
  val selectedConversation: StateFlow<Conversation?> = _selectedConversation.asStateFlow()

  private val _selectedShop = MutableStateFlow<VybeShop?>(null)
  val selectedShop: StateFlow<VybeShop?> = _selectedShop.asStateFlow()

  private val _selectedUserProfile = MutableStateFlow<User?>(null)
  val selectedUserProfile: StateFlow<User?> = _selectedUserProfile.asStateFlow()

  private val _uiBannerMessage = MutableStateFlow<String?>(null)
  val uiBannerMessage: StateFlow<String?> = _uiBannerMessage.asStateFlow()

  private val _feedRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val feedRefreshTrigger = _feedRefreshTrigger

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
  private var remoteSearchJob: Job? = null

  // Exposed Repository Flows
  val currentUser = repository.currentUser
  val isLoggedIn = repository.isLoggedIn
  val isOnline = repository.isOnline
  val isSyncingFeed = repository.isSyncingFeed
  val uploadStatus: StateFlow<UploadStatus> = repository.uploadStatus
  val passwordRecoveryState: StateFlow<PasswordRecoveryState> = repository.passwordRecoveryState
  val themeMode = repository.themeMode
  val isDarkMode = MutableStateFlow(true)

  val posts = repository.posts
  val unfilteredPosts = repository.unfilteredPosts
  val stories = repository.stories
  val unloadRooms = repository.unloadRooms
  val activeUnloadRoom = repository.activeUnloadRoom
  val crossedPaths = repository.crossedPaths
  val timeCapsules = repository.timeCapsules
  val doubleTakePrompts = repository.doubleTakePrompts
  val predictions = repository.predictions
  val shops = repository.shops
  val conversations = repository.conversations
  val activeCall = repository.activeCall
  val creatorStats = repository.creatorStats
  val adminReports = repository.adminReports
  val notifications = repository.notifications
  val selectedMoodFilter = repository.selectedMoodFilter
  val supportTickets = repository.supportTickets
  val timeManagementStats = repository.timeManagementStats
  val notificationPreferences = repository.notificationPreferences
  val userActivity = repository.userActivity
  val activeDeviceSessions = repository.activeDeviceSessions
  val isSpiritualMode = repository.isSpiritualMode
  val spiritualReflections = repository.spiritualReflections
  val spiritualGratitudes = repository.spiritualGratitudes
  val spiritualIntentions = repository.spiritualIntentions
  val businessApplications = repository.businessApplications
  val allUsers = repository.allUsers
  val vybeMatches = repository.vybeMatches
  val followingUserIds = repository.followingUserIds
  val requestedFollowUserIds = repository.requestedFollowUserIds
  val followerUserIds = repository.followerUserIds
  val incomingFollowRequestUserIds = repository.incomingFollowRequestUserIds
  val blockedUserIds = repository.blockedUserIds
  val restrictedUserIds = repository.restrictedUserIds

  val isSupabaseConfigured: Boolean
    get() = repository.isSupabaseConfigured

  init {
    viewModelScope.launch {
      repository.themeMode.collect { mode ->
        isDarkMode.value = (mode != AppThemeMode.LIGHT)
      }
    }

    viewModelScope.launch {
      repository.conversations.collect { convs ->
        val current = _selectedConversation.value
        if (current != null) {
          val updated = convs.firstOrNull {
            it.id == current.id || (!it.isGroup && !current.isGroup && it.peerUser.id == current.peerUser.id)
          }
          if (updated != null) {
            _selectedConversation.value = updated
          }
        }
      }
    }

    viewModelScope.launch {
      repository.allUsers.collect { list ->
        val selected = _selectedUserProfile.value
        if (selected != null) {
          list.firstOrNull { it.id == selected.id }?.let { fresh ->
            _selectedUserProfile.value = fresh
          }
        }
      }
    }

    viewModelScope.launch {
      repository.currentUser.collect { user ->
        val profile = _selectedUserProfile.value
        if (user != null && profile != null && profile.id == user.id) {
          _selectedUserProfile.value = user
        }
      }
    }

    viewModelScope.launch {
      delay(1100)
      // CRITICAL: Never override PASSWORD_RECOVERY if app was launched via a reset-password deep link!
      if (isHandlingRecoveryDeepLink || _currentScreen.value == Screen.PASSWORD_RECOVERY) {
        return@launch
      }
      if (repository.isLoggedIn.value) {
        val verifiedRole = repository.refreshCurrentUserRole()
        if (!isHandlingRecoveryDeepLink && _currentScreen.value != Screen.PASSWORD_RECOVERY) {
          _currentScreen.value = if (verifiedRole == UserRole.ADMIN) Screen.ADMIN_CONSOLE else Screen.MAIN
        }
      } else {
        _currentScreen.value = Screen.AUTH
      }
    }
  }

  /**
   * Handles incoming deep links (`vybe://reset-password`, `vybe://auth-callback`, HTTPS callbacks).
   * Guarantees password recovery links ALWAYS open `Screen.PASSWORD_RECOVERY`, never Home or Profile.
   */
  fun handleIncomingAuthUri(uri: Uri?) {
    if (uri == null) return
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.path?.lowercase().orEmpty()
    val fragmentParams = parseUrlFragmentParams(uri.fragment)

    val typeParam = uri.getQueryParameter("type") ?: fragmentParams["type"]
    val accessToken = uri.getQueryParameter("access_token") ?: fragmentParams["access_token"]
    val tokenHash = uri.getQueryParameter("token_hash")
      ?: fragmentParams["token_hash"]
      ?: uri.getQueryParameter("token")
      ?: fragmentParams["token"]
    val errorDesc = uri.getQueryParameter("error_description")
      ?: fragmentParams["error_description"]
      ?: uri.getQueryParameter("error")
      ?: fragmentParams["error"]
    val emailHint = uri.getQueryParameter("email") ?: fragmentParams["email"]

    val isRecoveryLink = host == "reset-password" ||
      path.contains("reset-password") ||
      typeParam.equals("recovery", ignoreCase = true)

    if (isRecoveryLink) {
      isHandlingRecoveryDeepLink = true
      screenBackStack.clear()
      _currentScreen.value = Screen.PASSWORD_RECOVERY
      viewModelScope.launch {
        repository.handlePasswordRecoveryDeepLink(
          accessTokenFromLink = accessToken,
          tokenHashFromLink = tokenHash,
          errorDescription = errorDesc,
          emailHint = emailHint
        )
      }
      return
    }

    // Standard email confirmation callback (`vybe://auth-callback`)
    if (host == "auth-callback" || path.contains("/auth/callback")) {
      if (!errorDesc.isNullOrBlank()) {
        _uiBannerMessage.value = errorDesc.replace("+", " ")
        _currentScreen.value = Screen.AUTH
      } else {
        _uiBannerMessage.value = "Email confirmed! You can now sign in."
        if (!repository.isLoggedIn.value) {
          _currentScreen.value = Screen.AUTH
        }
      }
    }
  }

  private fun parseUrlFragmentParams(fragment: String?): Map<String, String> {
    if (fragment.isNullOrBlank()) return emptyMap()
    return fragment.split("&").mapNotNull { part ->
      val idx = part.indexOf('=')
      if (idx > 0) {
        val k = Uri.decode(part.substring(0, idx))
        val v = Uri.decode(part.substring(idx + 1))
        k to v
      } else null
    }.toMap()
  }

  fun openManualPasswordRecovery(emailHint: String = "") {
    isHandlingRecoveryDeepLink = true
    repository.openManualPasswordRecovery(emailHint)
    navigateTo(Screen.PASSWORD_RECOVERY)
  }

  fun exitPasswordRecoveryToLogin() {
    isHandlingRecoveryDeepLink = false
    repository.resetPasswordRecoveryState()
    screenBackStack.clear()
    _currentScreen.value = Screen.AUTH
  }

  suspend fun verifyRecoveryTokenManually(email: String, token: String): Pair<Boolean, String> =
    repository.verifyRecoveryTokenManually(email, token)

  suspend fun submitRecoveredNewPassword(newPass: String, confirmPass: String): Pair<Boolean, String> {
    val res = repository.submitRecoveredNewPassword(newPass, confirmPass)
    if (res.first) {
      isHandlingRecoveryDeepLink = false
      _uiBannerMessage.value = res.second
      screenBackStack.clear()
      _currentScreen.value = Screen.AUTH
    }
    return res
  }

  fun consumeBannerMessage() {
    _uiBannerMessage.value = null
  }

  fun postBannerMessage(msg: String) {
    _uiBannerMessage.value = msg
  }

  // --- Cloud Refresh ---
  fun refreshAllData() {
    viewModelScope.launch {
      repository.syncAllFromSupabase()
    }
  }

  fun triggerFeedRefresh() {
    _feedRefreshTrigger.tryEmit(Unit)
    refreshAllData()
  }

  // --- Upload Reliability Controls ---
  fun retryLastUpload() = repository.retryLastUpload()
  fun clearUploadStatus() = repository.clearUploadStatus()

  // --- Navigation ---
  fun navigateTo(screen: Screen) {
    if (_currentScreen.value != screen) {
      screenBackStack.add(_currentScreen.value)
      _currentScreen.value = screen
    }
  }

  fun navigateBack(): Boolean {
    if (_currentScreen.value == Screen.PASSWORD_RECOVERY) {
      exitPasswordRecoveryToLogin()
      return true
    }
    if (screenBackStack.isNotEmpty()) {
      _currentScreen.value = screenBackStack.removeAt(screenBackStack.size - 1)
      return true
    } else if (_currentScreen.value != Screen.MAIN && _currentScreen.value != Screen.AUTH) {
      _currentScreen.value = if (repository.isLoggedIn.value) Screen.MAIN else Screen.AUTH
      return true
    }
    return false
  }

  fun selectTab(tab: MainTab) {
    if (_selectedTab.value == tab && tab == MainTab.FEED && _currentScreen.value == Screen.MAIN) {
      triggerFeedRefresh()
    }
    if (tab == MainTab.INBOX || tab == MainTab.EXPLORE || tab == MainTab.PROFILE) {
      refreshAllData()
    }
    _selectedTab.value = tab
    if (_currentScreen.value != Screen.MAIN) {
      _currentScreen.value = Screen.MAIN
    }
  }

  fun openConversation(conversation: Conversation) {
    _selectedConversation.value = conversation
    navigateTo(Screen.CHAT_DETAIL)
  }

  fun openShopDetail(shop: VybeShop) {
    _selectedShop.value = shop
    navigateTo(Screen.SHOP_DETAIL)
  }

  fun openUserProfile(user: User) {
    val me = repository.currentUser.value
    if (me != null && me.id == user.id) {
      // Opening own profile routes cleanly to own profile tab / latest state
      _selectedUserProfile.value = me
    } else {
      val latest = repository.allUsers.value.firstOrNull { it.id == user.id } ?: user
      _selectedUserProfile.value = latest
    }
    navigateTo(Screen.USER_PROFILE)
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
    remoteSearchJob?.cancel()
    if (query.trim().length >= 2) {
      remoteSearchJob = viewModelScope.launch {
        delay(280)
        repository.searchUsersRemote(query)
      }
    }
  }

  fun toggleDarkMode() {
    isDarkMode.value = !isDarkMode.value
    repository.setThemeMode(if (isDarkMode.value) AppThemeMode.DARK else AppThemeMode.LIGHT)
  }

  fun setThemeMode(mode: AppThemeMode) {
    repository.setThemeMode(mode)
    isDarkMode.value = (mode != AppThemeMode.LIGHT)
  }

  // --- Privacy & Social Graph ---
  fun canViewUserContent(user: User): Boolean = repository.canViewUserContent(user)
  fun checkMessagingEligibility(peerUser: User): MessagingEligibility = repository.checkMessagingEligibility(peerUser)
  fun setAccountPrivate(isPrivate: Boolean) = repository.setAccountPrivate(isPrivate)
  fun setMessagePrivacy(privacy: String) = repository.setMessagePrivacy(privacy)
  fun toggleFollowUser(targetUserId: String): Boolean = repository.toggleFollowUser(targetUserId)
  fun respondToFollowRequest(requesterUserId: String, approve: Boolean) = repository.respondToFollowRequest(requesterUserId, approve)

  // --- VYBE Matches ---
  fun addVybeMatch(user: User) = repository.addVybeMatch(user)
  fun removeVybeMatch(userId: String) = repository.removeVybeMatch(userId)
  fun isVybeMatch(userId: String): Boolean = repository.isVybeMatch(userId)

  // --- Posts, Comments & Reactions ---
  fun toggleLike(postId: String) = repository.toggleLikePost(postId)
  fun toggleSave(postId: String) = repository.toggleSavePost(postId)
  fun archivePost(postId: String) = repository.archivePost(postId)
  fun restoreArchivedPost(postId: String) = repository.restoreArchivedPost(postId)
  fun reactToPost(postId: String, reaction: VybeReaction) = repository.reactToPost(postId, reaction)
  fun loadCommentsForPost(postId: String) = repository.loadCommentsForPost(postId)
  fun addComment(postId: String, text: String) = repository.addComment(postId, text)

  fun createPost(
    caption: String,
    mood: MoodType?,
    mediaType: MediaType = MediaType.TEXT,
    imageUrl: String? = null,
    isVybes: Boolean = false,
    locationName: String? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    audioTrackTitle: String? = null,
    preferredAspectRatio: Float? = null
  ) {
    repository.createPost(
      caption = caption,
      mood = mood,
      mediaType = mediaType,
      imageUrl = imageUrl,
      isVybes = isVybes,
      locationName = locationName,
      latitude = latitude,
      longitude = longitude,
      audioTrackTitle = audioTrackTitle,
      preferredAspectRatio = preferredAspectRatio
    )
    navigateBack()
  }

  fun deletePost(postId: String) = repository.deletePost(postId)
  fun recordPostView(postId: String) = repository.recordPostView(postId)

  fun startConversationWithUser(user: User, onDenied: ((String) -> Unit)? = null) {
    val eligibility = repository.checkMessagingEligibility(user)
    if (eligibility is MessagingEligibility.Denied) {
      _uiBannerMessage.value = eligibility.reason
      onDenied?.invoke(eligibility.reason)
      return
    }
    viewModelScope.launch {
      val res = repository.openOrCreateRemoteConversation(user)
      if (res.isSuccess) {
        openConversation(res.getOrThrow())
      } else {
        val msg = res.exceptionOrNull()?.message ?: "Unable to start conversation."
        _uiBannerMessage.value = msg
        onDenied?.invoke(msg)
      }
    }
  }

  fun toggleConversationUnread(conversationId: String) = repository.toggleConversationUnread(conversationId)
  fun selectMoodFilter(mood: MoodType?) = repository.selectMoodFilter(mood)
  fun updateUserMoodAndPulse(mood: MoodType?, pulse: LivePulseStatus) = repository.updateUserMoodAndPulse(mood, pulse)

  // --- Notifications ---
  fun markNotificationAsRead(id: String) = repository.markNotificationAsRead(id)
  fun markAllNotificationsAsRead() = repository.markAllNotificationsAsRead()
  fun markAllNotificationsRead() = repository.markAllNotificationsRead()
  fun addNotification(item: NotificationItem) = repository.addNotification(item)
  fun deleteNotification(notificationId: String) = repository.deleteNotification(notificationId)

  // --- Stories ---
  fun openStory(story: Story) {
    val list = repository.stories.value
    val idx = list.indexOfFirst { it.id == story.id }
    _currentStoryIndex.value = if (idx >= 0) idx else 0
    repository.markStoryViewed(story.id)
    navigateTo(Screen.STORY_VIEWER)
  }

  fun openStoryAtIndex(index: Int) {
    val list = repository.stories.value
    if (index in list.indices) {
      _currentStoryIndex.value = index
      repository.markStoryViewed(list[index].id)
      navigateTo(Screen.STORY_VIEWER)
    }
  }

  fun nextStory(): Boolean {
    val list = repository.stories.value
    if (_currentStoryIndex.value < list.size - 1) {
      _currentStoryIndex.value += 1
      repository.markStoryViewed(list[_currentStoryIndex.value].id)
      return true
    }
    return false
  }

  fun prevStory(): Boolean {
    if (_currentStoryIndex.value > 0) {
      _currentStoryIndex.value -= 1
      return true
    }
    return false
  }

  fun createStory(
    mediaUrl: String,
    caption: String,
    mediaType: StoryMediaType = StoryMediaType.PHOTO,
    audience: StoryAudience = StoryAudience.EVERYONE,
    musicTrackTitle: String? = null,
    location: String? = null,
    textBackgroundColor: String? = null
  ) = repository.createStory(
    mediaUrl = mediaUrl,
    caption = caption,
    mediaType = mediaType,
    audience = audience,
    musicTrackTitle = musicTrackTitle,
    location = location,
    textBackgroundColor = textBackgroundColor
  )

  fun markStoryViewed(storyId: String) = repository.markStoryViewed(storyId)
  fun toggleStoryLike(storyId: String) = repository.toggleStoryLike(storyId)
  fun deleteStory(storyId: String) = repository.deleteStory(storyId)

  // --- Messaging & Calls ---
  fun sendDirectMessage(conversationId: String, text: String) = repository.sendDirectMessage(conversationId, text)

  fun sendExtendedDirectMessage(
    convId: String,
    text: String,
    mediaUrl: String? = null,
    mediaType: String = "TEXT",
    fileName: String? = null,
    locationCoordinates: String? = null,
    replyToId: String? = null,
    replyToText: String? = null,
    replyToSenderName: String? = null
  ) = repository.sendExtendedDirectMessage(convId, text, mediaUrl, mediaType, fileName, locationCoordinates, replyToId, replyToText, replyToSenderName)

  fun editDirectMessage(convId: String, msgId: String, text: String) = repository.editDirectMessage(convId, msgId, text)
  fun deleteMessageForMe(convId: String, msgId: String) = repository.deleteMessageForMe(convId, msgId)
  fun deleteMessageForEveryone(convId: String, msgId: String) = repository.deleteMessageForEveryone(convId, msgId)
  fun addMessageReaction(convId: String, msgId: String, emoji: String) = repository.addMessageReaction(convId, msgId, emoji)
  fun setHiddenChatPin(pin: String) = repository.setHiddenChatPin(pin)
  fun verifyHiddenChatPin(pin: String): Boolean = repository.verifyHiddenChatPin(pin)
  fun hasHiddenChatPinSet(): Boolean = repository.hasHiddenChatPinSet()
  fun toggleHideConversation(convId: String) = repository.toggleHideConversation(convId)
  fun markConversationAsRead(convId: String) = repository.markConversationAsRead(convId)
  fun deleteConversation(convId: String) = repository.deleteConversation(convId)
  fun addGroupMember(convId: String, user: User) = repository.addGroupMember(convId, user)
  fun removeGroupMember(convId: String, userId: String) = repository.removeGroupMember(convId, userId)
  fun sendVoiceMessage(convId: String, duration: String = "0:08") = repository.sendVoiceMessage(convId, duration)
  fun createGroupConversation(name: String, members: List<User>) = repository.createGroupConversation(name, members)
  fun updateChatNickname(convId: String, nickname: String) = repository.updateChatNickname(convId, nickname)
  fun updateChatTheme(convId: String, theme: String) = repository.updateChatTheme(convId, theme)
  fun retrySendMessage(convId: String, messageIdOrClientId: String) = repository.retrySendMessage(convId, messageIdOrClientId)
  fun toggleMuteChat(convId: String) = repository.toggleMuteChat(convId)
  fun toggleBlockUser(userId: String) = repository.toggleBlockUser(userId)
  fun unblockUser(userId: String) = repository.unblockUser(userId)
  fun toggleRestrictUser(userId: String) = repository.toggleRestrictUser(userId)
  fun searchUserByVybeId(query: String): User? = repository.searchUserByVybeId(query)
  fun searchUsers(query: String): List<User> = repository.searchUsers(query)
  fun getOrCreateConversation(peerUser: User): Conversation = repository.getOrCreateConversation(peerUser)
  fun syncMessagesForConversation(convId: String) = repository.syncMessagesForConversation(convId)
  suspend fun submitReport(targetType: String, targetId: String, reason: String, details: String = "") =
    repository.submitReport(targetType, targetId, reason, details)

  fun initiateCall(peer: User, isVideo: Boolean): CallAvailabilityResult {
    val result = repository.initiateCall(peer, isVideo)
    if (result is CallAvailabilityResult.Available) {
      navigateTo(Screen.CALL_SCREEN)
    }
    return result
  }
  fun startVoiceCall(peer: User) { initiateCall(peer, isVideo = false) }
  fun startVideoCall(peer: User) { initiateCall(peer, isVideo = true) }
  fun acceptCall() = repository.acceptCall()
  fun endCall() { repository.endCall(); navigateBack() }
  fun toggleCallMute() = repository.toggleCallMute()
  fun toggleCallCamera() = repository.toggleCallCamera()

  // --- Profile, Auth & Preserved Spaces ---
  fun updateProfilePhoto(avatarUrl: String) = repository.updateProfilePhoto(avatarUrl)
  fun updateProfileBanner(coverUrl: String) = repository.updateProfileBanner(coverUrl)
  fun checkUserIdAvailability(requestedId: String) = repository.checkUserIdAvailability(requestedId)

  fun updateAccountProfile(
    firstName: String,
    lastName: String,
    requestedUserId: String,
    email: String,
    phone: String,
    bio: String,
    avatarUrl: String? = null,
    profileVideoUrl: String? = null,
    coverUrl: String? = null
  ) = repository.updateAccountProfile(
    firstName = firstName,
    lastName = lastName,
    username = requestedUserId,
    email = email,
    phone = phone,
    bio = bio,
    profileVideoUrl = profileVideoUrl,
    avatarUrl = avatarUrl,
    coverUrl = coverUrl
  )

  fun refreshCurrentUserRole(onComplete: ((UserRole) -> Unit)? = null) {
    viewModelScope.launch {
      val verifiedRole = repository.refreshCurrentUserRole()
      onComplete?.invoke(verifiedRole)
    }
  }

  suspend fun verifyOtp(email: String, token: String, type: String = "signup"): Pair<Boolean, String> {
    val result = repository.verifyOtp(email, token, type)
    if (result.first) {
      val verifiedRole = repository.currentUser.value?.role ?: UserRole.USER
      _currentScreen.value = if (verifiedRole == UserRole.ADMIN) Screen.ADMIN_CONSOLE else Screen.MAIN
    }
    return result
  }

  suspend fun sendPasswordReset(email: String): Pair<Boolean, String> = repository.sendPasswordReset(email)
  suspend fun resetPasswordWithOtp(email: String, otp: String, newPass: String): Pair<Boolean, String> =
    repository.resetPasswordWithOtp(email, otp, newPass)

  suspend fun loginWithSupabase(identifier: String, pass: String): Pair<Boolean, String> {
    val result = repository.loginWithSupabase(identifier, pass)
    if (result.first) {
      val verifiedRole = repository.currentUser.value?.role ?: UserRole.USER
      _currentScreen.value = if (verifiedRole == UserRole.ADMIN) Screen.ADMIN_CONSOLE else Screen.MAIN
    }
    return result
  }

  suspend fun registerWithSupabase(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    phone: String,
    dob: String,
    termsAccepted: Boolean
  ): Pair<Boolean, String> {
    val result = repository.registerWithSupabase(username, email, password, firstName, lastName, phone, dob, termsAccepted)
    if (result.first && !result.second.startsWith("NEEDS_OTP")) {
      val verifiedRole = repository.currentUser.value?.role ?: UserRole.USER
      _currentScreen.value = if (verifiedRole == UserRole.ADMIN) Screen.ADMIN_CONSOLE else Screen.MAIN
    }
    return result
  }

  fun register(username: String, email: String, dob: String, firstName: String = "", lastName: String = "", phone: String = ""): Pair<Boolean, String> {
    val result = repository.registerAccount(firstName, lastName, username, email, phone, dob)
    if (result.first) {
      _currentScreen.value = Screen.MAIN
    }
    return result
  }

  fun login(identifier: String, pass: String): Boolean {
    val success = repository.loginUser(identifier, pass)
    if (success) _currentScreen.value = Screen.MAIN
    return success
  }

  fun logout() {
    repository.logout()
    screenBackStack.clear()
    _currentScreen.value = Screen.AUTH
  }

  fun deactivateAccount() {
    repository.deactivateAccount()
    screenBackStack.clear()
    _currentScreen.value = Screen.AUTH
  }

  // --- Remaining Preserved Delegations ---
  fun setSpiritualMode(enabled: Boolean) = repository.setSpiritualMode(enabled)
  fun toggleSpiritualMode() = repository.toggleSpiritualMode()
  fun addGratitudeEntry(entry: String) = repository.addGratitudeEntry(entry)
  fun addIntention(text: String) = repository.addIntention(text)
  fun toggleIntentionFulfilled(id: String) = repository.toggleIntentionFulfilled(id)
  fun joinUnloadRoom(room: UnloadRoom) { repository.joinUnloadRoom(room); navigateTo(Screen.UNLOAD_VIEW) }
  fun leaveUnloadRoom() { repository.leaveUnloadRoom(); navigateBack() }
  fun sendUnloadMessage(roomId: String, text: String, isAnonymous: Boolean) = repository.sendUnloadMessage(roomId, text, isAnonymous)
  fun createUnloadRoom(title: String, description: String, topicTag: String, mode: UnloadRoomMode, durationMinutes: Int, capacity: Int, scheduledTime: String, joinerMessage: String, termsAgreed: Boolean) =
    repository.createUnloadRoom(title, description, topicTag, mode, durationMinutes, capacity, scheduledTime, joinerMessage, termsAgreed)
  fun updateUnloadRoom(roomId: String, title: String, description: String, topicTag: String, mode: UnloadRoomMode, durationMinutes: Int, capacity: Int) =
    repository.updateUnloadRoom(roomId, title, description, topicTag, mode, durationMinutes, capacity)
  fun deleteUnloadRoom(roomId: String) = repository.deleteUnloadRoom(roomId)
  fun createUnfilteredPost(caption: String, imageUrl: String) = repository.createUnfilteredPost(caption, imageUrl)
  fun addUnfilteredComment(postId: String, text: String) = repository.addUnfilteredComment(postId, text)
  fun submitDoubleTakeAnswer(promptId: String, answer: String) = repository.submitDoubleTakeAnswer(promptId, answer)
  fun createDoubleTakePrompt(question: String, category: String, optA: String, optB: String, peer: User, isPublic: Boolean = false) =
    repository.createDoubleTakePrompt(question, category, optA, optB, peer, isPublic)
  fun submitPublicDoubleTakeAnswer(promptId: String, answer: String) = repository.submitPublicDoubleTakeAnswer(promptId, answer)
  fun connectWithPublicDoubleTakeAnswer(promptId: String, answerId: String) = repository.connectWithPublicDoubleTakeAnswer(promptId, answerId)
  fun deleteDoubleTakePrompt(promptId: String) = repository.deleteDoubleTakePrompt(promptId)
  fun acceptDoubleTakeMatch(promptId: String) = repository.acceptDoubleTakeMatch(promptId)
  fun votePrediction(predictionId: String, optionId: Int) = repository.votePrediction(predictionId, optionId)
  fun createPredictionPoll(title: String, category: String, durationHours: Int, options: List<String>) =
    repository.createPredictionPoll(title, category, durationHours, options)
  fun deletePredictionPoll(pollId: String) = repository.deletePredictionPoll(pollId)
  fun createTimeCapsule(title: String, message: String, recipient: String, unlockDate: String) {
    repository.createTimeCapsule(title, message, recipient, unlockDate)
    navigateBack()
  }
  fun unlockTimeCapsule(capsuleId: String) = repository.unlockTimeCapsule(capsuleId)
  fun respondToCapsuleInvitation(capsuleId: String, accept: Boolean) = repository.respondToCapsuleInvitation(capsuleId, accept)
  fun addShopReview(shopId: String, rating: Int, text: String) = repository.addShopReview(shopId, rating, text)
  fun submitBusinessApplication(name: String, cat: String, gst: String, email: String, phone: String, addr: String) =
    repository.submitBusinessApplication(name, cat, gst, email, phone, addr)
  fun reviewBusinessApplication(appId: String, status: BusinessVerificationStatus, notes: String) =
    repository.reviewBusinessApplication(appId, status, notes)
  fun activateBusinessShop(shopId: String) = repository.activateBusinessShop(shopId)
  fun submitAdminAction(reportId: String, action: ReportStatus) = repository.submitAdminReportAction(reportId, action)
  fun sendCrossedPathRequest(encounterId: String) = repository.sendCrossedPathRequest(encounterId)
  fun createCrossedPathEncounter(
    type: CrossedPathType,
    venueOrEventName: String,
    broadLocation: String,
    description: String,
    photoUrl: String? = null
  ) = repository.createCrossedPathEncounter(type, venueOrEventName, broadLocation, description, photoUrl)
  fun submitSupportTicket(category: String, subject: String, message: String) = repository.submitSupportTicket(category, subject, message)
  fun updateTimeManagement(breakMinutes: Int, isEnabled: Boolean) = repository.updateTimeManagement(breakMinutes, isEnabled)
  fun logoutDevice(sessionId: String) = repository.logoutDevice(sessionId)
  fun updateNotificationPreferences(prefs: NotificationPreferences) = repository.updateNotificationPreferences(prefs)
  fun completeOnboarding() = repository.completeOnboarding()
}

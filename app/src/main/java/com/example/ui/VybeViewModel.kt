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
  LIVE_STUDIO,
  COMMUNITY_ZONE
}

class VybeViewModel : ViewModel() {
  private val repository = VybeRepository.getInstance()

  private val _currentScreen = MutableStateFlow(Screen.SPLASH)
  val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

  private val _currentStoryIndex = MutableStateFlow(0)
  val currentStoryIndex: StateFlow<Int> = _currentStoryIndex.asStateFlow()

  private val _activeStorySequence = MutableStateFlow<List<Story>>(emptyList())
  val activeStorySequence: StateFlow<List<Story>> = _activeStorySequence.asStateFlow()

  private val _activeStoryAuthorId = MutableStateFlow<String?>(null)
  val activeStoryAuthorId: StateFlow<String?> = _activeStoryAuthorId.asStateFlow()

  private val _isFeedAudioEnabled = MutableStateFlow(false)
  val isFeedAudioEnabled: StateFlow<Boolean> = _isFeedAudioEnabled.asStateFlow()

  private val _isReelsAudioEnabled = MutableStateFlow(true)
  val isReelsAudioEnabled: StateFlow<Boolean> = _isReelsAudioEnabled.asStateFlow()

  fun setFeedAudioEnabled(enabled: Boolean) {
    _isFeedAudioEnabled.value = enabled
  }

  fun toggleFeedAudio(): Boolean {
    val next = !_isFeedAudioEnabled.value
    _isFeedAudioEnabled.value = next
    return next
  }

  fun setReelsAudioEnabled(enabled: Boolean) {
    _isReelsAudioEnabled.value = enabled
  }

  fun toggleReelsAudio(): Boolean {
    val next = !_isReelsAudioEnabled.value
    _isReelsAudioEnabled.value = next
    return next
  }

  /**
   * Invoked when the user presses physical Volume Up while viewing Feed or Reels.
   * Ensures muted playback unmutes naturally while Android adjusts STREAM_MUSIC volume.
   */
  fun onPhysicalVolumeUpInPlayback() {
    when {
      _currentScreen.value == Screen.REELS_VIEWER ||
        (_currentScreen.value == Screen.MAIN && _selectedTab.value == MainTab.VYBES) -> {
        if (!_isReelsAudioEnabled.value) {
          _isReelsAudioEnabled.value = true
        }
      }
      _currentScreen.value == Screen.MAIN && _selectedTab.value == MainTab.FEED -> {
        if (!_isFeedAudioEnabled.value) {
          _isFeedAudioEnabled.value = true
        }
      }
    }
  }

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

  private val _targetReelId = MutableStateFlow<String?>(null)
  val targetReelId: StateFlow<String?> = _targetReelId.asStateFlow()

  private val _targetFeedPostId = MutableStateFlow<String?>(null)
  val targetFeedPostId: StateFlow<String?> = _targetFeedPostId.asStateFlow()

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
  val hasMoreFeedPosts = repository.hasMoreFeedPosts
  val isLoadingMorePosts = repository.isLoadingMorePosts
  val uploadStatus: StateFlow<UploadStatus> = repository.uploadStatus
  val passwordRecoveryState: StateFlow<PasswordRecoveryState> = repository.passwordRecoveryState
  val lastRecoveredEmailHint = repository.lastRecoveredEmailHint
  val pendingVerificationEmail = repository.pendingVerificationEmail
  val pendingVerificationPhone = repository.pendingVerificationPhone
  val isEmailOtpVerified = repository.isEmailOtpVerified
  val isPhoneOtpVerified = repository.isPhoneOtpVerified
  val themeMode = repository.themeMode
  val isDarkMode = MutableStateFlow(true)

  val posts = repository.posts
  val unfilteredPosts = repository.unfilteredPosts
  private val _visibleStories = MutableStateFlow<List<Story>>(emptyList())
  val stories: StateFlow<List<Story>> = _visibleStories.asStateFlow()
  val unloadRooms = repository.unloadRooms
  val activeUnloadRoom = repository.activeUnloadRoom
  val crossedPaths = repository.crossedPaths
  val timeCapsules = repository.timeCapsules
  val doubleTakePrompts = repository.doubleTakePrompts
  val predictions = repository.predictions
  val shops = repository.shops
  val communities = repository.communities
  val activeCommunity = repository.activeCommunity
  val communityPosts = repository.communityPosts
  val communityMembers = repository.communityMembers
  val communityComments = repository.communityComments
  val liveStreams = repository.liveStreams
  val activeLiveStream = repository.activeLiveStream
  val liveStreamComments = repository.liveStreamComments
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
      var hadActiveCall = false
      repository.activeCall.collect { call ->
        if (call != null) {
          hadActiveCall = true
        } else if (hadActiveCall) {
          hadActiveCall = false
          if (_currentScreen.value == Screen.CALL_SCREEN) {
            navigateBack()
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

    // Keep visible stories & active Story sequence strictly scoped to privacy, expiration & activeStoryAuthorId
    viewModelScope.launch {
      kotlinx.coroutines.flow.combine(
        repository.stories,
        repository.currentUser,
        repository.followingUserIds,
        repository.vybeMatches,
        repository.blockedUserIds
      ) { rawStories, _, _, _, _ ->
        rawStories.filter { isStoryVisibleToViewer(it) }
      }.collect { visible ->
        _visibleStories.value = visible
        val activeAuthor = _activeStoryAuthorId.value
        if (!activeAuthor.isNullOrBlank()) {
          val updatedSeq = visible
            .filter { it.author.id == activeAuthor }
            .sortedBy { com.example.util.TimestampUtils.parseIsoMillis(it.createdAtIso) ?: 0L }
          _activeStorySequence.value = updatedSeq
          if (updatedSeq.isEmpty() && _currentScreen.value == Screen.STORY_VIEWER) {
            _activeStoryAuthorId.value = null
            _currentStoryIndex.value = 0
            navigateBack()
          } else if (_currentStoryIndex.value >= updatedSeq.size && updatedSeq.isNotEmpty()) {
            _currentStoryIndex.value = updatedSeq.lastIndex
          }
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

  // --- Cloud Refresh & Pagination ---
  fun refreshAllData() {
    viewModelScope.launch {
      repository.syncAllFromSupabase()
    }
  }

  fun triggerFeedRefresh() {
    _feedRefreshTrigger.tryEmit(Unit)
    refreshAllData()
  }

  fun loadMoreFeedPosts() {
    viewModelScope.launch {
      repository.loadMoreFeedPosts()
    }
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

  fun openReelViewer(reelId: String? = null) {
    _targetReelId.value = reelId
    if (_currentScreen.value == Screen.MAIN) {
      selectTab(MainTab.VYBES)
    } else {
      navigateTo(Screen.REELS_VIEWER)
    }
  }

  fun clearTargetReelId() {
    _targetReelId.value = null
  }

  fun openPostFromNotification(postId: String) {
    if (postId.isBlank()) return
    val targetPost = repository.posts.value.firstOrNull { it.id == postId }
    if (targetPost?.isReel == true) {
      openReelViewer(postId)
      return
    }
    _targetFeedPostId.value = postId
    _selectedTab.value = MainTab.FEED
    screenBackStack.clear()
    _currentScreen.value = Screen.MAIN
  }

  fun clearTargetFeedPostId() {
    _targetFeedPostId.value = null
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

  suspend fun publishPostAwait(
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
  ): Result<Post> {
    val result = repository.publishPostAwait(
      caption = caption,
      mood = mood,
      mediaType = mediaType,
      imageUrl = imageUrl,
      isVybes = isVybes,
      isUnfiltered = false,
      locationName = locationName,
      latitude = latitude,
      longitude = longitude,
      audioTrackTitle = audioTrackTitle,
      preferredAspectRatio = preferredAspectRatio
    )
    if (result.isSuccess) {
      navigateBack()
    }
    return result
  }

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

  // --- Stories (Strict Author-Scoped Sequences with Privacy & Expiration) ---
  fun isStoryVisibleToViewer(s: Story, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val myId = repository.currentUser.value?.id.orEmpty()
    val blocked = repository.blockedUserIds.value
    val following = repository.followingUserIds.value
    val matchIds = repository.vybeMatches.value.map { it.id }.toSet()

    if (blocked.contains(s.author.id)) return false
    val exp = com.example.util.TimestampUtils.parseIsoMillis(s.expiresAtIso)
    val notExpired = if (exp != null) exp > nowMillis else s.expiresHoursRemaining > 0
    if (!notExpired) return false

    if (myId.isNotBlank() && s.author.id == myId) return true
    if (s.author.isPrivate && !following.contains(s.author.id)) return false

    return when (s.audience) {
      StoryAudience.EVERYONE, StoryAudience.PUBLIC -> true
      StoryAudience.FOLLOWERS -> following.contains(s.author.id)
      StoryAudience.VYBE_MATCHES, StoryAudience.CLOSE_FRIENDS -> matchIds.contains(s.author.id)
      StoryAudience.ONLY_ME -> false
    }
  }

  fun getVisibleStoriesForAuthor(authorId: String): List<Story> {
    if (authorId.isBlank()) return emptyList()
    val nowMillis = System.currentTimeMillis()
    return repository.stories.value
      .filter { s -> s.author.id == authorId && isStoryVisibleToViewer(s, nowMillis) }
      .sortedBy { com.example.util.TimestampUtils.parseIsoMillis(it.createdAtIso) ?: 0L }
  }

  fun openUserStorySequence(authorId: String, initialStoryId: String? = null) {
    val authorStories = getVisibleStoriesForAuthor(authorId)
    if (authorStories.isEmpty()) return

    _activeStoryAuthorId.value = authorId
    _activeStorySequence.value = authorStories

    val targetIdx = if (!initialStoryId.isNullOrBlank()) {
      authorStories.indexOfFirst { it.id == initialStoryId }.coerceAtLeast(0)
    } else {
      val firstUnviewed = authorStories.indexOfFirst { !it.isViewed }
      if (firstUnviewed >= 0) firstUnviewed else 0
    }

    _currentStoryIndex.value = targetIdx
    repository.markStoryViewed(authorStories[targetIdx].id)
    navigateTo(Screen.STORY_VIEWER)
  }

  fun openStory(story: Story) {
    openUserStorySequence(story.author.id, story.id)
  }

  fun openStoryAtIndex(index: Int) {
    val list = repository.stories.value
    if (index in list.indices) {
      val target = list[index]
      openUserStorySequence(target.author.id, target.id)
    }
  }

  fun closeStoryViewer() {
    _activeStoryAuthorId.value = null
    _activeStorySequence.value = emptyList()
    _currentStoryIndex.value = 0
    navigateBack()
  }

  fun nextStory(): Boolean {
    val authorId = _activeStoryAuthorId.value ?: return false
    val list = getVisibleStoriesForAuthor(authorId).also { _activeStorySequence.value = it }
    if (_currentStoryIndex.value < list.size - 1) {
      _currentStoryIndex.value += 1
      repository.markStoryViewed(list[_currentStoryIndex.value].id)
      return true
    }
    return false
  }

  fun prevStory(): Boolean {
    val authorId = _activeStoryAuthorId.value ?: return false
    val list = getVisibleStoriesForAuthor(authorId).also { _activeStorySequence.value = it }
    if (list.isEmpty()) return false
    if (_currentStoryIndex.value > 0) {
      _currentStoryIndex.value -= 1
      return true
    }
    return false
  }

  suspend fun publishStoryAwait(
    mediaUrl: String,
    caption: String,
    mediaType: StoryMediaType = StoryMediaType.PHOTO,
    audience: StoryAudience = StoryAudience.EVERYONE,
    musicTrackTitle: String? = null,
    location: String? = null,
    textBackgroundColor: String? = null
  ): Result<Story> = repository.publishStoryAwait(
    mediaUrl = mediaUrl,
    caption = caption,
    mediaType = mediaType,
    audience = audience,
    musicTrackTitle = musicTrackTitle,
    location = location,
    textBackgroundColor = textBackgroundColor
  )

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
  fun openIncomingCallScreen() { navigateTo(Screen.CALL_SCREEN) }
  fun acceptCall() {
    repository.acceptCall()
    if (_currentScreen.value != Screen.CALL_SCREEN) {
      navigateTo(Screen.CALL_SCREEN)
    }
  }
  fun declineCall() {
    repository.declineCall()
    if (_currentScreen.value == Screen.CALL_SCREEN) {
      navigateBack()
    }
  }
  fun endCall() {
    repository.endCall()
    if (_currentScreen.value == Screen.CALL_SCREEN) {
      navigateBack()
    }
  }
  fun onCallAudioPermissionReady() = repository.onCallAudioPermissionReady()
  fun toggleCallMute() = repository.toggleCallMute()
  fun toggleCallSpeaker() = repository.toggleCallSpeaker()
  fun toggleCallCamera() = repository.toggleCallCamera()
  fun switchCallCamera() = repository.switchCallCamera()
  fun pushLocalVideoFrame(jpegBytes: ByteArray) = repository.pushLocalVideoFrame(jpegBytes)

  // --- Profile, Auth & Preserved Spaces ---
  fun updateProfilePhoto(avatarUrl: String) = repository.updateProfilePhoto(avatarUrl)
  fun updateProfileBanner(coverUrl: String) = repository.updateProfileBanner(coverUrl)
  fun checkUserIdAvailability(requestedId: String) = repository.checkUserIdAvailability(requestedId)
  suspend fun checkUsernameAvailabilityRemote(requestedUsername: String): Pair<Boolean, String> =
    repository.checkUsernameAvailabilityRemote(requestedUsername)

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
    if (result.first && !result.second.startsWith("EMAIL_VERIFIED_NEEDS_PHONE")) {
      val verifiedRole = repository.currentUser.value?.role ?: UserRole.USER
      _currentScreen.value = if (verifiedRole == UserRole.ADMIN) Screen.ADMIN_CONSOLE else Screen.MAIN
    }
    return result
  }

  suspend fun resendEmailVerificationCode(email: String): Pair<Boolean, String> =
    repository.resendEmailVerificationCode(email)

  suspend fun requestPhoneVerificationCode(phone: String): Pair<Boolean, String> =
    repository.requestPhoneVerificationCode(phone)

  suspend fun verifyPhoneOtpCode(phone: String, code: String): Pair<Boolean, String> {
    val result = repository.verifyPhoneOtpCode(phone, code)
    if (result.first && !result.second.startsWith("PHONE_VERIFIED_NEEDS_EMAIL") && repository.isLoggedIn.value) {
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
  suspend fun joinRoomByCode(code: String): Result<UnloadRoom> {
    val res = repository.joinRoomByCode(code)
    if (res.isSuccess) {
      navigateTo(Screen.UNLOAD_VIEW)
    }
    return res
  }
  fun leaveUnloadRoom() { repository.leaveUnloadRoom(); navigateBack() }
  fun sendUnloadMessage(roomId: String, text: String, isAnonymous: Boolean) = repository.sendUnloadMessage(roomId, text, isAnonymous)
  fun createUnloadRoom(title: String, description: String, topicTag: String, mode: UnloadRoomMode, durationMinutes: Int, capacity: Int, scheduledTime: String, joinerMessage: String, termsAgreed: Boolean) =
    repository.createUnloadRoom(title, description, topicTag, mode, durationMinutes, capacity, scheduledTime, joinerMessage, termsAgreed)
  fun updateUnloadRoom(roomId: String, title: String, description: String, topicTag: String, mode: UnloadRoomMode, durationMinutes: Int, capacity: Int) =
    repository.updateUnloadRoom(roomId, title, description, topicTag, mode, durationMinutes, capacity)
  fun deleteUnloadRoom(roomId: String) = repository.deleteUnloadRoom(roomId)

  // --- Live Zone Broadcast & Viewing ---
  fun openGoLiveStudio() {
    navigateTo(Screen.LIVE_STUDIO)
  }
  fun startLiveBroadcast(title: String, category: String) {
    repository.startLiveBroadcast(title, category)
    if (_currentScreen.value != Screen.LIVE_STUDIO) {
      navigateTo(Screen.LIVE_STUDIO)
    }
  }
  fun joinLiveBroadcast(stream: LiveStreamSession) {
    repository.joinLiveBroadcast(stream)
    navigateTo(Screen.LIVE_STUDIO)
  }
  fun leaveOrEndLiveBroadcast() {
    repository.leaveOrEndLiveBroadcast()
    if (_currentScreen.value == Screen.LIVE_STUDIO) {
      navigateBack()
    }
  }
  fun sendLiveBroadcastComment(content: String) = repository.sendLiveBroadcastComment(content)
  fun pushLiveBroadcastFrame(jpegBytes: ByteArray) = repository.pushLiveBroadcastFrame(jpegBytes)

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

  // --- Community Zone ---
  fun openCommunityZone() {
    repository.fetchCommunities()
    navigateTo(Screen.COMMUNITY_ZONE)
  }
  fun fetchCommunities() = repository.fetchCommunities()
  fun openCommunity(community: Community) = repository.openCommunity(community)
  fun closeActiveCommunity() = repository.closeActiveCommunity()
  fun createCommunity(name: String, description: String, category: String, isPrivate: Boolean) =
    repository.createCommunity(name, description, category, isPrivate)
  fun toggleJoinCommunity(communityId: String) = repository.toggleJoinCommunity(communityId)
  fun createCommunityPost(communityId: String, title: String, content: String, mediaUri: Uri? = null) =
    repository.createCommunityPost(communityId, title, content, mediaUri)
  fun editCommunityPost(postId: String, newTitle: String, newContent: String) =
    repository.editCommunityPost(postId, newTitle, newContent)
  fun deleteCommunityPost(postId: String) = repository.deleteCommunityPost(postId)
  fun toggleLikeCommunityPost(postId: String) = repository.toggleLikeCommunityPost(postId)
  fun fetchCommunityComments(postId: String) = repository.fetchCommunityComments(postId)
  fun addCommunityComment(postId: String, content: String) = repository.addCommunityComment(postId, content)
  fun fetchCommunityMembers(communityId: String) = repository.fetchCommunityMembers(communityId)
  fun updateCommunityMember(communityId: String, targetUserId: String, role: String?, status: String?) =
    repository.updateCommunityMember(communityId, targetUserId, role, status)
  fun removeCommunityMember(communityId: String, targetUserId: String) =
    repository.removeCommunityMember(communityId, targetUserId)
}

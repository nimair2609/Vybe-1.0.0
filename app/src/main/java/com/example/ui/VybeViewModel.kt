package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.VybeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MainTab {
  FEED, VYBES, MOOD_ZONES, EXPLORE, INBOX, CROSSED_PATHS
}

enum class Screen {
  SPLASH,
  AUTH,
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
  WHAT_NEXT_VIEW
}

class VybeViewModel : ViewModel() {
  private val repository = VybeRepository.getInstance()

  private val _currentScreen = MutableStateFlow(Screen.SPLASH)
  val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

  private val screenBackStack = mutableListOf<Screen>()

  private val _selectedTab = MutableStateFlow(MainTab.FEED)
  val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

  // Selected Detail targets
  private val _selectedConversation = MutableStateFlow<Conversation?>(null)
  val selectedConversation: StateFlow<Conversation?> = _selectedConversation.asStateFlow()

  private val _selectedShop = MutableStateFlow<VybeShop?>(null)
  val selectedShop: StateFlow<VybeShop?> = _selectedShop.asStateFlow()

  private val _selectedUserProfile = MutableStateFlow<User?>(null)
  val selectedUserProfile: StateFlow<User?> = _selectedUserProfile.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  // Repository flows
  val currentUser = repository.currentUser
  val isLoggedIn = repository.isLoggedIn
  val themeMode = repository.themeMode
  val isDarkMode = MutableStateFlow(true) // Kept for backward compatibility

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
  val activeDeviceSessions = repository.activeDeviceSessions
  val notificationPreferences = repository.notificationPreferences
  val userActivity = repository.userActivity

  init {
    viewModelScope.launch {
      delay(1500) // Brief polished startup animation
      _currentScreen.value = if (repository.isLoggedIn.value) Screen.MAIN else Screen.AUTH
    }
  }

  fun navigateTo(screen: Screen) {
    if (_currentScreen.value != screen) {
      screenBackStack.add(_currentScreen.value)
      _currentScreen.value = screen
    }
  }

  fun navigateBack(): Boolean {
    if (screenBackStack.isNotEmpty()) {
      _currentScreen.value = screenBackStack.removeAt(screenBackStack.size - 1)
      return true
    } else if (_currentScreen.value != Screen.MAIN && _currentScreen.value != Screen.AUTH) {
      _currentScreen.value = Screen.MAIN
      return true
    }
    return false
  }

  fun selectTab(tab: MainTab) {
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
    _selectedUserProfile.value = user
    navigateTo(Screen.USER_PROFILE)
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun toggleDarkMode() {
    isDarkMode.value = !isDarkMode.value
    repository.setThemeMode(if (isDarkMode.value) AppThemeMode.DARK else AppThemeMode.LIGHT)
  }

  fun setThemeMode(mode: AppThemeMode) {
    repository.setThemeMode(mode)
    isDarkMode.value = (mode == AppThemeMode.DARK)
  }

  // Repository delegated actions
  fun toggleLike(postId: String) = repository.toggleLikePost(postId)
  fun toggleSave(postId: String) = repository.toggleSavePost(postId)
  fun archivePost(postId: String) = repository.archivePost(postId)
  fun restoreArchivedPost(postId: String) = repository.restoreArchivedPost(postId)
  fun reactToPost(postId: String, reaction: VybeReaction) = repository.reactToPost(postId, reaction)
  fun addComment(postId: String, text: String) = repository.addComment(postId, text)
  fun createPost(caption: String, mood: MoodType?, mediaType: MediaType = MediaType.TEXT, imageUrl: String? = null) {
    repository.createPost(caption, mood, mediaType, imageUrl)
    navigateBack()
  }
  fun selectMoodFilter(mood: MoodType?) = repository.selectMoodFilter(mood)
  fun updateUserMoodAndPulse(mood: MoodType?, pulse: LivePulseStatus) = repository.updateUserMoodAndPulse(mood, pulse)

  // 14.1 UNLOAD
  fun joinUnloadRoom(room: UnloadRoom) {
    repository.joinUnloadRoom(room)
    navigateTo(Screen.UNLOAD_VIEW)
  }
  fun leaveUnloadRoom() {
    repository.leaveUnloadRoom()
    navigateBack()
  }
  fun sendUnloadMessage(roomId: String, text: String, isAnonymous: Boolean) =
    repository.sendUnloadMessage(roomId, text, isAnonymous)

  // 14.6 UNFILTERED
  fun createUnfilteredPost(caption: String, imageUrl: String) =
    repository.createUnfilteredPost(caption, imageUrl)
  fun addUnfilteredComment(postId: String, text: String) =
    repository.addUnfilteredComment(postId, text)

  // Double Take & Predictions
  fun submitDoubleTakeAnswer(promptId: String, answer: String) = repository.submitDoubleTakeAnswer(promptId, answer)
  fun votePrediction(predictionId: String, optionId: Int) = repository.votePrediction(predictionId, optionId)

  // Time Capsule
  fun createTimeCapsule(title: String, message: String, recipient: String, unlockDate: String) {
    repository.createTimeCapsule(title, message, recipient, unlockDate)
    navigateBack()
  }
  fun unlockTimeCapsule(capsuleId: String) = repository.unlockTimeCapsule(capsuleId)

  // Messages & Calls
  fun sendDirectMessage(conversationId: String, text: String) = repository.sendDirectMessage(conversationId, text)
  fun startVoiceCall(peer: User) {
    repository.startCall(peer, isVideo = false)
    navigateTo(Screen.CALL_SCREEN)
  }
  fun startVideoCall(peer: User) {
    repository.startCall(peer, isVideo = true)
    navigateTo(Screen.CALL_SCREEN)
  }
  fun acceptCall() = repository.acceptCall()
  fun endCall() {
    repository.endCall()
    navigateBack()
  }
  fun toggleCallMute() = repository.toggleCallMute()
  fun toggleCallCamera() = repository.toggleCallCamera()

  // Shops & Admin
  fun addShopReview(shopId: String, rating: Int, text: String) = repository.addShopReview(shopId, rating, text)
  fun submitAdminAction(reportId: String, action: ReportStatus) = repository.submitAdminReportAction(reportId, action)
  fun sendCrossedPathRequest(encounterId: String) = repository.sendCrossedPathRequest(encounterId)

  // Account & Settings
  fun checkUserIdAvailability(requestedId: String) = repository.checkUserIdAvailability(requestedId)
  fun updateAccountProfile(
    firstName: String,
    lastName: String,
    requestedUserId: String,
    email: String,
    phone: String,
    bio: String,
    avatarUrl: String,
    profileVideoUrl: String?
  ) = repository.updateAccountProfile(
    firstName, lastName, requestedUserId, email, phone, bio, avatarUrl, profileVideoUrl
  )

  fun submitSupportTicket(category: String, subject: String, message: String) =
    repository.submitSupportTicket(category, subject, message)
  fun updateTimeManagement(breakMinutes: Int, isEnabled: Boolean) =
    repository.updateTimeManagement(breakMinutes, isEnabled)
  fun logoutDevice(sessionId: String) = repository.logoutDevice(sessionId)
  fun updateNotificationPreferences(prefs: NotificationPreferences) =
    repository.updateNotificationPreferences(prefs)
  fun completeOnboarding() = repository.completeOnboarding()

  fun register(
    username: String,
    email: String,
    dob: String,
    firstName: String = "",
    lastName: String = "",
    phone: String = ""
  ): Pair<Boolean, String> {
    val result = repository.registerAccount(username, email, dob, firstName, lastName, phone)
    if (result.first) {
      _currentScreen.value = Screen.MAIN
    }
    return result
  }

  fun login(identifier: String, pass: String): Boolean {
    val success = repository.loginUser(identifier, pass)
    if (success) {
      _currentScreen.value = Screen.MAIN
    }
    return success
  }

  fun logout() {
    repository.logout()
    _currentScreen.value = Screen.AUTH
  }

  fun deactivateAccount() {
    repository.deactivateAccount()
    _currentScreen.value = Screen.AUTH
  }
}

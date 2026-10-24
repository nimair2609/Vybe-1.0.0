package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.VybeLocalStore
import com.example.data.model.*
import com.example.data.remote.SupabaseRepository
import com.example.data.session.SessionManager
import com.example.util.MediaUtils
import com.example.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class UploadStatus {
  object Idle : UploadStatus()
  data class Uploading(val label: String, val progressPercent: Int) : UploadStatus()
  data class Processing(val label: String) : UploadStatus()
  data class Published(val message: String) : UploadStatus()
  data class Failed(val errorMessage: String, val canRetry: Boolean = true) : UploadStatus()
}

sealed class PasswordRecoveryState {
  object Inactive : PasswordRecoveryState()
  object ValidatingLink : PasswordRecoveryState()
  data class NeedsTokenVerification(val emailHint: String = "", val infoMessage: String? = null) : PasswordRecoveryState()
  data class ReadyForNewPassword(val validatedAccessToken: String, val email: String) : PasswordRecoveryState()
  data class UpdatingPassword(val validatedAccessToken: String, val email: String) : PasswordRecoveryState()
  data class Error(val message: String, val emailHint: String = "") : PasswordRecoveryState()
  data class Completed(val message: String) : PasswordRecoveryState()
}

sealed class MessagingEligibility {
  object Allowed : MessagingEligibility()
  data class Denied(val reason: String) : MessagingEligibility()
}

private sealed class PendingUploadOperation {
  data class PostUpload(
    val clientUploadId: String,
    val caption: String,
    val mood: MoodType?,
    val mediaType: MediaType,
    val localOrRemoteMediaUri: String?,
    val isVybes: Boolean,
    val locationName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val audioTrackTitle: String?,
    val preferredAspectRatio: Float?
  ) : PendingUploadOperation()

  data class StoryUpload(
    val clientUploadId: String,
    val localOrRemoteMediaUri: String,
    val caption: String,
    val mediaType: StoryMediaType,
    val audience: StoryAudience,
    val musicTrackTitle: String?,
    val location: String?,
    val textBackgroundColor: String?
  ) : PendingUploadOperation()
}

class VybeRepository private constructor() {

  companion object {
    private const val TAG = "VybeRepository"

    @Volatile
    private var INSTANCE: VybeRepository? = null

    fun getInstance(): VybeRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: VybeRepository().also { INSTANCE = it }
      }
    }
  }

  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val supabaseRepo = SupabaseRepository.getInstance()
  private var appContext: Context? = null

  val isSupabaseConfigured: Boolean
    get() = supabaseRepo.isConfigured

  // --- Connectivity & Upload Progress State ---
  private val _isOnline = MutableStateFlow(true)
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val _uploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
  val uploadStatus: StateFlow<UploadStatus> = _uploadStatus.asStateFlow()

  private var lastFailedUpload: PendingUploadOperation? = null
  private var isUploadInFlight = false

  // --- Password Recovery Session State ---
  private val _passwordRecoveryState = MutableStateFlow<PasswordRecoveryState>(PasswordRecoveryState.Inactive)
  val passwordRecoveryState: StateFlow<PasswordRecoveryState> = _passwordRecoveryState.asStateFlow()

  // --- Authenticated Session State ---
  private val _currentUser = MutableStateFlow<User?>(null)
  val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

  private val _isLoggedIn = MutableStateFlow(false)
  val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

  // --- Real Registered Users (No placeholder/fake users) ---
  private val _allUsers = MutableStateFlow<List<User>>(emptyList())
  val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

  // --- VYBE Matches (Manual user-selected restricted story audience) ---
  private val _vybeMatches = MutableStateFlow<List<User>>(emptyList())
  val vybeMatches: StateFlow<List<User>> = _vybeMatches.asStateFlow()
  private var vybeMatchIdsCache: Set<String> = emptySet()

  // --- App Theme Mode (Persistent via Settings) ---
  private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
  val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

  // --- Feed, Unfiltered & Stories (No sample stories or fake posts) ---
  private val _posts = MutableStateFlow<List<Post>>(emptyList())
  val posts: StateFlow<List<Post>> = _posts.asStateFlow()

  private val _isSyncingFeed = MutableStateFlow(false)
  val isSyncingFeed: StateFlow<Boolean> = _isSyncingFeed.asStateFlow()

  private val _unfilteredPosts = MutableStateFlow<List<Post>>(emptyList())
  val unfilteredPosts: StateFlow<List<Post>> = _unfilteredPosts.asStateFlow()

  private val _stories = MutableStateFlow<List<Story>>(emptyList())
  val stories: StateFlow<List<Story>> = _stories.asStateFlow()

  private var viewedStoryIdsLocal: Set<String> = emptySet()

  // --- Social Graph State ---
  private val _followingUserIds = MutableStateFlow<Set<String>>(emptySet())
  val followingUserIds: StateFlow<Set<String>> = _followingUserIds.asStateFlow()

  private val _requestedFollowUserIds = MutableStateFlow<Set<String>>(emptySet())
  val requestedFollowUserIds: StateFlow<Set<String>> = _requestedFollowUserIds.asStateFlow()

  private val _followerUserIds = MutableStateFlow<Set<String>>(emptySet())
  val followerUserIds: StateFlow<Set<String>> = _followerUserIds.asStateFlow()

  private val _incomingFollowRequestUserIds = MutableStateFlow<Set<String>>(emptySet())
  val incomingFollowRequestUserIds: StateFlow<Set<String>> = _incomingFollowRequestUserIds.asStateFlow()

  // --- Moderation: Blocked & Restricted Users ---
  private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
  val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

  private val _restrictedUserIds = MutableStateFlow<Set<String>>(emptySet())
  val restrictedUserIds: StateFlow<Set<String>> = _restrictedUserIds.asStateFlow()

  // --- Conversations & Messaging (Only real conversations belonging to authenticated user) ---
  private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
  val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

  private val inFlightMessageClientIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

  // --- Notifications (Only real backend interaction events for recipient) ---
  private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
  val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

  // --- Mood Filter ---
  private val _selectedMoodFilter = MutableStateFlow<MoodType?>(null)
  val selectedMoodFilter: StateFlow<MoodType?> = _selectedMoodFilter.asStateFlow()

  // --- Preserved Feature States (Unload, Crossed Paths, Time Capsules, Double Take, What Next, Shops, Spiritual, Creator/Admin) ---
  private val _unloadRooms = MutableStateFlow<List<UnloadRoom>>(emptyList())
  val unloadRooms: StateFlow<List<UnloadRoom>> = _unloadRooms.asStateFlow()

  private val _activeUnloadRoom = MutableStateFlow<UnloadRoom?>(null)
  val activeUnloadRoom: StateFlow<UnloadRoom?> = _activeUnloadRoom.asStateFlow()

  private val _crossedPaths = MutableStateFlow<List<CrossedPathEncounter>>(emptyList())
  val crossedPaths: StateFlow<List<CrossedPathEncounter>> = _crossedPaths.asStateFlow()

  private val _timeCapsules = MutableStateFlow<List<TimeCapsule>>(emptyList())
  val timeCapsules: StateFlow<List<TimeCapsule>> = _timeCapsules.asStateFlow()

  private val _doubleTakePrompts = MutableStateFlow<List<DoubleTakePrompt>>(emptyList())
  val doubleTakePrompts: StateFlow<List<DoubleTakePrompt>> = _doubleTakePrompts.asStateFlow()

  private val _predictions = MutableStateFlow<List<SocialPrediction>>(emptyList())
  val predictions: StateFlow<List<SocialPrediction>> = _predictions.asStateFlow()

  private val _shops = MutableStateFlow<List<VybeShop>>(emptyList())
  val shops: StateFlow<List<VybeShop>> = _shops.asStateFlow()

  private val _activeCall = MutableStateFlow<CallSession?>(null)
  val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

  private val _creatorStats = MutableStateFlow(CreatorStats())
  val creatorStats: StateFlow<CreatorStats> = _creatorStats.asStateFlow()

  private val _adminReports = MutableStateFlow<List<AdminReport>>(emptyList())
  val adminReports: StateFlow<List<AdminReport>> = _adminReports.asStateFlow()

  private val _supportTickets = MutableStateFlow<List<SupportTicket>>(emptyList())
  val supportTickets: StateFlow<List<SupportTicket>> = _supportTickets.asStateFlow()

  private val _timeManagementStats = MutableStateFlow(TimeManagementStats())
  val timeManagementStats: StateFlow<TimeManagementStats> = _timeManagementStats.asStateFlow()

  private val _activeDeviceSessions = MutableStateFlow<List<ActiveDeviceSession>>(emptyList())
  val activeDeviceSessions: StateFlow<List<ActiveDeviceSession>> = _activeDeviceSessions.asStateFlow()

  private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
  val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()

  private val _userActivity = MutableStateFlow<List<UserActivityItem>>(emptyList())
  val userActivity: StateFlow<List<UserActivityItem>> = _userActivity.asStateFlow()

  private val _isSpiritualMode = MutableStateFlow(false)
  val isSpiritualMode: StateFlow<Boolean> = _isSpiritualMode.asStateFlow()

  private val _spiritualReflections = MutableStateFlow<List<SpiritualReflection>>(emptyList())
  val spiritualReflections: StateFlow<List<SpiritualReflection>> = _spiritualReflections.asStateFlow()

  private val _spiritualGratitudes = MutableStateFlow<List<SpiritualGratitude>>(emptyList())
  val spiritualGratitudes: StateFlow<List<SpiritualGratitude>> = _spiritualGratitudes.asStateFlow()

  private val _spiritualIntentions = MutableStateFlow<List<SpiritualIntention>>(emptyList())
  val spiritualIntentions: StateFlow<List<SpiritualIntention>> = _spiritualIntentions.asStateFlow()

  private val _businessApplications = MutableStateFlow<List<BusinessApplication>>(emptyList())
  val businessApplications: StateFlow<List<BusinessApplication>> = _businessApplications.asStateFlow()

  private var hiddenChatPinHash: String = ""

  init {
    seedPreservedFeatureSpaces()
  }

  private fun seedPreservedFeatureSpaces() {
    // Only non-user-impersonating community spaces & reflections (zero fake users, zero fake chats, zero fake notifications, zero fake stories)
    _stories.value = emptyList()
    _posts.value = emptyList()
    _conversations.value = emptyList()
    _notifications.value = emptyList()
    _vybeMatches.value = emptyList()

    _unloadRooms.value = listOf(
      UnloadRoom(
        id = "ul_1",
        title = "Late Night Overthinking & Lo-Fi Space",
        description = "A calm listening room for quiet reflection. Text and voice available.",
        topicTag = "Late Night Reflection",
        mode = UnloadRoomMode.VOICE_AND_TEXT,
        activeListenersCount = 0,
        isHostOnline = true
      ),
      UnloadRoom(
        id = "ul_2",
        title = "Work Burnout & Reset",
        description = "Share openly about demanding schedules and workplace balance.",
        topicTag = "Work Balance",
        mode = UnloadRoomMode.TEXT_ONLY,
        activeListenersCount = 0,
        isHostOnline = true
      )
    )

    _spiritualReflections.value = listOf(
      SpiritualReflection(
        id = "sr_1",
        quote = "Peace comes from within. Do not seek it without.",
        traditionOrAuthor = "Gautama Buddha",
        theme = "Peace",
        audioCue = "Gentle Tibetan Singing Bowl"
      ),
      SpiritualReflection(
        id = "sr_2",
        quote = "Let silence take you to the core of life. All outer noise dissolves in presence.",
        traditionOrAuthor = "Rumi",
        theme = "Mindfulness",
        audioCue = "Soft Bamboo Flute"
      )
    )

    _activeDeviceSessions.value = listOf(
      ActiveDeviceSession(
        id = "s_current",
        deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim().ifBlank { "Android Device" },
        platform = "Android ${android.os.Build.VERSION.RELEASE ?: ""}".trim(),
        locationApprox = "Current Session",
        lastActiveTimestamp = "Active now",
        isCurrent = true
      )
    )
  }

  // =========================================================================
  // INITIALIZATION, NETWORK MONITORING & FULL CLOUD SYNC
  // =========================================================================

  fun initSession(context: Context) {
    appContext = context.applicationContext
    val localStore = VybeLocalStore.getInstance(context)
    _themeMode.value = localStore.getThemeMode()

    // Attach real-time ConnectivityManager monitor
    val monitor = NetworkMonitor.getInstance(context)
    ioScope.launch {
      monitor.isOnline.collect { online ->
        _isOnline.value = online
      }
    }
    monitor.setOnReconnectListener {
      ioScope.launch {
        if (_isLoggedIn.value) {
          syncAllFromSupabase()
          // Automatically retry failed messages on reconnect
          retryAllFailedMessages()
        }
      }
    }

    val savedUsers = localStore.loadUsers()
    if (savedUsers.isNotEmpty()) {
      _allUsers.value = savedUsers
    }

    val session = SessionManager.getInstance(context)
    if (session.isLoggedIn) {
      val user = session.loadCurrentUser()
      if (user != null) {
        val latestUser = if (user.role == UserRole.ADMIN) {
          user.copy(isVerified = true, isVip = true)
        } else {
          savedUsers.firstOrNull { it.id == user.id } ?: user
        }
        _currentUser.value = latestUser
        _isLoggedIn.value = true
        viewedStoryIdsLocal = localStore.loadViewedStoryIds(latestUser.id)
        vybeMatchIdsCache = localStore.loadVybeMatches(latestUser.id)
        if (_allUsers.value.none { it.id == latestUser.id }) {
          _allUsers.value = listOf(latestUser) + _allUsers.value
        }

        _posts.value = localStore.loadPosts()
        _unfilteredPosts.value = localStore.loadUnfilteredPosts()
        _conversations.value = localStore.loadConversations()
        _notifications.value = localStore.loadNotifications()
        _followingUserIds.value = localStore.loadFollowing()
        rebuildVybeMatchesList()
      }
    }

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        syncAllFromSupabase()
      }
    }
  }

  /**
   * Synchronizes profiles, social graph, posts, stories (with viewed state), VYBE Matches,
   * conversations, and notifications from Supabase so every device shows identical real data.
   */
  suspend fun syncAllFromSupabase() {
    if (!supabaseRepo.isConfigured) return
    _isSyncingFeed.value = true
    try {
      val sm = SessionManager.get()
      val validToken = supabaseRepo.resolveValidAccessToken(sm?.accessToken).orEmpty()
      val me = _currentUser.value

      // 1. Refresh my own profile first if logged in
      if (me != null && me.id.isNotBlank() && validToken.isNotBlank()) {
        val freshMe = supabaseRepo.fetchUserProfile(me.id, validToken, me.email)
        if (freshMe != null) {
          _currentUser.value = freshMe
          sm?.updateCurrentUser(freshMe)
          VybeLocalStore.get()?.saveUser(freshMe)
        }
      }

      // 2. Sync social graph (follows, pending follow requests, blocks, restrictions) before mapping users
      val myId = _currentUser.value?.id.orEmpty()
      if (myId.isNotBlank() && validToken.isNotBlank()) {
        val outgoing = supabaseRepo.fetchMyOutgoingFollows(myId, validToken)
        val acceptedFollowing = outgoing.filter { (it.status ?: "ACCEPTED") == "ACCEPTED" }.map { it.followedId }.toSet()
        val pendingOutgoing = outgoing.filter { it.status == "PENDING" }.map { it.followedId }.toSet()
        _followingUserIds.value = acceptedFollowing
        _requestedFollowUserIds.value = pendingOutgoing
        VybeLocalStore.get()?.saveFollowing(acceptedFollowing)

        val incoming = supabaseRepo.fetchMyIncomingFollows(myId, validToken)
        val acceptedFollowers = incoming.filter { (it.status ?: "ACCEPTED") == "ACCEPTED" }.map { it.followerId }.toSet()
        val pendingIncoming = incoming.filter { it.status == "PENDING" }.map { it.followerId }.toSet()
        _followerUserIds.value = acceptedFollowers
        _incomingFollowRequestUserIds.value = pendingIncoming

        val blocks = supabaseRepo.fetchBlockedUserIds(myId, validToken)
        _blockedUserIds.value = blocks
        val restrictions = supabaseRepo.fetchRestrictedUserIds(myId, validToken)
        _restrictedUserIds.value = restrictions
      }

      // 3. Sync registered community profiles (passing valid JWT so PostgREST never 401s!)
      val remoteProfiles = supabaseRepo.fetchAllProfiles(validToken)
      if (remoteProfiles.isNotEmpty()) {
        val decorated = remoteProfiles.map { u -> decorateUserRelationshipFlags(u) }
        _allUsers.value = decorated
        VybeLocalStore.get()?.saveAllUsers(decorated)

        // Keep currentUser social counters in sync with profiles table
        val myRemote = decorated.firstOrNull { it.id == myId }
        if (myRemote != null) {
          _currentUser.value = myRemote
          sm?.updateCurrentUser(myRemote)
        }
      }

      // 4. Sync VYBE Matches
      if (myId.isNotBlank() && validToken.isNotBlank()) {
        val matchesRes = supabaseRepo.fetchVybeMatchUserIds(myId, validToken)
        if (matchesRes.isSuccess) {
          vybeMatchIdsCache = matchesRes.getOrThrow()
          VybeLocalStore.get()?.saveVybeMatches(myId, vybeMatchIdsCache)
        }
        rebuildVybeMatchesList()
      }

      // 5. Sync Feed Posts (respecting private profiles & blocked users)
      syncFeedFromSupabase(validToken)

      // 6. Sync Stories & Viewed State
      if (myId.isNotBlank()) {
        syncStoriesFromSupabase(myId, validToken)
      }

      // 7. Sync Conversations & Notifications for authenticated user
      if (myId.isNotBlank() && validToken.isNotBlank()) {
        val convRes = supabaseRepo.fetchConversationsForUser(
          myUserId = myId,
          blockedIds = _blockedUserIds.value,
          restrictedIds = _restrictedUserIds.value,
          accessToken = validToken
        )
        if (convRes.isSuccess) {
          val remoteConvs = convRes.getOrThrow()
          _conversations.value = remoteConvs
          VybeLocalStore.get()?.saveConversations(remoteConvs)
        }

        val notifRes = supabaseRepo.fetchNotifications(myId, validToken)
        if (notifRes.isSuccess) {
          val usersById = _allUsers.value.associateBy { it.id }
          val enriched = notifRes.getOrThrow().map { item ->
            val resolvedActor = item.actorUser?.id?.let { usersById[it] } ?: item.actorUser
            item.copy(actorUser = resolvedActor)
          }
          _notifications.value = enriched
          VybeLocalStore.get()?.saveNotifications(enriched)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "syncAllFromSupabase warning: ${e.message}")
    } finally {
      _isSyncingFeed.value = false
    }
  }

  private fun decorateUserRelationshipFlags(u: User): User {
    return u.copy(
      isFollowedByMe = _followingUserIds.value.contains(u.id),
      isFollowRequestedByMe = _requestedFollowUserIds.value.contains(u.id),
      followsMe = _followerUserIds.value.contains(u.id),
      hasPendingFollowRequestForMe = _incomingFollowRequestUserIds.value.contains(u.id)
    )
  }

  private fun rebuildVybeMatchesList() {
    val map = _allUsers.value.associateBy { it.id }
    _vybeMatches.value = vybeMatchIdsCache.mapNotNull { map[it] }
  }

  /**
   * Enforces Public vs Private profile visibility on client as defense-in-depth alongside Supabase RLS.
   */
  fun canViewUserContent(targetUser: User): Boolean {
    val me = _currentUser.value
    if (me != null && me.id == targetUser.id) return true
    if (_blockedUserIds.value.contains(targetUser.id)) return false
    val latestTarget = _allUsers.value.firstOrNull { it.id == targetUser.id } ?: targetUser
    if (!latestTarget.isPrivate) return true
    return _followingUserIds.value.contains(latestTarget.id)
  }

  suspend fun syncFeedFromSupabase(explicitToken: String? = null) {
    if (!supabaseRepo.isConfigured) return
    val token = explicitToken ?: supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
    val myId = _currentUser.value?.id
    val res = supabaseRepo.fetchPosts(currentUserId = myId, accessToken = token)
    if (res.isSuccess) {
      val usersMap = _allUsers.value.associateBy { it.id }
      val visiblePosts = res.getOrThrow()
        .map { p ->
          val fullAuthor = usersMap[p.author.id]?.let { decorateUserRelationshipFlags(it) } ?: p.author
          p.copy(author = fullAuthor)
        }
        .filter { p -> canViewUserContent(p.author) }

      _posts.value = visiblePosts
      VybeLocalStore.get()?.savePosts(visiblePosts)

      // Recalculate accurate postsCount for known profiles if DB trigger wasn't installed yet
      if (myId != null) {
        val myActualPosts = visiblePosts.count { it.author.id == myId && !it.isArchived }
        val me = _currentUser.value
        if (me != null && myActualPosts > me.postsCount) {
          val syncedMe = me.copy(postsCount = myActualPosts)
          _currentUser.value = syncedMe
          SessionManager.get()?.updateCurrentUser(syncedMe)
        }
      }
    }
  }

  suspend fun syncStoriesFromSupabase(myUserId: String, explicitToken: String? = null) {
    if (!supabaseRepo.isConfigured || myUserId.isBlank()) return
    val token = explicitToken ?: supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
    val res = supabaseRepo.fetchActiveStories(myUserId, token)
    if (res.isSuccess) {
      val usersMap = _allUsers.value.associateBy { it.id }
      val localViewed = VybeLocalStore.get()?.loadViewedStoryIds(myUserId).orEmpty()
      viewedStoryIdsLocal = viewedStoryIdsLocal + localViewed

      val filteredAndSorted = res.getOrThrow()
        .map { s ->
          val fullAuthor = usersMap[s.author.id]?.let { decorateUserRelationshipFlags(it) } ?: s.author
          val viewed = s.isViewed || viewedStoryIdsLocal.contains(s.id)
          s.copy(author = fullAuthor, isViewed = viewed)
        }
        .filter { s ->
          // Enforce creator privacy & audience rules:
          // Show own stories + stories from accounts we follow (or public stories if allowed)
          if (s.author.id == myUserId) {
            true
          } else if (_blockedUserIds.value.contains(s.author.id)) {
            false
          } else if (s.author.isPrivate && !_followingUserIds.value.contains(s.author.id)) {
            false
          } else {
            // Show followed accounts' stories immediately; also keep unblocked public stories accessible
            _followingUserIds.value.contains(s.author.id) || !s.author.isPrivate
          }
        }
        // Unviewed stories appear first in the tray; already-viewed stories move to the viewed section
        .sortedWith(compareBy<Story> { it.isViewed }.thenByDescending { it.author.id == myUserId })

      _stories.value = filteredAndSorted
      val allMarkedViewed = filteredAndSorted.filter { it.isViewed }.map { it.id }.toSet()
      if (allMarkedViewed.isNotEmpty()) {
        viewedStoryIdsLocal = viewedStoryIdsLocal + allMarkedViewed
        VybeLocalStore.get()?.saveViewedStoryIds(myUserId, viewedStoryIdsLocal)
      }
    }
  }

  suspend fun refreshCurrentUserRole(): UserRole {
    val current = _currentUser.value ?: return UserRole.USER
    val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
    if (supabaseRepo.isConfigured && current.id.isNotBlank()) {
      val updated = supabaseRepo.fetchUserProfile(current.id, token, current.email)
      if (updated != null) {
        _currentUser.value = updated
        _allUsers.value = _allUsers.value.map { if (it.id == updated.id) decorateUserRelationshipFlags(updated) else it }
        SessionManager.get()?.updateCurrentUser(updated)
        VybeLocalStore.get()?.saveUser(updated)
        return updated.role
      }
    }
    return current.role
  }

  // =========================================================================
  // RELIABLE MEDIA UPLOADS (POSTS, REELS & STORIES) WITH PROGRESS & RETRY
  // =========================================================================

  fun clearUploadStatus() {
    if (!isUploadInFlight) {
      _uploadStatus.value = UploadStatus.Idle
    }
  }

  fun retryLastUpload() {
    val pending = lastFailedUpload ?: return
    if (isUploadInFlight) return
    ioScope.launch {
      when (pending) {
        is PendingUploadOperation.PostUpload -> executePostUpload(pending)
        is PendingUploadOperation.StoryUpload -> executeStoryUpload(pending)
      }
    }
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
    val job = PendingUploadOperation.PostUpload(
      clientUploadId = UUID.randomUUID().toString(),
      caption = caption.trim(),
      mood = mood,
      mediaType = mediaType,
      localOrRemoteMediaUri = imageUrl?.takeIf { it.isNotBlank() },
      isVybes = isVybes,
      locationName = locationName?.takeIf { it.isNotBlank() },
      latitude = latitude,
      longitude = longitude,
      audioTrackTitle = audioTrackTitle?.takeIf { it.isNotBlank() },
      preferredAspectRatio = preferredAspectRatio
    )
    ioScope.launch {
      executePostUpload(job)
    }
  }

  private suspend fun executePostUpload(op: PendingUploadOperation.PostUpload) {
    val user = _currentUser.value
    if (user == null) {
      _uploadStatus.value = UploadStatus.Failed("Please sign in before publishing.", canRetry = false)
      return
    }

    val ctx = appContext
    if (supabaseRepo.isConfigured && ctx != null && !NetworkMonitor.checkCurrentlyOnline(ctx)) {
      lastFailedUpload = op
      _uploadStatus.value = UploadStatus.Failed(
        "No internet connection. Your post media is preserved — tap Retry once reconnected.",
        canRetry = true
      )
      return
    }

    isUploadInFlight = true
    try {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      var finalMediaUrls: List<String> = emptyList()
      var finalStoragePaths: List<String> = emptyList()
      var detectedAspectRatio: Float? = op.preferredAspectRatio
      var resolvedMediaType = op.mediaType

      val rawUri = op.localOrRemoteMediaUri
      if (!rawUri.isNullOrBlank()) {
        val isRemoteHttp = rawUri.startsWith("http://", true) || rawUri.startsWith("https://", true)
        if (isRemoteHttp) {
          _uploadStatus.value = UploadStatus.Uploading("Preparing media...", 100)
          finalMediaUrls = listOf(rawUri)
          if (MediaUtils.isVideoUri(ctx, rawUri) && resolvedMediaType != MediaType.REEL) {
            resolvedMediaType = if (op.isVybes) MediaType.REEL else MediaType.VIDEO
          }
        } else if (ctx != null) {
          _uploadStatus.value = UploadStatus.Uploading("Reading media file...", 5)
          val payload = MediaUtils.readMediaPayload(ctx, rawUri)
            ?: throw IllegalStateException("Could not read selected media file. Please re-select the photo or video.")

          if (detectedAspectRatio == null && payload.aspectRatio != null) {
            detectedAspectRatio = payload.aspectRatio
          }
          resolvedMediaType = when {
            op.isVybes -> MediaType.REEL
            payload.isVideo -> MediaType.VIDEO
            else -> MediaType.IMAGE
          }

          if (supabaseRepo.isConfigured) {
            val fileName = "post_${op.clientUploadId}.${payload.extension}"
            _uploadStatus.value = UploadStatus.Uploading(
              label = if (payload.isVideo) "Uploading video..." else "Uploading photo...",
              progressPercent = 8
            )
            val uploadRes = supabaseRepo.uploadMediaWithProgress(
              bucket = "posts",
              userId = user.id,
              fileName = fileName,
              bytes = payload.bytes,
              mimeType = payload.mimeType,
              accessToken = token,
              onProgress = { pct ->
                _uploadStatus.value = UploadStatus.Uploading(
                  label = if (payload.isVideo) "Uploading video..." else "Uploading photo...",
                  progressPercent = pct
                )
              }
            )
            val uploaded = uploadRes.getOrElse { throw it }
            finalMediaUrls = listOf(uploaded.url)
            finalStoragePaths = listOf(uploaded.storagePath)
          } else {
            finalMediaUrls = listOf(rawUri)
          }
        } else {
          finalMediaUrls = listOf(rawUri)
        }
      } else {
        resolvedMediaType = MediaType.TEXT
      }

      if (op.isVybes && finalMediaUrls.isNotEmpty()) {
        resolvedMediaType = MediaType.REEL
      }

      _uploadStatus.value = UploadStatus.Processing("Publishing post...")

      val draftPost = Post(
        id = "p_${op.clientUploadId}",
        author = user,
        caption = op.caption,
        mediaUrls = finalMediaUrls,
        mediaType = resolvedMediaType,
        timestamp = "Just now",
        isVybesReel = op.isVybes || resolvedMediaType == MediaType.REEL,
        moodZone = op.mood,
        audioTrackTitle = op.audioTrackTitle,
        locationName = op.locationName,
        latitude = op.latitude,
        longitude = op.longitude,
        mediaAspectRatio = detectedAspectRatio,
        storagePaths = finalStoragePaths,
        clientUploadId = op.clientUploadId
      )

      val publishedPost = if (supabaseRepo.isConfigured) {
        supabaseRepo.createPostOnSupabase(draftPost, token).getOrElse { err ->
          // Clean up orphaned Storage object if DB row creation failed cleanly and won't be reused
          throw err
        }
      } else {
        draftPost
      }

      // Only mark as published AFTER both Storage upload and DB insert succeeded!
      val deduplicatedList = listOf(publishedPost) + _posts.value.filterNot {
        it.id == publishedPost.id || (!it.clientUploadId.isNullOrBlank() && it.clientUploadId == op.clientUploadId)
      }
      _posts.value = deduplicatedList
      VybeLocalStore.get()?.savePosts(deduplicatedList)

      val updatedUser = user.copy(postsCount = user.postsCount + 1)
      _currentUser.value = updatedUser
      _allUsers.value = _allUsers.value.map { if (it.id == updatedUser.id) decorateUserRelationshipFlags(updatedUser) else it }
      SessionManager.get()?.updateCurrentUser(updatedUser)
      VybeLocalStore.get()?.saveUser(updatedUser)

      lastFailedUpload = null
      if (ctx != null && !rawUri.isNullOrBlank() && supabaseRepo.isConfigured) {
        MediaUtils.cleanupStagedFileIfOwned(ctx, rawUri)
      }
      _uploadStatus.value = UploadStatus.Published(
        if (publishedPost.isVybesReel) "Reel published successfully!" else "Post published successfully!"
      )
    } catch (e: Exception) {
      lastFailedUpload = op
      _uploadStatus.value = UploadStatus.Failed(
        errorMessage = e.message ?: "Upload failed. Your media is preserved — tap Retry.",
        canRetry = true
      )
    } finally {
      isUploadInFlight = false
    }
  }

  internal fun recordVerifiedPostForState(post: Post) {
    val deduplicated = listOf(post) + _posts.value.filterNot {
      it.id == post.id || (!it.clientUploadId.isNullOrBlank() && it.clientUploadId == post.clientUploadId)
    }
    _posts.value = deduplicated
  }

  fun createStory(
    mediaUrl: String,
    caption: String,
    mediaType: StoryMediaType = StoryMediaType.PHOTO,
    audience: StoryAudience = StoryAudience.EVERYONE,
    musicTrackTitle: String? = null,
    location: String? = null,
    textBackgroundColor: String? = null
  ) {
    // Enforce VYBE Matches non-empty audience guard at repository level too
    if ((audience == StoryAudience.VYBE_MATCHES || audience == StoryAudience.CLOSE_FRIENDS) && _vybeMatches.value.isEmpty()) {
      _uploadStatus.value = UploadStatus.Failed(
        "No VYBE Matches selected. Add at least one person to VYBE Matches before sharing a restricted story.",
        canRetry = false
      )
      return
    }

    val op = PendingUploadOperation.StoryUpload(
      clientUploadId = UUID.randomUUID().toString(),
      localOrRemoteMediaUri = mediaUrl,
      caption = caption.trim(),
      mediaType = mediaType,
      audience = audience,
      musicTrackTitle = musicTrackTitle?.takeIf { it.isNotBlank() },
      location = location?.takeIf { it.isNotBlank() },
      textBackgroundColor = textBackgroundColor
    )
    ioScope.launch {
      executeStoryUpload(op)
    }
  }

  private suspend fun executeStoryUpload(op: PendingUploadOperation.StoryUpload) {
    val user = _currentUser.value
    if (user == null) {
      _uploadStatus.value = UploadStatus.Failed("Please sign in to publish a story.", canRetry = false)
      return
    }

    val ctx = appContext
    if (supabaseRepo.isConfigured && ctx != null && !NetworkMonitor.checkCurrentlyOnline(ctx)) {
      lastFailedUpload = op
      _uploadStatus.value = UploadStatus.Failed(
        "No internet connection. Your story is preserved — tap Retry once reconnected.",
        canRetry = true
      )
      return
    }

    isUploadInFlight = true
    try {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      var resolvedUrl = op.localOrRemoteMediaUri
      var resolvedStoragePath: String? = null
      var resolvedMediaType = op.mediaType

      val needsBinaryUpload = op.mediaType != StoryMediaType.TEXT &&
        op.localOrRemoteMediaUri.isNotBlank() &&
        !op.localOrRemoteMediaUri.startsWith("http://", true) &&
        !op.localOrRemoteMediaUri.startsWith("https://", true) &&
        !op.localOrRemoteMediaUri.startsWith("text://", true)

      if (needsBinaryUpload && ctx != null) {
        _uploadStatus.value = UploadStatus.Uploading("Preparing story media...", 5)
        val payload = MediaUtils.readMediaPayload(ctx, op.localOrRemoteMediaUri)
          ?: throw IllegalStateException("Unable to read story media file. Please select it again.")

        if (payload.isVideo) {
          resolvedMediaType = StoryMediaType.VIDEO
        }
        if (supabaseRepo.isConfigured) {
          val fileName = "story_${op.clientUploadId}.${payload.extension}"
          val uploadRes = supabaseRepo.uploadMediaWithProgress(
            bucket = "stories",
            userId = user.id,
            fileName = fileName,
            bytes = payload.bytes,
            mimeType = payload.mimeType,
            accessToken = token,
            onProgress = { pct ->
              _uploadStatus.value = UploadStatus.Uploading(
                label = if (payload.isVideo) "Uploading story video..." else "Uploading story photo...",
                progressPercent = pct
              )
            }
          )
          val uploaded = uploadRes.getOrElse { throw it }
          resolvedUrl = uploaded.url
          resolvedStoragePath = uploaded.storagePath
        }
      }

      _uploadStatus.value = UploadStatus.Processing("Publishing story...")

      val draftStory = Story(
        id = "s_${op.clientUploadId}",
        author = user,
        mediaUrl = resolvedUrl.ifBlank { "text://story" },
        caption = op.caption,
        mediaType = resolvedMediaType,
        audience = op.audience,
        musicTrackTitle = op.musicTrackTitle,
        location = op.location,
        textBackgroundColor = op.textBackgroundColor,
        timestamp = "Just now",
        expiresHoursRemaining = 24,
        isViewed = false,
        storagePath = resolvedStoragePath,
        clientUploadId = op.clientUploadId
      )

      val publishedStory = if (supabaseRepo.isConfigured) {
        supabaseRepo.createStoryOnSupabase(draftStory, token).getOrElse { throw it }
      } else {
        draftStory
      }

      val nextStories = (listOf(publishedStory) + _stories.value.filterNot {
        it.id == publishedStory.id || (!it.clientUploadId.isNullOrBlank() && it.clientUploadId == op.clientUploadId)
      }).sortedWith(compareBy<Story> { it.isViewed }.thenByDescending { it.author.id == user.id })
      _stories.value = nextStories

      lastFailedUpload = null
      if (ctx != null && needsBinaryUpload && supabaseRepo.isConfigured) {
        MediaUtils.cleanupStagedFileIfOwned(ctx, op.localOrRemoteMediaUri)
      }
      _uploadStatus.value = UploadStatus.Published("Story published!")
    } catch (e: Exception) {
      lastFailedUpload = op
      _uploadStatus.value = UploadStatus.Failed(
        errorMessage = e.message ?: "Story upload failed. Tap Retry.",
        canRetry = true
      )
    } finally {
      isUploadInFlight = false
    }
  }

  // =========================================================================
  // PASSWORD RECOVERY FLOW (DEEP LINK + VERIFICATION + SAFE LOGOUT)
  // =========================================================================

  fun openManualPasswordRecovery(emailHint: String = "") {
    _passwordRecoveryState.value = PasswordRecoveryState.NeedsTokenVerification(emailHint = emailHint)
  }

  fun resetPasswordRecoveryState() {
    _passwordRecoveryState.value = PasswordRecoveryState.Inactive
  }

  suspend fun handlePasswordRecoveryDeepLink(
    accessTokenFromLink: String?,
    tokenHashFromLink: String?,
    errorDescription: String?,
    emailHint: String? = null
  ) {
    if (!errorDescription.isNullOrBlank()) {
      val cleanErr = errorDescription.replace("+", " ")
      _passwordRecoveryState.value = PasswordRecoveryState.Error(
        message = if (cleanErr.contains("expired", true)) {
          "This password reset link has expired. Please request a fresh recovery link."
        } else {
          cleanErr
        },
        emailHint = emailHint.orEmpty()
      )
      return
    }

    _passwordRecoveryState.value = PasswordRecoveryState.ValidatingLink
    val validation = supabaseRepo.validateRecoverySession(
      accessTokenFromLink = accessTokenFromLink,
      tokenHashFromLink = tokenHashFromLink,
      emailHint = emailHint
    )
    if (validation.isSuccess) {
      val (validatedJwt, verifiedEmail) = validation.getOrThrow()
      _passwordRecoveryState.value = PasswordRecoveryState.ReadyForNewPassword(
        validatedAccessToken = validatedJwt,
        email = verifiedEmail
      )
    } else {
      _passwordRecoveryState.value = PasswordRecoveryState.Error(
        message = validation.exceptionOrNull()?.message
          ?: "Invalid or expired recovery link. Please request a new password reset email.",
        emailHint = emailHint.orEmpty()
      )
    }
  }

  suspend fun verifyRecoveryTokenManually(email: String, tokenOrHash: String): Pair<Boolean, String> {
    if (tokenOrHash.isBlank()) {
      return Pair(false, "Please enter the recovery token from your email, or tap the reset link directly.")
    }
    _passwordRecoveryState.value = PasswordRecoveryState.ValidatingLink
    val res = supabaseRepo.validateRecoverySession(
      tokenFromInput = tokenOrHash.trim(),
      emailHint = email.trim().ifBlank { null }
    )
    return if (res.isSuccess) {
      val (jwt, verifiedEmail) = res.getOrThrow()
      _passwordRecoveryState.value = PasswordRecoveryState.ReadyForNewPassword(jwt, verifiedEmail)
      Pair(true, "Recovery session verified. Enter your new password.")
    } else {
      val msg = res.exceptionOrNull()?.message ?: "Invalid or expired recovery token."
      _passwordRecoveryState.value = PasswordRecoveryState.Error(msg, email)
      Pair(false, msg)
    }
  }

  suspend fun submitRecoveredNewPassword(
    newPassword: String,
    confirmPassword: String
  ): Pair<Boolean, String> {
    val currentState = _passwordRecoveryState.value
    val readyState = currentState as? PasswordRecoveryState.ReadyForNewPassword
      ?: return Pair(false, "Recovery session is not verified. Please open the password reset link from your email.")

    if (newPassword.length < 8) {
      return Pair(false, "New password must be at least 8 characters long.")
    }
    if (newPassword != confirmPassword) {
      return Pair(false, "New Password and Confirm Password do not match.")
    }

    _passwordRecoveryState.value = PasswordRecoveryState.UpdatingPassword(
      validatedAccessToken = readyState.validatedAccessToken,
      email = readyState.email
    )

    val result = supabaseRepo.completePasswordRecovery(readyState.validatedAccessToken, newPassword)
    return if (result.isSuccess) {
      // End recovery session and ensure normal session is signed out cleanly -> return to Login
      SessionManager.get()?.clearSession()
      VybeLocalStore.get()?.clearUserScopedCaches()
      _currentUser.value = null
      _isLoggedIn.value = false
      val msg = result.getOrThrow()
      _passwordRecoveryState.value = PasswordRecoveryState.Completed(msg)
      Pair(true, msg)
    } else {
      val errMsg = result.exceptionOrNull()?.message ?: "Failed to update password."
      // Restore ReadyForNewPassword unless session expired
      if (errMsg.contains("expired", true)) {
        _passwordRecoveryState.value = PasswordRecoveryState.Error(errMsg, readyState.email)
      } else {
        _passwordRecoveryState.value = readyState
      }
      Pair(false, errMsg)
    }
  }

  // =========================================================================
  // POST, STORY, SOCIAL GRAPH, PRIVACY & MESSAGING ACTIONS
  // =========================================================================

  fun setThemeMode(mode: AppThemeMode) {
    _themeMode.value = mode
    VybeLocalStore.get()?.setThemeMode(mode)
  }

  fun toggleLikePost(postId: String) {
    val me = _currentUser.value ?: return
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      val post = current[index]
      val newIsLiked = !post.isLiked
      val nextReaction = if (newIsLiked) VybeReaction.PURE_ENERGY else null
      val newCount = if (newIsLiked) post.likesCount + 1 else maxOf(0, post.likesCount - 1)
      current[index] = post.copy(isLiked = newIsLiked, userReaction = nextReaction, likesCount = newCount)
      _posts.value = current
      VybeLocalStore.get()?.savePosts(current)

      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          val token = SessionManager.get()?.accessToken.orEmpty()
          supabaseRepo.setPostReaction(postId, me.id, nextReaction, token)
          if (newIsLiked && post.author.id != me.id) {
            supabaseRepo.createInteractionNotificationIfNeeded(
              recipientId = post.author.id,
              actorId = me.id,
              type = "LIKE",
              title = "Post Liked",
              message = "${me.displayName} liked your post.",
              targetId = postId,
              accessToken = token
            )
          }
        }
      }
    }
  }

  fun reactToPost(postId: String, reaction: VybeReaction) {
    val me = _currentUser.value ?: return
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      val post = current[index]
      val currentMap = post.reactionsCount.toMutableMap()
      val prevReaction = post.userReaction
      if (prevReaction != null) {
        val prevCount = (currentMap[prevReaction] ?: 1) - 1
        if (prevCount <= 0) currentMap.remove(prevReaction) else currentMap[prevReaction] = prevCount
      }
      val appliedReaction: VybeReaction?
      val nextLikes: Int
      if (prevReaction != reaction) {
        currentMap[reaction] = (currentMap[reaction] ?: 0) + 1
        appliedReaction = reaction
        nextLikes = if (prevReaction == null) post.likesCount + 1 else post.likesCount
      } else {
        appliedReaction = null
        nextLikes = maxOf(0, post.likesCount - 1)
      }
      current[index] = post.copy(
        userReaction = appliedReaction,
        reactionsCount = currentMap,
        isLiked = appliedReaction != null,
        likesCount = nextLikes
      )
      _posts.value = current
      VybeLocalStore.get()?.savePosts(current)

      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          val token = SessionManager.get()?.accessToken.orEmpty()
          supabaseRepo.setPostReaction(postId, me.id, appliedReaction, token)
          if (appliedReaction != null && post.author.id != me.id) {
            supabaseRepo.createInteractionNotificationIfNeeded(
              recipientId = post.author.id,
              actorId = me.id,
              type = "LIKE",
              title = "${me.displayName} reacted ${appliedReaction.label}",
              message = "Reacted to your post.",
              targetId = postId,
              accessToken = token
            )
          }
        }
      }
    }
  }

  fun toggleSavePost(postId: String) {
    val me = _currentUser.value ?: return
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      val post = current[index]
      val newIsSaved = !post.isSaved
      val newCount = if (newIsSaved) post.savesCount + 1 else maxOf(0, post.savesCount - 1)
      current[index] = post.copy(isSaved = newIsSaved, savesCount = newCount)
      _posts.value = current
      VybeLocalStore.get()?.savePosts(current)

      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          supabaseRepo.setPostSaved(postId, me.id, newIsSaved, SessionManager.get()?.accessToken.orEmpty())
        }
      }
    }
  }

  fun archivePost(postId: String) {
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      current[index] = current[index].copy(isArchived = true)
      _posts.value = current
    }
  }

  fun restoreArchivedPost(postId: String) {
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      current[index] = current[index].copy(isArchived = false)
      _posts.value = current
    }
  }

  fun loadCommentsForPost(postId: String) {
    if (!supabaseRepo.isConfigured) return
    ioScope.launch {
      val token = SessionManager.get()?.accessToken.orEmpty()
      val res = supabaseRepo.fetchCommentsForPost(postId, token)
      if (res.isSuccess) {
        val comments = res.getOrThrow()
        _posts.value = _posts.value.map { p ->
          if (p.id == postId) {
            p.copy(comments = comments, commentsCount = maxOf(p.commentsCount, comments.size))
          } else p
        }
      }
    }
  }

  fun addComment(postId: String, text: String) {
    val user = _currentUser.value ?: return
    val clean = text.trim()
    if (clean.isBlank()) return

    val tempId = "c_${System.currentTimeMillis()}"
    val optimisticComment = PostComment(
      id = tempId,
      postId = postId,
      author = user,
      content = clean,
      timestamp = "Just now"
    )

    val targetPost = _posts.value.firstOrNull { it.id == postId }
    _posts.value = _posts.value.map { p ->
      if (p.id == postId) {
        p.copy(comments = p.comments + optimisticComment, commentsCount = p.commentsCount + 1)
      } else p
    }

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = SessionManager.get()?.accessToken.orEmpty()
        val res = supabaseRepo.addComment(postId, user.id, clean, token)
        if (res.isSuccess) {
          val dto = res.getOrThrow()
          _posts.value = _posts.value.map { p ->
            if (p.id == postId) {
              p.copy(comments = p.comments.map { c -> if (c.id == tempId) c.copy(id = dto.id ?: tempId) else c })
            } else p
          }
          if (targetPost != null && targetPost.author.id != user.id) {
            supabaseRepo.createInteractionNotificationIfNeeded(
              recipientId = targetPost.author.id,
              actorId = user.id,
              type = "COMMENT",
              title = "${user.displayName} commented",
              message = clean.take(80),
              targetId = postId,
              accessToken = token
            )
          }
        } else {
          // Roll back failed comment so counts stay truthful
          _posts.value = _posts.value.map { p ->
            if (p.id == postId) {
              p.copy(
                comments = p.comments.filterNot { it.id == tempId },
                commentsCount = maxOf(0, p.commentsCount - 1)
              )
            } else p
          }
        }
      }
    }
  }

  fun addUnfilteredComment(postId: String, text: String) {
    val user = _currentUser.value ?: return
    if (text.isBlank()) return
    val current = _unfilteredPosts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      val post = current[index]
      val newComment = PostComment(
        id = "ufc_${System.currentTimeMillis()}",
        postId = postId,
        author = user,
        content = text.trim(),
        timestamp = "Just now"
      )
      current[index] = post.copy(comments = post.comments + newComment, commentsCount = post.commentsCount + 1)
      _unfilteredPosts.value = current
    }
  }

  fun deletePost(postId: String) {
    val me = _currentUser.value ?: return
    val targetPost = _posts.value.firstOrNull { it.id == postId }
      ?: _unfilteredPosts.value.firstOrNull { it.id == postId }
      ?: return

    // Enforce ownership: users can only delete their own posts
    if (targetPost.author.id != me.id && me.role != UserRole.ADMIN) return

    val updatedPosts = _posts.value.filterNot { it.id == postId }
    val updatedUnfiltered = _unfilteredPosts.value.filterNot { it.id == postId }
    _posts.value = updatedPosts
    _unfilteredPosts.value = updatedUnfiltered
    VybeLocalStore.get()?.savePosts(updatedPosts)
    VybeLocalStore.get()?.saveUnfilteredPosts(updatedUnfiltered)

    if (targetPost.author.id == me.id) {
      val updatedUser = me.copy(postsCount = (me.postsCount - 1).coerceAtLeast(0))
      _currentUser.value = updatedUser
      _allUsers.value = _allUsers.value.map { if (it.id == updatedUser.id) decorateUserRelationshipFlags(updatedUser) else it }
      SessionManager.get()?.updateCurrentUser(updatedUser)
      VybeLocalStore.get()?.saveUser(updatedUser)
    }

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = SessionManager.get()?.accessToken.orEmpty()
        val mediaCleanup = (targetPost.storagePaths + targetPost.mediaUrls).distinct()
        supabaseRepo.deletePostOnSupabase(postId, mediaCleanup, token)
      }
    }
  }

  private val recordedViewIdsThisSession = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

  fun recordPostView(postId: String) {
    if (!recordedViewIdsThisSession.add(postId)) return
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      val p = current[index]
      val optimisticViews = p.viewsCount + 1
      current[index] = p.copy(viewsCount = optimisticViews)
      _posts.value = current

      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          val token = SessionManager.get()?.accessToken.orEmpty()
          val res = supabaseRepo.recordPostView(postId, _currentUser.value?.id, token)
          if (res.isSuccess) {
            val exactServerCount = res.getOrThrow()
            _posts.value = _posts.value.map {
              if (it.id == postId) it.copy(viewsCount = maxOf(exactServerCount, optimisticViews)) else it
            }
          }
        }
      }
    }
  }

  fun createUnfilteredPost(caption: String, imageUrl: String) {
    val user = _currentUser.value ?: return
    val newPost = Post(
      id = "uf_${System.currentTimeMillis()}",
      author = user,
      caption = caption,
      mediaUrls = listOf(imageUrl),
      mediaType = MediaType.IMAGE,
      timestamp = "Just now",
      isUnfiltered = true,
      commentsCount = 0
    )
    val updatedUnfiltered = listOf(newPost) + _unfilteredPosts.value
    _unfilteredPosts.value = updatedUnfiltered
    VybeLocalStore.get()?.saveUnfilteredPosts(updatedUnfiltered)
  }

  /**
   * Marks a story as viewed locally AND in Supabase `story_views`, immediately moving it out of
   * the unviewed story tray so it never repeatedly shows as new across restarts or devices.
   */
  fun markStoryViewed(storyId: String) {
    val myId = _currentUser.value?.id.orEmpty()
    viewedStoryIdsLocal = viewedStoryIdsLocal + storyId
    if (myId.isNotBlank()) {
      VybeLocalStore.get()?.saveViewedStoryIds(myId, viewedStoryIdsLocal)
    }

    _stories.value = _stories.value
      .map { if (it.id == storyId) it.copy(isViewed = true) else it }
      .sortedWith(compareBy<Story> { it.isViewed }.thenByDescending { it.author.id == myId })

    if (supabaseRepo.isConfigured && myId.isNotBlank()) {
      ioScope.launch {
        supabaseRepo.markStoryViewedOnSupabase(
          storyId = storyId,
          viewerId = myId,
          accessToken = SessionManager.get()?.accessToken.orEmpty()
        )
      }
    }
  }

  fun toggleStoryLike(storyId: String) {
    _stories.value = _stories.value.map {
      if (it.id == storyId) {
        val nextLiked = !it.isLikedByMe
        it.copy(
          isLikedByMe = nextLiked,
          reactionsCount = if (nextLiked) it.reactionsCount + 1 else maxOf(0, it.reactionsCount - 1)
        )
      } else it
    }
  }

  fun deleteStory(storyId: String) {
    val me = _currentUser.value ?: return
    val target = _stories.value.firstOrNull { it.id == storyId } ?: return
    if (target.author.id != me.id) return

    _stories.value = _stories.value.filter { it.id != storyId }
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        supabaseRepo.deleteStoryOnSupabase(
          storyId = storyId,
          storagePathOrUrl = target.storagePath ?: target.mediaUrl,
          accessToken = SessionManager.get()?.accessToken.orEmpty()
        )
      }
    }
  }

  // --- VYBE Matches Management (Manual Add/Remove + Supabase Persistence) ---

  fun addVybeMatch(user: User) {
    val me = _currentUser.value ?: return
    if (user.id == me.id) return
    if (_vybeMatches.value.none { it.id == user.id }) {
      _vybeMatches.value = _vybeMatches.value + user
      vybeMatchIdsCache = vybeMatchIdsCache + user.id
      VybeLocalStore.get()?.saveVybeMatches(me.id, vybeMatchIdsCache)
      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          supabaseRepo.addVybeMatch(me.id, user.id, SessionManager.get()?.accessToken.orEmpty())
        }
      }
    }
  }

  fun removeVybeMatch(userId: String) {
    val me = _currentUser.value ?: return
    _vybeMatches.value = _vybeMatches.value.filter { it.id != userId }
    vybeMatchIdsCache = vybeMatchIdsCache - userId
    VybeLocalStore.get()?.saveVybeMatches(me.id, vybeMatchIdsCache)
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        supabaseRepo.removeVybeMatch(me.id, userId, SessionManager.get()?.accessToken.orEmpty())
      }
    }
  }

  fun isVybeMatch(userId: String): Boolean = _vybeMatches.value.any { it.id == userId }

  // --- Mood Selector ---
  fun selectMoodFilter(mood: MoodType?) {
    _selectedMoodFilter.value = mood
  }

  fun updateUserMoodAndPulse(mood: MoodType?, pulse: LivePulseStatus) {
    val user = _currentUser.value ?: return
    _currentUser.value = user.copy(currentMood = mood, livePulse = pulse)
  }

  // --- Profile Editing & Privacy Settings (Strictly Owner-Only) ---

  fun checkUserIdAvailability(requestedId: String): Pair<Boolean, String> {
    val clean = requestedId.trim().lowercase().replace("@", "")
    if (clean.length < 3) {
      return Pair(false, "User ID must be at least 3 characters.")
    }
    val reserved = setOf("admin", "vybe", "nrnext", "support")
    val currentUser = _currentUser.value
    if (currentUser?.username?.equals(clean, true) == true) {
      return Pair(true, "This is your current User ID.")
    }
    if (reserved.contains(clean) || _allUsers.value.any { it.username.equals(clean, ignoreCase = true) }) {
      return Pair(false, "This User ID is already taken. Try another.")
    }
    return Pair(true, "This User ID is available.")
  }

  fun setAccountPrivate(isPrivate: Boolean) {
    val me = _currentUser.value ?: return
    val updated = me.copy(isPrivate = isPrivate)
    _currentUser.value = updated
    _allUsers.value = _allUsers.value.map { if (it.id == me.id) decorateUserRelationshipFlags(updated) else it }
    SessionManager.get()?.updateCurrentUser(updated)
    VybeLocalStore.get()?.saveUser(updated)

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        supabaseRepo.updateProfilePrivacy(
          userId = me.id,
          isPrivate = isPrivate,
          messagePrivacy = updated.messagePrivacy,
          accessToken = SessionManager.get()?.accessToken.orEmpty()
        )
      }
    }
  }

  fun setMessagePrivacy(messagePrivacy: String) {
    val me = _currentUser.value ?: return
    val updated = me.copy(messagePrivacy = messagePrivacy)
    _currentUser.value = updated
    _allUsers.value = _allUsers.value.map { if (it.id == me.id) decorateUserRelationshipFlags(updated) else it }
    SessionManager.get()?.updateCurrentUser(updated)
    VybeLocalStore.get()?.saveUser(updated)

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        supabaseRepo.updateProfilePrivacy(
          userId = me.id,
          isPrivate = updated.isPrivate,
          messagePrivacy = messagePrivacy,
          accessToken = SessionManager.get()?.accessToken.orEmpty()
        )
      }
    }
  }

  fun updateAccountProfile(
    firstName: String,
    lastName: String,
    username: String,
    email: String,
    phone: String,
    bio: String,
    profileVideoUrl: String? = null,
    avatarUrl: String? = null,
    coverUrl: String? = null
  ) {
    val user = _currentUser.value ?: return
    val cleanUsername = username.lowercase().replace(" ", "_").ifBlank { user.username }
    val displayName = "$firstName $lastName".trim().ifBlank { user.displayName.ifBlank { cleanUsername } }

    ioScope.launch {
      val ctx = appContext
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()

      // Upload local avatar / cover files to Supabase Storage (`avatars` bucket) if changed
      var finalAvatar = avatarUrl ?: user.avatarUrl
      if (ctx != null && supabaseRepo.isConfigured && !avatarUrl.isNullOrBlank() &&
        !avatarUrl.startsWith("http", true)
      ) {
        MediaUtils.readMediaPayload(ctx, avatarUrl)?.let { payload ->
          val uploaded = supabaseRepo.uploadMediaWithProgress(
            bucket = "avatars",
            userId = user.id,
            fileName = "avatar_${System.currentTimeMillis()}.${payload.extension}",
            bytes = payload.bytes,
            mimeType = payload.mimeType,
            accessToken = token
          )
          if (uploaded.isSuccess) {
            finalAvatar = uploaded.getOrThrow().url
          }
        }
      }

      var finalCover = coverUrl ?: user.coverUrl
      if (ctx != null && supabaseRepo.isConfigured && !coverUrl.isNullOrBlank() &&
        !coverUrl.startsWith("http", true)
      ) {
        MediaUtils.readMediaPayload(ctx, coverUrl)?.let { payload ->
          val uploaded = supabaseRepo.uploadMediaWithProgress(
            bucket = "avatars",
            userId = user.id,
            fileName = "cover_${System.currentTimeMillis()}.${payload.extension}",
            bytes = payload.bytes,
            mimeType = payload.mimeType,
            accessToken = token
          )
          if (uploaded.isSuccess) {
            finalCover = uploaded.getOrThrow().url
          }
        }
      }

      val updated = user.copy(
        firstName = firstName,
        lastName = lastName,
        displayName = displayName,
        username = cleanUsername,
        email = email,
        phone = phone,
        bio = bio,
        profileVideoUrl = profileVideoUrl ?: user.profileVideoUrl,
        avatarUrl = finalAvatar,
        coverUrl = finalCover
      )
      _currentUser.value = updated
      _allUsers.value = _allUsers.value.map { if (it.id == updated.id) decorateUserRelationshipFlags(updated) else it }
      SessionManager.get()?.updateCurrentUser(updated)
      VybeLocalStore.get()?.saveUser(updated)

      if (supabaseRepo.isConfigured) {
        supabaseRepo.updateUserProfile(
          userId = updated.id,
          firstName = firstName,
          lastName = lastName,
          displayName = displayName,
          bio = bio,
          avatarUrl = finalAvatar,
          coverUrl = finalCover,
          isPrivate = updated.isPrivate,
          messagePrivacy = updated.messagePrivacy,
          accessToken = token
        )
      }
    }
  }

  fun updateProfilePhoto(newAvatarUrl: String) {
    val user = _currentUser.value ?: return
    updateAccountProfile(
      firstName = user.firstName,
      lastName = user.lastName,
      username = user.username,
      email = user.email,
      phone = user.phone,
      bio = user.bio,
      avatarUrl = newAvatarUrl
    )
  }

  fun updateProfileBanner(newCoverUrl: String) {
    val user = _currentUser.value ?: return
    updateAccountProfile(
      firstName = user.firstName,
      lastName = user.lastName,
      username = user.username,
      email = user.email,
      phone = user.phone,
      bio = user.bio,
      coverUrl = newCoverUrl
    )
  }

  // --- Follow, Follow-Back & Private Account Follow Approval ---

  fun toggleFollowUser(targetUserId: String): Boolean {
    val me = _currentUser.value ?: return false
    if (me.id == targetUserId || me.username.equals(targetUserId, ignoreCase = true)) {
      return false
    }

    val targetUser = _allUsers.value.firstOrNull { it.id == targetUserId }
    val isTargetPrivate = targetUser?.isPrivate == true
    val alreadyFollowing = _followingUserIds.value.contains(targetUserId)
    val alreadyRequested = _requestedFollowUserIds.value.contains(targetUserId)

    if (alreadyFollowing || alreadyRequested) {
      // Unfollow or cancel pending follow request
      val nextFollowing = _followingUserIds.value - targetUserId
      val nextRequested = _requestedFollowUserIds.value - targetUserId
      _followingUserIds.value = nextFollowing
      _requestedFollowUserIds.value = nextRequested
      VybeLocalStore.get()?.saveFollowing(nextFollowing)

      if (alreadyFollowing) {
        val updatedMe = me.copy(followingCount = (me.followingCount - 1).coerceAtLeast(0))
        _currentUser.value = updatedMe
        SessionManager.get()?.updateCurrentUser(updatedMe)
      }
      _allUsers.value = _allUsers.value.map { u ->
        if (u.id == targetUserId) {
          val nextCount = if (alreadyFollowing) (u.followersCount - 1).coerceAtLeast(0) else u.followersCount
          decorateUserRelationshipFlags(u.copy(followersCount = nextCount))
        } else decorateUserRelationshipFlags(u)
      }

      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          supabaseRepo.unfollowUser(me.id, targetUserId, SessionManager.get()?.accessToken.orEmpty())
        }
      }
      return false
    } else if (isTargetPrivate) {
      // Send PENDING follow request for Private Profile
      _requestedFollowUserIds.value = _requestedFollowUserIds.value + targetUserId
      _allUsers.value = _allUsers.value.map { decorateUserRelationshipFlags(it) }

      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          val token = SessionManager.get()?.accessToken.orEmpty()
          val res = supabaseRepo.followUser(me.id, targetUserId, isTargetPrivate = true, accessToken = token)
          if (res.isSuccess && res.getOrThrow() == "ACCEPTED") {
            // If DB hadn't migrated `status` yet and accepted immediately, reconcile cleanly
            _requestedFollowUserIds.value = _requestedFollowUserIds.value - targetUserId
            _followingUserIds.value = _followingUserIds.value + targetUserId
            _allUsers.value = _allUsers.value.map { decorateUserRelationshipFlags(it) }
          } else if (res.isSuccess) {
            supabaseRepo.createInteractionNotificationIfNeeded(
              recipientId = targetUserId,
              actorId = me.id,
              type = "FOLLOW_REQUEST",
              title = "Follow Request",
              message = "${me.displayName} (@${me.username}) requested to follow you.",
              targetId = me.id,
              accessToken = token
            )
          }
        }
      }
      return false
    } else {
      // Public Profile: immediate follow
      val nextFollowing = _followingUserIds.value + targetUserId
      _followingUserIds.value = nextFollowing
      VybeLocalStore.get()?.saveFollowing(nextFollowing)

      val updatedMe = me.copy(followingCount = me.followingCount + 1)
      _currentUser.value = updatedMe
      SessionManager.get()?.updateCurrentUser(updatedMe)

      _allUsers.value = _allUsers.value.map { u ->
        if (u.id == targetUserId) {
          decorateUserRelationshipFlags(u.copy(followersCount = u.followersCount + 1))
        } else if (u.id == updatedMe.id) {
          decorateUserRelationshipFlags(updatedMe)
        } else decorateUserRelationshipFlags(u)
      }

      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          val token = SessionManager.get()?.accessToken.orEmpty()
          val res = supabaseRepo.followUser(me.id, targetUserId, isTargetPrivate = false, accessToken = token)
          if (res.isSuccess) {
            supabaseRepo.createInteractionNotificationIfNeeded(
              recipientId = targetUserId,
              actorId = me.id,
              type = "FOLLOW",
              title = "New Follower",
              message = "${me.displayName} (@${me.username}) started following you.",
              targetId = me.id,
              accessToken = token
            )
            syncStoriesFromSupabase(me.id, token)
          }
        }
      }
      return true
    }
  }

  fun respondToFollowRequest(requesterUserId: String, approve: Boolean) {
    val me = _currentUser.value ?: return
    _incomingFollowRequestUserIds.value = _incomingFollowRequestUserIds.value - requesterUserId
    if (approve) {
      _followerUserIds.value = _followerUserIds.value + requesterUserId
      val updatedMe = me.copy(followersCount = me.followersCount + 1)
      _currentUser.value = updatedMe
      SessionManager.get()?.updateCurrentUser(updatedMe)
    }
    _allUsers.value = _allUsers.value.map { decorateUserRelationshipFlags(it) }

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        supabaseRepo.respondToFollowRequest(
          requesterId = requesterUserId,
          myUserId = me.id,
          approve = approve,
          accessToken = SessionManager.get()?.accessToken.orEmpty()
        )
      }
    }
  }

  // =========================================================================
  // MESSAGING ELIGIBILITY, MUTUAL FOLLOW & REAL INBOX PERSISTENCE
  // =========================================================================

  fun checkMessagingEligibility(peerUser: User): MessagingEligibility {
    val me = _currentUser.value ?: return MessagingEligibility.Denied("Please sign in to send messages.")
    if (me.id == peerUser.id) return MessagingEligibility.Denied("You cannot message your own profile.")
    if (_blockedUserIds.value.contains(peerUser.id)) {
      return MessagingEligibility.Denied("Unblock @${peerUser.username} before sending messages.")
    }

    val latestPeer = _allUsers.value.firstOrNull { it.id == peerUser.id } ?: peerUser
    val iFollowPeer = _followingUserIds.value.contains(latestPeer.id)
    val peerFollowsMe = _followerUserIds.value.contains(latestPeer.id) || latestPeer.followsMe

    return when (latestPeer.messagePrivacy.uppercase()) {
      "NONE" -> MessagingEligibility.Denied("${latestPeer.displayName} has disabled direct messages.")
      "MUTUAL_FOLLOWS" -> {
        if (iFollowPeer && peerFollowsMe) MessagingEligibility.Allowed
        else MessagingEligibility.Denied("Both you and ${latestPeer.displayName} must follow each other to start a chat.")
      }
      else -> {
        // Private profiles require approved follow or mutual follow unless conversation already exists
        val hasExistingConv = _conversations.value.any { !it.isGroup && it.peerUser.id == latestPeer.id }
        if (latestPeer.isPrivate && !iFollowPeer && !peerFollowsMe && !hasExistingConv) {
          MessagingEligibility.Denied("Follow @${latestPeer.username} and wait for approval to message this private account.")
        } else {
          MessagingEligibility.Allowed
        }
      }
    }
  }

  suspend fun openOrCreateRemoteConversation(peerUser: User): Result<Conversation> {
    val eligibility = checkMessagingEligibility(peerUser)
    if (eligibility is MessagingEligibility.Denied) {
      return Result.failure(IllegalStateException(eligibility.reason))
    }

    val existing = _conversations.value.firstOrNull { !it.isGroup && it.peerUser.id == peerUser.id }
    if (existing != null && !existing.id.startsWith("conv_")) {
      return Result.success(existing)
    }

    val me = _currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
    if (supabaseRepo.isConfigured) {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      val convIdRes = supabaseRepo.getOrCreateDirectConversation(me.id, peerUser.id, token)
      if (convIdRes.isSuccess) {
        val convId = convIdRes.getOrThrow()
        val existingWithServerId = _conversations.value.firstOrNull { it.id == convId }
        if (existingWithServerId != null) return Result.success(existingWithServerId)

        val createdConv = Conversation(
          id = convId,
          peerUser = peerUser,
          lastMessage = "Conversation started",
          lastMessageTimestamp = "Just now",
          messages = emptyList(),
          unreadCount = 0
        )
        val next = listOf(createdConv) + _conversations.value.filterNot { !it.isGroup && it.peerUser.id == peerUser.id }
        _conversations.value = next
        VybeLocalStore.get()?.saveConversations(next)
        return Result.success(createdConv)
      } else {
        return Result.failure(convIdRes.exceptionOrNull() ?: Exception("Could not open conversation."))
      }
    }

    return Result.success(getOrCreateConversation(peerUser))
  }

  fun getOrCreateConversation(peerUser: User): Conversation {
    val existing = _conversations.value.firstOrNull { !it.isGroup && it.peerUser.id == peerUser.id }
    if (existing != null) return existing

    val placeholderConv = Conversation(
      id = "conv_${System.currentTimeMillis()}_${peerUser.id}",
      peerUser = peerUser,
      lastMessage = "Conversation started",
      lastMessageTimestamp = "Just now",
      messages = emptyList(),
      unreadCount = 0
    )
    _conversations.value = listOf(placeholderConv) + _conversations.value

    // Resolve real Supabase conversation UUID asynchronously
    val me = _currentUser.value
    if (supabaseRepo.isConfigured && me != null) {
      ioScope.launch {
        val token = SessionManager.get()?.accessToken.orEmpty()
        val res = supabaseRepo.getOrCreateDirectConversation(me.id, peerUser.id, token)
        if (res.isSuccess) {
          val realId = res.getOrThrow()
          _conversations.value = _conversations.value.map {
            if (it.id == placeholderConv.id) it.copy(id = realId) else it
          }
          VybeLocalStore.get()?.saveConversations(_conversations.value)
        }
      }
    }
    return placeholderConv
  }

  fun sendDirectMessage(conversationId: String, text: String) {
    sendExtendedDirectMessage(conversationId, text)
  }

  fun sendExtendedDirectMessage(
    conversationId: String,
    text: String,
    mediaUrl: String? = null,
    mediaType: String = "TEXT",
    fileName: String? = null,
    locationCoordinates: String? = null,
    replyToId: String? = null,
    replyToText: String? = null,
    replyToSenderName: String? = null
  ) {
    val user = _currentUser.value ?: return
    val cleanText = text.trim()
    if (cleanText.isBlank() && mediaUrl.isNullOrBlank()) return

    val clientId = "c_${System.currentTimeMillis()}_${(1000..9999).random()}"
    if (!inFlightMessageClientIds.add(clientId)) return

    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index == -1) {
      inFlightMessageClientIds.remove(clientId)
      return
    }
    val conv = current[index]
    if (conv.isBlocked || _blockedUserIds.value.contains(conv.peerUser.id)) {
      inFlightMessageClientIds.remove(clientId)
      return
    }

    val optimisticMsg = DirectMessage(
      id = clientId,
      clientId = clientId,
      senderId = user.id,
      text = cleanText,
      timestamp = "Sending...",
      isMe = true,
      mediaUrl = mediaUrl,
      mediaType = mediaType,
      fileName = fileName,
      locationCoordinates = locationCoordinates,
      replyToId = replyToId,
      replyToText = replyToText,
      replyToSenderName = replyToSenderName,
      isRead = true,
      status = MessageStatus.SENDING
    )
    current[index] = conv.copy(
      lastMessage = if (cleanText.isNotBlank()) cleanText else "[$mediaType attachment]",
      lastMessageTimestamp = "Just now",
      messages = conv.messages + optimisticMsg
    )
    _conversations.value = current
    VybeLocalStore.get()?.saveConversations(current)

    ioScope.launch {
      try {
        val ctx = appContext
        if (supabaseRepo.isConfigured && ctx != null && !NetworkMonitor.checkCurrentlyOnline(ctx)) {
          markMessageFailed(conversationId, clientId)
          return@launch
        }

        if (supabaseRepo.isConfigured) {
          val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()

          // Ensure conversation exists in Supabase if it still has a temporary `conv_` ID
          var resolvedConvId = conversationId
          if (conversationId.startsWith("conv_")) {
            val createRes = supabaseRepo.getOrCreateDirectConversation(user.id, conv.peerUser.id, token)
            if (createRes.isSuccess) {
              resolvedConvId = createRes.getOrThrow()
              _conversations.value = _conversations.value.map {
                if (it.id == conversationId) it.copy(id = resolvedConvId) else it
              }
            } else {
              markMessageFailed(conversationId, clientId)
              return@launch
            }
          }

          // Upload local chat media if present
          var resolvedMediaUrl = mediaUrl
          if (ctx != null && !mediaUrl.isNullOrBlank() && !mediaUrl.startsWith("http", true)) {
            val payload = MediaUtils.readMediaPayload(ctx, mediaUrl)
            if (payload != null) {
              val uploadRes = supabaseRepo.uploadMediaWithProgress(
                bucket = "chat-media",
                userId = user.id,
                fileName = "msg_${System.currentTimeMillis()}.${payload.extension}",
                bytes = payload.bytes,
                mimeType = payload.mimeType,
                accessToken = token
              )
              if (uploadRes.isSuccess) {
                resolvedMediaUrl = uploadRes.getOrThrow().url
              }
            }
          }

          val res = supabaseRepo.sendMessage(
            conversationId = resolvedConvId,
            senderId = user.id,
            content = cleanText.ifBlank { resolvedMediaUrl ?: "[Attachment]" },
            mediaUrl = resolvedMediaUrl,
            mediaType = mediaType,
            replyToId = replyToId?.takeIf { !it.startsWith("c_") },
            accessToken = token
          )
          val updatedList = _conversations.value.toMutableList()
          val convIdx = updatedList.indexOfFirst { it.id == resolvedConvId }
          if (convIdx != -1) {
            val c = updatedList[convIdx]
            if (res.isSuccess) {
              val serverMsg = res.getOrThrow()
              val reconciled = c.messages.map { m ->
                if (m.clientId == clientId || m.id == clientId) {
                  m.copy(
                    id = serverMsg.id ?: clientId,
                    mediaUrl = resolvedMediaUrl ?: m.mediaUrl,
                    status = MessageStatus.SENT,
                    timestamp = "Just now"
                  )
                } else m
              }
              updatedList[convIdx] = c.copy(messages = reconciled)
              _conversations.value = updatedList
              VybeLocalStore.get()?.saveConversations(updatedList)
            } else {
              markMessageFailed(resolvedConvId, clientId)
            }
          }
        } else {
          val updatedList = _conversations.value.toMutableList()
          val convIdx = updatedList.indexOfFirst { it.id == conversationId }
          if (convIdx != -1) {
            val c = updatedList[convIdx]
            updatedList[convIdx] = c.copy(
              messages = c.messages.map { m ->
                if (m.clientId == clientId || m.id == clientId) m.copy(status = MessageStatus.SENT, timestamp = "Just now") else m
              }
            )
            _conversations.value = updatedList
          }
        }
      } finally {
        inFlightMessageClientIds.remove(clientId)
      }
    }
  }

  private fun markMessageFailed(conversationId: String, clientId: String) {
    val updatedList = _conversations.value.toMutableList()
    val convIdx = updatedList.indexOfFirst { it.id == conversationId }
    if (convIdx != -1) {
      val c = updatedList[convIdx]
      updatedList[convIdx] = c.copy(
        messages = c.messages.map { m ->
          if (m.clientId == clientId || m.id == clientId) m.copy(status = MessageStatus.FAILED, timestamp = "Failed") else m
        }
      )
      _conversations.value = updatedList
    }
  }

  private fun retryAllFailedMessages() {
    _conversations.value.forEach { conv ->
      conv.messages.filter { it.status == MessageStatus.FAILED }.forEach { msg ->
        retrySendMessage(conv.id, msg.clientId ?: msg.id)
      }
    }
  }

  fun retrySendMessage(conversationId: String, messageIdOrClientId: String) {
    val user = _currentUser.value ?: return
    val current = _conversations.value.toMutableList()
    val convIdx = current.indexOfFirst { it.id == conversationId }
    if (convIdx == -1) return
    val conv = current[convIdx]
    val target = conv.messages.firstOrNull { it.id == messageIdOrClientId || it.clientId == messageIdOrClientId } ?: return

    val clientId = target.clientId ?: target.id
    if (!inFlightMessageClientIds.add(clientId)) return

    current[convIdx] = conv.copy(
      messages = conv.messages.map { m ->
        if (m.id == target.id) m.copy(status = MessageStatus.SENDING, timestamp = "Sending...") else m
      }
    )
    _conversations.value = current

    ioScope.launch {
      try {
        if (supabaseRepo.isConfigured) {
          val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
          val res = supabaseRepo.sendMessage(
            conversationId = conversationId,
            senderId = user.id,
            content = target.text.ifBlank { target.mediaUrl ?: "[Media]" },
            mediaUrl = target.mediaUrl,
            mediaType = target.mediaType,
            replyToId = target.replyToId,
            accessToken = token
          )
          if (res.isSuccess) {
            val serverId = res.getOrThrow().id ?: target.id
            _conversations.value = _conversations.value.map { c ->
              if (c.id == conversationId) {
                c.copy(
                  messages = c.messages.map { m ->
                    if (m.id == target.id || m.clientId == clientId) {
                      m.copy(id = serverId, status = MessageStatus.SENT, timestamp = "Just now")
                    } else m
                  }
                )
              } else c
            }
            VybeLocalStore.get()?.saveConversations(_conversations.value)
          } else {
            markMessageFailed(conversationId, clientId)
          }
        }
      } finally {
        inFlightMessageClientIds.remove(clientId)
      }
    }
  }

  fun syncMessagesForConversation(conversationId: String) {
    if (!supabaseRepo.isConfigured || conversationId.startsWith("conv_")) return
    val user = _currentUser.value ?: return
    ioScope.launch {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      val res = supabaseRepo.fetchMessages(conversationId, token, limit = 80)
      if (res.isSuccess) {
        val remoteList = res.getOrThrow()
        val current = _conversations.value.toMutableList()
        val convIdx = current.indexOfFirst { it.id == conversationId }
        if (convIdx != -1) {
          val conv = current[convIdx]
          val inFlightMsgs = conv.messages.filter { it.status == MessageStatus.SENDING || it.status == MessageStatus.FAILED }
          val mappedRemote = remoteList.mapNotNull { dto ->
            val dtoId = dto.id ?: return@mapNotNull null
            DirectMessage(
              id = dtoId,
              senderId = dto.senderId,
              text = if (dto.isDeletedForEveryone == true) "Message unsent" else dto.content,
              timestamp = "Synced",
              isMe = dto.senderId == user.id,
              mediaUrl = dto.mediaUrl,
              mediaType = dto.mediaType ?: "TEXT",
              isDeletedForEveryone = dto.isDeletedForEveryone ?: false,
              replyToId = dto.replyToMessageId,
              status = MessageStatus.SENT
            )
          }
          val remoteIds = mappedRemote.map { it.id }.toSet()
          val merged = mappedRemote + inFlightMsgs.filterNot { remoteIds.contains(it.id) }
          current[convIdx] = conv.copy(
            messages = merged,
            lastMessage = merged.lastOrNull()?.text ?: conv.lastMessage
          )
          _conversations.value = current
          VybeLocalStore.get()?.saveConversations(current)
        }
      }
    }
  }

  // =========================================================================
  // AUTHENTICATION ENTRY POINTS (TRIGGER FULL SYNC ON LOGIN / OTP)
  // =========================================================================

  fun checkAgeRequirement(birthDateStr: String): Pair<Boolean, String> {
    return try {
      val parts = birthDateStr.split("-")
      if (parts.size == 3) {
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val cal = java.util.Calendar.getInstance()
        val cy = cal.get(java.util.Calendar.YEAR)
        val cm = cal.get(java.util.Calendar.MONTH) + 1
        val cd = cal.get(java.util.Calendar.DAY_OF_MONTH)
        var age = cy - y
        if (cm < m || (cm == m && cd < d)) age--
        if (age < 16) {
          Pair(false, "VYBE is strictly for individuals aged 16 and above. Access cannot be granted.")
        } else {
          Pair(true, "Eligible")
        }
      } else {
        Pair(false, "Invalid date format. Use YYYY-MM-DD.")
      }
    } catch (_: Exception) {
      Pair(false, "Invalid date format. Use YYYY-MM-DD.")
    }
  }

  suspend fun registerWithSupabase(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    phone: String,
    birthDateStr: String,
    termsAccepted: Boolean
  ): Pair<Boolean, String> {
    if (!termsAccepted) {
      return Pair(false, "You must agree to the Terms of Service and Privacy Policy to register.")
    }
    val ageCheck = checkAgeRequirement(birthDateStr)
    if (!ageCheck.first) return ageCheck
    if (password.length < 8) {
      return Pair(false, "Password must be at least 8 characters.")
    }

    if (supabaseRepo.isConfigured) {
      val res = supabaseRepo.signUpWithEmail(
        username = username,
        email = email,
        password = password,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        dateOfBirth = birthDateStr
      )
      return if (res.isSuccess) {
        val payload = res.getOrThrow()
        if (payload.accessToken.isNotEmpty()) {
          activateAuthenticatedSession(payload.user, payload.accessToken, payload.refreshToken, payload.expiresInSeconds)
          Pair(true, "Account created! ${if (payload.user.isUnder18) "Teen Safety Protected Mode active." else "Welcome to VYBE."}")
        } else {
          Pair(true, "NEEDS_OTP: Verification email sent to ${payload.user.email}. Open the confirmation link or enter your verification token.")
        }
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Registration failed.")
      }
    } else {
      return registerAccount(firstName, lastName, username, email, phone, birthDateStr)
    }
  }

  fun registerAccount(
    firstName: String,
    lastName: String,
    username: String,
    email: String,
    phone: String,
    birthDateStr: String
  ): Pair<Boolean, String> {
    val ageCheck = checkAgeRequirement(birthDateStr)
    if (!ageCheck.first) return ageCheck
    return try {
      val parts = birthDateStr.split("-")
      val y = parts[0].toInt()
      val m = parts[1].toInt()
      val d = parts[2].toInt()
      val cal = java.util.Calendar.getInstance()
      var age = cal.get(java.util.Calendar.YEAR) - y
      if ((cal.get(java.util.Calendar.MONTH) + 1) < m ||
        ((cal.get(java.util.Calendar.MONTH) + 1) == m && cal.get(java.util.Calendar.DAY_OF_MONTH) < d)
      ) age--

      val newUser = User(
        id = "u_${System.currentTimeMillis()}",
        username = username.lowercase().replace(" ", "_"),
        displayName = "$firstName $lastName".trim().ifBlank { username },
        firstName = firstName,
        lastName = lastName,
        email = email,
        phone = phone,
        isEmailVerified = true,
        dateOfBirth = birthDateStr,
        age = age,
        isUnder18 = age in 16..17,
        role = UserRole.USER,
        isOnboardingCompleted = false
      )
      _currentUser.value = newUser
      _isLoggedIn.value = true
      _allUsers.value = listOf(newUser) + _allUsers.value.filterNot { it.id == newUser.id }
      SessionManager.get()?.saveSession(newUser)
      Pair(true, "Welcome to VYBE!")
    } catch (_: Exception) {
      Pair(false, "Registration could not be completed.")
    }
  }

  fun registerAccount(username: String, email: String, birthDateStr: String): Pair<Boolean, String> {
    return registerAccount(username, "", username, email, "", birthDateStr)
  }

  private suspend fun activateAuthenticatedSession(
    user: User,
    accessToken: String,
    refreshToken: String,
    expiresInSeconds: Long
  ) {
    // Isolate previous user's local inbox/notifications before switching accounts
    val previousUserId = SessionManager.get()?.userId
    if (!previousUserId.isNullOrBlank() && previousUserId != user.id) {
      VybeLocalStore.get()?.clearUserScopedCaches()
      _conversations.value = emptyList()
      _notifications.value = emptyList()
      _stories.value = emptyList()
      _vybeMatches.value = emptyList()
      _followingUserIds.value = emptySet()
    }

    _currentUser.value = user
    _isLoggedIn.value = true
    viewedStoryIdsLocal = VybeLocalStore.get()?.loadViewedStoryIds(user.id).orEmpty()
    vybeMatchIdsCache = VybeLocalStore.get()?.loadVybeMatches(user.id).orEmpty()

    SessionManager.get()?.saveSession(
      user = user,
      accessToken = accessToken,
      refreshToken = refreshToken,
      expiresInSeconds = expiresInSeconds
    )
    VybeLocalStore.get()?.saveUser(user)

    // Immediately sync profiles, feed, stories, inbox, and notifications for this user!
    syncAllFromSupabase()
  }

  suspend fun loginWithSupabase(identifier: String, pass: String): Pair<Boolean, String> {
    if (identifier.isBlank() || pass.isBlank()) {
      return Pair(false, "Please enter your email/username and password.")
    }

    if (supabaseRepo.isConfigured) {
      val digits = identifier.filter { it.isDigit() }
      val resolvedEmail = when {
        identifier.contains("@") -> identifier.trim().lowercase()
        digits.length >= 7 -> {
          val userWithPhone = _allUsers.value.firstOrNull {
            it.phone.filter { c -> c.isDigit() }.endsWith(digits.takeLast(10))
          }
          userWithPhone?.email ?: supabaseRepo.resolveEmailForIdentifier(identifier) ?: identifier.trim().lowercase()
        }
        else -> supabaseRepo.resolveEmailForIdentifier(identifier) ?: identifier.trim().lowercase()
      }

      if (!resolvedEmail.contains("@")) {
        return Pair(false, "Could not resolve '$identifier'. Please sign in using your registered email address.")
      }

      val res = supabaseRepo.signInWithEmail(resolvedEmail, pass)
      return if (res.isSuccess) {
        val payload = res.getOrThrow()
        activateAuthenticatedSession(payload.user, payload.accessToken, payload.refreshToken, payload.expiresInSeconds)
        Pair(true, "Signed in successfully.")
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Invalid login credentials.")
      }
    } else {
      val ok = loginUser(identifier, pass)
      return Pair(ok, if (ok) "Signed in." else "Invalid credentials.")
    }
  }

  suspend fun verifyOtp(email: String, token: String, type: String = "signup"): Pair<Boolean, String> {
    if (supabaseRepo.isConfigured) {
      val res = supabaseRepo.verifyOtp(email, token, type)
      return if (res.isSuccess) {
        val payload = res.getOrThrow()
        activateAuthenticatedSession(payload.user, payload.accessToken, payload.refreshToken, payload.expiresInSeconds)
        Pair(true, "Email verified!")
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Verification token invalid or expired.")
      }
    }
    return Pair(true, "Email verified!")
  }

  suspend fun sendPasswordReset(email: String): Pair<Boolean, String> {
    val clean = email.trim()
    if (clean.isBlank() || !clean.contains("@")) {
      return Pair(false, "Please enter your registered email address.")
    }
    if (supabaseRepo.isConfigured) {
      val res = supabaseRepo.sendPasswordReset(clean)
      return if (res.isSuccess) {
        Pair(true, res.getOrThrow())
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Unable to send password recovery link.")
      }
    }
    return Pair(true, "Password recovery link sent to $clean.")
  }

  suspend fun resetPasswordWithOtp(email: String, otp: String, newPass: String): Pair<Boolean, String> {
    if (newPass.length < 8) {
      return Pair(false, "Password must be at least 8 characters.")
    }
    val verified = verifyRecoveryTokenManually(email, otp)
    if (!verified.first) return verified
    return submitRecoveredNewPassword(newPass, newPass)
  }

  fun loginUser(identifier: String, pass: String): Boolean {
    val clean = identifier.trim().lowercase().replace("@", "")
    val matched = _allUsers.value.firstOrNull {
      it.email.equals(identifier.trim(), ignoreCase = true) || it.username.lowercase() == clean
    } ?: User(
      id = "u_${System.currentTimeMillis()}",
      username = clean.ifBlank { "vybe_user" },
      displayName = clean.ifBlank { "VYBE Member" },
      email = if (identifier.contains("@")) identifier.trim() else "$clean@vybe.app",
      role = UserRole.USER,
      isOnboardingCompleted = true
    )
    _currentUser.value = matched
    _isLoggedIn.value = true
    _allUsers.value = listOf(matched) + _allUsers.value.filterNot { it.id == matched.id }
    SessionManager.get()?.saveSession(matched)
    return true
  }

  fun logout() {
    val token = SessionManager.get()?.accessToken.orEmpty()
    ioScope.launch {
      supabaseRepo.signOut(token)
    }
    SessionManager.get()?.clearSession()
    VybeLocalStore.get()?.clearUserScopedCaches()
    _currentUser.value = null
    _isLoggedIn.value = false
    _conversations.value = emptyList()
    _notifications.value = emptyList()
    _stories.value = emptyList()
    _vybeMatches.value = emptyList()
    _followingUserIds.value = emptySet()
    _requestedFollowUserIds.value = emptySet()
    _followerUserIds.value = emptySet()
    _incomingFollowRequestUserIds.value = emptySet()
    _uploadStatus.value = UploadStatus.Idle
  }

  fun deactivateAccount() {
    logout()
  }

  fun completeOnboarding() {
    val user = _currentUser.value ?: return
    _currentUser.value = user.copy(isOnboardingCompleted = true)
  }

  // =========================================================================
  // NOTIFICATIONS, MODERATION, SEARCH & REMAINING FEATURE ACTIONS
  // =========================================================================

  fun markNotificationAsRead(id: String) {
    val updated = _notifications.value.map { if (it.id == id) it.copy(isRead = true) else it }
    _notifications.value = updated
    VybeLocalStore.get()?.saveNotifications(updated)
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        supabaseRepo.markNotificationRead(id, SessionManager.get()?.accessToken.orEmpty())
      }
    }
  }

  fun markAllNotificationsAsRead() {
    val myId = _currentUser.value?.id.orEmpty()
    val updated = _notifications.value.map { it.copy(isRead = true) }
    _notifications.value = updated
    VybeLocalStore.get()?.saveNotifications(updated)
    if (supabaseRepo.isConfigured && myId.isNotBlank()) {
      ioScope.launch {
        supabaseRepo.markAllNotificationsRead(myId, SessionManager.get()?.accessToken.orEmpty())
      }
    }
  }

  fun markAllNotificationsRead() = markAllNotificationsAsRead()

  fun addNotification(item: NotificationItem) {
    if (_notifications.value.any { it.id == item.id }) return
    val updated = listOf(item) + _notifications.value
    _notifications.value = updated
    VybeLocalStore.get()?.saveNotifications(updated)
  }

  fun deleteNotification(notificationId: String) {
    val updated = _notifications.value.filterNot { it.id == notificationId }
    _notifications.value = updated
    VybeLocalStore.get()?.saveNotifications(updated)
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        supabaseRepo.deleteNotification(notificationId, SessionManager.get()?.accessToken.orEmpty())
      }
    }
  }

  fun searchUsers(query: String): List<User> {
    val clean = query.trim().lowercase().removePrefix("@")
    if (clean.isBlank()) return emptyList()
    return _allUsers.value.filter { u ->
      !_blockedUserIds.value.contains(u.id) && (
        u.displayName.lowercase().contains(clean) ||
          u.username.lowercase().contains(clean) ||
          u.publicVybeId.lowercase().contains(clean) ||
          u.id.lowercase().contains(clean)
        )
    }
  }

  /**
   * Queries Supabase `profiles` live so newly registered users on other devices appear immediately in Search.
   */
  suspend fun searchUsersRemote(query: String): List<User> {
    val clean = query.trim()
    if (clean.isBlank()) return emptyList()
    if (supabaseRepo.isConfigured) {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
      val remoteMatches = supabaseRepo.searchProfilesRemote(clean, token)
      if (remoteMatches.isNotEmpty()) {
        val existingMap = _allUsers.value.associateBy { it.id }.toMutableMap()
        remoteMatches.forEach { u ->
          existingMap[u.id] = decorateUserRelationshipFlags(u)
        }
        val merged = existingMap.values.toList()
        _allUsers.value = merged
        VybeLocalStore.get()?.saveAllUsers(merged)
      }
    }
    return searchUsers(clean)
  }

  fun searchUserByVybeId(query: String): User? = searchUsers(query).firstOrNull()

  fun toggleBlockUser(userId: String): Boolean {
    val currentSet = _blockedUserIds.value.toMutableSet()
    val isBlockedNow = if (currentSet.contains(userId)) {
      currentSet.remove(userId)
      false
    } else {
      currentSet.add(userId)
      true
    }
    _blockedUserIds.value = currentSet

    _conversations.value = _conversations.value.map {
      if (it.peerUser.id == userId) it.copy(isBlocked = isBlockedNow) else it
    }
    if (isBlockedNow) {
      // Remove blocked user's posts & stories from active feed immediately
      _posts.value = _posts.value.filterNot { it.author.id == userId }
      _stories.value = _stories.value.filterNot { it.author.id == userId }
      _followingUserIds.value = _followingUserIds.value - userId
    }

    val me = _currentUser.value
    if (supabaseRepo.isConfigured && me != null) {
      ioScope.launch {
        val token = SessionManager.get()?.accessToken.orEmpty()
        if (isBlockedNow) {
          supabaseRepo.blockUser(me.id, userId, token)
          supabaseRepo.unfollowUser(me.id, userId, token)
        } else {
          supabaseRepo.unblockUser(me.id, userId, token)
        }
      }
    }
    return isBlockedNow
  }

  fun unblockUser(userId: String) {
    if (_blockedUserIds.value.contains(userId)) {
      toggleBlockUser(userId)
    }
  }

  fun toggleRestrictUser(userId: String): Boolean {
    val currentSet = _restrictedUserIds.value.toMutableSet()
    val isRestrictedNow = if (currentSet.contains(userId)) {
      currentSet.remove(userId)
      false
    } else {
      currentSet.add(userId)
      true
    }
    _restrictedUserIds.value = currentSet
    _conversations.value = _conversations.value.map {
      if (it.peerUser.id == userId) it.copy(isRestricted = isRestrictedNow) else it
    }

    val me = _currentUser.value
    if (supabaseRepo.isConfigured && me != null) {
      ioScope.launch {
        supabaseRepo.setUserRestricted(
          restrictorId = me.id,
          restrictedId = userId,
          restricted = isRestrictedNow,
          accessToken = SessionManager.get()?.accessToken.orEmpty()
        )
      }
    }
    return isRestrictedNow
  }

  suspend fun submitReport(
    targetType: String,
    targetId: String,
    reason: String,
    details: String = ""
  ): Pair<Boolean, String> {
    val me = _currentUser.value
    if (supabaseRepo.isConfigured && me != null) {
      val token = SessionManager.get()?.accessToken.orEmpty()
      val res = supabaseRepo.submitReport(me.id, targetType, targetId, reason, details, token)
      return if (res.isSuccess) {
        Pair(true, "Report submitted to VYBE Trust & Safety.")
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Failed to submit report.")
      }
    }
    return Pair(true, "Report recorded for safety review.")
  }

  // --- Remaining Preserved Feature Methods ---

  fun editDirectMessage(conversationId: String, messageId: String, newText: String) {
    _conversations.value = _conversations.value.map { conv ->
      if (conv.id == conversationId) {
        conv.copy(messages = conv.messages.map { m ->
          if (m.id == messageId && m.isMe) m.copy(text = newText.trim(), isEdited = true) else m
        })
      } else conv
    }
  }

  fun deleteMessageForMe(conversationId: String, messageId: String) {
    _conversations.value = _conversations.value.map { conv ->
      if (conv.id == conversationId) conv.copy(messages = conv.messages.filterNot { it.id == messageId }) else conv
    }
  }

  fun deleteMessageForEveryone(conversationId: String, messageId: String) {
    _conversations.value = _conversations.value.map { conv ->
      if (conv.id == conversationId) {
        conv.copy(messages = conv.messages.map { m ->
          if (m.id == messageId) m.copy(text = "This message was deleted", isDeletedForEveryone = true, mediaUrl = null) else m
        })
      } else conv
    }
  }

  fun addMessageReaction(conversationId: String, messageId: String, emoji: String) {
    _conversations.value = _conversations.value.map { conv ->
      if (conv.id == conversationId) {
        conv.copy(messages = conv.messages.map { m ->
          if (m.id == messageId) {
            val next = if (m.reactions.contains(emoji)) m.reactions - emoji else m.reactions + emoji
            m.copy(reactions = next)
          } else m
        })
      } else conv
    }
  }

  fun setHiddenChatPin(pin: String) {
    hiddenChatPinHash = pin.trim().hashCode().toString()
  }

  fun verifyHiddenChatPin(pin: String): Boolean =
    hiddenChatPinHash.isNotBlank() && pin.trim().hashCode().toString() == hiddenChatPinHash

  fun hasHiddenChatPinSet(): Boolean = hiddenChatPinHash.isNotBlank()

  fun toggleHideConversation(conversationId: String) {
    _conversations.value = _conversations.value.map {
      if (it.id == conversationId) it.copy(isHidden = !it.isHidden) else it
    }
  }

  fun createGroupConversation(groupName: String, members: List<User>): Conversation {
    val user = _currentUser.value ?: User(id = "u_self", username = "me", displayName = "Me")
    val groupConv = Conversation(
      id = "grp_${System.currentTimeMillis()}",
      peerUser = members.firstOrNull() ?: user,
      groupName = groupName,
      isGroup = true,
      groupMembers = listOf(user) + members,
      groupAdminId = user.id,
      lastMessage = "Group \"$groupName\" created",
      lastMessageTimestamp = "Just now",
      messages = emptyList()
    )
    _conversations.value = listOf(groupConv) + _conversations.value
    return groupConv
  }

  fun markConversationAsRead(conversationId: String) {
    _conversations.value = _conversations.value.map {
      if (it.id == conversationId) it.copy(unreadCount = 0) else it
    }
  }

  fun deleteConversation(conversationId: String) {
    _conversations.value = _conversations.value.filterNot { it.id == conversationId }
    VybeLocalStore.get()?.saveConversations(_conversations.value)
  }

  fun addGroupMember(conversationId: String, newMember: User) {
    _conversations.value = _conversations.value.map { conv ->
      if (conv.id == conversationId && conv.groupMembers.none { it.id == newMember.id }) {
        conv.copy(groupMembers = conv.groupMembers + newMember)
      } else conv
    }
  }

  fun removeGroupMember(conversationId: String, memberUserId: String) {
    _conversations.value = _conversations.value.map { conv ->
      if (conv.id == conversationId) conv.copy(groupMembers = conv.groupMembers.filterNot { it.id == memberUserId }) else conv
    }
  }

  fun sendVoiceMessage(conversationId: String, durationText: String = "0:08") {
    sendExtendedDirectMessage(conversationId, "Voice message ($durationText)", mediaType = "AUDIO")
  }

  fun updateChatNickname(conversationId: String, nickname: String) {
    _conversations.value = _conversations.value.map {
      if (it.id == conversationId) it.copy(customNickname = nickname.trim().ifBlank { null }) else it
    }
  }

  fun toggleMuteChat(conversationId: String) {
    _conversations.value = _conversations.value.map {
      if (it.id == conversationId) it.copy(isMuted = !it.isMuted) else it
    }
  }

  fun toggleConversationUnread(conversationId: String) {
    _conversations.value = _conversations.value.map {
      if (it.id == conversationId) it.copy(unreadCount = if (it.unreadCount > 0) 0 else 1) else it
    }
  }

  fun updateChatTheme(conversationId: String, theme: String) {
    _conversations.value = _conversations.value.map {
      if (it.id == conversationId) it.copy(chatTheme = theme) else it
    }
  }

  fun initiateCall(peerUser: User, isVideo: Boolean): CallAvailabilityResult {
    if (_currentUser.value == null) return CallAvailabilityResult.Error("Please sign in first.")
    if (_blockedUserIds.value.contains(peerUser.id)) {
      return CallAvailabilityResult.Blocked("Cannot call a blocked user.")
    }
    val isPeerOnline = peerUser.livePulse != LivePulseStatus.NONE && peerUser.livePulse != LivePulseStatus.QUIET_TIME
    if (!isPeerOnline) {
      return CallAvailabilityResult.Offline("${peerUser.displayName} is currently offline. Send a message instead.")
    }
    startCall(peerUser, isVideo)
    return CallAvailabilityResult.Available
  }

  fun startCall(peerUser: User, isVideo: Boolean) {
    _activeCall.value = CallSession(peerUser = peerUser, isVideo = isVideo, state = CallState.OUTGOING)
  }

  fun acceptCall() {
    _activeCall.value = _activeCall.value?.copy(state = CallState.CONNECTED)
  }

  fun endCall() {
    _activeCall.value = null
  }

  fun toggleCallMute() {
    _activeCall.value = _activeCall.value?.let { it.copy(isMuted = !it.isMuted) }
  }

  fun toggleCallCamera() {
    _activeCall.value = _activeCall.value?.let { it.copy(isCameraOff = !it.isCameraOff) }
  }

  fun joinUnloadRoom(room: UnloadRoom) {
    _activeUnloadRoom.value = room
  }

  fun leaveUnloadRoom() {
    _activeUnloadRoom.value = null
  }

  fun sendUnloadMessage(roomId: String, text: String, isAnonymous: Boolean) {
    val user = _currentUser.value ?: return
    if (text.isBlank()) return
    _unloadRooms.value = _unloadRooms.value.map { room ->
      if (room.id == roomId) {
        val msg = UnloadMessage(
          id = "ulm_${System.currentTimeMillis()}",
          senderName = if (isAnonymous) "Anonymous Vyber" else user.displayName,
          isAnonymous = isAnonymous,
          text = text.trim(),
          timestamp = "Just now",
          isMe = true
        )
        val updated = room.copy(recentMessages = room.recentMessages + msg)
        if (_activeUnloadRoom.value?.id == roomId) _activeUnloadRoom.value = updated
        updated
      } else room
    }
  }

  fun createUnloadRoom(
    title: String,
    description: String,
    topicTag: String,
    mode: UnloadRoomMode,
    durationMinutes: Int,
    capacity: Int,
    scheduledTime: String,
    joinerMessage: String,
    termsAgreed: Boolean
  ): UnloadRoom {
    val user = _currentUser.value
    val newRoom = UnloadRoom(
      id = "room_${System.currentTimeMillis()}",
      title = title,
      description = description,
      topicTag = topicTag,
      mode = mode,
      hostId = user?.id ?: "self",
      hostName = user?.displayName ?: "VYBE Host",
      durationMinutes = durationMinutes,
      capacity = capacity,
      scheduledTime = scheduledTime,
      joinerMessage = joinerMessage,
      termsAgreed = termsAgreed,
      activeListenersCount = 1
    )
    _unloadRooms.value = listOf(newRoom) + _unloadRooms.value
    _activeUnloadRoom.value = newRoom
    return newRoom
  }

  fun updateUnloadRoom(
    roomId: String,
    title: String,
    description: String,
    topicTag: String,
    mode: UnloadRoomMode,
    durationMinutes: Int,
    capacity: Int
  ) {
    _unloadRooms.value = _unloadRooms.value.map {
      if (it.id == roomId) {
        val updated = it.copy(title = title, description = description, topicTag = topicTag, mode = mode, durationMinutes = durationMinutes, capacity = capacity)
        if (_activeUnloadRoom.value?.id == roomId) _activeUnloadRoom.value = updated
        updated
      } else it
    }
  }

  fun deleteUnloadRoom(roomId: String) {
    _unloadRooms.value = _unloadRooms.value.filterNot { it.id == roomId }
    if (_activeUnloadRoom.value?.id == roomId) _activeUnloadRoom.value = null
  }

  fun submitDoubleTakeAnswer(promptId: String, answer: String) {
    _doubleTakePrompts.value = _doubleTakePrompts.value.map { p ->
      if (p.id == promptId) p.copy(myAnswer = answer, isMyAnswerSubmitted = true, isRevealed = p.isPeerAnswerSubmitted) else p
    }
  }

  fun createDoubleTakePrompt(
    question: String,
    category: String,
    optionA: String,
    optionB: String,
    peerUser: User,
    isPublic: Boolean = false
  ) {
    val user = _currentUser.value ?: return
    val newPrompt = DoubleTakePrompt(
      id = "dt_${System.currentTimeMillis()}",
      promptQuestion = question,
      category = category,
      optionA = optionA,
      optionB = optionB,
      creatorId = user.id,
      peerUser = peerUser,
      isPublic = isPublic
    )
    _doubleTakePrompts.value = listOf(newPrompt) + _doubleTakePrompts.value
  }

  fun submitPublicDoubleTakeAnswer(promptId: String, answer: String) {
    val user = _currentUser.value ?: return
    _doubleTakePrompts.value = _doubleTakePrompts.value.map { prompt ->
      if (prompt.id == promptId) {
        val ans = PublicAnswer("pa_${System.currentTimeMillis()}", user, answer, "Just now", false)
        prompt.copy(publicAnswers = prompt.publicAnswers + ans, myAnswer = answer, isMyAnswerSubmitted = true)
      } else prompt
    }
  }

  fun connectWithPublicDoubleTakeAnswer(promptId: String, answerId: String) {
    _doubleTakePrompts.value = _doubleTakePrompts.value.map { prompt ->
      if (prompt.id == promptId) {
        prompt.copy(
          publicAnswers = prompt.publicAnswers.map { if (it.id == answerId) it.copy(isConnected = true) else it },
          isChatUnlocked = true
        )
      } else prompt
    }
  }

  fun deleteDoubleTakePrompt(promptId: String) {
    _doubleTakePrompts.value = _doubleTakePrompts.value.filterNot { it.id == promptId }
  }

  fun acceptDoubleTakeMatch(promptId: String) {
    _doubleTakePrompts.value = _doubleTakePrompts.value.map {
      if (it.id == promptId) it.copy(isCreatorAccepted = true, isChatUnlocked = true) else it
    }
  }

  fun votePrediction(predictionId: String, optionId: Int) {
    _predictions.value = _predictions.value.map { pred ->
      if (pred.id == predictionId && pred.userSelectedOptionId == null) {
        val total = pred.totalVotes + 1
        val opts = pred.options.map { opt ->
          val vc = if (opt.id == optionId) opt.votesCount + 1 else opt.votesCount
          opt.copy(votesCount = vc, percentage = ((vc.toFloat() / total) * 100).toInt())
        }
        pred.copy(totalVotes = total, options = opts, userSelectedOptionId = optionId)
      } else pred
    }
  }

  fun createPredictionPoll(title: String, category: String, durationHours: Int, optionTexts: List<String>) {
    val user = _currentUser.value ?: return
    val opts = optionTexts.mapIndexed { idx, txt -> PredictionOption(idx + 1, txt, 0, 0) }
    val poll = SocialPrediction(
      id = "pred_${System.currentTimeMillis()}",
      title = title,
      category = category,
      closingTimeText = if (durationHours <= 24) "Closes in ${durationHours}h" else "Closes in ${durationHours / 24}d",
      durationHours = durationHours,
      totalVotes = 0,
      options = opts,
      authorId = user.id,
      authorUsername = user.username
    )
    _predictions.value = listOf(poll) + _predictions.value
  }

  fun deletePredictionPoll(pollId: String) {
    _predictions.value = _predictions.value.filterNot { it.id == pollId }
  }

  fun createTimeCapsule(title: String, message: String, recipient: String, unlockDate: String) {
    val user = _currentUser.value ?: return
    val capsule = TimeCapsule(
      id = "tc_${System.currentTimeMillis()}",
      title = title,
      sender = user,
      recipientSummary = recipient,
      sealedMessage = message,
      unlockDateFormatted = unlockDate,
      unlockTimestampMs = System.currentTimeMillis() + 86_400_000L * 30,
      isUnlocked = false,
      createdAtFormatted = "Today"
    )
    _timeCapsules.value = listOf(capsule) + _timeCapsules.value
  }

  fun unlockTimeCapsule(capsuleId: String) {
    _timeCapsules.value = _timeCapsules.value.map { if (it.id == capsuleId) it.copy(isUnlocked = true) else it }
  }

  fun respondToCapsuleInvitation(capsuleId: String, accept: Boolean) {
    _timeCapsules.value = _timeCapsules.value.map {
      if (it.id == capsuleId) it.copy(myInvitationStatus = if (accept) CapsuleInvitationStatus.ACCEPTED else CapsuleInvitationStatus.DECLINED) else it
    }
  }

  fun addShopReview(shopId: String, rating: Int, reviewText: String) {
    val user = _currentUser.value ?: return
    _shops.value = _shops.value.map { shop ->
      if (shop.id == shopId) {
        val review = ShopReview("sr_${System.currentTimeMillis()}", user, rating, reviewText, "Just now", true)
        shop.copy(reviews = listOf(review) + shop.reviews, reviewsCount = shop.reviewsCount + 1)
      } else shop
    }
  }

  fun submitBusinessApplication(
    businessName: String,
    category: String,
    gstOrReg: String,
    email: String,
    phone: String,
    address: String
  ): BusinessApplication {
    val user = _currentUser.value
    val app = BusinessApplication(
      id = "ba_${System.currentTimeMillis()}",
      ownerId = user?.id ?: "self",
      businessName = businessName,
      category = category,
      registrationNumberOrGst = gstOrReg,
      contactEmail = email,
      contactPhone = phone,
      address = address,
      status = BusinessVerificationStatus.PENDING,
      submittedAt = "Today"
    )
    _businessApplications.value = listOf(app) + _businessApplications.value
    return app
  }

  fun reviewBusinessApplication(applicationId: String, status: BusinessVerificationStatus, notes: String) {
    _businessApplications.value = _businessApplications.value.map {
      if (it.id == applicationId) it.copy(status = status, reviewerNotes = notes) else it
    }
  }

  fun activateBusinessShop(shopId: String) {
    _shops.value = _shops.value.map { if (it.id == shopId) it.copy(isShopActive = true) else it }
  }

  fun submitAdminReportAction(reportId: String, action: ReportStatus) {
    _adminReports.value = _adminReports.value.map { if (it.id == reportId) it.copy(status = action) else it }
  }

  fun sendCrossedPathRequest(encounterId: String) {
    _crossedPaths.value = _crossedPaths.value.map {
      if (it.id == encounterId) it.copy(hasSentRequest = true, requestStatus = ConnectionRequestStatus.PENDING) else it
    }
  }

  fun createCrossedPathEncounter(
    type: CrossedPathType,
    venueOrEventName: String,
    broadLocation: String,
    description: String,
    photoUrl: String? = null
  ) {
    val me = _currentUser.value ?: return
    val cleanVenue = venueOrEventName.trim()
    val cleanBroad = broadLocation.trim().ifBlank { cleanVenue.ifBlank { "Community Area" } }
    val title = if (cleanVenue.isNotBlank()) "Noticed at $cleanVenue" else "Missed Connection in $cleanBroad"
    val encounter = CrossedPathEncounter(
      id = "cp_${System.currentTimeMillis()}",
      type = type,
      title = title,
      description = description.trim(),
      broadLocation = cleanBroad,
      venueOrEventName = cleanVenue.takeIf { it.isNotBlank() },
      encounterDate = "Just now",
      poster = me,
      isAnonymous = true,
      expiresHoursRemaining = 72,
      photoUrl = photoUrl,
      mapQueryLocation = cleanVenue.ifBlank { cleanBroad }
    )
    _crossedPaths.value = listOf(encounter) + _crossedPaths.value
  }

  fun setSpiritualMode(enabled: Boolean) {
    _isSpiritualMode.value = enabled
    _currentUser.value = _currentUser.value?.copy(isSpiritualMode = enabled)
  }

  fun toggleSpiritualMode() = setSpiritualMode(!_isSpiritualMode.value)

  fun addGratitudeEntry(entry: String) {
    _spiritualGratitudes.value = listOf(SpiritualGratitude("sg_${System.currentTimeMillis()}", entry.trim(), "Just now")) + _spiritualGratitudes.value
  }

  fun addIntention(text: String) {
    _spiritualIntentions.value = listOf(SpiritualIntention("si_${System.currentTimeMillis()}", text.trim(), "Today", false)) + _spiritualIntentions.value
  }

  fun toggleIntentionFulfilled(id: String) {
    _spiritualIntentions.value = _spiritualIntentions.value.map { if (it.id == id) it.copy(isFulfilled = !it.isFulfilled) else it }
  }

  fun updateTimeManagement(breakMinutes: Int, isEnabled: Boolean) {
    _timeManagementStats.value = _timeManagementStats.value.copy(breakReminderMinutes = breakMinutes, isBreakReminderEnabled = isEnabled)
  }

  fun submitSupportTicket(category: String, subject: String, message: String): SupportTicket {
    val ticket = SupportTicket(
      id = "tk_${System.currentTimeMillis() % 10000}",
      category = category,
      subject = subject,
      message = message,
      status = "Open",
      createdAt = "Just now",
      agentResponse = "Thank you for contacting VYBE Support. Our team will review your request shortly."
    )
    _supportTickets.value = listOf(ticket) + _supportTickets.value
    return ticket
  }

  fun logoutDevice(sessionId: String) {
    _activeDeviceSessions.value = _activeDeviceSessions.value.filter { it.id != sessionId }
  }

  fun updateNotificationPreferences(prefs: NotificationPreferences) {
    _notificationPreferences.value = prefs
  }
}
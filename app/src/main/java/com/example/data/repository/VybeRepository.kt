package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.VybeLocalStore
import com.example.data.model.*
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseExploreAndLiveRemote
import com.example.data.remote.SupabaseRepository
import com.example.data.remote.SupabaseRoomsAndCallsRemote
import com.example.data.session.SessionManager
import com.example.util.MediaUtils
import com.example.util.NetworkMonitor
import com.example.util.TimestampUtils
import com.example.util.VybeCallEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
    val isUnfiltered: Boolean = false,
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
  private var callEngine: VybeCallEngine? = null
  private var presenceLoopJob: Job? = null
  private var callSignalingPollJob: Job? = null
  private var callDurationJob: Job? = null
  private var liveStreamSyncJob: Job? = null
  private val loggedCallIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

  private val feedPageSize = 30

  val isSupabaseConfigured: Boolean
    get() = supabaseRepo.isConfigured

  // --- Connectivity & Upload Progress State ---
  private val _isOnline = MutableStateFlow(true)
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val _uploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
  val uploadStatus: StateFlow<UploadStatus> = _uploadStatus.asStateFlow()

  private var lastFailedUpload: PendingUploadOperation? = null
  private var isUploadInFlight = false

  // --- Password Recovery & Dual OTP Verification State ---
  private val _passwordRecoveryState = MutableStateFlow<PasswordRecoveryState>(PasswordRecoveryState.Inactive)
  val passwordRecoveryState: StateFlow<PasswordRecoveryState> = _passwordRecoveryState.asStateFlow()

  private val _lastRecoveredEmailHint = MutableStateFlow("")
  val lastRecoveredEmailHint: StateFlow<String> = _lastRecoveredEmailHint.asStateFlow()

  private val _pendingVerificationEmail = MutableStateFlow("")
  val pendingVerificationEmail: StateFlow<String> = _pendingVerificationEmail.asStateFlow()

  private val _pendingVerificationPhone = MutableStateFlow("")
  val pendingVerificationPhone: StateFlow<String> = _pendingVerificationPhone.asStateFlow()

  private val _isEmailOtpVerified = MutableStateFlow(false)
  val isEmailOtpVerified: StateFlow<Boolean> = _isEmailOtpVerified.asStateFlow()

  private val _isPhoneOtpVerified = MutableStateFlow(false)
  val isPhoneOtpVerified: StateFlow<Boolean> = _isPhoneOtpVerified.asStateFlow()

  private var pendingSessionPayload: com.example.data.remote.AuthSessionPayload? = null

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

  // --- Feed, Pagination, Unfiltered & Stories ---
  private val _posts = MutableStateFlow<List<Post>>(emptyList())
  val posts: StateFlow<List<Post>> = _posts.asStateFlow()

  private val _isSyncingFeed = MutableStateFlow(false)
  val isSyncingFeed: StateFlow<Boolean> = _isSyncingFeed.asStateFlow()

  private val _hasMoreFeedPosts = MutableStateFlow(false)
  val hasMoreFeedPosts: StateFlow<Boolean> = _hasMoreFeedPosts.asStateFlow()

  private val _isLoadingMorePosts = MutableStateFlow(false)
  val isLoadingMorePosts: StateFlow<Boolean> = _isLoadingMorePosts.asStateFlow()

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

  private val _communities = MutableStateFlow<List<Community>>(emptyList())
  val communities: StateFlow<List<Community>> = _communities.asStateFlow()

  private val _activeCommunity = MutableStateFlow<Community?>(null)
  val activeCommunity: StateFlow<Community?> = _activeCommunity.asStateFlow()

  private val _communityPosts = MutableStateFlow<List<CommunityPost>>(emptyList())
  val communityPosts: StateFlow<List<CommunityPost>> = _communityPosts.asStateFlow()

  private val _communityMembers = MutableStateFlow<List<CommunityMember>>(emptyList())
  val communityMembers: StateFlow<List<CommunityMember>> = _communityMembers.asStateFlow()

  private val _communityComments = MutableStateFlow<Map<String, List<CommunityComment>>>(emptyMap())
  val communityComments: StateFlow<Map<String, List<CommunityComment>>> = _communityComments.asStateFlow()

  private val deletedMessageIdsCache: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

  private val _liveStreams = MutableStateFlow<List<LiveStreamSession>>(emptyList())
  val liveStreams: StateFlow<List<LiveStreamSession>> = _liveStreams.asStateFlow()

  private val _activeLiveStream = MutableStateFlow<LiveStreamSession?>(null)
  val activeLiveStream: StateFlow<LiveStreamSession?> = _activeLiveStream.asStateFlow()

  private val _liveStreamComments = MutableStateFlow<List<LiveStreamComment>>(emptyList())
  val liveStreamComments: StateFlow<List<LiveStreamComment>> = _liveStreamComments.asStateFlow()

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
    // Zero sample/mock rooms, users, posts, stories, polls, or streams
    _stories.value = emptyList()
    _posts.value = emptyList()
    _unfilteredPosts.value = emptyList()
    _conversations.value = emptyList()
    _notifications.value = emptyList()
    _vybeMatches.value = emptyList()
    _unloadRooms.value = emptyList()
    _liveStreams.value = emptyList()
    _predictions.value = emptyList()
    _doubleTakePrompts.value = emptyList()
    _timeCapsules.value = emptyList()
    _crossedPaths.value = emptyList()
    _shops.value = emptyList()
    _communities.value = emptyList()
    _activeCommunity.value = null
    _communityPosts.value = emptyList()
    _communityMembers.value = emptyList()
    _communityComments.value = emptyMap()

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
    _timeManagementStats.value = localStore.getWeeklyUsageStats()

    // Real session active screen-time tracking
    ioScope.launch {
      while (true) {
        kotlinx.coroutines.delay(60_000L)
        localStore.addUsageMinutes(1)
        _timeManagementStats.value = localStore.getWeeklyUsageStats()
      }
    }

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

        deletedMessageIdsCache.addAll(localStore.loadDeletedMessageIds())
        _posts.value = localStore.loadPosts()
        _unfilteredPosts.value = localStore.loadUnfilteredPosts()
        _conversations.value = dedupeConversationCallLogs(localStore.loadConversations())
        _notifications.value = enrichNotificationsWithLocalContext(localStore.loadNotifications())
        _followingUserIds.value = localStore.loadFollowing()
        rebuildVybeMatchesList()
        startPresenceAndIncomingCallLoops()
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

      // 5. Sync Shared Feed Posts (available immediately to new & returning users)
      syncFeedFromSupabase(validToken)

      // 6. Sync Stories & Viewed State (public stories visible even to newly created users)
      syncStoriesFromSupabase(myId, validToken)

      // 7. Sync Persistent Live Rooms, Live Zone & Explore Zone across all users
      syncRoomsFromSupabase(validToken)
      syncExploreAndLiveFromSupabase(validToken)

      // 8. Sync Conversations & Notifications for authenticated user
      if (myId.isNotBlank() && validToken.isNotBlank()) {
        val convRes = supabaseRepo.fetchConversationsForUser(
          myUserId = myId,
          blockedIds = _blockedUserIds.value,
          restrictedIds = _restrictedUserIds.value,
          accessToken = validToken
        )
        if (convRes.isSuccess) {
          val remoteConvs = dedupeConversationCallLogs(convRes.getOrThrow())
          _conversations.value = remoteConvs
          VybeLocalStore.get()?.saveConversations(remoteConvs)
        }

        val notifRes = supabaseRepo.fetchNotifications(myId, validToken)
        if (notifRes.isSuccess) {
          val enriched = enrichNotificationsWithLocalContext(notifRes.getOrThrow())
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
    val res = supabaseRepo.fetchPosts(
      currentUserId = myId,
      accessToken = token,
      offset = 0,
      limit = feedPageSize
    )
    if (res.isSuccess) {
      val rawFetched = res.getOrThrow()
      _hasMoreFeedPosts.value = rawFetched.size >= feedPageSize

      val usersMap = _allUsers.value.associateBy { it.id }
      val visiblePosts = rawFetched
        .distinctBy { it.id }
        .map { p ->
          val fullAuthor = usersMap[p.author.id]?.let { decorateUserRelationshipFlags(it) } ?: p.author
          p.copy(author = fullAuthor)
        }
        .filter { p -> canViewUserContent(p.author) }

      _posts.value = visiblePosts
      VybeLocalStore.get()?.savePosts(visiblePosts)

      val remoteUnfiltered = visiblePosts.filter { it.isUnfiltered }
      if (remoteUnfiltered.isNotEmpty() || _unfilteredPosts.value.none { it.id.startsWith("uf_") }) {
        _unfilteredPosts.value = remoteUnfiltered
        VybeLocalStore.get()?.saveUnfilteredPosts(remoteUnfiltered)
      }

      // Reconcile exact persisted postsCount (including 0 when user has 0 persisted posts!)
      reconcilePersistedPostCounts(visiblePosts, isCompleteList = !_hasMoreFeedPosts.value)
    }
  }

  suspend fun loadMoreFeedPosts() {
    if (!supabaseRepo.isConfigured || !_hasMoreFeedPosts.value || _isLoadingMorePosts.value || _isSyncingFeed.value) {
      return
    }
    _isLoadingMorePosts.value = true
    try {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      val myId = _currentUser.value?.id
      val currentSize = _posts.value.size
      val res = supabaseRepo.fetchPosts(
        currentUserId = myId,
        accessToken = token,
        offset = currentSize,
        limit = feedPageSize
      )
      if (res.isSuccess) {
        val nextBatch = res.getOrThrow()
        _hasMoreFeedPosts.value = nextBatch.size >= feedPageSize
        if (nextBatch.isNotEmpty()) {
          val usersMap = _allUsers.value.associateBy { it.id }
          val enrichedBatch = nextBatch
            .map { p ->
              val fullAuthor = usersMap[p.author.id]?.let { decorateUserRelationshipFlags(it) } ?: p.author
              p.copy(author = fullAuthor)
            }
            .filter { p -> canViewUserContent(p.author) }

          val merged = (_posts.value + enrichedBatch).distinctBy { it.id }
          _posts.value = merged
          VybeLocalStore.get()?.savePosts(merged)
          reconcilePersistedPostCounts(merged, isCompleteList = !_hasMoreFeedPosts.value)
        }
      }
    } finally {
      _isLoadingMorePosts.value = false
    }
  }

  private fun reconcilePersistedPostCounts(loadedPosts: List<Post>, isCompleteList: Boolean) {
    val myId = _currentUser.value?.id
    if (!myId.isNullOrBlank()) {
      val myActualPosts = loadedPosts.count { it.author.id == myId && !it.isArchived }
      val me = _currentUser.value
      if (me != null) {
        val authoritativeCount = if (isCompleteList) myActualPosts else maxOf(myActualPosts, me.postsCount)
        if (authoritativeCount != me.postsCount) {
          val syncedMe = me.copy(postsCount = authoritativeCount)
          _currentUser.value = syncedMe
          _allUsers.value = _allUsers.value.map { if (it.id == syncedMe.id) decorateUserRelationshipFlags(syncedMe) else it }
          SessionManager.get()?.updateCurrentUser(syncedMe)
          VybeLocalStore.get()?.saveUser(syncedMe)
        }
      }
    }
  }

  suspend fun syncStoriesFromSupabase(myUserId: String, explicitToken: String? = null) {
    if (!supabaseRepo.isConfigured) return
    val token = explicitToken ?: supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
    val res = supabaseRepo.fetchActiveStories(myUserId, token)
    if (res.isSuccess) {
      val usersMap = _allUsers.value.associateBy { it.id }
      val localViewed = if (myUserId.isNotBlank()) VybeLocalStore.get()?.loadViewedStoryIds(myUserId).orEmpty() else emptySet()
      viewedStoryIdsLocal = viewedStoryIdsLocal + localViewed

      val filteredAndSorted = res.getOrThrow()
        .distinctBy { it.id }
        .map { s ->
          val fullAuthor = usersMap[s.author.id]?.let { decorateUserRelationshipFlags(it) } ?: s.author
          val viewed = s.isViewed || viewedStoryIdsLocal.contains(s.id)
          s.copy(author = fullAuthor, isViewed = viewed)
        }
        .filter { s ->
          if (myUserId.isNotBlank() && s.author.id == myUserId) {
            true
          } else if (_blockedUserIds.value.contains(s.author.id)) {
            false
          } else if (s.audience == StoryAudience.VYBE_MATCHES || s.audience == StoryAudience.CLOSE_FRIENDS) {
            myUserId.isNotBlank() && (s.author.id == myUserId || vybeMatchIdsCache.contains(s.author.id) || _followerUserIds.value.contains(s.author.id))
          } else if (s.author.isPrivate && !_followingUserIds.value.contains(s.author.id) && !_followerUserIds.value.contains(s.author.id)) {
            false
          } else {
            _followingUserIds.value.contains(s.author.id) || _followerUserIds.value.contains(s.author.id) || !s.author.isPrivate
          }
        }
        .sortedWith(
          compareBy<Story> { it.isViewed && it.author.id != myUserId }
            .thenByDescending { myUserId.isNotBlank() && it.author.id == myUserId }
            .thenByDescending { TimestampUtils.parseIsoMillis(it.createdAtIso) ?: 0L }
        )

      _stories.value = filteredAndSorted
      if (myUserId.isNotBlank()) {
        val allMarkedViewed = filteredAndSorted.filter { it.isViewed }.map { it.id }.toSet()
        if (allMarkedViewed.isNotEmpty()) {
          viewedStoryIdsLocal = viewedStoryIdsLocal + allMarkedViewed
          VybeLocalStore.get()?.saveViewedStoryIds(myUserId, viewedStoryIdsLocal)
        }
      }
    }
  }

  suspend fun syncRoomsFromSupabase(explicitToken: String? = null) {
    if (!supabaseRepo.isConfigured) return
    val token = explicitToken ?: supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
    val authHeader = SupabaseConfig.buildAuthHeader(token)
    val myId = _currentUser.value?.id.orEmpty()
    val res = SupabaseRoomsAndCallsRemote.fetchActiveRooms(myId, authHeader)
    if (res.isSuccess) {
      val remoteRooms = res.getOrThrow()
      val inFlightLocal = _unloadRooms.value.filter {
        it.id.startsWith("room_") && remoteRooms.none { r -> r.roomCode.equals(it.roomCode, ignoreCase = true) }
      }
      val merged = (remoteRooms + inFlightLocal).distinctBy { it.roomCode.ifBlank { it.id } }
      _unloadRooms.value = merged
      val active = _activeUnloadRoom.value
      if (active != null) {
        val refreshed = merged.firstOrNull { it.id == active.id || (it.roomCode.isNotBlank() && it.roomCode == active.roomCode) }
        _activeUnloadRoom.value = refreshed
      }
    }
  }

  suspend fun syncExploreAndLiveFromSupabase(explicitToken: String? = null) {
    if (!supabaseRepo.isConfigured) return
    val token = explicitToken ?: supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
    val authHeader = SupabaseConfig.buildAuthHeader(token)
    val myId = _currentUser.value?.id.orEmpty()
    val usersMap = _allUsers.value.associateBy { it.id }

    val streams = SupabaseExploreAndLiveRemote.fetchActiveLiveStreams(usersMap, authHeader)
    _liveStreams.value = streams
    val curStream = _activeLiveStream.value
    if (curStream != null && !curStream.id.startsWith("local_")) {
      streams.firstOrNull { it.id == curStream.id }?.let { _activeLiveStream.value = it }
    }

    SupabaseExploreAndLiveRemote.fetchAudiencePools(myId, usersMap, authHeader)?.let {
      _predictions.value = it
    }
    SupabaseExploreAndLiveRemote.fetchDoubleTakes(myId, usersMap, authHeader)?.let {
      _doubleTakePrompts.value = it
    }
    SupabaseExploreAndLiveRemote.fetchTimeCapsules(usersMap, authHeader)?.let {
      _timeCapsules.value = it
    }
    SupabaseExploreAndLiveRemote.fetchCrossedPaths(myId, usersMap, authHeader)?.let {
      _crossedPaths.value = it
    }
    SupabaseExploreAndLiveRemote.fetchBusinessShops(usersMap, authHeader)?.let {
      _shops.value = it
    }
    SupabaseExploreAndLiveRemote.fetchCommunities(myId, authHeader)?.let { remoteComms ->
      _communities.value = remoteComms
      val curActive = _activeCommunity.value
      if (curActive != null) {
        remoteComms.firstOrNull { it.id == curActive.id }?.let { freshActive ->
          _activeCommunity.value = freshActive
          SupabaseExploreAndLiveRemote.fetchCommunityPosts(freshActive.id, myId, usersMap, authHeader)?.let { posts ->
            _communityPosts.value = posts
          }
        }
      }
    }
  }

  fun enrichNotificationsWithLocalContext(items: List<NotificationItem>): List<NotificationItem> {
    val usersById = _allUsers.value.associateBy { it.id }
    val postsById = _posts.value.associateBy { it.id }
    return items.map { item ->
      val resolvedActor = item.actorUser?.id?.let { aid ->
        usersById[aid] ?: if (_currentUser.value?.id == aid) _currentUser.value else item.actorUser
      } ?: item.actorUser

      val targetPost = item.targetEntityId?.let { postsById[it] }
      val isReel = (targetPost?.isVybesReel == true || targetPost?.mediaType == MediaType.REEL) || item.isReel
      val previewUrl = item.contentPreviewUrl?.takeIf { it.isNotBlank() }
        ?: targetPost?.mediaUrls?.firstOrNull()?.takeIf { it.isNotBlank() }
      val snippet = item.contentSnippet?.takeIf { it.isNotBlank() }
        ?: targetPost?.caption?.trim()?.takeIf { it.isNotEmpty() }

      val dest = when (item.type) {
        NotificationType.LIKE,
        NotificationType.COMMENT_OR_REPLY -> if (isReel) "REELS_VIEWER" else "POST_DETAIL"
        NotificationType.FOLLOW -> "USER_PROFILE"
        NotificationType.DIRECT_MESSAGE,
        NotificationType.GROUP_MESSAGE -> "INBOX"
        else -> item.destinationScreen ?: "MAIN"
      }

      val resolvedTitle = when {
        item.type == NotificationType.LIKE && resolvedActor != null && resolvedActor.username != "vyber" -> {
          "@${resolvedActor.username} liked your ${if (isReel) "Reel" else "post"}"
        }
        item.type == NotificationType.COMMENT_OR_REPLY && resolvedActor != null && resolvedActor.username != "vyber" -> {
          "@${resolvedActor.username} commented on your ${if (isReel) "Reel" else "post"}"
        }
        else -> item.title
      }

      item.copy(
        title = resolvedTitle,
        actorUser = resolvedActor,
        destinationScreen = dest,
        contentPreviewUrl = previewUrl,
        contentSnippet = snippet,
        isReel = isReel
      )
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

  suspend fun publishPostAwait(
    caption: String,
    mood: MoodType?,
    mediaType: MediaType = MediaType.TEXT,
    imageUrl: String? = null,
    isVybes: Boolean = false,
    isUnfiltered: Boolean = false,
    locationName: String? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    audioTrackTitle: String? = null,
    preferredAspectRatio: Float? = null
  ): Result<Post> {
    if (isUploadInFlight) {
      return Result.failure(IllegalStateException("An upload is already in progress."))
    }
    val job = PendingUploadOperation.PostUpload(
      clientUploadId = UUID.randomUUID().toString(),
      caption = caption.trim(),
      mood = mood,
      mediaType = mediaType,
      localOrRemoteMediaUri = imageUrl?.takeIf { it.isNotBlank() },
      isVybes = isVybes,
      isUnfiltered = isUnfiltered,
      locationName = locationName?.takeIf { it.isNotBlank() },
      latitude = latitude,
      longitude = longitude,
      audioTrackTitle = audioTrackTitle?.takeIf { it.isNotBlank() },
      preferredAspectRatio = preferredAspectRatio
    )
    return executePostUpload(job)
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
    if (isUploadInFlight) return
    val job = PendingUploadOperation.PostUpload(
      clientUploadId = UUID.randomUUID().toString(),
      caption = caption.trim(),
      mood = mood,
      mediaType = mediaType,
      localOrRemoteMediaUri = imageUrl?.takeIf { it.isNotBlank() },
      isVybes = isVybes,
      isUnfiltered = false,
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

  private suspend fun executePostUpload(op: PendingUploadOperation.PostUpload): Result<Post> {
    val user = _currentUser.value
    if (user == null) {
      val msg = "Please sign in before publishing."
      _uploadStatus.value = UploadStatus.Failed(msg, canRetry = false)
      return Result.failure(IllegalStateException(msg))
    }

    val ctx = appContext
    if (supabaseRepo.isConfigured && ctx != null && !NetworkMonitor.checkCurrentlyOnline(ctx)) {
      lastFailedUpload = op
      val msg = "No internet connection. Your post media is preserved — tap Retry once reconnected."
      _uploadStatus.value = UploadStatus.Failed(msg, canRetry = true)
      return Result.failure(IllegalStateException(msg))
    }

    isUploadInFlight = true
    return try {
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
        if (op.caption.isBlank()) {
          throw IllegalArgumentException("Write a caption or select media before publishing.")
        }
        resolvedMediaType = MediaType.TEXT
      }

      if (op.isVybes && finalMediaUrls.isNotEmpty()) {
        resolvedMediaType = MediaType.REEL
      }

      _uploadStatus.value = UploadStatus.Processing("Publishing post...")
      val nowIso = TimestampUtils.nowIsoUtc()

      val draftPost = Post(
        id = "p_${op.clientUploadId}",
        author = user,
        caption = op.caption,
        mediaUrls = finalMediaUrls,
        mediaType = resolvedMediaType,
        timestamp = "Just now",
        createdAtIso = nowIso,
        isUnfiltered = op.isUnfiltered,
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
          // Clean up orphaned Storage object if DB row creation failed
          finalStoragePaths.forEach { sp ->
            supabaseRepo.deleteStorageObject(sp, token)
          }
          throw err
        }
      } else {
        draftPost
      }

      // Only add to feed & calculate persisted count AFTER Storage upload AND DB insert succeeded!
      val deduplicatedList = (listOf(publishedPost) + _posts.value.filterNot {
        it.id == publishedPost.id || (!it.clientUploadId.isNullOrBlank() && it.clientUploadId == op.clientUploadId)
      }).distinctBy { it.id }
      _posts.value = deduplicatedList
      VybeLocalStore.get()?.savePosts(deduplicatedList)

      if (publishedPost.isUnfiltered) {
        val nextUnfiltered = (listOf(publishedPost) + _unfilteredPosts.value.filterNot { it.id == publishedPost.id }).distinctBy { it.id }
        _unfilteredPosts.value = nextUnfiltered
        VybeLocalStore.get()?.saveUnfilteredPosts(nextUnfiltered)
      }

      val persistedMyPostsCount = deduplicatedList.count { it.author.id == user.id && !it.isArchived }
      val updatedUser = user.copy(postsCount = persistedMyPostsCount)
      _currentUser.value = updatedUser
      _allUsers.value = _allUsers.value.map { if (it.id == updatedUser.id) decorateUserRelationshipFlags(updatedUser) else it }
      SessionManager.get()?.updateCurrentUser(updatedUser)
      VybeLocalStore.get()?.saveUser(updatedUser)

      lastFailedUpload = null
      if (ctx != null && !rawUri.isNullOrBlank() && supabaseRepo.isConfigured) {
        MediaUtils.cleanupStagedFileIfOwned(ctx, rawUri)
      }
      _uploadStatus.value = UploadStatus.Published(
        when {
          publishedPost.isVybesReel -> "Reel published successfully!"
          publishedPost.isUnfiltered -> "Unfiltered moment published!"
          else -> "Post published successfully!"
        }
      )

      // Revalidate feed from Supabase so author counts, signed URLs, and ordering are 100% authoritative
      if (supabaseRepo.isConfigured) {
        syncFeedFromSupabase(token)
      }
      Result.success(publishedPost)
    } catch (e: Exception) {
      lastFailedUpload = op
      val errMsg = e.message ?: "Upload failed. Your media is preserved — tap Retry."
      _uploadStatus.value = UploadStatus.Failed(
        errorMessage = errMsg,
        canRetry = true
      )
      Result.failure(e)
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

  suspend fun publishStoryAwait(
    mediaUrl: String,
    caption: String,
    mediaType: StoryMediaType = StoryMediaType.PHOTO,
    audience: StoryAudience = StoryAudience.EVERYONE,
    musicTrackTitle: String? = null,
    location: String? = null,
    textBackgroundColor: String? = null
  ): Result<Story> {
    if (isUploadInFlight) {
      return Result.failure(IllegalStateException("Another upload is currently in progress."))
    }
    if ((audience == StoryAudience.VYBE_MATCHES || audience == StoryAudience.CLOSE_FRIENDS) && _vybeMatches.value.isEmpty()) {
      val msg = "No VYBE Matches selected. Add at least one person to VYBE Matches before sharing a restricted story."
      _uploadStatus.value = UploadStatus.Failed(msg, canRetry = false)
      return Result.failure(IllegalArgumentException(msg))
    }
    if (mediaType != StoryMediaType.TEXT && mediaUrl.isBlank()) {
      val msg = "Select a photo or video before publishing your story."
      _uploadStatus.value = UploadStatus.Failed(msg, canRetry = false)
      return Result.failure(IllegalArgumentException(msg))
    }
    if (mediaType == StoryMediaType.TEXT && caption.trim().isBlank()) {
      val msg = "Type your story message before sharing."
      _uploadStatus.value = UploadStatus.Failed(msg, canRetry = false)
      return Result.failure(IllegalArgumentException(msg))
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
    return executeStoryUpload(op)
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

  private suspend fun executeStoryUpload(op: PendingUploadOperation.StoryUpload): Result<Story> {
    val user = _currentUser.value
    if (user == null) {
      val msg = "Please sign in to publish a story."
      _uploadStatus.value = UploadStatus.Failed(msg, canRetry = false)
      return Result.failure(IllegalStateException(msg))
    }

    val ctx = appContext
    if (supabaseRepo.isConfigured && ctx != null && !NetworkMonitor.checkCurrentlyOnline(ctx)) {
      lastFailedUpload = op
      val msg = "No internet connection. Your story is preserved — tap Retry once reconnected."
      _uploadStatus.value = UploadStatus.Failed(msg, canRetry = true)
      return Result.failure(IllegalStateException(msg))
    }

    isUploadInFlight = true
    return try {
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

        if (payload.bytes.isEmpty()) {
          throw IllegalStateException("Selected media file is empty or corrupted.")
        }
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
      val nowIso = TimestampUtils.nowIsoUtc()

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
        createdAtIso = nowIso,
        expiresHoursRemaining = 24,
        isViewed = false,
        storagePath = resolvedStoragePath,
        clientUploadId = op.clientUploadId
      )

      val publishedStory = if (supabaseRepo.isConfigured) {
        supabaseRepo.createStoryOnSupabase(draftStory, token).getOrElse { err ->
          if (!resolvedStoragePath.isNullOrBlank()) {
            supabaseRepo.deleteStorageObject(resolvedStoragePath, token)
          }
          throw err
        }
      } else {
        draftStory
      }

      val nextStories = (listOf(publishedStory) + _stories.value.filterNot {
        it.id == publishedStory.id || (!it.clientUploadId.isNullOrBlank() && it.clientUploadId == op.clientUploadId)
      }).distinctBy { it.id }
        .sortedWith(
          compareBy<Story> { it.isViewed && it.author.id != user.id }
            .thenByDescending { it.author.id == user.id }
        )
      _stories.value = nextStories

      lastFailedUpload = null
      if (ctx != null && needsBinaryUpload && supabaseRepo.isConfigured) {
        MediaUtils.cleanupStagedFileIfOwned(ctx, op.localOrRemoteMediaUri)
      }
      _uploadStatus.value = UploadStatus.Published("Story published!")

      if (supabaseRepo.isConfigured) {
        syncStoriesFromSupabase(user.id, token)
      }
      Result.success(publishedStory)
    } catch (e: Exception) {
      lastFailedUpload = op
      val errMsg = e.message ?: "Story upload failed. Tap Retry."
      _uploadStatus.value = UploadStatus.Failed(
        errorMessage = errMsg,
        canRetry = true
      )
      Result.failure(e)
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
      if (readyState.email.isNotBlank()) {
        _lastRecoveredEmailHint.value = readyState.email
      }
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
    val current = _posts.value
    val post = current.firstOrNull { it.id == postId } ?: return
    val targetReaction = post.userReaction ?: VybeReaction.PULSE
    reactToPost(postId, targetReaction)
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
            val contentKind = if (post.isVybesReel || post.mediaType == MediaType.REEL) "Reel" else "post"
            val snippetPart = post.caption.trim().take(48).let { if (it.isNotEmpty()) ": \"$it\"" else "" }
            supabaseRepo.createInteractionNotificationIfNeeded(
              recipientId = post.author.id,
              actorId = me.id,
              type = "LIKE",
              title = "@${me.username} liked your $contentKind",
              message = "${me.displayName} reacted ${appliedReaction.label} to your $contentKind$snippetPart",
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
      VybeLocalStore.get()?.savePosts(current)
      reconcilePersistedPostCounts(current, isCompleteList = !_hasMoreFeedPosts.value)
      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          supabaseRepo.setPostArchived(postId, true, SessionManager.get()?.accessToken.orEmpty())
        }
      }
    }
  }

  fun restoreArchivedPost(postId: String) {
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      current[index] = current[index].copy(isArchived = false)
      _posts.value = current
      VybeLocalStore.get()?.savePosts(current)
      reconcilePersistedPostCounts(current, isCompleteList = !_hasMoreFeedPosts.value)
      if (supabaseRepo.isConfigured) {
        ioScope.launch {
          supabaseRepo.setPostArchived(postId, false, SessionManager.get()?.accessToken.orEmpty())
        }
      }
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
    if (supabaseRepo.isConfigured) {
      val job = PendingUploadOperation.PostUpload(
        clientUploadId = UUID.randomUUID().toString(),
        caption = caption.trim(),
        mood = null,
        mediaType = MediaType.IMAGE,
        localOrRemoteMediaUri = imageUrl.takeIf { it.isNotBlank() },
        isVybes = false,
        isUnfiltered = true,
        locationName = null,
        latitude = null,
        longitude = null,
        audioTrackTitle = null,
        preferredAspectRatio = 0.75f
      )
      ioScope.launch {
        executePostUpload(job)
      }
    } else {
      val nowIso = TimestampUtils.nowIsoUtc()
      val newPost = Post(
        id = "uf_${System.currentTimeMillis()}",
        author = user,
        caption = caption,
        mediaUrls = listOf(imageUrl),
        mediaType = MediaType.IMAGE,
        timestamp = "Just now",
        createdAtIso = nowIso,
        isUnfiltered = true,
        commentsCount = 0
      )
      val updatedUnfiltered = listOf(newPost) + _unfilteredPosts.value
      _unfilteredPosts.value = updatedUnfiltered
      VybeLocalStore.get()?.saveUnfilteredPosts(updatedUnfiltered)
    }
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
          username = cleanUsername,
          email = email,
          phone = phone,
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

          // Upload local chat media if present (skipping http(s) URLs and vybe:// call-log metadata URIs)
          var resolvedMediaUrl = mediaUrl
          if (ctx != null && !mediaUrl.isNullOrBlank() && !mediaUrl.startsWith("http", true) && !mediaUrl.startsWith("vybe://", true)) {
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
          val inFlightMsgs = conv.messages.filter {
            (it.status == MessageStatus.SENDING || it.status == MessageStatus.FAILED) &&
              !deletedMessageIdsCache.contains(it.id)
          }
          val mappedRemote = remoteList.mapNotNull { dto ->
            val dtoId = dto.id ?: return@mapNotNull null
            if (dto.isDeletedForEveryone == true || deletedMessageIdsCache.contains(dtoId)) {
              return@mapNotNull null
            }
            DirectMessage(
              id = dtoId,
              senderId = dto.senderId,
              text = dto.content,
              timestamp = TimestampUtils.formatMessageTime(dto.createdAt, "Now"),
              createdAtIso = dto.createdAt,
              isMe = dto.senderId == user.id,
              mediaUrl = dto.mediaUrl,
              mediaType = dto.mediaType ?: "TEXT",
              isDeletedForEveryone = false,
              replyToId = dto.replyToMessageId,
              status = MessageStatus.SENT
            )
          }
          val remoteIds = mappedRemote.map { it.id }.toSet()
          val merged = mappedRemote + inFlightMsgs.filterNot { remoteIds.contains(it.id) }
          val lastRemoteIso = mappedRemote.lastOrNull()?.createdAtIso ?: conv.lastMessageCreatedAtIso
          current[convIdx] = conv.copy(
            messages = merged,
            lastMessage = merged.lastOrNull()?.text ?: "Conversation started",
            lastMessageCreatedAtIso = lastRemoteIso,
            lastMessageTimestamp = TimestampUtils.formatRelative(lastRemoteIso, conv.lastMessageTimestamp)
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
      val parts = birthDateStr.trim().split("-")
      if (parts.size == 3) {
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        if (y < 1900 || m !in 1..12 || d !in 1..31) {
          return Pair(false, "Please enter a valid calendar date (YYYY-MM-DD).")
        }
        val cal = java.util.Calendar.getInstance()
        val cy = cal.get(java.util.Calendar.YEAR)
        val cm = cal.get(java.util.Calendar.MONTH) + 1
        val cd = cal.get(java.util.Calendar.DAY_OF_MONTH)
        // Strict calendar validation (prevents Feb 30, etc.)
        val testCal = java.util.Calendar.getInstance().apply {
          isLenient = false
          set(y, m - 1, d)
        }
        testCal.timeInMillis // throws if invalid day for month

        var age = cy - y
        if (cm < m || (cm == m && cd < d)) age--
        when {
          age < 0 -> Pair(false, "Date of birth cannot be in the future.")
          age < 16 -> Pair(false, "VYBE is strictly for individuals aged 16 and above. Access cannot be granted.")
          age > 120 -> Pair(false, "Please enter a valid date of birth.")
          else -> Pair(true, "Eligible")
        }
      } else {
        Pair(false, "Invalid date format. Use YYYY-MM-DD.")
      }
    } catch (_: Exception) {
      Pair(false, "Invalid calendar date. Use YYYY-MM-DD.")
    }
  }

  suspend fun checkUsernameAvailabilityRemote(requestedUsername: String): Pair<Boolean, String> {
    val clean = requestedUsername.trim().lowercase().removePrefix("@")
    if (clean.length < 3) {
      return Pair(false, "Username must be at least 3 characters.")
    }
    if (!Regex("^[a-z0-9_.]{3,24}$").matches(clean)) {
      return Pair(false, "Use 3–24 lowercase letters, numbers, underscores, or periods.")
    }
    if (clean in setOf("null", "undefined", "admin", "vybe", "nrnext", "support")) {
      return Pair(false, "This username is reserved.")
    }
    val me = _currentUser.value
    if (me != null && me.username.equals(clean, ignoreCase = true)) {
      return Pair(true, "This is your current username.")
    }
    val available = if (supabaseRepo.isConfigured) {
      supabaseRepo.checkUsernameAvailable(clean)
    } else {
      _allUsers.value.none { it.username.equals(clean, ignoreCase = true) }
    }
    return if (available) {
      Pair(true, "@$clean is available")
    } else {
      Pair(false, "@$clean is already taken. Choose another.")
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
    val cleanFirst = firstName.trim()
    val cleanLast = lastName.trim()
    val cleanUsername = username.trim().lowercase().removePrefix("@")
    val cleanEmail = email.trim().lowercase()
    val cleanPhone = supabaseRepo.normalizePhoneE164(phone)

    if (cleanFirst.length < 2) {
      return Pair(false, "First name is required (at least 2 characters).")
    }
    if (cleanLast.isBlank()) {
      return Pair(false, "Last name is required.")
    }
    if (!Regex("^[a-z0-9_.]{3,24}$").matches(cleanUsername) || cleanUsername == "null") {
      return Pair(false, "Username must be 3–24 characters using letters, numbers, '_' or '.'.")
    }
    if (!cleanEmail.contains("@") || !cleanEmail.substringAfter("@").contains(".")) {
      return Pair(false, "Enter a valid email address.")
    }
    if (cleanPhone.filter { it.isDigit() }.length < 8) {
      return Pair(false, "Enter a valid phone number with country code (e.g. +14155550123).")
    }
    val ageCheck = checkAgeRequirement(birthDateStr)
    if (!ageCheck.first) return ageCheck
    if (password.length < 8) {
      return Pair(false, "Password must be at least 8 characters.")
    }

    if (supabaseRepo.isConfigured) {
      val usernameAvailable = supabaseRepo.checkUsernameAvailable(cleanUsername)
      if (!usernameAvailable) {
        return Pair(false, "Username @$cleanUsername is already taken. Please choose another.")
      }

      val res = supabaseRepo.signUpWithEmail(
        username = cleanUsername,
        email = cleanEmail,
        password = password,
        firstName = cleanFirst,
        lastName = cleanLast,
        phone = cleanPhone,
        dateOfBirth = birthDateStr.trim()
      )
      return if (res.isSuccess) {
        val payload = res.getOrThrow()
        pendingSessionPayload = payload
        _pendingVerificationEmail.value = payload.user.email
        _pendingVerificationPhone.value = payload.user.phone
        _isEmailOtpVerified.value = payload.user.isEmailVerified
        _isPhoneOtpVerified.value = payload.user.isPhoneVerified

        // Trigger SMS OTP if email is already verified and we have an access token, or prompt dual OTP step
        if (payload.accessToken.isNotEmpty() && payload.user.isEmailVerified && !payload.user.isPhoneVerified) {
          supabaseRepo.sendPhoneVerificationOtp(cleanPhone, payload.accessToken)
          Pair(
            true,
            "NEEDS_OTP: Email confirmed! Please verify your phone number ($cleanPhone) via SMS code to complete verification."
          )
        } else if (payload.accessToken.isNotEmpty() && payload.user.isEmailVerified && payload.user.isPhoneVerified) {
          activateAuthenticatedSession(payload.user, payload.accessToken, payload.refreshToken, payload.expiresInSeconds)
          Pair(true, "Account created & verified! Welcome to VYBE.")
        } else {
          if (payload.accessToken.isNotEmpty()) {
            supabaseRepo.sendPhoneVerificationOtp(cleanPhone, payload.accessToken)
          }
          Pair(
            true,
            "NEEDS_OTP: Verify your email ($cleanEmail) and phone number ($cleanPhone) to activate your VYBE account."
          )
        }
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Registration failed.")
      }
    } else {
      return registerAccount(cleanFirst, cleanLast, cleanUsername, cleanEmail, cleanPhone, birthDateStr.trim())
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
      val cleanUsername = username.trim().lowercase().removePrefix("@").replace(" ", "_")
      if (cleanUsername.isBlank() || cleanUsername == "null") {
        return Pair(false, "Valid username is required.")
      }
      val parts = birthDateStr.trim().split("-")
      val y = parts[0].toInt()
      val m = parts[1].toInt()
      val d = parts[2].toInt()
      val cal = java.util.Calendar.getInstance()
      var age = cal.get(java.util.Calendar.YEAR) - y
      if ((cal.get(java.util.Calendar.MONTH) + 1) < m ||
        ((cal.get(java.util.Calendar.MONTH) + 1) == m && cal.get(java.util.Calendar.DAY_OF_MONTH) < d)
      ) age--

      val nowIso = TimestampUtils.nowIsoUtc()
      val newUser = User(
        id = "u_${System.currentTimeMillis()}",
        username = cleanUsername,
        displayName = "$firstName $lastName".trim().ifBlank { cleanUsername },
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        email = email.trim().lowercase(),
        phone = phone.trim(),
        isEmailVerified = true,
        isPhoneVerified = phone.isNotBlank(),
        dateOfBirth = birthDateStr.trim(),
        age = age,
        isUnder18 = age in 16..17,
        role = UserRole.USER,
        createdAtIso = nowIso,
        joinedDate = TimestampUtils.formatJoinedMonthYear(nowIso),
        isOnline = true,
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
    return registerAccount(username, "Member", username, email, "+15550000000", birthDateStr)
  }

  private suspend fun activateAuthenticatedSession(
    user: User,
    accessToken: String,
    refreshToken: String,
    expiresInSeconds: Long
  ) {
    val previousUserId = SessionManager.get()?.userId
    if (!previousUserId.isNullOrBlank() && previousUserId != user.id) {
      VybeLocalStore.get()?.clearUserScopedCaches()
      _conversations.value = emptyList()
      _notifications.value = emptyList()
      _stories.value = emptyList()
      _vybeMatches.value = emptyList()
      _followingUserIds.value = emptySet()
    }

    val onlineUser = user.copy(isOnline = true)
    _currentUser.value = onlineUser
    _isLoggedIn.value = true
    pendingSessionPayload = null
    viewedStoryIdsLocal = VybeLocalStore.get()?.loadViewedStoryIds(onlineUser.id).orEmpty()
    vybeMatchIdsCache = VybeLocalStore.get()?.loadVybeMatches(onlineUser.id).orEmpty()

    SessionManager.get()?.saveSession(
      user = onlineUser,
      accessToken = accessToken,
      refreshToken = refreshToken,
      expiresInSeconds = expiresInSeconds
    )
    VybeLocalStore.get()?.saveUser(onlineUser)

    startPresenceAndIncomingCallLoops()
    syncAllFromSupabase()
  }

  suspend fun loginWithSupabase(identifier: String, pass: String): Pair<Boolean, String> {
    val cleanId = identifier.trim()
    if (cleanId.isBlank() || pass.isBlank()) {
      return Pair(false, "Please enter your email, phone number, or @username and password.")
    }

    if (supabaseRepo.isConfigured) {
      val resolved = supabaseRepo.resolveIdentifierSecurely(cleanId)
      if (!resolved.found) {
        return Pair(false, "Invalid login credentials. Check your identifier and password.")
      }

      val authResult = when (resolved.mode) {
        "PHONE" -> {
          val phoneToTry = resolved.phone ?: supabaseRepo.normalizePhoneE164(cleanId)
          val phoneRes = supabaseRepo.signInWithPhone(phoneToTry, pass)
          if (phoneRes.isSuccess || resolved.email.isNullOrBlank()) {
            phoneRes
          } else {
            supabaseRepo.signInWithEmail(resolved.email, pass)
          }
        }
        else -> {
          val emailToTry = resolved.email?.trim()?.lowercase().orEmpty()
          if (emailToTry.contains("@")) {
            val emailRes = supabaseRepo.signInWithEmail(emailToTry, pass)
            if (emailRes.isSuccess || resolved.phone.isNullOrBlank()) {
              emailRes
            } else {
              supabaseRepo.signInWithPhone(resolved.phone, pass)
            }
          } else if (!resolved.phone.isNullOrBlank()) {
            supabaseRepo.signInWithPhone(resolved.phone, pass)
          } else {
            Result.failure(Exception("Invalid login credentials. Check your identifier and password."))
          }
        }
      }

      return if (authResult.isSuccess) {
        val payload = authResult.getOrThrow()
        activateAuthenticatedSession(payload.user, payload.accessToken, payload.refreshToken, payload.expiresInSeconds)
        Pair(true, "Signed in successfully.")
      } else {
        Pair(false, authResult.exceptionOrNull()?.message ?: "Invalid login credentials.")
      }
    } else {
      val ok = loginUser(cleanId, pass)
      return Pair(ok, if (ok) "Signed in." else "Invalid credentials.")
    }
  }

  suspend fun verifyOtp(email: String, token: String, type: String = "signup"): Pair<Boolean, String> {
    if (supabaseRepo.isConfigured) {
      val res = supabaseRepo.verifyOtp(email, token, type)
      return if (res.isSuccess) {
        val payload = res.getOrThrow()
        pendingSessionPayload = payload
        _isEmailOtpVerified.value = true
        _pendingVerificationEmail.value = payload.user.email
        val phoneToVerify = payload.user.phone.ifBlank { _pendingVerificationPhone.value }
        if (phoneToVerify.isNotBlank() && !payload.user.isPhoneVerified && !_isPhoneOtpVerified.value) {
          // Trigger SMS OTP now that we have an authenticated JWT session for the new user
          val smsSend = supabaseRepo.sendPhoneVerificationOtp(phoneToVerify, payload.accessToken)
          val smsHint = smsSend.getOrElse { it.message ?: "Request SMS code below to verify your phone." }
          Pair(true, "EMAIL_VERIFIED_NEEDS_PHONE: Email verified! $smsHint")
        } else {
          activateAuthenticatedSession(payload.user, payload.accessToken, payload.refreshToken, payload.expiresInSeconds)
          Pair(true, "Email verified!")
        }
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Verification code invalid or expired.")
      }
    }
    _isEmailOtpVerified.value = true
    return Pair(true, "Email verified!")
  }

  suspend fun resendEmailVerificationCode(email: String): Pair<Boolean, String> {
    if (!supabaseRepo.isConfigured) return Pair(true, "Verification code resent.")
    val res = supabaseRepo.resendEmailOtp(email)
    return if (res.isSuccess) Pair(true, res.getOrThrow())
    else Pair(false, res.exceptionOrNull()?.message ?: "Unable to resend email code.")
  }

  suspend fun requestPhoneVerificationCode(phone: String): Pair<Boolean, String> {
    val cleanPhone = phone.trim().ifBlank { _pendingVerificationPhone.value }
    if (cleanPhone.isBlank()) return Pair(false, "Enter your phone number with country code first.")
    _pendingVerificationPhone.value = supabaseRepo.normalizePhoneE164(cleanPhone)
    if (!supabaseRepo.isConfigured) return Pair(true, "SMS code sent to ${_pendingVerificationPhone.value}.")

    val token = pendingSessionPayload?.accessToken ?: SessionManager.get()?.accessToken
    val res = supabaseRepo.sendPhoneVerificationOtp(_pendingVerificationPhone.value, token)
    return if (res.isSuccess) Pair(true, res.getOrThrow())
    else Pair(false, res.exceptionOrNull()?.message ?: "Unable to send SMS code.")
  }

  suspend fun verifyPhoneOtpCode(phone: String, code: String): Pair<Boolean, String> {
    val cleanPhone = supabaseRepo.normalizePhoneE164(phone.ifBlank { _pendingVerificationPhone.value })
    if (cleanPhone.isBlank()) return Pair(false, "Phone number is missing.")
    if (!supabaseRepo.isConfigured) {
      _isPhoneOtpVerified.value = true
      return Pair(true, "Phone number verified!")
    }

    val pending = pendingSessionPayload
    val activeUser = _currentUser.value
    val uid = pending?.user?.id ?: activeUser?.id
    val token = pending?.accessToken ?: SessionManager.get()?.accessToken

    val res = supabaseRepo.verifyPhoneOtp(cleanPhone, code, uid, token)
    return if (res.isSuccess) {
      _isPhoneOtpVerified.value = true
      val freshPayload = res.getOrNull()
      if (freshPayload != null && (_isEmailOtpVerified.value || freshPayload.user.isEmailVerified)) {
        activateAuthenticatedSession(
          freshPayload.user.copy(isEmailVerified = true, isPhoneVerified = true),
          freshPayload.accessToken,
          freshPayload.refreshToken,
          freshPayload.expiresInSeconds
        )
        Pair(true, "Account fully verified! Welcome to VYBE.")
      } else if (pending != null && pending.accessToken.isNotBlank() && (_isEmailOtpVerified.value || pending.user.isEmailVerified)) {
        activateAuthenticatedSession(
          pending.user.copy(isEmailVerified = true, isPhoneVerified = true, phone = cleanPhone),
          pending.accessToken,
          pending.refreshToken,
          pending.expiresInSeconds
        )
        Pair(true, "Account fully verified! Welcome to VYBE.")
      } else if (activeUser != null) {
        val updated = activeUser.copy(phone = cleanPhone, isPhoneVerified = true)
        _currentUser.value = updated
        SessionManager.get()?.updateCurrentUser(updated)
        VybeLocalStore.get()?.saveUser(updated)
        Pair(true, "Phone number verified!")
      } else {
        Pair(true, "PHONE_VERIFIED_NEEDS_EMAIL: Phone verified! Now enter your email verification code.")
      }
    } else {
      Pair(false, res.exceptionOrNull()?.message ?: "Invalid or expired SMS code.")
    }
  }

  suspend fun sendPasswordReset(email: String): Pair<Boolean, String> {
    val clean = email.trim().lowercase()
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
    val digits = identifier.filter { it.isDigit() }
    val matched = _allUsers.value.firstOrNull {
      it.email.equals(identifier.trim(), ignoreCase = true) ||
        it.username.lowercase() == clean ||
        (digits.length >= 7 && it.phone.filter { c -> c.isDigit() }.endsWith(digits.takeLast(8)))
    } ?: User(
      id = "u_${System.currentTimeMillis()}",
      username = clean.ifBlank { "vybe_user" },
      displayName = clean.ifBlank { "VYBE Member" },
      email = if (identifier.contains("@")) identifier.trim() else "$clean@vybe.app",
      role = UserRole.USER,
      isOnline = true,
      isOnboardingCompleted = true
    )
    _currentUser.value = matched
    _isLoggedIn.value = true
    _allUsers.value = listOf(matched) + _allUsers.value.filterNot { it.id == matched.id }
    SessionManager.get()?.saveSession(matched)
    return true
  }

  fun logout() {
    presenceLoopJob?.cancel()
    callSignalingPollJob?.cancel()
    stopCallDurationTimer()
    liveStreamSyncJob?.cancel()
    callEngine?.stopSession()
    callEngine = null
    _activeCall.value = null
    _activeLiveStream.value = null
    _liveStreamComments.value = emptyList()
    val token = SessionManager.get()?.accessToken.orEmpty()
    ioScope.launch {
      supabaseRepo.signOut(token)
    }
    SessionManager.get()?.clearSession()
    VybeLocalStore.get()?.clearUserScopedCaches()
    viewedStoryIdsLocal = emptySet()
    vybeMatchIdsCache = emptySet()
    _currentUser.value = null
    _isLoggedIn.value = false
    _posts.value = emptyList()
    _stories.value = emptyList()
    _unfilteredPosts.value = emptyList()
    _unloadRooms.value = emptyList()
    _liveStreams.value = emptyList()
    _predictions.value = emptyList()
    _doubleTakePrompts.value = emptyList()
    _timeCapsules.value = emptyList()
    _crossedPaths.value = emptyList()
    _shops.value = emptyList()
    _communities.value = emptyList()
    _activeCommunity.value = null
    _communityPosts.value = emptyList()
    _communityMembers.value = emptyList()
    _communityComments.value = emptyMap()
    _conversations.value = emptyList()
    _notifications.value = emptyList()
    _vybeMatches.value = emptyList()
    _followingUserIds.value = emptySet()
    _requestedFollowUserIds.value = emptySet()
    _followerUserIds.value = emptySet()
    _incomingFollowRequestUserIds.value = emptySet()
    _blockedUserIds.value = emptySet()
    _restrictedUserIds.value = emptySet()
    _uploadStatus.value = UploadStatus.Idle

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        syncAllFromSupabase()
      }
    }
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
    val cleanText = newText.trim()
    if (cleanText.isEmpty()) return
    val updated = _conversations.value.map { conv ->
      if (conv.id == conversationId) {
        val nextMsgs = conv.messages.map { m ->
          if (m.id == messageId && m.isMe) m.copy(text = cleanText, isEdited = true) else m
        }
        conv.copy(
          messages = nextMsgs,
          lastMessage = nextMsgs.lastOrNull()?.text ?: conv.lastMessage
        )
      } else conv
    }
    _conversations.value = updated
    VybeLocalStore.get()?.saveConversations(updated)

    if (supabaseRepo.isConfigured && !messageId.startsWith("m_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        supabaseRepo.editMessage(messageId, cleanText, token)
      }
    }
  }

  fun deleteMessageForMe(conversationId: String, messageId: String) {
    val updated = _conversations.value.map { conv ->
      if (conv.id == conversationId) {
        val remaining = conv.messages.filterNot { it.id == messageId }
        conv.copy(
          messages = remaining,
          lastMessage = remaining.lastOrNull()?.text ?: "Conversation started"
        )
      } else conv
    }
    _conversations.value = updated
    VybeLocalStore.get()?.saveConversations(updated)
  }

  fun deleteMessageForEveryone(conversationId: String, messageId: String) {
    if (messageId.isBlank()) return
    var targetMediaUrl: String? = null
    deletedMessageIdsCache.add(messageId)
    VybeLocalStore.get()?.addDeletedMessageId(messageId)

    val updated = _conversations.value.map { conv ->
      if (conv.id == conversationId || conv.messages.any { it.id == messageId }) {
        val target = conv.messages.firstOrNull { it.id == messageId }
        if (target?.mediaUrl != null) {
          targetMediaUrl = target.mediaUrl
        }
        val remaining = conv.messages.filterNot { it.id == messageId }
        val nextLastIso = remaining.lastOrNull()?.createdAtIso ?: conv.lastMessageCreatedAtIso
        conv.copy(
          messages = remaining,
          lastMessage = remaining.lastOrNull()?.text ?: "Conversation started",
          lastMessageCreatedAtIso = nextLastIso,
          lastMessageTimestamp = TimestampUtils.formatRelative(nextLastIso, "Recently")
        )
      } else conv
    }
    _conversations.value = updated
    VybeLocalStore.get()?.saveConversations(updated)

    if (supabaseRepo.isConfigured && !messageId.startsWith("m_")) {
      val mediaToClean = targetMediaUrl
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        supabaseRepo.deleteMessageForEveryone(
          messageId = messageId,
          mediaUrl = mediaToClean,
          accessToken = token
        )
      }
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
    val targetConv = _conversations.value.firstOrNull { it.id == conversationId }
    _conversations.value = _conversations.value.map {
      if (it.id == conversationId) {
        it.copy(
          unreadCount = 0,
          messages = it.messages.map { m -> if (!m.isMe) m.copy(isRead = true) else m }
        )
      } else it
    }
    val me = _currentUser.value
    if (supabaseRepo.isConfigured && me != null && targetConv != null) {
      val unreadPeerIds = targetConv.messages.filter { !it.isMe && !it.id.startsWith("c_") }.map { it.id }
      if (unreadPeerIds.isNotEmpty()) {
        ioScope.launch {
          val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
          SupabaseRoomsAndCallsRemote.markMessagesRead(
            userId = me.id,
            messageIds = unreadPeerIds,
            authHeader = SupabaseConfig.buildAuthHeader(token)
          )
        }
      }
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

  // --- Real Presence Heartbeat, Cross-Device Voice & Video Calls, and Persistent Live Rooms ---

  private fun startPresenceAndIncomingCallLoops() {
    presenceLoopJob?.cancel()
    if (!supabaseRepo.isConfigured) return
    presenceLoopJob = ioScope.launch {
      var tick = 0
      while (isActive && _isLoggedIn.value) {
        try {
          val me = _currentUser.value
          val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
          val authHeader = SupabaseConfig.buildAuthHeader(token)
          if (me != null && me.id.isNotBlank() && !authHeader.isNullOrBlank()) {
            if (tick % 5 == 0) {
              SupabaseRoomsAndCallsRemote.updatePresence(me.id, me.livePulse, authHeader)
            }
            // Check for incoming calls when idle
            val active = _activeCall.value
            if (active == null) {
              val incoming = SupabaseRoomsAndCallsRemote.fetchIncomingCalls(me.id, authHeader)
                .firstOrNull { !_blockedUserIds.value.contains(it.callerId) }
              if (incoming != null) {
                val incomingId = incoming.id
                if (incomingId.isNotBlank()) {
                  val caller = _allUsers.value.firstOrNull { it.id == incoming.callerId }
                    ?: supabaseRepo.fetchUserProfile(incoming.callerId, token)
                    ?: User(id = incoming.callerId, username = "caller", displayName = "VYBE Member", isOnline = true)
                  _activeCall.value = CallSession(
                    callId = incomingId,
                    peerUser = caller,
                    isVideo = incoming.isVideo,
                    state = CallState.INCOMING
                  )
                  attachCallEngineIfReady(incomingId, isVideo = incoming.isVideo, token = token)
                  startCallStateWatcher(incomingId)
                }
              }
            }
            // Refresh active room messages if user is inside a live room
            val openRoom = _activeUnloadRoom.value
            if (openRoom != null && !openRoom.id.startsWith("ul_") && !openRoom.id.startsWith("room_")) {
              refreshActiveRoomMessages(openRoom.id, token)
            }
            // Periodic cross-device sync (~13.5s) for Feed, Stories, Live Zone, Explore Zone & Chat/Call History
            if (tick > 0 && tick % 3 == 0 && !isUploadInFlight) {
              syncFeedFromSupabase(token)
              syncStoriesFromSupabase(me.id, token)
              syncRoomsFromSupabase(token)
              syncExploreAndLiveFromSupabase(token)
              val convRes = supabaseRepo.fetchConversationsForUser(
                myUserId = me.id,
                blockedIds = _blockedUserIds.value,
                restrictedIds = _restrictedUserIds.value,
                accessToken = token.orEmpty()
              )
              if (convRes.isSuccess) {
                val remoteConvs = dedupeConversationCallLogs(convRes.getOrThrow())
                _conversations.value = remoteConvs
                VybeLocalStore.get()?.saveConversations(remoteConvs)
              }
            }
          }
        } catch (_: Exception) {}
        tick++
        delay(4500L)
      }
    }
  }

  private fun startCallDurationTimer(callId: String) {
    if (callDurationJob?.isActive == true && _activeCall.value?.callId == callId) return
    callDurationJob?.cancel()
    callDurationJob = ioScope.launch {
      while (isActive) {
        delay(1000L)
        val cur = _activeCall.value ?: break
        if (cur.callId != callId && !cur.callId.startsWith("local_") && !callId.startsWith("local_")) break
        if (cur.state == CallState.CONNECTED) {
          _activeCall.value = cur.copy(durationSeconds = cur.durationSeconds + 1)
        } else if (cur.state in setOf(CallState.ENDED, CallState.DECLINED, CallState.MISSED, CallState.FAILED)) {
          break
        }
      }
    }
  }

  private fun stopCallDurationTimer() {
    callDurationJob?.cancel()
    callDurationJob = null
  }

  private fun dedupeConversationCallLogs(conversations: List<Conversation>): List<Conversation> {
    return conversations.map { conv ->
      val seenCallIds = HashSet<String>()
      val filteredMsgs = conv.messages.filter { msg ->
        if (msg.isDeletedForEveryone || deletedMessageIdsCache.contains(msg.id)) {
          false
        } else if (msg.mediaType == "CALL_LOG" && !msg.mediaUrl.isNullOrBlank()) {
          val callIdParam = msg.mediaUrl.substringAfter("callId=", "").substringBefore("&")
          if (callIdParam.isNotBlank()) {
            seenCallIds.add(callIdParam)
          } else true
        } else true
      }
      conv.copy(
        messages = filteredMsgs,
        lastMessage = filteredMsgs.lastOrNull()?.text ?: "Conversation started"
      )
    }
  }

  fun formatCallLogSummary(isVideo: Boolean, status: String, durationSeconds: Int): String {
    val kind = if (isVideo) "Video call" else "Voice call"
    val durText = String.format(java.util.Locale.US, "%02d:%02d", durationSeconds / 60, durationSeconds % 60)
    return when (status.uppercase()) {
      "MISSED" -> "Missed ${kind.lowercase()}"
      "DECLINED" -> "Declined ${kind.lowercase()}"
      else -> if (durationSeconds > 0) "$kind • $durText" else "$kind • 00:00"
    }
  }

  private fun recordCallHistoryInChat(call: CallSession, finalStatus: String, persistRemote: Boolean) {
    val me = _currentUser.value ?: return
    val dedupeKey = call.callId.ifBlank { "call_${System.currentTimeMillis()}" }
    if (!loggedCallIds.add(dedupeKey)) return

    val effectiveStatus = when {
      call.durationSeconds > 0 -> "COMPLETED"
      finalStatus.equals("DECLINED", true) -> "DECLINED"
      finalStatus.equals("MISSED", true) -> "MISSED"
      call.state == CallState.OUTGOING || call.state == CallState.INCOMING -> "MISSED"
      else -> "COMPLETED"
    }
    val summary = formatCallLogSummary(call.isVideo, effectiveStatus, call.durationSeconds)
    val callLogUri = "vybe://call-log?type=${if (call.isVideo) "VIDEO" else "VOICE"}&status=$effectiveStatus&duration=${call.durationSeconds}&callId=$dedupeKey"
    val conv = getOrCreateConversation(call.peerUser)

    if (persistRemote || !supabaseRepo.isConfigured || dedupeKey.startsWith("local_")) {
      sendExtendedDirectMessage(
        conversationId = conv.id,
        text = summary,
        mediaUrl = callLogUri,
        mediaType = "CALL_LOG"
      )
    } else {
      // Passive side shows optimistic call log immediately unless already synced, then reconciles with Supabase
      val currentList = _conversations.value.toMutableList()
      val idx = currentList.indexOfFirst { it.id == conv.id }
      if (idx != -1 && currentList[idx].messages.none { it.mediaUrl?.contains("callId=$dedupeKey") == true }) {
        val localLog = DirectMessage(
          id = "clog_$dedupeKey",
          senderId = me.id,
          text = summary,
          timestamp = "Just now",
          isMe = false,
          mediaUrl = callLogUri,
          mediaType = "CALL_LOG",
          isRead = true,
          status = MessageStatus.SENT
        )
        val c = currentList[idx]
        currentList[idx] = c.copy(
          lastMessage = summary,
          lastMessageTimestamp = "Just now",
          messages = c.messages + localLog
        )
        _conversations.value = currentList
      }
      ioScope.launch {
        delay(1200L)
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        if (token.isNotBlank()) {
          val convRes = supabaseRepo.fetchConversationsForUser(
            myUserId = me.id,
            blockedIds = _blockedUserIds.value,
            restrictedIds = _restrictedUserIds.value,
            accessToken = token
          )
          if (convRes.isSuccess) {
            val remoteConvs = dedupeConversationCallLogs(convRes.getOrThrow())
            _conversations.value = remoteConvs
            VybeLocalStore.get()?.saveConversations(remoteConvs)
          }
        }
      }
    }
  }

  private fun attachCallEngineIfReady(callId: String, isVideo: Boolean, token: String?) {
    val ctx = appContext ?: return
    val me = _currentUser.value ?: return
    if (callId.isBlank() || callId.startsWith("local_")) return
    if (callEngine != null) return

    val engine = VybeCallEngine(appContext = ctx)
    callEngine = engine
    engine.startSession(
      callId = callId,
      myUserId = me.id,
      accessToken = token.orEmpty(),
      initialSpeakerOn = _activeCall.value?.isSpeakerOn ?: isVideo,
      initialMuted = _activeCall.value?.isMuted ?: false,
      callback = object : VybeCallEngine.Listener {
        override fun onSignalingConnected(iceSummary: String) {
          _activeCall.value = _activeCall.value?.copy(
            isSignalingConnected = true,
            iceCandidateSummary = iceSummary,
            connectionStatusText = "Realtime signaling active"
          )
        }

        override fun onRemoteAccepted() {
          val cur = _activeCall.value
          if (cur != null && cur.state != CallState.CONNECTED) {
            _activeCall.value = cur.copy(
              state = CallState.CONNECTED,
              connectionStatusText = if (cur.isVideo) "HD Video & Audio connected" else "Encrypted Voice connected"
            )
            startCallDurationTimer(cur.callId)
            callEngine?.startMediaStreaming()
          }
        }

        override fun onRemoteDeclined() {
          val cur = _activeCall.value
          stopCallDurationTimer()
          if (cur != null) {
            recordCallHistoryInChat(cur, "DECLINED", persistRemote = false)
          }
          _activeCall.value = null
          callEngine?.stopSession()
          callEngine = null
        }

        override fun onRemoteEnded() {
          val cur = _activeCall.value
          stopCallDurationTimer()
          if (cur != null) {
            recordCallHistoryInChat(cur, "ENDED", persistRemote = false)
          }
          _activeCall.value = null
          callEngine?.stopSession()
          callEngine = null
        }

        override fun onRemoteMuteChanged(isMuted: Boolean) {
          _activeCall.value = _activeCall.value?.copy(isPeerMuted = isMuted)
        }

        override fun onRemoteCameraChanged(isCameraOff: Boolean) {
          _activeCall.value = _activeCall.value?.copy(isPeerCameraOff = isCameraOff)
        }

        override fun onRemoteVideoFrame(jpegBase64: String) {
          val cur = _activeCall.value ?: return
          // If video frames are arriving from peer, ensure state is CONNECTED and timer is running!
          if (cur.state != CallState.CONNECTED) {
            _activeCall.value = cur.copy(
              state = CallState.CONNECTED,
              remoteVideoFrameBase64 = jpegBase64,
              connectionStatusText = "HD Video & Audio connected"
            )
            startCallDurationTimer(cur.callId)
            callEngine?.startMediaStreaming()
          } else {
            _activeCall.value = cur.copy(remoteVideoFrameBase64 = jpegBase64)
          }
        }

        override fun onError(message: String) {
          _activeCall.value = _activeCall.value?.copy(connectionStatusText = message)
        }
      }
    )
  }

  private fun startCallStateWatcher(callId: String) {
    callSignalingPollJob?.cancel()
    if (!supabaseRepo.isConfigured || callId.startsWith("local_")) return
    callSignalingPollJob = ioScope.launch {
      val startedMs = System.currentTimeMillis()
      while (isActive) {
        val current = _activeCall.value ?: break
        if (current.callId != callId) break
        if (current.state in setOf(CallState.ENDED, CallState.DECLINED, CallState.MISSED, CallState.FAILED)) break

        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        val remoteSession = SupabaseRoomsAndCallsRemote.fetchCallSessionById(callId, authHeader)
        if (remoteSession != null) {
          when (remoteSession.status.uppercase()) {
            "CONNECTED" -> {
              if (current.state != CallState.CONNECTED) {
                _activeCall.value = current.copy(
                  state = CallState.CONNECTED,
                  connectionStatusText = if (current.isVideo) "HD Video & Audio connected" else "Encrypted Voice connected"
                )
                startCallDurationTimer(callId)
                callEngine?.startMediaStreaming()
              }
            }
            "DECLINED" -> {
              stopCallDurationTimer()
              recordCallHistoryInChat(current, "DECLINED", persistRemote = false)
              _activeCall.value = null
              callEngine?.stopSession()
              callEngine = null
              break
            }
            "MISSED" -> {
              stopCallDurationTimer()
              recordCallHistoryInChat(current, "MISSED", persistRemote = false)
              _activeCall.value = null
              callEngine?.stopSession()
              callEngine = null
              break
            }
            "ENDED" -> {
              stopCallDurationTimer()
              recordCallHistoryInChat(current, "ENDED", persistRemote = false)
              _activeCall.value = null
              callEngine?.stopSession()
              callEngine = null
              break
            }
          }
        }
        // Ringing timeout after 45 seconds if unanswered
        if (current.state == CallState.OUTGOING && System.currentTimeMillis() - startedMs > 45_000L) {
          stopCallDurationTimer()
          SupabaseRoomsAndCallsRemote.patchCallSession(
            callId = callId,
            status = "MISSED",
            endedAt = TimestampUtils.nowIsoUtc(),
            authHeader = authHeader
          )
          recordCallHistoryInChat(current, "MISSED", persistRemote = true)
          _activeCall.value = null
          callEngine?.stopSession()
          callEngine = null
          break
        }
        delay(1800L)
      }
    }
  }

  fun initiateCall(peerUser: User, isVideo: Boolean): CallAvailabilityResult {
    val me = _currentUser.value ?: return CallAvailabilityResult.Error("Please sign in first.")
    if (_blockedUserIds.value.contains(peerUser.id)) {
      return CallAvailabilityResult.Blocked("Cannot call a blocked user.")
    }
    val latestPeer = _allUsers.value.firstOrNull { it.id == peerUser.id } ?: peerUser
    val isRealCloudUser = supabaseRepo.isConfigured && latestPeer.id.count { it == '-' } == 4
    val isPeerOnline = isRealCloudUser ||
      latestPeer.isOnline ||
      TimestampUtils.isRecentlyOnline(latestPeer.lastSeenAtIso) ||
      (latestPeer.livePulse != LivePulseStatus.NONE && latestPeer.livePulse != LivePulseStatus.QUIET_TIME)

    if (!isPeerOnline) {
      return CallAvailabilityResult.Offline("${latestPeer.displayName} is currently offline. Send a message instead.")
    }
    startCall(latestPeer, isVideo, me.id)
    return CallAvailabilityResult.Available
  }

  fun startCall(peerUser: User, isVideo: Boolean, explicitCallerId: String? = null) {
    stopCallDurationTimer()
    callEngine?.stopSession()
    callEngine = null
    val localId = "local_${System.currentTimeMillis()}"
    _activeCall.value = CallSession(
      callId = localId,
      peerUser = peerUser,
      isVideo = isVideo,
      state = CallState.OUTGOING,
      isSpeakerOn = isVideo
    )

    val callerId = explicitCallerId ?: _currentUser.value?.id.orEmpty()
    if (supabaseRepo.isConfigured && callerId.isNotBlank()) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        val tempEngine = VybeCallEngine(appContext)
        val (offerSdp, iceSummary) = tempEngine.buildLocalSdpWithStun(isVideo = isVideo, isOffer = true)
        val createRes = SupabaseRoomsAndCallsRemote.createCallSession(
          callerId = callerId,
          calleeId = peerUser.id,
          isVideo = isVideo,
          offerSdp = offerSdp,
          authHeader = authHeader
        )
        if (createRes.isSuccess) {
          val serverCall = createRes.getOrThrow()
          val serverId = serverCall.id
          if (serverId.isBlank()) return@launch
          _activeCall.value = _activeCall.value?.copy(
            callId = serverId,
            iceCandidateSummary = iceSummary,
            connectionStatusText = iceSummary
          )
          attachCallEngineIfReady(serverId, isVideo = isVideo, token = token)
          startCallStateWatcher(serverId)
        }
      }
    }
  }

  fun acceptCall() {
    val cur = _activeCall.value ?: return
    _activeCall.value = cur.copy(
      state = CallState.CONNECTED,
      connectionStatusText = if (cur.isVideo) "HD Video & Audio connected" else "Encrypted Voice connected"
    )
    startCallDurationTimer(cur.callId)

    if (supabaseRepo.isConfigured && cur.callId.isNotBlank() && !cur.callId.startsWith("local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        if (callEngine == null) {
          attachCallEngineIfReady(cur.callId, isVideo = cur.isVideo, token = token)
        }
        val (answerSdp, iceSummary) = (callEngine ?: VybeCallEngine(appContext))
          .buildLocalSdpWithStun(isVideo = cur.isVideo, isOffer = false)
        SupabaseRoomsAndCallsRemote.patchCallSession(
          callId = cur.callId,
          status = "CONNECTED",
          answerSdp = answerSdp,
          startedAt = TimestampUtils.nowIsoUtc(),
          authHeader = authHeader
        )
        _activeCall.value = _activeCall.value?.copy(
          iceCandidateSummary = iceSummary,
          connectionStatusText = "Connected ($iceSummary)"
        )
        callEngine?.sendCallControlEvent("ANSWER")
        callEngine?.startMediaStreaming()
      }
    } else {
      callEngine?.startMediaStreaming()
    }
  }

  fun declineCall() {
    val cur = _activeCall.value ?: return
    stopCallDurationTimer()
    callEngine?.sendCallControlEvent("DECLINE")
    callEngine?.stopSession()
    callEngine = null
    _activeCall.value = null
    recordCallHistoryInChat(cur, "DECLINED", persistRemote = true)
    if (supabaseRepo.isConfigured && cur.callId.isNotBlank() && !cur.callId.startsWith("local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseRoomsAndCallsRemote.patchCallSession(
          callId = cur.callId,
          status = "DECLINED",
          endedAt = TimestampUtils.nowIsoUtc(),
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
      }
    }
  }

  fun endCall() {
    val cur = _activeCall.value
    stopCallDurationTimer()
    callEngine?.sendCallControlEvent("HANGUP")
    callEngine?.stopSession()
    callEngine = null
    callSignalingPollJob?.cancel()
    _activeCall.value = null
    if (cur != null) {
      val finalStatus = when {
        cur.durationSeconds > 0 || cur.state == CallState.CONNECTED -> "ENDED"
        cur.state == CallState.INCOMING -> "DECLINED"
        else -> "MISSED"
      }
      recordCallHistoryInChat(cur, finalStatus, persistRemote = true)
      if (supabaseRepo.isConfigured && cur.callId.isNotBlank() && !cur.callId.startsWith("local_")) {
        ioScope.launch {
          val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
          SupabaseRoomsAndCallsRemote.patchCallSession(
            callId = cur.callId,
            status = finalStatus,
            endedAt = TimestampUtils.nowIsoUtc(),
            authHeader = SupabaseConfig.buildAuthHeader(token)
          )
        }
      }
    }
  }

  fun onCallAudioPermissionReady() {
    if (_activeCall.value?.state == CallState.CONNECTED) {
      callEngine?.startMediaStreaming()
    }
  }

  fun toggleCallMute() {
    val nextMuted = !(_activeCall.value?.isMuted ?: false)
    _activeCall.value = _activeCall.value?.copy(isMuted = nextMuted)
    callEngine?.setMuted(nextMuted)
  }

  fun toggleCallSpeaker() {
    val nextSpeaker = !(_activeCall.value?.isSpeakerOn ?: false)
    _activeCall.value = _activeCall.value?.copy(isSpeakerOn = nextSpeaker)
    callEngine?.setSpeakerphone(nextSpeaker)
  }

  fun toggleCallCamera() {
    val nextOff = !(_activeCall.value?.isCameraOff ?: false)
    _activeCall.value = _activeCall.value?.copy(isCameraOff = nextOff)
    callEngine?.notifyCameraState(nextOff)
  }

  fun switchCallCamera() {
    val nextFront = !(_activeCall.value?.isFrontCamera ?: true)
    _activeCall.value = _activeCall.value?.copy(isFrontCamera = nextFront)
  }

  fun pushLocalVideoFrame(jpegBytes: ByteArray) {
    callEngine?.sendVideoFrameJpeg(jpegBytes)
  }

  // --- LIVE ZONE: REAL CROSS-DEVICE BROADCAST & VIEWING (`live_streams` + `live_stream_comments`) ---

  fun startLiveBroadcast(title: String, category: String) {
    val host = _currentUser.value ?: return
    val cleanTitle = title.trim().ifBlank { "${host.displayName} is Live" }
    val cleanCategory = category.trim().ifBlank { "Live Vibe" }
    val localStream = LiveStreamSession(
      id = "local_live_${System.currentTimeMillis()}",
      host = host,
      title = cleanTitle,
      category = cleanCategory,
      isLive = true,
      viewerCount = 1,
      startedAtIso = TimestampUtils.nowIsoUtc()
    )
    _activeLiveStream.value = localStream
    _liveStreamComments.value = emptyList()
    _liveStreams.value = listOf(localStream) + _liveStreams.value.filterNot { it.host.id == host.id }

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        val res = SupabaseExploreAndLiveRemote.startLiveStream(host, cleanTitle, cleanCategory, authHeader)
        if (res.isSuccess) {
          val created = res.getOrThrow()
          _activeLiveStream.value = created
          _liveStreams.value = listOf(created) + _liveStreams.value.filterNot {
            it.id == localStream.id || it.id == created.id
          }
          startActiveLiveStreamWatcher(created.id, isHost = true)
        }
      }
    }
  }

  fun joinLiveBroadcast(stream: LiveStreamSession) {
    val bumped = stream.copy(viewerCount = stream.viewerCount + 1)
    _activeLiveStream.value = bumped
    _liveStreamComments.value = emptyList()
    if (supabaseRepo.isConfigured && !stream.id.startsWith("local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        SupabaseExploreAndLiveRemote.updateLiveStreamState(
          streamId = stream.id,
          viewerCount = bumped.viewerCount,
          authHeader = authHeader
        )
        _liveStreamComments.value = SupabaseExploreAndLiveRemote.fetchLiveStreamComments(stream.id, authHeader)
      }
      startActiveLiveStreamWatcher(stream.id, isHost = stream.host.id == _currentUser.value?.id)
    }
  }

  fun leaveOrEndLiveBroadcast() {
    val active = _activeLiveStream.value ?: return
    val me = _currentUser.value
    liveStreamSyncJob?.cancel()
    liveStreamSyncJob = null
    _activeLiveStream.value = null
    _liveStreamComments.value = emptyList()

    val isHost = me != null && active.host.id == me.id
    if (isHost) {
      _liveStreams.value = _liveStreams.value.filterNot { it.id == active.id }
    }
    if (supabaseRepo.isConfigured && !active.id.startsWith("local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        if (isHost) {
          SupabaseExploreAndLiveRemote.updateLiveStreamState(
            streamId = active.id,
            isLive = false,
            endedAt = TimestampUtils.nowIsoUtc(),
            authHeader = authHeader
          )
        } else {
          SupabaseExploreAndLiveRemote.updateLiveStreamState(
            streamId = active.id,
            viewerCount = (active.viewerCount - 1).coerceAtLeast(1),
            authHeader = authHeader
          )
        }
        syncExploreAndLiveFromSupabase(token)
      }
    }
  }

  fun sendLiveBroadcastComment(content: String) {
    val active = _activeLiveStream.value ?: return
    val me = _currentUser.value ?: return
    val clean = content.trim()
    if (clean.isBlank()) return
    val optimistic = LiveStreamComment(
      id = "lsc_${System.currentTimeMillis()}",
      streamId = active.id,
      authorId = me.id,
      authorName = me.displayName,
      content = clean,
      timestamp = "Just now"
    )
    _liveStreamComments.value = _liveStreamComments.value + optimistic
    if (supabaseRepo.isConfigured && !active.id.startsWith("local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val saved = SupabaseExploreAndLiveRemote.sendLiveStreamComment(
          streamId = active.id,
          authorId = me.id,
          authorName = me.displayName,
          content = clean,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
        if (saved != null) {
          _liveStreamComments.value = _liveStreamComments.value.map {
            if (it.id == optimistic.id) saved else it
          }
        }
      }
    }
  }

  fun pushLiveBroadcastFrame(jpegBytes: ByteArray) {
    val active = _activeLiveStream.value ?: return
    val me = _currentUser.value ?: return
    if (active.host.id != me.id || active.id.startsWith("local_") || jpegBytes.isEmpty() || jpegBytes.size > 96_000) return
    val encoded = android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
    ioScope.launch {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
      SupabaseExploreAndLiveRemote.updateLiveStreamState(
        streamId = active.id,
        latestFrameBase64 = encoded,
        authHeader = SupabaseConfig.buildAuthHeader(token)
      )
    }
  }

  private fun startActiveLiveStreamWatcher(streamId: String, isHost: Boolean) {
    liveStreamSyncJob?.cancel()
    if (!supabaseRepo.isConfigured || streamId.startsWith("local_")) return
    liveStreamSyncJob = ioScope.launch {
      while (isActive && _activeLiveStream.value?.id == streamId) {
        try {
          val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
          val authHeader = SupabaseConfig.buildAuthHeader(token)
          val usersMap = _allUsers.value.associateBy { it.id }
          if (isHost) {
            SupabaseExploreAndLiveRemote.updateLiveStreamState(streamId = streamId, isLive = true, authHeader = authHeader)
          }
          val remote = SupabaseExploreAndLiveRemote.fetchLiveStreamById(streamId, usersMap, authHeader)
          if (remote != null) {
            if (!remote.isLive && !isHost) {
              _activeLiveStream.value = remote
              break
            }
            _activeLiveStream.value = remote
          }
          _liveStreamComments.value = SupabaseExploreAndLiveRemote.fetchLiveStreamComments(streamId, authHeader)
        } catch (_: Exception) {}
        delay(2000L)
      }
    }
  }

  // --- Persistent Live Rooms + Join by Unique Room ID (`VYB-XXXX-XXXX`) ---

  suspend fun joinRoomByCode(rawCode: String): Result<UnloadRoom> {
    val normalizedCode = SupabaseRoomsAndCallsRemote.normalizeRoomLookupInput(rawCode)
    if (normalizedCode.isBlank()) {
      return Result.failure(IllegalArgumentException("Enter a valid Room ID (e.g. VYB-7K4M-92QX)."))
    }

    val me = _currentUser.value
    if (supabaseRepo.isConfigured) {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
      val authHeader = SupabaseConfig.buildAuthHeader(token)
      val remoteRes = SupabaseRoomsAndCallsRemote.findRoomByCodeOrId(normalizedCode, me?.id, authHeader)
      if (remoteRes.isSuccess) {
        val found = remoteRes.getOrThrow()
        if (me != null && !authHeader.isNullOrBlank()) {
          SupabaseRoomsAndCallsRemote.joinRoomMember(found.id, me.id, "LISTENER", authHeader)
        }
        val joinedRoom = found.copy(activeListenersCount = maxOf(1, found.activeListenersCount))
        _activeUnloadRoom.value = joinedRoom
        _unloadRooms.value = (listOf(joinedRoom) + _unloadRooms.value.filterNot {
          it.id == joinedRoom.id || it.roomCode.equals(normalizedCode, ignoreCase = true)
        })
        return Result.success(joinedRoom)
      }
      // Also check seeded/local rooms if code matches one of them
      val localMatch = _unloadRooms.value.firstOrNull { it.roomCode.equals(normalizedCode, ignoreCase = true) }
      if (localMatch != null) {
        joinUnloadRoom(localMatch)
        return Result.success(localMatch)
      }
      return remoteRes
    } else {
      val localMatch = _unloadRooms.value.firstOrNull {
        it.roomCode.equals(normalizedCode, ignoreCase = true) || it.id.equals(rawCode.trim(), ignoreCase = true)
      } ?: return Result.failure(IllegalArgumentException("No active room found with ID $normalizedCode."))

      if (!localMatch.isActive) {
        return Result.failure(IllegalStateException("This room has ended."))
      }
      if (localMatch.activeListenersCount >= localMatch.capacity) {
        return Result.failure(IllegalStateException("This room is currently full (${localMatch.capacity}/${localMatch.capacity})."))
      }
      joinUnloadRoom(localMatch)
      return Result.success(localMatch)
    }
  }

  fun joinUnloadRoom(room: UnloadRoom) {
    val bumped = room.copy(activeListenersCount = maxOf(1, room.activeListenersCount))
    _activeUnloadRoom.value = bumped
    val me = _currentUser.value
    if (supabaseRepo.isConfigured && me != null && !room.id.startsWith("ul_") && !room.id.startsWith("room_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseRoomsAndCallsRemote.joinRoomMember(room.id, me.id, "LISTENER", SupabaseConfig.buildAuthHeader(token))
        refreshActiveRoomMessages(room.id, token)
      }
    }
  }

  fun leaveUnloadRoom() {
    val active = _activeUnloadRoom.value
    val me = _currentUser.value
    _activeUnloadRoom.value = null
    if (active != null && me != null && supabaseRepo.isConfigured && !active.id.startsWith("ul_") && !active.id.startsWith("room_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseRoomsAndCallsRemote.leaveRoomMember(active.id, me.id, SupabaseConfig.buildAuthHeader(token))
      }
    }
  }

  private suspend fun refreshActiveRoomMessages(roomId: String, explicitToken: String? = null) {
    val meId = _currentUser.value?.id.orEmpty()
    val token = explicitToken ?: supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
    val authHeader = SupabaseConfig.buildAuthHeader(token)
    val msgs = SupabaseRoomsAndCallsRemote.fetchRoomMessages(roomId, meId, authHeader)
    val roomRes = SupabaseRoomsAndCallsRemote.findRoomByCodeOrId(roomId, meId, authHeader)
    val count = roomRes.getOrNull()?.activeListenersCount ?: 1
    _unloadRooms.value = _unloadRooms.value.map { r ->
      if (r.id == roomId) {
        val updated = r.copy(
          recentMessages = msgs,
          activeListenersCount = maxOf(1, count)
        )
        if (_activeUnloadRoom.value?.id == roomId) {
          _activeUnloadRoom.value = updated
        }
        updated
      } else r
    }
  }

  fun sendUnloadMessage(roomId: String, text: String, isAnonymous: Boolean) {
    val user = _currentUser.value ?: return
    val clean = text.trim()
    if (clean.isBlank()) return
    val nowIso = TimestampUtils.nowIsoUtc()
    val senderLabel = if (isAnonymous) "Anonymous Vyber" else user.displayName

    val optimisticMsg = UnloadMessage(
      id = "ulm_${System.currentTimeMillis()}",
      senderName = senderLabel,
      isAnonymous = isAnonymous,
      text = clean,
      timestamp = "Just now",
      createdAtIso = nowIso,
      isMe = true
    )

    _unloadRooms.value = _unloadRooms.value.map { room ->
      if (room.id == roomId) {
        val updated = room.copy(recentMessages = room.recentMessages + optimisticMsg)
        if (_activeUnloadRoom.value?.id == roomId) _activeUnloadRoom.value = updated
        updated
      } else room
    }

    if (supabaseRepo.isConfigured && !roomId.startsWith("ul_") && !roomId.startsWith("room_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val res = SupabaseRoomsAndCallsRemote.sendRoomMessage(
          roomId = roomId,
          senderId = user.id,
          senderName = senderLabel,
          content = clean,
          isAnonymous = isAnonymous,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
        if (res.isSuccess) {
          val serverMsg = res.getOrThrow()
          _unloadRooms.value = _unloadRooms.value.map { room ->
            if (room.id == roomId) {
              val reconciled = room.recentMessages.map { if (it.id == optimisticMsg.id) serverMsg else it }
              val updated = room.copy(recentMessages = reconciled)
              if (_activeUnloadRoom.value?.id == roomId) _activeUnloadRoom.value = updated
              updated
            } else room
          }
        }
      }
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
    val generatedCode = SupabaseRoomsAndCallsRemote.generateUniqueRoomCode()
    val nowIso = TimestampUtils.nowIsoUtc()
    val tempId = "room_${System.currentTimeMillis()}"
    val newRoom = UnloadRoom(
      id = tempId,
      roomCode = generatedCode,
      title = title.trim(),
      description = description.trim(),
      topicTag = topicTag.trim().ifBlank { "Community" },
      mode = mode,
      hostId = user?.id ?: "self",
      hostName = user?.displayName ?: "VYBE Host",
      durationMinutes = durationMinutes,
      capacity = capacity,
      scheduledTime = scheduledTime,
      joinerMessage = joinerMessage.trim(),
      termsAgreed = termsAgreed,
      activeListenersCount = 1,
      createdAtIso = nowIso
    )
    _unloadRooms.value = listOf(newRoom) + _unloadRooms.value
    _activeUnloadRoom.value = newRoom

    if (supabaseRepo.isConfigured && user != null) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val createRes = SupabaseRoomsAndCallsRemote.createRoom(
          hostId = user.id,
          hostName = user.displayName,
          title = newRoom.title,
          description = newRoom.description,
          topicTag = newRoom.topicTag,
          mode = mode,
          durationMinutes = durationMinutes,
          capacity = capacity,
          scheduledTime = scheduledTime,
          joinerMessage = newRoom.joinerMessage,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
        if (createRes.isSuccess) {
          val persisted = createRes.getOrThrow()
          _unloadRooms.value = _unloadRooms.value.map { if (it.id == tempId) persisted else it }
          if (_activeUnloadRoom.value?.id == tempId) {
            _activeUnloadRoom.value = persisted
          }
        }
      }
    }
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
    if (supabaseRepo.isConfigured && !roomId.startsWith("ul_") && !roomId.startsWith("room_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseRoomsAndCallsRemote.updateRoomOnSupabase(
          roomId = roomId,
          title = title,
          description = description,
          topicTag = topicTag,
          mode = mode,
          durationMinutes = durationMinutes,
          capacity = capacity,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
      }
    }
  }

  fun deleteUnloadRoom(roomId: String) {
    _unloadRooms.value = _unloadRooms.value.filterNot { it.id == roomId }
    if (_activeUnloadRoom.value?.id == roomId) _activeUnloadRoom.value = null
    if (supabaseRepo.isConfigured && !roomId.startsWith("ul_") && !roomId.startsWith("room_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseRoomsAndCallsRemote.deleteRoomOnSupabase(
          roomId = roomId,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
      }
    }
  }

  fun submitDoubleTakeAnswer(promptId: String, answer: String) {
    val me = _currentUser.value ?: return
    val target = _doubleTakePrompts.value.firstOrNull { it.id == promptId } ?: return
    _doubleTakePrompts.value = _doubleTakePrompts.value.map { p ->
      if (p.id == promptId) p.copy(myAnswer = answer, isMyAnswerSubmitted = true, isRevealed = p.isPeerAnswerSubmitted) else p
    }
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.submitDoubleTakeAnswer(target, me.id, answer, isPublicAnswer = false, authHeader = SupabaseConfig.buildAuthHeader(token))
        syncExploreAndLiveFromSupabase(token)
      }
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
    val tempId = "dt_${System.currentTimeMillis()}"
    val newPrompt = DoubleTakePrompt(
      id = tempId,
      promptQuestion = question,
      category = category,
      optionA = optionA,
      optionB = optionB,
      creatorId = user.id,
      peerUser = peerUser,
      isPublic = isPublic
    )
    _doubleTakePrompts.value = listOf(newPrompt) + _doubleTakePrompts.value
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val createdId = SupabaseExploreAndLiveRemote.createDoubleTake(
          creatorId = user.id,
          partnerId = peerUser.id.takeIf { it.isNotBlank() && it != user.id },
          question = question,
          category = category,
          optionA = optionA,
          optionB = optionB,
          isPublic = isPublic,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
        if (createdId != null) {
          _doubleTakePrompts.value = _doubleTakePrompts.value.map {
            if (it.id == tempId) it.copy(id = createdId) else it
          }
          syncExploreAndLiveFromSupabase(token)
        }
      }
    }
  }

  fun submitPublicDoubleTakeAnswer(promptId: String, answer: String) {
    val user = _currentUser.value ?: return
    val target = _doubleTakePrompts.value.firstOrNull { it.id == promptId } ?: return
    _doubleTakePrompts.value = _doubleTakePrompts.value.map { prompt ->
      if (prompt.id == promptId) {
        val ans = PublicAnswer("pa_${System.currentTimeMillis()}", user, answer, "Just now", false)
        prompt.copy(publicAnswers = prompt.publicAnswers + ans, myAnswer = answer, isMyAnswerSubmitted = true)
      } else prompt
    }
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.submitDoubleTakeAnswer(target, user.id, answer, isPublicAnswer = true, authHeader = SupabaseConfig.buildAuthHeader(token))
        syncExploreAndLiveFromSupabase(token)
      }
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
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.deleteDoubleTake(promptId, SupabaseConfig.buildAuthHeader(token))
      }
    }
  }

  fun acceptDoubleTakeMatch(promptId: String) {
    _doubleTakePrompts.value = _doubleTakePrompts.value.map {
      if (it.id == promptId) it.copy(isCreatorAccepted = true, isChatUnlocked = true) else it
    }
  }

  fun votePrediction(predictionId: String, optionId: Int) {
    val me = _currentUser.value
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
    if (supabaseRepo.isConfigured && me != null && !predictionId.startsWith("pred_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.voteAudiencePool(predictionId, me.id, optionId, SupabaseConfig.buildAuthHeader(token))
        syncExploreAndLiveFromSupabase(token)
      }
    }
  }

  fun createPredictionPoll(title: String, category: String, durationHours: Int, optionTexts: List<String>) {
    val user = _currentUser.value ?: return
    val tempId = "pred_${System.currentTimeMillis()}"
    val opts = optionTexts.mapIndexed { idx, txt -> PredictionOption(idx + 1, txt, 0, 0) }
    val poll = SocialPrediction(
      id = tempId,
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
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val created = SupabaseExploreAndLiveRemote.createAudiencePool(
          creator = user,
          title = title,
          category = category,
          durationHours = durationHours,
          optionTexts = optionTexts,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
        if (created != null) {
          _predictions.value = _predictions.value.map { if (it.id == tempId) created else it }
        }
      }
    }
  }

  fun deletePredictionPoll(pollId: String) {
    _predictions.value = _predictions.value.filterNot { it.id == pollId }
    if (supabaseRepo.isConfigured && !pollId.startsWith("pred_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.deleteAudiencePool(pollId, SupabaseConfig.buildAuthHeader(token))
      }
    }
  }

  fun createTimeCapsule(title: String, message: String, recipient: String, unlockDate: String) {
    val user = _currentUser.value ?: return
    val tempId = "tc_${System.currentTimeMillis()}"
    val capsule = TimeCapsule(
      id = tempId,
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
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val createdId = SupabaseExploreAndLiveRemote.createTimeCapsule(
          creatorId = user.id,
          title = title,
          recipient = recipient,
          message = message,
          unlockDateRaw = unlockDate,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
        if (createdId != null) {
          _timeCapsules.value = _timeCapsules.value.map { if (it.id == tempId) it.copy(id = createdId) else it }
        }
      }
    }
  }

  fun unlockTimeCapsule(capsuleId: String) {
    _timeCapsules.value = _timeCapsules.value.map { if (it.id == capsuleId) it.copy(isUnlocked = true) else it }
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.unlockTimeCapsule(capsuleId, SupabaseConfig.buildAuthHeader(token))
      }
    }
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
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.addBusinessReview(shopId, user.id, rating, reviewText, SupabaseConfig.buildAuthHeader(token))
      }
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
    if (supabaseRepo.isConfigured && user != null) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.createBusinessShop(
          ownerId = user.id,
          businessName = businessName,
          category = category,
          gstNumber = gstOrReg,
          description = "Official VYBE community business ($email • $phone)",
          city = address,
          fullAddress = address,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
        syncExploreAndLiveFromSupabase(token)
      }
    }
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
    val me = _currentUser.value
    _crossedPaths.value = _crossedPaths.value.map {
      if (it.id == encounterId) it.copy(hasSentRequest = true, requestStatus = ConnectionRequestStatus.PENDING) else it
    }
    if (supabaseRepo.isConfigured && me != null) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        SupabaseExploreAndLiveRemote.sendCrossedPathRequest(encounterId, me.id, SupabaseConfig.buildAuthHeader(token))
      }
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
    val tempId = "cp_${System.currentTimeMillis()}"
    val encounter = CrossedPathEncounter(
      id = tempId,
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
    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken)
        val createdId = SupabaseExploreAndLiveRemote.createCrossedPath(
          creatorId = me.id,
          type = type,
          title = title,
          venue = cleanVenue,
          broadLocation = cleanBroad,
          description = description.trim(),
          photoUrl = photoUrl,
          authHeader = SupabaseConfig.buildAuthHeader(token)
        )
        if (createdId != null) {
          _crossedPaths.value = _crossedPaths.value.map { if (it.id == tempId) it.copy(id = createdId) else it }
        }
      }
    }
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
    val store = appContext?.let { VybeLocalStore.getInstance(it) } ?: VybeLocalStore.get()
    store?.saveTimeManagementSettings(breakMinutes, isEnabled)
    _timeManagementStats.value = _timeManagementStats.value.copy(
      breakReminderMinutes = breakMinutes,
      isBreakReminderEnabled = isEnabled
    )
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

  // =========================================================================
  // COMMUNITY ZONE (SUPABASE-BACKED COMMUNITIES, POSTS, COMMENTS & MEMBERSHIP)
  // =========================================================================

  fun fetchCommunities() {
    if (!supabaseRepo.isConfigured) return
    ioScope.launch {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      val authHeader = SupabaseConfig.buildAuthHeader(token)
      val myId = _currentUser.value?.id.orEmpty()
      SupabaseExploreAndLiveRemote.fetchCommunities(myId, authHeader)?.let { remote ->
        _communities.value = remote
        val cur = _activeCommunity.value
        if (cur != null) {
          remote.firstOrNull { it.id == cur.id }?.let { _activeCommunity.value = it }
        }
      }
    }
  }

  fun openCommunity(community: Community) {
    _activeCommunity.value = community
    _communityPosts.value = emptyList()
    _communityMembers.value = emptyList()
    if (!supabaseRepo.isConfigured) return
    ioScope.launch {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      val authHeader = SupabaseConfig.buildAuthHeader(token)
      val myId = _currentUser.value?.id.orEmpty()
      val usersMap = _allUsers.value.associateBy { it.id }
      SupabaseExploreAndLiveRemote.fetchCommunityPosts(community.id, myId, usersMap, authHeader)?.let {
        _communityPosts.value = it
      }
      _communityMembers.value = SupabaseExploreAndLiveRemote.fetchCommunityMembers(community.id, usersMap, authHeader)
    }
  }

  fun closeActiveCommunity() {
    _activeCommunity.value = null
    _communityPosts.value = emptyList()
    _communityMembers.value = emptyList()
  }

  fun createCommunity(
    name: String,
    description: String,
    category: String,
    isPrivate: Boolean
  ) {
    val me = _currentUser.value ?: return
    val cleanName = name.trim()
    if (cleanName.isBlank()) return

    val optimisticId = "comm_local_${System.currentTimeMillis()}"
    val optimistic = Community(
      id = optimisticId,
      name = cleanName,
      description = description.trim(),
      category = category.trim().ifBlank { "General" },
      creatorId = me.id,
      isPrivate = isPrivate,
      membersCount = 1,
      isJoined = true,
      membershipStatus = "ACCEPTED",
      myRole = "ADMIN",
      createdAtIso = java.time.Instant.now().toString()
    )
    _communities.value = listOf(optimistic) + _communities.value
    _activeCommunity.value = optimistic
    _communityPosts.value = emptyList()
    _communityMembers.value = listOf(
      CommunityMember(
        communityId = optimisticId,
        user = me,
        role = "ADMIN",
        status = "ACCEPTED",
        joinedAtIso = optimistic.createdAtIso
      )
    )

    if (supabaseRepo.isConfigured) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        val created = SupabaseExploreAndLiveRemote.createCommunity(
          creatorId = me.id,
          name = cleanName,
          description = description,
          category = category,
          isPrivate = isPrivate,
          authHeader = authHeader
        )
        if (created != null) {
          _communities.value = _communities.value.map { if (it.id == optimisticId) created else it }
          if (_activeCommunity.value?.id == optimisticId) {
            _activeCommunity.value = created
          }
          val usersMap = _allUsers.value.associateBy { it.id }
          _communityMembers.value = SupabaseExploreAndLiveRemote.fetchCommunityMembers(created.id, usersMap, authHeader)
        }
      }
    }
  }

  fun toggleJoinCommunity(communityId: String) {
    val me = _currentUser.value ?: return
    val target = _communities.value.firstOrNull { it.id == communityId } ?: return
    if (target.creatorId == me.id) return // Creator remains ADMIN

    val currentlyMemberOrPending = target.isJoined || target.membershipStatus == "PENDING"
    val nextStatus = when {
      currentlyMemberOrPending -> "NONE"
      target.isPrivate -> "PENDING"
      else -> "ACCEPTED"
    }
    val nextJoined = nextStatus == "ACCEPTED"
    val nextCount = when {
      target.isJoined && !nextJoined -> (target.membersCount - 1).coerceAtLeast(1)
      !target.isJoined && nextJoined -> target.membersCount + 1
      else -> target.membersCount
    }

    val updatedCommunity = target.copy(
      isJoined = nextJoined,
      membershipStatus = nextStatus,
      membersCount = nextCount
    )
    _communities.value = _communities.value.map { if (it.id == communityId) updatedCommunity else it }
    if (_activeCommunity.value?.id == communityId) {
      _activeCommunity.value = updatedCommunity
    }

    if (supabaseRepo.isConfigured && !communityId.startsWith("comm_local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        if (currentlyMemberOrPending) {
          SupabaseExploreAndLiveRemote.leaveCommunity(communityId, me.id, authHeader)
        } else {
          SupabaseExploreAndLiveRemote.joinCommunity(communityId, me.id, target.isPrivate, authHeader)
        }
        val usersMap = _allUsers.value.associateBy { it.id }
        if (_activeCommunity.value?.id == communityId) {
          _communityMembers.value = SupabaseExploreAndLiveRemote.fetchCommunityMembers(communityId, usersMap, authHeader)
        }
      }
    }
  }

  fun createCommunityPost(
    communityId: String,
    title: String,
    content: String,
    mediaUri: android.net.Uri? = null
  ) {
    val me = _currentUser.value ?: return
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank()) return

    // Enforce membership permission if community is private
    val comm = _communities.value.firstOrNull { it.id == communityId } ?: _activeCommunity.value
    if (comm != null && comm.isPrivate && !comm.isJoined && comm.creatorId != me.id) {
      return
    }

    val tempId = "cp_local_${System.currentTimeMillis()}"
    val nowIso = java.time.Instant.now().toString()
    val optimistic = CommunityPost(
      id = tempId,
      communityId = communityId,
      author = me,
      title = cleanTitle,
      content = content.trim(),
      mediaUrl = mediaUri?.toString(),
      likesCount = 0,
      commentsCount = 0,
      isLikedByMe = false,
      isEdited = false,
      createdAtIso = nowIso,
      updatedAtIso = nowIso
    )
    _communityPosts.value = listOf(optimistic) + _communityPosts.value

    if (supabaseRepo.isConfigured && !communityId.startsWith("comm_local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        var uploadedUrl: String? = null
        val ctx = appContext
        if (mediaUri != null && ctx != null) {
          val payload = MediaUtils.readMediaPayload(ctx, mediaUri.toString())
          if (payload != null) {
            val fileName = "comm_${UUID.randomUUID()}.${payload.extension}"
            val uploadRes = supabaseRepo.uploadMediaWithProgress(
              bucket = "posts",
              userId = me.id,
              fileName = fileName,
              bytes = payload.bytes,
              mimeType = payload.mimeType,
              accessToken = token
            )
            uploadedUrl = uploadRes.getOrNull()?.url
          }
        }
        val usersMap = _allUsers.value.associateBy { it.id }
        val created = SupabaseExploreAndLiveRemote.createCommunityPost(
          communityId = communityId,
          authorId = me.id,
          title = cleanTitle,
          content = content,
          mediaUrl = uploadedUrl,
          usersById = usersMap,
          authHeader = authHeader
        )
        if (created != null) {
          _communityPosts.value = _communityPosts.value.map { if (it.id == tempId) created else it }
        }
      }
    }
  }

  fun editCommunityPost(postId: String, newTitle: String, newContent: String) {
    val me = _currentUser.value ?: return
    val cleanTitle = newTitle.trim()
    if (cleanTitle.isBlank()) return
    val existing = _communityPosts.value.firstOrNull { it.id == postId } ?: return
    if (existing.author.id != me.id) return // Author-only edit enforced client-side & via Supabase RLS

    val nowIso = java.time.Instant.now().toString()
    _communityPosts.value = _communityPosts.value.map { post ->
      if (post.id == postId) {
        post.copy(
          title = cleanTitle,
          content = newContent.trim(),
          isEdited = true,
          updatedAtIso = nowIso
        )
      } else post
    }

    if (supabaseRepo.isConfigured && !postId.startsWith("cp_local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        SupabaseExploreAndLiveRemote.editCommunityPost(postId, cleanTitle, newContent, authHeader)
      }
    }
  }

  fun deleteCommunityPost(postId: String) {
    val me = _currentUser.value ?: return
    val existing = _communityPosts.value.firstOrNull { it.id == postId } ?: return
    val activeComm = _activeCommunity.value ?: _communities.value.firstOrNull { it.id == existing.communityId }
    val canDelete = existing.author.id == me.id ||
      activeComm?.creatorId == me.id ||
      activeComm?.myRole in setOf("ADMIN", "MODERATOR")
    if (!canDelete) return

    _communityPosts.value = _communityPosts.value.filterNot { it.id == postId }

    if (supabaseRepo.isConfigured && !postId.startsWith("cp_local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        if (existing.mediaUrl != null) {
          supabaseRepo.deleteStorageObject(existing.mediaUrl, token)
        }
        SupabaseExploreAndLiveRemote.deleteCommunityPost(postId, authHeader)
      }
    }
  }

  fun toggleLikeCommunityPost(postId: String) {
    val me = _currentUser.value ?: return
    val existing = _communityPosts.value.firstOrNull { it.id == postId } ?: return
    val shouldLike = !existing.isLikedByMe
    val nextCount = if (shouldLike) existing.likesCount + 1 else (existing.likesCount - 1).coerceAtLeast(0)
    _communityPosts.value = _communityPosts.value.map { post ->
      if (post.id == postId) post.copy(isLikedByMe = shouldLike, likesCount = nextCount) else post
    }

    if (supabaseRepo.isConfigured && !postId.startsWith("cp_local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        SupabaseExploreAndLiveRemote.toggleLikeCommunityPost(postId, me.id, shouldLike, authHeader)
      }
    }
  }

  fun fetchCommunityComments(postId: String) {
    if (!supabaseRepo.isConfigured || postId.startsWith("cp_local_")) return
    ioScope.launch {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      val authHeader = SupabaseConfig.buildAuthHeader(token)
      val usersMap = _allUsers.value.associateBy { it.id }
      val comments = SupabaseExploreAndLiveRemote.fetchCommunityComments(postId, usersMap, authHeader)
      _communityComments.value = _communityComments.value + (postId to comments)
    }
  }

  fun addCommunityComment(postId: String, content: String) {
    val me = _currentUser.value ?: return
    val clean = content.trim()
    if (clean.isBlank()) return

    val optimistic = CommunityComment(
      id = "cc_local_${System.currentTimeMillis()}",
      postId = postId,
      author = me,
      content = clean,
      createdAtIso = java.time.Instant.now().toString()
    )
    val prev = _communityComments.value[postId].orEmpty()
    _communityComments.value = _communityComments.value + (postId to (prev + optimistic))
    _communityPosts.value = _communityPosts.value.map { p ->
      if (p.id == postId) p.copy(commentsCount = p.commentsCount + 1) else p
    }

    if (supabaseRepo.isConfigured && !postId.startsWith("cp_local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        val usersMap = _allUsers.value.associateBy { it.id }
        val created = SupabaseExploreAndLiveRemote.addCommunityComment(postId, me.id, clean, usersMap, authHeader)
        if (created != null) {
          val updatedList = _communityComments.value[postId].orEmpty().map {
            if (it.id == optimistic.id) created else it
          }
          _communityComments.value = _communityComments.value + (postId to updatedList)
        }
      }
    }
  }

  fun fetchCommunityMembers(communityId: String) {
    if (!supabaseRepo.isConfigured || communityId.startsWith("comm_local_")) return
    ioScope.launch {
      val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
      val authHeader = SupabaseConfig.buildAuthHeader(token)
      val usersMap = _allUsers.value.associateBy { it.id }
      _communityMembers.value = SupabaseExploreAndLiveRemote.fetchCommunityMembers(communityId, usersMap, authHeader)
    }
  }

  fun updateCommunityMember(communityId: String, targetUserId: String, role: String?, status: String?) {
    val me = _currentUser.value ?: return
    val comm = _communities.value.firstOrNull { it.id == communityId } ?: _activeCommunity.value ?: return
    if (comm.creatorId != me.id && comm.myRole !in setOf("ADMIN", "MODERATOR")) return

    _communityMembers.value = _communityMembers.value.map { m ->
      if (m.user.id == targetUserId) {
        m.copy(
          role = role ?: m.role,
          status = status ?: m.status
        )
      } else m
    }

    if (supabaseRepo.isConfigured && !communityId.startsWith("comm_local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        SupabaseExploreAndLiveRemote.updateCommunityMember(communityId, targetUserId, role, status, authHeader)
        fetchCommunities()
      }
    }
  }

  fun removeCommunityMember(communityId: String, targetUserId: String) {
    val me = _currentUser.value ?: return
    val comm = _communities.value.firstOrNull { it.id == communityId } ?: _activeCommunity.value ?: return
    if (targetUserId == comm.creatorId) return
    if (comm.creatorId != me.id && comm.myRole !in setOf("ADMIN", "MODERATOR") && me.id != targetUserId) return

    _communityMembers.value = _communityMembers.value.filterNot { it.user.id == targetUserId }
    val nextCount = (comm.membersCount - 1).coerceAtLeast(1)
    _communities.value = _communities.value.map { if (it.id == communityId) it.copy(membersCount = nextCount) else it }
    if (_activeCommunity.value?.id == communityId) {
      _activeCommunity.value = _activeCommunity.value?.copy(membersCount = nextCount)
    }

    if (supabaseRepo.isConfigured && !communityId.startsWith("comm_local_")) {
      ioScope.launch {
        val token = supabaseRepo.resolveValidAccessToken(SessionManager.get()?.accessToken).orEmpty()
        val authHeader = SupabaseConfig.buildAuthHeader(token)
        SupabaseExploreAndLiveRemote.removeCommunityMember(communityId, targetUserId, authHeader)
      }
    }
  }
}
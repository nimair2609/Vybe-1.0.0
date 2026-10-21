package com.example.data.repository

import android.content.Context
import com.example.data.model.*
import com.example.data.remote.SupabaseRepository
import com.example.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

class VybeRepository private constructor() {

  companion object {
    @Volatile
    private var INSTANCE: VybeRepository? = null

    fun getInstance(): VybeRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: VybeRepository().also { INSTANCE = it }
      }
    }
  }

  // Pre-configured Verified Users & Creators
  private val userAarav = User(
    id = "u_aarav",
    username = "aarav_beats",
    displayName = "Aarav Mehta",
    firstName = "Aarav",
    lastName = "Mehta",
    bio = "Music Producer & Sound Designer 🎹 | Mumbai lo-fi sets | Mood: Chill vibes",
    avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400",
    coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1000",
    role = UserRole.CREATOR,
    isVerified = true,
    isVip = false,
    isVerifiedPublicFigure = true,
    vipBadgeTitle = "Top Creator 2026",
    followersCount = 42800,
    followingCount = 312,
    postsCount = 124,
    currentMood = MoodType.CHILL,
    livePulse = LivePulseStatus.LISTENING_MUSIC
  )

  private val userPriya = User(
    id = "u_priya",
    username = "priya_design",
    displayName = "Priya Krishnamurthy",
    firstName = "Priya",
    lastName = "Krishnamurthy",
    bio = "Senior Product Designer in Bengaluru 🎨 | Coffee snob ☕ | Building human-centered UI",
    avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400",
    coverUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1000",
    role = UserRole.USER,
    isVerified = true,
    isVip = false,
    followersCount = 8900,
    followingCount = 420,
    postsCount = 88,
    currentMood = MoodType.HAPPY,
    livePulse = LivePulseStatus.FEELING_CREATIVE
  )

  private val userVikramCricket = User(
    id = "u_vikram",
    username = "vikram_cricket",
    displayName = "Vikram Rathore",
    firstName = "Vikram",
    lastName = "Rathore",
    bio = "Cricket Analyst & Former State Player 🏏 | Tactical breakdowns & pitch analysis",
    avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400",
    role = UserRole.VERIFIED_PUBLIC_FIGURE,
    isVerified = true,
    isVip = true,
    followersCount = 189000,
    followingCount = 145,
    postsCount = 412,
    currentMood = MoodType.FUN,
    livePulse = LivePulseStatus.OPEN_TO_CHAT
  )

  private val userMe = User(
    id = "u_me",
    username = "sid_verma",
    displayName = "Siddharth Verma",
    firstName = "Siddharth",
    lastName = "Verma",
    bio = "Full-stack engineer & street photographer 📸 | Exploring Mumbai alleyways | Age 22 (Verified 16+)",
    avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400",
    coverUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=1000",
    profileVideoUrl = "https://assets.mixkit.co/videos/preview/mixkit-urban-city-traffic-at-night-42861-large.mp4",
    email = "sid.verma@example.com",
    phone = "+91 98765 43210",
    isEmailVerified = true,
    isPhoneVerified = true,
    dateOfBirth = "2004-03-15",
    age = 22,
    isUnder18 = false,
    role = UserRole.CREATOR,
    isVerified = true,
    isVip = true,
    isVerifiedPublicFigure = true,
    followersCount = 1420,
    followingCount = 385,
    postsCount = 18,
    currentMood = MoodType.HAPPY,
    livePulse = LivePulseStatus.OPEN_TO_CHAT,
    isOnboardingCompleted = true
  )

  private val supabaseRepo = SupabaseRepository()

  val isSupabaseConfigured: Boolean
    get() = supabaseRepo.isConfigured

  // Current session state (starts unauthenticated on first launch until session restored or logged in)
  private val _currentUser = MutableStateFlow<User?>(null)
  val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

  private val _isLoggedIn = MutableStateFlow(false)
  val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

  fun initSession(context: Context) {
    val session = SessionManager.getInstance(context)
    if (session.isLoggedIn) {
      val user = session.loadCurrentUser()
      if (user != null) {
        _currentUser.value = user
        _isLoggedIn.value = true
        if (_allUsers.value.none { it.id == user.id }) {
          _allUsers.value = listOf(user) + _allUsers.value
        }
      }
    }
  }

  // Registered Community Users (Searchable by username and public VYBE ID)
  private val _allUsers = MutableStateFlow<List<User>>(listOf(userMe, userAarav, userPriya, userVikramCricket))
  val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

  // VYBE Matches List (Section 4 requirement)
  private val _vybeMatches = MutableStateFlow<List<User>>(listOf(userAarav, userPriya))
  val vybeMatches: StateFlow<List<User>> = _vybeMatches.asStateFlow()

  // App Theme Mode (Persistent)
  private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
  val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

  // Posts Feed
  private val _posts = MutableStateFlow<List<Post>>(emptyList())
  val posts: StateFlow<List<Post>> = _posts.asStateFlow()

  // Dedicated UNFILTERED Feed ("Back to Real. Back to Us.")
  private val _unfilteredPosts = MutableStateFlow<List<Post>>(emptyList())
  val unfilteredPosts: StateFlow<List<Post>> = _unfilteredPosts.asStateFlow()

  // Stories
  private val _stories = MutableStateFlow<List<Story>>(emptyList())
  val stories: StateFlow<List<Story>> = _stories.asStateFlow()

  // 14.1 VYBE UNLOAD - 24/7 Live Listening Spaces
  private val _unloadRooms = MutableStateFlow<List<UnloadRoom>>(emptyList())
  val unloadRooms: StateFlow<List<UnloadRoom>> = _unloadRooms.asStateFlow()

  private val _activeUnloadRoom = MutableStateFlow<UnloadRoom?>(null)
  val activeUnloadRoom: StateFlow<UnloadRoom?> = _activeUnloadRoom.asStateFlow()

  // Crossed Paths Encounters
  private val _crossedPaths = MutableStateFlow<List<CrossedPathEncounter>>(emptyList())
  val crossedPaths: StateFlow<List<CrossedPathEncounter>> = _crossedPaths.asStateFlow()

  // Time Capsules
  private val _timeCapsules = MutableStateFlow<List<TimeCapsule>>(emptyList())
  val timeCapsules: StateFlow<List<TimeCapsule>> = _timeCapsules.asStateFlow()

  // Double Take prompts
  private val _doubleTakePrompts = MutableStateFlow<List<DoubleTakePrompt>>(emptyList())
  val doubleTakePrompts: StateFlow<List<DoubleTakePrompt>> = _doubleTakePrompts.asStateFlow()

  // Social Predictions (What Next)
  private val _predictions = MutableStateFlow<List<SocialPrediction>>(emptyList())
  val predictions: StateFlow<List<SocialPrediction>> = _predictions.asStateFlow()

  // Verified Shops
  private val _shops = MutableStateFlow<List<VybeShop>>(emptyList())
  val shops: StateFlow<List<VybeShop>> = _shops.asStateFlow()

  // Conversations
  private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
  val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

  // Active Calling
  private val _activeCall = MutableStateFlow<CallSession?>(null)
  val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

  // Following & Social Graph State
  private val _followingUserIds = MutableStateFlow<Set<String>>(setOf("u_aarav", "u_priya"))
  val followingUserIds: StateFlow<Set<String>> = _followingUserIds.asStateFlow()

  // Moderation: Blocked & Restricted Users
  private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
  val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

  private val _restrictedUserIds = MutableStateFlow<Set<String>>(emptySet())
  val restrictedUserIds: StateFlow<Set<String>> = _restrictedUserIds.asStateFlow()

  // In-flight message deduplication set
  private val inFlightMessageClientIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

  // Creator Analytics
  private val _creatorStats = MutableStateFlow(CreatorStats())
  val creatorStats: StateFlow<CreatorStats> = _creatorStats.asStateFlow()

  // Admin Reports Queue
  private val _adminReports = MutableStateFlow<List<AdminReport>>(emptyList())
  val adminReports: StateFlow<List<AdminReport>> = _adminReports.asStateFlow()

  // Notifications
  private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
  val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

  // Active Filter / Selected Mood Zone
  private val _selectedMoodFilter = MutableStateFlow<MoodType?>(null)
  val selectedMoodFilter: StateFlow<MoodType?> = _selectedMoodFilter.asStateFlow()

  // Settings & Activity State
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

  // Spiritual Mode State (Part F)
  private val _isSpiritualMode = MutableStateFlow(false)
  val isSpiritualMode: StateFlow<Boolean> = _isSpiritualMode.asStateFlow()

  private val _spiritualReflections = MutableStateFlow<List<SpiritualReflection>>(emptyList())
  val spiritualReflections: StateFlow<List<SpiritualReflection>> = _spiritualReflections.asStateFlow()

  private val _spiritualGratitudes = MutableStateFlow<List<SpiritualGratitude>>(emptyList())
  val spiritualGratitudes: StateFlow<List<SpiritualGratitude>> = _spiritualGratitudes.asStateFlow()

  private val _spiritualIntentions = MutableStateFlow<List<SpiritualIntention>>(emptyList())
  val spiritualIntentions: StateFlow<List<SpiritualIntention>> = _spiritualIntentions.asStateFlow()

  // Business Applications State (Part J)
  private val _businessApplications = MutableStateFlow<List<BusinessApplication>>(emptyList())
  val businessApplications: StateFlow<List<BusinessApplication>> = _businessApplications.asStateFlow()

  // Hidden chat PIN hash (in-memory hashed representation)
  private var hiddenChatPinHash: String = "1234".hashCode().toString()

  init {
    seedInitialData()
  }

  private fun seedInitialData() {
    _stories.value = listOf(
      Story(
        id = "s_1",
        author = userAarav,
        mediaUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
        caption = "Midnight studio session in Bandra 🎶",
        mediaType = StoryMediaType.MUSIC,
        musicTrackTitle = "Midnight Tape 04 - Aarav",
        location = "Bandra West, Mumbai",
        timestamp = "45m ago",
        isViewed = false
      ),
      Story(
        id = "s_2",
        author = userPriya,
        mediaUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600",
        caption = "Filter coffee & wireframing morning ☕ Secret preview!",
        mediaType = StoryMediaType.PHOTO,
        audience = StoryAudience.CLOSE_FRIENDS,
        location = "Indiranagar, Bengaluru",
        timestamp = "2h ago",
        isViewed = false
      ),
      Story(
        id = "s_3",
        author = userVikramCricket,
        mediaUrl = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=600",
        caption = "Matchday net practice at Wankhede! 🏟️",
        mediaType = StoryMediaType.VIDEO,
        timestamp = "3h ago",
        isViewed = false
      ),
      Story(
        id = "s_4",
        author = userPriya.copy(id = "u_ananya", username = "ananyadesign", displayName = "Ananya Sharma"),
        mediaUrl = "",
        caption = "Creativity is intelligence having fun. New drop coming Friday! ✨",
        mediaType = StoryMediaType.TEXT,
        textBackgroundColor = "NEON_PURPLE",
        timestamp = "5h ago",
        isViewed = false
      ),
      Story(
        id = "s_5",
        author = userAarav.copy(id = "u_rohan", username = "rohanroasts", displayName = "Rohan Mehta"),
        mediaUrl = "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600",
        caption = "Fresh single-origin roast from Chikmagalur ☕",
        mediaType = StoryMediaType.PHOTO,
        timestamp = "8h ago",
        isViewed = true
      )
    )

    _posts.value = listOf(
      Post(
        id = "p_1",
        author = userAarav,
        caption = "Dropped a new Bombay Monsoons lo-fi groove. Crafted with tape saturation, rain captured in Colaba, and warm Rhodes chords. Tell me what mood you're in when listening! 🌧️🎧 #MumbaiLoFi #IndieMusic #ChillVybe",
        mediaUrls = listOf("https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=800"),
        mediaType = MediaType.IMAGE,
        timestamp = "2 hours ago",
        likesCount = 384,
        commentsCount = 42,
        savesCount = 95,
        sharesCount = 18,
        isLiked = false,
        tags = listOf("MumbaiLoFi", "IndieMusic", "ChillVybe"),
        moodZone = MoodType.CHILL,
        audioTrackTitle = "Colaba Rain Tape - Aarav Beats",
        userReaction = VybeReaction.DEEP_VIBE,
        reactionsCount = mapOf(VybeReaction.DEEP_VIBE to 142, VybeReaction.CALM to 98, VybeReaction.PURE_ENERGY to 45),
        comments = listOf(
          PostComment("c_1", "p_1", userPriya, "Looping this while designing right now! Absolutely pristine mix ✨", "1h ago", 12, true),
          PostComment("c_2", "p_1", userMe, "That snare sound is ridiculous. What mic did you use on the ambient rain?", "45m ago", 5, false)
        )
      ),
      Post(
        id = "p_2",
        author = userPriya,
        caption = "Reflecting on 5 years in UX: The best interfaces are the ones that respect human attention rather than exploiting dopamine loops. VYBE 2.0 is proof we can build social without cynicism 🌱 #DesignEthics #ProductDesign",
        mediaUrls = listOf("https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=800"),
        mediaType = MediaType.IMAGE,
        timestamp = "5 hours ago",
        likesCount = 512,
        commentsCount = 68,
        savesCount = 143,
        sharesCount = 31,
        isLiked = true,
        tags = listOf("DesignEthics", "ProductDesign", "Mindfulness"),
        moodZone = MoodType.HEALING,
        userReaction = VybeReaction.UPLIFT,
        reactionsCount = mapOf(VybeReaction.UPLIFT to 210, VybeReaction.PURE_ENERGY to 88)
      ),
      Post(
        id = "p_3",
        author = userVikramCricket,
        caption = "18 runs needed in the final over with 2 wickets remaining! What happens next? Prediction pool is live in What Next! ⚡🏏 Can the tailenders pull off a historic win?",
        mediaUrls = listOf("https://images.unsplash.com/photo-1531415074868-036b107e775a?w=800"),
        mediaType = MediaType.IMAGE,
        timestamp = "7 hours ago",
        likesCount = 1420,
        commentsCount = 312,
        savesCount = 88,
        sharesCount = 205,
        isLiked = false,
        tags = listOf("CricketIndia", "Prediction", "IndVsAus"),
        moodZone = MoodType.FUN,
        userReaction = VybeReaction.HEAT,
        reactionsCount = mapOf(VybeReaction.HEAT to 450, VybeReaction.PURE_ENERGY to 320)
      ),
      Post(
        id = "p_4",
        author = userMe,
        caption = "Golden hour across Bandra-Worli Sea Link. Shot on 35mm film prime. Mumbai never stops moving, but if you look up for a second, the light is unreal ✨🌇",
        mediaUrls = listOf("https://images.unsplash.com/photo-1570168007204-dfb528c6958f?w=800"),
        mediaType = MediaType.IMAGE,
        timestamp = "1 day ago",
        likesCount = 295,
        commentsCount = 19,
        savesCount = 54,
        sharesCount = 12,
        isLiked = false,
        tags = listOf("MumbaiPhotographers", "SeaLink", "StreetVibe"),
        moodZone = MoodType.HAPPY,
        isVybesReel = true,
        reactionsCount = mapOf(VybeReaction.PURE_ENERGY to 110, VybeReaction.UPLIFT to 65)
      )
    )

    // Dedicated UNFILTERED authentic moments ("Back to Real. Back to Us.")
    _unfilteredPosts.value = listOf(
      Post(
        id = "uf_1",
        author = userMe,
        caption = "2:30 AM desk reality. Untangled headphones, three half-drunk chai cups, and open debugger tabs. No aesthetic filters, just real building.",
        mediaUrls = listOf("https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800"),
        mediaType = MediaType.IMAGE,
        timestamp = "3 hours ago",
        isUnfiltered = true,
        commentsCount = 8,
        comments = listOf(
          PostComment("ufc_1", "uf_1", userAarav, "The cold chai cups are universal law ☕", "2h ago", 0, false),
          PostComment("ufc_2", "uf_1", userPriya, "True engineering habitat! Proud of you Sid", "1h ago", 0, false)
        )
      ),
      Post(
        id = "uf_2",
        author = userPriya,
        caption = "Indiranagar afternoon thunderstorm hitting the balcony plants. Raw sound of thunder on terracotta tiles. No music, no color grading.",
        mediaUrls = listOf("https://images.unsplash.com/photo-1534274988757-a28bf1a57c17?w=800"),
        mediaType = MediaType.IMAGE,
        timestamp = "6 hours ago",
        isUnfiltered = true,
        commentsCount = 5,
        comments = listOf(
          PostComment("ufc_3", "uf_2", userMe, "Petrichor through the screen 🌱", "5h ago", 0, false)
        )
      )
    )

    // 14.1 VYBE UNLOAD - 24/7 Live Listening Spaces
    _unloadRooms.value = listOf(
      UnloadRoom(
        id = "ul_1",
        title = "Late Night Overthinking & Lo-Fi Space",
        description = "A quiet room for anyone struggling to sleep or carrying thoughts they cannot speak out loud at home. Text and voice available.",
        topicTag = "Late Night Overthinking",
        mode = UnloadRoomMode.VOICE_AND_TEXT,
        activeListenersCount = 18,
        isHostOnline = true,
        recentMessages = listOf(
          UnloadMessage("ulm_1", "Midnight Wanderer", true, "Sometimes the weight of expectations just feels very heavy at 2 AM.", "10 mins ago"),
          UnloadMessage("ulm_2", "Quiet Companion", true, "You are not alone in that feeling. Take a slow deep breath.", "8 mins ago"),
          UnloadMessage("ulm_3", "Aarav (Host)", false, "Gentle lo-fi piano playing. Feel free to speak or simply rest here.", "3 mins ago")
        )
      ),
      UnloadRoom(
        id = "ul_2",
        title = "Work Burnout & Corporate Fatigue",
        description = "Vent safely about toxic deadlines, corporate micromanagement, and feeling exhausted. Zero retention of messages.",
        topicTag = "Work Burnout",
        mode = UnloadRoomMode.TEXT_ONLY,
        activeListenersCount = 11,
        isHostOnline = true,
        recentMessages = listOf(
          UnloadMessage("ulm_4", "Burnt Out Engineer", true, "Shipped a 6-month feature and management asked for two more by Monday.", "15 mins ago"),
          UnloadMessage("ulm_5", "Fellow Traveler", true, "Set your boundary. Your physical health outlasts any company.", "11 mins ago")
        )
      ),
      UnloadRoom(
        id = "ul_3",
        title = "Silent Solidarity Room",
        description = "Presence without obligation. No talking required. Just presence and warmth for those having a hard day.",
        topicTag = "Silent Company",
        mode = UnloadRoomMode.TEXT_ONLY,
        activeListenersCount = 26,
        isHostOnline = true
      )
    )

    _crossedPaths.value = listOf(
      CrossedPathEncounter(
        id = "cp_1",
        type = CrossedPathType.MISSED_CONNECTION,
        title = "Third Wave Coffee, Indiranagar",
        description = "You were reading 'Klara and the Sun' at the corner wooden bench around 4 PM, wearing a navy trench and drinking an iced aero-press. We smiled when sharing the sugar jar.",
        broadLocation = "Indiranagar, Bengaluru",
        venueOrEventName = "Third Wave Coffee 12th Main",
        encounterDate = "Yesterday, ~4:15 PM",
        poster = userAarav,
        isAnonymous = true,
        expiresHoursRemaining = 58,
        tags = listOf("CoffeeShop", "Reading", "Books")
      ),
      CrossedPathEncounter(
        id = "cp_2",
        type = CrossedPathType.VENUE_EVENT,
        title = "Prithvi Theatre Festival",
        description = "Both opted-in during intermission near the outdoor chai stall. Discussing the Marathi experimental play.",
        broadLocation = "Juhu, Mumbai",
        venueOrEventName = "Prithvi Theatre",
        encounterDate = "Oct 18, 8:30 PM",
        poster = userPriya,
        isAnonymous = false,
        expiresHoursRemaining = 42,
        tags = listOf("Theatre", "ArtEncounter", "MumbaiVibe")
      )
    )

    _timeCapsules.value = listOf(
      TimeCapsule(
        id = "tc_1",
        title = "Start of VYBE Journey & Goals",
        sender = userMe,
        recipientSummary = "To my future self",
        sealedMessage = "Remember why we built this: A social platform that puts human kindness, mood solidarity, and safe connection above endless mindless doom-scrolling. Keep the craft pure!",
        unlockDateFormatted = "Jan 01, 2027",
        unlockTimestampMs = System.currentTimeMillis() + 86400000L * 72,
        isUnlocked = false,
        createdAtFormatted = "Oct 19, 2026",
        categoryTheme = "Personal Milestone"
      ),
      TimeCapsule(
        id = "tc_2",
        title = "Acoustic Demo Sealed",
        sender = userAarav,
        recipientSummary = "To @sid_verma",
        sealedMessage = "Hey Sid! Here's the early acoustic guitar master before we add the full brass section. Hope your project launched strong!",
        unlockDateFormatted = "Today (Eligible to unlock)",
        unlockTimestampMs = System.currentTimeMillis() - 1000L,
        isUnlocked = true,
        createdAtFormatted = "Oct 01, 2026",
        categoryTheme = "Creative Collaboration"
      )
    )

    _doubleTakePrompts.value = listOf(
      DoubleTakePrompt(
        id = "dt_1",
        promptQuestion = "What song instantly teleports you back to your teenage bedroom?",
        category = "Music & Nostalgia",
        peerUser = userPriya,
        peerAnswer = "Lucky Ali - O Sanam 🎸",
        isMyAnswerSubmitted = false,
        isPeerAnswerSubmitted = true,
        isRevealed = false
      ),
      DoubleTakePrompt(
        id = "dt_2",
        promptQuestion = "If you could take a spontaneous midnight road trip right now, where to?",
        category = "Travel Vibe",
        myAnswer = "Lonavala foggy viewpoint 🚗",
        peerUser = userAarav,
        peerAnswer = "Goa beach shack by dawn 🌊",
        isMyAnswerSubmitted = true,
        isPeerAnswerSubmitted = true,
        isRevealed = true,
        matchReactionsCount = 28
      )
    )

    _predictions.value = listOf(
      SocialPrediction(
        id = "pred_1",
        title = "India vs Australia T20: Final Over Drama",
        category = "Cricket",
        closingTimeText = "Closes in 45 mins",
        totalVotes = 12450,
        options = listOf(
          PredictionOption(1, "India wins via boundary", 6820, 55),
          PredictionOption(2, "Super Over shootout!", 3850, 31),
          PredictionOption(3, "Wicket falls on ball 6", 1780, 14)
        )
      ),
      SocialPrediction(
        id = "pred_2",
        title = "National Film Awards: Best Original Score",
        category = "Cinema",
        closingTimeText = "Closes Friday 8:00 PM",
        totalVotes = 8900,
        options = listOf(
          PredictionOption(1, "AR Rahman (Maidaan)", 4900, 55),
          PredictionOption(2, "Anirudh (Jawan)", 2900, 33),
          PredictionOption(3, "Amit Trivedi (Qala)", 1100, 12)
        )
      )
    )

    _shops.value = listOf(
      VybeShop(
        id = "shop_1",
        businessName = "Subko Coffee Roasters & Craft Bakehouse",
        category = "Specialty Coffee & Artisan Pastries",
        description = "Celebrating the subcontinent’s craft coffee and cacao farmers. Sourced ethically from Western Ghats estates.",
        logoUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=400",
        coverUrl = "https://images.unsplash.com/photo-1442512595331-e89e73853f31?w=1000",
        rating = 4.9f,
        reviewsCount = 342,
        isVerifiedBusiness = true,
        openingHours = "7:30 AM - 10:00 PM",
        locationAddress = "Chapel Rd, Bandra West, Mumbai",
        catalog = listOf(
          CatalogItem("ci_1", "Iced Lavender Bloom Pour-over", "₹320", "Cold brew infused with organic lavender and citrus peel.", "https://images.unsplash.com/photo-1517701550927-30cf4ba1dba5?w=400"),
          CatalogItem("ci_2", "Sourdough Podi Croissant", "₹260", "Flaky 72-hour sourdough lamination spiced with Gunpowder ghee podi.", "https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=400"),
          CatalogItem("ci_3", "Estate Whole Beans (Anamalai 250g)", "₹650", "Medium roast with notes of raw honey, figs, and cocoa nibs.", "https://images.unsplash.com/photo-1587734195503-904fca47e0e9?w=400")
        ),
        reviews = listOf(
          ShopReview("sr_1", userPriya, 5, "Hands down the best cold brew in Bandra. Beautiful courtyard atmosphere.", "2 days ago", true, "Thanks Priya! Our baristas love having you over."),
          ShopReview("sr_2", userAarav, 5, "The Podi croissant is pure genius. Always stop by before studio sessions.", "1 week ago", true, "Appreciate the love Aarav!")
        )
      ),
      VybeShop(
        id = "shop_2",
        businessName = "Bhaane India",
        category = "Contemporary Streetwear & Apparel",
        description = "Design philosophy rooted in relaxed silhouettes, breathable organic cotton, and contemporary Indian youth subculture.",
        logoUrl = "https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=400",
        coverUrl = "https://images.unsplash.com/photo-1489987707025-afc232f7ea0f?w=1000",
        rating = 4.8f,
        reviewsCount = 189,
        isVerifiedBusiness = true,
        openingHours = "11:00 AM - 9:00 PM",
        locationAddress = "Khar Danda Road, Mumbai",
        catalog = listOf(
          CatalogItem("ci_4", "Oversized Raw Indigo Boxy Tee", "₹1,850", "Heavyweight 280 GSM handloom cotton with dropped shoulders.", "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=400"),
          CatalogItem("ci_5", "Khadi Utility Cargo Pants", "₹3,400", "Handspun breathable khadi with deep structured cargo pockets.", "https://images.unsplash.com/photo-1517445312882-bc9910d016b7?w=400")
        )
      )
    )

    _conversations.value = listOf(
      Conversation(
        id = "conv_1",
        peerUser = userAarav,
        lastMessage = "Sent the early mix over! Check the Time Capsule too 🔥",
        lastMessageTimestamp = "15m ago",
        unreadCount = 1,
        messages = listOf(
          DirectMessage("m_1", userAarav.id, "Yo Sid! How is the VYBE launch coming along?", "10:30 AM", false),
          DirectMessage("m_2", userMe.id, "Architecting the full stack right now! Mood Zones and Crossed Paths are looking super slick.", "10:34 AM", true),
          DirectMessage("m_3", userAarav.id, "Sent the early mix over! Check the Time Capsule too 🔥", "10:45 AM", false)
        )
      ),
      Conversation(
        id = "conv_2",
        peerUser = userPriya,
        lastMessage = "Let's grab filter coffee at Subko tomorrow!",
        lastMessageTimestamp = "2h ago",
        unreadCount = 0,
        messages = listOf(
          DirectMessage("m_4", userPriya.id, "Hey! Loved your Sea Link photo on the feed.", "Yesterday", false),
          DirectMessage("m_5", userMe.id, "Thank you Priya! Golden hour light was miraculous.", "Yesterday", true),
          DirectMessage("m_6", userPriya.id, "Let's grab filter coffee at Subko tomorrow!", "2h ago", false)
        )
      )
    )

    _adminReports.value = listOf(
      AdminReport(
        id = "rep_1",
        targetType = ReportTargetType.POST,
        targetId = "p_spam_99",
        summaryText = "Suspicious cryptocurrency bot duplicate comment spam across Bollywood posts",
        reportedByUsername = "priya_design",
        reportedUser = "crypto_guru_bot",
        reason = "Automated Scam / Spam",
        timestamp = "30 mins ago",
        status = ReportStatus.PENDING,
        severity = "High"
      ),
      AdminReport(
        id = "rep_2",
        targetType = ReportTargetType.USER_PROFILE,
        targetId = "u_appeals_16",
        summaryText = "Age safety appeal: User self-reports age 17 turning 18, requesting minor protection status confirmation",
        reportedByUsername = "system_safety_guard",
        reportedUser = "rohit_teen",
        reason = "Age Verification Review (Under 18 Safe Mode)",
        timestamp = "2 hours ago",
        status = ReportStatus.PENDING,
        severity = "Medium"
      )
    )

    _notifications.value = listOf(
      NotificationItem(
        id = "notif_1",
        type = NotificationType.LIKE,
        title = "Priya reacted to your post",
        message = "Uplift reaction on Golden hour across Bandra-Worli Sea Link.",
        timestamp = "15m ago",
        actorUser = userPriya,
        destinationScreen = "MAIN"
      ),
      NotificationItem(
        id = "notif_2",
        type = NotificationType.COMMENT_OR_REPLY,
        title = "Vikram commented",
        message = "'Incredible acoustic composition, truly peaceful.'",
        timestamp = "32m ago",
        actorUser = userVikramCricket,
        destinationScreen = "MAIN"
      ),
      NotificationItem(
        id = "notif_3",
        type = NotificationType.TIME_CAPSULE_UNLOCK,
        title = "Time Capsule Unlocked! ⏳",
        message = "Aarav Mehta's sealed message 'Acoustic Demo Sealed' is now available to view.",
        timestamp = "1h ago",
        actorUser = userAarav,
        destinationScreen = "TIME_CAPSULES_VIEW"
      ),
      NotificationItem(
        id = "notif_4",
        type = NotificationType.ROOM_INVITATION,
        title = "Unload Room Invitation 🎙️",
        message = "You're invited to join 'Tech Builders & Midnight Creative Unload' by Aarav.",
        timestamp = "2h ago",
        actorUser = userAarav,
        destinationScreen = "UNLOAD_VIEW"
      ),
      NotificationItem(
        id = "notif_5",
        type = NotificationType.CROSSED_PATHS_INTERACTION,
        title = "Crossed Paths Encounter 💫",
        message = "Someone in Indiranagar noticed your presence and sent a private handshake request.",
        timestamp = "3h ago",
        actorUser = null,
        destinationScreen = "CREATE_CROSSED_PATH"
      ),
      NotificationItem(
        id = "notif_6",
        type = NotificationType.DOUBLE_TAKE_RESPONSE,
        title = "Double Take Answer Received 🎭",
        message = "Priya answered YES to: 'Do you believe architecture shapes music?'",
        timestamp = "5h ago",
        actorUser = userPriya,
        destinationScreen = "DOUBLE_TAKE_VIEW"
      ),
      NotificationItem(
        id = "notif_7",
        type = NotificationType.BUSINESS_VERIFICATION_UPDATE,
        title = "Business Shop Verified ✅",
        message = "Your shop 'Artisan Brews & Roastery' is now officially verified by NR Next Safety.",
        timestamp = "1d ago",
        actorUser = null,
        destinationScreen = "SHOP_DETAIL"
      ),
      NotificationItem(
        id = "notif_8",
        type = NotificationType.SECURITY_ACCOUNT_UPDATE,
        title = "Security & Session Alert 🛡️",
        message = "New login from Pixel 9 Pro (Mumbai, India) verified successfully.",
        timestamp = "2d ago",
        actorUser = null,
        destinationScreen = "SETTINGS"
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
      ),
      SpiritualReflection(
        id = "sr_3",
        quote = "The soul that sees beauty may sometimes walk alone, but is never without light.",
        traditionOrAuthor = "Johann Wolfgang von Goethe",
        theme = "Hope",
        audioCue = "Forest Mist Winds"
      ),
      SpiritualReflection(
        id = "sr_4",
        quote = "When you arise in the morning think of what a privilege it is to be alive, to breathe, to enjoy, to love.",
        traditionOrAuthor = "Marcus Aurelius",
        theme = "Gratitude",
        audioCue = "Morning Chimes"
      )
    )

    _spiritualGratitudes.value = listOf(
      SpiritualGratitude("sg_1", "Grateful for morning sunlight warming the studio and good coffee.", "Today at 7:15 AM"),
      SpiritualGratitude("sg_2", "Thankful for the calm conversation with an old friend yesterday.", "Yesterday")
    )

    _spiritualIntentions.value = listOf(
      SpiritualIntention("si_1", "Speak with kindness and listen without haste today.", "Today", false),
      SpiritualIntention("si_2", "Take 5 mindful breaths before answering demanding messages.", "Today", true)
    )

    _supportTickets.value = listOf(
      SupportTicket(
        id = "tk_101",
        category = "Account & Verification",
        subject = "Public Figure Verification inquiry",
        message = "Submitted official press coverage portfolio for verification audit.",
        status = "Investigating",
        createdAt = "Yesterday",
        agentResponse = "NR Next Safety team is auditing your verified identity credentials."
      )
    )

    _activeDeviceSessions.value = listOf(
      ActiveDeviceSession("s_1", "Pixel 9 Pro (This Device)", "Android 15", "Mumbai, India", "Active now", true),
      ActiveDeviceSession("s_2", "MacBook Pro 16\"", "Chrome Web", "Bengaluru, India", "Yesterday 6:40 PM", false)
    )

    _userActivity.value = listOf(
      UserActivityItem("act_1", "POST_CREATED", "Shared a post", "Golden hour across Bandra-Worli Sea Link", "1 day ago"),
      UserActivityItem("act_2", "REACTION_GIVEN", "Reacted ✨ Uplift", "On Priya's post 'Reflecting on 5 years in UX'", "5 hours ago"),
      UserActivityItem("act_3", "CAPSULE_SEALED", "Sealed a Time Capsule", "To my future self (Unlocks 2027)", "Yesterday")
    )
  }

  // --- ACTIONS ---

  fun setThemeMode(mode: AppThemeMode) {
    _themeMode.value = mode
  }

  fun toggleLikePost(postId: String) {
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      val post = current[index]
      val newIsLiked = !post.isLiked
      val newCount = if (newIsLiked) post.likesCount + 1 else post.likesCount - 1
      current[index] = post.copy(isLiked = newIsLiked, likesCount = maxOf(0, newCount))
      _posts.value = current
    }
  }

  fun reactToPost(postId: String, reaction: VybeReaction) {
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
      if (prevReaction != reaction) {
        currentMap[reaction] = (currentMap[reaction] ?: 0) + 1
        current[index] = post.copy(userReaction = reaction, reactionsCount = currentMap, isLiked = true)
      } else {
        current[index] = post.copy(userReaction = null, reactionsCount = currentMap, isLiked = false)
      }
      _posts.value = current
    }
  }

  fun toggleSavePost(postId: String) {
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      val post = current[index]
      val newIsSaved = !post.isSaved
      val newCount = if (newIsSaved) post.savesCount + 1 else post.savesCount - 1
      current[index] = post.copy(isSaved = newIsSaved, savesCount = maxOf(0, newCount))
      _posts.value = current
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

  fun addComment(postId: String, text: String) {
    val user = _currentUser.value ?: userMe
    if (text.isBlank()) return
    val current = _posts.value.toMutableList()
    val index = current.indexOfFirst { it.id == postId }
    if (index != -1) {
      val post = current[index]
      val newComment = PostComment(
        id = "c_${System.currentTimeMillis()}",
        postId = postId,
        author = user,
        content = text.trim(),
        timestamp = "Just now"
      )
      val updatedComments = post.comments + newComment
      current[index] = post.copy(comments = updatedComments, commentsCount = post.commentsCount + 1)
      _posts.value = current
    }
  }

  fun addUnfilteredComment(postId: String, text: String) {
    val user = _currentUser.value ?: userMe
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

  fun createPost(
    caption: String,
    mood: MoodType?,
    mediaType: MediaType = MediaType.TEXT,
    imageUrl: String? = null,
    isVybes: Boolean = false
  ) {
    val user = _currentUser.value ?: userMe
    val newPost = Post(
      id = "p_${System.currentTimeMillis()}",
      author = user,
      caption = caption,
      mediaUrls = if (!imageUrl.isNullOrBlank()) listOf(imageUrl) else emptyList(),
      mediaType = if (!imageUrl.isNullOrBlank()) (if (isVybes) MediaType.REEL else MediaType.IMAGE) else mediaType,
      timestamp = "Just now",
      likesCount = 0,
      commentsCount = 0,
      savesCount = 0,
      sharesCount = 0,
      isVybesReel = isVybes,
      moodZone = mood,
      tags = listOf("VybeCommunity")
    )
    _posts.value = listOf(newPost) + _posts.value
  }

  fun createUnfilteredPost(caption: String, imageUrl: String) {
    val user = _currentUser.value ?: userMe
    val newPost = Post(
      id = "uf_${System.currentTimeMillis()}",
      author = user,
      caption = caption,
      mediaUrls = listOf(imageUrl),
      mediaType = MediaType.IMAGE,
      timestamp = "Just now",
      isUnfiltered = true, // Strict rules: No likes, No shares, No ranking
      commentsCount = 0
    )
    _unfilteredPosts.value = listOf(newPost) + _unfilteredPosts.value
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
    val user = _currentUser.value ?: return
    val newStory = Story(
      id = "s_${System.currentTimeMillis()}",
      author = user,
      mediaUrl = mediaUrl,
      caption = caption,
      mediaType = mediaType,
      audience = audience,
      musicTrackTitle = musicTrackTitle,
      location = location,
      textBackgroundColor = textBackgroundColor,
      timestamp = "Just now",
      expiresHoursRemaining = 24,
      isViewed = false
    )
    _stories.value = listOf(newStory) + _stories.value
  }

  fun markStoryViewed(storyId: String) {
    _stories.value = _stories.value.map {
      if (it.id == storyId) it.copy(isViewed = true) else it
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
    _stories.value = _stories.value.filter { it.id != storyId }
  }

  fun selectMoodFilter(mood: MoodType?) {
    _selectedMoodFilter.value = mood
  }

  fun updateUserMoodAndPulse(mood: MoodType?, pulse: LivePulseStatus) {
    val user = _currentUser.value ?: return
    val updated = user.copy(currentMood = mood, livePulse = pulse)
    _currentUser.value = updated
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
    val currentRooms = _unloadRooms.value.toMutableList()
    val index = currentRooms.indexOfFirst { it.id == roomId }
    if (index != -1) {
      val room = currentRooms[index]
      val newMsg = UnloadMessage(
        id = "ulm_${System.currentTimeMillis()}",
        senderName = if (isAnonymous) "Anonymous Vyber" else user.displayName,
        isAnonymous = isAnonymous,
        text = text.trim(),
        timestamp = "Just now",
        isMe = true
      )
      val updatedRoom = room.copy(recentMessages = room.recentMessages + newMsg)
      currentRooms[index] = updatedRoom
      _unloadRooms.value = currentRooms
      if (_activeUnloadRoom.value?.id == roomId) {
        _activeUnloadRoom.value = updatedRoom
      }
    }
  }

  fun submitDoubleTakeAnswer(promptId: String, answer: String) {
    val current = _doubleTakePrompts.value.toMutableList()
    val index = current.indexOfFirst { it.id == promptId }
    if (index != -1) {
      val prompt = current[index]
      current[index] = prompt.copy(
        myAnswer = answer,
        isMyAnswerSubmitted = true,
        isRevealed = prompt.isPeerAnswerSubmitted
      )
      _doubleTakePrompts.value = current
    }
  }

  fun votePrediction(predictionId: String, optionId: Int) {
    val current = _predictions.value.toMutableList()
    val index = current.indexOfFirst { it.id == predictionId }
    if (index != -1) {
      val pred = current[index]
      if (pred.userSelectedOptionId != null) return
      val updatedOptions = pred.options.map { opt ->
        if (opt.id == optionId) opt.copy(votesCount = opt.votesCount + 1) else opt
      }
      val total = pred.totalVotes + 1
      val withPercentages = updatedOptions.map { opt ->
        opt.copy(percentage = ((opt.votesCount.toFloat() / total) * 100).toInt())
      }
      current[index] = pred.copy(
        totalVotes = total,
        options = withPercentages,
        userSelectedOptionId = optionId
      )
      _predictions.value = current
    }
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
      unlockTimestampMs = System.currentTimeMillis() + 86400000L * 30,
      isUnlocked = false,
      createdAtFormatted = "Today"
    )
    _timeCapsules.value = listOf(capsule) + _timeCapsules.value
  }

  fun unlockTimeCapsule(capsuleId: String) {
    val current = _timeCapsules.value.toMutableList()
    val index = current.indexOfFirst { it.id == capsuleId }
    if (index != -1) {
      current[index] = current[index].copy(isUnlocked = true)
      _timeCapsules.value = current
    }
  }

  fun sendDirectMessage(conversationId: String, text: String) {
    val user = _currentUser.value ?: return
    if (text.isBlank()) return
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      val conv = current[index]
      val newMsg = DirectMessage(
        id = "m_${System.currentTimeMillis()}",
        senderId = user.id,
        text = text.trim(),
        timestamp = "Just now",
        isMe = true,
        isRead = true
      )
      current[index] = conv.copy(
        lastMessage = text.trim(),
        lastMessageTimestamp = "Just now",
        messages = conv.messages + newMsg
      )
      _conversations.value = current
    }
  }

  fun initiateCall(peerUser: User, isVideo: Boolean): CallAvailabilityResult {
    val me = _currentUser.value ?: return CallAvailabilityResult.Error("Please log in first")
    if (_blockedUserIds.value.contains(peerUser.id) || _conversations.value.any { it.peerUser.id == peerUser.id && it.isBlocked }) {
      return CallAvailabilityResult.Blocked("Cannot call a blocked user.")
    }
    val isPeerOnline = peerUser.livePulse != LivePulseStatus.NONE && peerUser.livePulse != LivePulseStatus.QUIET_MODE
    if (!isPeerOnline) {
      return CallAvailabilityResult.Offline("${peerUser.displayName} is currently offline. You can leave a direct message instead.")
    }
    startCall(peerUser, isVideo)
    return CallAvailabilityResult.Available
  }

  fun startCall(peerUser: User, isVideo: Boolean) {
    _activeCall.value = CallSession(
      peerUser = peerUser,
      isVideo = isVideo,
      state = CallState.OUTGOING,
      durationSeconds = 0,
      isMuted = false,
      isCameraOff = false
    )
  }

  fun acceptCall() {
    val call = _activeCall.value ?: return
    _activeCall.value = call.copy(state = CallState.CONNECTED)
  }

  fun endCall() {
    _activeCall.value = null
  }

  fun toggleCallMute() {
    val call = _activeCall.value ?: return
    _activeCall.value = call.copy(isMuted = !call.isMuted)
  }

  fun toggleCallCamera() {
    val call = _activeCall.value ?: return
    _activeCall.value = call.copy(isCameraOff = !call.isCameraOff)
  }

  fun addShopReview(shopId: String, rating: Int, reviewText: String) {
    val user = _currentUser.value ?: return
    val current = _shops.value.toMutableList()
    val index = current.indexOfFirst { it.id == shopId }
    if (index != -1) {
      val shop = current[index]
      val review = ShopReview(
        id = "sr_${System.currentTimeMillis()}",
        author = user,
        ratingStars = rating,
        reviewText = reviewText,
        timestamp = "Just now",
        isVerifiedCustomer = true
      )
      val updatedReviews = listOf(review) + shop.reviews
      current[index] = shop.copy(
        reviews = updatedReviews,
        reviewsCount = shop.reviewsCount + 1
      )
      _shops.value = current
    }
  }

  fun submitAdminReportAction(reportId: String, action: ReportStatus) {
    val current = _adminReports.value.toMutableList()
    val index = current.indexOfFirst { it.id == reportId }
    if (index != -1) {
      current[index] = current[index].copy(status = action)
      _adminReports.value = current
    }
  }

  fun sendCrossedPathRequest(encounterId: String) {
    val current = _crossedPaths.value.toMutableList()
    val index = current.indexOfFirst { it.id == encounterId }
    if (index != -1) {
      current[index] = current[index].copy(
        hasSentRequest = true,
        requestStatus = ConnectionRequestStatus.PENDING
      )
      _crossedPaths.value = current
    }
  }

  // --- USER ID AVAILABILITY CHECK ---
  fun checkUserIdAvailability(requestedId: String): Pair<Boolean, String> {
    val clean = requestedId.trim().lowercase().replace("@", "")
    if (clean.length < 3) {
      return Pair(false, "User ID must be at least 3 characters.")
    }
    val existing = listOf("sid_verma", "aarav_beats", "priya_design", "vikram_cricket", "admin", "vybe", "nrnext")
    val currentUser = _currentUser.value
    if (currentUser?.username == clean) {
      return Pair(true, "This is your current User ID.")
    }
    if (existing.contains(clean) || _allUsers.value.any { it.username.equals(clean, ignoreCase = true) }) {
      return Pair(false, "This User ID is already taken. Try another.")
    }
    return Pair(true, "This User ID is available.")
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
    val cleanUsername = username.lowercase().replace(" ", "_")
    val updated = user.copy(
      firstName = firstName,
      lastName = lastName,
      displayName = "$firstName $lastName".trim().ifBlank { cleanUsername },
      username = cleanUsername,
      email = email,
      phone = phone,
      bio = bio,
      profileVideoUrl = profileVideoUrl ?: user.profileVideoUrl,
      avatarUrl = avatarUrl ?: user.avatarUrl,
      coverUrl = coverUrl ?: user.coverUrl
    )
    _currentUser.value = updated
    _allUsers.value = _allUsers.value.map { if (it.id == updated.id) updated else it }
    SessionManager.get()?.saveSession(updated)

    if (supabaseRepo.isConfigured) {
      CoroutineScope(Dispatchers.IO).launch {
        val token = SessionManager.get()?.accessToken ?: ""
        supabaseRepo.updateUserProfile(
          userId = updated.id,
          firstName = firstName,
          lastName = lastName,
          displayName = updated.displayName,
          bio = bio,
          avatarUrl = updated.avatarUrl,
          coverUrl = updated.coverUrl,
          accessToken = token
        )
      }
    }
  }

  fun updateTimeManagement(breakMinutes: Int, isEnabled: Boolean) {
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
      agentResponse = "Thank you for contacting VYBE Support (NR Next). A specialist will review your request shortly."
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

  fun checkAgeRequirement(birthDateStr: String): Pair<Boolean, String> {
    return try {
      val parts = birthDateStr.split("-")
      if (parts.size == 3) {
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val cal = java.util.Calendar.getInstance(); val cy = cal.get(java.util.Calendar.YEAR); val cm = cal.get(java.util.Calendar.MONTH) + 1; val cd = cal.get(java.util.Calendar.DAY_OF_MONTH); var age = cy - y; if (cm < m || (cm == m && cd < d)) age--
        
        if (age < 16) {
          Pair(false, "VYBE is strictly for individuals aged 16 and above. Access cannot be granted.")
        } else {
          Pair(true, "Eligible")
        }
      } else {
        Pair(false, "Invalid date format. Use YYYY-MM-DD.")
      }
    } catch (e: Exception) {
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

    if (password.length < 6) {
      return Pair(false, "Password must be at least 6 characters.")
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
        val (user, token) = res.getOrThrow()
        _currentUser.value = user
        _isLoggedIn.value = true
        if (_allUsers.value.none { it.id == user.id }) {
          _allUsers.value = listOf(user) + _allUsers.value
        }
        SessionManager.get()?.saveSession(user, token)
        Pair(true, "Account created with Supabase Auth! ${if (user.isUnder18) "Teen Safety Protected Mode active." else "Standard Verified Mode active."}")
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Supabase registration failed.")
      }
    } else {
      // Local Developer Mode fallback when Supabase keys are pending configuration in Secrets
      val local = registerAccount(firstName, lastName, username, email, phone, birthDateStr)
      if (local.first && _currentUser.value != null) {
        SessionManager.get()?.saveSession(_currentUser.value!!)
      }
      return local
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

    try {
      val parts = birthDateStr.split("-")
      val y = parts[0].toInt()
      val m = parts[1].toInt()
      val d = parts[2].toInt()
      val cal = java.util.Calendar.getInstance(); val cy = cal.get(java.util.Calendar.YEAR); val cm = cal.get(java.util.Calendar.MONTH) + 1; val cd = cal.get(java.util.Calendar.DAY_OF_MONTH); var age = cy - y; if (cm < m || (cm == m && cd < d)) age--
      

      val newUser = User(
        id = "u_${System.currentTimeMillis()}",
        username = username.lowercase().replace(" ", "_"),
        displayName = "$firstName $lastName".trim().ifBlank { username },
        firstName = firstName,
        lastName = lastName,
        email = email,
        phone = phone,
        isEmailVerified = true,
        isPhoneVerified = true,
        dateOfBirth = birthDateStr,
        age = age,
        isUnder18 = age in 16..17,
        role = UserRole.USER,
        isOnboardingCompleted = false
      )
      _currentUser.value = newUser
      _isLoggedIn.value = true
      if (_allUsers.value.none { it.id == newUser.id }) {
        _allUsers.value = listOf(newUser) + _allUsers.value
      }
      SessionManager.get()?.saveSession(newUser)
      return Pair(true, "Welcome to VYBE! Account created with ${if (newUser.isUnder18) "Teen Safety Protected Mode" else "Standard Verified Mode"}.")
    } catch (e: Exception) {
      return Pair(false, "Registration could not be completed.")
    }
  }

  // Backward compatible overload for existing tests
  fun registerAccount(username: String, email: String, birthDateStr: String): Pair<Boolean, String> {
    return registerAccount(username, "", username, email, "+91 98765 00000", birthDateStr)
  }

  suspend fun loginWithSupabase(identifier: String, pass: String): Pair<Boolean, String> {
    if (identifier.isBlank() || pass.isBlank()) {
      return Pair(false, "Please enter your email and password.")
    }

    if (supabaseRepo.isConfigured) {
      val resolvedEmail = if (identifier.contains("@")) {
        identifier.trim().lowercase()
      } else {
        supabaseRepo.resolveEmailForIdentifier(identifier) ?: identifier.trim().lowercase()
      }
      val res = supabaseRepo.signInWithEmail(resolvedEmail, pass)
      return if (res.isSuccess) {
        val (user, token) = res.getOrThrow()
        _currentUser.value = user
        _isLoggedIn.value = true
        if (_allUsers.value.none { it.id == user.id }) {
          _allUsers.value = listOf(user) + _allUsers.value
        }
        SessionManager.get()?.saveSession(user, token)
        Pair(true, "Logged in to Supabase!")
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Invalid email or password.")
      }
    } else {
      // Offline / Developer Mode fallback
      val clean = identifier.trim().lowercase().replace("@", "")
      val matched = _allUsers.value.firstOrNull {
        it.email.equals(identifier.trim(), ignoreCase = true) ||
        it.username.lowercase() == clean ||
        it.publicVybeId.lowercase() == clean
      } ?: User(
        id = "u_${System.currentTimeMillis()}",
        username = clean.ifBlank { "vybe_user" },
        displayName = identifier.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() },
        email = identifier.trim(),
        role = UserRole.USER,
        isOnboardingCompleted = true
      )
      _currentUser.value = matched
      _isLoggedIn.value = true
      if (_allUsers.value.none { it.id == matched.id }) {
        _allUsers.value = listOf(matched) + _allUsers.value
      }
      SessionManager.get()?.saveSession(matched)
      return Pair(true, "Logged in (Developer Mode - Supabase awaiting configuration in Secrets)")
    }
  }

  suspend fun verifyOtp(email: String, token: String, type: String = "signup"): Pair<Boolean, String> {
    if (supabaseRepo.isConfigured) {
      val res = supabaseRepo.verifyOtp(email, token, type)
      return if (res.isSuccess) {
        val (user, token) = res.getOrThrow()
        _currentUser.value = user
        _isLoggedIn.value = true
        if (_allUsers.value.none { it.id == user.id }) {
          _allUsers.value = listOf(user) + _allUsers.value
        }
        SessionManager.get()?.saveSession(user, token)
        Pair(true, "Email verified successfully!")
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Verification code invalid or expired.")
      }
    } else {
      val user = _currentUser.value
      if (user != null) {
        val updated = user.copy(isEmailVerified = true)
        _currentUser.value = updated
        _allUsers.value = _allUsers.value.map { if (it.id == updated.id) updated else it }
        SessionManager.get()?.saveSession(updated)
      }
      return Pair(true, "Email verified successfully!")
    }
  }

  suspend fun sendPasswordReset(email: String): Pair<Boolean, String> {
    if (email.isBlank()) {
      return Pair(false, "Please enter your email address.")
    }
    if (supabaseRepo.isConfigured) {
      val res = supabaseRepo.sendPasswordReset(email.trim())
      return if (res.isSuccess) {
        Pair(true, "Password reset code sent to your email.")
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Unable to send password reset code.")
      }
    } else {
      return Pair(true, "Password reset code sent to $email.")
    }
  }

  suspend fun resetPasswordWithOtp(email: String, otp: String, newPass: String): Pair<Boolean, String> {
    if (newPass.length < 6) {
      return Pair(false, "Password must be at least 6 characters.")
    }
    if (supabaseRepo.isConfigured) {
      val res = supabaseRepo.resetPasswordWithOtp(email.trim(), otp.trim(), newPass)
      return if (res.isSuccess) {
        Pair(true, "Password reset successfully. You can now log in.")
      } else {
        Pair(false, res.exceptionOrNull()?.message ?: "Failed to reset password.")
      }
    } else {
      return Pair(true, "Password reset successfully. You can now log in.")
    }
  }

  fun loginUser(identifier: String, pass: String): Boolean {
    val clean = identifier.trim().lowercase().replace("@", "")
    val matched = _allUsers.value.firstOrNull {
      it.email.equals(identifier.trim(), ignoreCase = true) ||
      it.username.lowercase() == clean
    } ?: User(
      id = "u_${System.currentTimeMillis()}",
      username = clean.ifBlank { "vybe_user" },
      displayName = identifier.substringBefore("@"),
      email = identifier.trim(),
      role = UserRole.USER,
      isOnboardingCompleted = true
    )
    _currentUser.value = matched
    _isLoggedIn.value = true
    SessionManager.get()?.saveSession(matched)
    return true
  }

  fun logout() {
    val token = SessionManager.get()?.accessToken ?: ""
    CoroutineScope(Dispatchers.IO).launch {
      supabaseRepo.signOut(token)
    }
    SessionManager.get()?.clearSession()
    _currentUser.value = null
    _isLoggedIn.value = false
  }

  fun deactivateAccount() {
    val user = _currentUser.value ?: return
    _currentUser.value = user.copy(isDeactivated = true)
    _isLoggedIn.value = false
  }

  fun completeOnboarding() {
    val user = _currentUser.value ?: return
    _currentUser.value = user.copy(isOnboardingCompleted = true)
  }

  // Notification Operations (Part G)
  fun markNotificationAsRead(id: String) {
    _notifications.value = _notifications.value.map {
      if (it.id == id) it.copy(isRead = true) else it
    }
  }

  fun markAllNotificationsAsRead() {
    _notifications.value = _notifications.value.map { it.copy(isRead = true) }
  }

  fun addNotification(item: NotificationItem) {
    _notifications.value = listOf(item) + _notifications.value
  }

  // Spiritual Mode Operations (Part F)
  fun setSpiritualMode(enabled: Boolean) {
    _isSpiritualMode.value = enabled
    _currentUser.value = _currentUser.value?.copy(isSpiritualMode = enabled)
  }

  fun toggleSpiritualMode() {
    val next = !_isSpiritualMode.value
    setSpiritualMode(next)
  }

  fun addGratitudeEntry(entry: String) {
    val newEntry = SpiritualGratitude(
      id = "sg_${System.currentTimeMillis()}",
      entry = entry.trim(),
      timestamp = "Just now"
    )
    _spiritualGratitudes.value = listOf(newEntry) + _spiritualGratitudes.value
  }

  fun addIntention(text: String) {
    val newIntention = SpiritualIntention(
      id = "si_${System.currentTimeMillis()}",
      text = text.trim(),
      timestamp = "Today",
      isFulfilled = false
    )
    _spiritualIntentions.value = listOf(newIntention) + _spiritualIntentions.value
  }

  fun toggleIntentionFulfilled(id: String) {
    _spiritualIntentions.value = _spiritualIntentions.value.map {
      if (it.id == id) it.copy(isFulfilled = !it.isFulfilled) else it
    }
  }

  // VYBE UNLOAD ROOMS (Part D)
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
      hostId = user?.id ?: "u_me",
      hostName = user?.displayName ?: "Vybe Host",
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
        val updated = it.copy(
          title = title,
          description = description,
          topicTag = topicTag,
          mode = mode,
          durationMinutes = durationMinutes,
          capacity = capacity
        )
        if (_activeUnloadRoom.value?.id == roomId) _activeUnloadRoom.value = updated
        updated
      } else it
    }
  }

  fun deleteUnloadRoom(roomId: String) {
    _unloadRooms.value = _unloadRooms.value.filterNot { it.id == roomId }
    if (_activeUnloadRoom.value?.id == roomId) {
      _activeUnloadRoom.value = null
    }
  }

  // WHAT NEXT / AUDIENCE POLLS (Part F)
  fun createPredictionPoll(
    title: String,
    category: String,
    durationHours: Int,
    optionTexts: List<String>
  ) {
    val user = _currentUser.value
    val opts = optionTexts.mapIndexed { idx, txt ->
      PredictionOption(id = idx + 1, text = txt, votesCount = 0, percentage = 0)
    }
    val newPoll = SocialPrediction(
      id = "pred_${System.currentTimeMillis()}",
      title = title,
      category = category,
      closingTimeText = if (durationHours <= 24) "Closes in ${durationHours}h" else "Closes in ${durationHours / 24} days",
      durationHours = durationHours,
      totalVotes = 0,
      options = opts,
      authorId = user?.id ?: "u_me",
      authorUsername = user?.username ?: "sid_verma"
    )
    _predictions.value = listOf(newPoll) + _predictions.value
  }

  fun deletePredictionPoll(pollId: String) {
    _predictions.value = _predictions.value.filterNot { it.id == pollId }
  }

  // FUTURE TIME CAPSULE (Part G)
  fun respondToCapsuleInvitation(capsuleId: String, accept: Boolean) {
    _timeCapsules.value = _timeCapsules.value.map {
      if (it.id == capsuleId) {
        it.copy(
          myInvitationStatus = if (accept) CapsuleInvitationStatus.ACCEPTED else CapsuleInvitationStatus.DECLINED
        )
      } else it
    }
  }

  // VYBE DOUBLE TAKE (Part H)
  fun createDoubleTakePrompt(
    question: String,
    category: String,
    optionA: String,
    optionB: String,
    peerUser: User
  ) {
    val user = _currentUser.value
    val newPrompt = DoubleTakePrompt(
      id = "dt_${System.currentTimeMillis()}",
      promptQuestion = question,
      category = category,
      optionA = optionA,
      optionB = optionB,
      creatorId = user?.id ?: "u_me",
      peerUser = peerUser,
      peerAnswer = if (optionA.isNotBlank()) optionA else "Yes",
      isPeerAnswerSubmitted = true
    )
    _doubleTakePrompts.value = listOf(newPrompt) + _doubleTakePrompts.value
  }

  fun deleteDoubleTakePrompt(promptId: String) {
    _doubleTakePrompts.value = _doubleTakePrompts.value.filterNot { it.id == promptId }
  }

  fun acceptDoubleTakeMatch(promptId: String) {
    _doubleTakePrompts.value = _doubleTakePrompts.value.map {
      if (it.id == promptId) it.copy(isCreatorAccepted = true, isChatUnlocked = true) else it
    }
  }

  // VERIFIED BUSINESS SHOP (Part J)
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
      ownerId = user?.id ?: "u_me",
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
      if (it.id == applicationId) {
        val updated = it.copy(status = status, reviewerNotes = notes)
        if (status == BusinessVerificationStatus.APPROVED) {
          _currentUser.value = _currentUser.value?.copy(isVerifiedBusiness = true)
        }
        updated
      } else it
    }
  }

  fun activateBusinessShop(shopId: String) {
    _shops.value = _shops.value.map {
      if (it.id == shopId) it.copy(isShopActive = true) else it
    }
  }

  // COMPLETE INBOX & MESSAGING (Part K & Performance Optimized)
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

    // Prevent duplicate submits using unique client ID
    val clientId = "c_${System.currentTimeMillis()}_${(1000..9999).random()}"
    if (!inFlightMessageClientIds.add(clientId)) return

    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      val conv = current[index]
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

      // Performance measurement: Send-button to request start
      val startTime = System.currentTimeMillis()

      CoroutineScope(Dispatchers.IO).launch {
        try {
          if (supabaseRepo.isConfigured) {
            val token = SessionManager.get()?.accessToken ?: ""
            val res = supabaseRepo.sendMessage(
              conversationId = conversationId,
              senderId = user.id,
              content = cleanText.ifBlank { mediaUrl ?: "[Media]" },
              mediaUrl = mediaUrl,
              mediaType = mediaType,
              replyToId = replyToId,
              accessToken = token
            )
            val dbDuration = System.currentTimeMillis() - startTime
            android.util.Log.d("VybePerf", "Message DB insert confirmed in ${dbDuration}ms for conv $conversationId")

            val updatedList = _conversations.value.toMutableList()
            val convIdx = updatedList.indexOfFirst { it.id == conversationId }
            if (convIdx != -1) {
              val c = updatedList[convIdx]
              if (res.isSuccess) {
                val serverMsg = res.getOrThrow()
                val reconciled = c.messages.map { m ->
                  if (m.clientId == clientId || m.id == clientId) {
                    m.copy(
                      id = serverMsg.id ?: clientId,
                      status = MessageStatus.SENT,
                      timestamp = "Just now"
                    )
                  } else m
                }
                updatedList[convIdx] = c.copy(messages = reconciled)
              } else {
                android.util.Log.e("VybePerf", "Message insert failed: ${res.exceptionOrNull()?.message}")
                val failedMsgs = c.messages.map { m ->
                  if (m.clientId == clientId || m.id == clientId) {
                    m.copy(status = MessageStatus.FAILED, timestamp = "Failed")
                  } else m
                }
                updatedList[convIdx] = c.copy(messages = failedMsgs)
              }
              _conversations.value = updatedList
            }
          } else {
            // Local mode: simulate instant server confirmation
            kotlinx.coroutines.delay(120)
            val updatedList = _conversations.value.toMutableList()
            val convIdx = updatedList.indexOfFirst { it.id == conversationId }
            if (convIdx != -1) {
              val c = updatedList[convIdx]
              val reconciled = c.messages.map { m ->
                if (m.clientId == clientId || m.id == clientId) {
                  m.copy(status = MessageStatus.SENT, timestamp = "Just now")
                } else m
              }
              updatedList[convIdx] = c.copy(messages = reconciled)
              _conversations.value = updatedList
            }
          }
        } finally {
          inFlightMessageClientIds.remove(clientId)
        }
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

    // Reset status to SENDING
    val updatedMsgs = conv.messages.map { m ->
      if (m.id == target.id) m.copy(status = MessageStatus.SENDING, timestamp = "Sending...") else m
    }
    current[convIdx] = conv.copy(messages = updatedMsgs)
    _conversations.value = current

    val clientId = target.clientId ?: target.id
    if (!inFlightMessageClientIds.add(clientId)) return

    CoroutineScope(Dispatchers.IO).launch {
      try {
        if (supabaseRepo.isConfigured) {
          val token = SessionManager.get()?.accessToken ?: ""
          val res = supabaseRepo.sendMessage(
            conversationId = conversationId,
            senderId = user.id,
            content = target.text.ifBlank { target.mediaUrl ?: "[Media]" },
            mediaUrl = target.mediaUrl,
            mediaType = target.mediaType,
            replyToId = target.replyToId,
            accessToken = token
          )
          val listNow = _conversations.value.toMutableList()
          val idx = listNow.indexOfFirst { it.id == conversationId }
          if (idx != -1) {
            val c = listNow[idx]
            val finalMsgs = c.messages.map { m ->
              if (m.id == target.id || m.clientId == clientId) {
                if (res.isSuccess) {
                  m.copy(
                    id = res.getOrThrow().id ?: m.id,
                    status = MessageStatus.SENT,
                    timestamp = "Just now"
                  )
                } else {
                  m.copy(status = MessageStatus.FAILED, timestamp = "Failed")
                }
              } else m
            }
            listNow[idx] = c.copy(messages = finalMsgs)
            _conversations.value = listNow
          }
        } else {
          kotlinx.coroutines.delay(100)
          val listNow = _conversations.value.toMutableList()
          val idx = listNow.indexOfFirst { it.id == conversationId }
          if (idx != -1) {
            val c = listNow[idx]
            val finalMsgs = c.messages.map { m ->
              if (m.id == target.id) m.copy(status = MessageStatus.SENT, timestamp = "Just now") else m
            }
            listNow[idx] = c.copy(messages = finalMsgs)
            _conversations.value = listNow
          }
        }
      } finally {
        inFlightMessageClientIds.remove(clientId)
      }
    }
  }

  fun syncMessagesForConversation(conversationId: String) {
    if (!supabaseRepo.isConfigured) return
    val user = _currentUser.value ?: return
    CoroutineScope(Dispatchers.IO).launch {
      val token = SessionManager.get()?.accessToken ?: ""
      val res = supabaseRepo.fetchMessages(conversationId, token, limit = 50)
      if (res.isSuccess) {
        val remoteList = res.getOrThrow()
        val current = _conversations.value.toMutableList()
        val convIdx = current.indexOfFirst { it.id == conversationId }
        if (convIdx != -1) {
          val conv = current[convIdx]
          val existingIds = conv.messages.map { it.id }.toSet()
          val pendingMap = conv.messages.filter { it.status == MessageStatus.SENDING }.associateBy { it.clientId }

          val newMessages = mutableListOf<DirectMessage>()
          for (dto in remoteList) {
            val dtoId = dto.id ?: continue
            if (!existingIds.contains(dtoId)) {
              newMessages.add(
                DirectMessage(
                  id = dtoId,
                  senderId = dto.senderId,
                  text = dto.content,
                  timestamp = "Just now",
                  isMe = dto.senderId == user.id,
                  mediaUrl = dto.mediaUrl,
                  mediaType = dto.mediaType ?: "TEXT",
                  replyToId = dto.replyToMessageId,
                  status = MessageStatus.SENT
                )
              )
            }
          }
          if (newMessages.isNotEmpty()) {
            val merged = (conv.messages + newMessages).distinctBy { it.id }
            current[convIdx] = conv.copy(
              messages = merged,
              lastMessage = merged.lastOrNull()?.text ?: conv.lastMessage
            )
            _conversations.value = current
          }
        }
      }
    }
  }

  fun editDirectMessage(conversationId: String, messageId: String, newText: String) {
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      val conv = current[index]
      val updatedMsgs = conv.messages.map { msg ->
        if (msg.id == messageId && msg.isMe) {
          msg.copy(text = newText.trim(), isEdited = true)
        } else msg
      }
      current[index] = conv.copy(messages = updatedMsgs)
      _conversations.value = current
    }
  }

  fun deleteMessageForMe(conversationId: String, messageId: String) {
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      val conv = current[index]
      val updatedMsgs = conv.messages.filterNot { it.id == messageId }
      current[index] = conv.copy(messages = updatedMsgs)
      _conversations.value = current
    }
  }

  fun deleteMessageForEveryone(conversationId: String, messageId: String) {
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      val conv = current[index]
      val updatedMsgs = conv.messages.map { msg ->
        if (msg.id == messageId) {
          msg.copy(
            text = "This message was deleted",
            isDeletedForEveryone = true,
            mediaUrl = null
          )
        } else msg
      }
      current[index] = conv.copy(messages = updatedMsgs)
      _conversations.value = current
    }
  }

  fun addMessageReaction(conversationId: String, messageId: String, emoji: String) {
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      val conv = current[index]
      val updatedMsgs = conv.messages.map { msg ->
        if (msg.id == messageId) {
          val next = if (msg.reactions.contains(emoji)) msg.reactions - emoji else msg.reactions + emoji
          msg.copy(reactions = next)
        } else msg
      }
      current[index] = conv.copy(messages = updatedMsgs)
      _conversations.value = current
    }
  }

  fun setHiddenChatPin(pin: String) {
    hiddenChatPinHash = pin.trim().hashCode().toString()
  }

  fun verifyHiddenChatPin(pin: String): Boolean {
    return pin.trim().hashCode().toString() == hiddenChatPinHash
  }

  fun toggleHideConversation(conversationId: String) {
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      val conv = current[index]
      current[index] = conv.copy(isHidden = !conv.isHidden)
      _conversations.value = current
    }
  }

  fun createGroupConversation(groupName: String, members: List<User>): Conversation {
    val user = _currentUser.value ?: userMe
    val groupConv = Conversation(
      id = "grp_${System.currentTimeMillis()}",
      peerUser = members.firstOrNull() ?: user,
      groupName = groupName,
      isGroup = true,
      groupMembers = listOf(user) + members,
      groupAdminId = user.id,
      lastMessage = "Group \"$groupName\" created",
      lastMessageTimestamp = "Just now",
      messages = listOf(
        DirectMessage(
          id = "gm_1",
          senderId = user.id,
          text = "Welcome to $groupName! Say hi 👋",
          timestamp = "Just now",
          isMe = true
        )
      )
    )
    _conversations.value = listOf(groupConv) + _conversations.value
    return groupConv
  }

  fun updateChatNickname(conversationId: String, nickname: String) {
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      current[index] = current[index].copy(customNickname = if (nickname.isBlank()) null else nickname.trim())
      _conversations.value = current
    }
  }

  fun toggleMuteChat(conversationId: String) {
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      current[index] = current[index].copy(isMuted = !current[index].isMuted)
      _conversations.value = current
    }
  }

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

    val currentConvs = _conversations.value.toMutableList()
    val index = currentConvs.indexOfFirst { it.peerUser.id == userId }
    if (index != -1) {
      currentConvs[index] = currentConvs[index].copy(isBlocked = isBlockedNow)
      _conversations.value = currentConvs
    }

    val me = _currentUser.value
    if (supabaseRepo.isConfigured && me != null) {
      CoroutineScope(Dispatchers.IO).launch {
        val token = SessionManager.get()?.accessToken ?: ""
        if (isBlockedNow) supabaseRepo.blockUser(me.id, userId, token)
        else supabaseRepo.unblockUser(me.id, userId, token)
      }
    }
    return isBlockedNow
  }

  fun unblockUser(userId: String) {
    val currentSet = _blockedUserIds.value.toMutableSet()
    if (currentSet.remove(userId)) {
      _blockedUserIds.value = currentSet
      val currentConvs = _conversations.value.toMutableList()
      val index = currentConvs.indexOfFirst { it.peerUser.id == userId }
      if (index != -1) {
        currentConvs[index] = currentConvs[index].copy(isBlocked = false)
        _conversations.value = currentConvs
      }
      val me = _currentUser.value
      if (supabaseRepo.isConfigured && me != null) {
        CoroutineScope(Dispatchers.IO).launch {
          val token = SessionManager.get()?.accessToken ?: ""
          supabaseRepo.unblockUser(me.id, userId, token)
        }
      }
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

    val currentConvs = _conversations.value.toMutableList()
    val index = currentConvs.indexOfFirst { it.peerUser.id == userId }
    if (index != -1) {
      currentConvs[index] = currentConvs[index].copy(isRestricted = isRestrictedNow)
      _conversations.value = currentConvs
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
      val token = SessionManager.get()?.accessToken ?: ""
      val res = supabaseRepo.submitReport(me.id, targetType, targetId, reason, details, token)
      return if (res.isSuccess) {
        Pair(true, "Report submitted successfully. Thank you for protecting the community.")
      } else {
        Pair(false, "Failed to submit report. Please try again.")
      }
    }
    // Record in local admin queue
    val newReport = AdminReport(
      id = "rep_${System.currentTimeMillis()}",
      reportedUser = _allUsers.value.firstOrNull { it.id == targetId } ?: User(
        id = targetId,
        username = "reported_user",
        displayName = "Reported Content",
        role = UserRole.USER,
        isOnboardingCompleted = true
      ),
      reason = "$reason: $details".trim(),
      severity = "MEDIUM",
      status = "PENDING_REVIEW"
    )
    _adminReports.value = listOf(newReport) + _adminReports.value
    return Pair(true, "Report submitted successfully. Our safety team will review it.")
  }

  fun toggleFollowUser(targetUserId: String): Boolean {
    val currentFollowing = _followingUserIds.value.toMutableSet()
    val isNowFollowing = if (currentFollowing.contains(targetUserId)) {
      currentFollowing.remove(targetUserId)
      false
    } else {
      currentFollowing.add(targetUserId)
      true
    }
    _followingUserIds.value = currentFollowing

    // Update target user's followersCount
    _allUsers.value = _allUsers.value.map { u ->
      if (u.id == targetUserId) {
        val newCount = (u.followersCount + if (isNowFollowing) 1 else -1).coerceAtLeast(0)
        u.copy(followersCount = newCount)
      } else u
    }

    // Update my followingCount
    val me = _currentUser.value
    if (me != null) {
      val updatedMe = me.copy(followingCount = (me.followingCount + if (isNowFollowing) 1 else -1).coerceAtLeast(0))
      _currentUser.value = updatedMe
      SessionManager.get()?.saveSession(updatedMe)

      if (supabaseRepo.isConfigured) {
        CoroutineScope(Dispatchers.IO).launch {
          val token = SessionManager.get()?.accessToken ?: ""
          if (isNowFollowing) supabaseRepo.followUser(me.id, targetUserId, token)
          else supabaseRepo.unfollowUser(me.id, targetUserId, token)
        }
      }
    }
    return isNowFollowing
  }

  fun deleteNotification(notificationId: String) {
    _notifications.value = _notifications.value.filterNot { it.id == notificationId }
    if (supabaseRepo.isConfigured) {
      CoroutineScope(Dispatchers.IO).launch {
        val token = SessionManager.get()?.accessToken ?: ""
        supabaseRepo.deleteNotification(notificationId, token)
      }
    }
  }

  fun markNotificationAsRead(notificationId: String) {
    _notifications.value = _notifications.value.map {
      if (it.id == notificationId) it.copy(isRead = true) else it
    }
  }

  fun markAllNotificationsRead() {
    _notifications.value = _notifications.value.map { it.copy(isRead = true) }
  }

  fun updateChatTheme(conversationId: String, theme: String) {
    val current = _conversations.value.toMutableList()
    val index = current.indexOfFirst { it.id == conversationId }
    if (index != -1) {
      current[index] = current[index].copy(chatTheme = theme)
      _conversations.value = current
    }
  }

  fun searchUserByVybeId(query: String): User? {
    val clean = query.trim().lowercase().replace("@", "")
    return _allUsers.value.firstOrNull {
      it.publicVybeId.lowercase() == clean ||
      it.username.lowercase() == clean ||
      it.id.lowercase() == clean ||
      it.displayName.lowercase() == clean
    }
  }

  fun addVybeMatch(user: User) {
    if (_vybeMatches.value.none { it.id == user.id }) {
      _vybeMatches.value = _vybeMatches.value + user
    }
  }

  fun removeVybeMatch(userId: String) {
    _vybeMatches.value = _vybeMatches.value.filter { it.id != userId }
  }

  fun isVybeMatch(userId: String): Boolean {
    return _vybeMatches.value.any { it.id == userId }
  }
}
 
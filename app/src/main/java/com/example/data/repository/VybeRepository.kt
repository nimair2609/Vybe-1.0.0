package com.example.data.repository

import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

  // Current session state
  private val _currentUser = MutableStateFlow<User?>(userMe)
  val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

  private val _isLoggedIn = MutableStateFlow(true)
  val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

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
        musicTrackTitle = "Midnight Tape 04",
        timestamp = "1h ago"
      ),
      Story(
        id = "s_2",
        author = userPriya,
        mediaUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600",
        caption = "Filter coffee & wireframing morning ☕",
        mediaType = StoryMediaType.PHOTO,
        timestamp = "3h ago"
      ),
      Story(
        id = "s_3",
        author = userVikramCricket,
        mediaUrl = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=600",
        caption = "Matchday prep at Wankhede! 🏟️",
        mediaType = StoryMediaType.VIDEO,
        timestamp = "4h ago"
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
    val user = _currentUser.value ?: return
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

  fun createPost(
    caption: String,
    mood: MoodType?,
    mediaType: MediaType = MediaType.TEXT,
    imageUrl: String? = null,
    isVybes: Boolean = false
  ) {
    val user = _currentUser.value ?: return
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
    val user = _currentUser.value ?: return
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
    musicTrackTitle: String? = null
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
      timestamp = "Just now",
      expiresHoursRemaining = 24
    )
    _stories.value = listOf(newStory) + _stories.value
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
    if (existing.contains(clean)) {
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
    avatarUrl: String? = null
  ) {
    val user = _currentUser.value ?: return
    val updated = user.copy(
      firstName = firstName,
      lastName = lastName,
      displayName = "$firstName $lastName".trim(),
      username = username.lowercase().replace(" ", "_"),
      email = email,
      phone = phone,
      bio = bio,
      profileVideoUrl = profileVideoUrl ?: user.profileVideoUrl,
      avatarUrl = avatarUrl ?: user.avatarUrl
    )
    _currentUser.value = updated
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

  fun registerAccount(
    firstName: String,
    lastName: String,
    username: String,
    email: String,
    phone: String,
    birthDateStr: String
  ): Pair<Boolean, String> {
    try {
      val parts = birthDateStr.split("-")
      if (parts.size == 3) {
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val dob = LocalDate.of(y, m, d)
        val age = Period.between(dob, LocalDate.now()).years

        if (age < 16) {
          return Pair(false, "VYBE is strictly for individuals aged 16 and above. Access cannot be granted.")
        }

        val newUser = User(
          id = "u_${System.currentTimeMillis()}",
          username = username.lowercase().replace(" ", "_"),
          displayName = "$firstName $lastName".trim(),
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
        return Pair(true, "Welcome to VYBE! Account created with ${if (newUser.isUnder18) "Teen Safety Protected Mode" else "Standard Verified Mode"}.")
      }
    } catch (e: Exception) {
      return Pair(false, "Invalid date format. Use YYYY-MM-DD.")
    }
    return Pair(false, "Registration could not be completed.")
  }

  // Backward compatible overload for existing tests
  fun registerAccount(username: String, email: String, birthDateStr: String): Pair<Boolean, String> {
    return registerAccount(username, "", username, email, "+91 98765 00000", birthDateStr)
  }

  fun loginUser(identifier: String, pass: String): Boolean {
    _isLoggedIn.value = true
    return true
  }

  fun logout() {
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
}

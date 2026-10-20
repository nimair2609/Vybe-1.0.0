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
    bio = "Music Producer & Sound Designer 🎹 | Mumbai lo-fi sets | Mood: Chill vibes",
    avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400",
    coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1000",
    role = UserRole.CREATOR,
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
    bio = "Senior Product Designer in Bengaluru 🎨 | Coffee snob ☕ | Building human-centered UI",
    avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400",
    coverUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1000",
    role = UserRole.USER,
    isVerifiedPublicFigure = false,
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
    bio = "Cricket Analyst & Former State Player 🏏 | Tactical breakdowns & pitch analysis",
    avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400",
    role = UserRole.VERIFIED_PUBLIC_FIGURE,
    isVerifiedPublicFigure = true,
    vipBadgeTitle = "Sports Verified",
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
    bio = "Full-stack engineer & photographer 📸 | Exploring Mumbai alleyways | Age 22 (Verified 16+)",
    avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400",
    coverUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=1000",
    email = "sid.verma@example.com",
    phone = "+91 98765 43210",
    dateOfBirth = "2004-03-15",
    age = 22,
    isUnder18 = false,
    role = UserRole.CREATOR,
    isVerifiedPublicFigure = false,
    isVerifiedBusiness = false,
    followersCount = 1420,
    followingCount = 385,
    postsCount = 18,
    currentMood = MoodType.HAPPY,
    livePulse = LivePulseStatus.OPEN_TO_CHAT
  )

  // Current session state
  private val _currentUser = MutableStateFlow<User?>(userMe)
  val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

  private val _isLoggedIn = MutableStateFlow(true)
  val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

  // Posts Feed
  private val _posts = MutableStateFlow<List<Post>>(emptyList())
  val posts: StateFlow<List<Post>> = _posts.asStateFlow()

  // Stories
  private val _stories = MutableStateFlow<List<Story>>(emptyList())
  val stories: StateFlow<List<Story>> = _stories.asStateFlow()

  // Crossed Paths Encounters
  private val _crossedPaths = MutableStateFlow<List<CrossedPathEncounter>>(emptyList())
  val crossedPaths: StateFlow<List<CrossedPathEncounter>> = _crossedPaths.asStateFlow()

  // Time Capsules
  private val _timeCapsules = MutableStateFlow<List<TimeCapsule>>(emptyList())
  val timeCapsules: StateFlow<List<TimeCapsule>> = _timeCapsules.asStateFlow()

  // Double Take prompts
  private val _doubleTakePrompts = MutableStateFlow<List<DoubleTakePrompt>>(emptyList())
  val doubleTakePrompts: StateFlow<List<DoubleTakePrompt>> = _doubleTakePrompts.asStateFlow()

  // Social Predictions
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
        timestamp = "1h ago"
      ),
      Story(
        id = "s_2",
        author = userPriya,
        mediaUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600",
        caption = "Filter coffee & wireframing morning ☕",
        timestamp = "3h ago"
      ),
      Story(
        id = "s_3",
        author = userVikramCricket,
        mediaUrl = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=600",
        caption = "Matchday prep at Wankhede! 🏟️",
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
        comments = listOf(
          PostComment("c_1", "p_1", userPriya, "Looping this while designing right now! Absolutely pristine mix ✨", "1h ago", 12, true),
          PostComment("c_2", "p_1", userMe, "That snare sound is ridiculous. What mic did you use on the ambient rain?", "45m ago", 5, false)
        )
      ),
      Post(
        id = "p_2",
        author = userPriya,
        caption = "Reflecting on 5 years in UX: The best interfaces are the ones that respect human attention rather than exploiting dopamine loops. VYBE's consent-first discovery is proof we can build social without cynicism 🌱 #DesignEthics #ProductDesign",
        mediaUrls = listOf("https://images.unsplash.com/photo-1497215728101-856f4ea42174?w=800"),
        mediaType = MediaType.IMAGE,
        timestamp = "5 hours ago",
        likesCount = 512,
        commentsCount = 68,
        savesCount = 143,
        sharesCount = 31,
        isLiked = true,
        tags = listOf("DesignEthics", "ProductDesign", "Mindfulness"),
        moodZone = MoodType.HEALING
      ),
      Post(
        id = "p_3",
        author = userVikramCricket,
        caption = "18 runs needed in the final over with 2 wickets remaining! What happens next? Prediction pool is live in Explore! ⚡🏏 Can the tailenders pull off a historic win?",
        mediaUrls = listOf("https://images.unsplash.com/photo-1531415074868-036b107e775a?w=800"),
        mediaType = MediaType.IMAGE,
        timestamp = "7 hours ago",
        likesCount = 1420,
        commentsCount = 312,
        savesCount = 88,
        sharesCount = 205,
        isLiked = false,
        tags = listOf("CricketIndia", "Prediction", "IndVsAus"),
        moodZone = MoodType.FUN
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
        moodZone = MoodType.HAPPY
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
        title = "Priya liked your post",
        message = "Golden hour across Bandra-Worli Sea Link.",
        timestamp = "15m ago",
        actorUser = userPriya
      ),
      NotificationItem(
        id = "notif_2",
        type = NotificationType.TIME_CAPSULE_UNLOCK,
        title = "Time Capsule Unlocked! ⏳",
        message = "Aarav Mehta's sealed message 'Acoustic Demo Sealed' is now available to view.",
        timestamp = "1h ago",
        actorUser = userAarav
      ),
      NotificationItem(
        id = "notif_3",
        type = NotificationType.CROSSED_PATHS_REQUEST,
        title = "Crossed Paths Encounter 💫",
        message = "Someone in Indiranagar noticed your presence and sent a private handshake request.",
        timestamp = "3h ago",
        actorUser = null
      )
    )
  }

  // --- ACTIONS ---

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

  fun createPost(caption: String, mood: MoodType?, mediaType: MediaType = MediaType.TEXT, imageUrl: String? = null) {
    val user = _currentUser.value ?: return
    val newPost = Post(
      id = "p_${System.currentTimeMillis()}",
      author = user,
      caption = caption,
      mediaUrls = if (!imageUrl.isNullOrBlank()) listOf(imageUrl) else emptyList(),
      mediaType = if (!imageUrl.isNullOrBlank()) MediaType.IMAGE else mediaType,
      timestamp = "Just now",
      likesCount = 0,
      commentsCount = 0,
      savesCount = 0,
      sharesCount = 0,
      moodZone = mood,
      tags = listOf("VybeCommunity")
    )
    _posts.value = listOf(newPost) + _posts.value
  }

  fun selectMoodFilter(mood: MoodType?) {
    _selectedMoodFilter.value = mood
  }

  fun updateUserMoodAndPulse(mood: MoodType?, pulse: LivePulseStatus) {
    val user = _currentUser.value ?: return
    val updated = user.copy(currentMood = mood, livePulse = pulse)
    _currentUser.value = updated
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
      if (pred.userSelectedOptionId != null) return // Already voted
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

  fun registerAccount(username: String, email: String, birthDateStr: String): Pair<Boolean, String> {
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
          displayName = username,
          email = email,
          dateOfBirth = birthDateStr,
          age = age,
          isUnder18 = age in 16..17,
          role = UserRole.USER
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
}

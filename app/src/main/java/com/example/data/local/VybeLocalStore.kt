package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject

class VybeLocalStore private constructor(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("vybe_persistent_data", Context.MODE_PRIVATE)

  companion object {
    private const val KEY_POSTS = "saved_posts_json"
    private const val KEY_UNFILTERED = "saved_unfiltered_json"
    private const val KEY_CONVERSATIONS = "saved_conversations_json"
    private const val KEY_NOTIFICATIONS = "saved_notifications_json"
    private const val KEY_FOLLOWING = "saved_following_json"
    private const val KEY_VIEWED_STORIES = "saved_viewed_stories_json"
    private const val KEY_VYBE_MATCHES = "saved_vybe_matches_json"
    private const val KEY_USERS = "saved_users_json"
    private const val KEY_GITHUB_REPO = "saved_github_repo"
    private const val KEY_THEME_MODE = "saved_theme_mode"
    private const val KEY_DELETED_MESSAGE_IDS = "saved_deleted_message_ids_json"

    private val LEGACY_SAMPLE_IDS = setOf(
      "u_me", "u_aarav", "u_priya", "u_vikram",
      "conv_1", "conv_2",
      "notif_1", "notif_2", "notif_3", "notif_4", "notif_5", "notif_6", "notif_7", "notif_8"
    )

    @Volatile
    private var instance: VybeLocalStore? = null

    fun getInstance(context: Context): VybeLocalStore {
      return instance ?: synchronized(this) {
        instance ?: VybeLocalStore(context.applicationContext).also { instance = it }
      }
    }

    fun get(): VybeLocalStore? = instance
  }

  fun clearUserScopedCaches() {
    prefs.edit()
      .remove(KEY_CONVERSATIONS)
      .remove(KEY_NOTIFICATIONS)
      .remove(KEY_FOLLOWING)
      .remove(KEY_VIEWED_STORIES)
      .remove(KEY_VYBE_MATCHES)
      .remove(KEY_POSTS)
      .remove(KEY_UNFILTERED)
      .apply()
  }

  fun addDeletedMessageId(messageId: String) {
    if (messageId.isBlank()) return
    val current = loadDeletedMessageIds().toMutableSet()
    if (current.add(messageId)) {
      val arr = JSONArray()
      current.toList().takeLast(500).forEach { arr.put(it) }
      prefs.edit().putString(KEY_DELETED_MESSAGE_IDS, arr.toString()).apply()
    }
  }

  fun loadDeletedMessageIds(): Set<String> {
    val raw = prefs.getString(KEY_DELETED_MESSAGE_IDS, null) ?: return emptySet()
    return try {
      val arr = JSONArray(raw)
      buildSet {
        for (i in 0 until arr.length()) {
          val id = arr.optString(i)
          if (id.isNotBlank()) add(id)
        }
      }
    } catch (_: Exception) {
      emptySet()
    }
  }

  // --- Theme Mode ---
  fun getThemeMode(): AppThemeMode {
    val raw = prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
    return try { AppThemeMode.valueOf(raw) } catch (_: Exception) { AppThemeMode.DARK }
  }

  fun setThemeMode(mode: AppThemeMode) {
    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
  }

  // --- GitHub Repo Config for In-App Updates ---
  fun getGitHubRepo(): String {
    return prefs.getString(KEY_GITHUB_REPO, "owner/vybe-app") ?: "owner/vybe-app"
  }

  fun setGitHubRepo(repo: String) {
    prefs.edit().putString(KEY_GITHUB_REPO, repo.trim()).apply()
  }

  // --- Saved Users Cache ---
  fun saveUser(user: User) {
    if (LEGACY_SAMPLE_IDS.contains(user.id)) return
    val users = loadUsers().toMutableList()
    val idx = users.indexOfFirst { it.id == user.id || it.username.equals(user.username, ignoreCase = true) }
    if (idx != -1) {
      users[idx] = user
    } else {
      users.add(user)
    }
    saveUsers(users)
  }

  fun saveAllUsers(users: List<User>) {
    saveUsers(users.filterNot { LEGACY_SAMPLE_IDS.contains(it.id) })
  }

  fun loadUsers(): List<User> {
    val raw = prefs.getString(KEY_USERS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<User>()
      for (i in 0 until array.length()) {
        val u = userFromJson(array.getJSONObject(i))
        if (!LEGACY_SAMPLE_IDS.contains(u.id)) {
          list.add(u)
        }
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun saveUsers(users: List<User>) {
    val array = JSONArray()
    for (u in users) {
      if (!LEGACY_SAMPLE_IDS.contains(u.id)) {
        array.put(userToJson(u))
      }
    }
    prefs.edit().putString(KEY_USERS, array.toString()).apply()
  }

  // --- Posts Cache ---
  fun savePosts(posts: List<Post>) {
    val array = JSONArray()
    for (p in posts) {
      if (!LEGACY_SAMPLE_IDS.contains(p.author.id)) {
        array.put(postToJson(p))
      }
    }
    prefs.edit().putString(KEY_POSTS, array.toString()).apply()
  }

  fun loadPosts(): List<Post> {
    val raw = prefs.getString(KEY_POSTS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<Post>()
      for (i in 0 until array.length()) {
        val p = postFromJson(array.getJSONObject(i))
        if (!LEGACY_SAMPLE_IDS.contains(p.author.id)) {
          list.add(p)
        }
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  // --- Unfiltered Posts Cache ---
  fun saveUnfilteredPosts(posts: List<Post>) {
    val array = JSONArray()
    for (p in posts) {
      array.put(postToJson(p))
    }
    prefs.edit().putString(KEY_UNFILTERED, array.toString()).apply()
  }

  fun loadUnfilteredPosts(): List<Post> {
    val raw = prefs.getString(KEY_UNFILTERED, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<Post>()
      for (i in 0 until array.length()) {
        list.add(postFromJson(array.getJSONObject(i)))
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  // --- Conversations Cache ---
  fun saveConversations(convs: List<Conversation>) {
    val array = JSONArray()
    for (c in convs) {
      if (!LEGACY_SAMPLE_IDS.contains(c.id) && !LEGACY_SAMPLE_IDS.contains(c.peerUser.id)) {
        array.put(conversationToJson(c))
      }
    }
    prefs.edit().putString(KEY_CONVERSATIONS, array.toString()).apply()
  }

  fun loadConversations(): List<Conversation> {
    val raw = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<Conversation>()
      for (i in 0 until array.length()) {
        val c = conversationFromJson(array.getJSONObject(i))
        if (!LEGACY_SAMPLE_IDS.contains(c.id) && !LEGACY_SAMPLE_IDS.contains(c.peerUser.id)) {
          list.add(c)
        }
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  // --- Viewed Stories Cache (User-scoped) ---
  fun saveViewedStoryIds(userId: String, ids: Set<String>) {
    if (userId.isBlank()) return
    val array = JSONArray()
    ids.forEach { array.put(it) }
    prefs.edit().putString("${KEY_VIEWED_STORIES}_$userId", array.toString()).apply()
  }

  fun loadViewedStoryIds(userId: String): Set<String> {
    if (userId.isBlank()) return emptySet()
    val raw = prefs.getString("${KEY_VIEWED_STORIES}_$userId", null) ?: return emptySet()
    return try {
      val array = JSONArray(raw)
      val set = mutableSetOf<String>()
      for (i in 0 until array.length()) set.add(array.getString(i))
      set
    } catch (_: Exception) {
      emptySet()
    }
  }

  // --- VYBE Matches Cache (User-scoped) ---
  fun saveVybeMatches(userId: String, ids: Set<String>) {
    if (userId.isBlank()) return
    val array = JSONArray()
    ids.filterNot { LEGACY_SAMPLE_IDS.contains(it) }.forEach { array.put(it) }
    prefs.edit().putString("${KEY_VYBE_MATCHES}_$userId", array.toString()).apply()
  }

  fun loadVybeMatches(userId: String): Set<String> {
    if (userId.isBlank()) return emptySet()
    val raw = prefs.getString("${KEY_VYBE_MATCHES}_$userId", null) ?: return emptySet()
    return try {
      val array = JSONArray(raw)
      val set = mutableSetOf<String>()
      for (i in 0 until array.length()) {
        val id = array.getString(i)
        if (!LEGACY_SAMPLE_IDS.contains(id)) set.add(id)
      }
      set
    } catch (_: Exception) {
      emptySet()
    }
  }

  // ================= Serialization Helpers =================
  private fun userToJson(u: User): JSONObject {
    return JSONObject().apply {
      put("id", u.id)
      put("username", u.username)
      put("displayName", u.displayName)
      put("firstName", u.firstName)
      put("lastName", u.lastName)
      put("bio", u.bio)
      put("avatarUrl", u.avatarUrl)
      put("coverUrl", u.coverUrl)
      put("email", u.email)
      put("phone", u.phone)
      put("role", u.role.name)
      put("isVerified", u.isVerified)
      put("isVip", u.isVip)
      put("isPrivate", u.isPrivate)
      put("messagePrivacy", u.messagePrivacy)
      put("followersCount", u.followersCount)
      put("followingCount", u.followingCount)
      put("postsCount", u.postsCount)
      put("dateOfBirth", u.dateOfBirth)
      put("age", u.age)
      put("isUnder18", u.isUnder18)
      put("isEmailVerified", u.isEmailVerified)
      put("isPhoneVerified", u.isPhoneVerified)
      put("joinedDate", u.joinedDate)
      if (!u.createdAtIso.isNullOrBlank()) put("createdAtIso", u.createdAtIso)
      if (!u.lastSeenAtIso.isNullOrBlank()) put("lastSeenAtIso", u.lastSeenAtIso)
      put("livePulse", u.livePulse.name)
    }
  }

  private fun userFromJson(obj: JSONObject): User {
    val roleStr = obj.optString("role", UserRole.USER.name)
    val role = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.USER }
    val id = obj.optString("id", "u_unknown")
    val rawUsername = obj.optString("username", "").trim()
    val safeUsername = if (rawUsername.isBlank() || rawUsername.equals("null", true)) {
      "vybe_${id.filter { it.isLetterOrDigit() }.take(8).lowercase()}"
    } else rawUsername
    val rawDisplay = obj.optString("displayName", "").trim()
    val safeDisplay = if (rawDisplay.isBlank() || rawDisplay.equals("null", true)) safeUsername else rawDisplay
    val pulseStr = obj.optString("livePulse", LivePulseStatus.NONE.name)
    val pulse = try { LivePulseStatus.valueOf(pulseStr) } catch (_: Exception) { LivePulseStatus.NONE }
    val createdAtIso = obj.optString("createdAtIso", "").ifBlank { null }
    val lastSeenIso = obj.optString("lastSeenAtIso", "").ifBlank { null }

    return User(
      id = id,
      username = safeUsername,
      displayName = safeDisplay,
      firstName = obj.optString("firstName", "").takeUnless { it.equals("null", true) }.orEmpty(),
      lastName = obj.optString("lastName", "").takeUnless { it.equals("null", true) }.orEmpty(),
      bio = obj.optString("bio", "").takeUnless { it.equals("null", true) }.orEmpty(),
      avatarUrl = obj.optString("avatarUrl", "").takeUnless { it.equals("null", true) }.orEmpty(),
      coverUrl = obj.optString("coverUrl", "").takeUnless { it.equals("null", true) }.orEmpty(),
      email = obj.optString("email", "").takeUnless { it.equals("null", true) }.orEmpty(),
      phone = obj.optString("phone", "").takeUnless { it.equals("null", true) }.orEmpty(),
      isEmailVerified = obj.optBoolean("isEmailVerified", false),
      isPhoneVerified = obj.optBoolean("isPhoneVerified", false),
      role = role,
      isVerified = obj.optBoolean("isVerified", false),
      isVip = obj.optBoolean("isVip", false),
      isPrivate = obj.optBoolean("isPrivate", false),
      messagePrivacy = obj.optString("messagePrivacy", "EVERYONE"),
      followersCount = obj.optInt("followersCount", 0),
      followingCount = obj.optInt("followingCount", 0),
      postsCount = obj.optInt("postsCount", 0),
      dateOfBirth = obj.optString("dateOfBirth", "2000-01-01"),
      age = obj.optInt("age", 22),
      isUnder18 = obj.optBoolean("isUnder18", false),
      livePulse = pulse,
      joinedDate = obj.optString("joinedDate", "VYBE Member").takeUnless { it.equals("null", true) } ?: "VYBE Member",
      createdAtIso = createdAtIso,
      lastSeenAtIso = lastSeenIso,
      isOnline = com.example.util.TimestampUtils.isRecentlyOnline(lastSeenIso),
      isOnboardingCompleted = true
    )
  }

  private fun postToJson(p: Post): JSONObject {
    return JSONObject().apply {
      put("id", p.id)
      put("author", userToJson(p.author))
      put("caption", p.caption)
      val mediaArr = JSONArray()
      p.mediaUrls.forEach { mediaArr.put(it) }
      put("mediaUrls", mediaArr)
      put("mediaType", p.mediaType.name)
      put("timestamp", p.timestamp)
      if (!p.createdAtIso.isNullOrBlank()) put("createdAtIso", p.createdAtIso)
      put("likesCount", p.likesCount)
      put("commentsCount", p.commentsCount)
      put("savesCount", p.savesCount)
      put("sharesCount", p.sharesCount)
      put("viewsCount", p.viewsCount)
      put("isLiked", p.isLiked)
      put("isSaved", p.isSaved)
      put("isArchived", p.isArchived)
      put("isUnfiltered", p.isUnfiltered)
      put("isVybesReel", p.isVybesReel)
      put("moodZone", p.moodZone?.name)
      put("locationName", p.locationName)
      if (p.mediaAspectRatio != null) put("mediaAspectRatio", p.mediaAspectRatio.toDouble())
      if (p.userReaction != null) put("userReaction", p.userReaction.name)
      if (p.reactionsCount.isNotEmpty()) {
        val rcObj = JSONObject()
        p.reactionsCount.forEach { (k, v) -> rcObj.put(k.name, v) }
        put("reactionsCount", rcObj)
      }
    }
  }

  private fun postFromJson(obj: JSONObject): Post {
    val authorObj = obj.optJSONObject("author")
    val author = if (authorObj != null) userFromJson(authorObj) else User(id = "u_anon", username = "vybe", displayName = "Vyber")
    val mediaUrlsList = mutableListOf<String>()
    val mediaArr = obj.optJSONArray("mediaUrls")
    if (mediaArr != null) {
      for (i in 0 until mediaArr.length()) {
        mediaUrlsList.add(mediaArr.getString(i))
      }
    }
    val mediaTypeStr = obj.optString("mediaType", MediaType.IMAGE.name)
    val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (_: Exception) { MediaType.IMAGE }
    val moodStr = obj.optString("moodZone", null)
    val mood = if (!moodStr.isNullOrBlank() && !moodStr.equals("null", true)) {
      try { MoodType.valueOf(moodStr) } catch (_: Exception) { null }
    } else null
    val aspect = if (obj.has("mediaAspectRatio")) obj.optDouble("mediaAspectRatio").toFloat() else null
    val loc = obj.optString("locationName", "").takeUnless { it.isBlank() || it.equals("null", true) }
    val createdAtIso = obj.optString("createdAtIso", "").ifBlank { null }
    val userReactionStr = obj.optString("userReaction", "").takeUnless { it.isBlank() || it.equals("null", true) }
    val userReaction = userReactionStr?.let { VybeReaction.fromWire(it) }
    val reactionsMap = mutableMapOf<VybeReaction, Int>()
    val rcObj = obj.optJSONObject("reactionsCount")
    if (rcObj != null) {
      val keys = rcObj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val count = rcObj.optInt(k, 0)
        if (count > 0) {
          val rx = VybeReaction.fromWire(k)
          reactionsMap[rx] = (reactionsMap[rx] ?: 0) + count
        }
      }
    }

    return Post(
      id = obj.optString("id", "p_${System.currentTimeMillis()}"),
      author = author,
      caption = obj.optString("caption", ""),
      mediaUrls = mediaUrlsList,
      mediaType = mediaType,
      timestamp = obj.optString("timestamp", "Just now"),
      createdAtIso = createdAtIso,
      likesCount = obj.optInt("likesCount", 0),
      commentsCount = obj.optInt("commentsCount", 0),
      savesCount = obj.optInt("savesCount", 0),
      sharesCount = obj.optInt("sharesCount", 0),
      viewsCount = obj.optInt("viewsCount", 0),
      isLiked = obj.optBoolean("isLiked", false) || userReaction != null,
      isSaved = obj.optBoolean("isSaved", false),
      isArchived = obj.optBoolean("isArchived", false),
      isUnfiltered = obj.optBoolean("isUnfiltered", false),
      isVybesReel = obj.optBoolean("isVybesReel", false),
      moodZone = mood,
      locationName = loc,
      mediaAspectRatio = aspect,
      userReaction = userReaction,
      reactionsCount = reactionsMap
    )
  }

  private fun conversationToJson(c: Conversation): JSONObject {
    return JSONObject().apply {
      put("id", c.id)
      put("peerUser", userToJson(c.peerUser))
      put("lastMessage", c.lastMessage)
      put("lastMessageTimestamp", c.lastMessageTimestamp)
      if (!c.lastMessageCreatedAtIso.isNullOrBlank()) put("lastMessageCreatedAtIso", c.lastMessageCreatedAtIso)
      put("unreadCount", c.unreadCount)
      put("isGroup", c.isGroup)
      put("groupName", c.groupName)
      val msgs = JSONArray()
      for (m in c.messages) {
        val mObj = JSONObject().apply {
          put("id", m.id)
          put("senderId", m.senderId)
          put("text", m.text)
          put("timestamp", m.timestamp)
          if (!m.createdAtIso.isNullOrBlank()) put("createdAtIso", m.createdAtIso)
          put("isMe", m.isMe)
          put("mediaUrl", m.mediaUrl)
          put("mediaType", m.mediaType)
          put("isRead", m.isRead)
        }
        msgs.put(mObj)
      }
      put("messages", msgs)
    }
  }

  private fun conversationFromJson(obj: JSONObject): Conversation {
    val peerObj = obj.optJSONObject("peerUser")
    val peer = if (peerObj != null) userFromJson(peerObj) else User(id = "u_peer", username = "friend", displayName = "Friend")
    val msgsList = mutableListOf<DirectMessage>()
    val msgsArr = obj.optJSONArray("messages")
    if (msgsArr != null) {
      for (i in 0 until msgsArr.length()) {
        val mObj = msgsArr.getJSONObject(i)
        msgsList.add(
          DirectMessage(
            id = mObj.optString("id", "m_${System.currentTimeMillis()}"),
            senderId = mObj.optString("senderId", ""),
            text = mObj.optString("text", ""),
            timestamp = mObj.optString("timestamp", "Just now"),
            createdAtIso = mObj.optString("createdAtIso", "").ifBlank { null },
            isMe = mObj.optBoolean("isMe", false),
            mediaUrl = mObj.optString("mediaUrl", "").takeUnless { it.isBlank() || it.equals("null", true) },
            mediaType = mObj.optString("mediaType", "TEXT"),
            isRead = mObj.optBoolean("isRead", true)
          )
        )
      }
    }
    return Conversation(
      id = obj.optString("id", "conv_${System.currentTimeMillis()}"),
      peerUser = peer,
      lastMessage = obj.optString("lastMessage", ""),
      lastMessageTimestamp = obj.optString("lastMessageTimestamp", "Just now"),
      lastMessageCreatedAtIso = obj.optString("lastMessageCreatedAtIso", "").ifBlank { null },
      unreadCount = obj.optInt("unreadCount", 0),
      isGroup = obj.optBoolean("isGroup", false),
      groupName = obj.optString("groupName", "").takeUnless { it.isBlank() || it.equals("null", true) },
      messages = msgsList
    )
  }

  // --- Notifications Cache ---
  fun saveNotifications(notifications: List<NotificationItem>) {
    val array = JSONArray()
    for (n in notifications) {
      if (LEGACY_SAMPLE_IDS.contains(n.id)) continue
      val obj = JSONObject().apply {
        put("id", n.id)
        put("type", n.type.name)
        put("title", n.title)
        put("message", n.message)
        put("timestamp", n.timestamp)
        if (!n.createdAtIso.isNullOrBlank()) put("createdAtIso", n.createdAtIso)
        put("isRead", n.isRead)
        put("destinationScreen", n.destinationScreen)
        put("targetEntityId", n.targetEntityId)
        n.actorUser?.let { put("actorUser", userToJson(it)) }
      }
      array.put(obj)
    }
    prefs.edit().putString(KEY_NOTIFICATIONS, array.toString()).apply()
  }

  fun loadNotifications(): List<NotificationItem> {
    val raw = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<NotificationItem>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val id = obj.optString("id", "")
        if (LEGACY_SAMPLE_IDS.contains(id)) continue
        val actorObj = obj.optJSONObject("actorUser")
        val actor = if (actorObj != null) userFromJson(actorObj) else null
        val typeStr = obj.optString("type", NotificationType.LIKE.name)
        val type = try { NotificationType.valueOf(typeStr) } catch (_: Exception) { NotificationType.LIKE }
        list.add(
          NotificationItem(
            id = id.ifBlank { "notif_${System.currentTimeMillis()}" },
            type = type,
            title = obj.optString("title", ""),
            message = obj.optString("message", ""),
            timestamp = obj.optString("timestamp", "Just now"),
            createdAtIso = obj.optString("createdAtIso", "").ifBlank { null },
            isRead = obj.optBoolean("isRead", false),
            actorUser = actor,
            destinationScreen = obj.optString("destinationScreen", "MAIN"),
            targetEntityId = obj.optString("targetEntityId", "").takeUnless { it.isBlank() || it.equals("null", true) }
          )
        )
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  // --- Following User IDs Cache ---
  fun saveFollowing(followingIds: Set<String>) {
    val array = JSONArray()
    for (id in followingIds) {
      if (!LEGACY_SAMPLE_IDS.contains(id)) array.put(id)
    }
    prefs.edit().putString(KEY_FOLLOWING, array.toString()).apply()
  }

  fun loadFollowing(): Set<String> {
    val raw = prefs.getString(KEY_FOLLOWING, null) ?: return emptySet()
    return try {
      val array = JSONArray(raw)
      val set = mutableSetOf<String>()
      for (i in 0 until array.length()) {
        val id = array.getString(i)
        if (!LEGACY_SAMPLE_IDS.contains(id)) set.add(id)
      }
      set
    } catch (_: Exception) {
      emptySet()
    }
  }

  // --- Real Time Management & Usage Tracking ---
  fun addUsageMinutes(minutes: Int) {
    if (minutes <= 0) return
    val sdf = java.text.SimpleDateFormat("yyyy_MM_dd", java.util.Locale.US)
    val todayKey = "usage_min_" + sdf.format(java.util.Date())
    val current = prefs.getInt(todayKey, 0)
    prefs.edit().putInt(todayKey, current + minutes).apply()
  }

  fun getWeeklyUsageStats(): TimeManagementStats {
    val sdf = java.text.SimpleDateFormat("yyyy_MM_dd", java.util.Locale.US)
    val todayKey = "usage_min_" + sdf.format(java.util.Date())
    val todayMinutes = prefs.getInt(todayKey, 0)
    var totalWeekMinutes = 0
    var activeDays = 0

    for (i in 0..6) {
      val dayCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -i) }
      val dayKey = "usage_min_" + sdf.format(dayCal.time)
      val m = prefs.getInt(dayKey, 0)
      totalWeekMinutes += m
      if (m > 0 || i == 0) {
        activeDays++
      }
    }

    val dailyAvg = if (activeDays > 0) totalWeekMinutes / activeDays else todayMinutes
    val breakMin = prefs.getInt("time_mgmt_break_minutes", 45)
    val breakEnabled = prefs.getBoolean("time_mgmt_break_enabled", true)
    val limitMin = prefs.getInt("time_mgmt_daily_limit_minutes", 90)
    val limitEnabled = prefs.getBoolean("time_mgmt_daily_limit_enabled", false)

    return TimeManagementStats(
      todayUsageMinutes = todayMinutes,
      dailyAverageMinutes = dailyAvg,
      weeklyTotalHours = (totalWeekMinutes / 60f),
      breakReminderMinutes = breakMin,
      isBreakReminderEnabled = breakEnabled,
      dailyLimitMinutes = limitMin,
      isDailyLimitEnabled = limitEnabled
    )
  }

  fun saveTimeManagementSettings(breakMinutes: Int, isEnabled: Boolean) {
    prefs.edit()
      .putInt("time_mgmt_break_minutes", breakMinutes)
      .putBoolean("time_mgmt_break_enabled", isEnabled)
      .apply()
  }
}

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
    private const val KEY_STORIES = "saved_stories_json"
    private const val KEY_CONVERSATIONS = "saved_conversations_json"
    private const val KEY_NOTIFICATIONS = "saved_notifications_json"
    private const val KEY_FOLLOWING = "saved_following_json"
    private const val KEY_USERS = "saved_users_json"
    private const val KEY_GITHUB_REPO = "saved_github_repo"

    @Volatile
    private var instance: VybeLocalStore? = null

    fun getInstance(context: Context): VybeLocalStore {
      return instance ?: synchronized(this) {
        instance ?: VybeLocalStore(context.applicationContext).also { instance = it }
      }
    }

    fun get(): VybeLocalStore? = instance
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
    val users = loadUsers().toMutableList()
    val idx = users.indexOfFirst { it.id == user.id || it.username.equals(user.username, ignoreCase = true) }
    if (idx != -1) {
      users[idx] = user
    } else {
      users.add(user)
    }
    saveUsers(users)
  }

  fun loadUsers(): List<User> {
    val raw = prefs.getString(KEY_USERS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<User>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(userFromJson(obj))
      }
      list
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun saveUsers(users: List<User>) {
    val array = JSONArray()
    for (u in users) {
      array.put(userToJson(u))
    }
    prefs.edit().putString(KEY_USERS, array.toString()).apply()
  }

  // --- Posts Cache ---
  fun savePosts(posts: List<Post>) {
    val array = JSONArray()
    for (p in posts) {
      array.put(postToJson(p))
    }
    prefs.edit().putString(KEY_POSTS, array.toString()).apply()
  }

  fun loadPosts(): List<Post> {
    val raw = prefs.getString(KEY_POSTS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<Post>()
      for (i in 0 until array.length()) {
        list.add(postFromJson(array.getJSONObject(i)))
      }
      list
    } catch (e: Exception) {
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
    } catch (e: Exception) {
      emptyList()
    }
  }

  // --- Conversations Cache ---
  fun saveConversations(convs: List<Conversation>) {
    val array = JSONArray()
    for (c in convs) {
      array.put(conversationToJson(c))
    }
    prefs.edit().putString(KEY_CONVERSATIONS, array.toString()).apply()
  }

  fun loadConversations(): List<Conversation> {
    val raw = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = mutableListOf<Conversation>()
      for (i in 0 until array.length()) {
        list.add(conversationFromJson(array.getJSONObject(i)))
      }
      list
    } catch (e: Exception) {
      emptyList()
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
      put("followersCount", u.followersCount)
      put("followingCount", u.followingCount)
      put("postsCount", u.postsCount)
      put("dateOfBirth", u.dateOfBirth)
      put("age", u.age)
      put("isUnder18", u.isUnder18)
    }
  }

  private fun userFromJson(obj: JSONObject): User {
    val roleStr = obj.optString("role", UserRole.USER.name)
    val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.USER }
    return User(
      id = obj.optString("id", "u_unknown"),
      username = obj.optString("username", "user"),
      displayName = obj.optString("displayName", "User"),
      firstName = obj.optString("firstName", ""),
      lastName = obj.optString("lastName", ""),
      bio = obj.optString("bio", ""),
      avatarUrl = obj.optString("avatarUrl", ""),
      coverUrl = obj.optString("coverUrl", ""),
      email = obj.optString("email", ""),
      phone = obj.optString("phone", ""),
      role = role,
      isVerified = obj.optBoolean("isVerified", false),
      followersCount = obj.optInt("followersCount", 0),
      followingCount = obj.optInt("followingCount", 0),
      postsCount = obj.optInt("postsCount", 0),
      dateOfBirth = obj.optString("dateOfBirth", "2000-01-01"),
      age = obj.optInt("age", 22),
      isUnder18 = obj.optBoolean("isUnder18", false),
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
      put("likesCount", p.likesCount)
      put("commentsCount", p.commentsCount)
      put("savesCount", p.savesCount)
      put("sharesCount", p.sharesCount)
      put("viewsCount", p.viewsCount)
      put("isLiked", p.isLiked)
      put("isSaved", p.isSaved)
      put("isUnfiltered", p.isUnfiltered)
      put("isVybesReel", p.isVybesReel)
      put("moodZone", p.moodZone?.name)
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
    val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (e: Exception) { MediaType.IMAGE }
    val moodStr = obj.optString("moodZone", null)
    val mood = if (!moodStr.isNullOrBlank()) {
      try { MoodType.valueOf(moodStr) } catch (e: Exception) { null }
    } else null

    return Post(
      id = obj.optString("id", "p_${System.currentTimeMillis()}"),
      author = author,
      caption = obj.optString("caption", ""),
      mediaUrls = mediaUrlsList,
      mediaType = mediaType,
      timestamp = obj.optString("timestamp", "Just now"),
      likesCount = obj.optInt("likesCount", 0),
      commentsCount = obj.optInt("commentsCount", 0),
      savesCount = obj.optInt("savesCount", 0),
      sharesCount = obj.optInt("sharesCount", 0),
      viewsCount = obj.optInt("viewsCount", 0),
      isLiked = obj.optBoolean("isLiked", false),
      isSaved = obj.optBoolean("isSaved", false),
      isUnfiltered = obj.optBoolean("isUnfiltered", false),
      isVybesReel = obj.optBoolean("isVybesReel", false),
      moodZone = mood
    )
  }

  private fun conversationToJson(c: Conversation): JSONObject {
    return JSONObject().apply {
      put("id", c.id)
      put("peerUser", userToJson(c.peerUser))
      put("lastMessage", c.lastMessage)
      put("lastMessageTimestamp", c.lastMessageTimestamp)
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
            isMe = mObj.optBoolean("isMe", false),
            mediaUrl = mObj.optString("mediaUrl", null),
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
      unreadCount = obj.optInt("unreadCount", 0),
      isGroup = obj.optBoolean("isGroup", false),
      groupName = obj.optString("groupName", null),
      messages = msgsList
    )
  }

  // --- Notifications Cache ---
  fun saveNotifications(notifications: List<NotificationItem>) {
    val array = JSONArray()
    for (n in notifications) {
      val obj = JSONObject().apply {
        put("id", n.id)
        put("type", n.type.name)
        put("title", n.title)
        put("message", n.message)
        put("timestamp", n.timestamp)
        put("isRead", n.isRead)
        put("destinationScreen", n.destinationScreen)
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
        val actorObj = obj.optJSONObject("actorUser")
        val actor = if (actorObj != null) userFromJson(actorObj) else null
        val typeStr = obj.optString("type", NotificationType.LIKE.name)
        val type = try { NotificationType.valueOf(typeStr) } catch (e: Exception) { NotificationType.LIKE }
        list.add(
          NotificationItem(
            id = obj.optString("id", "notif_${System.currentTimeMillis()}"),
            type = type,
            title = obj.optString("title", ""),
            message = obj.optString("message", ""),
            timestamp = obj.optString("timestamp", "Just now"),
            isRead = obj.optBoolean("isRead", false),
            actorUser = actor,
            destinationScreen = obj.optString("destinationScreen", "MAIN")
          )
        )
      }
      list
    } catch (e: Exception) {
      emptyList()
    }
  }

  // --- Following User IDs Cache ---
  fun saveFollowing(followingIds: Set<String>) {
    val array = JSONArray()
    for (id in followingIds) {
      array.put(id)
    }
    prefs.edit().putString(KEY_FOLLOWING, array.toString()).apply()
  }

  fun loadFollowing(): Set<String> {
    val raw = prefs.getString(KEY_FOLLOWING, null) ?: return emptySet()
    return try {
      val array = JSONArray(raw)
      val set = mutableSetOf<String>()
      for (i in 0 until array.length()) {
        set.add(array.getString(i))
      }
      set
    } catch (e: Exception) {
      emptySet()
    }
  }
}


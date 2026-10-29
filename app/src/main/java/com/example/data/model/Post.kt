package com.example.data.model

enum class MediaType {
  TEXT, IMAGE, VIDEO, REEL
}

enum class VybeReaction(val label: String, val emoji: String) {
  LOVE("LOVE", "🫰"),
  PULSE("PULSE", "⚡"),
  SPARK("SPARK", "✨"),
  WAVE("WAVE", "💫"),
  FLAME("FLAME", "🔥");

  companion object {
    fun fromWire(raw: String?): VybeReaction {
      return when (raw?.trim()?.uppercase()) {
        "LOVE", "HEART", "FINGER_HEART", "LIKE" -> LOVE
        "PULSE", "PURE_ENERGY" -> PULSE
        "SPARK", "UPLIFT" -> SPARK
        "WAVE", "DEEP_VIBE", "CALM" -> WAVE
        "FLAME", "HEAT" -> FLAME
        else -> LOVE
      }
    }
  }
}

data class PostComment(
  val id: String,
  val postId: String,
  val author: User,
  val content: String,
  val timestamp: String,
  val createdAtIso: String? = null,
  val likesCount: Int = 0,
  val isLiked: Boolean = false
)

data class Post(
  val id: String,
  val author: User,
  val caption: String,
  val mediaUrls: List<String> = emptyList(),
  val mediaType: MediaType = MediaType.TEXT,
  val timestamp: String,
  val createdAtIso: String? = null,
  val likesCount: Int = 0,
  val commentsCount: Int = 0,
  val savesCount: Int = 0,
  val sharesCount: Int = 0,
  val viewsCount: Int = 0,
  val isLiked: Boolean = false,
  val isSaved: Boolean = false,
  val isArchived: Boolean = false,
  val isUnfiltered: Boolean = false, // "Back to Real. Back to Us." authentic moment (Raw Zone only)
  val isVybesReel: Boolean = false,   // VYBES short video (Reels Zone)
  val tags: List<String> = emptyList(),
  val moodZone: MoodType? = null,
  val remixSourceId: String? = null,
  val remixOriginalAuthor: String? = null,
  val comments: List<PostComment> = emptyList(),
  val audioTrackTitle: String? = null,
  val musicAttachment: MusicAttachment? = null,
  val locationName: String? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val mediaAspectRatio: Float? = null,
  val storagePaths: List<String> = emptyList(),
  val clientUploadId: String? = null,
  val isPinned: Boolean = false,
  val userReaction: VybeReaction? = null,
  val reactionsCount: Map<VybeReaction, Int> = emptyMap()
) {
  fun reactionCountFor(reaction: VybeReaction): Int = (reactionsCount[reaction] ?: 0).coerceAtLeast(0)

  val totalReactionsCount: Int
    get() {
      val mapSum = VybeReaction.values().sumOf { reactionCountFor(it) }
      return maxOf(likesCount.coerceAtLeast(0), mapSum)
    }
}

/**
 * Ensures consistent mood filtering across Feed and Mood Zones:
 * - `null` ("All") displays all posts.
 * - `MoodType.GENERAL` displays posts explicitly tagged General AND posts without an explicit mood (`moodZone == null`).
 * - Any specific mood displays posts tagged with that mood.
 */
fun Post.matchesMoodFilter(filter: MoodType?): Boolean = when (filter) {
  null -> true
  MoodType.GENERAL -> moodZone == null || moodZone == MoodType.GENERAL
  else -> moodZone == filter
}

/**
 * Main feed shows all active published posts (images, text, video, and reels shared to feed).
 * Raw moments (isUnfiltered = true) stay isolated to the Unfiltered/Raw zone, and archived posts remain hidden.
 */
fun Post.belongsToMainFeed(): Boolean = !isArchived && !isUnfiltered



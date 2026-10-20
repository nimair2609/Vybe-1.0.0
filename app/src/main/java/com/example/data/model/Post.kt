package com.example.data.model

enum class MediaType {
  TEXT, IMAGE, VIDEO, REEL
}

enum class VybeReaction(val label: String, val emoji: String) {
  PURE_ENERGY("Pure Energy", "⚡"),
  DEEP_VIBE("Deep Vibe", "💜"),
  UPLIFT("Uplift", "✨"),
  CALM("Calm", "🌊"),
  HEAT("Heat", "🔥")
}

data class PostComment(
  val id: String,
  val postId: String,
  val author: User,
  val content: String,
  val timestamp: String,
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
  val likesCount: Int = 0,
  val commentsCount: Int = 0,
  val savesCount: Int = 0,
  val sharesCount: Int = 0,
  val isLiked: Boolean = false,
  val isSaved: Boolean = false,
  val isArchived: Boolean = false,
  val isUnfiltered: Boolean = false, // "Back to Real. Back to Us." authentic moment
  val isVybesReel: Boolean = false,   // VYBES short video
  val tags: List<String> = emptyList(),
  val moodZone: MoodType? = null,
  val remixSourceId: String? = null,
  val remixOriginalAuthor: String? = null,
  val comments: List<PostComment> = emptyList(),
  val audioTrackTitle: String? = null,
  val isPinned: Boolean = false,
  val userReaction: VybeReaction? = null,
  val reactionsCount: Map<VybeReaction, Int> = emptyMap()
)

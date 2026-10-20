package com.example.data.model

enum class StoryMediaType {
  PHOTO, VIDEO, TEXT, MUSIC
}

enum class StoryAudience {
  EVERYONE, CLOSE_FRIENDS, ONLY_ME
}

data class Story(
  val id: String,
  val author: User,
  val mediaUrl: String = "",
  val caption: String = "",
  val mediaType: StoryMediaType = StoryMediaType.PHOTO,
  val audience: StoryAudience = StoryAudience.EVERYONE,
  val musicTrackTitle: String? = null,
  val timestamp: String = "2h ago",
  val expiresHoursRemaining: Int = 22,
  val isViewed: Boolean = false,
  val reactionsCount: Int = 14,
  val isLikedByMe: Boolean = false
)

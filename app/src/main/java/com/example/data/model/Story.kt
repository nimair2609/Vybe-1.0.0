package com.example.data.model

enum class StoryMediaType {
  PHOTO, VIDEO, TEXT, MUSIC
}

enum class StoryAudience(val label: String) {
  PUBLIC("Public"),
  VYBE_MATCHES("VYBE Matches"),
  EVERYONE("Public"),
  CLOSE_FRIENDS("VYBE Matches"),
  ONLY_ME("Only Me")
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
  val isLikedByMe: Boolean = false,
  val location: String? = null,
  val textBackgroundColor: String? = null
)

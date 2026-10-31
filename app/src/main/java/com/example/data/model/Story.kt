package com.example.data.model

enum class StoryMediaType {
  PHOTO, VIDEO, TEXT, MUSIC
}

enum class StoryAudience(val label: String) {
  PUBLIC("Public"),
  FOLLOWERS("LINKUPS"),
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
  val musicAttachment: MusicAttachment? = null,
  val timestamp: String = "Just now",
  val expiresHoursRemaining: Int = 24,
  val isViewed: Boolean = false,
  val reactionsCount: Int = 0,
  val isLikedByMe: Boolean = false,
  val location: String? = null,
  val textBackgroundColor: String? = null,
  val createdAtIso: String? = null,
  val expiresAtIso: String? = null,
  val storagePath: String? = null,
  val clientUploadId: String? = null
)

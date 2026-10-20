package com.example.data.model

enum class MediaType {
  TEXT, IMAGE, VIDEO, REEL
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
  val tags: List<String> = emptyList(),
  val moodZone: MoodType? = null,
  val remixSourceId: String? = null,
  val remixOriginalAuthor: String? = null,
  val comments: List<PostComment> = emptyList(),
  val audioTrackTitle: String? = null,
  val isPinned: Boolean = false
)

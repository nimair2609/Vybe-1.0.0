package com.example.data.model

data class Story(
  val id: String,
  val author: User,
  val mediaUrl: String,
  val caption: String = "",
  val timestamp: String = "2h ago",
  val expiresHoursRemaining: Int = 22,
  val isViewed: Boolean = false,
  val reactionsCount: Int = 14
)

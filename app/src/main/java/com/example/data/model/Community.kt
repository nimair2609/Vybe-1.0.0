package com.example.data.model

data class Community(
  val id: String,
  val name: String,
  val description: String = "",
  val category: String = "General",
  val avatarUrl: String? = null,
  val bannerUrl: String? = null,
  val creatorId: String = "",
  val isPrivate: Boolean = false,
  val membersCount: Int = 1,
  val isJoined: Boolean = false,
  val createdAtIso: String? = null
)

data class CommunityPost(
  val id: String,
  val communityId: String,
  val author: User,
  val title: String,
  val content: String = "",
  val mediaUrl: String? = null,
  val likesCount: Int = 0,
  val commentsCount: Int = 0,
  val isLikedByMe: Boolean = false,
  val createdAtIso: String? = null
)

data class CommunityComment(
  val id: String,
  val postId: String,
  val author: User,
  val content: String,
  val createdAtIso: String? = null
)

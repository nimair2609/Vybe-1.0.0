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
  val membershipStatus: String = "NONE", // NONE, ACCEPTED, PENDING
  val myRole: String = "MEMBER", // ADMIN, MODERATOR, MEMBER
  val createdAtIso: String? = null
)

data class CommunityMember(
  val communityId: String,
  val user: User,
  val role: String = "MEMBER", // ADMIN, MODERATOR, MEMBER
  val status: String = "ACCEPTED", // ACCEPTED, PENDING
  val joinedAtIso: String? = null
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
  val isEdited: Boolean = false,
  val createdAtIso: String? = null,
  val updatedAtIso: String? = null
)

data class CommunityComment(
  val id: String,
  val postId: String,
  val author: User,
  val content: String,
  val createdAtIso: String? = null
)

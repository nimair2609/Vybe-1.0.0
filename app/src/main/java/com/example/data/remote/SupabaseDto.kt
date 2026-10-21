package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseSignUpRequest(
  val email: String,
  val password: String,
  val data: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseSignInRequest(
  val email: String,
  val password: String
)

@JsonClass(generateAdapter = true)
data class SupabaseVerifyOtpRequest(
  val type: String,
  val token: String,
  val email: String? = null,
  val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseRecoverPasswordRequest(
  val email: String
)

@JsonClass(generateAdapter = true)
data class SupabaseUpdateUserRequest(
  val password: String? = null,
  val data: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
  @Json(name = "access_token") val accessToken: String? = null,
  @Json(name = "token_type") val tokenType: String? = null,
  @Json(name = "expires_in") val expiresIn: Long? = null,
  @Json(name = "refresh_token") val refreshToken: String? = null,
  val user: SupabaseUserDto? = null,
  @Json(name = "error_description") val errorDescription: String? = null,
  @Json(name = "msg") val msg: String? = null,
  val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseUserDto(
  val id: String,
  val email: String? = null,
  val phone: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "user_metadata") val userMetadata: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseProfileDto(
  val id: String,
  val username: String,
  @Json(name = "display_name") val displayName: String,
  @Json(name = "first_name") val firstName: String? = null,
  @Json(name = "last_name") val lastName: String? = null,
  val bio: String? = null,
  @Json(name = "avatar_url") val avatarUrl: String? = null,
  @Json(name = "cover_url") val coverUrl: String? = null,
  val email: String? = null,
  val phone: String? = null,
  @Json(name = "date_of_birth") val dateOfBirth: String? = null,
  val age: Int? = null,
  @Json(name = "is_under_18") val isUnder18: Boolean? = false,
  val role: String? = "USER",
  @Json(name = "is_verified") val isVerified: Boolean? = false,
  @Json(name = "is_vip") val isVip: Boolean? = false,
  @Json(name = "followers_count") val followersCount: Int? = 0,
  @Json(name = "following_count") val followingCount: Int? = 0,
  @Json(name = "posts_count") val postsCount: Int? = 0
)

@JsonClass(generateAdapter = true)
data class SupabaseMessageDto(
  val id: String? = null,
  @Json(name = "conversation_id") val conversationId: String,
  @Json(name = "sender_id") val senderId: String,
  val content: String,
  @Json(name = "media_url") val mediaUrl: String? = null,
  @Json(name = "media_type") val mediaType: String? = "TEXT",
  @Json(name = "reply_to_message_id") val replyToMessageId: String? = null,
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseCommentDto(
  val id: String? = null,
  @Json(name = "post_id") val postId: String,
  @Json(name = "author_id") val authorId: String,
  val content: String,
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseNotificationDto(
  val id: String? = null,
  @Json(name = "recipient_id") val recipientId: String,
  @Json(name = "actor_id") val actorId: String? = null,
  val type: String,
  val title: String,
  val message: String,
  @Json(name = "target_id") val targetId: String? = null,
  @Json(name = "is_read") val isRead: Boolean? = false,
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseReportDto(
  val id: String? = null,
  @Json(name = "reporter_id") val reporterId: String,
  @Json(name = "target_type") val targetType: String,
  @Json(name = "target_id") val targetId: String,
  val reason: String,
  @Json(name = "admin_notes") val adminNotes: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseBlockDto(
  @Json(name = "blocker_id") val blockerId: String,
  @Json(name = "blocked_id") val blockedId: String,
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseFollowDto(
  @Json(name = "follower_id") val followerId: String,
  @Json(name = "followed_id") val followedId: String,
  @Json(name = "created_at") val createdAt: String? = null
)


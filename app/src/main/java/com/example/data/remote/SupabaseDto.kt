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
data class SupabaseRefreshTokenRequest(
  @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseVerifyOtpRequest(
  val type: String,
  val token: String? = null,
  @Json(name = "token_hash") val tokenHash: String? = null,
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
  @Json(name = "error_code") val errorCode: String? = null,
  @Json(name = "msg") val msg: String? = null,
  val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseUserDto(
  val id: String,
  val email: String? = null,
  val phone: String? = null,
  @Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null,
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
  @Json(name = "is_private_account") val isPrivateAccount: Boolean? = false,
  @Json(name = "message_privacy") val messagePrivacy: String? = "EVERYONE",
  @Json(name = "followers_count") val followersCount: Int? = 0,
  @Json(name = "following_count") val followingCount: Int? = 0,
  @Json(name = "posts_count") val postsCount: Int? = 0
)

@JsonClass(generateAdapter = true)
data class SupabaseProfilePatchDto(
  @Json(name = "display_name") val displayName: String? = null,
  @Json(name = "first_name") val firstName: String? = null,
  @Json(name = "last_name") val lastName: String? = null,
  val bio: String? = null,
  @Json(name = "avatar_url") val avatarUrl: String? = null,
  @Json(name = "cover_url") val coverUrl: String? = null,
  @Json(name = "is_private_account") val isPrivateAccount: Boolean? = null,
  @Json(name = "message_privacy") val messagePrivacy: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseConversationDto(
  val id: String? = null,
  @Json(name = "is_group") val isGroup: Boolean = false,
  @Json(name = "group_name") val groupName: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseConversationMemberDto(
  @Json(name = "conversation_id") val conversationId: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "is_muted") val isMuted: Boolean? = false,
  @Json(name = "is_hidden") val isHidden: Boolean? = false,
  @Json(name = "custom_nickname") val customNickname: String? = null,
  @Json(name = "chat_theme") val chatTheme: String? = "DEFAULT",
  val profiles: SupabaseProfileDto? = null,
  val conversations: SupabaseConversationDto? = null
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
  @Json(name = "is_deleted_for_everyone") val isDeletedForEveryone: Boolean? = false,
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseCommentDto(
  val id: String? = null,
  @Json(name = "post_id") val postId: String,
  @Json(name = "author_id") val authorId: String,
  val content: String,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseReactionDto(
  @Json(name = "post_id") val postId: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "reaction_type") val reactionType: String = "PURE_ENERGY",
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseSavedPostDto(
  @Json(name = "user_id") val userId: String,
  @Json(name = "post_id") val postId: String,
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
data class SupabaseRestrictionDto(
  @Json(name = "restrictor_id") val restrictorId: String,
  @Json(name = "restricted_id") val restrictedId: String,
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseFollowDto(
  @Json(name = "follower_id") val followerId: String,
  @Json(name = "followed_id") val followedId: String,
  val status: String? = null,
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseVybeMatchDto(
  @Json(name = "user_id") val userId: String,
  @Json(name = "matched_user_id") val matchedUserId: String,
  @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseStoryDto(
  val id: String? = null,
  @Json(name = "author_id") val authorId: String,
  @Json(name = "media_url") val mediaUrl: String,
  @Json(name = "media_type") val mediaType: String = "PHOTO",
  val caption: String? = "",
  val audience: String? = "EVERYONE",
  @Json(name = "music_title") val musicTitle: String? = null,
  @Json(name = "location_name") val locationName: String? = null,
  @Json(name = "text_bg_key") val textBgKey: String? = null,
  @Json(name = "storage_path") val storagePath: String? = null,
  @Json(name = "client_upload_id") val clientUploadId: String? = null,
  @Json(name = "expires_at") val expiresAt: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseStoryLegacyInsertDto(
  @Json(name = "author_id") val authorId: String,
  @Json(name = "media_url") val mediaUrl: String,
  @Json(name = "media_type") val mediaType: String = "PHOTO",
  val caption: String? = "",
  @Json(name = "expires_at") val expiresAt: String
)

@JsonClass(generateAdapter = true)
data class SupabaseStoryViewDto(
  @Json(name = "story_id") val storyId: String,
  @Json(name = "viewer_id") val viewerId: String,
  @Json(name = "viewed_at") val viewedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabasePostDto(
  val id: String,
  @Json(name = "author_id") val authorId: String,
  val caption: String? = "",
  @Json(name = "media_type") val mediaType: String? = "TEXT",
  @Json(name = "mood_zone") val moodZone: String? = null,
  @Json(name = "location_name") val locationName: String? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  @Json(name = "music_title") val musicTitle: String? = null,
  @Json(name = "is_unfiltered") val isUnfiltered: Boolean? = false,
  @Json(name = "is_vybes_reel") val isVybesReel: Boolean? = false,
  @Json(name = "is_spiritual") val isSpiritual: Boolean? = false,
  @Json(name = "is_archived") val isArchived: Boolean? = false,
  @Json(name = "likes_count") val likesCount: Int? = 0,
  @Json(name = "comments_count") val commentsCount: Int? = 0,
  @Json(name = "saves_count") val savesCount: Int? = 0,
  @Json(name = "views_count") val viewsCount: Int? = 0,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "post_media") val postMedia: List<SupabasePostMediaDto>? = null,
  val profiles: SupabaseProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class SupabasePostInsertDto(
  val id: String? = null,
  @Json(name = "author_id") val authorId: String,
  val caption: String? = "",
  @Json(name = "media_type") val mediaType: String? = "TEXT",
  @Json(name = "mood_zone") val moodZone: String? = null,
  @Json(name = "location_name") val locationName: String? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  @Json(name = "music_title") val musicTitle: String? = null,
  @Json(name = "client_upload_id") val clientUploadId: String? = null,
  @Json(name = "is_unfiltered") val isUnfiltered: Boolean? = false,
  @Json(name = "is_vybes_reel") val isVybesReel: Boolean? = false,
  @Json(name = "views_count") val viewsCount: Int? = 0
)

@JsonClass(generateAdapter = true)
data class SupabasePostLegacyInsertDto(
  @Json(name = "author_id") val authorId: String,
  val caption: String? = "",
  @Json(name = "media_type") val mediaType: String? = "TEXT",
  @Json(name = "mood_zone") val moodZone: String? = null,
  @Json(name = "is_unfiltered") val isUnfiltered: Boolean? = false,
  @Json(name = "is_vybes_reel") val isVybesReel: Boolean? = false,
  @Json(name = "views_count") val viewsCount: Int? = 0
)

@JsonClass(generateAdapter = true)
data class SupabasePostMediaDto(
  val id: String? = null,
  @Json(name = "post_id") val postId: String,
  @Json(name = "media_url") val mediaUrl: String,
  @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
  @Json(name = "media_type") val mediaType: String = "IMAGE",
  @Json(name = "display_order") val displayOrder: Int = 0,
  @Json(name = "aspect_ratio") val aspectRatio: Double? = null,
  @Json(name = "storage_path") val storagePath: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabasePostMediaLegacyDto(
  @Json(name = "post_id") val postId: String,
  @Json(name = "media_url") val mediaUrl: String,
  @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
  @Json(name = "media_type") val mediaType: String = "IMAGE",
  @Json(name = "display_order") val displayOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class SupabaseRpcIncrementViewRequest(
  @Json(name = "p_post_id") val postId: String,
  @Json(name = "p_viewer_id") val viewerId: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseRpcPeerConversationRequest(
  @Json(name = "p_peer_id") val peerId: String
)

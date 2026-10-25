package com.example.data.remote

import com.squareup.moshi.Json

data class SupabaseSignUpRequest(
  val email: String,
  val password: String,
  val phone: String? = null,
  val data: Map<String, String>? = null
)

data class SupabaseSignInRequest(
  val email: String,
  val password: String
)

data class SupabasePhoneSignInRequest(
  val phone: String,
  val password: String
)

data class SupabaseOtpSendRequest(
  val email: String? = null,
  val phone: String? = null,
  @Json(name = "create_user") val createUser: Boolean = false
)

data class SupabaseResendOtpRequest(
  val type: String,
  val email: String? = null,
  val phone: String? = null
)

data class SupabaseRefreshTokenRequest(
  @Json(name = "refresh_token") val refreshToken: String
)

data class SupabaseVerifyOtpRequest(
  val type: String,
  val token: String? = null,
  @Json(name = "token_hash") val tokenHash: String? = null,
  val email: String? = null,
  val phone: String? = null
)

data class SupabaseRecoverPasswordRequest(
  val email: String
)

data class SupabaseUpdateUserRequest(
  val password: String? = null,
  val email: String? = null,
  val phone: String? = null,
  val data: Map<String, Any?>? = null
)

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

data class SupabaseUserDto(
  val id: String,
  val email: String? = null,
  val phone: String? = null,
  @Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null,
  @Json(name = "phone_confirmed_at") val phoneConfirmedAt: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "user_metadata") val userMetadata: Map<String, Any?>? = null
)

/**
 * NOTE: `username` and `displayName` are nullable in the wire DTO so any legacy profile row
 * with NULL username/display_name never breaks Moshi deserialization of the entire feed or story tray.
 */
data class SupabaseProfileDto(
  val id: String,
  val username: String? = null,
  @Json(name = "display_name") val displayName: String? = null,
  @Json(name = "first_name") val firstName: String? = null,
  @Json(name = "last_name") val lastName: String? = null,
  val bio: String? = null,
  @Json(name = "avatar_url") val avatarUrl: String? = null,
  @Json(name = "cover_url") val coverUrl: String? = null,
  val email: String? = null,
  val phone: String? = null,
  @Json(name = "is_email_verified") val isEmailVerified: Boolean? = null,
  @Json(name = "is_phone_verified") val isPhoneVerified: Boolean? = null,
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
  @Json(name = "posts_count") val postsCount: Int? = 0,
  @Json(name = "live_pulse") val livePulse: String? = null,
  @Json(name = "last_seen_at") val lastSeenAt: String? = null,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseProfilePatchDto(
  val username: String? = null,
  @Json(name = "display_name") val displayName: String? = null,
  @Json(name = "first_name") val firstName: String? = null,
  @Json(name = "last_name") val lastName: String? = null,
  val bio: String? = null,
  @Json(name = "avatar_url") val avatarUrl: String? = null,
  @Json(name = "cover_url") val coverUrl: String? = null,
  val email: String? = null,
  val phone: String? = null,
  @Json(name = "is_email_verified") val isEmailVerified: Boolean? = null,
  @Json(name = "is_phone_verified") val isPhoneVerified: Boolean? = null,
  @Json(name = "is_private_account") val isPrivateAccount: Boolean? = null,
  @Json(name = "message_privacy") val messagePrivacy: String? = null,
  @Json(name = "live_pulse") val livePulse: String? = null,
  @Json(name = "last_seen_at") val lastSeenAt: String? = null
)

data class SupabasePresencePatchDto(
  @Json(name = "last_seen_at") val lastSeenAt: String,
  @Json(name = "live_pulse") val livePulse: String? = null
)

data class SupabaseConversationDto(
  val id: String? = null,
  @Json(name = "created_by") val createdBy: String? = null,
  @Json(name = "is_group") val isGroup: Boolean = false,
  @Json(name = "group_name") val groupName: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "updated_at") val updatedAt: String? = null
)

data class SupabaseConversationInsertDto(
  @Json(name = "created_by") val createdBy: String? = null,
  @Json(name = "is_group") val isGroup: Boolean = false,
  @Json(name = "group_name") val groupName: String? = null
)

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

data class SupabaseMessageInsertDto(
  @Json(name = "conversation_id") val conversationId: String,
  @Json(name = "sender_id") val senderId: String,
  val content: String,
  @Json(name = "media_url") val mediaUrl: String? = null,
  @Json(name = "media_type") val mediaType: String? = "TEXT",
  @Json(name = "reply_to_message_id") val replyToMessageId: String? = null
)

data class SupabaseMessageReceiptDto(
  @Json(name = "message_id") val messageId: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "read_at") val readAt: String? = null
)

data class SupabaseCommentDto(
  val id: String? = null,
  @Json(name = "post_id") val postId: String,
  @Json(name = "author_id") val authorId: String,
  val content: String,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseCommentInsertDto(
  @Json(name = "post_id") val postId: String,
  @Json(name = "author_id") val authorId: String,
  val content: String
)

data class SupabaseReactionDto(
  @Json(name = "post_id") val postId: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "reaction_type") val reactionType: String = "PURE_ENERGY",
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseSavedPostDto(
  @Json(name = "user_id") val userId: String,
  @Json(name = "post_id") val postId: String,
  @Json(name = "created_at") val createdAt: String? = null
)

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

data class SupabaseNotificationInsertDto(
  @Json(name = "recipient_id") val recipientId: String,
  @Json(name = "actor_id") val actorId: String? = null,
  val type: String,
  val title: String,
  val message: String,
  @Json(name = "target_id") val targetId: String? = null,
  @Json(name = "is_read") val isRead: Boolean = false
)

data class SupabaseReportDto(
  val id: String? = null,
  @Json(name = "reporter_id") val reporterId: String,
  @Json(name = "target_type") val targetType: String,
  @Json(name = "target_id") val targetId: String,
  val reason: String,
  @Json(name = "admin_notes") val adminNotes: String? = null
)

data class SupabaseReportInsertDto(
  @Json(name = "reporter_id") val reporterId: String,
  @Json(name = "target_type") val targetType: String,
  @Json(name = "target_id") val targetId: String,
  val reason: String,
  @Json(name = "admin_notes") val adminNotes: String? = null
)

data class SupabaseBlockDto(
  @Json(name = "blocker_id") val blockerId: String,
  @Json(name = "blocked_id") val blockedId: String,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseRestrictionDto(
  @Json(name = "restrictor_id") val restrictorId: String,
  @Json(name = "restricted_id") val restrictedId: String,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseFollowDto(
  @Json(name = "follower_id") val followerId: String,
  @Json(name = "followed_id") val followedId: String,
  val status: String? = null,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseVybeMatchDto(
  @Json(name = "user_id") val userId: String,
  @Json(name = "matched_user_id") val matchedUserId: String,
  @Json(name = "created_at") val createdAt: String? = null
)

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

data class SupabaseStoryInsertDto(
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
  @Json(name = "expires_at") val expiresAt: String
)

data class SupabaseStoryLegacyInsertDto(
  @Json(name = "author_id") val authorId: String,
  @Json(name = "media_url") val mediaUrl: String,
  @Json(name = "media_type") val mediaType: String = "PHOTO",
  val caption: String? = "",
  @Json(name = "expires_at") val expiresAt: String
)

data class SupabaseStoryViewDto(
  @Json(name = "story_id") val storyId: String,
  @Json(name = "viewer_id") val viewerId: String,
  @Json(name = "viewed_at") val viewedAt: String? = null
)

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

/**
 * Omits `id` so PostgreSQL `DEFAULT gen_random_uuid()` assigns the primary key cleanly without `null` PK errors.
 */
data class SupabasePostInsertDto(
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

data class SupabasePostLegacyInsertDto(
  @Json(name = "author_id") val authorId: String,
  val caption: String? = "",
  @Json(name = "media_type") val mediaType: String? = "TEXT",
  @Json(name = "mood_zone") val moodZone: String? = null,
  @Json(name = "is_unfiltered") val isUnfiltered: Boolean? = false,
  @Json(name = "is_vybes_reel") val isVybesReel: Boolean? = false,
  @Json(name = "views_count") val viewsCount: Int? = 0
)

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

data class SupabasePostMediaInsertDto(
  @Json(name = "post_id") val postId: String,
  @Json(name = "media_url") val mediaUrl: String,
  @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
  @Json(name = "media_type") val mediaType: String = "IMAGE",
  @Json(name = "display_order") val displayOrder: Int = 0,
  @Json(name = "aspect_ratio") val aspectRatio: Double? = null,
  @Json(name = "storage_path") val storagePath: String? = null
)

data class SupabasePostMediaLegacyDto(
  @Json(name = "post_id") val postId: String,
  @Json(name = "media_url") val mediaUrl: String,
  @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
  @Json(name = "media_type") val mediaType: String = "IMAGE",
  @Json(name = "display_order") val displayOrder: Int = 0
)

// --- PERSISTENT LIVE ROOMS DTOs ---
data class SupabaseRoomDto(
  val id: String,
  @Json(name = "room_code") val roomCode: String? = null,
  @Json(name = "host_id") val hostId: String,
  val title: String,
  val description: String? = null,
  val category: String? = null,
  val mode: String? = "VOICE_AND_TEXT",
  @Json(name = "duration_minutes") val durationMinutes: Int? = 45,
  @Json(name = "max_participants") val maxParticipants: Int? = 25,
  @Json(name = "is_private") val isPrivate: Boolean? = false,
  @Json(name = "is_active") val isActive: Boolean? = true,
  @Json(name = "joiner_message") val joinerMessage: String? = null,
  @Json(name = "scheduled_time") val scheduledTime: String? = "Live Now",
  @Json(name = "expires_at") val expiresAt: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseRoomInsertDto(
  @Json(name = "room_code") val roomCode: String,
  @Json(name = "host_id") val hostId: String,
  val title: String,
  val description: String,
  val category: String,
  val mode: String,
  @Json(name = "duration_minutes") val durationMinutes: Int,
  @Json(name = "max_participants") val maxParticipants: Int,
  @Json(name = "is_private") val isPrivate: Boolean = false,
  @Json(name = "is_active") val isActive: Boolean = true,
  @Json(name = "joiner_message") val joinerMessage: String? = null,
  @Json(name = "scheduled_time") val scheduledTime: String? = "Live Now",
  @Json(name = "expires_at") val expiresAt: String
)

data class SupabaseRoomLegacyInsertDto(
  @Json(name = "host_id") val hostId: String,
  val title: String,
  val description: String,
  val category: String,
  @Json(name = "max_participants") val maxParticipants: Int,
  @Json(name = "is_private") val isPrivate: Boolean = false,
  @Json(name = "is_active") val isActive: Boolean = true
)

data class SupabaseRoomPatchDto(
  val title: String? = null,
  val description: String? = null,
  val category: String? = null,
  val mode: String? = null,
  @Json(name = "duration_minutes") val durationMinutes: Int? = null,
  @Json(name = "max_participants") val maxParticipants: Int? = null,
  @Json(name = "is_active") val isActive: Boolean? = null
)

data class SupabaseRoomMemberDto(
  @Json(name = "room_id") val roomId: String,
  @Json(name = "user_id") val userId: String,
  val role: String = "LISTENER",
  @Json(name = "joined_at") val joinedAt: String? = null
)

data class SupabaseRoomMessageDto(
  val id: String? = null,
  @Json(name = "room_id") val roomId: String,
  @Json(name = "sender_id") val senderId: String,
  val content: String,
  @Json(name = "sender_name") val senderName: String? = null,
  @Json(name = "is_anonymous") val isAnonymous: Boolean? = false,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseRoomMessageInsertDto(
  @Json(name = "room_id") val roomId: String,
  @Json(name = "sender_id") val senderId: String,
  val content: String,
  @Json(name = "sender_name") val senderName: String? = null,
  @Json(name = "is_anonymous") val isAnonymous: Boolean = false
)

data class SupabaseRoomMessageLegacyInsertDto(
  @Json(name = "room_id") val roomId: String,
  @Json(name = "sender_id") val senderId: String,
  val content: String
)

// --- WEBRTC VOICE & VIDEO CALL SIGNALING DTOs ---
data class SupabaseCallSessionDto(
  val id: String,
  @Json(name = "caller_id") val callerId: String,
  @Json(name = "callee_id") val calleeId: String,
  @Json(name = "conversation_id") val conversationId: String? = null,
  @Json(name = "is_video") val isVideo: Boolean = false,
  val status: String = "RINGING",
  @Json(name = "offer_sdp") val offerSdp: String? = null,
  @Json(name = "answer_sdp") val answerSdp: String? = null,
  @Json(name = "started_at") val startedAt: String? = null,
  @Json(name = "ended_at") val endedAt: String? = null,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCallSessionInsertDto(
  @Json(name = "caller_id") val callerId: String,
  @Json(name = "callee_id") val calleeId: String,
  @Json(name = "conversation_id") val conversationId: String? = null,
  @Json(name = "is_video") val isVideo: Boolean,
  val status: String = "RINGING",
  @Json(name = "offer_sdp") val offerSdp: String? = null
)

data class SupabaseCallSessionPatchDto(
  val status: String? = null,
  @Json(name = "answer_sdp") val answerSdp: String? = null,
  @Json(name = "started_at") val startedAt: String? = null,
  @Json(name = "ended_at") val endedAt: String? = null
)

data class SupabaseCallSignalDto(
  val id: String? = null,
  @Json(name = "call_id") val callId: String,
  @Json(name = "sender_id") val senderId: String,
  @Json(name = "signal_type") val signalType: String,
  val payload: Map<String, Any?>? = null,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCallSignalInsertDto(
  @Json(name = "call_id") val callId: String,
  @Json(name = "sender_id") val senderId: String,
  @Json(name = "signal_type") val signalType: String,
  val payload: Map<String, String> = emptyMap()
)

// --- RPC DTOs ---
data class SupabaseRpcIncrementViewRequest(
  @Json(name = "p_post_id") val postId: String,
  @Json(name = "p_viewer_id") val viewerId: String? = null
)

data class SupabaseRpcPeerConversationRequest(
  @Json(name = "p_peer_id") val peerId: String,
  @Json(name = "p_other_user_id") val otherUserId: String? = peerId
)

data class SupabaseRpcResolveIdentifierRequest(
  @Json(name = "p_identifier") val identifier: String
)

data class SupabaseResolvedIdentifierDto(
  val found: Boolean = false,
  val mode: String? = null,
  val email: String? = null,
  val phone: String? = null
)

data class SupabaseRpcCheckUsernameRequest(
  @Json(name = "p_username") val username: String
)

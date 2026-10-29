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
  @Json(name = "is_verified_public_figure") val isVerifiedPublicFigure: Boolean? = false,
  @Json(name = "is_verified_business") val isVerifiedBusiness: Boolean? = false,
  @Json(name = "is_worker") val isWorker: Boolean? = false,
  @Json(name = "worker_badge_mandatory") val workerBadgeMandatory: Boolean? = false,
  @Json(name = "vip_expires_at") val vipExpiresAt: String? = null,
  @Json(name = "badge_approval_status") val badgeApprovalStatus: String? = "ACCEPTED",
  @Json(name = "badge_display_status") val badgeDisplayStatus: String? = "ACCEPTED",
  @Json(name = "badge_type") val badgeType: String? = null,
  @Json(name = "is_deactivated") val isDeactivated: Boolean? = false,
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
  @Json(name = "last_seen_at") val lastSeenAt: String? = null,
  @Json(name = "badge_display_status") val badgeDisplayStatus: String? = null
)

data class SupabasePresencePatchDto(
  @Json(name = "last_seen_at") val lastSeenAt: String?,
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
  @Json(name = "reaction_type") val reactionType: String = "LOVE",
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
  @Json(name = "event_key") val eventKey: String? = null,
  @Json(name = "is_read") val isRead: Boolean? = false,
  @Json(name = "created_at") val createdAt: String? = null,
  val actor: SupabaseProfileDto? = null
)

data class SupabaseRpcDeleteMessageRequest(
  @Json(name = "p_message_id") val messageId: String
)

data class SupabaseNotificationInsertDto(
  @Json(name = "recipient_id") val recipientId: String,
  @Json(name = "actor_id") val actorId: String? = null,
  val type: String,
  val title: String,
  val message: String,
  @Json(name = "target_id") val targetId: String? = null,
  @Json(name = "event_key") val eventKey: String? = null,
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
  @Json(name = "media_type") val mediaType: String? = null,
  @Json(name = "story_type") val storyType: String? = null,
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
) {
  val resolvedMediaType: String
    get() = (mediaType ?: storyType ?: "PHOTO").uppercase()
}

data class SupabaseStoryInsertDto(
  @Json(name = "author_id") val authorId: String,
  @Json(name = "media_url") val mediaUrl: String,
  @Json(name = "media_type") val mediaType: String = "PHOTO",
  @Json(name = "story_type") val storyType: String = mediaType,
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
  @Json(name = "story_type") val storyType: String = "PHOTO",
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
  @Json(name = "love_count") val loveCount: Int? = 0,
  @Json(name = "flame_count") val flameCount: Int? = 0,
  @Json(name = "laugh_count") val laughCount: Int? = 0,
  @Json(name = "wow_count") val wowCount: Int? = 0,
  @Json(name = "sad_count") val sadCount: Int? = 0,
  @Json(name = "pulse_count") val pulseCount: Int? = 0,
  @Json(name = "spark_count") val sparkCount: Int? = 0,
  @Json(name = "wave_count") val waveCount: Int? = 0,
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

// --- LIVE ZONE DTOs ---
data class SupabaseLiveStreamDto(
  val id: String,
  @Json(name = "host_id") val hostId: String,
  val title: String,
  val category: String? = "Live Vibe",
  val visibility: String? = "PUBLIC",
  @Json(name = "is_live") val isLive: Boolean = true,
  @Json(name = "viewer_count") val viewerCount: Int = 1,
  @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
  @Json(name = "latest_frame_base64") val latestFrameBase64: String? = null,
  @Json(name = "started_at") val startedAt: String? = null,
  @Json(name = "ended_at") val endedAt: String? = null,
  @Json(name = "last_heartbeat_at") val lastHeartbeatAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseLiveStreamInsertDto(
  @Json(name = "host_id") val hostId: String,
  val title: String,
  val category: String = "Live Vibe",
  val visibility: String = "PUBLIC",
  @Json(name = "is_live") val isLive: Boolean = true,
  @Json(name = "viewer_count") val viewerCount: Int = 1
)

data class SupabaseLiveStreamLegacyInsertDto(
  @Json(name = "host_id") val hostId: String,
  val title: String,
  val visibility: String = "PUBLIC",
  @Json(name = "is_live") val isLive: Boolean = true,
  @Json(name = "viewer_count") val viewerCount: Int = 1
)

data class SupabaseLiveStreamPatchDto(
  @Json(name = "is_live") val isLive: Boolean? = null,
  @Json(name = "viewer_count") val viewerCount: Int? = null,
  @Json(name = "latest_frame_base64") val latestFrameBase64: String? = null,
  @Json(name = "last_heartbeat_at") val lastHeartbeatAt: String? = null,
  @Json(name = "ended_at") val endedAt: String? = null
)

data class SupabaseLiveStreamCommentDto(
  val id: String? = null,
  @Json(name = "stream_id") val streamId: String,
  @Json(name = "author_id") val authorId: String,
  @Json(name = "author_name") val authorName: String? = null,
  val content: String,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseLiveStreamCommentInsertDto(
  @Json(name = "stream_id") val streamId: String,
  @Json(name = "author_id") val authorId: String,
  @Json(name = "author_name") val authorName: String,
  val content: String
)

// --- EXPLORE ZONE DTOs ---
data class SupabaseAudiencePoolOptionDto(
  val id: Int? = null,
  @Json(name = "pool_id") val poolId: String,
  @Json(name = "option_text") val optionText: String,
  @Json(name = "votes_count") val votesCount: Int = 0
)

data class SupabaseAudiencePoolOptionInsertDto(
  @Json(name = "pool_id") val poolId: String,
  @Json(name = "option_text") val optionText: String,
  @Json(name = "votes_count") val votesCount: Int = 0
)

data class SupabaseAudiencePoolDto(
  val id: String,
  @Json(name = "creator_id") val creatorId: String,
  val title: String,
  val category: String? = "Community",
  @Json(name = "closes_at") val closesAt: String? = null,
  @Json(name = "total_votes") val totalVotes: Int = 0,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "audience_pool_options") val options: List<SupabaseAudiencePoolOptionDto>? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseAudiencePoolInsertDto(
  @Json(name = "creator_id") val creatorId: String,
  val title: String,
  val category: String,
  @Json(name = "closes_at") val closesAt: String
)

data class SupabaseAudiencePoolVoteDto(
  @Json(name = "pool_id") val poolId: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "option_id") val optionId: Int
)

data class SupabaseDoubleTakeAnswerDto(
  val id: String? = null,
  @Json(name = "prompt_id") val promptId: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "answer_text") val answerText: String,
  @Json(name = "is_connected") val isConnected: Boolean = false,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseDoubleTakeAnswerInsertDto(
  @Json(name = "prompt_id") val promptId: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "answer_text") val answerText: String
)

data class SupabaseDoubleTakeDto(
  val id: String,
  @Json(name = "creator_id") val creatorId: String,
  @Json(name = "partner_id") val partnerId: String? = null,
  @Json(name = "prompt_question") val promptQuestion: String,
  val category: String? = "Perspective",
  @Json(name = "option_a") val optionA: String? = "Yes",
  @Json(name = "option_b") val optionB: String? = "No",
  @Json(name = "creator_answer") val creatorAnswer: String? = null,
  @Json(name = "partner_answer") val partnerAnswer: String? = null,
  @Json(name = "is_creator_submitted") val isCreatorSubmitted: Boolean = false,
  @Json(name = "is_partner_submitted") val isPartnerSubmitted: Boolean = false,
  @Json(name = "is_revealed") val isRevealed: Boolean = false,
  @Json(name = "is_public") val isPublic: Boolean? = true,
  @Json(name = "is_creator_accepted") val isCreatorAccepted: Boolean = false,
  @Json(name = "is_chat_unlocked") val isChatUnlocked: Boolean = false,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "double_take_answers") val publicAnswers: List<SupabaseDoubleTakeAnswerDto>? = null
)

data class SupabaseDoubleTakeInsertDto(
  @Json(name = "creator_id") val creatorId: String,
  @Json(name = "partner_id") val partnerId: String? = null,
  @Json(name = "prompt_question") val promptQuestion: String,
  val category: String,
  @Json(name = "option_a") val optionA: String,
  @Json(name = "option_b") val optionB: String,
  @Json(name = "is_public") val isPublic: Boolean = true
)

data class SupabaseDoubleTakeLegacyInsertDto(
  @Json(name = "creator_id") val creatorId: String,
  @Json(name = "partner_id") val partnerId: String,
  @Json(name = "prompt_question") val promptQuestion: String,
  val category: String
)

data class SupabaseDoubleTakePatchDto(
  @Json(name = "creator_answer") val creatorAnswer: String? = null,
  @Json(name = "partner_answer") val partnerAnswer: String? = null,
  @Json(name = "is_creator_submitted") val isCreatorSubmitted: Boolean? = null,
  @Json(name = "is_partner_submitted") val isPartnerSubmitted: Boolean? = null,
  @Json(name = "is_revealed") val isRevealed: Boolean? = null,
  @Json(name = "is_creator_accepted") val isCreatorAccepted: Boolean? = null,
  @Json(name = "is_chat_unlocked") val isChatUnlocked: Boolean? = null
)

data class SupabaseTimeCapsuleDto(
  val id: String,
  @Json(name = "creator_id") val creatorId: String,
  val title: String,
  @Json(name = "recipient_summary") val recipientSummary: String,
  @Json(name = "sealed_message") val sealedMessage: String,
  @Json(name = "media_url") val mediaUrl: String? = null,
  @Json(name = "unlock_date") val unlockDate: String,
  @Json(name = "is_unlocked") val isUnlocked: Boolean = false,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseTimeCapsuleInsertDto(
  @Json(name = "creator_id") val creatorId: String,
  val title: String,
  @Json(name = "recipient_summary") val recipientSummary: String,
  @Json(name = "sealed_message") val sealedMessage: String,
  @Json(name = "unlock_date") val unlockDate: String,
  @Json(name = "is_unlocked") val isUnlocked: Boolean = false
)

data class SupabaseCrossedPathDto(
  val id: String,
  @Json(name = "creator_id") val creatorId: String,
  @Json(name = "neighborhood_or_venue") val neighborhoodOrVenue: String? = null,
  val description: String,
  @Json(name = "time_window") val timeWindow: String? = null,
  @Json(name = "encounter_type") val encounterType: String? = "MISSED_CONNECTION",
  val title: String? = null,
  @Json(name = "broad_location") val broadLocation: String? = null,
  @Json(name = "venue_name") val venueName: String? = null,
  @Json(name = "photo_url") val photoUrl: String? = null,
  @Json(name = "expires_at") val expiresAt: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseCrossedPathInsertDto(
  @Json(name = "creator_id") val creatorId: String,
  @Json(name = "neighborhood_or_venue") val neighborhoodOrVenue: String,
  val description: String,
  @Json(name = "time_window") val timeWindow: String = "Today",
  @Json(name = "encounter_type") val encounterType: String = "MISSED_CONNECTION",
  val title: String? = null,
  @Json(name = "broad_location") val broadLocation: String? = null,
  @Json(name = "venue_name") val venueName: String? = null,
  @Json(name = "photo_url") val photoUrl: String? = null
)

data class SupabaseCrossedPathLegacyInsertDto(
  @Json(name = "creator_id") val creatorId: String,
  @Json(name = "neighborhood_or_venue") val neighborhoodOrVenue: String,
  val description: String,
  @Json(name = "time_window") val timeWindow: String = "Today",
  @Json(name = "photo_url") val photoUrl: String? = null
)

data class SupabaseCrossedPathRequestDto(
  val id: String? = null,
  @Json(name = "encounter_id") val encounterId: String,
  @Json(name = "sender_id") val senderId: String,
  val status: String = "PENDING"
)

data class SupabaseBusinessReviewDto(
  val id: String? = null,
  @Json(name = "business_id") val businessId: String,
  @Json(name = "author_id") val authorId: String,
  val rating: Int,
  @Json(name = "review_text") val reviewText: String,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseBusinessReviewInsertDto(
  @Json(name = "business_id") val businessId: String,
  @Json(name = "author_id") val authorId: String,
  val rating: Int,
  @Json(name = "review_text") val reviewText: String
)

data class SupabaseBusinessProfileDto(
  val id: String,
  @Json(name = "owner_id") val ownerId: String,
  @Json(name = "business_name") val businessName: String,
  val category: String,
  @Json(name = "gstin_or_registration_number") val gstNumber: String? = null,
  val description: String? = null,
  @Json(name = "logo_url") val logoUrl: String? = null,
  @Json(name = "cover_image_url") val coverImageUrl: String? = null,
  val city: String? = null,
  @Json(name = "full_address") val fullAddress: String? = null,
  @Json(name = "verification_status") val verificationStatus: String? = "PENDING",
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "business_reviews") val reviews: List<SupabaseBusinessReviewDto>? = null
)

data class SupabaseBusinessProfileInsertDto(
  @Json(name = "owner_id") val ownerId: String,
  @Json(name = "business_name") val businessName: String,
  val category: String,
  @Json(name = "gstin_or_registration_number") val gstNumber: String,
  val description: String,
  val city: String,
  @Json(name = "full_address") val fullAddress: String
)

// --- COMMUNITY DTOs ---
data class SupabaseCommunityDto(
  val id: String,
  @Json(name = "creator_id") val creatorId: String,
  val name: String,
  val description: String? = null,
  val category: String? = "General",
  @Json(name = "avatar_url") val avatarUrl: String? = null,
  @Json(name = "banner_url") val bannerUrl: String? = null,
  @Json(name = "is_private") val isPrivate: Boolean = false,
  @Json(name = "members_count") val membersCount: Int = 1,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCommunityInsertDto(
  @Json(name = "creator_id") val creatorId: String,
  val name: String,
  val description: String = "",
  val category: String = "General",
  @Json(name = "avatar_url") val avatarUrl: String? = null,
  @Json(name = "banner_url") val bannerUrl: String? = null,
  @Json(name = "is_private") val isPrivate: Boolean = false
)

data class SupabaseCommunityMemberDto(
  @Json(name = "community_id") val communityId: String,
  @Json(name = "user_id") val userId: String,
  val role: String? = "MEMBER",
  val status: String? = "ACCEPTED",
  @Json(name = "joined_at") val joinedAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseCommunityMemberInsertDto(
  @Json(name = "community_id") val communityId: String,
  @Json(name = "user_id") val userId: String,
  val role: String = "MEMBER",
  val status: String = "ACCEPTED"
)

data class SupabaseCommunityMemberPatchDto(
  val role: String? = null,
  val status: String? = null
)

data class SupabaseCommunityPostDto(
  val id: String,
  @Json(name = "community_id") val communityId: String,
  @Json(name = "author_id") val authorId: String,
  val title: String,
  val content: String = "",
  @Json(name = "media_url") val mediaUrl: String? = null,
  @Json(name = "likes_count") val likesCount: Int = 0,
  @Json(name = "comments_count") val commentsCount: Int = 0,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "updated_at") val updatedAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseCommunityPostInsertDto(
  @Json(name = "community_id") val communityId: String,
  @Json(name = "author_id") val authorId: String,
  val title: String,
  val content: String = "",
  @Json(name = "media_url") val mediaUrl: String? = null
)

data class SupabaseCommunityPostPatchDto(
  val title: String,
  val content: String,
  @Json(name = "updated_at") val updatedAt: String
)

data class SupabaseCommunityPostLikeDto(
  @Json(name = "post_id") val postId: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCommunityCommentDto(
  val id: String,
  @Json(name = "post_id") val postId: String,
  @Json(name = "author_id") val authorId: String,
  val content: String,
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseCommunityCommentInsertDto(
  @Json(name = "post_id") val postId: String,
  @Json(name = "author_id") val authorId: String,
  val content: String
)

// --- FCM DEVICE TOKENS & PUSH DISPATCH ---
data class SupabaseDeviceTokenDto(
  @Json(name = "user_id") val userId: String,
  @Json(name = "fcm_token") val fcmToken: String,
  @Json(name = "device_model") val deviceModel: String = "Android Phone",
  val platform: String = "android",
  @Json(name = "is_active") val isActive: Boolean = true,
  @Json(name = "last_seen_at") val lastSeenAt: String? = null,
  @Json(name = "updated_at") val updatedAt: String? = null
)

data class SupabaseDeviceTokenPatchDto(
  @Json(name = "is_active") val isActive: Boolean,
  @Json(name = "updated_at") val updatedAt: String
)

// --- VERIFICATION REQUESTS & OFFICIAL VYBE BADGE RPCs ---
data class SupabaseVerificationRequestDto(
  val id: String,
  @Json(name = "user_id") val userId: String,
  @Json(name = "request_type") val requestType: String? = "VERIFIED",
  @Json(name = "full_name") val fullName: String? = "",
  val category: String? = "",
  @Json(name = "identity_document_url") val identityDocumentUrl: String? = null,
  val statement: String? = "",
  val status: String? = "PENDING",
  @Json(name = "rejection_reason") val rejectionReason: String? = null,
  @Json(name = "admin_notes") val adminNotes: String? = null,
  @Json(name = "vip_expires_at") val vipExpiresAt: String? = null,
  @Json(name = "user_response_status") val userResponseStatus: String? = "NONE",
  @Json(name = "created_at") val createdAt: String? = null,
  val profiles: SupabaseProfileDto? = null
)

data class SupabaseVerificationRequestInsertDto(
  @Json(name = "user_id") val userId: String,
  @Json(name = "request_type") val requestType: String,
  @Json(name = "full_name") val fullName: String,
  val category: String,
  @Json(name = "identity_document_url") val identityDocumentUrl: String? = null,
  val statement: String = "",
  val status: String = "PENDING"
)

data class SupabaseRpcAdminAssignBadgeRequest(
  @Json(name = "p_target_user_id") val targetUserId: String,
  @Json(name = "p_badge_category") val badgeCategory: String,
  @Json(name = "p_is_grant") val isGrant: Boolean = true,
  @Json(name = "p_admin_id") val adminId: String? = null,
  @Json(name = "p_vip_expires_at") val vipExpiresAt: String? = null,
  @Json(name = "p_reason") val reason: String? = null,
  @Json(name = "p_require_acceptance") val requireAcceptance: Boolean = false,
  @Json(name = "p_worker_mandatory") val workerMandatory: Boolean = false
)

data class SupabaseRpcAdminReviewVerificationRequest(
  @Json(name = "p_request_id") val requestId: String,
  @Json(name = "p_decision") val decision: String, // APPROVED, REJECTED, MORE_INFO_REQUIRED
  @Json(name = "p_notes") val notes: String? = null,
  @Json(name = "p_admin_id") val adminId: String? = null,
  @Json(name = "p_vip_expires_at") val vipExpiresAt: String? = null
)

data class SupabaseRpcUserRespondBadgeOfferRequest(
  @Json(name = "p_action") val action: String // ACCEPT, DECLINE, HIDE, SHOW
)

data class SupabaseAuditLogDto(
  val id: String,
  @Json(name = "admin_id") val adminId: String? = null,
  @Json(name = "admin_email") val adminEmail: String? = null,
  val action: String? = "",
  @Json(name = "target_type") val targetType: String? = "",
  @Json(name = "target_id") val targetId: String? = "",
  val details: Any? = null,
  @Json(name = "created_at") val createdAt: String? = null
)



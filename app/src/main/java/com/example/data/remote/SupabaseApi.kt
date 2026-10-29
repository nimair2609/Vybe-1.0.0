package com.example.data.remote

import retrofit2.Response
import retrofit2.http.*

interface SupabaseAuthApi {
  @POST("signup")
  suspend fun signUp(
    @Header("apikey") apiKey: String,
    @Query("redirect_to") redirectTo: String = "vybe://auth-callback",
    @Body request: SupabaseSignUpRequest
  ): Response<SupabaseAuthResponse>

  @POST("token")
  suspend fun signIn(
    @Header("apikey") apiKey: String,
    @Query("grant_type") grantType: String = "password",
    @Body request: SupabaseSignInRequest
  ): Response<SupabaseAuthResponse>

  @POST("token")
  suspend fun signInWithPhone(
    @Header("apikey") apiKey: String,
    @Query("grant_type") grantType: String = "password",
    @Body request: SupabasePhoneSignInRequest
  ): Response<SupabaseAuthResponse>

  @POST("token")
  suspend fun refreshSession(
    @Header("apikey") apiKey: String,
    @Query("grant_type") grantType: String = "refresh_token",
    @Body request: SupabaseRefreshTokenRequest
  ): Response<SupabaseAuthResponse>

  @POST("otp")
  suspend fun sendOtp(
    @Header("apikey") apiKey: String,
    @Body request: SupabaseOtpSendRequest
  ): Response<Unit>

  @POST("resend")
  suspend fun resendOtp(
    @Header("apikey") apiKey: String,
    @Body request: SupabaseResendOtpRequest
  ): Response<Unit>

  @POST("verify")
  suspend fun verifyOtp(
    @Header("apikey") apiKey: String,
    @Body request: SupabaseVerifyOtpRequest
  ): Response<SupabaseAuthResponse>

  @POST("recover")
  suspend fun recoverPassword(
    @Header("apikey") apiKey: String,
    @Query("redirect_to") redirectTo: String = "vybe://reset-password",
    @Body request: SupabaseRecoverPasswordRequest
  ): Response<Unit>

  @PUT("user")
  suspend fun updateUser(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Body request: SupabaseUpdateUserRequest
  ): Response<SupabaseUserDto>

  @GET("user")
  suspend fun getUser(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String
  ): Response<SupabaseUserDto>

  @POST("logout")
  suspend fun logout(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String
  ): Response<Unit>
}

interface SupabaseRestApi {
  // --- PROFILES & SECURE IDENTIFIER RESOLUTION ---
  @POST("rpc/resolve_login_identifier")
  suspend fun resolveLoginIdentifier(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcResolveIdentifierRequest
  ): Response<SupabaseResolvedIdentifierDto>

  @POST("rpc/check_username_available")
  suspend fun checkUsernameAvailableRpc(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcCheckUsernameRequest
  ): Response<Boolean>

  @GET("profiles")
  suspend fun getProfileById(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Query("select") select: String = "*"
  ): Response<List<SupabaseProfileDto>>

  @GET("profiles")
  suspend fun getProfileByEmail(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("email") emailFilter: String,
    @Query("select") select: String = "*"
  ): Response<List<SupabaseProfileDto>>

  @GET("profiles")
  suspend fun getProfileByUsername(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("username") usernameFilter: String,
    @Query("select") select: String = "*"
  ): Response<List<SupabaseProfileDto>>

  @GET("profiles")
  suspend fun getProfileByPhone(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("phone") phoneFilter: String,
    @Query("select") select: String = "*"
  ): Response<List<SupabaseProfileDto>>

  @GET("profiles")
  suspend fun getProfiles(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("select") select: String = "*",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 100
  ): Response<List<SupabaseProfileDto>>

  @GET("profiles")
  suspend fun searchProfiles(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("or") orFilter: String,
    @Query("select") select: String = "*",
    @Query("limit") limit: Int = 40
  ): Response<List<SupabaseProfileDto>>

  @POST("profiles")
  suspend fun upsertProfile(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body profile: SupabaseProfileDto
  ): Response<List<SupabaseProfileDto>>

  @PATCH("profiles")
  suspend fun patchProfile(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Query("id") idFilter: String,
    @Body patch: SupabaseProfilePatchDto
  ): Response<List<SupabaseProfileDto>>

  @PATCH("profiles")
  suspend fun patchPresence(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Body patch: SupabasePresencePatchDto
  ): Response<Unit>

  // --- CONVERSATIONS, MESSAGING & READ RECEIPTS ---
  @GET("conversation_members")
  suspend fun getMyConversationMemberships(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String,
    @Query("select") select: String = "*,conversations(*)"
  ): Response<List<SupabaseConversationMemberDto>>

  @GET("conversation_members")
  suspend fun getMembersForConversations(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("conversation_id") conversationIdsInFilter: String,
    @Query("select") select: String = "*,profiles(*)"
  ): Response<List<SupabaseConversationMemberDto>>

  @POST("conversations")
  suspend fun insertConversation(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body conversation: SupabaseConversationInsertDto
  ): Response<List<SupabaseConversationDto>>

  @POST("conversation_members")
  suspend fun insertConversationMembers(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body members: List<SupabaseConversationMemberDto>
  ): Response<List<SupabaseConversationMemberDto>>

  @POST("rpc/get_or_create_direct_conversation")
  suspend fun getOrCreateDirectConversation(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcPeerConversationRequest
  ): Response<String>

  @GET("messages")
  suspend fun getMessages(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("conversation_id") conversationIdFilter: String,
    @Query("order") order: String = "created_at.asc",
    @Query("limit") limit: Int = 80
  ): Response<List<SupabaseMessageDto>>

  @POST("messages")
  suspend fun insertMessage(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body message: SupabaseMessageInsertDto
  ): Response<List<SupabaseMessageDto>>

  @DELETE("messages")
  suspend fun deleteMessage(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<Unit>

  @POST("rpc/delete_message_for_everyone")
  suspend fun deleteMessageForEveryoneRpc(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcDeleteMessageRequest
  ): Response<Boolean>

  @PATCH("messages")
  suspend fun patchMessageDeleted(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Body body: Map<String, @JvmSuppressWildcards Any?>
  ): Response<Unit>

  @GET("message_receipts")
  suspend fun getMessageReceipts(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String,
    @Query("message_id") messageIdsInFilter: String
  ): Response<List<SupabaseMessageReceiptDto>>

  @POST("message_receipts")
  suspend fun upsertMessageReceipts(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates",
    @Body receipts: List<SupabaseMessageReceiptDto>
  ): Response<Unit>

  // --- COMMENTS ---
  @GET("comments")
  suspend fun getComments(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("post_id") postIdFilter: String,
    @Query("select") select: String = "*,profiles:author_id(*)",
    @Query("order") order: String = "created_at.asc",
    @Query("limit") limit: Int = 100
  ): Response<List<SupabaseCommentDto>>

  @POST("comments")
  suspend fun insertComment(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body comment: SupabaseCommentInsertDto
  ): Response<List<SupabaseCommentDto>>

  // --- POST REACTIONS & SAVES ---
  @GET("post_reactions")
  suspend fun getMyReactions(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String
  ): Response<List<SupabaseReactionDto>>

  @GET("post_reactions")
  suspend fun getReactionsForPosts(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("post_id") postIdsInFilter: String,
    @Query("limit") limit: Int = 2000
  ): Response<List<SupabaseReactionDto>>

  @POST("post_reactions")
  suspend fun upsertReaction(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Query("on_conflict") onConflict: String = "post_id,user_id",
    @Body reaction: SupabaseReactionDto
  ): Response<List<SupabaseReactionDto>>

  @DELETE("post_reactions")
  suspend fun deleteReaction(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("post_id") postIdFilter: String,
    @Query("user_id") userIdFilter: String
  ): Response<Unit>

  @GET("saved_posts")
  suspend fun getMySavedPosts(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String
  ): Response<List<SupabaseSavedPostDto>>

  @POST("saved_posts")
  suspend fun insertSavedPost(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body body: SupabaseSavedPostDto
  ): Response<List<SupabaseSavedPostDto>>

  @DELETE("saved_posts")
  suspend fun deleteSavedPost(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String,
    @Query("post_id") postIdFilter: String
  ): Response<Unit>

  // --- NOTIFICATIONS ---
  @GET("notifications")
  suspend fun getNotifications(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("recipient_id") recipientIdFilter: String,
    @Query("select") select: String = "*,actor:actor_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 50
  ): Response<List<SupabaseNotificationDto>>

  @GET("posts")
  suspend fun getPostsByIds(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idsInFilter: String,
    @Query("select") select: String = "*,profiles:author_id(*)"
  ): Response<List<SupabasePostDto>>

  @POST("notifications")
  suspend fun insertNotification(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body notification: SupabaseNotificationInsertDto
  ): Response<List<SupabaseNotificationDto>>

  @POST("notifications")
  suspend fun upsertNotificationByEventKey(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Query("on_conflict") onConflict: String = "recipient_id,event_key",
    @Body notification: SupabaseNotificationInsertDto
  ): Response<List<SupabaseNotificationDto>>

  @DELETE("notifications")
  suspend fun deleteNotification(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<Unit>

  @PATCH("notifications")
  suspend fun markNotificationsRead(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("recipient_id") recipientIdFilter: String,
    @Body body: Map<String, Boolean>
  ): Response<Unit>

  @PATCH("notifications")
  suspend fun markSingleNotificationRead(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Body body: Map<String, Boolean>
  ): Response<Unit>

  // --- FOLLOWS ---
  @GET("follows")
  suspend fun getFollowsByFollower(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("follower_id") followerIdFilter: String
  ): Response<List<SupabaseFollowDto>>

  @GET("follows")
  suspend fun getFollowsByFollowed(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("followed_id") followedIdFilter: String
  ): Response<List<SupabaseFollowDto>>

  @POST("follows")
  suspend fun insertFollow(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body body: SupabaseFollowDto
  ): Response<List<SupabaseFollowDto>>

  @PATCH("follows")
  suspend fun updateFollowStatus(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("follower_id") followerFilter: String,
    @Query("followed_id") followedFilter: String,
    @Body body: Map<String, String>
  ): Response<Unit>

  @DELETE("follows")
  suspend fun deleteFollow(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("follower_id") followerFilter: String,
    @Query("followed_id") followedFilter: String
  ): Response<Unit>

  // --- BLOCKS & RESTRICTIONS ---
  @GET("blocks")
  suspend fun getBlocks(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("blocker_id") blockerIdFilter: String
  ): Response<List<SupabaseBlockDto>>

  @POST("blocks")
  suspend fun insertBlock(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body body: SupabaseBlockDto
  ): Response<List<SupabaseBlockDto>>

  @DELETE("blocks")
  suspend fun deleteBlock(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("blocker_id") blockerFilter: String,
    @Query("blocked_id") blockedFilter: String
  ): Response<Unit>

  @GET("user_restrictions")
  suspend fun getRestrictions(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("restrictor_id") restrictorIdFilter: String
  ): Response<List<SupabaseRestrictionDto>>

  @POST("user_restrictions")
  suspend fun insertRestriction(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body body: SupabaseRestrictionDto
  ): Response<List<SupabaseRestrictionDto>>

  @DELETE("user_restrictions")
  suspend fun deleteRestriction(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("restrictor_id") restrictorFilter: String,
    @Query("restricted_id") restrictedFilter: String
  ): Response<Unit>

  // --- VYBE MATCHES ---
  @GET("vybe_matches")
  suspend fun getVybeMatches(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String
  ): Response<List<SupabaseVybeMatchDto>>

  @POST("vybe_matches")
  suspend fun insertVybeMatch(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body body: SupabaseVybeMatchDto
  ): Response<List<SupabaseVybeMatchDto>>

  @DELETE("vybe_matches")
  suspend fun deleteVybeMatch(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String,
    @Query("matched_user_id") matchedUserIdFilter: String
  ): Response<Unit>

  // --- STORIES & STORY VIEWS ---
  @GET("stories")
  suspend fun getActiveStories(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("expires_at") expiresAtFilter: String,
    @Query("select") select: String = "*,profiles:author_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 80
  ): Response<List<SupabaseStoryDto>>

  @POST("stories")
  suspend fun insertStory(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body story: SupabaseStoryInsertDto
  ): Response<List<SupabaseStoryDto>>

  @POST("stories")
  suspend fun insertStoryLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body story: SupabaseStoryLegacyInsertDto
  ): Response<List<SupabaseStoryDto>>

  @DELETE("stories")
  suspend fun deleteStory(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<Unit>

  @GET("story_views")
  suspend fun getMyStoryViews(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("viewer_id") viewerIdFilter: String
  ): Response<List<SupabaseStoryViewDto>>

  @POST("story_views")
  suspend fun upsertStoryView(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body view: SupabaseStoryViewDto
  ): Response<List<SupabaseStoryViewDto>>

  // --- REPORTS ---
  @POST("reports")
  suspend fun submitReport(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body body: SupabaseReportInsertDto
  ): Response<List<SupabaseReportDto>>

  // --- POSTS & POST MEDIA ---
  @GET("posts")
  suspend fun getPosts(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("select") select: String = "*,post_media(*),profiles:author_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("offset") offset: Int = 0,
    @Query("limit") limit: Int = 60
  ): Response<List<SupabasePostDto>>

  @GET("posts")
  suspend fun getPostByClientUploadId(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("client_upload_id") clientUploadFilter: String,
    @Query("select") select: String = "*,post_media(*),profiles:author_id(*)"
  ): Response<List<SupabasePostDto>>

  @POST("posts")
  suspend fun insertPost(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body post: SupabasePostInsertDto
  ): Response<List<SupabasePostDto>>

  @POST("posts")
  suspend fun insertPostLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body post: SupabasePostLegacyInsertDto
  ): Response<List<SupabasePostDto>>

  @PATCH("posts")
  suspend fun patchPost(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Body body: Map<String, Boolean>
  ): Response<Unit>

  @DELETE("posts")
  suspend fun deletePost(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<Unit>

  @POST("post_media")
  suspend fun insertPostMedia(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body media: SupabasePostMediaInsertDto
  ): Response<List<SupabasePostMediaDto>>

  @POST("post_media")
  suspend fun insertPostMediaLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body media: SupabasePostMediaLegacyDto
  ): Response<List<SupabasePostMediaDto>>

  @POST("rpc/increment_post_view")
  suspend fun incrementPostView(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcIncrementViewRequest
  ): Response<Int>

  // --- PERSISTENT LIVE ROOMS ---
  @GET("rooms")
  suspend fun getRooms(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("is_active") activeFilter: String = "eq.true",
    @Query("select") select: String = "*,profiles:host_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 50
  ): Response<List<SupabaseRoomDto>>

  @GET("rooms")
  suspend fun getRoomByCode(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("room_code") roomCodeFilter: String,
    @Query("select") select: String = "*,profiles:host_id(*)"
  ): Response<List<SupabaseRoomDto>>

  @GET("rooms")
  suspend fun getRoomById(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Query("select") select: String = "*,profiles:host_id(*)"
  ): Response<List<SupabaseRoomDto>>

  @POST("rooms")
  suspend fun insertRoom(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body room: SupabaseRoomInsertDto
  ): Response<List<SupabaseRoomDto>>

  @POST("rooms")
  suspend fun insertRoomLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body room: SupabaseRoomLegacyInsertDto
  ): Response<List<SupabaseRoomDto>>

  @PATCH("rooms")
  suspend fun patchRoom(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Query("id") idFilter: String,
    @Body patch: SupabaseRoomPatchDto
  ): Response<List<SupabaseRoomDto>>

  @DELETE("rooms")
  suspend fun deleteRoom(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<Unit>

  @GET("room_members")
  suspend fun getRoomMembers(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("room_id") roomIdFilter: String
  ): Response<List<SupabaseRoomMemberDto>>

  @POST("room_members")
  suspend fun upsertRoomMember(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body member: SupabaseRoomMemberDto
  ): Response<List<SupabaseRoomMemberDto>>

  @DELETE("room_members")
  suspend fun deleteRoomMember(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("room_id") roomIdFilter: String,
    @Query("user_id") userIdFilter: String
  ): Response<Unit>

  @GET("room_messages")
  suspend fun getRoomMessages(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("room_id") roomIdFilter: String,
    @Query("select") select: String = "*,profiles:sender_id(*)",
    @Query("order") order: String = "created_at.asc",
    @Query("limit") limit: Int = 80
  ): Response<List<SupabaseRoomMessageDto>>

  @POST("room_messages")
  suspend fun insertRoomMessage(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body message: SupabaseRoomMessageInsertDto
  ): Response<List<SupabaseRoomMessageDto>>

  @POST("room_messages")
  suspend fun insertRoomMessageLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body message: SupabaseRoomMessageLegacyInsertDto
  ): Response<List<SupabaseRoomMessageDto>>

  // --- WEBRTC CALL SESSIONS & SIGNALING ---
  @POST("call_sessions")
  suspend fun insertCallSession(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body session: SupabaseCallSessionInsertDto
  ): Response<List<SupabaseCallSessionDto>>

  @GET("call_sessions")
  suspend fun getIncomingCallSessions(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("callee_id") calleeIdFilter: String,
    @Query("status") statusFilter: String = "eq.RINGING",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 5
  ): Response<List<SupabaseCallSessionDto>>

  @GET("call_sessions")
  suspend fun getCallSessionById(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<List<SupabaseCallSessionDto>>

  @PATCH("call_sessions")
  suspend fun patchCallSession(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Query("id") idFilter: String,
    @Body patch: SupabaseCallSessionPatchDto
  ): Response<List<SupabaseCallSessionDto>>

  @POST("call_signals")
  suspend fun insertCallSignal(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body signal: SupabaseCallSignalInsertDto
  ): Response<Unit>

  @GET("call_signals")
  suspend fun getCallSignals(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("call_id") callIdFilter: String,
    @Query("created_at") createdAtFilter: String? = null,
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 30
  ): Response<List<SupabaseCallSignalDto>>

  @DELETE("call_signals")
  suspend fun deleteCallSignalsBySender(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("call_id") callIdFilter: String,
    @Query("sender_id") senderIdFilter: String,
    @Query("signal_type") signalTypeFilter: String = "eq.VIDEO_FRAME"
  ): Response<Unit>

  // --- LIVE ZONE (`live_streams` & `live_stream_comments`) ---
  @GET("live_streams")
  suspend fun getActiveLiveStreams(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("is_live") isLiveFilter: String = "eq.true",
    @Query("select") select: String = "*,profiles:host_id(*)",
    @Query("order") order: String = "started_at.desc",
    @Query("limit") limit: Int = 30
  ): Response<List<SupabaseLiveStreamDto>>

  @GET("live_streams")
  suspend fun getLiveStreamById(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Query("select") select: String = "*,profiles:host_id(*)"
  ): Response<List<SupabaseLiveStreamDto>>

  @POST("live_streams")
  suspend fun insertLiveStream(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body stream: SupabaseLiveStreamInsertDto
  ): Response<List<SupabaseLiveStreamDto>>

  @POST("live_streams")
  suspend fun insertLiveStreamLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body stream: SupabaseLiveStreamLegacyInsertDto
  ): Response<List<SupabaseLiveStreamDto>>

  @PATCH("live_streams")
  suspend fun patchLiveStream(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Body patch: SupabaseLiveStreamPatchDto
  ): Response<Unit>

  @GET("live_stream_comments")
  suspend fun getLiveStreamComments(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("stream_id") streamIdFilter: String,
    @Query("order") order: String = "created_at.asc",
    @Query("limit") limit: Int = 100
  ): Response<List<SupabaseLiveStreamCommentDto>>

  @POST("live_stream_comments")
  suspend fun insertLiveStreamComment(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body comment: SupabaseLiveStreamCommentInsertDto
  ): Response<List<SupabaseLiveStreamCommentDto>>

  // --- EXPLORE ZONE: AUDIENCE POLLS ("WHAT NEXT") ---
  @GET("audience_pools")
  suspend fun getAudiencePools(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("select") select: String = "*,audience_pool_options(*),profiles:creator_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 40
  ): Response<List<SupabaseAudiencePoolDto>>

  @POST("audience_pools")
  suspend fun insertAudiencePool(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body pool: SupabaseAudiencePoolInsertDto
  ): Response<List<SupabaseAudiencePoolDto>>

  @DELETE("audience_pools")
  suspend fun deleteAudiencePool(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<Unit>

  @POST("audience_pool_options")
  suspend fun insertAudiencePoolOptions(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body options: List<SupabaseAudiencePoolOptionInsertDto>
  ): Response<List<SupabaseAudiencePoolOptionDto>>

  @GET("audience_pool_votes")
  suspend fun getMyAudiencePoolVotes(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String
  ): Response<List<SupabaseAudiencePoolVoteDto>>

  @POST("audience_pool_votes")
  suspend fun upsertAudiencePoolVote(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates",
    @Body vote: SupabaseAudiencePoolVoteDto
  ): Response<Unit>

  // --- EXPLORE ZONE: DOUBLE TAKE ---
  @GET("double_takes")
  suspend fun getDoubleTakes(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("select") select: String = "*,double_take_answers(*,profiles:user_id(*))",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 40
  ): Response<List<SupabaseDoubleTakeDto>>

  @POST("double_takes")
  suspend fun insertDoubleTake(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body body: SupabaseDoubleTakeInsertDto
  ): Response<List<SupabaseDoubleTakeDto>>

  @POST("double_takes")
  suspend fun insertDoubleTakeLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body body: SupabaseDoubleTakeLegacyInsertDto
  ): Response<List<SupabaseDoubleTakeDto>>

  @PATCH("double_takes")
  suspend fun patchDoubleTake(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Body patch: SupabaseDoubleTakePatchDto
  ): Response<Unit>

  @DELETE("double_takes")
  suspend fun deleteDoubleTake(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<Unit>

  @POST("double_take_answers")
  suspend fun upsertDoubleTakeAnswer(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body answer: SupabaseDoubleTakeAnswerInsertDto
  ): Response<List<SupabaseDoubleTakeAnswerDto>>

  @PATCH("double_take_answers")
  suspend fun patchDoubleTakeAnswer(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Body patch: Map<String, Boolean>
  ): Response<Unit>

  // --- EXPLORE ZONE: TIME CAPSULES ---
  @GET("time_capsules")
  suspend fun getTimeCapsules(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("select") select: String = "*,profiles:creator_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 40
  ): Response<List<SupabaseTimeCapsuleDto>>

  @POST("time_capsules")
  suspend fun insertTimeCapsule(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body capsule: SupabaseTimeCapsuleInsertDto
  ): Response<List<SupabaseTimeCapsuleDto>>

  @PATCH("time_capsules")
  suspend fun patchTimeCapsule(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String,
    @Body patch: Map<String, Boolean>
  ): Response<Unit>

  // --- EXPLORE ZONE: CROSSED PATHS ---
  @GET("crossed_paths")
  suspend fun getCrossedPaths(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("select") select: String = "*,profiles:creator_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 40
  ): Response<List<SupabaseCrossedPathDto>>

  @POST("crossed_paths")
  suspend fun insertCrossedPath(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body encounter: SupabaseCrossedPathInsertDto
  ): Response<List<SupabaseCrossedPathDto>>

  @POST("crossed_paths")
  suspend fun insertCrossedPathLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body encounter: SupabaseCrossedPathLegacyInsertDto
  ): Response<List<SupabaseCrossedPathDto>>

  @GET("crossed_path_requests")
  suspend fun getMyCrossedPathRequests(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("sender_id") senderIdFilter: String
  ): Response<List<SupabaseCrossedPathRequestDto>>

  @POST("crossed_path_requests")
  suspend fun insertCrossedPathRequest(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseCrossedPathRequestDto
  ): Response<Unit>

  // --- EXPLORE ZONE: VERIFIED SHOPS & REVIEWS ---
  @GET("business_profiles")
  suspend fun getBusinessProfiles(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("select") select: String = "*,business_reviews(*,profiles:author_id(*))",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 40
  ): Response<List<SupabaseBusinessProfileDto>>

  @POST("business_profiles")
  suspend fun insertBusinessProfile(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body profile: SupabaseBusinessProfileInsertDto
  ): Response<List<SupabaseBusinessProfileDto>>

  @POST("business_reviews")
  suspend fun insertBusinessReview(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body review: SupabaseBusinessReviewInsertDto
  ): Response<List<SupabaseBusinessReviewDto>>

  // --- COMMUNITIES ---
  @GET("communities")
  suspend fun getCommunities(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("order") order: String = "members_count.desc",
    @Query("limit") limit: Int = 50
  ): Response<List<SupabaseCommunityDto>>

  @POST("communities")
  suspend fun insertCommunity(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body community: SupabaseCommunityInsertDto
  ): Response<List<SupabaseCommunityDto>>

  @GET("community_members")
  suspend fun getCommunityMembers(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("community_id") communityIdFilter: String? = null,
    @Query("user_id") userIdFilter: String? = null,
    @Query("select") select: String = "*,profiles:user_id(*)"
  ): Response<List<SupabaseCommunityMemberDto>>

  @POST("community_members")
  suspend fun insertCommunityMember(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Query("on_conflict") onConflict: String = "community_id,user_id",
    @Body member: SupabaseCommunityMemberInsertDto
  ): Response<List<SupabaseCommunityMemberDto>>

  @PATCH("community_members")
  suspend fun patchCommunityMember(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("community_id") communityIdFilter: String,
    @Query("user_id") userIdFilter: String,
    @Body patch: SupabaseCommunityMemberPatchDto
  ): Response<Unit>

  @DELETE("community_members")
  suspend fun deleteCommunityMember(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("community_id") communityIdFilter: String,
    @Query("user_id") userIdFilter: String
  ): Response<Unit>

  @GET("community_posts")
  suspend fun getCommunityPosts(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("community_id") communityIdFilter: String,
    @Query("select") select: String = "*,profiles:author_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 50
  ): Response<List<SupabaseCommunityPostDto>>

  @POST("community_posts")
  suspend fun insertCommunityPost(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body post: SupabaseCommunityPostInsertDto
  ): Response<List<SupabaseCommunityPostDto>>

  @PATCH("community_posts")
  suspend fun patchCommunityPost(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Query("id") idFilter: String,
    @Body patch: SupabaseCommunityPostPatchDto
  ): Response<List<SupabaseCommunityPostDto>>

  @DELETE("community_posts")
  suspend fun deleteCommunityPost(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("id") idFilter: String
  ): Response<Unit>

  @GET("community_post_likes")
  suspend fun getCommunityPostLikes(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("post_id") postIdsInFilter: String,
    @Query("user_id") userIdFilter: String? = null
  ): Response<List<SupabaseCommunityPostLikeDto>>

  @POST("community_post_likes")
  suspend fun insertCommunityPostLike(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates",
    @Query("on_conflict") onConflict: String = "post_id,user_id",
    @Body like: SupabaseCommunityPostLikeDto
  ): Response<Unit>

  @DELETE("community_post_likes")
  suspend fun deleteCommunityPostLike(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("post_id") postIdFilter: String,
    @Query("user_id") userIdFilter: String
  ): Response<Unit>

  @GET("community_post_comments")
  suspend fun getCommunityPostComments(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("post_id") postIdFilter: String,
    @Query("select") select: String = "*,profiles:author_id(*)",
    @Query("order") order: String = "created_at.asc",
    @Query("limit") limit: Int = 100
  ): Response<List<SupabaseCommunityCommentDto>>

  @POST("community_post_comments")
  suspend fun insertCommunityPostComment(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body comment: SupabaseCommunityCommentInsertDto
  ): Response<List<SupabaseCommunityCommentDto>>

  // --- FCM DEVICE TOKENS ---
  @POST("user_device_tokens")
  suspend fun upsertDeviceToken(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Query("on_conflict") onConflict: String = "user_id,fcm_token",
    @Body body: SupabaseDeviceTokenDto
  ): Response<List<SupabaseDeviceTokenDto>>

  @PATCH("user_device_tokens")
  suspend fun patchDeviceToken(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String,
    @Query("fcm_token") fcmTokenFilter: String? = null,
    @Body patch: SupabaseDeviceTokenPatchDto
  ): Response<Unit>

  // --- OFFICIAL VYBE BADGE APPLICATIONS & ADMIN RPCs ---
  @GET("verification_requests")
  suspend fun getVerificationRequests(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String? = null,
    @Query("status") statusFilter: String? = null,
    @Query("select") select: String = "*,profiles:user_id(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 100
  ): Response<List<SupabaseVerificationRequestDto>>

  @POST("verification_requests")
  suspend fun insertVerificationRequest(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body body: SupabaseVerificationRequestInsertDto
  ): Response<List<SupabaseVerificationRequestDto>>

  @POST("rpc/admin_assign_badge")
  suspend fun adminAssignBadgeRpc(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcAdminAssignBadgeRequest
  ): Response<Map<String, @JvmSuppressWildcards Any?>>

  @POST("rpc/admin_review_verification_request")
  suspend fun adminReviewVerificationRequestRpc(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcAdminReviewVerificationRequest
  ): Response<Map<String, @JvmSuppressWildcards Any?>>

  @POST("rpc/user_respond_badge_offer")
  suspend fun userRespondBadgeOfferRpc(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcUserRespondBadgeOfferRequest
  ): Response<Map<String, @JvmSuppressWildcards Any?>>

  @GET("admin_audit_logs")
  suspend fun getAdminAuditLogs(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 50
  ): Response<List<SupabaseAuditLogDto>>
}

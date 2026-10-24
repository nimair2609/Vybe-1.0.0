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
  suspend fun refreshSession(
    @Header("apikey") apiKey: String,
    @Query("grant_type") grantType: String = "refresh_token",
    @Body request: SupabaseRefreshTokenRequest
  ): Response<SupabaseAuthResponse>

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
  // --- PROFILES ---
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

  // --- CONVERSATIONS & MESSAGING ---
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
    @Body conversation: SupabaseConversationDto
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
    @Body message: SupabaseMessageDto
  ): Response<List<SupabaseMessageDto>>

  // --- COMMENTS ---
  @GET("comments")
  suspend fun getComments(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("post_id") postIdFilter: String,
    @Query("select") select: String = "*,profiles(*)",
    @Query("order") order: String = "created_at.asc",
    @Query("limit") limit: Int = 100
  ): Response<List<SupabaseCommentDto>>

  @POST("comments")
  suspend fun insertComment(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body comment: SupabaseCommentDto
  ): Response<List<SupabaseCommentDto>>

  // --- POST REACTIONS & SAVES ---
  @GET("post_reactions")
  suspend fun getMyReactions(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("user_id") userIdFilter: String
  ): Response<List<SupabaseReactionDto>>

  @POST("post_reactions")
  suspend fun upsertReaction(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
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
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 50
  ): Response<List<SupabaseNotificationDto>>

  @POST("notifications")
  suspend fun insertNotification(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body notification: SupabaseNotificationDto
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
    @Query("select") select: String = "*,profiles(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 80
  ): Response<List<SupabaseStoryDto>>

  @POST("stories")
  suspend fun insertStory(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body story: SupabaseStoryDto
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
    @Body body: SupabaseReportDto
  ): Response<List<SupabaseReportDto>>

  // --- POSTS & POST MEDIA ---
  @GET("posts")
  suspend fun getPosts(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("select") select: String = "*,post_media(*),profiles(*)",
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 60
  ): Response<List<SupabasePostDto>>

  @GET("posts")
  suspend fun getPostByClientUploadId(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Query("client_upload_id") clientUploadFilter: String,
    @Query("select") select: String = "*,post_media(*),profiles(*)"
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
    @Body media: SupabasePostMediaDto
  ): Response<List<SupabasePostMediaDto>>

  @POST("post_media")
  suspend fun insertPostMediaLegacy(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Header("Prefer") prefer: String = "return=representation",
    @Body media: SupabasePostMediaLegacyDto
  ): Response<List<SupabasePostMediaDto>>

  // --- RPC: INCREMENT POST VIEW ---
  @POST("rpc/increment_post_view")
  suspend fun incrementPostView(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String? = null,
    @Body request: SupabaseRpcIncrementViewRequest
  ): Response<Int>
}

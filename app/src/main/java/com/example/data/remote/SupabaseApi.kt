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
  @GET("profiles")
  suspend fun getProfileById(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("id") idFilter: String,
    @Query("select") select: String = "*"
  ): Response<List<SupabaseProfileDto>>

  @GET("profiles")
  suspend fun getProfileByUsername(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("username") usernameFilter: String,
    @Query("select") select: String = "*"
  ): Response<List<SupabaseProfileDto>>

  @POST("profiles")
  suspend fun upsertProfile(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body profile: SupabaseProfileDto
  ): Response<List<SupabaseProfileDto>>

  // --- MESSAGING ---
  @GET("messages")
  suspend fun getMessages(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("conversation_id") conversationIdFilter: String,
    @Query("order") order: String = "created_at.asc",
    @Query("limit") limit: Int = 50
  ): Response<List<SupabaseMessageDto>>

  @POST("messages")
  suspend fun insertMessage(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Header("Prefer") prefer: String = "return=representation",
    @Body message: SupabaseMessageDto
  ): Response<List<SupabaseMessageDto>>

  // --- COMMENTS ---
  @GET("comments")
  suspend fun getComments(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("post_id") postIdFilter: String,
    @Query("order") order: String = "created_at.asc",
    @Query("limit") limit: Int = 50
  ): Response<List<SupabaseCommentDto>>

  @POST("comments")
  suspend fun insertComment(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Header("Prefer") prefer: String = "return=representation",
    @Body comment: SupabaseCommentDto
  ): Response<List<SupabaseCommentDto>>

  // --- NOTIFICATIONS ---
  @GET("notifications")
  suspend fun getNotifications(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("recipient_id") recipientIdFilter: String,
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 40
  ): Response<List<SupabaseNotificationDto>>

  @DELETE("notifications")
  suspend fun deleteNotification(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("id") idFilter: String
  ): Response<Unit>

  @PATCH("notifications")
  suspend fun markNotificationRead(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("id") idFilter: String,
    @Body body: Map<String, Boolean>
  ): Response<Unit>

  // --- FOLLOWS ---
  @GET("follows")
  suspend fun getFollows(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("follower_id") followerIdFilter: String
  ): Response<List<SupabaseFollowDto>>

  @POST("follows")
  suspend fun insertFollow(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body body: SupabaseFollowDto
  ): Response<List<SupabaseFollowDto>>

  @DELETE("follows")
  suspend fun deleteFollow(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("follower_id") followerFilter: String,
    @Query("followed_id") followedFilter: String
  ): Response<Unit>

  // --- BLOCKS ---
  @GET("blocks")
  suspend fun getBlocks(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("blocker_id") blockerIdFilter: String
  ): Response<List<SupabaseBlockDto>>

  @POST("blocks")
  suspend fun insertBlock(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
    @Body body: SupabaseBlockDto
  ): Response<List<SupabaseBlockDto>>

  @DELETE("blocks")
  suspend fun deleteBlock(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("blocker_id") blockerFilter: String,
    @Query("blocked_id") blockedFilter: String
  ): Response<Unit>

  // --- REPORTS ---
  @POST("reports")
  suspend fun submitReport(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Header("Prefer") prefer: String = "return=representation",
    @Body body: SupabaseReportDto
  ): Response<List<SupabaseReportDto>>
}


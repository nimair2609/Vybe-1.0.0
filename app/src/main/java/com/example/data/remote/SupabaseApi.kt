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
}

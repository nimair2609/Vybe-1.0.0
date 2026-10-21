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

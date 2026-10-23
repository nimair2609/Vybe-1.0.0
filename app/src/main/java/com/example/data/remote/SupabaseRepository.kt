package com.example.data.remote

import android.util.Log
import com.example.data.model.MediaType
import com.example.data.model.MoodType
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Supabase Data Repository for VYBE.
 *
 * Implements real GoTrue REST authentication (signup, email/password login,
 * in-app OTP verification, password recovery/reset, and session logout)
 * and PostgREST profile operations.
 */
class SupabaseRepository {

  companion object {
    private const val TAG = "SupabaseRepo"
    @Volatile
    private var instance: SupabaseRepository? = null

    fun getInstance(): SupabaseRepository {
      return instance ?: synchronized(this) {
        instance ?: SupabaseRepository().also { instance = it }
      }
    }
  }

  val isConfigured: Boolean
    get() = SupabaseConfig.isConfigured()

  /**
   * Registers a new user with email and password via Supabase Auth.
   */
  suspend fun signUpWithEmail(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    phone: String,
    dateOfBirth: String
  ): Result<Pair<User, String>> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(
        IllegalStateException("Supabase backend is not configured.")
      )
    }

    try {
      val cleanUsername = username.trim().lowercase().removePrefix("@")
      val displayName = if (firstName.isNotBlank()) "$firstName $lastName".trim() else cleanUsername
      val metadata = mapOf(
        "username" to cleanUsername,
        "display_name" to displayName,
        "first_name" to firstName,
        "last_name" to lastName,
        "phone" to phone,
        "date_of_birth" to dateOfBirth
      )

      val request = SupabaseSignUpRequest(
        email = email.trim(),
        password = password,
        data = metadata
      )

      val response = SupabaseNetworkClient.authApi.signUp(
        apiKey = SupabaseConfig.supabaseAnonKey,
        redirectTo = "vybe://auth-callback",
        request = request
      )

      if (response.isSuccessful && response.body() != null) {
        val authBody = response.body()!!
        val userDto = authBody.user ?: return@withContext Result.failure(
          Exception(authBody.msg ?: authBody.errorDescription ?: "Sign up succeeded but user record was not returned.")
        )

        val age = calculateAge(dateOfBirth)
        val isUnder18 = age in 16..17

        val createdUser = User(
          id = userDto.id,
          username = cleanUsername,
          displayName = displayName,
          firstName = firstName,
          lastName = lastName,
          email = email.trim(),
          phone = phone,
          isEmailVerified = false,
          isPhoneVerified = false,
          dateOfBirth = dateOfBirth,
          age = age,
          isUnder18 = isUnder18,
          role = UserRole.USER,
          isOnboardingCompleted = false
        )

        val token = authBody.accessToken ?: ""

        // Try creating / upserting profile in public.profiles if token is present
        if (token.isNotEmpty()) {
          try {
            val profileDto = SupabaseProfileDto(
              id = userDto.id,
              username = cleanUsername,
              displayName = displayName,
              firstName = firstName,
              lastName = lastName,
              email = email.trim(),
              phone = phone,
              dateOfBirth = dateOfBirth,
              age = age,
              isUnder18 = isUnder18,
              role = "USER"
            )
            SupabaseNetworkClient.restApi.upsertProfile(
              apiKey = SupabaseConfig.supabaseAnonKey,
              authHeader = "Bearer $token",
              profile = profileDto
            )
          } catch (e: Exception) {
            Log.w(TAG, "Profile upsert notice (trigger handles): ${e.message}")
          }
        }

        Result.success(Pair(createdUser, token))
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        val errorMsg = parseErrorMessage(errorBody, "Sign up failed (${response.code()})")
        Result.failure(Exception(errorMsg))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Sign up exception: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Signs in an existing user with email and password via Supabase Auth.
   */
  suspend fun signInWithEmail(
    email: String,
    password: String
  ): Result<Pair<User, String>> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(
        IllegalStateException("Supabase backend is not configured.")
      )
    }

    try {
      val request = SupabaseSignInRequest(
        email = email.trim(),
        password = password
      )

      val response = SupabaseNetworkClient.authApi.signIn(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = request
      )

      if (response.isSuccessful && response.body() != null) {
        val authBody = response.body()!!
        val token = authBody.accessToken ?: return@withContext Result.failure(
          Exception("No access token returned from Supabase. (Email may not be confirmed yet)")
        )
        val userDto = authBody.user ?: return@withContext Result.failure(
          Exception("No user record returned from Supabase.")
        )

        var userProfile = fetchUserProfile(userDto.id, token, userDto.email ?: email.trim())

        if (userProfile == null) {
          val meta = userDto.userMetadata ?: emptyMap()
          val username = (meta["username"] as? String) ?: email.substringBefore("@")
          val displayName = (meta["display_name"] as? String) ?: username
          val firstName = (meta["first_name"] as? String) ?: ""
          val lastName = (meta["last_name"] as? String) ?: ""
          val phone = (meta["phone"] as? String) ?: userDto.phone ?: ""
          val dob = (meta["date_of_birth"] as? String) ?: "2000-01-01"
          val age = calculateAge(dob)

          userProfile = User(
            id = userDto.id,
            username = username,
            displayName = displayName,
            firstName = firstName,
            lastName = lastName,
            email = userDto.email ?: email.trim(),
            phone = phone,
            dateOfBirth = dob,
            age = age,
            isUnder18 = age in 16..17,
            role = UserRole.USER,
            isOnboardingCompleted = true
          )
        }

        Result.success(Pair(userProfile, token))
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        val errorMsg = parseErrorMessage(errorBody, "Invalid login credentials (${response.code()})")
        Result.failure(Exception(errorMsg))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Sign in exception: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Verifies an email or recovery OTP code directly in-app without requiring browser redirects.
   */
  suspend fun verifyOtp(
    email: String,
    token: String,
    type: String = "signup"
  ): Result<Pair<User, String>> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    }
    try {
      val request = SupabaseVerifyOtpRequest(
        type = type,
        token = token.trim(),
        email = email.trim()
      )
      val response = SupabaseNetworkClient.authApi.verifyOtp(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = request
      )
      if (response.isSuccessful && response.body() != null) {
        val authBody = response.body()!!
        val accessToken = authBody.accessToken ?: return@withContext Result.failure(
          Exception("OTP verified, but session token was empty.")
        )
        val userDto = authBody.user ?: return@withContext Result.failure(
          Exception("OTP verified, but user data was missing.")
        )

        var userProfile = fetchUserProfile(userDto.id, accessToken, userDto.email ?: email.trim())

        if (userProfile == null) {
          val meta = userDto.userMetadata ?: emptyMap()
          val username = (meta["username"] as? String) ?: email.substringBefore("@")
          val displayName = (meta["display_name"] as? String) ?: username
          val firstName = (meta["first_name"] as? String) ?: ""
          val lastName = (meta["last_name"] as? String) ?: ""
          val phone = (meta["phone"] as? String) ?: userDto.phone ?: ""
          val dob = (meta["date_of_birth"] as? String) ?: "2000-01-01"
          val age = calculateAge(dob)

          userProfile = User(
            id = userDto.id,
            username = username,
            displayName = displayName,
            firstName = firstName,
            lastName = lastName,
            email = userDto.email ?: email.trim(),
            phone = phone,
            dateOfBirth = dob,
            age = age,
            isUnder18 = age in 16..17,
            role = UserRole.USER,
            isOnboardingCompleted = true
          )
        }

        Result.success(Pair(userProfile, accessToken))
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        val errorMsg = parseErrorMessage(errorBody, "Invalid or expired verification code.")
        Result.failure(Exception(errorMsg))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Sends a password reset request via Supabase Auth.
   */
  suspend fun sendPasswordReset(email: String): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    }
    try {
      val request = SupabaseRecoverPasswordRequest(email = email.trim())
      val response = SupabaseNetworkClient.authApi.recoverPassword(
        apiKey = SupabaseConfig.supabaseAnonKey,
        redirectTo = "vybe://reset-password",
        request = request
      )
      if (response.isSuccessful) {
        Result.success("Password reset code sent to $email. Please check your inbox.")
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        val errorMsg = parseErrorMessage(errorBody, "Failed to send password reset request.")
        Result.failure(Exception(errorMsg))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Resets the user's password using the OTP code received in email.
   */
  suspend fun resetPasswordWithOtp(
    email: String,
    otp: String,
    newPassword: String
  ): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    }
    try {
      val verifyReq = SupabaseVerifyOtpRequest(
        type = "recovery",
        token = otp.trim(),
        email = email.trim()
      )
      val verifyResp = SupabaseNetworkClient.authApi.verifyOtp(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = verifyReq
      )
      if (!verifyResp.isSuccessful || verifyResp.body()?.accessToken == null) {
        val err = parseErrorMessage(verifyResp.errorBody()?.string() ?: "", "Invalid or expired recovery code.")
        return@withContext Result.failure(Exception(err))
      }

      val token = verifyResp.body()!!.accessToken!!
      val updateReq = SupabaseUpdateUserRequest(password = newPassword)
      val updateResp = SupabaseNetworkClient.authApi.updateUser(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer $token",
        request = updateReq
      )

      if (updateResp.isSuccessful) {
        Result.success("Password reset successfully! Please log in with your new password.")
      } else {
        val err = parseErrorMessage(updateResp.errorBody()?.string() ?: "", "Failed to update password.")
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun signOut(accessToken: String): Boolean = withContext(Dispatchers.IO) {
    if (!isConfigured || accessToken.isEmpty()) return@withContext true
    try {
      val response = SupabaseNetworkClient.authApi.logout(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer $accessToken"
      )
      response.isSuccessful
    } catch (e: Exception) {
      Log.w(TAG, "Sign out network call error: ${e.message}")
      true
    }
  }

  private fun calculateAge(dateOfBirth: String): Int {
    return try {
      val parts = dateOfBirth.split("-")
      if (parts.size == 3) {
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val cal = java.util.Calendar.getInstance()
        val cy = cal.get(java.util.Calendar.YEAR)
        val cm = cal.get(java.util.Calendar.MONTH) + 1
        val cd = cal.get(java.util.Calendar.DAY_OF_MONTH)
        var calculated = cy - y
        if (cm < m || (cm == m && cd < d)) calculated--
        calculated
      } else 20
    } catch (e: Exception) {
      20
    }
  }

  private fun parseErrorMessage(jsonBody: String, fallback: String): String {
    if (jsonBody.isBlank()) return fallback
    return try {
      val json = JSONObject(jsonBody)
      when {
        json.has("error_description") -> json.getString("error_description")
        json.has("msg") -> json.getString("msg")
        json.has("message") -> json.getString("message")
        json.has("error") -> json.getString("error")
        else -> fallback
      }
    } catch (e: Exception) {
      fallback
    }
  }

  suspend fun updateUserProfile(
    userId: String,
    firstName: String,
    lastName: String,
    displayName: String,
    bio: String,
    avatarUrl: String,
    coverUrl: String,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val dto = SupabaseProfileDto(
        id = userId,
        username = "",
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        bio = bio,
        avatarUrl = avatarUrl,
        coverUrl = coverUrl
      )
      val resp = SupabaseNetworkClient.restApi.upsertProfile(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        profile = dto
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to update profile"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun mapProfileDtoToUser(p: SupabaseProfileDto, fallbackEmail: String? = null): User {
    val rawRole = (p.role ?: "USER").trim().uppercase()
    val parsedRole = try {
      UserRole.valueOf(rawRole)
    } catch (e: Exception) {
      UserRole.USER
    }
    val isAdmin = parsedRole == UserRole.ADMIN

    return User(
      id = p.id,
      username = p.username,
      displayName = p.displayName,
      firstName = p.firstName ?: "",
      lastName = p.lastName ?: "",
      email = p.email ?: fallbackEmail ?: "",
      phone = p.phone ?: "",
      bio = p.bio ?: "",
      avatarUrl = p.avatarUrl ?: "",
      coverUrl = p.coverUrl ?: "",
      dateOfBirth = p.dateOfBirth ?: "2000-01-01",
      age = p.age ?: 20,
      isUnder18 = p.isUnder18 ?: false,
      role = parsedRole,
      isVerified = if (isAdmin) true else (p.isVerified ?: false),
      isVip = if (isAdmin) true else (p.isVip ?: false),
      followersCount = p.followersCount ?: 0,
      followingCount = p.followingCount ?: 0,
      postsCount = p.postsCount ?: 0,
      isOnboardingCompleted = true
    )
  }

  suspend fun fetchUserProfile(userId: String, token: String? = null, email: String? = null): User? = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext null
    val authToken = if (!token.isNullOrBlank()) token else SupabaseConfig.supabaseAnonKey
    val authHeader = "Bearer $authToken"

    // 1. Primary lookup: by ID
    try {
      val resp = SupabaseNetworkClient.restApi.getProfileById(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$userId"
      )
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        return@withContext mapProfileDtoToUser(resp.body()!!.first(), email)
      }
    } catch (e: Exception) {
      Log.w(TAG, "getProfileById failed: ${e.message}")
    }

    // 2. Secondary lookup: by Email if provided
    if (!email.isNullOrBlank()) {
      try {
        val emailResp = SupabaseNetworkClient.restApi.getProfileByEmail(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          emailFilter = "eq.${email.trim()}"
        )
        if (emailResp.isSuccessful && !emailResp.body().isNullOrEmpty()) {
          return@withContext mapProfileDtoToUser(emailResp.body()!!.first(), email)
        }
      } catch (e: Exception) {
        Log.w(TAG, "getProfileByEmail failed: ${e.message}")
      }
    }

    return@withContext null
  }

  suspend fun checkUsernameAvailable(username: String): Boolean = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext true
    try {
      val resp = SupabaseNetworkClient.restApi.getProfileByUsername(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer ${SupabaseConfig.supabaseAnonKey}",
        usernameFilter = "eq.${username.trim().lowercase()}"
      )
      if (resp.isSuccessful) {
        val list = resp.body() ?: emptyList()
        list.isEmpty()
      } else {
        true
      }
    } catch (e: Exception) {
      true
    }
  }

  suspend fun resolveEmailForIdentifier(identifier: String): String? = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext null
    val trimmed = identifier.trim()
    val clean = trimmed.lowercase().removePrefix("@")
    if (clean.contains("@")) return@withContext clean

    val phoneClean = trimmed.replace(" ", "").replace("-", "")

    // Try username
    try {
      val resp = SupabaseNetworkClient.restApi.getProfileByUsername(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer ${SupabaseConfig.supabaseAnonKey}",
        usernameFilter = "eq.$clean"
      )
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        val em = resp.body()!!.first().email
        if (!em.isNullOrBlank()) return@withContext em
      }
    } catch (e: Exception) {
      Log.w(TAG, "Username resolution error: ${e.message}")
    }

    // Try phone
    try {
      val phoneResp = SupabaseNetworkClient.restApi.getProfileByPhone(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer ${SupabaseConfig.supabaseAnonKey}",
        phoneFilter = "eq.$trimmed"
      )
      if (phoneResp.isSuccessful && !phoneResp.body().isNullOrEmpty()) {
        val em = phoneResp.body()!!.first().email
        if (!em.isNullOrBlank()) return@withContext em
      }
    } catch (e: Exception) {
      Log.w(TAG, "Phone resolution error: ${e.message}")
    }

    // Fallback: search all profiles for matching username, phone or id
    try {
      val allResp = SupabaseNetworkClient.restApi.getProfiles(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer ${SupabaseConfig.supabaseAnonKey}"
      )
      if (allResp.isSuccessful && !allResp.body().isNullOrEmpty()) {
        val matched = allResp.body()!!.firstOrNull {
          it.username.equals(clean, true) ||
          it.id.equals(clean, true) ||
          (!it.phone.isNullOrBlank() && (it.phone.replace(" ", "").replace("-", "") == phoneClean || it.phone == trimmed))
        }
        if (matched != null && !matched.email.isNullOrBlank()) {
          return@withContext matched.email
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "All profiles search error: ${e.message}")
    }

    null
  }

  suspend fun fetchAllProfiles(): List<User> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext emptyList()
    try {
      val resp = SupabaseNetworkClient.restApi.getProfiles(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer ${SupabaseConfig.supabaseAnonKey}"
      )
      if (resp.isSuccessful && resp.body() != null) {
        return@withContext resp.body()!!.map { p ->
          User(
            id = p.id,
            username = p.username,
            displayName = p.displayName,
            firstName = p.firstName ?: "",
            lastName = p.lastName ?: "",
            bio = p.bio ?: "",
            avatarUrl = p.avatarUrl ?: "",
            coverUrl = p.coverUrl ?: "",
            email = p.email ?: "",
            phone = p.phone ?: "",
            dateOfBirth = p.dateOfBirth ?: "2000-01-01",
            age = p.age ?: 20,
            isUnder18 = p.isUnder18 ?: false,
            role = try { UserRole.valueOf(p.role ?: "USER") } catch (e: Exception) { UserRole.USER },
            isVerified = p.isVerified ?: false,
            isVip = p.isVip ?: false,
            followersCount = p.followersCount ?: 0,
            followingCount = p.followingCount ?: 0,
            postsCount = p.postsCount ?: 0,
            isOnboardingCompleted = true
          )
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "fetchAllProfiles exception: ${e.message}")
    }
    emptyList()
  }

  suspend fun sendMessage(
    conversationId: String,
    senderId: String,
    content: String,
    mediaUrl: String? = null,
    mediaType: String = "TEXT",
    replyToId: String? = null,
    accessToken: String = ""
  ): Result<SupabaseMessageDto> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(Exception("Supabase not configured"))
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val dto = SupabaseMessageDto(
        conversationId = conversationId,
        senderId = senderId,
        content = content,
        mediaUrl = mediaUrl,
        mediaType = mediaType,
        replyToMessageId = replyToId
      )
      val resp = SupabaseNetworkClient.restApi.insertMessage(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        message = dto
      )
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        Result.success(resp.body()!!.first())
      } else {
        val err = parseErrorMessage(resp.errorBody()?.string() ?: "", "Failed to send message.")
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun fetchMessages(
    conversationId: String,
    accessToken: String = "",
    limit: Int = 40
  ): Result<List<SupabaseMessageDto>> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(emptyList())
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.getMessages(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        conversationIdFilter = "eq.$conversationId",
        limit = limit
      )
      if (resp.isSuccessful && resp.body() != null) {
        Result.success(resp.body()!!)
      } else {
        Result.failure(Exception("Failed to fetch messages"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun addComment(
    postId: String,
    authorId: String,
    content: String,
    accessToken: String = ""
  ): Result<SupabaseCommentDto> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(Exception("Supabase not configured"))
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val dto = SupabaseCommentDto(
        postId = postId,
        authorId = authorId,
        content = content
      )
      val resp = SupabaseNetworkClient.restApi.insertComment(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        comment = dto
      )
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        Result.success(resp.body()!!.first())
      } else {
        Result.failure(Exception("Failed to post comment"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun followUser(followerId: String, followedId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.insertFollow(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        body = SupabaseFollowDto(followerId = followerId, followedId = followedId)
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to follow"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun unfollowUser(followerId: String, followedId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.deleteFollow(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        followerFilter = "eq.$followerId",
        followedFilter = "eq.$followedId"
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to unfollow"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun blockUser(blockerId: String, blockedId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.insertBlock(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        body = SupabaseBlockDto(blockerId = blockerId, blockedId = blockedId)
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to block user"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun unblockUser(blockerId: String, blockedId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.deleteBlock(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        blockerFilter = "eq.$blockerId",
        blockedFilter = "eq.$blockedId"
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to unblock user"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun submitReport(
    reporterId: String,
    targetType: String,
    targetId: String,
    reason: String,
    details: String = "",
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val dto = SupabaseReportDto(
        reporterId = reporterId,
        targetType = targetType,
        targetId = targetId,
        reason = reason,
        adminNotes = details.ifBlank { null }
      )
      val resp = SupabaseNetworkClient.restApi.submitReport(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        body = dto
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to submit report"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteNotification(notificationId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.deleteNotification(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$notificationId"
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to delete notification"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Uploads raw media (JPEG/PNG/MP4) to Supabase Storage and returns the public CDN URL.
   */
  suspend fun uploadMediaFile(
    bucket: String,
    fileName: String,
    bytes: ByteArray,
    mimeType: String,
    accessToken: String = ""
  ): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase backend not configured"))
    try {
      val cleanBucket = bucket.trim()
      val cleanFileName = fileName.trim().removePrefix("/")
      val uploadUrl = "${SupabaseConfig.supabaseUrl}/storage/v1/object/$cleanBucket/$cleanFileName"
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"

      val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
      val request = Request.Builder()
        .url(uploadUrl)
        .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
        .addHeader("Authorization", authHeader)
        .addHeader("Content-Type", mimeType)
        .addHeader("x-upsert", "true")
        .post(body)
        .build()

      val response = SupabaseNetworkClient.okHttpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val publicUrl = "${SupabaseConfig.supabaseUrl}/storage/v1/object/public/$cleanBucket/$cleanFileName"
        Result.success(publicUrl)
      } else {
        Result.failure(Exception("Storage upload error HTTP ${response.code}: ${response.message}"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Storage upload failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Fetches posts from Supabase PostgREST, including post media and author profile.
   */
  suspend fun fetchPosts(accessToken: String = ""): Result<List<Post>> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(emptyList())
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.getPosts(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        limit = 50
      )
      if (resp.isSuccessful && resp.body() != null) {
        val dtos = resp.body()!!
        val postsList = dtos.map { dto ->
          val authorUser = if (dto.profiles != null) {
            User(
              id = dto.profiles.id,
              username = dto.profiles.username,
              displayName = dto.profiles.displayName,
              avatarUrl = dto.profiles.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
              isVerified = dto.profiles.isVerified ?: false,
              isVip = dto.profiles.isVip ?: false,
              followersCount = dto.profiles.followersCount ?: 0,
              followingCount = dto.profiles.followingCount ?: 0,
              postsCount = dto.profiles.postsCount ?: 0
            )
          } else {
            User(id = dto.authorId, username = "vyber", displayName = "Vyber")
          }

          val mediaUrls = dto.postMedia?.sortedBy { it.displayOrder }?.map { it.mediaUrl } ?: emptyList()
          val mediaType = try {
            MediaType.valueOf(dto.mediaType ?: "IMAGE")
          } catch (e: Exception) {
            if (mediaUrls.any { it.endsWith(".mp4", ignoreCase = true) }) MediaType.VIDEO else MediaType.IMAGE
          }
          val moodZone = if (!dto.moodZone.isNullOrBlank()) {
            try { MoodType.valueOf(dto.moodZone) } catch (e: Exception) { null }
          } else null

          Post(
            id = dto.id,
            author = authorUser,
            caption = dto.caption ?: "",
            mediaUrls = mediaUrls,
            mediaType = mediaType,
            timestamp = dto.createdAt?.take(10) ?: "Recently",
            likesCount = dto.likesCount ?: 0,
            commentsCount = dto.commentsCount ?: 0,
            savesCount = dto.savesCount ?: 0,
            viewsCount = dto.viewsCount ?: 0,
            isUnfiltered = dto.isUnfiltered ?: false,
            isVybesReel = dto.isVybesReel ?: false,
            moodZone = moodZone
          )
        }
        Result.success(postsList)
      } else {
        Result.failure(Exception("Failed to fetch posts: HTTP ${resp.code()}"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching posts from Supabase: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Creates a post in Supabase and inserts associated media items.
   */
  suspend fun createPostOnSupabase(
    post: Post,
    accessToken: String = ""
  ): Result<Post> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(post)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val insertDto = SupabasePostInsertDto(
        authorId = post.author.id,
        caption = post.caption,
        mediaType = post.mediaType.name,
        moodZone = post.moodZone?.name,
        isUnfiltered = post.isUnfiltered,
        isVybesReel = post.isVybesReel,
        viewsCount = 0
      )

      val postResp = SupabaseNetworkClient.restApi.insertPost(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        post = insertDto
      )

      if (postResp.isSuccessful && !postResp.body().isNullOrEmpty()) {
        val createdPostDto = postResp.body()!!.first()
        val createdId = createdPostDto.id

        // Insert media if present
        if (post.mediaUrls.isNotEmpty()) {
          post.mediaUrls.forEachIndexed { index, url ->
            val mediaDto = SupabasePostMediaDto(
              postId = createdId,
              mediaUrl = url,
              mediaType = post.mediaType.name,
              displayOrder = index
            )
            try {
              SupabaseNetworkClient.restApi.insertPostMedia(
                apiKey = SupabaseConfig.supabaseAnonKey,
                authHeader = authHeader,
                media = mediaDto
              )
            } catch (mediaErr: Exception) {
              Log.w(TAG, "Media insertion warning: ${mediaErr.message}")
            }
          }
        }

        Result.success(post.copy(id = createdId))
      } else {
        Result.failure(Exception("Post creation failed on Supabase: HTTP ${postResp.code()}"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed creating post on Supabase: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Deletes a post from Supabase by post ID.
   */
  suspend fun deletePostOnSupabase(
    postId: String,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.deletePost(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$postId"
      )
      if (resp.isSuccessful) {
        Result.success(Unit)
      } else {
        Result.failure(Exception("Failed to delete post on Supabase: HTTP ${resp.code()}"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete post: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Increments post view count via Supabase RPC.
   */
  suspend fun recordPostView(
    postId: String,
    viewerId: String? = null,
    accessToken: String = ""
  ): Result<Int> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(1)
    try {
      val authHeader = if (accessToken.isNotBlank()) "Bearer $accessToken" else "Bearer ${SupabaseConfig.supabaseAnonKey}"
      val resp = SupabaseNetworkClient.restApi.incrementPostView(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        request = SupabaseRpcIncrementViewRequest(postId = postId, viewerId = viewerId)
      )
      if (resp.isSuccessful && resp.body() != null) {
        Result.success(resp.body()!!)
      } else {
        Result.success(1)
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}


package com.example.data.remote

import android.util.Log
import com.example.data.model.User
import com.example.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period

class SupabaseRepository {

  companion object {
    private const val TAG = "SupabaseRepo"
  }

  val isConfigured: Boolean
    get() = SupabaseConfig.isConfigured()

  /**
   * Registers a new user with Supabase Auth (email + password).
   * Also ensures a public profile is created in public.profiles.
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
        IllegalStateException("Supabase backend is not configured. Please add your SUPABASE_URL and SUPABASE_ANON_KEY in the AI Studio Secrets panel.")
      )
    }

    try {
      val cleanUsername = username.trim().lowercase().replace(" ", "_").replace("@", "")
      val displayName = "$firstName $lastName".trim().ifBlank { cleanUsername }

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
        val refreshToken = authBody.refreshToken ?: ""

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
            Log.w(TAG, "Profile upsert notice (trigger may handle): ${e.message}")
          }
        }

        val confirmationNotice = if (token.isEmpty()) {
          "Account created! Please check your email inbox to confirm your email address before logging in."
        } else {
          "Account registered and verified successfully in Supabase!"
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
        IllegalStateException("Supabase backend is not configured. Please add your SUPABASE_URL and SUPABASE_ANON_KEY in the AI Studio Secrets panel.")
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
          Exception("No access token returned from Supabase.")
        )
        val userDto = authBody.user ?: return@withContext Result.failure(
          Exception("No user record returned from Supabase.")
        )

        // Fetch or derive user profile
        var userProfile: User? = null
        try {
          val profilesResp = SupabaseNetworkClient.restApi.getProfileById(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = "Bearer $token",
            idFilter = "eq.${userDto.id}"
          )
          if (profilesResp.isSuccessful && !profilesResp.body().isNullOrEmpty()) {
            val p = profilesResp.body()!!.first()
            userProfile = User(
              id = p.id,
              username = p.username,
              displayName = p.displayName,
              firstName = p.firstName ?: "",
              lastName = p.lastName ?: "",
              email = p.email ?: userDto.email ?: "",
              phone = p.phone ?: userDto.phone ?: "",
              bio = p.bio ?: "",
              avatarUrl = p.avatarUrl ?: "",
              coverUrl = p.coverUrl ?: "",
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
        } catch (e: Exception) {
          Log.w(TAG, "Could not fetch profile from rest/v1/profiles: ${e.message}")
        }

        if (userProfile == null) {
          // Derive from auth metadata
          val meta = userDto.userMetadata ?: emptyMap()
          val username = (meta["username"] as? String) ?: email.substringBefore("@")
          val displayName = (meta["display_name"] as? String) ?: username
          val firstName = (meta["first_name"] as? String) ?: ""
          val lastName = (meta["last_name"] as? String) ?: ""
          val dob = (meta["date_of_birth"] as? String) ?: "2000-01-01"
          val age = calculateAge(dob)

          userProfile = User(
            id = userDto.id,
            username = username,
            displayName = displayName,
            firstName = firstName,
            lastName = lastName,
            email = userDto.email ?: email.trim(),
            phone = userDto.phone ?: "",
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
        val errorMsg = parseErrorMessage(errorBody, "Invalid login credentials. (${response.code()})")
        Result.failure(Exception(errorMsg))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Sign in exception: ${e.message}", e)
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
        val dob = LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        Period.between(dob, LocalDate.now()).years
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
}

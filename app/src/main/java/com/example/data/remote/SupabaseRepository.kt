package com.example.data.remote

import android.util.Log
import com.example.data.model.Conversation
import com.example.data.model.DirectMessage
import com.example.data.model.LivePulseStatus
import com.example.data.model.MediaType
import com.example.data.model.MessageStatus
import com.example.data.model.MoodType
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.PostComment
import com.example.data.model.Story
import com.example.data.model.StoryAudience
import com.example.data.model.StoryMediaType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.VybeReaction
import com.example.data.session.SessionManager
import com.example.util.TimestampUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class AuthSessionPayload(
  val user: User,
  val accessToken: String,
  val refreshToken: String,
  val expiresInSeconds: Long
)

data class UploadedStorageMedia(
  val url: String,
  val storagePath: String
)

/**
 * Production Supabase Data Repository for VYBE.
 *
 * Fixes:
 * - Never sends `sb_publishable_*` keys inside `Authorization: Bearer ...` (uses `SupabaseConfig.buildAuthHeader`).
 * - Supports automatic session token refresh when JWT expires.
 * - Implements full GoTrue password recovery link/token verification, password update, and recovery sign-out.
 * - Uploads photos & videos with real byte progress % and signed/public URL resolution + Storage object cleanup on delete.
 * - Syncs profiles, posts, stories, story_views, vybe_matches, follows (with private approval), conversations, messages, and notifications.
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

  private val signedUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()

  fun normalizePhoneE164(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    val hasPlus = trimmed.startsWith("+")
    val digits = trimmed.filter { it.isDigit() }
    if (digits.isBlank()) return ""
    return when {
      hasPlus -> "+$digits"
      digits.length == 10 -> "+1$digits"
      else -> "+$digits"
    }
  }

  /**
   * Resolves a fresh valid JWT access token from explicit parameter or SessionManager,
   * refreshing via refresh_token automatically if within 60 seconds of expiry.
   */
  suspend fun resolveValidAccessToken(explicitToken: String? = null): String? {
    val cleanExplicit = explicitToken?.trim()?.removePrefix("Bearer ")?.trim()
    val sm = SessionManager.get()

    // 1. If an explicit token was provided and is still fresh, use it
    if (!cleanExplicit.isNullOrBlank() && SupabaseConfig.isJwtAccessToken(cleanExplicit) && !SupabaseConfig.isJwtExpired(cleanExplicit)) {
      return cleanExplicit
    }

    if (sm == null) {
      return if (!cleanExplicit.isNullOrBlank() && SupabaseConfig.isJwtAccessToken(cleanExplicit)) cleanExplicit else null
    }

    val currentToken = sm.accessToken
    val refreshToken = sm.refreshToken
    val expiresAt = sm.expiresAtMs

    val isCurrentValid = !currentToken.isNullOrBlank() &&
      SupabaseConfig.isJwtAccessToken(currentToken) &&
      !SupabaseConfig.isJwtExpired(currentToken) &&
      (expiresAt <= 0L || System.currentTimeMillis() <= (expiresAt - 60_000L))

    if (isCurrentValid) {
      return currentToken
    }

    // 2. Proactively refresh token when expired or near expiry to eliminate 'exp claim timestamp check failed'
    if (!refreshToken.isNullOrBlank()) {
      try {
        val resp = SupabaseNetworkClient.authApi.refreshSession(
          apiKey = SupabaseConfig.supabaseAnonKey,
          request = SupabaseRefreshTokenRequest(refreshToken = refreshToken)
        )
        if (resp.isSuccessful && resp.body()?.accessToken != null) {
          val body = resp.body()!!
          val freshAccess = body.accessToken!!
          val freshRefresh = body.refreshToken ?: refreshToken
          val expiresIn = body.expiresIn ?: 3600L
          sm.updateTokens(freshAccess, freshRefresh, expiresIn)
          return freshAccess
        }
      } catch (e: Exception) {
        Log.w(TAG, "Token refresh warning: ${e.message}")
      }
    }

    return if (!currentToken.isNullOrBlank() && SupabaseConfig.isJwtAccessToken(currentToken)) currentToken else cleanExplicit
  }

  private suspend fun authHeaderFor(explicitToken: String? = null): String? {
    val validJwt = resolveValidAccessToken(explicitToken)
    return SupabaseConfig.buildAuthHeader(validJwt)
  }

  // =========================================================================
  // 1. AUTHENTICATION & SECURE PASSWORD RECOVERY
  // =========================================================================

  suspend fun signUpWithEmail(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    phone: String,
    dateOfBirth: String
  ): Result<AuthSessionPayload> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
    }

    try {
      val cleanUsername = username.trim().lowercase().removePrefix("@").replace(" ", "_")
      val cleanFirst = firstName.trim()
      val cleanLast = lastName.trim()
      val normalizedPhone = normalizePhoneE164(phone).ifBlank { phone.trim() }
      val displayName = if (cleanFirst.isNotBlank()) "$cleanFirst $cleanLast".trim() else cleanUsername
      val metadata = mapOf(
        "username" to cleanUsername,
        "display_name" to displayName,
        "first_name" to cleanFirst,
        "last_name" to cleanLast,
        "phone" to normalizedPhone,
        "date_of_birth" to dateOfBirth.trim()
      )

      val response = SupabaseNetworkClient.authApi.signUp(
        apiKey = SupabaseConfig.supabaseAnonKey,
        redirectTo = "vybe://auth-callback",
        request = SupabaseSignUpRequest(
          email = email.trim().lowercase(),
          password = password,
          data = metadata
        )
      )

      if (response.isSuccessful && response.body() != null) {
        val authBody = response.body()!!
        val userDto = authBody.user ?: return@withContext Result.failure(
          Exception(authBody.msg ?: authBody.errorDescription ?: "Sign up succeeded, please check your email to verify.")
        )

        val age = calculateAge(dateOfBirth)
        val isUnder18 = age in 16..17
        val token = authBody.accessToken ?: ""
        val refresh = authBody.refreshToken ?: ""
        val expiresIn = authBody.expiresIn ?: 3600L
        val emailVerifiedNow = !userDto.emailConfirmedAt.isNullOrBlank()
        val phoneVerifiedNow = !userDto.phoneConfirmedAt.isNullOrBlank()
        val createdIso = userDto.createdAt ?: TimestampUtils.nowIsoUtc()

        val createdUser = User(
          id = userDto.id,
          username = cleanUsername,
          displayName = displayName,
          firstName = cleanFirst,
          lastName = cleanLast,
          email = email.trim().lowercase(),
          phone = normalizedPhone,
          isEmailVerified = emailVerifiedNow,
          isPhoneVerified = phoneVerifiedNow,
          dateOfBirth = dateOfBirth.trim(),
          age = age,
          isUnder18 = isUnder18,
          role = UserRole.USER,
          createdAtIso = createdIso,
          joinedDate = TimestampUtils.formatJoinedMonthYear(createdIso),
          isOnline = true,
          isOnboardingCompleted = false
        )

        if (SupabaseConfig.isJwtAccessToken(token)) {
          try {
            val profileDto = SupabaseProfileDto(
              id = userDto.id,
              username = cleanUsername,
              displayName = displayName,
              firstName = cleanFirst,
              lastName = cleanLast,
              email = email.trim().lowercase(),
              phone = normalizedPhone,
              isEmailVerified = emailVerifiedNow,
              isPhoneVerified = phoneVerifiedNow,
              dateOfBirth = dateOfBirth.trim(),
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
            Log.w(TAG, "Profile upsert notice (auth trigger handles): ${e.message}")
          }
        }

        Result.success(AuthSessionPayload(createdUser, token, refresh, expiresIn))
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        Result.failure(Exception(parseErrorMessage(errorBody, "Sign up failed (${response.code()})")))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Sign up exception: ${e.message}", e)
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to complete sign up.")))
    }
  }

  suspend fun signInWithEmail(
    email: String,
    password: String
  ): Result<AuthSessionPayload> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
    }

    try {
      val response = SupabaseNetworkClient.authApi.signIn(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = SupabaseSignInRequest(email = email.trim(), password = password)
      )

      if (response.isSuccessful && response.body() != null) {
        val authBody = response.body()!!
        val token = authBody.accessToken ?: return@withContext Result.failure(
          Exception("No access token returned. Please verify your email address first.")
        )
        val refresh = authBody.refreshToken ?: ""
        val expiresIn = authBody.expiresIn ?: 3600L
        val userDto = authBody.user ?: return@withContext Result.failure(
          Exception("No user account returned from Supabase.")
        )

        var userProfile = fetchUserProfile(userDto.id, token, userDto.email ?: email.trim())
        if (userProfile == null) {
          userProfile = buildFallbackUserFromAuthDto(userDto, email.trim())
          // Ensure public.profiles row exists for accounts created outside standard flow
          try {
            SupabaseNetworkClient.restApi.upsertProfile(
              apiKey = SupabaseConfig.supabaseAnonKey,
              authHeader = "Bearer $token",
              profile = SupabaseProfileDto(
                id = userProfile.id,
                username = userProfile.username,
                displayName = userProfile.displayName,
                firstName = userProfile.firstName,
                lastName = userProfile.lastName,
                email = userProfile.email,
                phone = userProfile.phone,
                dateOfBirth = userProfile.dateOfBirth,
                age = userProfile.age,
                isUnder18 = userProfile.isUnder18,
                role = "USER"
              )
            )
          } catch (_: Exception) {}
        }

        Result.success(AuthSessionPayload(userProfile, token, refresh, expiresIn))
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        Result.failure(Exception(parseErrorMessage(errorBody, "Invalid email or password.")))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Sign in exception: ${e.message}", e)
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to sign in. Check your internet connection.")))
    }
  }

  suspend fun signInWithPhone(
    phone: String,
    password: String
  ): Result<AuthSessionPayload> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase backend is not configured."))
    }
    try {
      val normalized = normalizePhoneE164(phone).ifBlank { phone.trim() }
      val response = SupabaseNetworkClient.authApi.signInWithPhone(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = SupabasePhoneSignInRequest(phone = normalized, password = password)
      )
      if (response.isSuccessful && response.body() != null) {
        val authBody = response.body()!!
        val token = authBody.accessToken ?: return@withContext Result.failure(
          Exception("No access token returned. Please verify your phone/email first.")
        )
        val refresh = authBody.refreshToken ?: ""
        val expiresIn = authBody.expiresIn ?: 3600L
        val userDto = authBody.user ?: return@withContext Result.failure(
          Exception("No user account returned from Supabase.")
        )
        val userProfile = fetchUserProfile(userDto.id, token, userDto.email.orEmpty())
          ?: buildFallbackUserFromAuthDto(userDto, userDto.email.orEmpty())
        Result.success(AuthSessionPayload(userProfile, token, refresh, expiresIn))
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        Result.failure(Exception(parseErrorMessage(errorBody, "Invalid phone number or password.")))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to sign in with phone.")))
    }
  }

  suspend fun resendEmailOtp(email: String, type: String = "signup"): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
      return@withContext Result.failure(IllegalArgumentException("Enter a valid email address."))
    }
    try {
      val resp = SupabaseNetworkClient.authApi.resendOtp(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = SupabaseResendOtpRequest(type = type, email = cleanEmail)
      )
      if (resp.isSuccessful) {
        Result.success("Verification code resent to $cleanEmail.")
      } else {
        // Fallback to /auth/v1/otp if account was already confirmed or created via email OTP
        val otpResp = SupabaseNetworkClient.authApi.sendOtp(
          apiKey = SupabaseConfig.supabaseAnonKey,
          request = SupabaseOtpSendRequest(email = cleanEmail, createUser = false)
        )
        if (otpResp.isSuccessful) {
          Result.success("Verification code sent to $cleanEmail.")
        } else {
          val err = parseErrorMessage(resp.errorBody()?.string().orEmpty(), "Please wait before requesting another email code.")
          Result.failure(Exception(err))
        }
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to resend email verification code.")))
    }
  }

  suspend fun sendPhoneVerificationOtp(
    phone: String,
    accessToken: String? = null
  ): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    val normalized = normalizePhoneE164(phone)
    if (normalized.length < 8) {
      return@withContext Result.failure(IllegalArgumentException("Enter a valid phone number with country code (e.g. +14155550123)."))
    }
    try {
      val validJwt = resolveValidAccessToken(accessToken)
      if (!validJwt.isNullOrBlank()) {
        // Attach phone to authenticated account & trigger Supabase Phone Change / SMS OTP
        val updateResp = SupabaseNetworkClient.authApi.updateUser(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = "Bearer $validJwt",
          request = SupabaseUpdateUserRequest(phone = normalized)
        )
        if (updateResp.isSuccessful) {
          return@withContext Result.success("SMS verification code sent to $normalized.")
        }
        val updateErr = updateResp.errorBody()?.string().orEmpty()
        if (updateErr.contains("sms_send_failed", true) ||
          updateErr.contains("Phone provider", true) ||
          updateErr.contains("Unsupported phone provider", true) ||
          updateErr.contains("phone_provider_disabled", true)
        ) {
          return@withContext Result.failure(
            Exception("SMS OTP requires enabling the Phone provider (Twilio/MessageBird/Vonage) in your Supabase Auth settings.")
          )
        }
      }

      val otpResp = SupabaseNetworkClient.authApi.sendOtp(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = SupabaseOtpSendRequest(phone = normalized, createUser = false)
      )
      if (otpResp.isSuccessful) {
        Result.success("SMS verification code sent to $normalized.")
      } else {
        val rawErr = otpResp.errorBody()?.string().orEmpty()
        val msg = if (rawErr.contains("Phone", true) || rawErr.contains("sms", true) || otpResp.code() == 422 || otpResp.code() == 400) {
          parseErrorMessage(
            rawErr,
            "SMS OTP could not be dispatched. Verify Phone Auth & SMS provider (Twilio/Vonage) are enabled in Supabase Auth."
          )
        } else {
          parseErrorMessage(rawErr, "Unable to send SMS code (${otpResp.code()}).")
        }
        Result.failure(Exception(msg))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error while requesting SMS verification code.")))
    }
  }

  suspend fun verifyPhoneOtp(
    phone: String,
    token: String,
    userId: String? = null,
    accessToken: String? = null
  ): Result<AuthSessionPayload?> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    val normalized = normalizePhoneE164(phone)
    val cleanCode = token.trim()
    if (cleanCode.length < 4) {
      return@withContext Result.failure(IllegalArgumentException("Enter the 6-digit SMS verification code."))
    }
    try {
      // Try `phone_change` first if authenticated session updated phone, then fallback to `sms`
      var verifyResp = SupabaseNetworkClient.authApi.verifyOtp(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = SupabaseVerifyOtpRequest(
          type = "phone_change",
          token = cleanCode,
          phone = normalized
        )
      )
      if (!verifyResp.isSuccessful) {
        verifyResp = SupabaseNetworkClient.authApi.verifyOtp(
          apiKey = SupabaseConfig.supabaseAnonKey,
          request = SupabaseVerifyOtpRequest(
            type = "sms",
            token = cleanCode,
            phone = normalized
          )
        )
      }

      if (verifyResp.isSuccessful) {
        val body = verifyResp.body()
        val jwt = body?.accessToken ?: resolveValidAccessToken(accessToken).orEmpty()
        val uid = body?.user?.id ?: userId.orEmpty()
        if (uid.isNotBlank() && jwt.isNotBlank()) {
          try {
            SupabaseNetworkClient.restApi.patchProfile(
              apiKey = SupabaseConfig.supabaseAnonKey,
              authHeader = "Bearer $jwt",
              idFilter = "eq.$uid",
              patch = SupabaseProfilePatchDto(phone = normalized, isPhoneVerified = true)
            )
          } catch (_: Exception) {}
        }
        if (body?.user != null && !body.accessToken.isNullOrBlank()) {
          val profile = fetchUserProfile(body.user.id, body.accessToken, body.user.email.orEmpty())
            ?.copy(isPhoneVerified = true, phone = normalized)
            ?: buildFallbackUserFromAuthDto(body.user, body.user.email.orEmpty()).copy(isPhoneVerified = true, phone = normalized)
          return@withContext Result.success(
            AuthSessionPayload(
              user = profile,
              accessToken = body.accessToken,
              refreshToken = body.refreshToken.orEmpty(),
              expiresInSeconds = body.expiresIn ?: 3600L
            )
          )
        }
        return@withContext Result.success(null)
      } else {
        val err = parseErrorMessage(verifyResp.errorBody()?.string().orEmpty(), "Invalid or expired SMS verification code.")
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error while verifying SMS code.")))
    }
  }

  suspend fun verifyOtp(
    email: String,
    token: String,
    type: String = "signup"
  ): Result<AuthSessionPayload> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    }
    try {
      val cleanEmail = email.trim().lowercase().ifBlank { null }
      var response = SupabaseNetworkClient.authApi.verifyOtp(
        apiKey = SupabaseConfig.supabaseAnonKey,
        request = SupabaseVerifyOtpRequest(
          type = type,
          token = token.trim(),
          email = cleanEmail
        )
      )
      if (!response.isSuccessful && type == "signup") {
        // Fallback to `email` OTP type if user requested resend via /otp
        response = SupabaseNetworkClient.authApi.verifyOtp(
          apiKey = SupabaseConfig.supabaseAnonKey,
          request = SupabaseVerifyOtpRequest(
            type = "email",
            token = token.trim(),
            email = cleanEmail
          )
        )
      }
      if (response.isSuccessful && response.body() != null) {
        val authBody = response.body()!!
        val accessToken = authBody.accessToken ?: return@withContext Result.failure(
          Exception("Verification succeeded, but session token was empty.")
        )
        val refresh = authBody.refreshToken ?: ""
        val expiresIn = authBody.expiresIn ?: 3600L
        val userDto = authBody.user ?: return@withContext Result.failure(
          Exception("Verification succeeded, but user account details were missing.")
        )

        try {
          SupabaseNetworkClient.restApi.patchProfile(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = "Bearer $accessToken",
            idFilter = "eq.${userDto.id}",
            patch = SupabaseProfilePatchDto(isEmailVerified = true)
          )
        } catch (_: Exception) {}

        val userProfile = (fetchUserProfile(userDto.id, accessToken, userDto.email ?: email.trim())
          ?: buildFallbackUserFromAuthDto(userDto, email.trim())).copy(isEmailVerified = true)

        Result.success(AuthSessionPayload(userProfile, accessToken, refresh, expiresIn))
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        Result.failure(Exception(parseErrorMessage(errorBody, "Invalid or expired verification code.")))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error while verifying token.")))
    }
  }

  /**
   * Step 1 & 2 of Forgot Password:
   * Requests Supabase Auth to send a secure password-recovery email link to `vybe://reset-password`.
   */
  suspend fun sendPasswordReset(email: String): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    }
    val cleanEmail = email.trim()
    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
      return@withContext Result.failure(IllegalArgumentException("Please enter a valid registered email address."))
    }
    try {
      val response = SupabaseNetworkClient.authApi.recoverPassword(
        apiKey = SupabaseConfig.supabaseAnonKey,
        redirectTo = "vybe://reset-password",
        request = SupabaseRecoverPasswordRequest(email = cleanEmail)
      )
      if (response.isSuccessful) {
        Result.success("Recovery link sent to $cleanEmail. Open the link in your email to securely reset your password.")
      } else {
        val errorBody = response.errorBody()?.string() ?: ""
        Result.failure(Exception(parseErrorMessage(errorBody, "Unable to send password recovery email.")))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error while requesting password recovery.")))
    }
  }

  /**
   * Step 4 & 5 of Forgot Password:
   * Securely validates a recovery deep link's `access_token` OR `token_hash` / `token` against Supabase Auth
   * before allowing password entry. Returns `(validatedAccessToken, verifiedEmail)`.
   */
  suspend fun validateRecoverySession(
    accessTokenFromLink: String? = null,
    tokenHashFromLink: String? = null,
    tokenFromInput: String? = null,
    emailHint: String? = null
  ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    }
    try {
      // Path A: Deep link already carries #access_token=...&type=recovery -> validate live against /auth/v1/user
      if (!accessTokenFromLink.isNullOrBlank() && SupabaseConfig.isJwtAccessToken(accessTokenFromLink)) {
        val cleanJwt = accessTokenFromLink.trim()
        val userResp = SupabaseNetworkClient.authApi.getUser(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = "Bearer $cleanJwt"
        )
        if (userResp.isSuccessful && userResp.body() != null) {
          val verifiedUser = userResp.body()!!
          return@withContext Result.success(Pair(cleanJwt, verifiedUser.email ?: emailHint.orEmpty()))
        } else {
          val err = parseErrorMessage(
            userResp.errorBody()?.string().orEmpty(),
            "This password recovery link has expired or is invalid. Please request a new link."
          )
          return@withContext Result.failure(Exception(err))
        }
      }

      // Path B: PKCE / token_hash link (?token_hash=...&type=recovery)
      if (!tokenHashFromLink.isNullOrBlank()) {
        val verifyResp = SupabaseNetworkClient.authApi.verifyOtp(
          apiKey = SupabaseConfig.supabaseAnonKey,
          request = SupabaseVerifyOtpRequest(
            type = "recovery",
            tokenHash = tokenHashFromLink.trim()
          )
        )
        if (verifyResp.isSuccessful && !verifyResp.body()?.accessToken.isNullOrBlank()) {
          val body = verifyResp.body()!!
          return@withContext Result.success(Pair(body.accessToken!!, body.user?.email ?: emailHint.orEmpty()))
        } else {
          val err = parseErrorMessage(
            verifyResp.errorBody()?.string().orEmpty(),
            "This password recovery link has expired or has already been used."
          )
          return@withContext Result.failure(Exception(err))
        }
      }

      // Path C: Explicit token + email verification (when Supabase email template includes a recovery token)
      if (!tokenFromInput.isNullOrBlank()) {
        val verifyReq = if (emailHint.isNullOrBlank()) {
          SupabaseVerifyOtpRequest(type = "recovery", tokenHash = tokenFromInput.trim())
        } else {
          SupabaseVerifyOtpRequest(type = "recovery", token = tokenFromInput.trim(), email = emailHint.trim())
        }
        val verifyResp = SupabaseNetworkClient.authApi.verifyOtp(
          apiKey = SupabaseConfig.supabaseAnonKey,
          request = verifyReq
        )
        if (verifyResp.isSuccessful && !verifyResp.body()?.accessToken.isNullOrBlank()) {
          val body = verifyResp.body()!!
          return@withContext Result.success(Pair(body.accessToken!!, body.user?.email ?: emailHint.orEmpty()))
        } else {
          val err = parseErrorMessage(
            verifyResp.errorBody()?.string().orEmpty(),
            "Invalid or expired recovery token. Please open the latest recovery email link."
          )
          return@withContext Result.failure(Exception(err))
        }
      }

      Result.failure(Exception("Missing recovery token. Please open the password reset link from your email."))
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network failure while validating recovery session.")))
    }
  }

  /**
   * Step 8 & 9 of Forgot Password:
   * Updates the password using the validated recovery token and immediately revokes the recovery session
   * so the user returns safely to the Login screen.
   */
  suspend fun completePasswordRecovery(
    validatedRecoveryToken: String,
    newPassword: String
  ): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    }
    if (!SupabaseConfig.isJwtAccessToken(validatedRecoveryToken)) {
      return@withContext Result.failure(Exception("Recovery session is invalid or expired. Please request a new reset link."))
    }
    try {
      val updateResp = SupabaseNetworkClient.authApi.updateUser(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer ${validatedRecoveryToken.trim()}",
        request = SupabaseUpdateUserRequest(password = newPassword)
      )
      if (updateResp.isSuccessful) {
        // Safely terminate the recovery session on Supabase Auth
        try {
          SupabaseNetworkClient.authApi.logout(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = "Bearer ${validatedRecoveryToken.trim()}"
          )
        } catch (_: Exception) {}
        Result.success("Your password has been updated. Please sign in with your new password.")
      } else {
        val rawErr = updateResp.errorBody()?.string().orEmpty()
        val msg = when (updateResp.code()) {
          401, 403 -> "Your recovery session expired. Please request a new password reset email."
          422 -> parseErrorMessage(rawErr, "New password must be different and meet security requirements.")
          else -> parseErrorMessage(rawErr, "Failed to update password (${updateResp.code()}).")
        }
        Result.failure(Exception(msg))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network failure while updating password.")))
    }
  }

  suspend fun signOut(accessToken: String): Boolean = withContext(Dispatchers.IO) {
    if (!isConfigured || !SupabaseConfig.isJwtAccessToken(accessToken)) return@withContext true
    try {
      val response = SupabaseNetworkClient.authApi.logout(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = "Bearer $accessToken"
      )
      response.isSuccessful
    } catch (e: Exception) {
      Log.w(TAG, "Sign out network call warning: ${e.message}")
      true
    }
  }

  // =========================================================================
  // 2. PROFILES, PRIVACY & SOCIAL GRAPH
  // =========================================================================

  private fun sanitizeUsername(raw: String?, userId: String, fallbackEmail: String? = null): String {
    val cleaned = raw?.trim()?.lowercase()?.removePrefix("@").orEmpty()
    if (cleaned.isNotBlank() && cleaned != "null" && cleaned != "undefined") {
      return cleaned
    }
    val emailPrefix = fallbackEmail?.substringBefore("@")?.lowercase()
      ?.replace(Regex("[^a-z0-9_.]"), "")
      ?.takeIf { it.length >= 3 }
    if (emailPrefix != null) return emailPrefix
    val suffix = userId.replace("-", "").take(6).lowercase().ifBlank { "member" }
    return "vyber_$suffix"
  }

  private fun buildFallbackUserFromAuthDto(userDto: SupabaseUserDto, fallbackEmail: String): User {
    val meta = userDto.userMetadata ?: emptyMap()
    val rawUsername = meta["username"] as? String
    val safeUsername = sanitizeUsername(rawUsername, userDto.id, userDto.email ?: fallbackEmail)
    val firstName = (meta["first_name"] as? String)?.takeIf { it != "null" } ?: ""
    val lastName = (meta["last_name"] as? String)?.takeIf { it != "null" } ?: ""
    val rawDisplay = (meta["display_name"] as? String)?.takeIf { it.isNotBlank() && it != "null" }
    val displayName = rawDisplay ?: "$firstName $lastName".trim().ifBlank { safeUsername }
    val phone = (meta["phone"] as? String)?.takeIf { it != "null" } ?: userDto.phone ?: ""
    val dob = (meta["date_of_birth"] as? String)?.takeIf { it.isNotBlank() && it != "null" } ?: ""
    val age = calculateAge(dob)
    val createdIso = userDto.createdAt
    return User(
      id = userDto.id,
      username = safeUsername,
      displayName = displayName,
      firstName = firstName,
      lastName = lastName,
      email = userDto.email ?: fallbackEmail,
      phone = phone,
      isEmailVerified = !userDto.emailConfirmedAt.isNullOrBlank(),
      isPhoneVerified = !userDto.phoneConfirmedAt.isNullOrBlank(),
      dateOfBirth = dob,
      age = age,
      isUnder18 = age in 16..17,
      role = UserRole.USER,
      createdAtIso = createdIso,
      joinedDate = TimestampUtils.formatJoinedMonthYear(createdIso),
      isOnline = true,
      isOnboardingCompleted = true
    )
  }

  fun mapProfileDtoToUser(p: SupabaseProfileDto, fallbackEmail: String? = null): User {
    val rawRole = (p.role ?: "USER").trim().uppercase()
    val parsedRole = try {
      UserRole.valueOf(rawRole)
    } catch (_: Exception) {
      UserRole.USER
    }
    val isAdmin = parsedRole == UserRole.ADMIN
    val safeUsername = sanitizeUsername(p.username, p.id, p.email ?: fallbackEmail)
    val safeFirst = p.firstName?.takeIf { it != "null" }?.trim().orEmpty()
    val safeLast = p.lastName?.takeIf { it != "null" }?.trim().orEmpty()
    val safeDisplay = p.displayName
      ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
      ?.trim()
      ?: "$safeFirst $safeLast".trim().ifBlank { safeUsername }

    val parsedPulse = if (!p.livePulse.isNullOrBlank()) {
      try { LivePulseStatus.valueOf(p.livePulse.trim().uppercase()) } catch (_: Exception) { LivePulseStatus.NONE }
    } else {
      LivePulseStatus.NONE
    }
    // Online presence is based on authoritative recent heartbeat activity (last 90 seconds),
    // ensuring signed-out or inactive users immediately appear offline.
    val isSeenRecently = TimestampUtils.isRecentlyOnline(p.lastSeenAt, thresholdSeconds = 90L)
    val onlineNow = isSeenRecently

    return User(
      id = p.id,
      username = safeUsername,
      displayName = safeDisplay,
      firstName = safeFirst,
      lastName = safeLast,
      email = p.email?.takeIf { it != "null" } ?: fallbackEmail ?: "",
      phone = p.phone?.takeIf { it != "null" } ?: "",
      isEmailVerified = p.isEmailVerified ?: false,
      isPhoneVerified = p.isPhoneVerified ?: false,
      bio = p.bio?.takeIf { it != "null" } ?: "",
      avatarUrl = p.avatarUrl?.takeIf { it != "null" } ?: "",
      coverUrl = p.coverUrl?.takeIf { it != "null" } ?: "",
      dateOfBirth = p.dateOfBirth?.takeIf { it != "null" } ?: "",
      age = p.age ?: 20,
      isUnder18 = p.isUnder18 ?: false,
      role = parsedRole,
      isVerified = if (isAdmin) true else (p.isVerified ?: false),
      isVip = if (isAdmin) true else (p.isVip ?: false),
      isPrivate = p.isPrivateAccount ?: false,
      messagePrivacy = p.messagePrivacy ?: "EVERYONE",
      livePulse = parsedPulse,
      isOnline = onlineNow,
      lastSeenAtIso = p.lastSeenAt,
      createdAtIso = p.createdAt,
      joinedDate = TimestampUtils.formatJoinedMonthYear(p.createdAt),
      followersCount = (p.followersCount ?: 0).coerceAtLeast(0),
      followingCount = (p.followingCount ?: 0).coerceAtLeast(0),
      postsCount = (p.postsCount ?: 0).coerceAtLeast(0),
      isOnboardingCompleted = true
    )
  }

  suspend fun fetchUserProfile(userId: String, token: String? = null, email: String? = null): User? = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext null
    val authHeader = authHeaderFor(token)

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

  suspend fun updateUserProfile(
    userId: String,
    firstName: String,
    lastName: String,
    displayName: String,
    bio: String,
    avatarUrl: String,
    coverUrl: String,
    username: String? = null,
    email: String? = null,
    phone: String? = null,
    isPrivate: Boolean? = null,
    messagePrivacy: String? = null,
    accessToken: String = ""
  ): Result<User?> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(null)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required to edit profile."))
    try {
      val patch = SupabaseProfilePatchDto(
        username = username?.trim()?.lowercase()?.removePrefix("@")?.takeIf { it.isNotBlank() && it != "null" },
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        bio = bio,
        avatarUrl = avatarUrl,
        coverUrl = coverUrl,
        email = email?.trim()?.takeIf { it.isNotBlank() },
        phone = phone?.trim()?.takeIf { it.isNotBlank() },
        isPrivateAccount = isPrivate,
        messagePrivacy = messagePrivacy
      )
      var resp = SupabaseNetworkClient.restApi.patchProfile(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$userId",
        patch = patch
      )
      // Fallback if newer columns are not yet migrated in live DB
      if (!resp.isSuccessful && resp.code() == 400) {
        resp = SupabaseNetworkClient.restApi.patchProfile(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          idFilter = "eq.$userId",
          patch = SupabaseProfilePatchDto(
            username = patch.username,
            displayName = displayName,
            firstName = firstName,
            lastName = lastName,
            bio = bio,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            isPrivateAccount = isPrivate
          )
        )
      }
      if (resp.isSuccessful) {
        val updated = resp.body()?.firstOrNull()?.let { mapProfileDtoToUser(it) }
        Result.success(updated)
      } else {
        val err = parseErrorMessage(resp.errorBody()?.string().orEmpty(), "Failed to update profile.")
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Failed to save profile changes.")))
    }
  }

  suspend fun updateProfilePrivacy(
    userId: String,
    isPrivate: Boolean,
    messagePrivacy: String? = null,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      var resp = SupabaseNetworkClient.restApi.patchProfile(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$userId",
        patch = SupabaseProfilePatchDto(
          isPrivateAccount = isPrivate,
          messagePrivacy = messagePrivacy
        )
      )
      if (!resp.isSuccessful && resp.code() == 400 && messagePrivacy != null) {
        resp = SupabaseNetworkClient.restApi.patchProfile(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          idFilter = "eq.$userId",
          patch = SupabaseProfilePatchDto(isPrivateAccount = isPrivate)
        )
      }
      if (resp.isSuccessful) Result.success(Unit)
      else Result.failure(Exception("Failed to update privacy settings (${resp.code()})."))
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to update privacy settings.")))
    }
  }

  suspend fun checkUsernameAvailable(username: String): Boolean = withContext(Dispatchers.IO) {
    val clean = username.trim().lowercase().removePrefix("@")
    if (clean.length < 3 || clean == "null" || clean == "admin" || clean == "vybe" || clean == "support") {
      return@withContext false
    }
    if (!isConfigured) return@withContext true
    val authHeader = authHeaderFor()
    try {
      val rpcResp = SupabaseNetworkClient.restApi.checkUsernameAvailableRpc(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        request = SupabaseRpcCheckUsernameRequest(username = clean)
      )
      if (rpcResp.isSuccessful && rpcResp.body() != null) {
        return@withContext rpcResp.body()!!
      }
    } catch (_: Exception) {}

    try {
      val resp = SupabaseNetworkClient.restApi.getProfileByUsername(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        usernameFilter = "eq.$clean"
      )
      if (resp.isSuccessful) {
        (resp.body() ?: emptyList()).isEmpty()
      } else {
        true
      }
    } catch (_: Exception) {
      true
    }
  }

  /**
   * Resolves Email, Phone Number, or @Username / VYBE ID via trusted server-side SECURITY DEFINER RPC
   * (`resolve_login_identifier`) first so client code never scrapes user emails/phones.
   */
  suspend fun resolveIdentifierSecurely(identifier: String): SupabaseResolvedIdentifierDto = withContext(Dispatchers.IO) {
    val trimmed = identifier.trim()
    if (trimmed.isBlank()) return@withContext SupabaseResolvedIdentifierDto(found = false)
    if (trimmed.contains("@") && trimmed.indexOf('@') > 0 && trimmed.contains(".")) {
      return@withContext SupabaseResolvedIdentifierDto(
        found = true,
        mode = "EMAIL",
        email = trimmed.lowercase()
      )
    }

    if (!isConfigured) {
      return@withContext SupabaseResolvedIdentifierDto(found = false)
    }

    val authHeader = authHeaderFor()
    try {
      val rpcResp = SupabaseNetworkClient.restApi.resolveLoginIdentifier(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        request = SupabaseRpcResolveIdentifierRequest(identifier = trimmed)
      )
      if (rpcResp.isSuccessful && rpcResp.body() != null) {
        val dto = rpcResp.body()!!
        if (dto.found) return@withContext dto
      }
    } catch (e: Exception) {
      Log.w(TAG, "resolveLoginIdentifier RPC notice: ${e.message}")
    }

    // Safe fallback if migration 0005 RPC is not yet deployed on target Supabase project
    val clean = trimmed.lowercase().removePrefix("@").removePrefix("vybe:")
    val phoneClean = trimmed.filter { it.isDigit() }
    if (phoneClean.length >= 7 && trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }) {
      val normalized = normalizePhoneE164(trimmed)
      try {
        val phoneResp = SupabaseNetworkClient.restApi.getProfileByPhone(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          phoneFilter = "eq.$normalized"
        )
        if (phoneResp.isSuccessful && !phoneResp.body().isNullOrEmpty()) {
          val p = phoneResp.body()!!.first()
          return@withContext SupabaseResolvedIdentifierDto(
            found = true,
            mode = if (!p.email.isNullOrBlank()) "EMAIL" else "PHONE",
            email = p.email,
            phone = p.phone ?: normalized
          )
        }
      } catch (_: Exception) {}
      return@withContext SupabaseResolvedIdentifierDto(
        found = true,
        mode = "PHONE",
        phone = normalized
      )
    }

    try {
      val resp = SupabaseNetworkClient.restApi.getProfileByUsername(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        usernameFilter = "eq.$clean"
      )
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        val p = resp.body()!!.first()
        if (!p.email.isNullOrBlank()) {
          return@withContext SupabaseResolvedIdentifierDto(found = true, mode = "EMAIL", email = p.email, phone = p.phone)
        }
      }
    } catch (_: Exception) {}

    SupabaseResolvedIdentifierDto(found = false)
  }

  suspend fun resolveEmailForIdentifier(identifier: String): String? {
    val resolved = resolveIdentifierSecurely(identifier)
    return resolved.email?.takeIf { it.isNotBlank() }
  }

  suspend fun fetchAllProfiles(accessToken: String? = null): List<User> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext emptyList()
    try {
      val resp = SupabaseNetworkClient.restApi.getProfiles(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken)
      )
      if (resp.isSuccessful && resp.body() != null) {
        return@withContext resp.body()!!.map { mapProfileDtoToUser(it) }
      } else {
        Log.w(TAG, "fetchAllProfiles HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
      }
    } catch (e: Exception) {
      Log.w(TAG, "fetchAllProfiles exception: ${e.message}")
    }
    emptyList()
  }

  suspend fun searchProfilesRemote(query: String, accessToken: String? = null): List<User> = withContext(Dispatchers.IO) {
    if (!isConfigured || query.isBlank()) return@withContext emptyList()
    val q = query.trim().removePrefix("@").replace(",", "")
    if (q.isBlank()) return@withContext emptyList()
    try {
      val orFilter = "(username.ilike.*$q*,display_name.ilike.*$q*)"
      val resp = SupabaseNetworkClient.restApi.searchProfiles(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken),
        orFilter = orFilter
      )
      if (resp.isSuccessful && resp.body() != null) {
        return@withContext resp.body()!!.map { mapProfileDtoToUser(it) }
      }
    } catch (e: Exception) {
      Log.w(TAG, "searchProfilesRemote exception: ${e.message}")
    }
    emptyList()
  }

  suspend fun fetchMyOutgoingFollows(userId: String, accessToken: String? = null): List<SupabaseFollowDto> = withContext(Dispatchers.IO) {
    if (!isConfigured || userId.isBlank()) return@withContext emptyList()
    try {
      val resp = SupabaseNetworkClient.restApi.getFollowsByFollower(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken),
        followerIdFilter = "eq.$userId"
      )
      if (resp.isSuccessful) return@withContext resp.body() ?: emptyList()
    } catch (e: Exception) {
      Log.w(TAG, "fetchMyOutgoingFollows warning: ${e.message}")
    }
    emptyList()
  }

  suspend fun fetchMyIncomingFollows(userId: String, accessToken: String? = null): List<SupabaseFollowDto> = withContext(Dispatchers.IO) {
    if (!isConfigured || userId.isBlank()) return@withContext emptyList()
    try {
      val resp = SupabaseNetworkClient.restApi.getFollowsByFollowed(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken),
        followedIdFilter = "eq.$userId"
      )
      if (resp.isSuccessful) return@withContext resp.body() ?: emptyList()
    } catch (e: Exception) {
      Log.w(TAG, "fetchMyIncomingFollows warning: ${e.message}")
    }
    emptyList()
  }

  suspend fun followUser(
    followerId: String,
    followedId: String,
    isTargetPrivate: Boolean = false,
    accessToken: String = ""
  ): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success("ACCEPTED")
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Please sign in to follow users."))
    val desiredStatus = if (isTargetPrivate) "PENDING" else "ACCEPTED"
    try {
      var resp = SupabaseNetworkClient.restApi.insertFollow(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        body = SupabaseFollowDto(followerId = followerId, followedId = followedId, status = desiredStatus)
      )
      if (!resp.isSuccessful && resp.code() == 400) {
        resp = SupabaseNetworkClient.restApi.insertFollow(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          body = SupabaseFollowDto(followerId = followerId, followedId = followedId, status = null)
        )
      }
      if (resp.isSuccessful) {
        val actualStatus = resp.body()?.firstOrNull()?.status ?: desiredStatus
        Result.success(actualStatus)
      } else {
        Result.failure(Exception(parseErrorMessage(resp.errorBody()?.string().orEmpty(), "Unable to follow user.")))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error while following user.")))
    }
  }

  suspend fun respondToFollowRequest(
    requesterId: String,
    myUserId: String,
    approve: Boolean,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      if (approve) {
        val resp = SupabaseNetworkClient.restApi.updateFollowStatus(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          followerFilter = "eq.$requesterId",
          followedFilter = "eq.$myUserId",
          body = mapOf("status" to "ACCEPTED")
        )
        if (resp.isSuccessful) Result.success(Unit)
        else Result.failure(Exception("Failed to approve follow request."))
      } else {
        val resp = SupabaseNetworkClient.restApi.deleteFollow(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          followerFilter = "eq.$requesterId",
          followedFilter = "eq.$myUserId"
        )
        if (resp.isSuccessful) Result.success(Unit)
        else Result.failure(Exception("Failed to decline follow request."))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to process follow request.")))
    }
  }

  suspend fun unfollowUser(followerId: String, followedId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      val resp = SupabaseNetworkClient.restApi.deleteFollow(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        followerFilter = "eq.$followerId",
        followedFilter = "eq.$followedId"
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to unfollow user."))
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error while unfollowing.")))
    }
  }

  // =========================================================================
  // 3. BLOCKS, RESTRICTIONS & REPORTS
  // =========================================================================

  suspend fun fetchBlockedUserIds(userId: String, accessToken: String? = null): Set<String> = withContext(Dispatchers.IO) {
    if (!isConfigured || userId.isBlank()) return@withContext emptySet()
    try {
      val resp = SupabaseNetworkClient.restApi.getBlocks(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken),
        blockerIdFilter = "eq.$userId"
      )
      if (resp.isSuccessful) {
        return@withContext resp.body().orEmpty().map { it.blockedId }.toSet()
      }
    } catch (_: Exception) {}
    emptySet()
  }

  suspend fun blockUser(blockerId: String, blockedId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      val resp = SupabaseNetworkClient.restApi.insertBlock(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        body = SupabaseBlockDto(blockerId = blockerId, blockedId = blockedId)
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to block user."))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun unblockUser(blockerId: String, blockedId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      val resp = SupabaseNetworkClient.restApi.deleteBlock(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        blockerFilter = "eq.$blockerId",
        blockedFilter = "eq.$blockedId"
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to unblock user."))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun fetchRestrictedUserIds(userId: String, accessToken: String? = null): Set<String> = withContext(Dispatchers.IO) {
    if (!isConfigured || userId.isBlank()) return@withContext emptySet()
    try {
      val resp = SupabaseNetworkClient.restApi.getRestrictions(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken),
        restrictorIdFilter = "eq.$userId"
      )
      if (resp.isSuccessful) {
        return@withContext resp.body().orEmpty().map { it.restrictedId }.toSet()
      }
    } catch (_: Exception) {}
    emptySet()
  }

  suspend fun setUserRestricted(
    restrictorId: String,
    restrictedId: String,
    restricted: Boolean,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.success(Unit)
    try {
      if (restricted) {
        SupabaseNetworkClient.restApi.insertRestriction(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          body = SupabaseRestrictionDto(restrictorId = restrictorId, restrictedId = restrictedId)
        )
      } else {
        SupabaseNetworkClient.restApi.deleteRestriction(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          restrictorFilter = "eq.$restrictorId",
          restrictedFilter = "eq.$restrictedId"
        )
      }
      Result.success(Unit)
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
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required to submit report."))
    try {
      val resp = SupabaseNetworkClient.restApi.submitReport(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        body = SupabaseReportInsertDto(
          reporterId = reporterId,
          targetType = targetType,
          targetId = targetId,
          reason = reason,
          adminNotes = details.ifBlank { null }
        )
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to submit report."))
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to submit report.")))
    }
  }

  // =========================================================================
  // 4. SUPABASE STORAGE UPLOAD, SIGNED URLS & OBJECT DELETION
  // =========================================================================

  /**
   * Uploads binary media to `<bucket>/<userId>/<fileName>` with live byte progress reporting.
   * For restricted/private buckets (`posts`, `stories`, `chat-media`), creates a signed URL when
   * configured as private, falling back cleanly to public object URL if the bucket is public.
   */
  suspend fun uploadMediaWithProgress(
    bucket: String,
    userId: String,
    fileName: String,
    bytes: ByteArray,
    mimeType: String,
    accessToken: String = "",
    onProgress: ((Int) -> Unit)? = null
  ): Result<UploadedStorageMedia> = withContext(Dispatchers.IO) {
    if (!isConfigured) {
      return@withContext Result.failure(IllegalStateException("Supabase backend not configured."))
    }
    val validJwt = resolveValidAccessToken(accessToken)
      ?: return@withContext Result.failure(IllegalStateException("Please sign in again before uploading media."))

    try {
      val cleanBucket = bucket.trim()
      val cleanLeaf = fileName.trim().removePrefix("/")
      val objectPath = if (cleanLeaf.startsWith("$userId/")) cleanLeaf else "$userId/$cleanLeaf"
      val uploadUrl = "${SupabaseConfig.supabaseUrl}/storage/v1/object/$cleanBucket/$objectPath"

      val body = ProgressRequestBody(
        bytes = bytes,
        contentType = mimeType.toMediaTypeOrNull(),
        onProgress = onProgress
      )

      val request = Request.Builder()
        .url(uploadUrl)
        .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
        .addHeader("Authorization", "Bearer $validJwt")
        .addHeader("Content-Type", mimeType)
        .addHeader("x-upsert", "true")
        .post(body)
        .build()

      SupabaseNetworkClient.storageHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val errText = response.body?.string().orEmpty()
          val parsed = parseErrorMessage(errText, "Media upload failed (HTTP ${response.code}).")
          return@withContext Result.failure(Exception(parsed))
        }
      }

      val resolvedUrl = resolveAccessibleMediaUrl(cleanBucket, objectPath, validJwt)
      Result.success(UploadedStorageMedia(url = resolvedUrl, storagePath = "$cleanBucket/$objectPath"))
    } catch (e: Exception) {
      Log.e(TAG, "Storage upload failed: ${e.message}", e)
      Result.failure(Exception(friendlyNetworkMessage(e, "Upload interrupted. Check your internet connection and retry.")))
    }
  }

  /**
   * Backward-compatible wrapper preserving existing signature.
   */
  suspend fun uploadMediaFile(
    bucket: String,
    fileName: String,
    bytes: ByteArray,
    mimeType: String,
    accessToken: String = ""
  ): Result<String> {
    val uid = SessionManager.get()?.userId ?: "shared"
    return uploadMediaWithProgress(
      bucket = bucket,
      userId = uid,
      fileName = fileName,
      bytes = bytes,
      mimeType = mimeType,
      accessToken = accessToken
    ).map { it.url }
  }

  /**
   * Generates a 7-day signed URL for private buckets (`posts`, `stories`, `chat-media`) so restricted media
   * cannot be scraped via unauthenticated public URLs; gracefully falls back to `/object/public/` if bucket is public.
   */
  suspend fun resolveAccessibleMediaUrl(
    bucket: String,
    objectPath: String,
    accessToken: String? = null
  ): String = withContext(Dispatchers.IO) {
    val publicUrl = "${SupabaseConfig.supabaseUrl}/storage/v1/object/public/$bucket/$objectPath"
    if (bucket == "avatars") return@withContext publicUrl

    val cacheKey = "$bucket/$objectPath"
    val now = System.currentTimeMillis()
    val cached = signedUrlCache[cacheKey]
    if (cached != null && cached.second > now) {
      return@withContext cached.first
    }

    val validJwt = resolveValidAccessToken(accessToken) ?: return@withContext publicUrl
    try {
      val signEndpoint = "${SupabaseConfig.supabaseUrl}/storage/v1/object/sign/$bucket/$objectPath"
      val reqBody = """{"expiresIn":604800}""".toRequestBody("application/json".toMediaTypeOrNull())
      val req = Request.Builder()
        .url(signEndpoint)
        .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
        .addHeader("Authorization", "Bearer $validJwt")
        .post(reqBody)
        .build()

      SupabaseNetworkClient.okHttpClient.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) {
          val json = JSONObject(resp.body?.string().orEmpty())
          val signedPath = json.optString("signedURL", "").ifBlank { json.optString("signedUrl", "") }
          if (signedPath.isNotBlank()) {
            val fullSigned = if (signedPath.startsWith("http")) {
              signedPath
            } else if (signedPath.startsWith("/storage/v1")) {
              "${SupabaseConfig.supabaseUrl}$signedPath"
            } else {
              "${SupabaseConfig.supabaseUrl}/storage/v1$signedPath"
            }
            // Cache signed URL for 6 hours to keep feed & story loads instantaneous
            signedUrlCache[cacheKey] = Pair(fullSigned, now + 6L * 3_600_000L)
            return@withContext fullSigned
          }
        }
      }
    } catch (_: Exception) {}
    publicUrl
  }

  /**
   * Deletes an uploaded Storage object (`<bucket>/<objectPath>`) or parses it directly from a Supabase Storage URL.
   */
  suspend fun deleteStorageObject(
    storagePathOrUrl: String,
    accessToken: String = ""
  ): Boolean = withContext(Dispatchers.IO) {
    if (!isConfigured || storagePathOrUrl.isBlank()) return@withContext false
    val validJwt = resolveValidAccessToken(accessToken) ?: return@withContext false

    val parsedPair: Pair<String, String>? = extractBucketAndObjectPath(storagePathOrUrl)
    if (parsedPair == null) return@withContext false

    val (bucket, objectPath) = parsedPair
    try {
      val deleteUrl = "${SupabaseConfig.supabaseUrl}/storage/v1/object/$bucket/$objectPath"
      val req = Request.Builder()
        .url(deleteUrl)
        .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
        .addHeader("Authorization", "Bearer $validJwt")
        .delete()
        .build()
      SupabaseNetworkClient.okHttpClient.newCall(req).execute().use { resp ->
        resp.isSuccessful
      }
    } catch (e: Exception) {
      Log.w(TAG, "Storage object delete warning: ${e.message}")
      false
    }
  }

  private fun extractBucketAndObjectPath(storagePathOrUrl: String): Pair<String, String>? {
    val clean = storagePathOrUrl.substringBefore("?").trim()
    val markers = listOf(
      "/storage/v1/object/public/",
      "/storage/v1/object/sign/",
      "/storage/v1/object/authenticated/",
      "/storage/v1/object/"
    )
    for (marker in markers) {
      val idx = clean.indexOf(marker)
      if (idx >= 0) {
        val remainder = clean.substring(idx + marker.length)
        val slash = remainder.indexOf('/')
        if (slash > 0 && slash < remainder.length - 1) {
          return Pair(remainder.substring(0, slash), remainder.substring(slash + 1))
        }
      }
    }
    if (!clean.startsWith("http") && clean.contains('/')) {
      val slash = clean.indexOf('/')
      return Pair(clean.substring(0, slash), clean.substring(slash + 1))
    }
    return null
  }

  // =========================================================================
  // 5. POSTS, REELS, REACTIONS, SAVES & COMMENTS
  // =========================================================================

  suspend fun fetchPosts(
    currentUserId: String? = null,
    accessToken: String = "",
    offset: Int = 0,
    limit: Int = 40
  ): Result<List<Post>> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(emptyList())
    try {
      val authHeader = authHeaderFor(accessToken)
      var resp = SupabaseNetworkClient.restApi.getPosts(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        offset = offset,
        limit = limit
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getPosts(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          select = "*,post_media(*)",
          offset = offset,
          limit = limit
        )
      }
      if (!resp.isSuccessful || resp.body() == null) {
        val err = parseErrorMessage(resp.errorBody()?.string().orEmpty(), "Failed to load feed (HTTP ${resp.code()}).")
        return@withContext Result.failure(Exception(err))
      }

      val rawPosts = resp.body().orEmpty()
      val postIds = rawPosts.map { it.id }.filter { it.isNotBlank() }

      val aggregatedReactions = mutableMapOf<String, MutableMap<VybeReaction, Int>>()
      val myReactions = mutableMapOf<String, VybeReaction>()

      if (postIds.isNotEmpty()) {
        try {
          val inFilter = "in.(${postIds.joinToString(",")})"
          val allRResp = SupabaseNetworkClient.restApi.getReactionsForPosts(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            postIdsInFilter = inFilter
          )
          if (allRResp.isSuccessful) {
            // Deduplicate by (postId, userId) in case legacy DB had duplicate rows prior to migration 0009
            val seenPairs = HashSet<String>()
            allRResp.body().orEmpty().forEach { rDto ->
              val pairKey = "${rDto.postId}:${rDto.userId}"
              if (seenPairs.add(pairKey)) {
                val parsed = VybeReaction.fromWire(rDto.reactionType) ?: VybeReaction.PULSE
                val postMap = aggregatedReactions.getOrPut(rDto.postId) { mutableMapOf() }
                postMap[parsed] = (postMap[parsed] ?: 0) + 1
                if (!currentUserId.isNullOrBlank() && rDto.userId == currentUserId) {
                  myReactions[rDto.postId] = parsed
                }
              }
            }
          }
        } catch (_: Exception) {
          // Fallback to user-only reactions below if batch query fails
        }
      }

      if (!currentUserId.isNullOrBlank() && myReactions.isEmpty()) {
        try {
          val rResp = SupabaseNetworkClient.restApi.getMyReactions(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            userIdFilter = "eq.$currentUserId"
          )
          if (rResp.isSuccessful) {
            rResp.body().orEmpty().forEach { dto ->
              val reaction = VybeReaction.fromWire(dto.reactionType) ?: VybeReaction.PULSE
              myReactions[dto.postId] = reaction
            }
          }
        } catch (_: Exception) {}
      }

      val mySavedIds: Set<String> = if (!currentUserId.isNullOrBlank()) {
        try {
          val sResp = SupabaseNetworkClient.restApi.getMySavedPosts(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            userIdFilter = "eq.$currentUserId"
          )
          if (sResp.isSuccessful) sResp.body().orEmpty().map { it.postId }.toSet() else emptySet()
        } catch (_: Exception) { emptySet() }
      } else emptySet()

      val postsList = rawPosts.map { dto ->
        mapPostDtoToModel(dto, myReactions, aggregatedReactions[dto.id], mySavedIds, accessToken)
      }
      Result.success(postsList)
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching posts: ${e.message}", e)
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to refresh feed.")))
    }
  }

  private suspend fun mapPostDtoToModel(
    dto: SupabasePostDto,
    myReactions: Map<String, VybeReaction>,
    liveReactionBreakdown: Map<VybeReaction, Int>?,
    mySavedIds: Set<String>,
    accessToken: String
  ): Post {
    val authorUser = if (dto.profiles != null) {
      mapProfileDtoToUser(dto.profiles)
    } else {
      User(id = dto.authorId, username = "vyber", displayName = "VYBE Member")
    }

    val sortedMedia = dto.postMedia?.sortedBy { it.displayOrder }.orEmpty()
    val knownBuckets = setOf("posts", "stories", "avatars", "chat-media")
    val mediaUrls = sortedMedia.map { media ->
      val sp = media.storagePath?.trim()
      if (!sp.isNullOrBlank() && sp.contains('/')) {
        val firstSeg = sp.substringBefore('/')
        val (bucket, obj) = if (firstSeg in knownBuckets) {
          firstSeg to sp.substringAfter('/')
        } else {
          "posts" to sp
        }
        resolveAccessibleMediaUrl(bucket, obj, accessToken)
      } else {
        val extracted = extractBucketAndObjectPath(media.mediaUrl)
        if (extracted != null) {
          resolveAccessibleMediaUrl(extracted.first, extracted.second, accessToken)
        } else {
          media.mediaUrl
        }
      }
    }
    val storagePaths = sortedMedia.mapNotNull { it.storagePath ?: extractBucketAndObjectPath(it.mediaUrl)?.let { p -> "${p.first}/${p.second}" } }
    val firstRatio = sortedMedia.firstOrNull()?.aspectRatio?.toFloat()

    val mediaType = try {
      val parsed = MediaType.valueOf(dto.mediaType ?: "TEXT")
      val anyVideoUrl = mediaUrls.any {
        it.contains(".mp4", true) || it.contains(".webm", true) || it.contains(".mov", true)
      }
      if (parsed == MediaType.IMAGE && anyVideoUrl) MediaType.VIDEO else parsed
    } catch (_: Exception) {
      if (mediaUrls.any { it.contains(".mp4", true) || it.contains(".webm", true) }) MediaType.VIDEO else MediaType.IMAGE
    }

    val moodZone = if (!dto.moodZone.isNullOrBlank()) {
      try { MoodType.valueOf(dto.moodZone) } catch (_: Exception) { null }
    } else null

    val myReaction = myReactions[dto.id]
    val dtoBreakdown = buildMap<VybeReaction, Int> {
      val p = (dto.pulseCount ?: 0).coerceAtLeast(0)
      val s = (dto.sparkCount ?: 0).coerceAtLeast(0)
      val w = (dto.waveCount ?: 0).coerceAtLeast(0)
      val f = (dto.flameCount ?: 0).coerceAtLeast(0)
      if (p > 0) put(VybeReaction.PULSE, p)
      if (s > 0) put(VybeReaction.SPARK, s)
      if (w > 0) put(VybeReaction.WAVE, w)
      if (f > 0) put(VybeReaction.FLAME, f)
    }
    val rawLikes = (dto.likesCount ?: 0).coerceAtLeast(0)
    val resolvedReactionsCount: Map<VybeReaction, Int> = when {
      !liveReactionBreakdown.isNullOrEmpty() -> liveReactionBreakdown
      dtoBreakdown.isNotEmpty() -> dtoBreakdown
      rawLikes > 0 -> mapOf((myReaction ?: VybeReaction.PULSE) to rawLikes)
      myReaction != null -> mapOf(myReaction to 1)
      else -> emptyMap()
    }
    val resolvedLikesCount = if (!liveReactionBreakdown.isNullOrEmpty()) {
      liveReactionBreakdown.values.sum()
    } else {
      maxOf(rawLikes, resolvedReactionsCount.values.sum())
    }

    return Post(
      id = dto.id,
      author = authorUser,
      caption = dto.caption ?: "",
      mediaUrls = mediaUrls,
      mediaType = mediaType,
      timestamp = TimestampUtils.formatRelative(dto.createdAt, "Recently"),
      createdAtIso = dto.createdAt,
      likesCount = resolvedLikesCount,
      commentsCount = (dto.commentsCount ?: 0).coerceAtLeast(0),
      savesCount = (dto.savesCount ?: 0).coerceAtLeast(0),
      viewsCount = (dto.viewsCount ?: 0).coerceAtLeast(0),
      isLiked = myReaction != null,
      isSaved = mySavedIds.contains(dto.id),
      isArchived = dto.isArchived ?: false,
      isUnfiltered = dto.isUnfiltered ?: false,
      isVybesReel = (dto.isVybesReel ?: false) || mediaType == MediaType.REEL,
      moodZone = moodZone,
      audioTrackTitle = dto.musicTitle,
      locationName = dto.locationName,
      latitude = dto.latitude,
      longitude = dto.longitude,
      mediaAspectRatio = firstRatio,
      storagePaths = storagePaths,
      userReaction = myReaction,
      reactionsCount = resolvedReactionsCount
    )
  }

  /**
   * Idempotent post creation: checks `client_upload_id` first if retrying so retries NEVER create duplicate posts!
   */
  suspend fun createPostOnSupabase(
    post: Post,
    accessToken: String = ""
  ): Result<Post> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(IllegalStateException("Session expired. Please sign in again."))

    try {
      // Deduplication check on retry
      if (!post.clientUploadId.isNullOrBlank()) {
        try {
          val existing = SupabaseNetworkClient.restApi.getPostByClientUploadId(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            clientUploadFilter = "eq.${post.clientUploadId}"
          )
          if (existing.isSuccessful && !existing.body().isNullOrEmpty()) {
            val dto = existing.body()!!.first()
            return@withContext Result.success(post.copy(id = dto.id))
          }
        } catch (_: Exception) {}
      }

      val insertDto = SupabasePostInsertDto(
        authorId = post.author.id,
        caption = post.caption,
        mediaType = post.mediaType.name,
        moodZone = post.moodZone?.name,
        locationName = post.locationName,
        latitude = post.latitude,
        longitude = post.longitude,
        musicTitle = post.audioTrackTitle,
        clientUploadId = post.clientUploadId,
        isUnfiltered = post.isUnfiltered,
        isVybesReel = post.isVybesReel,
        viewsCount = 0
      )

      var postResp = SupabaseNetworkClient.restApi.insertPost(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        post = insertDto
      )

      // Fallback 1: If DB's mood_type_enum hasn't run migration 0005 yet (e.g. GENERAL/MOTIVATIONAL), retry with moodZone = null
      if (!postResp.isSuccessful && postResp.code() == 400 && insertDto.moodZone != null) {
        postResp = SupabaseNetworkClient.restApi.insertPost(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          post = insertDto.copy(moodZone = null)
        )
      }

      // Fallback 2: Backward-compatible fallback if migration 0004 columns aren't applied yet
      if (!postResp.isSuccessful && postResp.code() == 400) {
        postResp = SupabaseNetworkClient.restApi.insertPostLegacy(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          post = SupabasePostLegacyInsertDto(
            authorId = post.author.id,
            caption = post.caption,
            mediaType = post.mediaType.name,
            moodZone = null,
            isUnfiltered = post.isUnfiltered,
            isVybesReel = post.isVybesReel,
            viewsCount = 0
          )
        )
      }

      if (postResp.isSuccessful && !postResp.body().isNullOrEmpty()) {
        val createdRow = postResp.body()!!.first()
        val createdId = createdRow.id
        val createdIso = createdRow.createdAt ?: TimestampUtils.nowIsoUtc()

        if (post.mediaUrls.isNotEmpty()) {
          val mediaTypeStr = if (post.mediaType == MediaType.VIDEO || post.mediaType == MediaType.REEL) "VIDEO" else "IMAGE"
          post.mediaUrls.forEachIndexed { index, url ->
            val sp = post.storagePaths.getOrNull(index)
            val mediaDto = SupabasePostMediaInsertDto(
              postId = createdId,
              mediaUrl = url,
              mediaType = mediaTypeStr,
              displayOrder = index,
              aspectRatio = post.mediaAspectRatio?.toDouble(),
              storagePath = sp
            )
            var mResp = SupabaseNetworkClient.restApi.insertPostMedia(
              apiKey = SupabaseConfig.supabaseAnonKey,
              authHeader = authHeader,
              media = mediaDto
            )
            if (!mResp.isSuccessful && mResp.code() == 400) {
              mResp = SupabaseNetworkClient.restApi.insertPostMediaLegacy(
                apiKey = SupabaseConfig.supabaseAnonKey,
                authHeader = authHeader,
                media = SupabasePostMediaLegacyDto(
                  postId = createdId,
                  mediaUrl = url,
                  mediaType = mediaTypeStr,
                  displayOrder = index
                )
              )
            }
            if (!mResp.isSuccessful) {
              // Roll back partial post row if media record insertion failed!
              try {
                SupabaseNetworkClient.restApi.deletePost(
                  apiKey = SupabaseConfig.supabaseAnonKey,
                  authHeader = authHeader,
                  idFilter = "eq.$createdId"
                )
              } catch (_: Exception) {}
              return@withContext Result.failure(Exception("Failed to attach media metadata (HTTP ${mResp.code()})."))
            }
          }
        }

        Result.success(
          post.copy(
            id = createdId,
            createdAtIso = createdIso,
            timestamp = TimestampUtils.formatRelative(createdIso, "Just now")
          )
        )
      } else {
        val err = parseErrorMessage(postResp.errorBody()?.string().orEmpty(), "Post creation failed (HTTP ${postResp.code()}).")
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed creating post: ${e.message}", e)
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to publish post. Check your connection and retry.")))
    }
  }

  /**
   * Deletes the post database row AND removes all associated Supabase Storage objects.
   */
  suspend fun deletePostOnSupabase(
    postId: String,
    mediaToCleanup: List<String> = emptyList(),
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required to delete post."))
    try {
      val resp = SupabaseNetworkClient.restApi.deletePost(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$postId"
      )
      if (resp.isSuccessful) {
        mediaToCleanup.distinct().forEach { pathOrUrl ->
          deleteStorageObject(pathOrUrl, accessToken)
        }
        Result.success(Unit)
      } else {
        Result.failure(Exception("Failed to delete post (HTTP ${resp.code()})."))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error while deleting post.")))
    }
  }

  suspend fun setPostArchived(
    postId: String,
    archived: Boolean,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      val resp = SupabaseNetworkClient.restApi.patchPost(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$postId",
        body = mapOf("is_archived" to archived)
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to update post archive state."))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun setPostReaction(
    postId: String,
    userId: String,
    reaction: VybeReaction?,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.failure(Exception("Sign in required."))
    try {
      if (reaction == null) {
        val resp = SupabaseNetworkClient.restApi.deleteReaction(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          postIdFilter = "eq.$postId",
          userIdFilter = "eq.$userId"
        )
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to remove reaction."))
      } else {
        var resp = SupabaseNetworkClient.restApi.upsertReaction(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          onConflict = "post_id,user_id",
          reaction = SupabaseReactionDto(postId = postId, userId = userId, reactionType = reaction.name)
        )
        if (!resp.isSuccessful) {
          // Safe delete-then-insert fallback in case legacy DB lacked composite unique index or UPDATE policy
          SupabaseNetworkClient.restApi.deleteReaction(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            postIdFilter = "eq.$postId",
            userIdFilter = "eq.$userId"
          )
          resp = SupabaseNetworkClient.restApi.upsertReaction(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            onConflict = "post_id,user_id",
            reaction = SupabaseReactionDto(postId = postId, userId = userId, reactionType = reaction.name)
          )
        }
        if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to react to post."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun setPostSaved(
    postId: String,
    userId: String,
    save: Boolean,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.failure(Exception("Sign in required."))
    try {
      if (save) {
        SupabaseNetworkClient.restApi.insertSavedPost(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          body = SupabaseSavedPostDto(userId = userId, postId = postId)
        )
      } else {
        SupabaseNetworkClient.restApi.deleteSavedPost(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          userIdFilter = "eq.$userId",
          postIdFilter = "eq.$postId"
        )
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun fetchCommentsForPost(
    postId: String,
    accessToken: String = ""
  ): Result<List<PostComment>> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(emptyList())
    try {
      val resp = SupabaseNetworkClient.restApi.getComments(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken),
        postIdFilter = "eq.$postId"
      )
      if (resp.isSuccessful && resp.body() != null) {
        val mapped = resp.body()!!.map { dto ->
          val author = dto.profiles?.let { mapProfileDtoToUser(it) }
            ?: User(id = dto.authorId, username = "vyber", displayName = "VYBE Member")
          PostComment(
            id = dto.id ?: "c_${System.currentTimeMillis()}",
            postId = dto.postId,
            author = author,
            content = dto.content,
            timestamp = TimestampUtils.formatRelative(dto.createdAt, "Just now"),
            createdAtIso = dto.createdAt
          )
        }
        Result.success(mapped)
      } else {
        Result.failure(Exception("Failed to load comments"))
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
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Please sign in to comment."))
    try {
      val resp = SupabaseNetworkClient.restApi.insertComment(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        comment = SupabaseCommentInsertDto(postId = postId, authorId = authorId, content = content)
      )
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        Result.success(resp.body()!!.first())
      } else {
        Result.failure(Exception(parseErrorMessage(resp.errorBody()?.string().orEmpty(), "Failed to post comment.")))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to post comment.")))
    }
  }

  suspend fun recordPostView(
    postId: String,
    viewerId: String? = null,
    accessToken: String = ""
  ): Result<Int> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(1)
    try {
      val resp = SupabaseNetworkClient.restApi.incrementPostView(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken),
        request = SupabaseRpcIncrementViewRequest(postId = postId, viewerId = viewerId)
      )
      if (resp.isSuccessful && resp.body() != null) {
        Result.success(resp.body()!!)
      } else {
        Result.failure(Exception("View increment skipped"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  // =========================================================================
  // 6. STORIES, PERSISTENT STORY VIEWS & VYBE MATCHES
  // =========================================================================

  suspend fun fetchActiveStories(
    currentUserId: String,
    accessToken: String = ""
  ): Result<List<Story>> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(emptyList())
    val authHeader = authHeaderFor(accessToken)
    try {
      val nowIso = formatIsoUtc(System.currentTimeMillis())
      var resp = SupabaseNetworkClient.restApi.getActiveStories(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        expiresAtFilter = "gt.$nowIso"
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getActiveStories(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          expiresAtFilter = "gt.$nowIso",
          select = "*"
        )
      }
      if (!resp.isSuccessful || resp.body() == null) {
        return@withContext Result.failure(Exception("Unable to load stories (${resp.code()})."))
      }

      val viewedStoryIds: Set<String> = if (currentUserId.isNotBlank()) {
        try {
          val vResp = SupabaseNetworkClient.restApi.getMyStoryViews(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            viewerIdFilter = "eq.$currentUserId"
          )
          if (vResp.isSuccessful) vResp.body().orEmpty().map { it.storyId }.toSet() else emptySet()
        } catch (_: Exception) { emptySet() }
      } else emptySet()

      val stories = resp.body()!!.mapNotNull { dto ->
        val id = dto.id ?: return@mapNotNull null
        val expiresMillis = parseIsoMillis(dto.expiresAt)
        if (expiresMillis != null && expiresMillis <= System.currentTimeMillis()) {
          return@mapNotNull null
        }
        val remainingHours = if (expiresMillis != null) {
          (((expiresMillis - System.currentTimeMillis()) / 3_600_000L).toInt()).coerceIn(1, 24)
        } else 24

        val author = dto.profiles?.let { mapProfileDtoToUser(it) }
          ?: User(id = dto.authorId, username = "vyber", displayName = "VYBE Member")

        val knownStoryBuckets = setOf("stories", "posts", "avatars", "chat-media")
        val resolvedMediaUrl = if (!dto.storagePath.isNullOrBlank() && dto.storagePath.contains('/')) {
          val sp = dto.storagePath.trim()
          val firstSeg = sp.substringBefore('/')
          val (bucket, obj) = if (firstSeg in knownStoryBuckets) {
            firstSeg to sp.substringAfter('/')
          } else {
            "stories" to sp
          }
          resolveAccessibleMediaUrl(bucket, obj, accessToken)
        } else {
          val extracted = extractBucketAndObjectPath(dto.mediaUrl)
          if (extracted != null) {
            resolveAccessibleMediaUrl(extracted.first, extracted.second, accessToken)
          } else {
            dto.mediaUrl
          }
        }

        val mediaType = try {
          StoryMediaType.valueOf(dto.resolvedMediaType)
        } catch (_: Exception) {
          if (resolvedMediaUrl.contains(".mp4", true) || resolvedMediaUrl.contains(".webm", true)) {
            StoryMediaType.VIDEO
          } else StoryMediaType.PHOTO
        }

        val audience = try {
          StoryAudience.valueOf(dto.audience ?: "EVERYONE")
        } catch (_: Exception) {
          StoryAudience.EVERYONE
        }

        Story(
          id = id,
          author = author,
          mediaUrl = resolvedMediaUrl,
          caption = dto.caption ?: "",
          mediaType = mediaType,
          audience = audience,
          musicTrackTitle = dto.musicTitle,
          timestamp = TimestampUtils.formatRelative(dto.createdAt, "Recently"),
          expiresHoursRemaining = remainingHours,
          isViewed = viewedStoryIds.contains(id),
          reactionsCount = 0,
          location = dto.locationName,
          textBackgroundColor = dto.textBgKey,
          createdAtIso = dto.createdAt,
          expiresAtIso = dto.expiresAt,
          storagePath = dto.storagePath,
          clientUploadId = dto.clientUploadId
        )
      }

      Result.success(stories)
    } catch (e: Exception) {
      Log.w(TAG, "fetchActiveStories error: ${e.message}")
      Result.failure(Exception(friendlyNetworkMessage(e, "Failed to sync stories.")))
    }
  }

  suspend fun createStoryOnSupabase(
    story: Story,
    accessToken: String = ""
  ): Result<Story> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase not configured."))
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(IllegalStateException("Please sign in again to share a story."))

    try {
      val expiresIso = formatIsoUtc(System.currentTimeMillis() + 24L * 3_600_000L)
      val audienceStr = when (story.audience) {
        StoryAudience.VYBE_MATCHES, StoryAudience.CLOSE_FRIENDS -> "VYBE_MATCHES"
        else -> "EVERYONE"
      }

      val dto = SupabaseStoryInsertDto(
        authorId = story.author.id,
        mediaUrl = story.mediaUrl.ifBlank { "text://story" },
        mediaType = story.mediaType.name,
        caption = story.caption,
        audience = audienceStr,
        musicTitle = story.musicTrackTitle,
        locationName = story.location,
        textBgKey = story.textBackgroundColor,
        storagePath = story.storagePath,
        clientUploadId = story.clientUploadId,
        expiresAt = expiresIso
      )

      var resp = SupabaseNetworkClient.restApi.insertStory(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        story = dto
      )
      if (!resp.isSuccessful && resp.code() == 400) {
        resp = SupabaseNetworkClient.restApi.insertStoryLegacy(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          story = SupabaseStoryLegacyInsertDto(
            authorId = story.author.id,
            mediaUrl = story.mediaUrl.ifBlank { "text://story" },
            storyType = story.mediaType.name,
            caption = story.caption,
            expiresAt = expiresIso
          )
        )
      }

      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        val created = resp.body()!!.first()
        val createdIso = created.createdAt ?: TimestampUtils.nowIsoUtc()
        Result.success(
          story.copy(
            id = created.id ?: story.id,
            timestamp = TimestampUtils.formatRelative(createdIso, "Just now"),
            expiresHoursRemaining = 24,
            createdAtIso = createdIso,
            expiresAtIso = created.expiresAt ?: expiresIso
          )
        )
      } else {
        val err = parseErrorMessage(resp.errorBody()?.string().orEmpty(), "Unable to publish story (${resp.code()}).")
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error while publishing story.")))
    }
  }

  suspend fun markStoryViewedOnSupabase(
    storyId: String,
    viewerId: String,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured || storyId.isBlank() || viewerId.isBlank()) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.success(Unit)
    try {
      val resp = SupabaseNetworkClient.restApi.upsertStoryView(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        view = SupabaseStoryViewDto(storyId = storyId, viewerId = viewerId)
      )
      if (resp.isSuccessful) Result.success(Unit)
      else Result.failure(Exception("Story view sync pending"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteStoryOnSupabase(
    storyId: String,
    storagePathOrUrl: String? = null,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      val resp = SupabaseNetworkClient.restApi.deleteStory(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$storyId"
      )
      if (resp.isSuccessful) {
        if (!storagePathOrUrl.isNullOrBlank() && !storagePathOrUrl.startsWith("text://")) {
          deleteStorageObject(storagePathOrUrl, accessToken)
        }
        Result.success(Unit)
      } else {
        Result.failure(Exception("Failed to delete story (${resp.code()})."))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to delete story.")))
    }
  }

  suspend fun fetchVybeMatchUserIds(
    userId: String,
    accessToken: String = ""
  ): Result<Set<String>> = withContext(Dispatchers.IO) {
    if (!isConfigured || userId.isBlank()) return@withContext Result.success(emptySet())
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.success(emptySet())
    try {
      val resp = SupabaseNetworkClient.restApi.getVybeMatches(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        userIdFilter = "eq.$userId"
      )
      if (resp.isSuccessful && resp.body() != null) {
        Result.success(resp.body()!!.map { it.matchedUserId }.toSet())
      } else {
        Result.failure(Exception("Unable to load VYBE Matches"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun addVybeMatch(
    userId: String,
    matchedUserId: String,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      val resp = SupabaseNetworkClient.restApi.insertVybeMatch(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        body = SupabaseVybeMatchDto(userId = userId, matchedUserId = matchedUserId)
      )
      if (resp.isSuccessful) Result.success(Unit)
      else Result.failure(Exception(parseErrorMessage(resp.errorBody()?.string().orEmpty(), "Could not add VYBE Match.")))
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error updating VYBE Matches.")))
    }
  }

  suspend fun removeVybeMatch(
    userId: String,
    matchedUserId: String,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      val resp = SupabaseNetworkClient.restApi.deleteVybeMatch(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        userIdFilter = "eq.$userId",
        matchedUserIdFilter = "eq.$matchedUserId"
      )
      if (resp.isSuccessful) Result.success(Unit)
      else Result.failure(Exception("Could not remove VYBE Match."))
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Network error updating VYBE Matches.")))
    }
  }

  // =========================================================================
  // 7. MESSAGING & CONVERSATIONS (REAL USER-OWNED INBOX ONLY)
  // =========================================================================

  suspend fun fetchConversationsForUser(
    myUserId: String,
    blockedIds: Set<String> = emptySet(),
    restrictedIds: Set<String> = emptySet(),
    accessToken: String = ""
  ): Result<List<Conversation>> = withContext(Dispatchers.IO) {
    if (!isConfigured || myUserId.isBlank()) return@withContext Result.success(emptyList())
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.success(emptyList())
    try {
      val myMembershipsResp = SupabaseNetworkClient.restApi.getMyConversationMemberships(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        userIdFilter = "eq.$myUserId"
      )
      if (!myMembershipsResp.isSuccessful || myMembershipsResp.body().isNullOrEmpty()) {
        return@withContext Result.success(emptyList())
      }

      val myMemberships = myMembershipsResp.body()!!
      val convIds = myMemberships.map { it.conversationId }.distinct()
      val inFilter = "in.(${convIds.joinToString(",")})"

      val allMembersResp = SupabaseNetworkClient.restApi.getMembersForConversations(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        conversationIdsInFilter = inFilter
      )
      val membersByConv = allMembersResp.body().orEmpty().groupBy { it.conversationId }

      val conversations = myMemberships.mapNotNull { myMember ->
        val convId = myMember.conversationId
        val convMeta = myMember.conversations
        val isGroup = convMeta?.isGroup ?: false
        val participants = membersByConv[convId].orEmpty()
        val peerDtos = participants.filter { it.userId != myUserId }.mapNotNull { it.profiles }
        val peerUser = peerDtos.firstOrNull()?.let { mapProfileDtoToUser(it) }
          ?: if (!isGroup) return@mapNotNull null else User(id = convId, username = "group", displayName = convMeta?.groupName ?: "Group")

        val msgResp = SupabaseNetworkClient.restApi.getMessages(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          conversationIdFilter = "eq.$convId",
          limit = 60
        )
        val rawMessages = msgResp.body().orEmpty()
        val mappedMessages = rawMessages
          .filter { m -> m.isDeletedForEveryone != true }
          .map { m ->
            DirectMessage(
              id = m.id ?: "m_${System.currentTimeMillis()}",
              senderId = m.senderId,
              text = m.content,
              timestamp = TimestampUtils.formatMessageTime(m.createdAt, "Now"),
              createdAtIso = m.createdAt,
              isMe = m.senderId == myUserId,
              mediaUrl = m.mediaUrl,
              mediaType = m.mediaType ?: "TEXT",
              isDeletedForEveryone = false,
              replyToId = m.replyToMessageId,
              status = MessageStatus.SENT
            )
          }
        val lastMsg = mappedMessages.lastOrNull()
        val lastIso = lastMsg?.createdAtIso ?: convMeta?.updatedAt ?: convMeta?.createdAt

        Conversation(
          id = convId,
          peerUser = peerUser,
          lastMessage = lastMsg?.text ?: "Conversation started",
          lastMessageTimestamp = TimestampUtils.formatRelative(lastIso, lastMsg?.timestamp ?: "Recently"),
          lastMessageCreatedAtIso = lastIso,
          unreadCount = 0,
          isGroup = isGroup,
          groupName = convMeta?.groupName,
          groupMembers = participants.mapNotNull { it.profiles?.let { p -> mapProfileDtoToUser(p) } },
          messages = mappedMessages,
          isHidden = myMember.isHidden ?: false,
          chatTheme = myMember.chatTheme ?: "DEFAULT",
          customNickname = myMember.customNickname,
          isMuted = myMember.isMuted ?: false,
          isBlocked = blockedIds.contains(peerUser.id),
          isRestricted = restrictedIds.contains(peerUser.id)
        )
      }

      Result.success(conversations)
    } catch (e: Exception) {
      Log.w(TAG, "fetchConversationsForUser warning: ${e.message}")
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to sync inbox.")))
    }
  }

  suspend fun getOrCreateDirectConversation(
    myUserId: String,
    peerUserId: String,
    accessToken: String = ""
  ): Result<String> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.failure(Exception("Supabase not configured."))
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Please sign in to send messages."))

    try {
      // 1. Try atomic server-side RPC enforcing mutual-follow / privacy / block rules
      val rpcResp = SupabaseNetworkClient.restApi.getOrCreateDirectConversation(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        request = SupabaseRpcPeerConversationRequest(peerId = peerUserId)
      )
      if (rpcResp.isSuccessful && !rpcResp.body().isNullOrBlank()) {
        return@withContext Result.success(rpcResp.body()!!.trim().removeSurrounding("\""))
      }
      if (!rpcResp.isSuccessful && rpcResp.code() != 404) {
        val rpcErr = parseErrorMessage(rpcResp.errorBody()?.string().orEmpty(), "")
        if (rpcErr.contains("Mutual follow", true) ||
          rpcErr.contains("disabled", true) ||
          rpcErr.contains("block", true) ||
          rpcErr.contains("private", true)
        ) {
          return@withContext Result.failure(Exception(rpcErr))
        }
      }

      // 2. Fallback direct table creation if migration 0004 RPC is not yet deployed
      val convResp = SupabaseNetworkClient.restApi.insertConversation(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        conversation = SupabaseConversationInsertDto(isGroup = false, createdBy = myUserId)
      )
      if (convResp.isSuccessful && !convResp.body().isNullOrEmpty()) {
        val convId = convResp.body()!!.first().id ?: return@withContext Result.failure(Exception("Missing conversation ID."))
        val membersResp = SupabaseNetworkClient.restApi.insertConversationMembers(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          members = listOf(
            SupabaseConversationMemberDto(conversationId = convId, userId = myUserId),
            SupabaseConversationMemberDto(conversationId = convId, userId = peerUserId)
          )
        )
        if (membersResp.isSuccessful) {
          return@withContext Result.success(convId)
        }
      }
      Result.failure(Exception("Unable to start conversation. Ensure migration 0004 RLS policies are applied."))
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Unable to open conversation.")))
    }
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
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Please sign in again to send messages."))
    try {
      val dto = SupabaseMessageInsertDto(
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
        val err = parseErrorMessage(resp.errorBody()?.string().orEmpty(), "Failed to deliver message.")
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      Result.failure(Exception(friendlyNetworkMessage(e, "Message failed to send. Check your internet connection.")))
    }
  }

  suspend fun fetchMessages(
    conversationId: String,
    accessToken: String = "",
    limit: Int = 80
  ): Result<List<SupabaseMessageDto>> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(emptyList())
    try {
      val resp = SupabaseNetworkClient.restApi.getMessages(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeaderFor(accessToken),
        conversationIdFilter = "eq.$conversationId",
        limit = limit
      )
      if (resp.isSuccessful && resp.body() != null) {
        Result.success(resp.body()!!.filter { it.isDeletedForEveryone != true })
      } else {
        Result.failure(Exception("Failed to fetch messages"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteMessageForEveryone(
    messageId: String,
    mediaUrl: String? = null,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured || messageId.isBlank() || messageId.startsWith("m_")) {
      return@withContext Result.success(Unit)
    }
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required to delete message."))
    try {
      // 1. Try SECURITY DEFINER RPC (deletes message row + touches conversation updated_at for realtime sync)
      var deletedHard = false
      try {
        val rpcResp = SupabaseNetworkClient.restApi.deleteMessageForEveryoneRpc(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          request = SupabaseRpcDeleteMessageRequest(messageId = messageId)
        )
        if (rpcResp.isSuccessful && rpcResp.body() == true) {
          deletedHard = true
        }
      } catch (_: Exception) {
        // Fallback to REST DELETE below if RPC migration is not yet applied
      }

      // 2. Direct REST DELETE (enforced by messages_delete_sender RLS policy)
      if (!deletedHard) {
        val delResp = SupabaseNetworkClient.restApi.deleteMessage(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          idFilter = "eq.$messageId"
        )
        deletedHard = delResp.isSuccessful
      }

      // 3. Soft-delete fallback in case older database lacks DELETE policy (still filtered out on sync)
      SupabaseNetworkClient.restApi.patchMessageDeleted(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$messageId",
        body = mapOf(
          "is_deleted_for_everyone" to true,
          "content" to "",
          "media_url" to null
        )
      )

      // 4. Clean up attached media in Supabase Storage if present
      if (!mediaUrl.isNullOrBlank()) {
        deleteStorageObject(mediaUrl, accessToken)
      }

      Result.success(Unit)
    } catch (e: Exception) {
      Log.w(TAG, "deleteMessageForEveryone warning: ${e.message}")
      Result.failure(Exception(friendlyNetworkMessage(e, "Failed to delete message for everyone.")))
    }
  }

  suspend fun editMessage(
    messageId: String,
    newContent: String,
    accessToken: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured || messageId.isBlank() || messageId.startsWith("m_")) {
      return@withContext Result.success(Unit)
    }
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
      val resp = SupabaseNetworkClient.restApi.patchMessageDeleted(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$messageId",
        body = mapOf("content" to newContent)
      )
      if (resp.isSuccessful) Result.success(Unit)
      else Result.failure(Exception("Failed to edit message."))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  // =========================================================================
  // 8. REAL NOTIFICATIONS (RECIPIENT-SCOPED, DEDUPLICATED, SYNCED)
  // =========================================================================

  suspend fun fetchNotifications(
    recipientId: String,
    accessToken: String = ""
  ): Result<List<NotificationItem>> = withContext(Dispatchers.IO) {
    if (!isConfigured || recipientId.isBlank()) return@withContext Result.success(emptyList())
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.success(emptyList())
    try {
      var resp = SupabaseNetworkClient.restApi.getNotifications(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        recipientIdFilter = "eq.$recipientId",
        select = "*,actor:actor_id(*)"
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getNotifications(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          recipientIdFilter = "eq.$recipientId",
          select = "*"
        )
      }
      if (resp.isSuccessful && resp.body() != null) {
        val rawDtos = resp.body()!!
        // Batch-fetch target posts/reels so every LIKE/COMMENT notification has preview & type
        val postTargetIds = rawDtos.mapNotNull { dto ->
          val t = dto.type.uppercase()
          if (t in setOf("LIKE", "REACTION", "COMMENT", "COMMENT_OR_REPLY")) dto.targetId?.takeIf { it.isNotBlank() }
          else null
        }.distinct()

        val postsById: Map<String, SupabasePostDto> = if (postTargetIds.isNotEmpty()) {
          try {
            val postResp = SupabaseNetworkClient.restApi.getPostsByIds(
              apiKey = SupabaseConfig.supabaseAnonKey,
              authHeader = authHeader,
              idsInFilter = "in.(${postTargetIds.joinToString(",")})"
            )
            postResp.body().orEmpty().mapNotNull { p -> p.id?.let { it to p } }.toMap()
          } catch (_: Exception) {
            emptyMap()
          }
        } else {
          emptyMap()
        }

        val seenKeys = mutableSetOf<String>()
        val items = rawDtos.mapNotNull { dto ->
          val id = dto.id ?: return@mapNotNull null
          val dedupKey = if (dto.type == "COMMENT") id else "${dto.type}_${dto.actorId}_${dto.targetId}"
          if (!seenKeys.add(dedupKey)) return@mapNotNull null

          val notifType = when (dto.type.uppercase()) {
            "LIKE", "REACTION" -> com.example.data.model.NotificationType.LIKE
            "COMMENT", "COMMENT_OR_REPLY" -> com.example.data.model.NotificationType.COMMENT_OR_REPLY
            "FOLLOW", "FOLLOW_REQUEST", "FOLLOW_ACCEPTED" -> com.example.data.model.NotificationType.FOLLOW
            "DIRECT_MESSAGE", "MESSAGE" -> com.example.data.model.NotificationType.DIRECT_MESSAGE
            "GROUP_MESSAGE" -> com.example.data.model.NotificationType.GROUP_MESSAGE
            "SECURITY_ACCOUNT_UPDATE" -> com.example.data.model.NotificationType.SECURITY_ACCOUNT_UPDATE
            else -> com.example.data.model.NotificationType.LIKE
          }

          val targetPost = dto.targetId?.let { postsById[it] }
          val isReelContent = targetPost?.mediaType?.equals("VIDEO", ignoreCase = true) == true
          val previewUrl = targetPost?.postMedia?.firstOrNull()?.mediaUrl?.takeIf { it.isNotBlank() }
          val snippet = targetPost?.caption?.trim()?.takeIf { it.isNotEmpty() }

          val dest = when (notifType) {
            com.example.data.model.NotificationType.FOLLOW -> "USER_PROFILE"
            com.example.data.model.NotificationType.DIRECT_MESSAGE,
            com.example.data.model.NotificationType.GROUP_MESSAGE -> "INBOX"
            com.example.data.model.NotificationType.LIKE,
            com.example.data.model.NotificationType.COMMENT_OR_REPLY -> if (isReelContent) "REELS_VIEWER" else "POST_DETAIL"
            else -> "MAIN"
          }

          val actorUser = dto.actor?.let { mapProfileDtoToUser(it) }
            ?: dto.actorId?.takeIf { it.isNotBlank() }?.let { aid ->
              User(id = aid, username = "vyber", displayName = "VYBE Member")
            }

          val resolvedTitle = if (notifType == com.example.data.model.NotificationType.LIKE && actorUser != null && actorUser.username != "vyber") {
            "@${actorUser.username} liked your ${if (isReelContent) "Reel" else "post"}"
          } else {
            dto.title
          }

          NotificationItem(
            id = id,
            type = notifType,
            title = resolvedTitle,
            message = dto.message,
            timestamp = TimestampUtils.formatRelative(dto.createdAt, "Recently"),
            createdAtIso = dto.createdAt,
            isRead = dto.isRead ?: false,
            actorUser = actorUser,
            destinationScreen = dest,
            targetEntityId = dto.targetId ?: dto.actorId,
            contentPreviewUrl = previewUrl,
            contentSnippet = snippet,
            isReel = isReelContent
          )
        }
        Result.success(items)
      } else {
        Result.failure(Exception("Unable to load notifications"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun createInteractionNotificationIfNeeded(
    recipientId: String,
    actorId: String,
    type: String,
    title: String,
    message: String,
    targetId: String? = null,
    accessToken: String = ""
  ) = withContext(Dispatchers.IO) {
    if (!isConfigured || recipientId.isBlank() || actorId.isBlank() || recipientId == actorId) return@withContext
    val authHeader = authHeaderFor(accessToken) ?: return@withContext
    try {
      SupabaseNetworkClient.restApi.insertNotification(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        notification = SupabaseNotificationInsertDto(
          recipientId = recipientId,
          actorId = actorId,
          type = type,
          title = title,
          message = message,
          targetId = targetId,
          isRead = false
        )
      )
    } catch (_: Exception) {
      // Safe no-op when DB trigger already created it or RLS restricts duplicate insert
    }
  }

  suspend fun markAllNotificationsRead(recipientId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured || recipientId.isBlank()) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.success(Unit)
    try {
      val resp = SupabaseNetworkClient.restApi.markNotificationsRead(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        recipientIdFilter = "eq.$recipientId",
        body = mapOf("is_read" to true)
      )
      if (resp.isSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to mark read"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun markNotificationRead(notificationId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured || notificationId.isBlank()) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken) ?: return@withContext Result.success(Unit)
    try {
      SupabaseNetworkClient.restApi.markSingleNotificationRead(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$notificationId",
        body = mapOf("is_read" to true)
      )
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteNotification(notificationId: String, accessToken: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isConfigured) return@withContext Result.success(Unit)
    val authHeader = authHeaderFor(accessToken)
      ?: return@withContext Result.failure(Exception("Authentication required."))
    try {
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

  // =========================================================================
  // 9. HELPERS (TIMESTAMPS, AGE & ERROR PARSING)
  // =========================================================================

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
    } catch (_: Exception) {
      20
    }
  }

  private fun formatIsoUtc(epochMillis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(epochMillis))
  }

  private fun parseIsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val clean = iso.trim()
      .replace(Regex("\\.\\d+"), "")
      .replace("+00:00", "Z")
      .replace("+00", "Z")
    return try {
      val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
      sdf.timeZone = TimeZone.getTimeZone("UTC")
      sdf.parse(clean)?.time
    } catch (_: Exception) {
      null
    }
  }

  private fun formatRelativeTimestamp(iso: String?): String {
    val millis = parseIsoMillis(iso) ?: return iso?.take(10) ?: "Recently"
    val diffSec = ((System.currentTimeMillis() - millis) / 1000L).coerceAtLeast(0L)
    return when {
      diffSec < 60L -> "Just now"
      diffSec < 3600L -> "${diffSec / 60L}m ago"
      diffSec < 86_400L -> "${diffSec / 3600L}h ago"
      diffSec < 604_800L -> "${diffSec / 86_400L}d ago"
      else -> iso?.take(10) ?: "Recently"
    }
  }

  private fun formatShortClock(iso: String?): String {
    val millis = parseIsoMillis(iso) ?: return "Now"
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
  }

  private fun parseErrorMessage(jsonBody: String, fallback: String): String {
    if (jsonBody.isBlank()) return fallback
    return try {
      val trimmed = jsonBody.trim()
      val json = if (trimmed.startsWith("[")) JSONArray(trimmed).optJSONObject(0) ?: return fallback else JSONObject(trimmed)
      val raw = when {
        json.has("error_description") && !json.isNull("error_description") -> json.getString("error_description")
        json.has("msg") && !json.isNull("msg") -> json.getString("msg")
        json.has("message") && !json.isNull("message") -> json.getString("message")
        json.has("error") && !json.isNull("error") -> json.getString("error")
        else -> fallback
      }
      when {
        raw.contains("otp_expired", true) || raw.contains("Token has expired", true) ->
          "This verification or recovery link has expired. Please request a new one."
        raw.contains("Invalid login credentials", true) ->
          "Incorrect email or password. Please try again."
        raw.contains("Email not confirmed", true) ->
          "Your email address is not confirmed yet. Please check your inbox."
        else -> raw
      }
    } catch (_: Exception) {
      fallback
    }
  }

  private fun friendlyNetworkMessage(e: Exception, fallback: String): String {
    val msg = e.message.orEmpty()
    return when {
      e is java.net.UnknownHostException || msg.contains("Unable to resolve host", true) ->
        "No internet connection. Please reconnect and try again."
      e is java.net.SocketTimeoutException || msg.contains("timeout", true) ->
        "The server took too long to respond. Please retry."
      msg.isNotBlank() -> msg
      else -> fallback
    }
  }
}

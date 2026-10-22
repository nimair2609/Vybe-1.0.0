package com.example.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.MoodType
import com.example.data.model.User
import com.example.data.model.UserRole
import org.json.JSONObject

class SessionManager private constructor(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

  companion object {
    private const val PREF_NAME = "vybe_auth_session_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_EMAIL = "email"
    private const val KEY_PHONE = "phone"
    private const val KEY_FIRST_NAME = "first_name"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_DOB = "date_of_birth"
    private const val KEY_AGE = "age"
    private const val KEY_IS_UNDER_18 = "is_under_18"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_COVER_URL = "cover_url"
    private const val KEY_BIO = "bio"
    private const val KEY_ROLE = "role"
    private const val KEY_IS_VERIFIED = "is_verified"
    private const val KEY_IS_VIP = "is_vip"

    @Volatile
    private var instance: SessionManager? = null

    fun getInstance(context: Context): SessionManager {
      return instance ?: synchronized(this) {
        instance ?: SessionManager(context.applicationContext).also { instance = it }
      }
    }

    // Secondary accessor if initialized
    fun get(): SessionManager? = instance
  }

  val isLoggedIn: Boolean
    get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

  val accessToken: String?
    get() = prefs.getString(KEY_ACCESS_TOKEN, null)

  val refreshToken: String?
    get() = prefs.getString(KEY_REFRESH_TOKEN, null)

  val userId: String?
    get() = prefs.getString(KEY_USER_ID, null)

  fun saveSession(user: User, accessToken: String = "", refreshToken: String = "") {
    prefs.edit().apply {
      putBoolean(KEY_IS_LOGGED_IN, true)
      if (accessToken.isNotEmpty()) putString(KEY_ACCESS_TOKEN, accessToken)
      if (refreshToken.isNotEmpty()) putString(KEY_REFRESH_TOKEN, refreshToken)
      putString(KEY_USER_ID, user.id)
      putString(KEY_USERNAME, user.username)
      putString(KEY_DISPLAY_NAME, user.displayName)
      putString(KEY_EMAIL, user.email)
      putString(KEY_PHONE, user.phone)
      putString(KEY_FIRST_NAME, user.firstName)
      putString(KEY_LAST_NAME, user.lastName)
      putString(KEY_DOB, user.dateOfBirth)
      putInt(KEY_AGE, user.age)
      putBoolean(KEY_IS_UNDER_18, user.isUnder18)
      putString(KEY_AVATAR_URL, user.avatarUrl)
      putString(KEY_COVER_URL, user.coverUrl)
      putString(KEY_BIO, user.bio)
      putString(KEY_ROLE, user.role.name)
      putBoolean(KEY_IS_VERIFIED, user.isVerified)
      putBoolean(KEY_IS_VIP, user.isVip)
      apply()
    }
  }

  fun updateCurrentUser(user: User) {
    if (!isLoggedIn) return
    prefs.edit().apply {
      putString(KEY_USERNAME, user.username)
      putString(KEY_DISPLAY_NAME, user.displayName)
      putString(KEY_EMAIL, user.email)
      putString(KEY_PHONE, user.phone)
      putString(KEY_FIRST_NAME, user.firstName)
      putString(KEY_LAST_NAME, user.lastName)
      putString(KEY_AVATAR_URL, user.avatarUrl)
      putString(KEY_COVER_URL, user.coverUrl)
      putString(KEY_BIO, user.bio)
      apply()
    }
  }

  fun clearSession() {
    prefs.edit().clear().apply()
  }

  fun loadCurrentUser(): User? {
    if (!isLoggedIn) return null
    val id = prefs.getString(KEY_USER_ID, null) ?: return null
    val username = prefs.getString(KEY_USERNAME, "") ?: ""
    val displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
    val email = prefs.getString(KEY_EMAIL, "") ?: ""
    val phone = prefs.getString(KEY_PHONE, "") ?: ""
    val firstName = prefs.getString(KEY_FIRST_NAME, "") ?: ""
    val lastName = prefs.getString(KEY_LAST_NAME, "") ?: ""
    val dob = prefs.getString(KEY_DOB, "2000-01-01") ?: "2000-01-01"
    val age = prefs.getInt(KEY_AGE, 20)
    val isUnder18 = prefs.getBoolean(KEY_IS_UNDER_18, false)
    val avatarUrl = prefs.getString(KEY_AVATAR_URL, "") ?: ""
    val coverUrl = prefs.getString(KEY_COVER_URL, "") ?: ""
    val bio = prefs.getString(KEY_BIO, "") ?: ""
    val roleStr = prefs.getString(KEY_ROLE, UserRole.USER.name) ?: UserRole.USER.name
    val isVerified = prefs.getBoolean(KEY_IS_VERIFIED, false)
    val isVip = prefs.getBoolean(KEY_IS_VIP, false)

    val role = try {
      UserRole.valueOf(roleStr)
    } catch (e: Exception) {
      UserRole.USER
    }

    return User(
      id = id,
      username = username,
      displayName = displayName,
      email = email,
      phone = phone,
      firstName = firstName,
      lastName = lastName,
      dateOfBirth = dob,
      age = age,
      isUnder18 = isUnder18,
      avatarUrl = avatarUrl,
      coverUrl = coverUrl,
      bio = bio,
      role = role,
      isVerified = isVerified,
      isVip = isVip,
      isOnboardingCompleted = true
    )
  }
}

package com.example.data.model

enum class UserRole {
  USER, CREATOR, BUSINESS_OWNER, VERIFIED_PUBLIC_FIGURE, MODERATOR, ADMIN
}

enum class LivePulseStatus(val label: String, val icon: String) {
  OPEN_TO_CHAT("Open to chatting", "💬"),
  FEELING_CREATIVE("Feeling creative", "✨"),
  LISTENING_MUSIC("Listening to music", "🎧"),
  GAMING_PARTNER("Looking for gaming partner", "🎮"),
  QUIET_TIME("Taking quiet time", "🌙"),
  NONE("None", "")
}

data class User(
  val id: String,
  val username: String,
  val displayName: String,
  val publicVybeId: String = "VYBE-${id.takeLast(8).uppercase().replace("_", "K")}",
  val firstName: String = "",
  val lastName: String = "",
  val bio: String = "",
  val avatarUrl: String = "",
  val coverUrl: String = "",
  val profileVideoUrl: String? = null, // Up to 5-second looping profile video
  val email: String = "",
  val phone: String = "",
  val isEmailVerified: Boolean = false,
  val isPhoneVerified: Boolean = false,
  val dateOfBirth: String = "2000-01-01",
  val age: Int = 24,
  val isUnder18: Boolean = false,
  val isVerified: Boolean = false, // Distinctive rounded circular badge with official VYBE "V"
  val isVip: Boolean = false, // Official VIP status badge with circular "V"
  val isVerifiedPublicFigure: Boolean = false,
  val isVerifiedBusiness: Boolean = false,
  val vipBadgeTitle: String = "",
  val role: UserRole = UserRole.USER,
  val isPrivate: Boolean = false,
  val isFollowedByMe: Boolean = false,
  val followersCount: Int = 0,
  val followingCount: Int = 0,
  val postsCount: Int = 0,
  val currentMood: MoodType? = null,
  val livePulse: LivePulseStatus = LivePulseStatus.NONE,
  val joinedDate: String = "2024",
  val isDeactivated: Boolean = false,
  val isOnboardingCompleted: Boolean = false,
  val isSpiritualMode: Boolean = false
)

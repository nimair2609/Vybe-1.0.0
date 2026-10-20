package com.example.data.model

enum class AppThemeMode(val title: String) {
  DARK("Dark Cyber-Luxe"),
  LIGHT("Light Clean"),
  SYSTEM("System Default")
}

data class SupportTicket(
  val id: String,
  val category: String, // e.g. "Account & Verification", "Privacy & Safety", "Bug Report", "Creator Tools"
  val subject: String,
  val message: String,
  val status: String = "Open", // "Open", "Investigating", "Resolved"
  val createdAt: String,
  val agentResponse: String? = null
)

data class TimeManagementStats(
  val todayUsageMinutes: Int = 42,
  val dailyAverageMinutes: Int = 38,
  val weeklyTotalHours: Float = 4.4f,
  val breakReminderMinutes: Int = 45,
  val isBreakReminderEnabled: Boolean = true,
  val dailyLimitMinutes: Int = 90,
  val isDailyLimitEnabled: Boolean = false
)

data class ActiveDeviceSession(
  val id: String,
  val deviceModel: String,
  val platform: String,
  val locationApprox: String,
  val lastActiveTimestamp: String,
  val isCurrent: Boolean
)

data class NotificationPreferences(
  val pushDirectMessages: Boolean = true,
  val pushPostReactions: Boolean = true,
  val pushComments: Boolean = true,
  val pushTimeCapsuleUnlocks: Boolean = true,
  val pushCrossedPathsRequests: Boolean = true,
  val pushUnloadRoomActivity: Boolean = false,
  val emailSecurityAlerts: Boolean = true,
  val emailWeeklyDigest: Boolean = false
)

data class UserActivityItem(
  val id: String,
  val type: String, // "POST_CREATED", "COMMENT_ADDED", "REACTION_GIVEN", "STORY_SHARED", "CAPSULE_SEALED"
  val title: String,
  val details: String,
  val timestamp: String
)

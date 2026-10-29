package com.example.data.model

data class CreatorStats(
  val totalImpressions30d: Int = 184500,
  val engagementRatePercent: Float = 6.4f,
  val netFollowersGain30d: Int = 3840,
  val profileVisits30d: Int = 12900,
  val estimatedMonthlyEarningsInr: Int = 34500,
  val pendingPayoutInr: Int = 18200,
  val confirmedPayoutInr: Int = 16300,
  val isMonetizationApproved: Boolean = true,
  val brandCollaborationReadiness: String = "Eligible - Profile Verified"
)

enum class ReportTargetType {
  POST, USER_PROFILE, SHOP_REVIEW, CROSSED_PATHS_MESSAGE
}

enum class ReportStatus {
  PENDING, INVESTIGATING, ACTION_TAKEN, DISMISSED
}

data class AdminReport(
  val id: String,
  val targetType: ReportTargetType,
  val targetId: String,
  val summaryText: String,
  val reportedByUsername: String,
  val reportedUser: String,
  val reason: String,
  val timestamp: String,
  val status: ReportStatus = ReportStatus.PENDING,
  val severity: String = "Medium"
)

enum class NotificationType {
  LIKE,
  COMMENT_OR_REPLY,
  FOLLOW,
  DIRECT_MESSAGE,
  GROUP_MESSAGE,
  ROOM_INVITATION,
  TIME_CAPSULE_INVITATION,
  TIME_CAPSULE_UNLOCK,
  DOUBLE_TAKE_RESPONSE,
  CROSSED_PATHS_INTERACTION,
  BUSINESS_VERIFICATION_UPDATE,
  LIVE_STREAM_ALERT,
  SECURITY_ACCOUNT_UPDATE,
  SHOP_REVIEW
}

data class NotificationItem(
  val id: String,
  val type: NotificationType,
  val title: String,
  val message: String,
  val timestamp: String,
  val createdAtIso: String? = null,
  val isRead: Boolean = false,
  val actorUser: User? = null,
  val destinationScreen: String? = null,
  val targetEntityId: String? = null,
  val contentPreviewUrl: String? = null,
  val contentSnippet: String? = null,
  val isReel: Boolean = false
)


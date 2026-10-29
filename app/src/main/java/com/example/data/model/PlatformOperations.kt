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
  POST, REEL, STORY, COMMENT, ROOM, COMMUNITY_POST, USER_PROFILE, SHOP_REVIEW, CROSSED_PATHS_MESSAGE
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
  val isReel: Boolean = false,
  val eventKey: String? = null
)

enum class AdminSubRole(val displayName: String) {
  OWNER_ADMIN("Owner / Super Admin"),
  TECHNICAL_ADMIN("Technical Administrator"),
  CONTENT_MODERATOR("Content Moderator"),
  COMPLIANCE_OFFICER("Trust & Safety Compliance"),
  GROWTH_ANALYST("Growth & Advertising Analyst")
}

data class AdminAppSettings(
  val appName: String = "VYBE",
  val companyName: String = "NR Next | NR Group",
  val isMaintenanceMode: Boolean = false,
  val registrationMode: String = "OPEN",
  val minAgeRequirement: Int = 13,
  val matureContentAgeGate: Int = 18,
  val maxDailyDmLimit: Int = 50,
  val isMonetizationSandboxEnabled: Boolean = true
)

enum class AdCampaignPlacement(val displayName: String) {
  FEED_NATIVE("Feed Native Card"),
  REELS_IN_STREAM("Reels In-Stream Sponsor"),
  EXPLORE_BANNER("Explore Zone Banner"),
  STORIES_INTERSTITIAL("Stories Interstitial");

  val label: String
    get() = displayName
}

enum class AdCampaignStatus {
  ACTIVE, PAUSED, SCHEDULED, DRAFT, COMPLETED, ARCHIVED
}

data class AdCampaign(
  val id: String,
  val advertiserName: String,
  val campaignTitle: String,
  val placement: AdCampaignPlacement = AdCampaignPlacement.FEED_NATIVE,
  val creativeUrl: String = "",
  val targetCtaUrl: String = "",
  val ctaButtonText: String = "Learn More",
  val dailyBudgetInr: Int = 1000,
  val totalBudgetInr: Int = 10000,
  val spentInr: Int = 0,
  val impressions: Int = 0,
  val clicks: Int = 0,
  val status: AdCampaignStatus = AdCampaignStatus.ACTIVE,
  val startDateIso: String = "2026-10-01",
  val endDateIso: String = "2026-12-31"
)

enum class IntegrationHealth {
  HEALTHY, DEGRADED, MISSING, ERROR
}

data class AdminIntegrationItem(
  val id: String,
  val name: String,
  val provider: String,
  val category: String,
  val purpose: String,
  val status: IntegrationHealth = IntegrationHealth.HEALTHY,
  val maskedSecret: String = "••••••••",
  val lastCheckedIso: String = "Live",
  val requiredFields: List<String> = emptyList(),
  val documentationUrl: String = "",
  val setupInstructions: String = "",
  val isDynamicallyToggleable: Boolean = false,
  val isEnabled: Boolean = true
)

data class AdminAuditLog(
  val id: String,
  val adminEmail: String,
  val adminRole: String,
  val action: String,
  val targetType: String,
  val targetId: String,
  val details: String,
  val timestamp: String
)

data class AdminDashboardStats(
  val totalUsers: Int = 148,
  val activeUsers24h: Int = 32,
  val activeUsers7d: Int = 96,
  val totalPosts: Int = 24,
  val totalReels: Int = 14,
  val totalStories: Int = 8,
  val totalCommunities: Int = 6,
  val verifiedCreators: Int = 12,
  val pendingReports: Int = 0,
  val activeAdCampaigns: Int = 2,
  val totalImpressionsCount: Int = 140700,
  val totalClicksCount: Int = 6960,
  val storageUsageMb: Float = 440.8f,
  val apiErrorCount24h: Int = 0,
  val backendStatus: String = "OPERATIONAL",
  val realtimeLatencyMs: Int = 28
)

data class LicensedMusicTrack(
  val trackId: String = "",
  val title: String = "",
  val artist: String = "",
  val albumName: String = "VYBE Originals",
  val durationSeconds: Int = 30,
  val previewUrl: String? = null,
  val coverArtUrl: String? = null,
  val isTrending: Boolean = false,
  val usageCount: Int = 1240
) {
  val displayLabel: String
    get() = if (artist.isNotBlank()) "$title — $artist" else title

  fun toAttachment(): MusicAttachment = MusicAttachment(
    trackId = trackId,
    title = title,
    artist = artist,
    previewUrl = previewUrl,
    streamUrl = previewUrl,
    durationSeconds = durationSeconds,
    coverUrl = coverArtUrl
  )
}

data class BadgeVerificationRequest(
  val id: String,
  val userId: String,
  val username: String,
  val displayName: String,
  val avatarUrl: String = "",
  val requestType: String = "VERIFIED", // VERIFIED, VIP, SHOP, WORKER
  val fullName: String = "",
  val category: String = "",
  val identityDocumentUrl: String? = null,
  val statement: String = "",
  val status: String = "PENDING", // PENDING, APPROVED, REJECTED, MORE_INFO_REQUIRED, EXPIRED
  val rejectionReason: String? = null,
  val adminNotes: String? = null,
  val vipExpiresAtIso: String? = null,
  val userResponseStatus: String = "NONE", // NONE, PENDING_ACCEPTANCE, ACCEPTED, DECLINED
  val createdAtIso: String? = null,
  val timestampLabel: String = "Recently"
) {
  val reviewerNotes: String?
    get() = adminNotes
}

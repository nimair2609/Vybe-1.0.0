package com.example.data.model

enum class AdminSubRole(val displayName: String, val badgeColorHex: Long) {
  OWNER_ADMIN("Owner / Super Admin", 0xFFF59E0B),
  TECH_ADMIN("Technical Administrator", 0xFF3B82F6),
  MODERATOR("Content Moderator", 0xFFEF4444),
  SUPPORT_ADMIN("Support Administrator", 0xFF10B981),
  ADS_MANAGER("Advertising Manager", 0xFF8B5CF6)
}

data class AdminDashboardStats(
  val totalUsers: Int = 0,
  val activeUsers24h: Int = 0,
  val activeUsers7d: Int = 0,
  val totalPosts: Int = 0,
  val totalReels: Int = 0,
  val totalStories: Int = 0,
  val totalCommunities: Int = 0,
  val verifiedCreators: Int = 0,
  val pendingReports: Int = 0,
  val activeAdCampaigns: Int = 0,
  val totalImpressionsCount: Long = 0,
  val totalClicksCount: Long = 0,
  val storageUsageMb: Float = 0f,
  val apiErrorCount24h: Int = 0,
  val backendStatus: String = "OPERATIONAL",
  val realtimeLatencyMs: Int = 28
)

enum class IntegrationHealth(val label: String, val colorHex: Long) {
  HEALTHY("Configured & Live", 0xFF10B981),
  DEGRADED("Partial / Sandbox", 0xFFF59E0B),
  MISSING("Missing Configuration", 0xFFEF4444),
  DEPLOYMENT_REQUIRED("Requires Code Deploy", 0xFF6B7280)
}

data class AdminIntegrationItem(
  val id: String,
  val name: String,
  val provider: String,
  val category: String, // "Database & Auth", "Maps & Geolocation", "Notifications & Messaging", "Payments", "Audio & Media", "Ad & Analytics"
  val purpose: String,
  val status: IntegrationHealth,
  val maskedSecret: String, // e.g. "sb_publ••••••••4xQp" or "Not Configured"
  val lastCheckedIso: String,
  val requiredFields: List<String>,
  val documentationUrl: String,
  val setupInstructions: String,
  val isDynamicallyToggleable: Boolean = false,
  val isEnabled: Boolean = true
)

data class AdminAppSettings(
  val appName: String = "VYBE",
  val appLogoUrl: String = "",
  val approvedBrandTagline: String = "Back to Real. Back to Us.",
  val isMaintenanceMode: Boolean = false,
  val maintenanceMessage: String = "VYBE is currently undergoing scheduled platform upgrades. We will be right back!",
  val minSupportedVersion: String = "3.2.0",
  val currentAppVersion: String = "3.5.0",
  val isRegistrationOpen: Boolean = true,
  val registrationMode: String = "OPEN", // "OPEN", "VERIFIED_EMAIL_ONLY", "INVITE_ONLY"
  val isReelsEnabled: Boolean = true,
  val isStoriesEnabled: Boolean = true,
  val isUnloadVoiceEnabled: Boolean = true,
  val isCrossedPathsEnabled: Boolean = true,
  val isCommunitiesEnabled: Boolean = true,
  val isSpiritualModeEnabled: Boolean = true,
  val maxVideoUploadSeconds: Int = 60,
  val maxStoryHours: Int = 24,
  val maxUploadFileSizeMb: Int = 50,
  val supportContactEmail: String = "support@vybe.nrnext.in",
  val grievanceOfficerEmail: String = "grievance@vybe.nrnext.in",
  val termsOfServiceUrl: String = "https://vybe.nrnext.in/terms",
  val privacyPolicyUrl: String = "https://vybe.nrnext.in/privacy",
  val activeAnnouncementTitle: String = "",
  val activeAnnouncementMessage: String = "",
  val isAnnouncementActive: Boolean = false
)

enum class AdCampaignPlacement(val label: String) {
  FEED_NATIVE("Main Feed Native"),
  REELS_IN_STREAM("VYBES Reel In-Stream"),
  EXPLORE_BANNER("Explore Zone Header"),
  STORIES_INTERSTITIAL("Stories Interstitial")
}

enum class AdCampaignStatus(val label: String, val colorHex: Long) {
  ACTIVE("Active", 0xFF10B981),
  PAUSED("Paused", 0xFFF59E0B),
  SCHEDULED("Scheduled", 0xFF3B82F6),
  ARCHIVED("Archived", 0xFF6B7280),
  DRAFT("Draft", 0xFF9CA3AF)
}

data class AdCampaign(
  val id: String,
  val advertiserName: String,
  val campaignTitle: String,
  val placement: AdCampaignPlacement,
  val creativeUrl: String,
  val targetCtaUrl: String,
  val ctaButtonText: String = "Learn More",
  val dailyBudgetInr: Int = 1000,
  val totalBudgetInr: Int = 10000,
  val spentInr: Int = 0,
  val impressions: Long = 0,
  val clicks: Long = 0,
  val status: AdCampaignStatus = AdCampaignStatus.ACTIVE,
  val startDateIso: String,
  val endDateIso: String,
  val createdAtIso: String? = null
) {
  val ctrPercent: Float
    get() = if (impressions > 0) (clicks.toFloat() / impressions.toFloat()) * 100f else 0f
}

data class AdminAuditLog(
  val id: String,
  val adminEmail: String,
  val adminRole: String,
  val action: String, // e.g. "USER_SUSPENDED", "POST_TAKEDOWN", "CAMPAIGN_PAUSED", "SETTING_UPDATED", "BADGE_GRANTED"
  val targetType: String,
  val targetId: String,
  val details: String,
  val timestamp: String,
  val createdAtIso: String? = null
)

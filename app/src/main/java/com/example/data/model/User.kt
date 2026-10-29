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

enum class OfficialVybeBadge(
  val code: String,
  val title: String,
  val shortDescription: String
) {
  VERIFIED_BLUE(
    code = "VERIFIED",
    title = "VYBE Verified",
    shortDescription = "Authentic public profile confirmed by VYBE admin review."
  ),
  VIP_GOLD(
    code = "VIP",
    title = "VYBE VIP",
    shortDescription = "Official VYBE VIP creator or recognized community member."
  ),
  SHOP_GOLD(
    code = "SHOP",
    title = "VYBE Verified Shop",
    shortDescription = "Verified business or storefront approved by VYBE."
  ),
  WORKER_GREEN(
    code = "WORKER",
    title = "VYBE Worker",
    shortDescription = "Authorized VYBE staff or platform team member."
  );

  val description: String
    get() = shortDescription
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
  val isVerified: Boolean = false, // VYBE Verified — Blue Tick
  val isVip: Boolean = false, // VYBE VIP — Gold Badge (subject to vipExpiresAtIso)
  val isVerifiedPublicFigure: Boolean = false,
  val isVerifiedBusiness: Boolean = false, // VYBE Shop — Gold Storefront Badge
  val isWorker: Boolean = false, // VYBE Worker — Green Tick
  val isWorkerBadgeMandatory: Boolean = false,
  val vipExpiresAtIso: String? = null,
  val badgeApprovalStatus: String = "ACCEPTED", // NONE, PENDING_ACCEPTANCE, ACCEPTED, DECLINED
  val badgeDisplayStatus: String = "ACCEPTED", // ACCEPTED, HIDDEN, DECLINED
  val badgeType: String? = null,
  val isFoundingCreator: Boolean = false,
  val vipBadgeTitle: String = "",
  val role: UserRole = UserRole.USER,
  val isPrivate: Boolean = false,
  val isFollowedByMe: Boolean = false,
  val isFollowRequestedByMe: Boolean = false,
  val followsMe: Boolean = false,
  val hasPendingFollowRequestForMe: Boolean = false,
  val messagePrivacy: String = "EVERYONE", // "EVERYONE", "MUTUAL_FOLLOWS", "NONE"
  val followersCount: Int = 0,
  val followingCount: Int = 0,
  val postsCount: Int = 0,
  val currentMood: MoodType? = null,
  val livePulse: LivePulseStatus = LivePulseStatus.NONE,
  val joinedDate: String = "VYBE Member",
  val createdAtIso: String? = null,
  val lastSeenAtIso: String? = null,
  val isOnline: Boolean = false,
  val isDeactivated: Boolean = false,
  val isSuspended: Boolean = false,
  val suspendedUntilIso: String? = null,
  val isBanned: Boolean = false,
  val isCreatorSpotlight: Boolean = false,
  val hasPendingDeletion: Boolean = false,
  val isOnboardingCompleted: Boolean = false,
  val isSpiritualMode: Boolean = false,
  val goalType: String = "Creator Journey"
) {
  val isFullyVerifiedAccount: Boolean
    get() = isEmailVerified && isPhoneVerified

  val hasIncompleteProfile: Boolean
    get() = username.isBlank() ||
      username.equals("null", ignoreCase = true) ||
      username.startsWith("vybe_") ||
      displayName.isBlank() ||
      displayName.equals("null", ignoreCase = true)

  val isVipExpired: Boolean
    get() {
      val raw = vipExpiresAtIso?.trim().orEmpty()
      if (raw.isBlank()) return false
      return try {
        val expiryMillis = java.time.Instant.parse(raw).toEpochMilli()
        System.currentTimeMillis() > expiryMillis
      } catch (_: Throwable) {
        try {
          val expiryMillis = java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
          System.currentTimeMillis() > expiryMillis
        } catch (_: Throwable) {
          false
        }
      }
    }

  val hasActiveVip: Boolean
    get() = isVip && !isVipExpired

  val hasVerifiedBlueBadge: Boolean
    get() = isVerified || isVerifiedPublicFigure

  val hasAnyApprovedBadge: Boolean
    get() = hasVerifiedBlueBadge || hasActiveVip || isVerifiedBusiness || isWorker

  val isPendingBadgeAcceptance: Boolean
    get() = hasAnyApprovedBadge && (
      badgeApprovalStatus.equals("PENDING_ACCEPTANCE", ignoreCase = true) ||
        badgeDisplayStatus.equals("PENDING_ACCEPTANCE", ignoreCase = true)
      )

  val isBadgeHiddenByUser: Boolean
    get() = badgeDisplayStatus.equals("HIDDEN", ignoreCase = true) && !(isWorker && isWorkerBadgeMandatory)

  val canDisplayBadgesPublicly: Boolean
    get() {
      if (!hasAnyApprovedBadge) return false
      if (isWorker && isWorkerBadgeMandatory) return true
      if (badgeApprovalStatus.equals("PENDING_ACCEPTANCE", ignoreCase = true) ||
        badgeApprovalStatus.equals("DECLINED", ignoreCase = true) ||
        badgeDisplayStatus.equals("PENDING_ACCEPTANCE", ignoreCase = true)
      ) {
        return false
      }
      if (badgeDisplayStatus.equals("HIDDEN", ignoreCase = true) ||
        badgeDisplayStatus.equals("DECLINED", ignoreCase = true)
      ) {
        return false
      }
      return true
    }

  val activePublicBadges: List<OfficialVybeBadge>
    get() {
      if (!canDisplayBadgesPublicly) return emptyList()
      return buildList {
        if (hasVerifiedBlueBadge && !(isWorker && isWorkerBadgeMandatory && badgeDisplayStatus.equals("HIDDEN", true))) {
          add(OfficialVybeBadge.VERIFIED_BLUE)
        }
        if (isVerifiedBusiness && !(isWorker && isWorkerBadgeMandatory && badgeDisplayStatus.equals("HIDDEN", true))) {
          add(OfficialVybeBadge.SHOP_GOLD)
        } else if (hasActiveVip && !(isWorker && isWorkerBadgeMandatory && badgeDisplayStatus.equals("HIDDEN", true))) {
          add(OfficialVybeBadge.VIP_GOLD)
        }
        if (isWorker) {
          add(OfficialVybeBadge.WORKER_GREEN)
        }
      }
    }
}

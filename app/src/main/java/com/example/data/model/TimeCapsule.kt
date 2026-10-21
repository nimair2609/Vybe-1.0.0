package com.example.data.model

enum class CapsuleInvitationStatus {
  PENDING, ACCEPTED, DECLINED, NOT_APPLICABLE
}

data class TimeCapsule(
  val id: String,
  val title: String,
  val sender: User,
  val recipientSummary: String, // "To myself" or "To @priya_k" or "Bangalore College Crew"
  val sealedMessage: String,
  val mediaAttachmentUrl: String? = null,
  val mediaType: String = "PHOTO", // "PHOTO", "VIDEO", "NONE"
  val unlockDateFormatted: String,
  val unlockTimestampMs: Long,
  val isUnlocked: Boolean = false,
  val unlockedAtTimestampMs: Long? = null,
  val expiresAtTimestampMs: Long? = null, // 24-hour server-enforced expiry window
  val createdAtFormatted: String,
  val categoryTheme: String = "Graduation / Future Goal",
  val taggedUserIds: List<String> = emptyList(),
  val taggedUsernames: List<String> = emptyList(),
  val myInvitationStatus: CapsuleInvitationStatus = CapsuleInvitationStatus.NOT_APPLICABLE
)

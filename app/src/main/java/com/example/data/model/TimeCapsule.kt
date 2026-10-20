package com.example.data.model

data class TimeCapsule(
  val id: String,
  val title: String,
  val sender: User,
  val recipientSummary: String, // "To myself" or "To @priya_k" or "Bangalore College Crew"
  val sealedMessage: String,
  val mediaAttachmentUrl: String? = null,
  val unlockDateFormatted: String,
  val unlockTimestampMs: Long,
  val isUnlocked: Boolean = false,
  val createdAtFormatted: String,
  val categoryTheme: String = "Graduation / Future Goal"
)

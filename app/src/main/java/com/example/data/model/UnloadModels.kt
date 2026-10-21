package com.example.data.model

enum class UnloadRoomMode(val label: String) {
  TEXT_ONLY("Text Only"),
  VOICE_ONLY("Voice Only"),
  VOICE_AND_TEXT("Voice & Text")
}

data class UnloadMessage(
  val id: String,
  val senderName: String,
  val isAnonymous: Boolean,
  val text: String,
  val timestamp: String,
  val isMe: Boolean = false
)

data class UnloadRoom(
  val id: String,
  val title: String,
  val description: String,
  val topicTag: String, // e.g. "Work Burnout", "Silent Company", "Late Night Overthinking", "Post-Breakup Healing"
  val mode: UnloadRoomMode = UnloadRoomMode.VOICE_AND_TEXT,
  val activeListenersCount: Int = 12,
  val isHostOnline: Boolean = true,
  val is24hAvailable: Boolean = true,
  val safetyNotice: String = "Zero voice recording. Conversations are ephemeral. Hate speech or romantic harassment will result in immediate removal.",
  val recentMessages: List<UnloadMessage> = emptyList(),
  val hostId: String = "u_host",
  val hostName: String = "Vybe Host",
  val durationMinutes: Int = 45,
  val capacity: Int = 25,
  val scheduledTime: String = "Live Now",
  val joinerMessage: String = "Welcome to this listening space. Share your thoughts or simply listen.",
  val termsAgreed: Boolean = true
)

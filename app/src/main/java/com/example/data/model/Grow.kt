package com.example.data.model

import java.util.UUID

data class GrowJourney(
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val subtitle: String,
  val description: String,
  val durationDays: Int = 21,
  val category: String = "Mindfulness",
  val dailyPrompt: String = "",
  val currentDay: Int = 1,
  val isJoined: Boolean = false,
  val isCompleted: Boolean = false,
  val streakDays: Int = 0,
  val participantsCount: Int = 120
)

data class GrowReflection(
  val id: String = UUID.randomUUID().toString(),
  val journeyId: String,
  val dayNumber: Int,
  val reflectionText: String,
  val moodState: String = "Serene",
  val createdAtIso: String = "",
  val isSharedPublicly: Boolean = false
)

data class GrowBadge(
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val description: String,
  val category: String,
  val earnedDate: String,
  val badgeTier: String = "Gold" // Gold, Silver, Bronze
)

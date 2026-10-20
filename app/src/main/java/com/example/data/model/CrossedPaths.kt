package com.example.data.model

enum class CrossedPathType {
  MISSED_CONNECTION, VENUE_EVENT, BROAD_NEARBY
}

enum class ConnectionRequestStatus {
  PENDING, ACCEPTED, DECLINED, EXPIRED
}

data class CrossedPathEncounter(
  val id: String,
  val type: CrossedPathType,
  val title: String,
  val description: String,
  val broadLocation: String, // e.g. "Bandra West, Mumbai" or "Indiranagar 100ft Rd, Bengaluru"
  val venueOrEventName: String? = null,
  val encounterDate: String,
  val poster: User,
  val isAnonymous: Boolean = true,
  val expiresHoursRemaining: Int = 72,
  val potentialMatchesCount: Int = 1,
  val hasSentRequest: Boolean = false,
  val requestStatus: ConnectionRequestStatus = ConnectionRequestStatus.PENDING,
  val tags: List<String> = emptyList()
)

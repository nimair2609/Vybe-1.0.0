package com.example.data.model

data class SpiritualReflection(
  val id: String,
  val quote: String,
  val traditionOrAuthor: String,
  val theme: String, // Peace, Compassion, Mindfulness, Gratitude, Hope
  val audioCue: String = "Serene Temple Bells"
)

data class SpiritualIntention(
  val id: String,
  val text: String,
  val timestamp: String,
  val isFulfilled: Boolean = false
)

data class SpiritualGratitude(
  val id: String,
  val entry: String,
  val timestamp: String
)

enum class BreathingPhase(val label: String, val seconds: Int) {
  INHALE("Inhale Peace", 4),
  HOLD("Hold Stillness", 4),
  EXHALE("Release Tension", 4),
  REST("Rest in Calm", 4)
}

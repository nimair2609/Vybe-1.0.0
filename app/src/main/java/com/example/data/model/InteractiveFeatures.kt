package com.example.data.model

data class DoubleTakePrompt(
  val id: String,
  val promptQuestion: String,
  val category: String,
  val optionA: String = "Yes",
  val optionB: String = "No",
  val creatorId: String = "u_me",
  val myAnswer: String = "",
  val peerUser: User,
  val peerAnswer: String = "",
  val isMyAnswerSubmitted: Boolean = false,
  val isPeerAnswerSubmitted: Boolean = true,
  val isRevealed: Boolean = false,
  val matchReactionsCount: Int = 0,
  val isCreatorAccepted: Boolean = false,
  val isChatUnlocked: Boolean = false
)

data class PredictionOption(
  val id: Int,
  val text: String,
  val votesCount: Int = 0,
  val percentage: Int = 0
)

data class SocialPrediction(
  val id: String,
  val title: String,
  val category: String, // Cricket, Cinema, Pop Culture, Tech, City Life
  val closingTimeText: String,
  val durationHours: Int = 24,
  val totalVotes: Int = 0,
  val options: List<PredictionOption> = emptyList(),
  val userSelectedOptionId: Int? = null,
  val resolvedOptionId: Int? = null,
  val isResolved: Boolean = false,
  val resolutionNotes: String = "",
  val authorId: String = "u_me",
  val authorUsername: String = "sid_verma",
  val isDraft: Boolean = false
)

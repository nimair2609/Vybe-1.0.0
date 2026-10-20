package com.example.data.model

data class DoubleTakePrompt(
  val id: String,
  val promptQuestion: String,
  val category: String,
  val myAnswer: String = "",
  val peerUser: User,
  val peerAnswer: String = "",
  val isMyAnswerSubmitted: Boolean = false,
  val isPeerAnswerSubmitted: Boolean = true,
  val isRevealed: Boolean = false,
  val matchReactionsCount: Int = 0
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
  val category: String, // Cricket, Cinema, Pop Culture, Tech
  val closingTimeText: String,
  val totalVotes: Int,
  val options: List<PredictionOption>,
  val userSelectedOptionId: Int? = null,
  val resolvedOptionId: Int? = null,
  val isResolved: Boolean = false,
  val resolutionNotes: String = ""
)

package com.example.data.model

data class PublicAnswer(
  val id: String,
  val user: User,
  val answer: String,
  val timestamp: String = "Just now",
  val isConnected: Boolean = false
)

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
  val isChatUnlocked: Boolean = false,
  val isPublic: Boolean = false,
  val publicAnswers: List<PublicAnswer> = emptyList()
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
  val authorId: String = "",
  val authorUsername: String = "vyber",
  val isDraft: Boolean = false
)

data class LiveStreamComment(
  val id: String,
  val streamId: String,
  val authorId: String,
  val authorName: String,
  val content: String,
  val timestamp: String = "Just now",
  val createdAtIso: String? = null
)

data class LiveStreamSession(
  val id: String,
  val host: User,
  val title: String,
  val category: String = "Live Vibe",
  val visibility: String = "PUBLIC",
  val isLive: Boolean = true,
  val viewerCount: Int = 1,
  val thumbnailUrl: String? = null,
  val latestFrameBase64: String? = null,
  val startedAtIso: String? = null
)


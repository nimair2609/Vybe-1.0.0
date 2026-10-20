package com.example.data.model

data class DirectMessage(
  val id: String,
  val senderId: String,
  val text: String,
  val timestamp: String,
  val isMe: Boolean,
  val mediaUrl: String? = null,
  val isRead: Boolean = true,
  val replyToText: String? = null,
  val reactions: List<String> = emptyList() // Reactions: ❤️, 🔥, ⚡, 😂, 👏
)

data class Conversation(
  val id: String,
  val peerUser: User,
  val lastMessage: String,
  val lastMessageTimestamp: String,
  val unreadCount: Int = 0,
  val isGroup: Boolean = false,
  val groupName: String? = null,
  val messages: List<DirectMessage> = emptyList(),
  val isMessageRequest: Boolean = false,
  val isHidden: Boolean = false // Protected behind user PIN lock (Part O)
)

enum class CallState {
  IDLE, OUTGOING, INCOMING, CONNECTED, ENDED
}

data class CallSession(
  val peerUser: User,
  val isVideo: Boolean,
  val state: CallState,
  val durationSeconds: Int = 0,
  val isMuted: Boolean = false,
  val isSpeakerOn: Boolean = true,
  val isCameraOff: Boolean = false
)

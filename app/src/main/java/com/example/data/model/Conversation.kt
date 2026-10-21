package com.example.data.model

enum class MessageStatus {
  SENDING,
  SENT,
  FAILED
}

data class DirectMessage(
  val id: String,
  val senderId: String,
  val text: String,
  val timestamp: String,
  val isMe: Boolean,
  val mediaUrl: String? = null,
  val mediaType: String = "TEXT", // "TEXT", "IMAGE", "VIDEO", "GIF", "LOCATION", "FILE"
  val fileName: String? = null,
  val locationCoordinates: String? = null,
  val isRead: Boolean = true,
  val isEdited: Boolean = false,
  val isDeletedForEveryone: Boolean = false,
  val replyToId: String? = null,
  val replyToText: String? = null,
  val replyToSenderName: String? = null,
  val reactions: List<String> = emptyList(), // Reactions: ❤️, 🔥, ⚡, 😂, 👏
  val status: MessageStatus = MessageStatus.SENT,
  val clientId: String? = null
)

sealed class CallAvailabilityResult {
  object Available : CallAvailabilityResult()
  data class Unavailable(val reason: String) : CallAvailabilityResult()
  data class Offline(val reason: String = "User is currently offline.") : CallAvailabilityResult()
  data class Blocked(val reason: String = "Cannot call a blocked user.") : CallAvailabilityResult()
  data class Error(val message: String) : CallAvailabilityResult()
}

data class Conversation(
  val id: String,
  val peerUser: User,
  val lastMessage: String,
  val lastMessageTimestamp: String,
  val unreadCount: Int = 0,
  val isGroup: Boolean = false,
  val groupName: String? = null,
  val groupMembers: List<User> = emptyList(),
  val groupAdminId: String? = null,
  val messages: List<DirectMessage> = emptyList(),
  val isMessageRequest: Boolean = false,
  val isHidden: Boolean = false, // Protected behind user PIN lock (Part O)
  val chatTheme: String = "DEFAULT", // "DEFAULT", "CYBER_LUXE", "SUNSET", "NEON_VIOLET"
  val customNickname: String? = null,
  val isMuted: Boolean = false,
  val isBlocked: Boolean = false,
  val isRestricted: Boolean = false,
  val typingIndicator: String? = null
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
  val isCameraOff: Boolean = false,
  val isSignalingConnected: Boolean = false,
  val connectionStatusText: String = "Connecting signaling..."
)

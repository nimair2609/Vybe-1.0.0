package com.example.data.model

import java.util.UUID

data class VybeCircle(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val description: String,
  val category: String = "Lifestyle",
  val coverUrl: String = "",
  val avatarUrl: String = "",
  val isPrivate: Boolean = false,
  val membersCount: Int = 1,
  val ownerId: String = "",
  val ownerName: String = "",
  val isMember: Boolean = false,
  val isAdmin: Boolean = false,
  val tags: List<String> = emptyList(),
  val recentActivity: String = "Active now"
)

data class CircleMoment(
  val id: String = UUID.randomUUID().toString(),
  val circleId: String,
  val author: User,
  val content: String,
  val mediaUrl: String? = null,
  val mediaType: MediaType = MediaType.TEXT,
  val reactionsCount: Int = 0,
  val commentsCount: Int = 0,
  val createdAtIso: String = ""
)

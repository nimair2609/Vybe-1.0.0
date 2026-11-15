package com.example.util

import com.example.data.model.Community
import com.example.data.model.MediaType
import com.example.data.model.Post
import com.example.data.model.User

data class ProfileValidationResult(
  val isValid: Boolean,
  val displayName: String = "",
  val bio: String = "",
  val location: String = "",
  val website: String = "",
  val interests: List<String> = emptyList(),
  val errorMessage: String? = null
)

data class MediaUploadValidationResult(
  val isValid: Boolean,
  val errorMessage: String? = null
)

object ProfileInputValidator {
  const val MAX_DISPLAY_NAME_LENGTH = 50
  const val MAX_BIO_LENGTH = 240
  const val MAX_LOCATION_LENGTH = 80
  const val MAX_WEBSITE_LENGTH = 200
  const val MAX_INTEREST_COUNT = 10
  const val MAX_INTEREST_TAG_LENGTH = 28
  const val MAX_PROFILE_MEDIA_BYTES = 10L * 1024L * 1024L

  private val ALLOWED_IMAGE_MIMES = setOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
    "image/heic",
    "image/heif"
  )

  private val DOMAIN_REGEX = Regex(
    "^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9-]{1,63})+(?::\\d{1,5})?(?:/[^\\s]*)?$"
  )

  fun safeExternalUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val lower = trimmed.lowercase()
    if (
      lower.startsWith("javascript:") ||
      lower.startsWith("data:") ||
      lower.startsWith("vbscript:") ||
      lower.startsWith("file:") ||
      lower.startsWith("intent:") ||
      lower.startsWith("content:")
    ) {
      return null
    }
    val candidate = when {
      lower.startsWith("https://") || lower.startsWith("http://") -> trimmed
      trimmed.contains("://") -> return null
      DOMAIN_REGEX.matches(trimmed) -> "https://$trimmed"
      else -> return null
    }
    if (candidate.length > MAX_WEBSITE_LENGTH) return null
    return try {
      val uri = java.net.URI(candidate)
      val scheme = uri.scheme?.lowercase()
      val host = uri.host
      if ((scheme == "https" || scheme == "http") && !host.isNullOrBlank() && host.contains('.')) {
        candidate
      } else {
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun validateProfileEdit(
    displayName: String,
    bio: String,
    location: String,
    website: String,
    interestsRaw: List<String>
  ): ProfileValidationResult {
    val cleanDisplay = displayName.trim().replace(Regex("\\s+"), " ")
    if (cleanDisplay.isEmpty()) {
      return ProfileValidationResult(false, errorMessage = "Display name cannot be empty.")
    }
    if (cleanDisplay.length > MAX_DISPLAY_NAME_LENGTH) {
      return ProfileValidationResult(
        false,
        errorMessage = "Display name must be $MAX_DISPLAY_NAME_LENGTH characters or fewer."
      )
    }

    val cleanBio = bio.trim()
    if (cleanBio.length > MAX_BIO_LENGTH) {
      return ProfileValidationResult(
        false,
        errorMessage = "Bio must be $MAX_BIO_LENGTH characters or fewer."
      )
    }

    val cleanLocation = location.trim().replace(Regex("\\s+"), " ")
    if (cleanLocation.length > MAX_LOCATION_LENGTH) {
      return ProfileValidationResult(
        false,
        errorMessage = "Location must be $MAX_LOCATION_LENGTH characters or fewer."
      )
    }

    val rawWebsite = website.trim()
    val cleanWebsite = if (rawWebsite.isEmpty()) {
      ""
    } else {
      safeExternalUrl(rawWebsite)
        ?: return ProfileValidationResult(
          false,
          errorMessage = "Enter a valid website URL (e.g. https://example.com)."
        )
    }

    val seenLower = HashSet<String>()
    val cleanInterests = mutableListOf<String>()
    for (raw in interestsRaw) {
      val tag = raw.trim().removePrefix("#").trim()
      if (tag.isEmpty()) continue
      if (tag.length > MAX_INTEREST_TAG_LENGTH) {
        return ProfileValidationResult(
          false,
          errorMessage = "Each interest tag must be $MAX_INTEREST_TAG_LENGTH characters or fewer."
        )
      }
      val key = tag.lowercase()
      if (seenLower.add(key)) {
        cleanInterests.add(tag)
      }
      if (cleanInterests.size > MAX_INTEREST_COUNT) {
        return ProfileValidationResult(
          false,
          errorMessage = "You can add up to $MAX_INTEREST_COUNT interest tags."
        )
      }
    }

    return ProfileValidationResult(
      isValid = true,
      displayName = cleanDisplay,
      bio = cleanBio,
      location = cleanLocation,
      website = cleanWebsite,
      interests = cleanInterests
    )
  }

  fun validateProfileMediaUpload(mimeType: String?, sizeBytes: Long): MediaUploadValidationResult {
    if (sizeBytes <= 0L) {
      return MediaUploadValidationResult(false, "Selected image file is empty or unreadable.")
    }
    if (sizeBytes > MAX_PROFILE_MEDIA_BYTES) {
      return MediaUploadValidationResult(false, "Image must be 10 MB or smaller.")
    }
    val cleanMime = mimeType?.trim()?.lowercase()?.substringBefore(';').orEmpty()
    if (cleanMime.isNotEmpty() && cleanMime !in ALLOWED_IMAGE_MIMES) {
      return MediaUploadValidationResult(false, "Only JPEG, PNG, WebP, or HEIC images are supported.")
    }
    return MediaUploadValidationResult(true)
  }

  fun sanitizePostgrestSearchTerm(raw: String): String {
    val cleaned = raw
      .trim()
      .removePrefix("@")
      .removePrefix("#")
      .replace(Regex("[%(),*\\\\\"';]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
    return cleaned.take(64)
  }

  fun filterProfileTabPosts(
    tab: String,
    userPosts: List<Post>,
    savedPosts: List<Post>,
    allPosts: List<Post>,
    pinnedPostIds: List<String>,
    username: String,
    userId: String
  ): List<Post> {
    val activeUserPosts = userPosts.filter { !it.isArchived }
    return when (tab) {
      "Unfiltered" -> activeUserPosts.filter { it.isUnfiltered }
      "Tagged" -> {
        val cleanHandle = username.trim().removePrefix("@")
        if (cleanHandle.isBlank()) {
          emptyList()
        } else {
          allPosts.filter { p ->
            !p.isArchived &&
              !p.author.id.equals(userId, ignoreCase = true) &&
              (
                p.caption.contains("@$cleanHandle", ignoreCase = true) ||
                  p.tags.any {
                    it.removePrefix("#").removePrefix("@").equals(cleanHandle, ignoreCase = true)
                  }
                )
          }
        }
      }
      "Highlights" -> {
        if (pinnedPostIds.isEmpty()) {
          emptyList()
        } else {
          pinnedPostIds.mapNotNull { pinId ->
            activeUserPosts.firstOrNull { it.id == pinId }
          }
        }
      }
      "Saved" -> savedPosts.filter { !it.isArchived }
      else -> activeUserPosts.filter { !it.isUnfiltered }
    }
  }

  fun filterExploreCommunities(communities: List<Community>, query: String): List<Community> {
    val clean = query.trim().lowercase()
    return if (clean.isBlank()) {
      communities
    } else {
      communities.filter { comm ->
        comm.name.lowercase().contains(clean) ||
          comm.description.lowercase().contains(clean) ||
          comm.category.lowercase().contains(clean)
      }
    }
  }

  fun resolveTargetProfileOrNull(
    isOwnProfileContext: Boolean,
    currentUser: User?,
    profileUser: User?,
    allUsers: List<User>
  ): User? {
    if (isOwnProfileContext) {
      return currentUser
    }
    val target = profileUser ?: return null
    if (currentUser != null && target.id.equals(currentUser.id, ignoreCase = true)) {
      return currentUser
    }
    return allUsers.firstOrNull { it.id.equals(target.id, ignoreCase = true) } ?: target
  }
}

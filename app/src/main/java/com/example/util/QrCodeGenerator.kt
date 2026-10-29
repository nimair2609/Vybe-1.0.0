package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * QR Code Generator for VYBE profiles.
 * Generates high-contrast, error-corrected QR code bitmaps suitable for on-screen display,
 * camera scanning, and social sharing.
 */
object QrCodeGenerator {

  fun generateQrBitmap(
    content: String,
    sizePx: Int = 512,
    foregroundHex: Int = Color.WHITE,
    backgroundHex: Int = Color.TRANSPARENT
  ): Bitmap? {
    if (content.isBlank()) return null
    return try {
      val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
        put(EncodeHintType.MARGIN, 1)
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
      }
      val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val width = bitMatrix.width
      val height = bitMatrix.height
      val pixels = IntArray(width * height)

      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundHex else backgroundHex
        }
      }

      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
      }
    } catch (_: Throwable) {
      null
    }
  }

  const val CANONICAL_WEB_HOST = "ais-pre-lfp6zq6ltqfgleylo2u72h-707648560411.asia-northeast1.run.app"
  const val CANONICAL_DEV_HOST = "ais-dev-lfp6zq6ltqfgleylo2u72h-707648560411.asia-northeast1.run.app"
  const val CANONICAL_WEB_BASE = "https://$CANONICAL_WEB_HOST"

  private val SUPPORTED_WEB_HOSTS = setOf(
    CANONICAL_WEB_HOST.lowercase(),
    CANONICAL_DEV_HOST.lowercase(),
    "vybe.social",
    "www.vybe.social"
  )

  data class ParsedProfileDeepLink(
    val userId: String?,
    val username: String?
  )

  /**
   * Generates the official app deep link for instant Android routing (`vybe://profile/{userId}?u={username}`).
   * Never embeds auth tokens, emails, phone numbers, or private secrets.
   */
  fun buildProfileDeepLink(userId: String, username: String = ""): String {
    val cleanId = sanitizeSegment(userId)
    val cleanUser = sanitizeUsername(username)
    if (cleanId.isBlank() && cleanUser.isBlank()) return "vybe://profile"
    val targetSegment = cleanId.ifBlank { cleanUser }
    return if (cleanUser.isNotBlank() && cleanUser != targetSegment) {
      "vybe://profile/$targetSegment?u=${java.net.URLEncoder.encode(cleanUser, "UTF-8")}"
    } else {
      "vybe://profile/$targetSegment"
    }
  }

  /**
   * Generates the canonical HTTPS URL for VYBE profile QR codes and shares.
   * Uses the immutable user ID as the primary path segment so username changes never break old QR codes.
   */
  fun buildCanonicalProfileUrl(immutableUserId: String, username: String): String {
    val cleanId = sanitizeSegment(immutableUserId)
    val cleanUser = sanitizeUsername(username)
    val primarySegment = cleanId.ifBlank { cleanUser }
    if (primarySegment.isBlank()) return CANONICAL_WEB_BASE
    return if (cleanUser.isNotBlank()) {
      "$CANONICAL_WEB_BASE/profile/$primarySegment?u=${java.net.URLEncoder.encode(cleanUser, "UTF-8")}"
    } else {
      "$CANONICAL_WEB_BASE/profile/$primarySegment"
    }
  }

  /**
   * Backwards-compatible wrapper delegating to [buildCanonicalProfileUrl].
   */
  fun buildProfileShareUrl(username: String, userId: String): String {
    return buildCanonicalProfileUrl(immutableUserId = userId, username = username)
  }

  /**
   * Parses VYBE profile deep links (`vybe://profile/{id}?u={username}` and
   * `https://<canonical-host>/profile/{id}?u={username}` or `/u/{id}`) while rejecting unrelated domains.
   */
  fun parseProfileDeepLink(rawUri: String?): ParsedProfileDeepLink? {
    val trimmed = rawUri?.trim().orEmpty()
    if (trimmed.isBlank()) return null

    // Ignore OAuth/Auth recovery callbacks
    if (trimmed.contains("access_token=", ignoreCase = true) ||
      trimmed.contains("refresh_token=", ignoreCase = true) ||
      trimmed.startsWith("vybe://auth-callback", ignoreCase = true) ||
      trimmed.startsWith("vybe://reset-password", ignoreCase = true)
    ) {
      return null
    }

    return try {
      val uri = java.net.URI(trimmed)
      val scheme = uri.scheme?.lowercase().orEmpty()
      val host = uri.host?.lowercase().orEmpty()
      val pathSegments = (uri.path ?: "")
        .split("/")
        .map { it.trim() }
        .filter { it.isNotBlank() }

      val queryParams = parseQueryMap(uri.rawQuery)
      val queryUsername = sanitizeUsername(queryParams["u"] ?: queryParams["username"] ?: "")

      when (scheme) {
        "vybe" -> {
          if (host != "profile" && host != "u") return null
          val rawSegment = pathSegments.firstOrNull().orEmpty()
          classifyProfileTarget(rawSegment, queryUsername)
        }
        "https", "http" -> {
          if (host !in SUPPORTED_WEB_HOSTS) return null
          if (pathSegments.size < 2) return null
          val prefix = pathSegments[0].lowercase()
          if (prefix != "profile" && prefix != "u") return null
          val rawSegment = java.net.URLDecoder.decode(pathSegments[1], "UTF-8").trim()
          classifyProfileTarget(rawSegment, queryUsername)
        }
        else -> null
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun classifyProfileTarget(rawSegment: String, queryUsername: String): ParsedProfileDeepLink? {
    val cleanedSegment = sanitizeSegment(rawSegment.removePrefix("@"))
    if (cleanedSegment.isBlank() && queryUsername.isBlank()) return null

    val looksLikeUuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
      .matches(cleanedSegment)
    val looksLikeInternalId = looksLikeUuid || cleanedSegment.startsWith("u_")

    return if (looksLikeInternalId) {
      ParsedProfileDeepLink(
        userId = cleanedSegment,
        username = queryUsername.ifBlank { null }
      )
    } else if (cleanedSegment.startsWith("VYBE-", ignoreCase = true)) {
      // Legacy publicVybeId QR fallback: prefer username query parameter if present
      ParsedProfileDeepLink(
        userId = cleanedSegment,
        username = queryUsername.ifBlank { null }
      )
    } else {
      ParsedProfileDeepLink(
        userId = null,
        username = cleanedSegment.ifBlank { queryUsername }.ifBlank { null }
      )
    }
  }

  private fun parseQueryMap(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split("&").mapNotNull { pair ->
      val idx = pair.indexOf('=')
      if (idx <= 0) null
      else {
        val k = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8").trim()
        val v = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8").trim()
        k to v
      }
    }.toMap()
  }

  private fun sanitizeSegment(raw: String): String {
    return raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "")
  }

  private fun sanitizeUsername(raw: String): String {
    return raw.trim().removePrefix("@").replace(Regex("[^A-Za-z0-9._]"), "")
  }
}

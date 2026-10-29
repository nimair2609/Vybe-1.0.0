package com.example.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Authoritative timestamp parsing and relative formatting utility for VYBE.
 *
 * Produces consistent compact relative strings (`Just now`, `2m`, `45m`, `2h`, `12h`, `1d`, `3d`, `2w`,
 * or localized calendar date for older items) from Supabase ISO-8601 `TIMESTAMPTZ` values, and updates
 * dynamically in Compose without extra database requests.
 */
object TimestampUtils {

  fun nowIsoUtc(): String = formatIsoUtc(System.currentTimeMillis())

  fun currentIsoTimestamp(): String = nowIsoUtc()

  fun formatIsoUtc(epochMillis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(epochMillis))
  }

  /**
   * Parses ISO-8601 timestamps from Supabase (`2026-10-24T11:20:05.123456+00:00`, `...Z`, `+05:30`, etc.)
   * into epoch milliseconds safely across API 24+.
   */
  fun parseIsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val raw = iso.trim()
    if (raw.equals("null", ignoreCase = true)) return null

    // Normalize fractional seconds and timezone offset for SimpleDateFormat compatibility on API 24+
    val withoutFraction = raw.replace(Regex("\\.\\d+"), "")
    val normalizedSpace = withoutFraction.replace(' ', 'T')

    return try {
      when {
        normalizedSpace.endsWith("Z", ignoreCase = true) -> {
          val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
          }
          sdf.parse(normalizedSpace.dropLast(1) + "Z")?.time
        }
        normalizedSpace.endsWith("+00:00") || normalizedSpace.endsWith("-00:00") || normalizedSpace.endsWith("+00") -> {
          val base = normalizedSpace
            .removeSuffix("+00:00")
            .removeSuffix("-00:00")
            .removeSuffix("+00")
          val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
          }
          sdf.parse(base)?.time
        }
        Regex(".*[+-]\\d{2}:\\d{2}$").matches(normalizedSpace) -> {
          val compactTz = normalizedSpace.substring(0, normalizedSpace.length - 3) +
            normalizedSpace.substring(normalizedSpace.length - 2)
          val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
          sdf.parse(compactTz)?.time
        }
        Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(normalizedSpace) -> {
          val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
          }
          sdf.parse(normalizedSpace)?.time
        }
        else -> {
          val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
          }
          sdf.parse(normalizedSpace)?.time
        }
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Formats an authoritative creation timestamp into VYBE's relative format:
   * - `< 45s` -> `Just now`
   * - `< 60m` -> `2m`, `45m`
   * - `< 24h` -> `2h`, `12h`
   * - `< 7d`  -> `1d`, `3d`
   * - `< 30d` -> `1w`, `2w`, `4w`
   * - older   -> `Oct 24` (same year) or `Oct 24, 2025` (previous year)
   */
  fun formatRelative(iso: String?, nowMillis: Long = System.currentTimeMillis(), fallback: String = ""): String {
    val createdMillis = parseIsoMillis(iso)
    if (createdMillis == null) {
      val cleanFallback = fallback.trim()
      if (cleanFallback.isBlank() || cleanFallback.equals("null", true) || cleanFallback.equals("Synced", true)) {
        return ""
      }
      // Normalize legacy "5m ago" strings if fallback is already human-readable
      return cleanFallback.removeSuffix(" ago")
    }

    val diffSeconds = ((nowMillis - createdMillis) / 1000L).coerceAtLeast(0L)
    return when {
      diffSeconds < 45L -> "Just now"
      diffSeconds < 3_600L -> "${(diffSeconds / 60L).coerceAtLeast(1L)}m"
      diffSeconds < 86_400L -> "${diffSeconds / 3_600L}h"
      diffSeconds < 604_800L -> "${diffSeconds / 86_400L}d"
      diffSeconds < 2_592_000L -> "${diffSeconds / 604_800L}w"
      else -> formatCalendarDate(createdMillis, nowMillis)
    }
  }

  /**
   * Formats message timestamps cleanly: compact relative + local clock for recent messages,
   * preserving status labels like `Sending...` or `Failed`.
   */
  fun formatMessageTimestamp(
    iso: String?,
    nowMillis: Long = System.currentTimeMillis(),
    fallback: String = ""
  ): String {
    if (fallback == "Sending..." || fallback == "Failed") return fallback
    val millis = parseIsoMillis(iso) ?: return formatRelative(null, nowMillis, fallback)
    val rel = formatRelative(iso, nowMillis, fallback)
    val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val localClock = clockFmt.format(Date(millis))
    return if (rel == "Just now" || rel.endsWith("m")) {
      "$rel • $localClock"
    } else {
      "$rel, $localClock"
    }
  }

  fun formatRelative(iso: String?, fallback: String): String =
    formatRelative(iso = iso, nowMillis = System.currentTimeMillis(), fallback = fallback)

  fun formatMessageTimestamp(iso: String?, fallback: String): String =
    formatMessageTimestamp(iso = iso, nowMillis = System.currentTimeMillis(), fallback = fallback)

  fun formatLastSeen(lastSeenIso: String?): String {
    if (lastSeenIso.isNullOrBlank()) return ""
    val rel = formatRelative(lastSeenIso, fallback = "")
    return when {
      rel.isBlank() -> ""
      rel == "Just now" -> "Active just now"
      else -> "Active $rel ago"
    }
  }

  fun formatMessageTime(iso: String?, fallback: String = ""): String =
    formatMessageTimestamp(iso = iso, nowMillis = System.currentTimeMillis(), fallback = fallback)

  /**
   * Formats real account `created_at` into `"Joined October 2026"`.
   * Never substitutes current time when timestamp is missing.
   */
  fun formatJoinedMonthYear(createdAtIso: String?, fallback: String = ""): String {
    val millis = parseIsoMillis(createdAtIso)
    if (millis != null) {
      val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
      return "Joined ${sdf.format(Date(millis))}"
    }
    val clean = fallback.trim()
    if (clean.isBlank() || clean.equals("null", true)) return "VYBE Member"
    return if (clean.startsWith("Joined", ignoreCase = true)) clean else "Joined $clean"
  }

  /**
   * Checks whether `lastSeenIso` represents an active online session within [thresholdSeconds].
   * Uses a tight 90-second active window paired with a 35-second foreground heartbeat.
   */
  fun isRecentlyOnline(lastSeenIso: String?, thresholdSeconds: Long = 90L, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val seenMillis = parseIsoMillis(lastSeenIso) ?: return false
    val diffSec = (nowMillis - seenMillis) / 1000L
    return diffSec in -45L..thresholdSeconds
  }

  private fun formatCalendarDate(epochMillis: Long, nowMillis: Long): String {
    val targetCal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val pattern = if (targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) "MMM d" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))
  }
}

/**
 * Dynamically updates a relative timestamp in Jetpack Compose every 30 seconds
 * without triggering any network or database requests.
 */
@Composable
fun rememberRelativeTimestamp(createdAtIso: String?, fallback: String = ""): String {
  var nowTick by remember(createdAtIso) { mutableLongStateOf(System.currentTimeMillis()) }

  LaunchedEffect(createdAtIso) {
    if (!createdAtIso.isNullOrBlank()) {
      while (true) {
        delay(30_000L)
        nowTick = System.currentTimeMillis()
      }
    }
  }

  return remember(createdAtIso, fallback, nowTick) {
    TimestampUtils.formatRelative(createdAtIso, nowTick, fallback)
  }
}

@Composable
fun rememberMessageTimestamp(createdAtIso: String?, fallback: String = ""): String {
  var nowTick by remember(createdAtIso) { mutableLongStateOf(System.currentTimeMillis()) }

  LaunchedEffect(createdAtIso) {
    if (!createdAtIso.isNullOrBlank()) {
      while (true) {
        delay(30_000L)
        nowTick = System.currentTimeMillis()
      }
    }
  }

  return remember(createdAtIso, fallback, nowTick) {
    TimestampUtils.formatMessageTimestamp(createdAtIso, nowTick, fallback)
  }
}

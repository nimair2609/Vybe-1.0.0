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
 * Produces accurate human-readable relative strings (`Just now`, `1 minute ago`, `2 minutes ago`,
 * `1 hour ago`, `3 hours ago`, `Yesterday`, `3 days ago`, or localized calendar date for older items)
 * from Supabase ISO-8601 UTC/offset `TIMESTAMPTZ` values converted to the user's local timezone,
 * clamps slight future server/client clock skew, and updates dynamically in Compose every 30 seconds.
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
   * Parses ISO-8601 timestamps from Supabase (`2026-10-24T11:20:05.123456+00:00`, `...Z`, `+05:30`,
   * `2026-10-24 11:20:05+00`, etc.) into UTC epoch milliseconds safely across API 24+.
   */
  fun parseIsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val raw = iso.trim()
    if (raw.equals("null", ignoreCase = true)) return null

    // Extract optional fractional milliseconds (.123456 -> 123ms) before stripping fraction
    val fractionMatch = Regex("\\.(\\d+)").find(raw)
    val subSecondMillis = fractionMatch?.groupValues?.getOrNull(1)
      ?.padEnd(3, '0')
      ?.take(3)
      ?.toLongOrNull() ?: 0L

    val withoutFraction = raw.replace(Regex("\\.\\d+"), "")
    val normalizedSpace = withoutFraction.replace(' ', 'T')

    val parsedSecondsMillis = try {
      when {
        normalizedSpace.endsWith("Z", ignoreCase = true) -> {
          val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
          }
          sdf.parse(normalizedSpace.dropLast(1) + "Z")?.time
        }
        normalizedSpace.endsWith("+00:00") || normalizedSpace.endsWith("-00:00") ||
          normalizedSpace.endsWith("+00") || normalizedSpace.endsWith("-00") -> {
          val base = normalizedSpace
            .removeSuffix("+00:00")
            .removeSuffix("-00:00")
            .removeSuffix("+00")
            .removeSuffix("-00")
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
        Regex(".*[+-]\\d{4}$").matches(normalizedSpace) -> {
          val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
          sdf.parse(normalizedSpace)?.time
        }
        Regex(".*[+-]\\d{2}$").matches(normalizedSpace) -> {
          val compactTz = "${normalizedSpace}00"
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

    return parsedSecondsMillis?.plus(subSecondMillis)
  }

  /**
   * Formats an authoritative creation timestamp into VYBE's human-readable relative format
   * in the user's local timezone:
   * - Future or `< 60s` -> `Just now`
   * - `< 60m`           -> `1 minute ago`, `2 minutes ago`
   * - `< 24h`           -> `1 hour ago`, `3 hours ago`
   * - Yesterday         -> `Yesterday`
   * - `2..6 days`       -> `2 days ago` .. `6 days ago`
   * - Older             -> `Oct 24` (same year) or `Oct 24, 2025` (previous year)
   */
  fun formatRelative(
    iso: String?,
    nowMillis: Long = System.currentTimeMillis(),
    fallback: String = "",
    timeZone: TimeZone = TimeZone.getDefault()
  ): String {
    val createdMillis = parseIsoMillis(iso)
    if (createdMillis == null) {
      val cleanFallback = fallback.trim()
      if (cleanFallback.isBlank() || cleanFallback.equals("null", true) || cleanFallback.equals("Synced", true)) {
        return ""
      }
      if (cleanFallback.startsWith("in ", ignoreCase = true)) {
        return "Just now"
      }
      return normalizeLegacyRelativeFallback(cleanFallback)
    }

    // Clamp future timestamps caused by server/client clock skew
    if (createdMillis >= nowMillis) {
      return "Just now"
    }

    val diffSeconds = (nowMillis - createdMillis) / 1000L
    if (diffSeconds < 60L) {
      return "Just now"
    }
    if (diffSeconds < 3_600L) {
      val minutes = (diffSeconds / 60L).coerceAtLeast(1L)
      return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
    }
    if (diffSeconds < 86_400L) {
      val hours = (diffSeconds / 3_600L).coerceAtLeast(1L)
      return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    }

    val calendarDayDiff = localCalendarDayDiff(createdMillis, nowMillis, timeZone)
    return when {
      calendarDayDiff == 1 || diffSeconds < 172_800L -> "Yesterday"
      calendarDayDiff in 2..6 -> "$calendarDayDiff days ago"
      diffSeconds < 604_800L -> "${(diffSeconds / 86_400L).coerceAtLeast(2L)} days ago"
      else -> formatCalendarDate(createdMillis, nowMillis, timeZone)
    }
  }

  private fun normalizeLegacyRelativeFallback(raw: String): String {
    if (raw.equals("Just now", ignoreCase = true) || raw.equals("Yesterday", ignoreCase = true)) {
      return if (raw.equals("Yesterday", ignoreCase = true)) "Yesterday" else "Just now"
    }
    val compactMatch = Regex("^(\\d+)\\s*([smhdw])(?:\\s+ago)?$", RegexOption.IGNORE_CASE).matchEntire(raw)
    if (compactMatch != null) {
      val n = compactMatch.groupValues[1].toLongOrNull() ?: return raw
      return when (compactMatch.groupValues[2].lowercase()) {
        "s" -> "Just now"
        "m" -> if (n <= 1L) "1 minute ago" else "$n minutes ago"
        "h" -> if (n <= 1L) "1 hour ago" else "$n hours ago"
        "d" -> if (n <= 1L) "Yesterday" else "$n days ago"
        "w" -> "${n * 7L} days ago"
        else -> raw
      }
    }
    return raw
  }

  private fun localCalendarDayDiff(earlierMillis: Long, laterMillis: Long, timeZone: TimeZone): Int {
    val startCal = Calendar.getInstance(timeZone).apply {
      timeInMillis = earlierMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val endCal = Calendar.getInstance(timeZone).apply {
      timeInMillis = laterMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val diffDays = ((endCal.timeInMillis - startCal.timeInMillis) / 86_400_000L).toInt()
    return diffDays.coerceAtLeast(0)
  }

  /**
   * Formats message timestamps cleanly in the user's local timezone while preserving
   * transient status labels (`Sending...`, `Failed`).
   */
  fun formatMessageTimestamp(
    iso: String?,
    nowMillis: Long = System.currentTimeMillis(),
    fallback: String = "",
    timeZone: TimeZone = TimeZone.getDefault()
  ): String {
    if (fallback == "Sending..." || fallback == "Failed") return fallback
    val millis = parseIsoMillis(iso) ?: return formatRelative(null, nowMillis, fallback, timeZone)
    val rel = formatRelative(iso, nowMillis, fallback, timeZone)
    val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
      this.timeZone = timeZone
    }
    val localClock = clockFmt.format(Date(millis))
    return if (rel == "Just now" || rel.endsWith("minute ago") || rel.endsWith("minutes ago")) {
      "$rel • $localClock"
    } else {
      "$rel, $localClock"
    }
  }

  fun formatRelative(iso: String?, fallback: String): String =
    formatRelative(iso = iso, nowMillis = System.currentTimeMillis(), fallback = fallback)

  fun formatMessageTimestamp(iso: String?, fallback: String): String =
    formatMessageTimestamp(iso = iso, nowMillis = System.currentTimeMillis(), fallback = fallback)

  fun formatLastSeen(lastSeenIso: String?, nowMillis: Long = System.currentTimeMillis()): String {
    if (lastSeenIso.isNullOrBlank()) return ""
    val rel = formatRelative(lastSeenIso, nowMillis = nowMillis, fallback = "")
    return when {
      rel.isBlank() -> ""
      rel == "Just now" -> "Active just now"
      rel == "Yesterday" -> "Active yesterday"
      rel.endsWith("ago") -> "Active $rel"
      else -> "Active $rel"
    }
  }

  fun formatMessageTime(iso: String?, fallback: String = ""): String =
    formatMessageTimestamp(iso = iso, nowMillis = System.currentTimeMillis(), fallback = fallback)

  /**
   * Formats real account `created_at` into `"Joined October 2026"` in local timezone.
   */
  fun formatJoinedMonthYear(createdAtIso: String?, fallback: String = ""): String {
    val millis = parseIsoMillis(createdAtIso)
    if (millis != null) {
      val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
      }
      return "Joined ${sdf.format(Date(millis))}"
    }
    val clean = fallback.trim()
    if (clean.isBlank() || clean.equals("null", true)) return "VYBE Member"
    return if (clean.startsWith("Joined", ignoreCase = true)) clean else "Joined $clean"
  }

  /**
   * Checks whether `lastSeenIso` represents an active online session within [thresholdSeconds].
   */
  fun isRecentlyOnline(lastSeenIso: String?, thresholdSeconds: Long = 90L, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val seenMillis = parseIsoMillis(lastSeenIso) ?: return false
    val diffSec = (nowMillis - seenMillis) / 1000L
    return diffSec in -45L..thresholdSeconds
  }

  private fun formatCalendarDate(epochMillis: Long, nowMillis: Long, timeZone: TimeZone): String {
    val targetCal = Calendar.getInstance(timeZone).apply { timeInMillis = epochMillis }
    val nowCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
    val pattern = if (targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) "MMM d" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).apply {
      this.timeZone = timeZone
    }.format(Date(epochMillis))
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

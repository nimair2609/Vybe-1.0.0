package com.example.data.model

import com.example.data.remote.SupabaseNetworkClient
import com.squareup.moshi.Json
import kotlin.math.roundToInt

enum class MusicCategory(val wireKey: String, val displayLabel: String, val isMoodBased: Boolean = false) {
  TRENDING("TRENDING", "Trending"),
  HINDI("HINDI", "Hindi"),
  BOLLYWOOD("BOLLYWOOD", "Bollywood"),
  PUNJABI("PUNJABI", "Punjabi"),
  INTERNATIONAL("INTERNATIONAL", "International"),
  INSTRUMENTAL("INSTRUMENTAL", "Instrumental"),
  CHILL("CHILL", "Chill", isMoodBased = true),
  UPBEAT("UPBEAT", "Upbeat", isMoodBased = true),
  ROMANTIC("ROMANTIC", "Romantic", isMoodBased = true),
  ENERGETIC("ENERGETIC", "Energetic", isMoodBased = true);

  companion object {
    fun fromWire(raw: String?): MusicCategory? {
      if (raw.isNullOrBlank()) return null
      val normalized = raw.trim().uppercase()
      return entries.firstOrNull { it.wireKey == normalized }
    }
  }
}

/**
 * Represents a track returned from a properly licensed music catalog adapter.
 * Tracks without verified sync licensing ([isSyncLicensed] == false) are never playable in production.
 */
data class LicensedMusicTrack(
  @param:Json(name = "id") val id: String,
  @param:Json(name = "provider") val provider: String,
  @param:Json(name = "title") val title: String,
  @param:Json(name = "artist") val artist: String,
  @param:Json(name = "albumTitle") val albumTitle: String? = null,
  @param:Json(name = "artworkUrl") val artworkUrl: String? = null,
  @param:Json(name = "previewStreamUrl") val previewStreamUrl: String? = null,
  @param:Json(name = "durationMs") val durationMs: Long = 30_000L,
  @param:Json(name = "genre") val genre: String? = null,
  @param:Json(name = "mood") val mood: String? = null,
  @param:Json(name = "language") val language: String? = null,
  @param:Json(name = "isSyncLicensed") val isSyncLicensed: Boolean = false,
  @param:Json(name = "isAvailable") val isAvailable: Boolean = true,
  @param:Json(name = "licenseSummary") val licenseSummary: String? = null,
  @param:Json(name = "attributionUrl") val attributionUrl: String? = null
) {
  val isPlayableAndLicensed: Boolean
    get() = isSyncLicensed && isAvailable && !previewStreamUrl.isNullOrBlank()

  val formattedDuration: String
    get() = formatMusicMillis(durationMs)
}

/**
 * Persisted music selection, segment trim, and dual-volume settings saved with a Story or Reel.
 * Stores provider track ID and metadata rather than duplicating raw music files in Supabase Storage.
 */
data class MusicAttachment(
  @param:Json(name = "track_id") val trackId: String,
  @param:Json(name = "provider") val provider: String,
  @param:Json(name = "title") val title: String,
  @param:Json(name = "artist") val artist: String,
  @param:Json(name = "album_title") val albumTitle: String? = null,
  @param:Json(name = "artwork_url") val artworkUrl: String? = null,
  @param:Json(name = "stream_url") val streamUrl: String? = null,
  @param:Json(name = "track_duration_ms") val trackDurationMs: Long = 30_000L,
  @param:Json(name = "start_time_ms") val startTimeMs: Long = 0L,
  @param:Json(name = "clip_duration_ms") val clipDurationMs: Long = 15_000L,
  @param:Json(name = "music_volume") val musicVolume: Float = 0.85f,
  @param:Json(name = "original_audio_volume") val originalAudioVolume: Float = 0.6f,
  @param:Json(name = "genre") val genre: String? = null,
  @param:Json(name = "mood") val mood: String? = null,
  @param:Json(name = "is_rendered_with_mixed_audio") val isRenderedWithMixedAudio: Boolean = false,
  @param:Json(name = "license_summary") val licenseSummary: String? = null
) {
  val displayLabel: String
    get() = if (artist.isNotBlank()) "$title • $artist" else title

  val endTimeMs: Long
    get() = (startTimeMs + clipDurationMs).coerceAtMost(trackDurationMs.coerceAtLeast(startTimeMs + 1000L))

  val formattedSegmentRange: String
    get() = "${formatMusicMillis(startTimeMs)} – ${formatMusicMillis(endTimeMs)}"

  fun sanitized(): MusicAttachment {
    val safeTrackDur = trackDurationMs.coerceAtLeast(1_000L)
    val safeStart = startTimeMs.coerceIn(0L, (safeTrackDur - 1_000L).coerceAtLeast(0L))
    val maxRemaining = (safeTrackDur - safeStart).coerceAtLeast(1_000L)
    val safeClipDur = clipDurationMs.coerceIn(1_000L, minOf(60_000L, maxRemaining))
    return copy(
      trackId = trackId.trim(),
      provider = provider.trim().ifBlank { "licensed" },
      title = title.trim(),
      artist = artist.trim(),
      trackDurationMs = safeTrackDur,
      startTimeMs = safeStart,
      clipDurationMs = safeClipDur,
      musicVolume = ((musicVolume.coerceIn(0f, 1f) * 100f).roundToInt() / 100f),
      originalAudioVolume = ((originalAudioVolume.coerceIn(0f, 1f) * 100f).roundToInt() / 100f)
    )
  }

  fun toLicensedTrack(): LicensedMusicTrack = LicensedMusicTrack(
    id = trackId,
    provider = provider,
    title = title,
    artist = artist,
    albumTitle = albumTitle,
    artworkUrl = artworkUrl,
    previewStreamUrl = streamUrl,
    durationMs = trackDurationMs,
    genre = genre,
    mood = mood,
    isSyncLicensed = true,
    isAvailable = !streamUrl.isNullOrBlank(),
    licenseSummary = licenseSummary
  )

  companion object {
    fun fromTrack(
      track: LicensedMusicTrack,
      startTimeMs: Long = 0L,
      clipDurationMs: Long = minOf(15_000L, track.durationMs.coerceAtLeast(1_000L)),
      musicVolume: Float = 0.85f,
      originalAudioVolume: Float = 0.6f
    ): MusicAttachment {
      return MusicAttachment(
        trackId = track.id,
        provider = track.provider,
        title = track.title,
        artist = track.artist,
        albumTitle = track.albumTitle,
        artworkUrl = track.artworkUrl,
        streamUrl = track.previewStreamUrl,
        trackDurationMs = track.durationMs,
        startTimeMs = startTimeMs,
        clipDurationMs = clipDurationMs,
        musicVolume = musicVolume,
        originalAudioVolume = originalAudioVolume,
        genre = track.genre,
        mood = track.mood,
        licenseSummary = track.licenseSummary
      ).sanitized()
    }
  }
}

fun formatMusicMillis(ms: Long): String {
  val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
  val mins = totalSec / 60
  val secs = totalSec % 60
  return "%d:%02d".format(mins, secs)
}

/**
 * Deterministic, Moshi-backed serializer for cross-device music metadata persistence.
 * Supports both dedicated Supabase columns AND backward-compatible `music_title` envelope encoding
 * when older live Supabase deployments have not yet applied migration 20261028000003.
 */
object MusicMetadataCodec {
  private const val ENVELOPE_DELIMITER = " ||vybe_music:"

  private val adapter by lazy {
    SupabaseNetworkClient.moshi.adapter(MusicAttachment::class.java)
  }

  fun toJson(attachment: MusicAttachment): String {
    return adapter.toJson(attachment.sanitized())
  }

  fun fromJson(rawJson: String?): MusicAttachment? {
    if (rawJson.isNullOrBlank()) return null
    return try {
      adapter.fromJson(rawJson.trim())?.takeIf { it.trackId.isNotBlank() && it.title.isNotBlank() }?.sanitized()
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Encodes human-readable title + compact structured metadata for backward-compatible fallback
   * in the legacy `music_title` column when extended music columns are unavailable.
   */
  fun encodeLegacyMusicTitleColumn(displayTitle: String?, attachment: MusicAttachment?): String? {
    if (attachment == null) return displayTitle?.takeIf { it.isNotBlank() }
    val cleanAttachment = attachment.sanitized()
    val humanLabel = displayTitle?.takeIf { it.isNotBlank() } ?: cleanAttachment.displayLabel
    return "$humanLabel$ENVELOPE_DELIMITER${toJson(cleanAttachment)}"
  }

  /**
   * Resolves clean `(displayTitle, MusicAttachment?)` from either dedicated Supabase columns
   * or a fallback `music_title` wire value, guaranteeing existing Stories/Reels without music
   * or with plain audio titles work identically.
   */
  fun resolveFromColumns(
    rawMusicTitle: String?,
    musicTrackId: String? = null,
    musicProvider: String? = null,
    musicArtist: String? = null,
    musicArtworkUrl: String? = null,
    musicStreamUrl: String? = null,
    musicStartMs: Int? = null,
    musicDurationMs: Int? = null,
    musicVolume: Double? = null,
    originalAudioVolume: Double? = null,
    musicMetadataJson: String? = null
  ): Pair<String?, MusicAttachment?> {
    // 1. Strip any legacy envelope from rawMusicTitle first so UI labels are always clean
    val hasEnvelope = rawMusicTitle?.contains(ENVELOPE_DELIMITER) == true
    val cleanTitle = if (hasEnvelope) {
      rawMusicTitle!!.substringBefore(ENVELOPE_DELIMITER).trim().ifBlank { null }
    } else {
      rawMusicTitle?.trim()?.ifBlank { null }
    }
    val envelopeJson = if (hasEnvelope) {
      rawMusicTitle!!.substringAfter(ENVELOPE_DELIMITER, "").trim().ifBlank { null }
    } else {
      null
    }

    // 2. Prefer explicit metadata JSON if present
    fromJson(musicMetadataJson)?.let { parsed ->
      val finalLabel = cleanTitle ?: parsed.displayLabel
      return finalLabel to parsed
    }

    // 3. Construct from dedicated SQL columns if music_track_id is populated
    if (!musicTrackId.isNullOrBlank()) {
      val baseFromEnvelope = fromJson(envelopeJson)
      val resolvedSongTitle = baseFromEnvelope?.title
        ?: cleanTitle?.substringBefore("•")?.trim()?.ifBlank { null }
        ?: cleanTitle
        ?: "Audio Track"
      val resolvedArtist = musicArtist?.takeIf { it.isNotBlank() }
        ?: baseFromEnvelope?.artist
        ?: cleanTitle?.takeIf { it.contains("•") }?.substringAfter("•")?.trim().orEmpty()

      val constructed = MusicAttachment(
        trackId = musicTrackId.trim(),
        provider = musicProvider?.ifBlank { null } ?: baseFromEnvelope?.provider ?: "licensed",
        title = resolvedSongTitle,
        artist = resolvedArtist,
        albumTitle = baseFromEnvelope?.albumTitle,
        artworkUrl = musicArtworkUrl ?: baseFromEnvelope?.artworkUrl,
        streamUrl = musicStreamUrl ?: baseFromEnvelope?.streamUrl,
        trackDurationMs = baseFromEnvelope?.trackDurationMs
          ?: ((musicStartMs ?: 0).toLong() + (musicDurationMs ?: 15_000).toLong()).coerceAtLeast(15_000L),
        startTimeMs = (musicStartMs ?: 0).toLong(),
        clipDurationMs = (musicDurationMs ?: 15_000).toLong(),
        musicVolume = musicVolume?.toFloat() ?: baseFromEnvelope?.musicVolume ?: 0.85f,
        originalAudioVolume = originalAudioVolume?.toFloat() ?: baseFromEnvelope?.originalAudioVolume ?: 0.6f,
        genre = baseFromEnvelope?.genre,
        mood = baseFromEnvelope?.mood,
        isRenderedWithMixedAudio = baseFromEnvelope?.isRenderedWithMixedAudio ?: true,
        licenseSummary = baseFromEnvelope?.licenseSummary
      ).sanitized()
      return (cleanTitle ?: constructed.displayLabel) to constructed
    }

    // 4. Fallback to envelope stored inside `music_title`
    fromJson(envelopeJson)?.let { envParsed ->
      return (cleanTitle ?: envParsed.displayLabel) to envParsed
    }

    // 5. Plain legacy string or null (no structured licensed track attached)
    return cleanTitle to null
  }
}

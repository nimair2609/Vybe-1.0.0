package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LicensedMusicTrack
import com.example.data.model.MusicAttachment
import com.example.data.model.MusicCategory
import com.example.data.model.formatMusicMillis
import com.example.data.music.MusicCatalogRepository
import com.example.data.music.MusicCatalogResult
import com.example.data.music.MusicCatalogStatus
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeViolet
import com.example.util.music.BindMusicPreviewLifecycle
import com.example.util.music.MusicPreviewController
import com.example.util.music.MusicPreviewState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Instagram-style Music Sticker displaying album artwork, equalizer pulse icon, track title, and artist.
 * Used in Story creation preview, Story viewer, and Reel composer overlays.
 */
@Composable
fun VybeMusicSticker(
  title: String,
  artist: String,
  artworkUrl: String? = null,
  segmentLabel: String? = null,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color.Black.copy(alpha = 0.72f),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
    modifier = modifier
      .then(
        if (onClick != null) {
          Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
        } else {
          Modifier
        }
      )
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(9.dp))
          .background(Brush.linearGradient(listOf(VybeViolet, VybeMagenta, VybeBrandCyan))),
        contentAlignment = Alignment.Center
      ) {
        if (!artworkUrl.isNullOrBlank()) {
          AsyncImage(
            model = artworkUrl,
            contentDescription = "$title album art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "Music sticker",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f, fill = false)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = VybeBrandCyan,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        val subtitle = buildString {
          if (artist.isNotBlank()) append(artist)
          if (!segmentLabel.isNullOrBlank()) {
            if (isNotEmpty()) append(" • ")
            append(segmentLabel)
          }
        }
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      if (onClick != null) {
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = "Open track details",
          tint = Color.White.copy(alpha = 0.8f),
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPickerSheet(
  initialAttachment: MusicAttachment? = null,
  showOriginalAudioControl: Boolean = false,
  defaultClipDurationMs: Long = 15_000L,
  onDismiss: () -> Unit,
  onMusicSelected: (MusicAttachment) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = {
      MusicPreviewController.stop()
      onDismiss()
    },
    sheetState = sheetState,
    contentWindowInsets = { WindowInsets(0.dp) },
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    MusicPickerContent(
      initialAttachment = initialAttachment,
      showOriginalAudioControl = showOriginalAudioControl,
      defaultClipDurationMs = defaultClipDurationMs,
      onCancel = {
        MusicPreviewController.stop()
        onDismiss()
      },
      onConfirm = { attachment ->
        MusicPreviewController.stop()
        onMusicSelected(attachment)
      }
    )
  }
}

/**
 * Searchable, keyboard-safe Licensed Music Picker & Segment Trimmer content.
 * Separates UI from [ModalBottomSheet] wrapper so both modal sheets and automated Compose tests
 * can exercise search, category filtering, previewing, trimming, and volume mixing cleanly.
 */
@Composable
fun MusicPickerContent(
  initialAttachment: MusicAttachment? = null,
  showOriginalAudioControl: Boolean = false,
  defaultClipDurationMs: Long = 15_000L,
  onCancel: () -> Unit,
  onConfirm: (MusicAttachment) -> Unit,
  modifier: Modifier = Modifier
) {
  BindMusicPreviewLifecycle(stopOnDispose = true)

  val previewState by MusicPreviewController.state.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(MusicCategory.TRENDING) }
  var isLoading by remember { mutableStateOf(true) }
  var catalogResult by remember { mutableStateOf<MusicCatalogResult?>(null) }
  var refreshTrigger by remember { mutableIntStateOf(0) }

  var selectedTrack by remember {
    mutableStateOf(initialAttachment?.toLicensedTrack())
  }
  var startTimeMs by remember {
    mutableLongStateOf(initialAttachment?.startTimeMs ?: 0L)
  }
  var clipDurationMs by remember {
    mutableLongStateOf(initialAttachment?.clipDurationMs ?: defaultClipDurationMs)
  }
  var musicVolume by remember {
    mutableFloatStateOf(initialAttachment?.musicVolume ?: 0.85f)
  }
  var originalAudioVolume by remember {
    mutableFloatStateOf(initialAttachment?.originalAudioVolume ?: 0.6f)
  }

  LaunchedEffect(searchQuery, selectedCategory, refreshTrigger) {
    if (searchQuery.isNotBlank()) {
      delay(260L)
    }
    isLoading = true
    val result = MusicCatalogRepository.searchTracks(
      query = searchQuery.trim(),
      category = selectedCategory,
      mood = if (selectedCategory.isMoodBased) selectedCategory.displayLabel else null
    )
    catalogResult = result
    isLoading = false
  }

  val displayedCategories = catalogResult?.availableCategories?.takeIf { it.isNotEmpty() }
    ?: MusicCategory.entries

  Column(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .imePadding()
      .padding(horizontal = 16.dp, vertical = 6.dp)
      .testTag("music_picker_sheet")
  ) {
    // Header Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.music_picker_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Licensed catalog • Track ID & segment sync",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      IconButton(
        onClick = onCancel,
        modifier = Modifier
          .size(48.dp)
          .testTag("music_picker_close_button")
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close music picker")
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Search Input (Title, Artist, Genre, Mood)
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = {
        Text(
          text = stringResource(R.string.music_picker_search_hint),
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      },
      leadingIcon = {
        Icon(Icons.Default.Search, contentDescription = "Search music", modifier = Modifier.size(20.dp))
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(
            onClick = { searchQuery = "" },
            modifier = Modifier.size(48.dp)
          ) {
            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("music_picker_search_input")
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Category & Mood Chips
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(displayedCategories, key = { it.wireKey }) { category ->
        val selected = selectedCategory == category
        FilterChip(
          selected = selected,
          onClick = { selectedCategory = category },
          label = {
            Text(
              text = category.displayLabel,
              fontSize = 12.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
          },
          leadingIcon = if (category.isMoodBased) {
            {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
              )
            }
          } else null,
          modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .testTag("music_category_chip_${category.wireKey}")
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Preview playback error banner if a stream fails at runtime
    if (previewState is MusicPreviewState.Error) {
      val err = previewState as MusicPreviewState.Error
      Surface(
        color = VybeDanger.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 8.dp)
          .testTag("music_preview_error_banner")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = err.message,
            color = VybeDanger,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    val activeTrack = selectedTrack
    val safeTrackDur = activeTrack?.durationMs?.coerceAtLeast(1_000L) ?: 15_000L
    val maxStartMs = (safeTrackDur - clipDurationMs).coerceAtLeast(0L)
    val clampedStart = startTimeMs.coerceIn(0L, maxStartMs)
    val playingPos = if (activeTrack != null) {
      (previewState as? MusicPreviewState.Playing)
        ?.takeIf { it.trackId == activeTrack.id }
        ?.positionMs
    } else null

    // Unified scrollable body (Search results + Segment Trimmer + Volume Controls) — keyboard & compact-screen safe
    val currentRes = catalogResult
    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(bottom = 8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = false)
        .testTag("music_picker_track_list")
    ) {
      when {
        isLoading -> {
          item(key = "loading_state") {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
                .testTag("music_picker_loading"),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = VybeViolet, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = "Searching licensed music catalog...",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        currentRes == null || currentRes.status != MusicCatalogStatus.OK -> {
          item(key = "catalog_status_state") {
            val status = currentRes?.status ?: MusicCatalogStatus.PROVIDER_NOT_CONFIGURED
            CatalogStatusCard(
              status = status,
              diagnosticMessage = currentRes?.diagnosticMessage,
              onRetry = { refreshTrigger++ }
            )
          }
        }

        currentRes.tracks.isEmpty() -> {
          item(key = "empty_tracks_state") {
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("music_picker_empty_state")
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(
                  imageVector = Icons.Default.MusicOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = stringResource(R.string.music_empty_results_title),
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Try another song title, artist, genre, or mood category.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        else -> {
          items(currentRes.tracks, key = { it.id }) { track ->
            val isSelected = selectedTrack?.id == track.id
            val isPlayingThis = (previewState as? MusicPreviewState.Playing)?.trackId == track.id
            val isBufferingThis = (previewState as? MusicPreviewState.Buffering)?.trackId == track.id

            MusicTrackRow(
              track = track,
              isSelected = isSelected,
              isPlaying = isPlayingThis,
              isBuffering = isBufferingThis,
              onSelect = {
                if (track.isPlayableAndLicensed) {
                  selectedTrack = track
                  startTimeMs = 0L
                  clipDurationMs = minOf(defaultClipDurationMs, track.durationMs.coerceAtLeast(1_000L))
                }
              },
              onTogglePreview = {
                if (track.isPlayableAndLicensed) {
                  if (selectedTrack?.id != track.id) {
                    selectedTrack = track
                    startTimeMs = 0L
                    clipDurationMs = minOf(defaultClipDurationMs, track.durationMs.coerceAtLeast(1_000L))
                  }
                  MusicPreviewController.toggleTrackPreview(
                    track = track,
                    startTimeMs = if (selectedTrack?.id == track.id) startTimeMs else 0L,
                    clipDurationMs = if (selectedTrack?.id == track.id) clipDurationMs else minOf(defaultClipDurationMs, track.durationMs),
                    volume = musicVolume
                  )
                }
              }
            )
          }
        }
      }

      if (activeTrack != null && activeTrack.isPlayableAndLicensed) {
        item(key = "music_segment_mixer_item") {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.45f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp)
              .testTag("music_segment_mixer_panel")
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${activeTrack.title} • ${activeTrack.artist}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = buildString {
                      append("Segment: ${formatMusicMillis(clampedStart)} – ${formatMusicMillis(minOf(safeTrackDur, clampedStart + clipDurationMs))}")
                      if (playingPos != null) {
                        append("  •  Playing ${formatMusicMillis(playingPos)}")
                      }
                    },
                    fontSize = 11.sp,
                    color = VybeBrandCyan,
                    fontWeight = FontWeight.SemiBold
                  )
                }

                OutlinedButton(
                  onClick = {
                    MusicPreviewController.toggleTrackPreview(
                      track = activeTrack,
                      startTimeMs = clampedStart,
                      clipDurationMs = clipDurationMs,
                      volume = musicVolume
                    )
                  },
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("music_trim_preview_button")
                ) {
                  val isPlayingSelected = playingPos != null
                  Icon(
                    imageVector = if (isPlayingSelected) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlayingSelected) "Stop segment preview" else "Preview selected segment",
                    tint = if (isPlayingSelected) VybeMagenta else VybeViolet,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isPlayingSelected) "Stop" else "Preview",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              Spacer(modifier = Modifier.height(6.dp))

              // Clip length selector chips
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Clip:",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(10_000L to "10s", 15_000L to "15s", 30_000L to "30s", 60_000L to "60s")
                  .filter { (ms, _) -> ms <= safeTrackDur || ms == 15_000L }
                  .forEach { (ms, label) ->
                    val targetMs = minOf(ms, safeTrackDur)
                    val selected = clipDurationMs == targetMs
                    FilterChip(
                      selected = selected,
                      onClick = {
                        clipDurationMs = targetMs
                        val nextMaxStart = (safeTrackDur - targetMs).coerceAtLeast(0L)
                        if (startTimeMs > nextMaxStart) {
                          startTimeMs = nextMaxStart
                        }
                        MusicPreviewController.updateSegmentBounds(startTimeMs, clipDurationMs)
                      },
                      label = { Text(label, fontSize = 11.sp) },
                      modifier = Modifier.testTag("music_clip_duration_$label")
                    )
                  }
              }

              // Start-Time / Timeline Slider
              if (maxStartMs > 0L) {
                Text(
                  text = "Start Time (${formatMusicMillis(clampedStart)} of ${activeTrack.formattedDuration})",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                  value = clampedStart.toFloat(),
                  onValueChange = { raw ->
                    startTimeMs = raw.toLong().coerceIn(0L, maxStartMs)
                  },
                  onValueChangeFinished = {
                    MusicPreviewController.updateSegmentBounds(startTimeMs, clipDurationMs)
                  },
                  valueRange = 0f..maxStartMs.toFloat().coerceAtLeast(1f),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_start_time_slider")
                )
              }

              // Music Volume Slider
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.MusicNote,
                  contentDescription = null,
                  tint = VybeMagenta,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "${stringResource(R.string.music_volume_label)} ${(musicVolume * 100).roundToInt()}%",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.width(122.dp)
                )
                Slider(
                  value = musicVolume,
                  onValueChange = { v ->
                    musicVolume = v.coerceIn(0f, 1f)
                    MusicPreviewController.setVolume(musicVolume)
                  },
                  valueRange = 0f..1f,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("picker_music_volume_slider")
                )
              }

              // Separate Original Video Audio Volume Slider (Video Stories & Reels)
              if (showOriginalAudioControl) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = VybeBrandCyan,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "${stringResource(R.string.original_audio_volume_label)} ${(originalAudioVolume * 100).roundToInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(122.dp)
                  )
                  Slider(
                    value = originalAudioVolume,
                    onValueChange = { v ->
                      originalAudioVolume = v.coerceIn(0f, 1f)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                      .weight(1f)
                      .testTag("picker_original_volume_slider")
                  )
                }
              }
            }
          }
        }
      }
    }

    // Pinned, always-accessible Confirm Action Bar when a licensed track is selected
    if (activeTrack != null && activeTrack.isPlayableAndLicensed) {
      Spacer(modifier = Modifier.height(6.dp))
      Button(
        onClick = {
          val attachment = MusicAttachment.fromTrack(
            track = activeTrack,
            startTimeMs = clampedStart,
            clipDurationMs = clipDurationMs,
            musicVolume = musicVolume,
            originalAudioVolume = if (showOriginalAudioControl) originalAudioVolume else 0f
          )
          onConfirm(attachment)
        },
        colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .testTag("music_picker_confirm_button")
      ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.use_selected_music),
          color = Color.White,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}

@Composable
private fun MusicTrackRow(
  track: LicensedMusicTrack,
  isSelected: Boolean,
  isPlaying: Boolean,
  isBuffering: Boolean,
  onSelect: () -> Unit,
  onTogglePreview: () -> Unit
) {
  val enabled = track.isPlayableAndLicensed
  Surface(
    color = when {
      isSelected -> VybeViolet.copy(alpha = 0.16f)
      else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.65f else 0.35f)
    },
    shape = RoundedCornerShape(14.dp),
    border = if (isSelected) BorderStroke(1.5.dp, VybeViolet) else null,
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(enabled = enabled) { onSelect() }
      .testTag("music_track_item_${track.id}")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Album Artwork
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Brush.linearGradient(listOf(VybeViolet, VybeMagenta))),
        contentAlignment = Alignment.Center
      ) {
        if (!track.artworkUrl.isNullOrBlank()) {
          AsyncImage(
            model = track.artworkUrl,
            contentDescription = "${track.title} artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(
            imageVector = Icons.Default.Album,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      // Title, Artist, Genre/Mood & Duration
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = track.title,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = track.artist,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = track.formattedDuration,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = VybeBrandCyan
          )
          track.genre?.takeIf { it.isNotBlank() }?.let { g ->
            Text("• $g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          track.mood?.takeIf { it.isNotBlank() }?.let { m ->
            Text("• $m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (!enabled) {
            Text(
              text = "• ${stringResource(R.string.music_unavailable_track)}",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = VybeDanger
            )
          } else {
            Icon(
              imageVector = Icons.Default.Verified,
              contentDescription = "Sync licensed",
              tint = VybeOnlineGreen,
              modifier = Modifier.size(13.dp)
            )
          }
        }
      }

      // Preview Play/Stop Button (48dp touch target)
      IconButton(
        onClick = onTogglePreview,
        enabled = enabled,
        modifier = Modifier
          .size(48.dp)
          .testTag("music_preview_btn_${track.id}")
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
              if (isPlaying) VybeMagenta.copy(alpha = 0.22f)
              else MaterialTheme.colorScheme.surface
            )
            .border(
              width = 1.dp,
              color = if (isPlaying) VybeMagenta else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
              shape = CircleShape
            ),
          contentAlignment = Alignment.Center
        ) {
          when {
            isBuffering -> CircularProgressIndicator(
              strokeWidth = 2.dp,
              color = VybeViolet,
              modifier = Modifier.size(16.dp)
            )
            isPlaying -> Icon(
              imageVector = Icons.Default.Stop,
              contentDescription = "Stop preview for ${track.title}",
              tint = VybeMagenta,
              modifier = Modifier.size(18.dp)
            )
            else -> Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = "Preview ${track.title}",
              tint = if (enabled) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CatalogStatusCard(
  status: MusicCatalogStatus,
  diagnosticMessage: String?,
  onRetry: () -> Unit
) {
  val (title, defaultBody, accent) = when (status) {
    MusicCatalogStatus.PROVIDER_NOT_CONFIGURED -> Triple(
      stringResource(R.string.music_provider_not_configured_title),
      stringResource(R.string.music_provider_not_configured_body),
      VybeBrandCyan
    )
    MusicCatalogStatus.LICENSE_APPROVAL_REQUIRED -> Triple(
      stringResource(R.string.music_license_pending_title),
      stringResource(R.string.music_license_pending_body),
      VybeMagenta
    )
    MusicCatalogStatus.NETWORK_ERROR -> Triple(
      "Network Connection Issue",
      "Unable to reach the licensed music service. Check your connection and retry.",
      VybeDanger
    )
    else -> Triple(
      "Music Catalog Temporarily Unavailable",
      "The licensed music provider could not fulfill this request right now.",
      VybeDanger
    )
  }

  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("music_catalog_status_${status.name}")
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.LibraryMusic,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = diagnosticMessage?.takeIf { it.isNotBlank() } ?: defaultBody,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(12.dp))
      OutlinedButton(
        onClick = onRetry,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("music_picker_retry_button")
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Retry Catalog Check", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
      }
    }
  }
}

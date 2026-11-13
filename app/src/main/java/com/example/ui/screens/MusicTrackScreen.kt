package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.LicensedMusicTrack
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import com.example.util.music.MusicPreviewController

@Composable
fun MusicTrackScreen(viewModel: VybeViewModel) {
  val palette = LocalVybePalette.current
  val attachment by viewModel.selectedMusicTrackAttachment.collectAsState()
  val trackReels by viewModel.trackPageReels.collectAsState()
  val isLoading by viewModel.isLoadingTrackPage.collectAsState()
  var isPlayingPreview by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    onDispose {
      MusicPreviewController.stop()
    }
  }

  BackHandler {
    MusicPreviewController.stop()
    viewModel.navigateBack()
  }

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = "Audio Track",
        subtitle = "Licensed Sound & Reels",
        onBackClick = {
          MusicPreviewController.stop()
          viewModel.navigateBack()
        },
        backTestTag = "music_track_back_button"
      )
    }
  ) { innerPadding ->
    val activeTrack = attachment
    if (activeTrack == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        VybeEmptyState(
          icon = Icons.Default.MusicNote,
          title = "Track Unavailable",
          subtitle = "Track information unavailable for this reel."
        )
      }
      return@Scaffold
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .navigationBarsPadding()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(vertical = 16.dp)
    ) {
      item {
        VybeSurfaceCard(
          containerColor = palette.elevated,
          contentPadding = PaddingValues(18.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (!activeTrack.coverUrl.isNullOrBlank()) {
              AsyncImage(
                model = activeTrack.coverUrl,
                contentDescription = activeTrack.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(72.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.md))
              )
            } else {
              Box(
                modifier = Modifier
                  .size(72.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.md))
                  .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlueBright))),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.MusicNote,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(34.dp)
                )
              }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = activeTrack.title.ifBlank { "Original Audio" },
                fontFamily = PlusJakartaSansFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              if (activeTrack.artist.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                  text = activeTrack.artist,
                  style = MaterialTheme.typography.bodyMedium,
                  color = palette.textSecondary
                )
              }
              Spacer(Modifier.height(4.dp))
              Text(
                text = "${trackReels.size} Reels • ${activeTrack.durationSeconds}s licensed segment",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accentSecondary,
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          Spacer(Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            VybeSecondaryButton(
              text = if (isPlayingPreview) "Stop Preview" else "Preview",
              onClick = {
                if (isPlayingPreview) {
                  MusicPreviewController.stop()
                  isPlayingPreview = false
                } else {
                  MusicPreviewController.playTrack(activeTrack)
                  isPlayingPreview = true
                }
              },
              icon = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
              testTag = "music_track_preview_button",
              modifier = Modifier.weight(1f)
            )

            VybePrimaryButton(
              text = "Use Audio",
              onClick = {
                isPlayingPreview = false
                viewModel.useTrackInReel(
                  LicensedMusicTrack(
                    trackId = activeTrack.trackId,
                    title = activeTrack.title,
                    artist = activeTrack.artist,
                    durationSeconds = activeTrack.durationSeconds,
                    previewUrl = activeTrack.previewUrl ?: activeTrack.streamUrl,
                    coverArtUrl = activeTrack.coverUrl
                  )
                )
              },
              icon = Icons.Default.VideoLibrary,
              testTag = "use_audio_in_reel_button",
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      item {
        Text(
          text = "Reels Using This Track",
          fontFamily = PlusJakartaSansFontFamily,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary
        )
      }

      if (isLoading) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(28.dp))
          }
        }
      } else if (trackReels.isEmpty()) {
        item {
          VybeEmptyState(
            icon = Icons.Default.VideoLibrary,
            title = "No Reels Yet",
            subtitle = "Be the first creator to publish a Reel with this track."
          )
        }
      } else {
        items(trackReels, key = { it.id }) { reel ->
          VybeSurfaceCard(
            onClick = { viewModel.openReelViewer(reel.id) },
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("track_reel_item_${reel.id}")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              VybeAvatar(
                user = reel.author,
                size = 42.dp,
                onClick = { viewModel.openUserProfile(reel.author) }
              )
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "@${reel.author.username}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = palette.textPrimary
                  )
                  VerificationBadge(user = reel.author, iconSize = 14.dp)
                }
                Text(
                  text = reel.caption.ifBlank { "Watch Reel" },
                  fontSize = 12.sp,
                  color = palette.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Text(
                text = "${reel.viewsCount} views",
                fontSize = 11.sp,
                color = palette.textMuted
              )
            }
          }
        }
      }
    }
  }
}

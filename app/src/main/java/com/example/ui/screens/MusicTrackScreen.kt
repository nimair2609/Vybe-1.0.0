package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeBrandMagenta
import com.example.ui.theme.VybeBrandViolet
import com.example.util.music.MusicPreviewController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTrackScreen(viewModel: VybeViewModel) {
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
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Audio Track",
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              MusicPreviewController.stop()
              viewModel.navigateBack()
            },
            modifier = Modifier.testTag("music_track_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { innerPadding ->
    val activeTrack = attachment
    if (activeTrack == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Track information unavailable.",
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      return@Scaffold
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(vertical = 16.dp)
    ) {
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (!activeTrack.coverUrl.isNullOrBlank()) {
                AsyncImage(
                  model = activeTrack.coverUrl,
                  contentDescription = activeTrack.title,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                )
              } else {
                Box(
                  modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(VybeBrandCyan, VybeBrandViolet, VybeBrandMagenta))),
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
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.ExtraBold,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
                if (activeTrack.artist.isNotBlank()) {
                  Spacer(Modifier.height(2.dp))
                  Text(
                    text = activeTrack.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                  text = "${trackReels.size} Reels • ${activeTrack.durationSeconds}s licensed segment",
                  style = MaterialTheme.typography.labelSmall,
                  color = VybeBrandCyan,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }

            Spacer(Modifier.height(16.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedButton(
                onClick = {
                  if (isPlayingPreview) {
                    MusicPreviewController.stop()
                    isPlayingPreview = false
                  } else {
                    MusicPreviewController.playTrack(activeTrack)
                    isPlayingPreview = true
                  }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("music_track_preview_button")
              ) {
                Icon(
                  imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isPlayingPreview) "Stop Preview" else "Preview")
              }

              Button(
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("use_audio_in_reel_button")
              ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Use Audio", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      item {
        Text(
          text = "Reels Using This Track",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
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
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
          }
        }
      } else if (trackReels.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Be the first creator to publish a Reel with this track.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(18.dp)
            )
          }
        }
      } else {
        items(trackReels, key = { it.id }) { reel ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { viewModel.openReelViewer(reel.id) }
              .testTag("track_reel_item_${reel.id}")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    fontSize = 13.sp
                  )
                  VerificationBadge(user = reel.author, iconSize = 14.dp)
                }
                Text(
                  text = reel.caption.ifBlank { "Watch Reel" },
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Text(
                text = "${reel.viewsCount} views",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

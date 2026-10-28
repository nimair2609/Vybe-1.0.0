package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.model.Post
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeViolet
import com.example.util.music.BindMusicPreviewLifecycle
import com.example.util.music.MusicPreviewController
import com.example.util.music.MusicPreviewState

/**
 * Instagram-style Music Track Page (`Screen.MUSIC_TRACK_DETAIL`):
 * - Displays track artwork, title, artist, provider/license metadata, and audio preview controls.
 * - Lets creators start a new VYBES Reel pre-loaded with this track ("Use Audio in Reel").
 * - Lists all published VYBE Reels using this track ID so users can discover other Reels with the same song.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTrackScreen(viewModel: VybeViewModel) {
  BindMusicPreviewLifecycle(stopOnDispose = true)

  val attachment by viewModel.selectedMusicTrackAttachment.collectAsState()
  val reels by viewModel.trackPageReels.collectAsState()
  val isLoading by viewModel.isLoadingTrackPage.collectAsState()
  val previewState by MusicPreviewController.state.collectAsState()

  BackHandler {
    MusicPreviewController.stop()
    viewModel.navigateBack()
  }

  val currentTrack = attachment
  if (currentTrack == null) {
    LaunchedEffect(Unit) {
      viewModel.navigateBack()
    }
    return
  }

  val isPlayingPreview = (previewState as? MusicPreviewState.Playing)?.trackId == currentTrack.trackId
  val isBufferingPreview = (previewState as? MusicPreviewState.Buffering)?.trackId == currentTrack.trackId
  val hasPlayableStream = !currentTrack.streamUrl.isNullOrBlank()

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = {
              MusicPreviewController.stop()
              viewModel.navigateBack()
            },
            modifier = Modifier
              .size(48.dp)
              .testTag("music_track_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text(
            text = stringResource(R.string.track_page_title),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        actions = {
          IconButton(
            onClick = { viewModel.refreshMusicTrackReels(currentTrack.trackId) },
            modifier = Modifier
              .size(48.dp)
              .testTag("music_track_refresh_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh track reels")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    modifier = Modifier.testTag("music_track_screen")
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentAlignment = Alignment.TopCenter
    ) {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = 12.dp,
          bottom = 32.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize()
          .navigationBarsPadding()
          .testTag("track_page_reels_grid")
      ) {
        // Track Hero Card
        item(span = { GridItemSpan(maxLineSpan) }) {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(VybeViolet, VybeMagenta, VybeBrandCyan))),
                  contentAlignment = Alignment.Center
                ) {
                  if (!currentTrack.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                      model = currentTrack.artworkUrl,
                      contentDescription = "${currentTrack.title} album artwork",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.Album,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(42.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (currentTrack.artist.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = currentTrack.artist,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Surface(
                      color = VybeOnlineGreen.copy(alpha = 0.14f),
                      shape = RoundedCornerShape(8.dp)
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(
                          imageVector = Icons.Default.Verified,
                          contentDescription = null,
                          tint = VybeOnlineGreen,
                          modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = currentTrack.provider.uppercase(),
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          color = VybeOnlineGreen
                        )
                      }
                    }

                    Text(
                      text = "${reels.size} ${if (reels.size == 1) "Reel" else "Reels"}",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = VybeBrandCyan
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(14.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    if (isPlayingPreview || isBufferingPreview) {
                      MusicPreviewController.stop()
                    } else {
                      MusicPreviewController.playAttachment(currentTrack, muted = false)
                    }
                  },
                  enabled = hasPlayableStream,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("track_page_preview_button")
                ) {
                  when {
                    isBufferingPreview -> CircularProgressIndicator(
                      strokeWidth = 2.dp,
                      color = VybeViolet,
                      modifier = Modifier.size(16.dp)
                    )
                    isPlayingPreview -> Icon(
                      Icons.Default.Stop,
                      contentDescription = "Stop track preview",
                      tint = VybeMagenta,
                      modifier = Modifier.size(18.dp)
                    )
                    else -> Icon(
                      Icons.Default.PlayArrow,
                      contentDescription = "Preview track",
                      tint = if (hasPlayableStream) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = when {
                      !hasPlayableStream -> "Stream Unavailable"
                      isPlayingPreview -> "Stop Preview"
                      else -> "Preview Track"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                  )
                }

                if (hasPlayableStream) {
                  Button(
                    onClick = {
                      MusicPreviewController.stop()
                      viewModel.useTrackInReel(currentTrack.toLicensedTrack())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("track_page_use_in_reel_button")
                  ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = stringResource(R.string.track_page_use_audio),
                      color = Color.White,
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      maxLines = 1
                    )
                  }
                }
              }
            }
          }
        }

        // Section Header
        item(span = { GridItemSpan(maxLineSpan) }) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = stringResource(R.string.track_page_reels_header),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            if (isLoading) {
              CircularProgressIndicator(
                color = VybeViolet,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }

        if (reels.isEmpty() && !isLoading) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("track_page_empty_reels")
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(
                  imageVector = Icons.Default.MovieFilter,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "No Reels found for this track yet",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Be the first to publish a VYBES Reel featuring ${currentTrack.title}.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        } else {
          items(reels, key = { it.id }) { reel ->
            TrackReelDiscoveryCard(
              reel = reel,
              onClick = {
                MusicPreviewController.stop()
                viewModel.openReelViewer(reel.id)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TrackReelDiscoveryCard(
  reel: Post,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFF101322),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(9f / 14f)
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .testTag("track_reel_card_${reel.id}")
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(
                VybeViolet.copy(alpha = 0.32f),
                Color(0xFF0A0C18),
                Color.Black.copy(alpha = 0.92f)
              )
            )
          )
      )

      // Center Play Badge
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .size(44.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = "Open Reel by ${reel.author.displayName}",
          tint = Color.White,
          modifier = Modifier.size(26.dp)
        )
      }

      // Top Views Pill
      Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            tint = VybeBrandCyan,
            modifier = Modifier.size(12.dp)
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "${reel.viewsCount}",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Bottom Author + Caption
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .padding(10.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          VybeAvatar(user = reel.author, size = 24.dp)
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "@${reel.author.username}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (reel.caption.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = reel.caption,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
          )
        }
      }
    }
  }
}

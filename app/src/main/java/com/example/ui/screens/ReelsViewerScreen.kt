package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.MediaType
import com.example.data.model.Post
import com.example.data.model.VybeReaction
import com.example.data.repository.MessagingEligibility
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.ReelReactionBreakdownSheet
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VideoFitMode
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeLikeIcon
import com.example.ui.components.reactionColor
import com.example.ui.components.VybeReactionOutlineIcon
import com.example.ui.components.VybeReactionSelector
import com.example.ui.components.VybeVideoPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.example.util.FeedAudioController
import com.example.util.music.BindMusicPreviewLifecycle
import com.example.util.music.MusicPreviewController
import com.example.util.rememberRelativeTimestamp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsViewerScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val currentScreen by viewModel.currentScreen.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val conversations by viewModel.conversations.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val targetReelId by viewModel.targetReelId.collectAsState()

  BindMusicPreviewLifecycle(stopOnDispose = true)

  fun handleBackFromReels() {
    MusicPreviewController.stop()
    if (currentScreen == Screen.MAIN) {
      viewModel.selectTab(MainTab.FEED)
    } else {
      viewModel.navigateBack()
    }
  }

  BackHandler {
    handleBackFromReels()
  }

  val isReelsAudioEnabled by viewModel.isReelsAudioEnabled.collectAsState()
  val isMuted = !isReelsAudioEnabled

  DisposableEffect(Unit) {
    FeedAudioController.stopAll()
    onDispose {
      MusicPreviewController.stop()
    }
  }

  var isPlaying by remember { mutableStateOf(true) }
  var showShareInboxSheet by remember { mutableStateOf(false) }
  var showCommentsSheet by remember { mutableStateOf(false) }
  var showReactionSelectorReelId by remember { mutableStateOf<String?>(null) }
  var showBreakdownSheetReel by remember { mutableStateOf<Post?>(null) }
  var reelCommentText by remember { mutableStateOf("") }
  var shareSearchQuery by remember { mutableStateOf("") }
  var sharedFeedback by remember { mutableStateOf<String?>(null) }

  // Strictly render real video reels only
  val videoPosts = remember(posts) {
    posts.filter { post ->
      !post.isArchived && post.mediaUrls.isNotEmpty() && (
        post.mediaType == MediaType.REEL ||
          post.mediaType == MediaType.VIDEO ||
          post.isVybesReel
        )
    }
  }

  if (videoPosts.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0A0A0F))
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { handleBackFromReels() },
          modifier = Modifier
            .size(48.dp)
            .testTag("reels_back_button")
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
          text = "VYBES",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 1.sp,
          color = Color.White
        )
      }

      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          Icons.Default.VideocamOff,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.55f),
          modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("No VYBES reels yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          "Upload a short video to start the stream.",
          color = Color.White.copy(alpha = 0.7f),
          fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
          onClick = { viewModel.navigateTo(Screen.CREATE_POST) },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Create VYBES Reel", fontWeight = FontWeight.Bold)
        }
      }
    }
    return
  }

  val initialPageIndex = remember(videoPosts, targetReelId) {
    if (!targetReelId.isNullOrBlank()) {
      val found = videoPosts.indexOfFirst { it.id == targetReelId }
      if (found >= 0) found else 0
    } else 0
  }

  val pagerState = rememberPagerState(
    initialPage = initialPageIndex,
    pageCount = { videoPosts.size }
  )

  LaunchedEffect(targetReelId, videoPosts.size) {
    if (!targetReelId.isNullOrBlank()) {
      val found = videoPosts.indexOfFirst { it.id == targetReelId }
      if (found >= 0 && found != pagerState.currentPage) {
        pagerState.scrollToPage(found)
      }
      viewModel.clearTargetReelId()
    }
  }

  LaunchedEffect(pagerState.settledPage, videoPosts.size) {
    val activeReel = videoPosts.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
    isPlaying = true
    viewModel.recordPostView(activeReel.id)
    viewModel.loadCommentsForPost(activeReel.id)
  }

  // Synchronize external licensed music segment when a Reel was published without pre-mixed audio
  val settledReel = videoPosts.getOrNull(pagerState.settledPage)
  val settledMusic = settledReel?.musicAttachment
  val shouldPlayExternalMusic = settledMusic != null &&
    !settledMusic.isRenderedWithMixedAudio &&
    !settledMusic.streamUrl.isNullOrBlank() &&
    isPlaying &&
    !isMuted &&
    !showCommentsSheet &&
    !showShareInboxSheet

  LaunchedEffect(
    settledReel?.id,
    settledMusic?.trackId,
    settledMusic?.isRenderedWithMixedAudio,
    shouldPlayExternalMusic,
    settledMusic?.musicVolume
  ) {
    if (shouldPlayExternalMusic && settledMusic != null) {
      MusicPreviewController.playAttachment(settledMusic, muted = false)
    } else {
      MusicPreviewController.stop()
    }
  }

  // Full-bleed container — no unintended nested boxes around reels
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    VerticalPager(
      state = pagerState,
      beyondViewportPageCount = 0,
      modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
      val reelPost = videoPosts[pageIndex]
      val isCurrentPage = pageIndex == pagerState.settledPage
      val reelRelativeTime = rememberRelativeTimestamp(reelPost.createdAtIso, reelPost.timestamp)
      var showDoubleTapResonance by remember { mutableStateOf(false) }

      LaunchedEffect(showDoubleTapResonance) {
        if (showDoubleTapResonance) {
          delay(850L)
          showDoubleTapResonance = false
        }
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
          .pointerInput(reelPost.id) {
            detectTapGestures(
              onTap = { isPlaying = !isPlaying },
              onDoubleTap = {
                showDoubleTapResonance = true
                viewModel.reactToPost(reelPost.id, VybeReaction.LOVE)
              }
            )
          }
      ) {
        val reelOriginalVolume = if (reelPost.musicAttachment != null && !reelPost.musicAttachment.isRenderedWithMixedAudio) {
          reelPost.musicAttachment.originalAudioVolume
        } else {
          1f
        }
        VybeVideoPlayer(
          videoUrl = reelPost.mediaUrls.first(),
          autoPlay = isCurrentPage,
          isLooping = true,
          initialMuted = isMuted || reelOriginalVolume <= 0.01f,
          isPausedExternal = !isCurrentPage || !isPlaying || showCommentsSheet || showShareInboxSheet,
          fitMode = VideoFitMode.FIT_CONTAIN,
          showControls = false,
          audioVolume = reelOriginalVolume,
          onTapOverlay = { isPlaying = !isPlaying },
          onDoubleTapOverlay = {
            showDoubleTapResonance = true
            viewModel.reactToPost(reelPost.id, VybeReaction.LOVE)
          },
          modifier = Modifier.fillMaxSize()
        )

        // Double-Tap Resonance LOVE Animation Overlay
        androidx.compose.animation.AnimatedVisibility(
          visible = showDoubleTapResonance,
          enter = androidx.compose.animation.scaleIn(initialScale = 0.4f) + androidx.compose.animation.fadeIn(),
          exit = androidx.compose.animation.scaleOut(targetScale = 1.35f) + androidx.compose.animation.fadeOut(),
          modifier = Modifier.align(Alignment.Center)
        ) {
          Box(
            modifier = Modifier
              .size(90.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
          ) {
            VybeReactionOutlineIcon(
              reaction = VybeReaction.LOVE,
              size = 54.dp,
              tint = Color(0xFFEC4899)
            )
          }
        }

        if (!isPlaying && isCurrentPage && !showDoubleTapResonance) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .align(Alignment.Center)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.55f))
              .clickable { isPlaying = true },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.PlayArrow,
              contentDescription = "Resume reel",
              tint = Color.White,
              modifier = Modifier.size(36.dp)
            )
          }
        }

        // Top subtle gradient & Controls
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent)))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = { handleBackFromReels() },
              modifier = Modifier
                .size(48.dp)
                .testTag("reels_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text(
              text = "VYBES",
              fontFamily = SyneFontFamily,
              fontSize = 19.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 1.4.sp,
              color = Color.White
            )

            IconButton(
              onClick = {
                MusicPreviewController.stop()
                viewModel.navigateTo(Screen.CREATE_POST)
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("reels_create_shortcut_button")
            ) {
              Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Create Reel",
                tint = Color.White
              )
            }
          }
        }

        // Right Action Rail
        Column(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(bottom = 32.dp, end = 14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Like & 4-Reactions (Reels: Show total reactions and individual reaction breakdowns)
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
              IconButton(
                onClick = {
                  if (reelPost.userReaction != null) {
                    viewModel.reactToPost(reelPost.id, reelPost.userReaction)
                  } else {
                    viewModel.reactToPost(reelPost.id, VybeReaction.LOVE)
                  }
                },
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(Color.Black.copy(alpha = 0.5f))
                  .pointerInput(reelPost.id, reelPost.userReaction) {
                    detectTapGestures(
                      onTap = {
                        if (reelPost.userReaction != null) {
                          viewModel.reactToPost(reelPost.id, reelPost.userReaction)
                        } else {
                          viewModel.reactToPost(reelPost.id, VybeReaction.LOVE)
                        }
                      },
                      onLongPress = {
                        showReactionSelectorReelId = if (showReactionSelectorReelId == reelPost.id) null else reelPost.id
                      }
                    )
                  }
                  .testTag("reel_like_button")
              ) {
                if (reelPost.userReaction != null) {
                  VybeReactionOutlineIcon(
                    reaction = reelPost.userReaction,
                    size = 24.dp,
                    tint = reactionColor(reelPost.userReaction)
                  )
                } else {
                  VybeReactionOutlineIcon(
                    reaction = VybeReaction.LOVE,
                    size = 24.dp,
                    tint = Color.White
                  )
                }
              }

              if (showReactionSelectorReelId == reelPost.id) {
                Box(
                  modifier = Modifier
                    .offset(x = (-180).dp, y = (-8).dp)
                    .zIndex(30f)
                ) {
                  VybeReactionSelector(
                    selectedReaction = reelPost.userReaction,
                    onSelectReaction = { rx ->
                      showReactionSelectorReelId = null
                      viewModel.reactToPost(reelPost.id, rx)
                    },
                    onDismiss = { showReactionSelectorReelId = null }
                  )
                }
              }
            }

            // Total reactions count - tappable to see breakdown of all 4 reactions
            Row(
              modifier = Modifier
                .clickable { showBreakdownSheetReel = reelPost }
                .padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${reelPost.totalReactionsCount}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
              Spacer(modifier = Modifier.width(2.dp))
              Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Breakdown",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
              )
            }
          }

          // Comments
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = {
                viewModel.loadCommentsForPost(reelPost.id)
                showCommentsSheet = true
              },
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .testTag("reel_comment_button")
            ) {
              Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", tint = Color.White)
            }
            Text(
              text = "${maxOf(reelPost.commentsCount, reelPost.comments.size)}",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }

          // Views count
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Visibility, contentDescription = "Views", tint = VybeBrandCyan)
            }
            Text(
              text = "${reelPost.viewsCount}",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }

          // Share to Inbox / External
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { showShareInboxSheet = true },
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .testTag("reel_share_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Share Reel", tint = Color.White)
            }
            Text(
              text = "Share",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }

          // Volume Control (Moved below Share control per specification)
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { viewModel.toggleReelsAudio() },
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .testTag("reel_volume_button")
            ) {
              Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute audio" else "Mute audio",
                tint = if (isMuted) Color.White.copy(alpha = 0.7f) else VybeBrandCyan
              )
            }
            Text(
              text = if (isMuted) "Muted" else "Audio",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }
        }

        // Bottom Metadata Overlay (Author, Location, Caption, Audio)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomStart)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 82.dp, bottom = 24.dp, top = 40.dp)
        ) {
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { viewModel.openUserProfile(reelPost.author) }
            ) {
              VybeAvatar(user = reelPost.author, size = 38.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = reelPost.author.displayName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  VerificationBadge(user = reelPost.author)
                  if (reelRelativeTime.isNotBlank()) {
                    Text(
                      text = " • $reelRelativeTime",
                      color = Color.White.copy(alpha = 0.75f),
                      fontSize = 12.sp
                    )
                  }
                }
                if (!reelPost.locationName.isNullOrBlank()) {
                  Text(
                    text = reelPost.locationName,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }

            if (reelPost.caption.isNotBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = reelPost.caption,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
              )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val attachedMusic = reelPost.musicAttachment
            Surface(
              color = Color.Black.copy(alpha = 0.58f),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(
                width = 1.dp,
                color = if (attachedMusic != null) VybeBrandCyan.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.22f)
              ),
              modifier = Modifier
                .then(
                  if (attachedMusic != null) {
                    Modifier
                      .defaultMinSize(minHeight = 48.dp)
                      .clip(RoundedCornerShape(16.dp))
                      .clickable {
                        MusicPreviewController.stop()
                        viewModel.openMusicTrackPage(attachedMusic)
                      }
                  } else {
                    Modifier
                  }
                )
                .testTag("reel_music_track_pill")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = if (attachedMusic != null) 8.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (!attachedMusic?.artworkUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = attachedMusic?.artworkUrl,
                    contentDescription = "${attachedMusic?.title} artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .size(24.dp)
                      .clip(RoundedCornerShape(6.dp))
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                } else {
                  Icon(
                    imageVector = if (attachedMusic != null) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                    contentDescription = "Reel audio track",
                    tint = if (attachedMusic != null) VybeBrandCyan else Color.White,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                  text = attachedMusic?.displayLabel ?: reelPost.audioTrackTitle ?: "Original Audio • ${reelPost.author.displayName}",
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false)
                )
                if (attachedMusic != null) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open track page",
                    tint = VybeBrandCyan,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }
      }
    }

    // Share to Inbox Sheet (Enforces recipient message privacy)
    if (showShareInboxSheet) {
      val currentReel = videoPosts.getOrNull(pagerState.currentPage)
      val filteredUsers = remember(shareSearchQuery, allUsers, currentUser?.id) {
        val peers = allUsers.filter { it.id != currentUser?.id }
        if (shareSearchQuery.isBlank()) peers else {
          val q = shareSearchQuery.trim().lowercase().removePrefix("@")
          peers.filter { it.displayName.lowercase().contains(q) || it.username.lowercase().contains(q) }
        }
      }

      if (currentReel != null) {
        ModalBottomSheet(
          onDismissRequest = {
            showShareInboxSheet = false
            shareSearchQuery = ""
          },
          sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
          contentWindowInsets = { WindowInsets(0.dp) },
          containerColor = MaterialTheme.colorScheme.surface
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 8.dp)
              .navigationBarsPadding()
              .imePadding()
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Share VYBES Reel",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Send to contacts or share externally to other apps.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              OutlinedButton(
                onClick = {
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "VYBES Reel by @${currentReel.author.username}")
                    putExtra(
                      Intent.EXTRA_TEXT,
                      "Watch @${currentReel.author.username}'s reel on VYBE:\n${currentReel.caption}\n${currentReel.mediaUrls.firstOrNull() ?: ""}"
                    )
                  }
                  context.startActivity(Intent.createChooser(shareIntent, "Share Reel Link"))
                  showShareInboxSheet = false
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("reel_external_share_button")
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("External Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = shareSearchQuery,
              onValueChange = { shareSearchQuery = it },
              placeholder = { Text("Search people by name or @username...", fontSize = 12.sp) },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              if (conversations.isNotEmpty() && shareSearchQuery.isBlank()) {
                item {
                  Text(
                    "Active Conversations",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                items(conversations, key = { it.id }) { conv ->
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(10.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      VybeAvatar(user = conv.peerUser, size = 36.dp)
                      Spacer(modifier = Modifier.width(10.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(conv.groupName ?: conv.peerUser.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("@${conv.peerUser.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                      Button(
                        onClick = {
                          viewModel.sendExtendedDirectMessage(
                            convId = conv.id,
                            text = "Shared reel from @${currentReel.author.username}: ${currentReel.caption}".trim(),
                            mediaUrl = currentReel.mediaUrls.firstOrNull(),
                            mediaType = "VIDEO"
                          )
                          sharedFeedback = "Reel sent to ${conv.groupName ?: conv.peerUser.displayName}."
                          showShareInboxSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 38.dp)
                      ) {
                        Text("Send", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }

              item {
                Text(
                  "Community Members",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              items(filteredUsers, key = { it.id }) { targetUser ->
                val eligibility = viewModel.checkMessagingEligibility(targetUser)
                val canMessage = eligibility is MessagingEligibility.Allowed
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    VybeAvatar(user = targetUser, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(targetUser.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      Text(
                        text = if (canMessage) "@${targetUser.username}" else "Requires mutual follow to message",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Button(
                      onClick = {
                        if (eligibility is MessagingEligibility.Denied) {
                          sharedFeedback = eligibility.reason
                          showShareInboxSheet = false
                        } else {
                          val conv = viewModel.getOrCreateConversation(targetUser)
                          viewModel.sendExtendedDirectMessage(
                            convId = conv.id,
                            text = "Shared reel from @${currentReel.author.username}: ${currentReel.caption}".trim(),
                            mediaUrl = currentReel.mediaUrls.firstOrNull(),
                            mediaType = "VIDEO"
                          )
                          sharedFeedback = "Reel sent to ${targetUser.displayName}."
                          showShareInboxSheet = false
                        }
                      },
                      enabled = canMessage,
                      shape = RoundedCornerShape(16.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                      modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                      Text("Send", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }
    }

    // Reel Comments Sheet
    if (showCommentsSheet) {
      val currentReel = videoPosts.getOrNull(pagerState.currentPage)
      if (currentReel != null) {
        ModalBottomSheet(
          onDismissRequest = { showCommentsSheet = false },
          sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
          contentWindowInsets = { WindowInsets(0.dp) },
          containerColor = MaterialTheme.colorScheme.surface
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 8.dp)
              .navigationBarsPadding()
              .imePadding()
          ) {
            Text(
              text = "Comments (${maxOf(currentReel.commentsCount, currentReel.comments.size)})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (currentReel.comments.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  "No comments yet. Share your thoughts below.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                items(currentReel.comments, key = { it.id }) { c ->
                  val commentTime = rememberRelativeTimestamp(c.createdAtIso, c.timestamp)
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                  ) {
                    VybeAvatar(user = c.author, size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(c.author.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                      Text(c.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                      if (commentTime.isNotBlank()) {
                        Text(commentTime, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = reelCommentText,
                onValueChange = { reelCommentText = it },
                placeholder = { Text("Add a comment...") },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
                singleLine = true
              )
              Spacer(modifier = Modifier.width(8.dp))
              IconButton(
                onClick = {
                  if (reelCommentText.isNotBlank()) {
                    viewModel.addComment(currentReel.id, reelCommentText.trim())
                    reelCommentText = ""
                  }
                },
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(VybeViolet)
              ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }
        }
      }
    }

    if (sharedFeedback != null) {
      Snackbar(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp)
      ) {
        Text(sharedFeedback ?: "")
      }
      LaunchedEffect(sharedFeedback) {
        delay(2500)
        sharedFeedback = null
      }
    }

    if (showBreakdownSheetReel != null) {
      val rPost = showBreakdownSheetReel!!
      ReelReactionBreakdownSheet(
        totalCount = rPost.totalReactionsCount,
        breakdown = rPost.reactionsCount,
        userReaction = rPost.userReaction,
        onReactionClick = { rx ->
          viewModel.reactToPost(rPost.id, rx)
        },
        onDismiss = { showBreakdownSheetReel = null }
      )
    }
  }
}

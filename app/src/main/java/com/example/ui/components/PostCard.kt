package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.model.VybeReaction
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.ui.zIndex
import com.example.ui.theme.*
import com.example.util.FeedAudioController
import com.example.util.rememberRelativeTimestamp

import com.example.data.model.MusicAttachment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCard(
  post: Post,
  onLikeClick: () -> Unit,
  onSaveClick: () -> Unit,
  onCommentClick: () -> Unit,
  onShareClick: () -> Unit,
  onUserClick: (User) -> Unit,
  onReact: ((VybeReaction) -> Unit)? = null,
  onRemixClick: (() -> Unit)? = null,
  onAddComment: ((String) -> Unit)? = null,
  onLoadComments: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  onReportPost: ((String) -> Unit)? = null,
  onPostViewed: ((String) -> Unit)? = null,
  currentUserId: String? = null,
  shouldAutoPlay: Boolean = false,
  isAudioEnabled: Boolean = false,
  shouldPlayAudio: Boolean = shouldAutoPlay && isAudioEnabled,
  isHighlighted: Boolean = false,
  isFollowing: Boolean = false,
  onFollowClick: (() -> Unit)? = null,
  onToggleAudio: ((Boolean) -> Unit)? = null,
  onOpenMusicTrack: ((MusicAttachment) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  var showCommentDialog by remember { mutableStateOf(false) }
  var showOptionsMenu by remember { mutableStateOf(false) }
  var showAnalyticsSheet by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var showReportDialog by remember { mutableStateOf(false) }
  var showReactionSelector by remember { mutableStateOf(false) }
  var selectedReportReason by remember { mutableStateOf("Spam or Misleading") }
  var commentText by remember { mutableStateOf("") }
  var detectedMediaRatio by remember(post.id, post.mediaAspectRatio) { mutableStateOf(post.mediaAspectRatio) }
  var showDoubleTapResonance by remember { mutableStateOf(false) }

  LaunchedEffect(showDoubleTapResonance) {
    if (showDoubleTapResonance) {
      delay(850L)
      showDoubleTapResonance = false
    }
  }

  val isAuthor = currentUserId != null && currentUserId == post.author.id
  val relativeTimestamp = rememberRelativeTimestamp(post.createdAtIso, post.timestamp)

  LaunchedEffect(post.id) {
    onPostViewed?.invoke(post.id)
  }

  LaunchedEffect(showCommentDialog) {
    if (showCommentDialog) {
      onLoadComments?.invoke()
    }
  }

  val firstMediaUrl = post.mediaUrls.firstOrNull().orEmpty()
  val isVideoPost = post.mediaUrls.isNotEmpty() && (
    post.mediaType == MediaType.VIDEO ||
      post.mediaType == MediaType.REEL ||
      post.isVybesReel ||
      firstMediaUrl.contains(".mp4", ignoreCase = true) ||
      firstMediaUrl.contains(".mov", ignoreCase = true) ||
      firstMediaUrl.contains(".webm", ignoreCase = true)
    )
  val anySheetOpen = showCommentDialog || showAnalyticsSheet || showDeleteConfirm || showReportDialog

  // Coordinate non-overlapping music playback for photo/text posts that carry an audio track
  LaunchedEffect(post.id, post.musicAttachment, post.audioTrackTitle, isVideoPost, shouldPlayAudio, anySheetOpen) {
    val activePlay = shouldPlayAudio && !anySheetOpen
    val attached = post.musicAttachment
    if (!isVideoPost && attached != null && !attached.streamUrl.isNullOrBlank()) {
      if (activePlay) {
        com.example.util.music.MusicPreviewController.playAttachment(attached, muted = false)
      } else if (com.example.util.music.MusicPreviewController.isPlayingTrack(attached.trackId)) {
        com.example.util.music.MusicPreviewController.stop()
      }
    } else if (!isVideoPost && !post.audioTrackTitle.isNullOrBlank()) {
      FeedAudioController.syncPostMusicTrack(
        postId = post.id,
        audioTrackTitle = post.audioTrackTitle,
        shouldPlayAudio = activePlay
      )
    }
  }

  DisposableEffect(post.id) {
    onDispose {
      val attached = post.musicAttachment
      if (!isVideoPost && attached != null && com.example.util.music.MusicPreviewController.isPlayingTrack(attached.trackId)) {
        com.example.util.music.MusicPreviewController.stop()
      }
      if (!isVideoPost && !post.audioTrackTitle.isNullOrBlank()) {
        FeedAudioController.syncPostMusicTrack(post.id, post.audioTrackTitle, shouldPlayAudio = false)
      }
    }
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 7.dp)
      .testTag("post_card_${post.id}"),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(
      width = if (isHighlighted) 1.8.dp else 1.dp,
      color = if (isHighlighted) VybeViolet else Color(0xFF1B2338)
    )
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Header: Author + Verified + Relative Timestamp • Privacy Globe + Follow Button + 3-dot Menu
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        VybeAvatar(user = post.author, size = 40.dp, onClick = { onUserClick(post.author) })

        Spacer(modifier = Modifier.width(10.dp))

        Column(
          modifier = Modifier
            .weight(1f)
            .clickable { onUserClick(post.author) }
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = post.author.displayName.ifBlank { post.author.username },
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            VerificationBadge(user = post.author)
          }
          Spacer(modifier = Modifier.height(2.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = if (relativeTimestamp.isNotBlank()) "$relativeTimestamp • " else "",
              fontSize = 12.sp,
              color = Color.White.copy(alpha = 0.55f)
            )
            Icon(
              imageVector = Icons.Outlined.Public,
              contentDescription = "Public",
              tint = Color.White.copy(alpha = 0.55f),
              modifier = Modifier.size(12.dp)
            )
            if (!post.locationName.isNullOrBlank()) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "• ${post.locationName}",
                fontSize = 11.sp,
                color = VybeBrandCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // Outlined Follow Pill Button (if not author)
        if (!isAuthor) {
          Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2E3856)),
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .clickable { onFollowClick?.invoke() }
              .testTag("follow_button_${post.id}")
          ) {
            Text(
              text = if (isFollowing) "Following" else "Follow",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
        }

        // 3-dot overflow menu
        Box {
          IconButton(
            onClick = { showOptionsMenu = true },
            modifier = Modifier
              .size(36.dp)
              .testTag("post_options_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "Post options",
              tint = Color.White.copy(alpha = 0.7f),
              modifier = Modifier.size(20.dp)
            )
          }

          DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false }
          ) {
            if (isAuthor) {
              DropdownMenuItem(
                text = { Text("Post Insights") },
                onClick = {
                  showOptionsMenu = false
                  showAnalyticsSheet = true
                },
                leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null, tint = VybeViolet) }
              )
              DropdownMenuItem(
                text = { Text("Delete Post", color = VybeDanger) },
                onClick = {
                  showOptionsMenu = false
                  showDeleteConfirm = true
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VybeDanger) }
              )
            } else {
              DropdownMenuItem(
                text = { Text("Share Post") },
                onClick = {
                  showOptionsMenu = false
                  onShareClick()
                },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
              )
              if (onReportPost != null) {
                DropdownMenuItem(
                  text = { Text("Report Post", color = VybeDanger) },
                  onClick = {
                    showOptionsMenu = false
                    showReportDialog = true
                  },
                  leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = VybeDanger) }
                )
              }
            }
          }
        }
      }

      // Caption positioned above media
      if (post.caption.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = post.caption,
          fontSize = 14.sp,
          color = Color.White,
          lineHeight = 20.sp
        )
      }

      // Interactive Feed Music Pill if present
      val effectiveTrackLabel = post.musicAttachment?.displayLabel ?: post.audioTrackTitle
      if (!effectiveTrackLabel.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            color = if (shouldPlayAudio) VybeViolet.copy(alpha = 0.16f) else Color(0xFF141A2E),
            shape = RoundedCornerShape(10.dp),
            border = if (shouldPlayAudio) BorderStroke(1.dp, VybeViolet.copy(alpha = 0.5f)) else BorderStroke(1.dp, Color(0xFF222C46)),
            modifier = Modifier
              .weight(1f, fill = false)
              .clip(RoundedCornerShape(10.dp))
              .clickable { onToggleAudio?.invoke(!isAudioEnabled) }
              .testTag("post_music_toggle_${post.id}")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = "Audio track",
                tint = if (shouldPlayAudio) VybeViolet else VybeMagenta,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = effectiveTrackLabel,
                fontSize = 11.sp,
                fontWeight = if (shouldPlayAudio) FontWeight.SemiBold else FontWeight.Normal,
                color = if (shouldPlayAudio) Color.White else Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Icon(
                imageVector = if (isAudioEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                contentDescription = if (isAudioEnabled) "Mute feed music" else "Enable feed music",
                tint = if (isAudioEnabled) VybeViolet else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(15.dp)
              )
            }
          }

          if (post.musicAttachment != null && onOpenMusicTrack != null) {
            TextButton(
              onClick = { onOpenMusicTrack(post.musicAttachment) },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
              modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("post_open_track_${post.id}")
            ) {
              Text(
                text = "Track",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VybeBrandCyan
              )
              Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open music track page",
                tint = VybeBrandCyan,
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }
      }

      // Media Container (Supports 1:1, 4:5, and 16:9 cleanly without stretching or side black bars)
      if (post.mediaUrls.isNotEmpty()) {
        val mediaUrl = post.mediaUrls.first()
        val isVideo = post.mediaType == MediaType.VIDEO ||
          post.mediaType == MediaType.REEL ||
          post.isVybesReel ||
          mediaUrl.contains(".mp4", ignoreCase = true) ||
          mediaUrl.contains(".mov", ignoreCase = true) ||
          mediaUrl.contains(".webm", ignoreCase = true)

        val containerRatio = resolveFeedContainerAspectRatio(
          rawRatio = detectedMediaRatio ?: post.mediaAspectRatio,
          isReel = false
        )

        Spacer(modifier = Modifier.height(12.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(containerRatio)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(post.id) {
              detectTapGestures(
                onDoubleTap = {
                  showDoubleTapResonance = true
                  if (onReact != null) {
                    onReact(VybeReaction.LOVE)
                  } else {
                    onLikeClick()
                  }
                }
              )
            }
        ) {
          if (isVideo) {
            VybeVideoPlayer(
              videoUrl = mediaUrl,
              autoPlay = shouldAutoPlay,
              isLooping = true,
              initialMuted = !shouldPlayAudio,
              isPausedExternal = !shouldAutoPlay || anySheetOpen,
              respectReducedMotion = true,
              fitMode = VideoFitMode.COVER_PROPORTIONAL,
              feedAudioOwnerId = post.id,
              onAspectRatioDetected = { ratio ->
                detectedMediaRatio = ratio
              },
              onDoubleTapOverlay = {
                showDoubleTapResonance = true
                if (onReact != null) {
                  onReact(VybeReaction.LOVE)
                } else {
                  onLikeClick()
                }
              },
              onMuteToggled = { muted ->
                onToggleAudio?.invoke(!muted)
              },
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101524))
            ) {
              AsyncImage(
                model = mediaUrl,
                contentDescription = "Post media by ${post.author.displayName}",
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                  if (post.mediaAspectRatio == null) {
                    val d = state.result.drawable
                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                      detectedMediaRatio = d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                    }
                  }
                },
                modifier = Modifier.fillMaxSize()
              )
              if (!post.audioTrackTitle.isNullOrBlank() && onToggleAudio != null) {
                IconButton(
                  onClick = { onToggleAudio(!isAudioEnabled) },
                  modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(44.dp)
                    .testTag("photo_music_mute_toggle_${post.id}")
                ) {
                  Box(
                    modifier = Modifier
                      .size(32.dp)
                      .clip(CircleShape)
                      .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = if (isAudioEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                      contentDescription = if (isAudioEnabled) "Mute feed music" else "Enable feed music",
                      tint = Color.White,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }

          // Carousel page indicator pill (e.g. 1/5)
          val totalMediaCount = maxOf(post.mediaUrls.size, 1)
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(10.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black.copy(alpha = 0.65f))
              .padding(horizontal = 9.dp, vertical = 3.5.dp)
          ) {
            Text(
              text = "1/$totalMediaCount",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }

          // Double-Tap Animated Resonance Feedback
          androidx.compose.animation.AnimatedVisibility(
            visible = showDoubleTapResonance,
            enter = scaleIn(initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(targetScale = 1.35f) + fadeOut(),
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
                size = 50.dp,
                tint = Color(0xFFEC4899)
              )
            }
          }
        }
      }

      // Action Bar: Signature 🫰 Reaction with count, Comment with count, Share with count
      Spacer(modifier = Modifier.height(14.dp))
      val displayedCommentsCount = maxOf(post.commentsCount, post.comments.size)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left: Signature 🫰 Finger Heart Reaction Button + Count
        Box {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .testTag("like_button_${post.id}")
              .pointerInput(post.id, post.userReaction, post.isLiked) {
                detectTapGestures(
                  onTap = {
                    if (onReact != null) {
                      if (post.isLiked) {
                        onLikeClick()
                      } else {
                        onReact(VybeReaction.LOVE)
                      }
                    } else {
                      onLikeClick()
                    }
                  },
                  onLongPress = {
                    showReactionSelector = !showReactionSelector
                  }
                )
              }
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                  Brush.sweepGradient(
                    listOf(
                      Color(0xFF3B82F6),
                      Color(0xFF8B5CF6),
                      Color(0xFFEC4899),
                      Color(0xFF3B82F6)
                    )
                  )
                )
                .padding(1.8.dp)
                .clip(CircleShape)
                .background(Color(0xFF0A0E1A)),
              contentAlignment = Alignment.Center
            ) {
              val activeRx = post.userReaction ?: VybeReaction.LOVE
              VybeReactionOutlineIcon(
                reaction = activeRx,
                size = 20.dp,
                tint = if (post.isLiked) reactionColor(activeRx) else Color.White
              )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "${post.likesCount}",
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color.White
            )
          }

          if (showReactionSelector) {
            Box(
              modifier = Modifier
                .offset(y = (-52).dp)
                .zIndex(20f)
            ) {
              VybeReactionSelector(
                selectedReaction = post.userReaction,
                onSelectReaction = { rx ->
                  showReactionSelector = false
                  if (onReact != null) {
                    onReact(rx)
                  } else {
                    onLikeClick()
                  }
                },
                onDismiss = { showReactionSelector = false }
              )
            }
          }
        }

        // Center: Minimalist Comment Outline + Count
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable { showCommentDialog = true }
            .testTag("comment_button_${post.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = "Comments",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(7.dp))
          Text(
            text = "$displayedCommentsCount",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
          )
        }

        // Right: Minimalist Share Curved Arrow + Count
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable { onShareClick() }
            .testTag("share_button_${post.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.Reply,
            contentDescription = "Share",
            tint = Color.White,
            modifier = Modifier
              .size(21.dp)
              .graphicsLayer(scaleX = -1f)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${post.sharesCount.coerceAtLeast(12)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
          )
        }
      }

      // Comments Preview Section: top comment + "View all X comments >"
      Spacer(modifier = Modifier.height(12.dp))
      val topComment = post.comments.firstOrNull()
      val previewAuthor = topComment?.author?.username ?: "priya.__"
      val previewContent = topComment?.content ?: "This view is insane! 😍"
      val previewAvatar = topComment?.author?.avatarUrl
      val previewTime = topComment?.timestamp ?: "1h ago"

      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(22.dp)
              .clip(CircleShape)
              .background(Color(0xFF1E2638)),
            contentAlignment = Alignment.Center
          ) {
            if (!previewAvatar.isNullOrBlank()) {
              AsyncImage(
                model = previewAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
              )
            } else {
              Text(
                text = previewAuthor.take(1).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = previewAuthor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = previewContent,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = previewTime,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Reply",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.clickable { showCommentDialog = true }
              )
            }
          }
          IconButton(
            onClick = { /* like comment */ },
            modifier = Modifier.size(28.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.FavoriteBorder,
              contentDescription = "Like comment",
              tint = Color.White.copy(alpha = 0.6f),
              modifier = Modifier.size(15.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "View all $displayedCommentsCount comments >",
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF818CF8),
          modifier = Modifier
            .clickable { showCommentDialog = true }
            .testTag("view_all_comments_${post.id}")
        )
      }
    }
  }

  // VYBE Comments Bottom Sheet (Keyboard-safe)
  if (showCommentDialog) {
    val displayedCount = maxOf(post.commentsCount, post.comments.size)
    val commentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showCommentDialog = false },
      sheetState = commentSheetState,
      contentWindowInsets = { WindowInsets(0.dp) },
      dragHandle = { BottomSheetDefaults.DragHandle() },
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .navigationBarsPadding()
          .imePadding()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Comments ($displayedCount)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .heightIn(min = 100.dp, max = 260.dp)
        ) {
          if (post.comments.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  Icons.Outlined.ChatBubbleOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No comments yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                  "Start a thoughtful conversation below.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              items(post.comments, key = { it.id }) { comment ->
                val commentRelativeTime = rememberRelativeTimestamp(comment.createdAtIso, comment.timestamp)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.Top
                ) {
                  VybeAvatar(user = comment.author, size = 36.dp, onClick = { onUserClick(comment.author) })
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(comment.author.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      if (commentRelativeTime.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(commentRelativeTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = comment.content,
                      fontSize = 13.sp,
                      color = MaterialTheme.colorScheme.onSurface,
                      lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "Reply",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.clickable {
                        commentText = "@${comment.author.username} "
                      }
                    )
                  }
                }
              }
            }
          }
        }

        // Comment Input Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            placeholder = { Text("Add a comment for ${post.author.displayName}...") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("comment_input_${post.id}")
          )
          Spacer(modifier = Modifier.width(8.dp))
          IconButton(
            onClick = {
              val clean = commentText.trim()
              if (clean.isNotEmpty()) {
                if (onAddComment != null) {
                  onAddComment(clean)
                } else {
                  onCommentClick()
                }
                commentText = ""
              }
            },
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(VybeViolet)
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Comment", tint = Color.White, modifier = Modifier.size(18.dp))
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }

  // Personal Post Analytics Bottom Sheet
  if (showAnalyticsSheet) {
    ModalBottomSheet(
      onDismissRequest = { showAnalyticsSheet = false },
      dragHandle = { BottomSheetDefaults.DragHandle() },
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .navigationBarsPadding()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Post Insights",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Live backend metrics for your post",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Surface(
            color = VybeViolet.copy(alpha = 0.18f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(
              text = "Author View",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = VybeViolet,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          AnalyticsMetricCard(
            title = "Views",
            value = "${post.viewsCount}",
            icon = Icons.Default.Visibility,
            iconTint = VybeCyan,
            modifier = Modifier.weight(1f)
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.weight(1f)
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              VybeLikeIcon(isLiked = true, size = 18.dp, contentDescriptionText = null)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "${post.likesCount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Likes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          AnalyticsMetricCard(
            title = "Comments",
            value = "${maxOf(post.commentsCount, post.comments.size)}",
            icon = Icons.Default.ChatBubble,
            iconTint = VybeViolet,
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          AnalyticsMetricCard(
            title = "Saves",
            value = "${post.savesCount}",
            icon = Icons.Default.Bookmark,
            iconTint = VybeViolet,
            modifier = Modifier.weight(1f)
          )
          AnalyticsMetricCard(
            title = "Shares",
            value = "${post.sharesCount}",
            icon = Icons.Default.Share,
            iconTint = VybeBrandCyan,
            modifier = Modifier.weight(1f)
          )
          val totalInteractions = post.likesCount + maxOf(post.commentsCount, post.comments.size) + post.savesCount
          val rate = ((totalInteractions.toFloat() / post.viewsCount.coerceAtLeast(1).toFloat()) * 100).let { "%.1f".format(it) }
          AnalyticsMetricCard(
            title = "Engagement",
            value = "$rate%",
            icon = Icons.Default.TrendingUp,
            iconTint = VybeOnlineGreen,
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
          onClick = { showAnalyticsSheet = false },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Text("Done", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }

  // Delete Post Confirmation Dialog
  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete Post?") },
      text = {
        Text("This permanently deletes the post record and removes the associated media object from Supabase Storage.")
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDeleteClick?.invoke()
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showDeleteConfirm = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Report Post Dialog
  if (showReportDialog && onReportPost != null) {
    val reasons = listOf(
      "Spam or Misleading",
      "Harassment or Bullying",
      "Copyright Infringement",
      "Inappropriate Content",
      "Impersonation"
    )
    AlertDialog(
      onDismissRequest = { showReportDialog = false },
      title = { Text("Report Post", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Select a reason for reporting this post:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          reasons.forEach { reason ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (selectedReportReason == reason) VybeDanger.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { selectedReportReason = reason }
            ) {
              Text(
                text = reason,
                fontSize = 13.sp,
                fontWeight = if (selectedReportReason == reason) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showReportDialog = false
            onReportPost(selectedReportReason)
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Submit Report", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReportDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun AnalyticsMetricCard(
  title: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  iconTint: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

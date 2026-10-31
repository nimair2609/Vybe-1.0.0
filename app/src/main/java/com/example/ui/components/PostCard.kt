package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
  isFollowRequested: Boolean = false,
  followsMe: Boolean = post.author.followsMe,
  onFollowClick: (() -> Unit)? = null,
  onToggleAudio: ((Boolean) -> Unit)? = null,
  onOpenMusicTrack: ((MusicAttachment) -> Unit)? = null,
  onDoubleTapLike: (() -> Unit)? = null,
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
  var doubleTapBurstNonce by remember { mutableIntStateOf(0) }
  val likedCommentIds = remember(post.id) { mutableStateListOf<String>() }

  val isCurrentlyLiked = post.isLiked || post.userReaction != null
  val handleDoubleTapLike = {
    doubleTapBurstNonce += 1
    showDoubleTapResonance = true
    if (onDoubleTapLike != null) {
      onDoubleTapLike()
    } else if (!isCurrentlyLiked) {
      if (onReact != null) onReact(VybeReaction.LOVE) else onLikeClick()
    }
  }

  LaunchedEffect(showDoubleTapResonance, doubleTapBurstNonce) {
    if (showDoubleTapResonance) {
      delay(720L)
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

  val isDark = LocalIsDarkTheme.current
  val cardBg = if (isDark) Color(0xFF0A0E1A) else Color.White
  val cardBorder = if (isDark) Color(0xFF1B2338) else Color(0xFFE2E8F0)
  val primaryText = if (isDark) Color.White else Color(0xFF0F172A)
  val secondaryText = if (isDark) Color.White.copy(alpha = 0.86f) else Color(0xFF334155)
  val mutedText = if (isDark) Color.White.copy(alpha = 0.58f) else Color(0xFF64748B)
  val followPillActiveBg = if (isDark) Color(0xFF161F33) else Color(0xFFF1F5F9)
  val followPillActiveBorder = if (isDark) Color(0xFF26334D) else Color(0xFFCBD5E1)
  val followPillInactiveBorder = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)
  val musicPillBg = if (isDark) Color(0xFF141A2E) else Color(0xFFF8FAFC)
  val musicPillBorder = if (isDark) Color(0xFF222C46) else Color(0xFFE2E8F0)
  val mediaPlaceholderBg = if (isDark) Color(0xFF101524) else Color(0xFFF1F5F9)
  val accentCyan = if (isDark) VybeBrandCyan else Color(0xFF0284C7)
  val linkAccent = if (isDark) Color(0xFF818CF8) else VybeVioletDark

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 7.dp)
      .testTag("post_card_${post.id}"),
    colors = CardDefaults.cardColors(containerColor = cardBg),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(
      width = if (isHighlighted) 1.8.dp else 1.dp,
      color = if (isHighlighted) VybeViolet else cardBorder
    )
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Header: Single-border Author Avatar + Verified + Relative Timestamp • Privacy + Follow + 3-dot Menu
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        VybeAvatar(
          user = post.author,
          size = 42.dp,
          showPulseDot = false,
          onClick = { onUserClick(post.author) }
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
          modifier = Modifier
            .weight(1f)
            .clickable { onUserClick(post.author) }
        ) {
          val safeDisplayName = remember(post.author.displayName, post.author.username) {
            post.author.displayName.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
              ?: post.author.username.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
              ?: "VYBE Member"
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = safeDisplayName,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = primaryText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            VerificationBadge(user = post.author)
          }
          Spacer(modifier = Modifier.height(2.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.author.username.isNotBlank()) {
              Text(
                text = "@${post.author.username} • ",
                fontSize = 12.sp,
                color = mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Text(
              text = if (relativeTimestamp.isNotBlank()) "$relativeTimestamp • " else "",
              fontSize = 12.sp,
              color = mutedText,
              modifier = Modifier.testTag("post_timestamp_${post.id}")
            )
            Icon(
              imageVector = if (post.author.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public,
              contentDescription = if (post.author.isPrivate) "Private" else "Public",
              tint = mutedText,
              modifier = Modifier.size(12.dp)
            )
            if (!post.locationName.isNullOrBlank()) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "• ${post.locationName}",
                fontSize = 11.sp,
                color = accentCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // Sleek Pill Follow Button
        if (!isAuthor && onFollowClick != null) {
          Surface(
            color = if (isFollowing) followPillActiveBg else Color.Transparent,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (isFollowing) followPillActiveBorder else followPillInactiveBorder),
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .clickable { onFollowClick.invoke() }
              .testTag("follow_button_${post.id}")
          ) {
            Text(
              text = if (isFollowing) "LINKED" else if (isFollowRequested) "LINKUP Requested" else "LINK UP",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = primaryText,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
        }

        // 3-dot overflow menu
        Box {
          IconButton(
            onClick = { showOptionsMenu = true },
            modifier = Modifier
              .size(44.dp)
              .testTag("post_options_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "Post options",
              tint = mutedText,
              modifier = Modifier.size(20.dp)
            )
          }

          DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false }
          ) {
            DropdownMenuItem(
              text = { Text("Share Post") },
              onClick = {
                showOptionsMenu = false
                onShareClick()
              },
              leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
            )
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
            } else if (onReportPost != null) {
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

      // Caption positioned above media
      if (post.caption.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = post.caption,
          fontSize = 14.sp,
          color = primaryText,
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
            color = if (shouldPlayAudio) VybeViolet.copy(alpha = 0.16f) else musicPillBg,
            shape = RoundedCornerShape(10.dp),
            border = if (shouldPlayAudio) BorderStroke(1.dp, VybeViolet.copy(alpha = 0.5f)) else BorderStroke(1.dp, musicPillBorder),
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
                color = if (shouldPlayAudio) primaryText else secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Icon(
                imageVector = if (isAudioEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                contentDescription = if (isAudioEnabled) "Mute feed music" else "Enable feed music",
                tint = if (isAudioEnabled) VybeViolet else mutedText,
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
                color = accentCyan
              )
              Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open music track page",
                tint = accentCyan,
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }
      }

      // Media Container with multi-photo HorizontalPager & dynamic counter
      if (post.mediaUrls.isNotEmpty()) {
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
            .pointerInput(post.id, isCurrentlyLiked) {
              detectTapGestures(
                onDoubleTap = { handleDoubleTapLike() }
              )
            }
        ) {
          if (post.mediaUrls.size > 1) {
            val pagerState = rememberPagerState(pageCount = { post.mediaUrls.size })
            HorizontalPager(
              state = pagerState,
              modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
              val mediaUrl = post.mediaUrls[pageIndex]
              val isVideo = mediaUrl.contains(".mp4", ignoreCase = true) ||
                mediaUrl.contains(".mov", ignoreCase = true) ||
                mediaUrl.contains(".webm", ignoreCase = true)

              if (isVideo) {
                VybeVideoPlayer(
                  videoUrl = mediaUrl,
                  autoPlay = shouldAutoPlay && (pagerState.currentPage == pageIndex),
                  isLooping = true,
                  initialMuted = !shouldPlayAudio,
                  isPausedExternal = !shouldAutoPlay || anySheetOpen || (pagerState.currentPage != pageIndex),
                  respectReducedMotion = true,
                  fitMode = VideoFitMode.COVER_PROPORTIONAL,
                  feedAudioOwnerId = post.id,
                  onAspectRatioDetected = { ratio ->
                    detectedMediaRatio = ratio
                  },
                  onDoubleTapOverlay = { handleDoubleTapLike() },
                  onMuteToggled = { muted -> onToggleAudio?.invoke(!muted) },
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(mediaPlaceholderBg)
                ) {
                  AsyncImage(
                    model = mediaUrl,
                    contentDescription = "Post media ${pageIndex + 1} by ${post.author.displayName}",
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
                }
              }
            }

            // Dynamic Photo Counter Pill (e.g. 1/5, 2/5)
            Box(
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 9.dp, vertical = 3.5.dp)
            ) {
              Text(
                text = "${pagerState.currentPage + 1}/${post.mediaUrls.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          } else {
            val mediaUrl = post.mediaUrls.first()
            val isVideo = post.mediaType == MediaType.VIDEO ||
              post.mediaType == MediaType.REEL ||
              post.isVybesReel ||
              mediaUrl.contains(".mp4", ignoreCase = true) ||
              mediaUrl.contains(".mov", ignoreCase = true) ||
              mediaUrl.contains(".webm", ignoreCase = true)

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
                onDoubleTapOverlay = { handleDoubleTapLike() },
                onMuteToggled = { muted ->
                  onToggleAudio?.invoke(!muted)
                },
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(mediaPlaceholderBg)
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
          }

          // Double-Tap Central Finger Heart Burst Feedback
          FingerHeartDoubleTapOverlay(
            visible = showDoubleTapResonance,
            burstKey = doubleTapBurstNonce,
            modifier = Modifier.align(Alignment.Center)
          )
        }
      }

      // Action Bar: Optically aligned Finger Heart, Comment, Share, and Bookmark buttons
      Spacer(modifier = Modifier.height(10.dp))
      val displayedCommentsCount = if (post.comments.isNotEmpty()) post.comments.size else post.commentsCount.coerceAtLeast(0)
      val isLiked = post.isLiked || post.userReaction != null

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left Action Group: Finger Heart, Comment, Share
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // 1. Global VYBE Finger Heart Reaction
          FingerHeartReaction(
            isLiked = isLiked,
            count = post.likesCount,
            iconSize = 23.dp,
            countColorOverride = primaryText,
            testTag = "like_button_${post.id}",
            onClick = {
              if (isLiked) {
                if (post.userReaction != null && onReact != null) {
                  onReact(post.userReaction!!)
                } else {
                  onLikeClick()
                }
              } else {
                if (onReact != null) onReact(VybeReaction.LOVE) else onLikeClick()
              }
            }
          )

          // 2. Comment
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .defaultMinSize(minHeight = 44.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable { showCommentDialog = true }
              .padding(horizontal = 2.dp, vertical = 6.dp)
              .testTag("comment_button_${post.id}")
          ) {
            Box(
              modifier = Modifier.size(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Comments",
                tint = primaryText,
                modifier = Modifier.size(22.dp)
              )
            }
            if (displayedCommentsCount > 0) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "$displayedCommentsCount",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
              )
            }
          }

          // 3. Share — optically centered 24dp frame matching Like, Comment & Bookmark
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .defaultMinSize(minHeight = 44.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable { onShareClick() }
              .padding(horizontal = 2.dp, vertical = 6.dp)
              .testTag("share_button_${post.id}")
          ) {
            Box(
              modifier = Modifier.size(24.dp),
              contentAlignment = Alignment.Center
            ) {
              VybeCenteredShareIcon(
                tint = primaryText,
                size = 22.dp,
                contentDescriptionText = "Share"
              )
            }
            if (post.sharesCount > 0) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${post.sharesCount}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
              )
            }
          }
        }

        // Right Action: Bookmark / Save — aligned on identical 44dp vertical center
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onSaveClick() }
            .testTag("save_button_${post.id}"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (post.isSaved) "Remove from saved" else "Save post",
            tint = if (post.isSaved) accentCyan else primaryText,
            modifier = Modifier.size(23.dp)
          )
        }
      }

      // Comments Preview Section
      Spacer(modifier = Modifier.height(8.dp))
      val topComment = post.comments.firstOrNull()

      if (topComment != null) {
        val previewAuthor = topComment.author.displayName.ifBlank { topComment.author.username }
        val previewContent = topComment.content
        val previewAvatar = topComment.author.avatarUrl
        val previewTime = rememberRelativeTimestamp(topComment.createdAtIso, topComment.timestamp)

        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(followPillActiveBg),
              contentAlignment = Alignment.Center
            ) {
              if (previewAvatar.isNotBlank()) {
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
                  color = primaryText
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = previewAuthor,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = primaryText
                )
                VerificationBadge(user = topComment.author, iconSize = 14.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = previewContent,
                  fontSize = 12.sp,
                  color = secondaryText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.weight(1f))
                val isPreviewCommentLiked = likedCommentIds.contains(topComment.id)
                FingerHeartReaction(
                  isLiked = isPreviewCommentLiked,
                  iconSize = 15.dp,
                  compactPadding = true,
                  testTag = "comment_preview_like_${topComment.id}",
                  onClick = {
                    if (isPreviewCommentLiked) likedCommentIds.remove(topComment.id)
                    else likedCommentIds.add(topComment.id)
                  }
                )
              }
              Spacer(modifier = Modifier.height(2.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (previewTime.isNotBlank()) {
                  Text(
                    text = previewTime,
                    fontSize = 10.sp,
                    color = mutedText
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                  text = "Reply",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = secondaryText,
                  modifier = Modifier.clickable { showCommentDialog = true }
                )
              }
            }
          }

          if (displayedCommentsCount > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "View all $displayedCommentsCount comments >",
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = linkAccent,
              modifier = Modifier
                .clickable { showCommentDialog = true }
                .testTag("view_all_comments_${post.id}")
            )
          }
        }
      } else {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showCommentDialog = true }
            .padding(vertical = 4.dp)
            .testTag("view_all_comments_${post.id}"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (displayedCommentsCount > 0) "View all $displayedCommentsCount comments >" else "Add a comment...",
            fontSize = 12.sp,
            color = if (displayedCommentsCount > 0) linkAccent else mutedText,
            fontWeight = if (displayedCommentsCount > 0) FontWeight.Medium else FontWeight.Normal
          )
        }
      }
    }
  }

  // VYBE Comments Bottom Sheet (Keyboard-safe)
  if (showCommentDialog) {
    val displayedCount = if (post.comments.isNotEmpty()) post.comments.size else post.commentsCount.coerceAtLeast(0)
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
                      VerificationBadge(user = comment.author, iconSize = 15.dp)
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
                  val isCommentLiked = likedCommentIds.contains(comment.id)
                  FingerHeartReaction(
                    isLiked = isCommentLiked,
                    count = if (isCommentLiked) 1 else 0,
                    iconSize = 17.dp,
                    compactPadding = true,
                    testTag = "comment_like_button_${comment.id}",
                    onClick = {
                      if (isCommentLiked) likedCommentIds.remove(comment.id)
                      else likedCommentIds.add(comment.id)
                    }
                  )
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

        val effectiveCommentTotal = if (post.comments.isNotEmpty()) post.comments.size else post.commentsCount.coerceAtLeast(0)

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
            value = "$effectiveCommentTotal",
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
          val totalInteractions = post.likesCount + effectiveCommentTotal + post.savesCount
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

@Composable
fun VybeCenteredShareIcon(
  tint: Color,
  size: Dp = 22.dp,
  contentDescriptionText: String? = "Share"
) {
  Canvas(
    modifier = Modifier
      .size(size)
      .then(
        if (contentDescriptionText != null) {
          Modifier.semantics { contentDescription = contentDescriptionText }
        } else {
          Modifier
        }
      )
  ) {
    val w = this.size.width
    val h = this.size.height
    val strokePx = (w * 0.085f).coerceAtLeast(1.8f)

    // Optically centered paper-plane outline in a 24x24 normalized grid (visual centroid at 12,12)
    val outer = Path().apply {
      moveTo(w * 0.875f, h * 0.145f)
      lineTo(w * 0.125f, h * 0.455f)
      lineTo(w * 0.435f, h * 0.565f)
      lineTo(w * 0.545f, h * 0.875f)
      close()
    }
    drawPath(
      path = outer,
      color = tint,
      style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawLine(
      color = tint,
      start = androidx.compose.ui.geometry.Offset(w * 0.875f, h * 0.145f),
      end = androidx.compose.ui.geometry.Offset(w * 0.435f, h * 0.565f),
      strokeWidth = strokePx,
      cap = StrokeCap.Round
    )
  }
}


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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.zIndex
import com.example.ui.theme.*
import com.example.util.rememberRelativeTimestamp

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
  onToggleAudio: ((Boolean) -> Unit)? = null,
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

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .testTag("post_card_${post.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Header: Author + Verified + Relative Timestamp + Location + Mood Tag + Menu
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        VybeAvatar(user = post.author, size = 42.dp, onClick = { onUserClick(post.author) })

        Spacer(modifier = Modifier.width(10.dp))

        Column(
          modifier = Modifier
            .weight(1f)
            .clickable { onUserClick(post.author) }
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = post.author.displayName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            VerificationBadge(user = post.author)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "@${post.author.username}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (relativeTimestamp.isNotBlank()) {
              Text(
                text = " • $relativeTimestamp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1
              )
            }
          }
          if (!post.locationName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = VybeBrandCyan,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = post.locationName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // Mood Zone Pill
        if (post.moodZone != null) {
          Surface(
            color = post.moodZone.accentColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, post.moodZone.accentColor.copy(alpha = 0.4f))
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = post.moodZone.emoji, fontSize = 12.sp)
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = post.moodZone.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = post.moodZone.accentColor
              )
            }
          }
          Spacer(modifier = Modifier.width(4.dp))
        }

        // 3-dot overflow menu
        Box {
          IconButton(
            onClick = { showOptionsMenu = true },
            modifier = Modifier
              .size(48.dp)
              .testTag("post_options_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "Post options",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

      // Audio Track Pill if present
      if (!post.audioTrackTitle.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.MusicNote,
              contentDescription = "Audio track",
              tint = VybeMagenta,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = post.audioTrackTitle,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Caption
      if (post.caption.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = post.caption,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          lineHeight = 22.sp
        )
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
                  onLikeClick()
                }
              )
            }
        ) {
          if (isVideo) {
            val anySheetOpen = showCommentDialog || showAnalyticsSheet || showDeleteConfirm || showReportDialog
            VybeVideoPlayer(
              videoUrl = mediaUrl,
              autoPlay = shouldAutoPlay,
              isLooping = true,
              initialMuted = !isAudioEnabled,
              isPausedExternal = !shouldAutoPlay || anySheetOpen,
              respectReducedMotion = true,
              fitMode = VideoFitMode.COVER_PROPORTIONAL,
              onAspectRatioDetected = { ratio ->
                detectedMediaRatio = ratio
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
            }
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
                .background(Color.Black.copy(alpha = 0.55f)),
              contentAlignment = Alignment.Center
            ) {
              VybeLikeIcon(
                isLiked = true,
                size = 54.dp,
                activeTintOverride = VybeMagenta
              )
            }
          }
        }
      }

      // Action Bar: VYBE Resonance Like, Comments, Views, Remix, Save, Share
      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Unique VYBE 4-Reaction Bar & Button (Posts: Hide total reaction counts)
          Box {
            Surface(
              color = if (post.isLiked) {
                post.userReaction?.let { reactionColor(it).copy(alpha = 0.20f) } ?: VybeViolet.copy(alpha = 0.16f)
              } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(post.id, post.userReaction, post.isLiked) {
                  detectTapGestures(
                    onTap = {
                      if (onReact != null) {
                        if (post.userReaction != null) {
                          onReact(post.userReaction)
                        } else {
                          onReact(VybeReaction.PULSE)
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
              Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (post.userReaction != null) {
                  Text(
                    text = post.userReaction.emoji,
                    fontSize = 16.sp,
                    modifier = Modifier.testTag("like_button_${post.id}")
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = post.userReaction.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = reactionColor(post.userReaction)
                  )
                } else {
                  VybeLikeIcon(
                    isLiked = false,
                    size = 18.dp,
                    inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("like_button_${post.id}")
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = "React",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                  imageVector = Icons.Default.ArrowDropDown,
                  contentDescription = "Select reaction",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                  modifier = Modifier
                    .size(16.dp)
                    .clickable { showReactionSelector = !showReactionSelector }
                )
              }
            }

            if (showReactionSelector) {
              Box(
                modifier = Modifier
                  .offset(y = (-56).dp)
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

          Spacer(modifier = Modifier.width(8.dp))

          // Comment Button
          val displayedCommentsCount = maxOf(post.commentsCount, post.comments.size)
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .heightIn(min = 40.dp)
              .clip(RoundedCornerShape(14.dp))
              .clickable { showCommentDialog = true }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Comments",
                tint = VybeViolet,
                modifier = Modifier
                  .size(17.dp)
                  .testTag("comment_button_${post.id}")
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "$displayedCommentsCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Views Pill (reflects backend views count)
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(14.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Views",
                tint = VybeCyan,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${post.viewsCount}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (onRemixClick != null) {
            IconButton(
              onClick = { onRemixClick.invoke() },
              modifier = Modifier
                .size(44.dp)
                .testTag("remix_button_${post.id}")
            ) {
              Icon(
                imageVector = Icons.Default.Transform,
                contentDescription = "Open VYBES Reels",
                tint = VybeCyan,
                modifier = Modifier.size(18.dp)
              )
            }
          }

          IconButton(
            onClick = onSaveClick,
            modifier = Modifier
              .size(44.dp)
              .testTag("save_button_${post.id}")
          ) {
            Icon(
              imageVector = if (post.isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription = "Save post",
              tint = if (post.isSaved) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }

          IconButton(
            onClick = onShareClick,
            modifier = Modifier
              .size(44.dp)
              .testTag("share_button_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = "Share post",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      // Inline comments preview
      if (post.comments.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        val firstComment = post.comments.first()
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { showCommentDialog = true }
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = firstComment.author.displayName,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = firstComment.content,
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }
  }

  // VYBE Comments Bottom Sheet
  if (showCommentDialog) {
    val displayedCount = maxOf(post.commentsCount, post.comments.size)
    ModalBottomSheet(
      onDismissRequest = { showCommentDialog = false },
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
            .heightIn(min = 120.dp, max = 360.dp)
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

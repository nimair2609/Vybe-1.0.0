package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.Post
import com.example.data.model.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCard(
  post: Post,
  onLikeClick: () -> Unit,
  onSaveClick: () -> Unit,
  onCommentClick: () -> Unit,
  onShareClick: () -> Unit,
  onUserClick: (User) -> Unit,
  onRemixClick: (() -> Unit)? = null,
  onAddComment: ((String) -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  onPostViewed: ((String) -> Unit)? = null,
  currentUserId: String? = null,
  isFollowing: Boolean = false,
  onFollowClick: (() -> Unit)? = null,
  isAutoPlayActive: Boolean = true,
  modifier: Modifier = Modifier
) {
  var showCommentDialog by remember { mutableStateOf(false) }
  var showOptionsMenu by remember { mutableStateOf(false) }
  var showAnalyticsSheet by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var commentText by remember { mutableStateOf("") }
  var isCaptionExpanded by remember { mutableStateOf(false) }
  val isAuthor = currentUserId == post.author.id || (currentUserId != null && currentUserId == "u_me" && post.author.id == "u_me")

  // Instagram-style double tap heart pop animation
  var showHeartPop by remember { mutableStateOf(false) }
  val heartScale = remember { Animatable(0.2f) }
  val heartAlpha = remember { Animatable(0f) }

  LaunchedEffect(showHeartPop) {
    if (showHeartPop) {
      heartScale.snapTo(0.2f)
      heartAlpha.snapTo(1f)
      heartScale.animateTo(
        targetValue = 1.35f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessLow
        )
      )
      heartScale.animateTo(
        targetValue = 1.0f,
        animationSpec = tween(140)
      )
      heartAlpha.animateTo(
        targetValue = 0f,
        animationSpec = tween(280)
      )
      showHeartPop = false
    }
  }

  LaunchedEffect(post.id) {
    // Only count as view if user actually stops on the post for >= 2.5 seconds (prevents fast scroll counting)
    kotlinx.coroutines.delay(2500)
    onPostViewed?.invoke(post.id)
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("post_card_${post.id}"),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      // Header: Author + Verified + Follow button + Timestamp + Mood Tag
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        VybeAvatar(user = post.author, size = 40.dp, onClick = { onUserClick(post.author) })

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f).clickable { onUserClick(post.author) }) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = post.author.displayName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            VerificationBadge(user = post.author)

            // Instagram-style Follow Button right in feed header
            if (!isAuthor && onFollowClick != null) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "•",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isFollowing) "Following" else "Follow",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else VybeViolet,
                modifier = Modifier
                  .clickable { onFollowClick() }
                  .testTag("follow_header_${post.id}")
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "@${post.author.username}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = " • ${post.timestamp}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }

        // Mood Zone Pill (Preserved VYBE Flagship Core Feature)
        if (post.moodZone != null) {
          Surface(
            color = post.moodZone.accentColor.copy(alpha = 0.18f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, post.moodZone.accentColor.copy(alpha = 0.4f))
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
            modifier = Modifier.size(32.dp).testTag("post_options_${post.id}")
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
                text = { Text("Personal Post Analytics 📊") },
                onClick = {
                  showOptionsMenu = false
                  showAnalyticsSheet = true
                },
                leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null, tint = VybeViolet) }
              )
              DropdownMenuItem(
                text = { Text("Delete Post 🗑️", color = VybeDanger) },
                onClick = {
                  showOptionsMenu = false
                  showDeleteConfirm = true
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VybeDanger) }
              )
            } else {
              DropdownMenuItem(
                text = { Text("Share Post ↗️") },
                onClick = {
                  showOptionsMenu = false
                  onShareClick()
                },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
              )
            }
          }
        }
      }

      // Audio Track Pill if any
      if (post.audioTrackTitle != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.padding(horizontal = 4.dp)
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

      // Media Image or Video Player with Instagram Double-Tap to Like Gesture
      if (post.mediaUrls.isNotEmpty()) {
        val mediaUrl = post.mediaUrls.first()
        val isVideo = post.mediaType == MediaType.VIDEO || post.isVybesReel || mediaUrl.endsWith(".mp4", ignoreCase = true)

        Spacer(modifier = Modifier.height(8.dp))
        if (isVideo) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(280.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black)
              .pointerInput(post.id, post.isLiked) {
                detectTapGestures(
                  onDoubleTap = {
                    if (!post.isLiked) onLikeClick()
                    showHeartPop = true
                  }
                )
              }
          ) {
            VideoPlayerView(
              videoUrl = mediaUrl,
              isActive = isAutoPlayActive,
              modifier = Modifier.fillMaxSize()
            )

            // Reel Badge if post is a Reel
            if (post.isVybesReel || post.mediaType == MediaType.REEL) {
              Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .align(Alignment.TopStart)
                  .padding(8.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = VybeMagenta,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "REEL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            }

            // Double tap scaling heart animation overlay
            if (heartAlpha.value > 0.01f) {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Favorite,
                  contentDescription = null,
                  tint = Color.White.copy(alpha = heartAlpha.value),
                  modifier = Modifier
                    .size(90.dp)
                    .graphicsLayer(
                      scaleX = heartScale.value,
                      scaleY = heartScale.value
                    )
                )
              }
            }
          }
        } else {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(280.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .pointerInput(post.id, post.isLiked) {
                detectTapGestures(
                  onDoubleTap = {
                    if (!post.isLiked) onLikeClick()
                    showHeartPop = true
                  }
                )
              }
          ) {
            AsyncImage(
              model = mediaUrl,
              contentDescription = "Post media",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )

            // Double tap scaling heart animation overlay
            if (heartAlpha.value > 0.01f) {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Favorite,
                  contentDescription = null,
                  tint = Color.White.copy(alpha = heartAlpha.value),
                  modifier = Modifier
                    .size(90.dp)
                    .graphicsLayer(
                      scaleX = heartScale.value,
                      scaleY = heartScale.value
                    )
                )
              }
            }
          }
        }
      }

      // Instagram-style Action Bar: Likes, Comments, Share, Views, Remix, Save
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Like Button (Heart)
          IconButton(
            onClick = {
              onLikeClick()
              if (!post.isLiked) showHeartPop = true
            },
            modifier = Modifier.size(38.dp).testTag("like_button_${post.id}")
          ) {
            Icon(
              imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
              contentDescription = "Like",
              tint = if (post.isLiked) VybeMagenta else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(24.dp)
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          // Comment Button (Speech bubble)
          IconButton(
            onClick = { showCommentDialog = true },
            modifier = Modifier.size(38.dp).testTag("comment_button_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Outlined.ChatBubbleOutline,
              contentDescription = "Comment",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(23.dp)
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          // Share / Direct Message (Paper plane)
          IconButton(
            onClick = onShareClick,
            modifier = Modifier.size(38.dp).testTag("share_button_${post.id}")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Share",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(21.dp)
            )
          }

          // VYBE Core Feature: Views Telemetry Pill
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Views",
                tint = VybeCyan,
                modifier = Modifier.size(13.dp)
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
          // VYBE Core Feature: Remix Chain
          if (onRemixClick != null) {
            IconButton(
              onClick = { onRemixClick() },
              modifier = Modifier.size(36.dp).testTag("remix_button_${post.id}")
            ) {
              Icon(
                imageVector = Icons.Default.Transform,
                contentDescription = "Remix chain",
                tint = VybeCyan,
                modifier = Modifier.size(19.dp)
              )
            }
          }

          // Bookmark Save Button
          IconButton(
            onClick = onSaveClick,
            modifier = Modifier.size(38.dp).testTag("save_button_${post.id}")
          ) {
            Icon(
              imageVector = if (post.isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription = "Save",
              tint = if (post.isSaved) VybeViolet else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(23.dp)
            )
          }
        }
      }

      // Instagram-style Likes Count Line (e.g. "42 likes")
      Text(
        text = if (post.likesCount == 1) "1 like" else "${post.likesCount} likes",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
      )

      // Instagram-style Caption with Username
      if (post.caption.isNotBlank()) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
          Text(
            buildAnnotatedString {
              withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                append(post.author.username)
                append(" ")
              }
              withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                append(post.caption)
              }
            },
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp,
            maxLines = if (isCaptionExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { isCaptionExpanded = !isCaptionExpanded }
          )
          if (!isCaptionExpanded && post.caption.length > 80) {
            Text(
              text = "more",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.clickable { isCaptionExpanded = true }
            )
          }
        }
      }

      // Instagram-style "View all X comments"
      if (post.comments.isNotEmpty()) {
        Text(
          text = if (post.comments.size == 1) "View 1 comment" else "View all ${post.comments.size} comments",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .clickable { showCommentDialog = true }
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("view_comments_${post.id}")
        )
      }

      // Instagram-style Quick Inline Reactions
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Add a comment...",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          modifier = Modifier
            .weight(1f)
            .clickable { showCommentDialog = true }
            .padding(vertical = 4.dp)
        )
        IconButton(
          onClick = {
            onLikeClick()
            if (!post.isLiked) showHeartPop = true
          },
          modifier = Modifier.size(28.dp)
        ) {
          Text("❤️", fontSize = 14.sp)
        }
        IconButton(
          onClick = { onAddComment?.invoke("🙌 Amazing vibe!") },
          modifier = Modifier.size(28.dp)
        ) {
          Text("🙌", fontSize = 14.sp)
        }
        IconButton(
          onClick = { onAddComment?.invoke("🔥 Pure fire!") },
          modifier = Modifier.size(28.dp)
        ) {
          Text("🔥", fontSize = 14.sp)
        }
      }
    }
  }

  // Instagram / Facebook Style Comment Sheet
  if (showCommentDialog) {
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
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Comments (${post.comments.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Comments List
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .heightIn(min = 120.dp, max = 360.dp)
        ) {
          if (post.comments.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No comments yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Start the conversation by sharing your vibe below.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          } else {
            androidx.compose.foundation.lazy.LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              items(post.comments) { comment ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.Top
                ) {
                  VybeAvatar(user = comment.author, size = 36.dp)
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(comment.author.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(comment.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = comment.content,
                      fontSize = 13.sp,
                      color = MaterialTheme.colorScheme.onSurface,
                      lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                  IconButton(
                    onClick = { /* Toggle comment like */ },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                      contentDescription = "Like comment",
                      tint = if (comment.isLiked) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }
        }

        // Quick Emoji Reaction Row (Insta/FB feature)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          listOf("❤️", "🙌", "🔥", "👏", "😂", "😍", "✨").forEach { emoji ->
            Text(
              text = emoji,
              fontSize = 20.sp,
              modifier = Modifier
                .clickable {
                  commentText += emoji
                }
                .padding(4.dp)
            )
          }
        }

        // Bottom Input Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
              if (commentText.isNotBlank()) {
                if (onAddComment != null) {
                  onAddComment(commentText)
                } else {
                  onCommentClick()
                }
                commentText = ""
              }
            },
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(VybeViolet)
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Comment", tint = Color.White, modifier = Modifier.size(18.dp))
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
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
              text = "Personal Post Analytics",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Real-time performance metrics for your post",
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

        // Metrics Grid (Views, Likes, Comments, Saves, Shares, Engagement)
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
          AnalyticsMetricCard(
            title = "Reactions",
            value = "${post.likesCount}",
            icon = Icons.Default.Favorite,
            iconTint = VybeMagenta,
            modifier = Modifier.weight(1f)
          )
          AnalyticsMetricCard(
            title = "Comments",
            value = "${post.commentsCount}",
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
          val totalInteractions = post.likesCount + post.commentsCount + post.savesCount
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
          modifier = Modifier.fillMaxWidth(),
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
      text = { Text("This will permanently remove this post from your feed and Supabase cloud storage.") },
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
}

@Composable
fun VideoPlayerView(
  videoUrl: String,
  modifier: Modifier = Modifier,
  isActive: Boolean = true
) {
  var isPlaying by remember { mutableStateOf(true) }
  var isMuted by remember { mutableStateOf(true) }
  var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
  var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
  var isPrepared by remember { mutableStateOf(false) }

  // Smart Autoplay / Pause: React to isActive changes when scrolled in or out of viewport
  LaunchedEffect(isActive) {
    val vv = videoViewRef
    if (vv != null) {
      if (isActive) {
        if (!vv.isPlaying && isPlaying && isPrepared) {
          try {
            vv.start()
          } catch (_: Exception) {}
        }
      } else {
        if (vv.isPlaying) {
          try {
            vv.pause()
          } catch (_: Exception) {}
        }
      }
    }
  }

  // Safe resource disposal when scrolled away or post unmounted
  DisposableEffect(videoUrl) {
    onDispose {
      try {
        videoViewRef?.stopPlayback()
        mediaPlayerRef?.release()
        mediaPlayerRef = null
        videoViewRef = null
      } catch (_: Exception) {}
    }
  }

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      factory = { ctx ->
        android.widget.VideoView(ctx).apply {
          setVideoURI(android.net.Uri.parse(videoUrl))
          setOnPreparedListener { mp ->
            mediaPlayerRef = mp
            mp.isLooping = true
            val vol = if (isMuted) 0.0f else 1.0f
            mp.setVolume(vol, vol)
            isPrepared = true
            if (isActive && isPlaying) {
              start()
            }
          }
          setOnCompletionListener {
            isPlaying = false
          }
          videoViewRef = this
        }
      },
      update = { view ->
        videoViewRef = view
      },
      modifier = Modifier.fillMaxSize()
    )

    // Tap on media to play/pause
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clickable {
          if (isPlaying) {
            videoViewRef?.pause()
            isPlaying = false
          } else {
            videoViewRef?.start()
            isPlaying = true
          }
        }
    )

    // Play button indicator if paused
    if (!isPlaying) {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = "Play Video",
          tint = Color.White,
          modifier = Modifier.size(32.dp)
        )
      }
    }

    // Mute/Unmute control pill in bottom right
    Surface(
      color = Color.Black.copy(alpha = 0.6f),
      shape = CircleShape,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(10.dp)
        .clickable {
          isMuted = !isMuted
          val vol = if (isMuted) 0.0f else 1.0f
          mediaPlayerRef?.setVolume(vol, vol)
        }
    ) {
      Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
        Icon(
          imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
          contentDescription = if (isMuted) "Unmute" else "Mute",
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }
    }
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
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
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


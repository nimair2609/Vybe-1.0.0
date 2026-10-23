package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.items
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
  modifier: Modifier = Modifier
) {
  var showCommentDialog by remember { mutableStateOf(false) }
  var showOptionsMenu by remember { mutableStateOf(false) }
  var showAnalyticsSheet by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var commentText by remember { mutableStateOf("") }
  val isAuthor = currentUserId == post.author.id || (currentUserId != null && currentUserId == "u_me" && post.author.id == "u_me")

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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
      // Header: Author + Verified + Timestamp + Mood Tag
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        VybeAvatar(user = post.author, size = 42.dp, onClick = { onUserClick(post.author) })

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

        // Mood Zone Pill
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
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = post.caption,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 22.sp
      )

      // Media Image or Video Player
      if (post.mediaUrls.isNotEmpty()) {
        val mediaUrl = post.mediaUrls.first()
        val isVideo = post.mediaType == MediaType.VIDEO || post.isVybesReel || mediaUrl.endsWith(".mp4", ignoreCase = true)

        Spacer(modifier = Modifier.height(12.dp))
        if (isVideo) {
          VideoPlayerView(
            videoUrl = mediaUrl,
            modifier = Modifier
              .fillMaxWidth()
              .height(260.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.Black)
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
          ) {
            AsyncImage(
              model = mediaUrl,
              contentDescription = "Post media",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
        }
      }

      // Action Bar: Likes, Comments, Views, Saves, Remix, Share
      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Like Button
          Surface(
            color = if (post.isLiked) VybeMagenta.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.clickable { onLikeClick() }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Like",
                tint = if (post.isLiked) VybeMagenta else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp).testTag("like_button_${post.id}")
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "${post.likesCount}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Comment Button
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.clickable { showCommentDialog = true }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Comment",
                tint = VybeViolet,
                modifier = Modifier.size(17.dp).testTag("comment_button_${post.id}")
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                text = "${post.commentsCount}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Views Pill (displays views count)
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(14.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
          // Remix Action
          IconButton(
            onClick = { onRemixClick?.invoke() },
            modifier = Modifier.size(34.dp).testTag("remix_button_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Transform,
              contentDescription = "Remix chain",
              tint = VybeCyan,
              modifier = Modifier.size(18.dp)
            )
          }

          // Bookmark Save Button
          IconButton(
            onClick = onSaveClick,
            modifier = Modifier.size(34.dp).testTag("save_button_${post.id}")
          ) {
            Icon(
              imageVector = if (post.isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription = "Save",
              tint = if (post.isSaved) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }

          // Share Button
          IconButton(
            onClick = onShareClick,
            modifier = Modifier.size(34.dp).testTag("share_button_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = "Share",
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
          modifier = Modifier.fillMaxWidth().clickable { showCommentDialog = true }
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
              modifier = Modifier.weight(1f)
            )
          }
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
  modifier: Modifier = Modifier
) {
  var isPlaying by remember { mutableStateOf(true) }
  var isMuted by remember { mutableStateOf(true) }
  var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
  var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

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
            mp.setVolume(0.0f, 0.0f) // polite autoplay muted
            start()
            isPlaying = true
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


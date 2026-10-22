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
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.items
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
  modifier: Modifier = Modifier
) {
  var showCommentDialog by remember { mutableStateOf(false) }
  var commentText by remember { mutableStateOf("") }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .testTag("post_card_${post.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(20.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
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

      // Media Image
      if (post.mediaUrls.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
          AsyncImage(
            model = post.mediaUrls.first(),
            contentDescription = "Post media",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }
      }

      // Action Bar: Likes, Comments, Saves, Shares, Remix
      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Comment Button (Primary conversation interaction per Section 7)
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
                modifier = Modifier.size(18.dp).testTag("comment_button_${post.id}")
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${post.commentsCount} Comments",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          // Remix Action
          IconButton(
            onClick = { onRemixClick?.invoke() },
            modifier = Modifier.size(36.dp).testTag("remix_button_${post.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Transform,
              contentDescription = "Remix chain",
              tint = VybeCyan
            )
          }

          // Bookmark Save Button
          IconButton(
            onClick = onSaveClick,
            modifier = Modifier.size(36.dp).testTag("save_button_${post.id}")
          ) {
            Icon(
              imageVector = if (post.isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription = "Save",
              tint = if (post.isSaved) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant
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
}

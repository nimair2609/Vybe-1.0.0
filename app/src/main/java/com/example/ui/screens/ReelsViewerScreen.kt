package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.example.data.model.*
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
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsViewerScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val posts by viewModel.posts.collectAsState()
  val conversations by viewModel.conversations.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  var isPlaying by remember { mutableStateOf(true) }
  var isMuted by remember { mutableStateOf(false) }
  var showShareInboxSheet by remember { mutableStateOf(false) }
  var showCommentsSheet by remember { mutableStateOf(false) }
  var reelCommentText by remember { mutableStateOf("") }
  var shareSearchQuery by remember { mutableStateOf("") }
  var sharedFeedback by remember { mutableStateOf<String?>(null) }

  // Filter strictly only video reels (Never show static text or feed posts)
  val videoPosts = remember(posts) {
    posts.filter { it.mediaType == com.example.data.model.MediaType.REEL || it.mediaType == com.example.data.model.MediaType.VIDEO || it.isVybesReel }
  }

  if (videoPosts.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("No video reels available yet", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Create the first VYBES video reel!", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
      }
    }
    return
  }

  val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { videoPosts.size })

  Box(modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.pager.VerticalPager(
      state = pagerState,
      beyondViewportPageCount = 0,
      modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
      val reelPost = videoPosts[pageIndex]
      val isPageActive = (pageIndex == pagerState.currentPage)

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
      ) {
        // View counting policy: Genuine playback of active page for at least 3 seconds
        LaunchedEffect(pageIndex, reelPost.id) {
          if (isPageActive) {
            kotlinx.coroutines.delay(3000)
            if (pageIndex == pagerState.currentPage) {
              viewModel.recordPostView(reelPost.id)
            }
          }
        }

        // Media content: Real video playback with SmartReelVideoPlayer (strictly 1 active video in memory)
        if (reelPost.mediaUrls.isNotEmpty()) {
          val mediaUrl = reelPost.mediaUrls.first()
          val isVideo = reelPost.mediaType == com.example.data.model.MediaType.REEL ||
                        reelPost.mediaType == com.example.data.model.MediaType.VIDEO ||
                        reelPost.isVybesReel ||
                        mediaUrl.endsWith(".mp4", ignoreCase = true)

          if (isVideo) {
            com.example.ui.components.SmartReelVideoPlayer(
              videoUrl = mediaUrl,
              isActive = isPageActive,
              isMuted = isMuted,
              onMuteToggle = { isMuted = !isMuted },
              onDoubleTap = {
                if (!reelPost.isLiked) {
                  viewModel.toggleLike(reelPost.id)
                }
              },
              modifier = Modifier.fillMaxSize()
            )
          } else {
            AsyncImage(
              model = mediaUrl,
              contentDescription = "Reel content",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
        }

        // Top subtle gradient & Controls
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = { viewModel.navigateBack() },
              modifier = Modifier.testTag("reels_back_button")
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

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { viewModel.navigateTo(com.example.ui.Screen.CREATE_POST) },
                modifier = Modifier.testTag("reels_camera_create_button")
              ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Create Reel", tint = Color.White)
              }
              IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier.testTag("reels_mute_button")
              ) {
                Icon(
                  imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                  contentDescription = "Mute audio",
                  tint = Color.White
                )
              }
            }
          }
        }

        // Right Side Action Bar (Likes, Comments, Remix, Share to Inbox)
        Column(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(bottom = 60.dp, end = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Like
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { viewModel.toggleLike(reelPost.id) },
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .testTag("reel_like_button")
            ) {
              Icon(
                imageVector = if (reelPost.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Like",
                tint = if (reelPost.isLiked) VybeMagenta else Color.White
              )
            }
            Text(
              text = "${reelPost.likesCount}",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }

          // Comments
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { showCommentsSheet = true },
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .testTag("reel_comment_button")
            ) {
              Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", tint = Color.White)
            }
            Text(
              text = "${reelPost.commentsCount}",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }

          // Remix Chain Action
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { /* Remix chain */ },
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .testTag("reel_remix_button")
            ) {
              Icon(Icons.Default.Transform, contentDescription = "Remix Chain", tint = VybeCyan)
            }
            Text(
              text = "Remix",
              color = VybeCyan,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }

          // Share to Inbox (Functional)
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { showShareInboxSheet = true },
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .testTag("reel_share_button")
            ) {
              Icon(Icons.Outlined.Send, contentDescription = "Share to Inbox", tint = Color.White)
            }
            Text(
              text = "Share",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }
        }

        // Bottom info (Author, Caption, Sound, Mood)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomStart)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 80.dp, bottom = 20.dp, top = 40.dp)
        ) {
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { viewModel.openUserProfile(reelPost.author) }
            ) {
              VybeAvatar(user = reelPost.author, size = 38.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = reelPost.author.displayName,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp
              )
              Spacer(modifier = Modifier.width(4.dp))
              VerificationBadge(user = reelPost.author)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = reelPost.caption,
              color = Color.White,
              fontSize = 13.sp,
              maxLines = 3,
              lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sound Track Pill
            Surface(
              color = Color.White.copy(alpha = 0.2f),
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = reelPost.audioTrackTitle ?: "Original Audio - ${reelPost.author.displayName}",
                  color = Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }
      }
    }

    // Share to Inbox Sheet
    if (showShareInboxSheet) {
      val currentReel = videoPosts[pagerState.currentPage]
      val filteredUsers = remember(shareSearchQuery, allUsers) {
        if (shareSearchQuery.isBlank()) allUsers else {
          val q = shareSearchQuery.trim().lowercase().replace("@", "")
          allUsers.filter { it.displayName.lowercase().contains(q) || it.username.lowercase().contains(q) }
        }
      }

      ModalBottomSheet(
        onDismissRequest = {
          showShareInboxSheet = false
          shareSearchQuery = ""
        },
        containerColor = MaterialTheme.colorScheme.surface
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
        ) {
          Text(
            text = "Share Reel to Inbox",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Send this reel directly to any friend or group.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = shareSearchQuery,
            onValueChange = { shareSearchQuery = it },
            placeholder = { Text("Search friends by name or @userid...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(12.dp))

          androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // First list active conversations
            if (conversations.isNotEmpty() && shareSearchQuery.isBlank()) {
              item {
                Text("Recent Conversations", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              items(conversations) { conv ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      viewModel.sendExtendedDirectMessage(
                        convId = conv.id,
                        text = "🎬 Shared Reel from @${currentReel.author.username}: ${currentReel.caption}",
                        mediaUrl = currentReel.mediaUrls.firstOrNull(),
                        mediaType = "VIDEO"
                      )
                      sharedFeedback = "Reel sent to ${conv.groupName ?: conv.peerUser.displayName}! 🚀"
                      showShareInboxSheet = false
                    }
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
                          text = "🎬 Shared Reel from @${currentReel.author.username}: ${currentReel.caption}",
                          mediaUrl = currentReel.mediaUrls.firstOrNull(),
                          mediaType = "VIDEO"
                        )
                        sharedFeedback = "Reel sent to ${conv.groupName ?: conv.peerUser.displayName}! 🚀"
                        showShareInboxSheet = false
                      },
                      shape = RoundedCornerShape(16.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                      Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }
            }

            // Next list community members/friends
            item {
              Text("Community Contacts", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(filteredUsers) { targetUser ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    val conv = viewModel.getOrCreateConversation(targetUser)
                    viewModel.sendExtendedDirectMessage(
                      convId = conv.id,
                      text = "🎬 Shared Reel from @${currentReel.author.username}: ${currentReel.caption}",
                      mediaUrl = currentReel.mediaUrls.firstOrNull(),
                      mediaType = "VIDEO"
                    )
                    sharedFeedback = "Reel sent to ${targetUser.displayName}! 🚀"
                    showShareInboxSheet = false
                  }
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  VybeAvatar(user = targetUser, size = 36.dp)
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(targetUser.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("@${targetUser.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Button(
                    onClick = {
                      val conv = viewModel.getOrCreateConversation(targetUser)
                      viewModel.sendExtendedDirectMessage(
                        convId = conv.id,
                        text = "🎬 Shared Reel from @${currentReel.author.username}: ${currentReel.caption}",
                        mediaUrl = currentReel.mediaUrls.firstOrNull(),
                        mediaType = "VIDEO"
                      )
                      sharedFeedback = "Reel sent to ${targetUser.displayName}! 🚀"
                      showShareInboxSheet = false
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                  ) {
                    Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }

    // Reel Comments Sheet
    if (showCommentsSheet) {
      val currentReel = videoPosts[pagerState.currentPage]
      ModalBottomSheet(
        onDismissRequest = { showCommentsSheet = false },
        containerColor = MaterialTheme.colorScheme.surface
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
        ) {
          Text(
            text = "Comments (${currentReel.comments.size})",
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
              Text("No comments yet. Be the first to vibe on this reel!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          } else {
            androidx.compose.foundation.lazy.LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(currentReel.comments) { c ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.Top
                ) {
                  VybeAvatar(user = c.author, size = 32.dp)
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(c.author.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(c.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(c.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
              placeholder = { Text("Comment on this reel...") },
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
                .size(44.dp)
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

    // Shared feedback snackbar
    if (sharedFeedback != null) {
      Snackbar(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp)
      ) {
        Text(sharedFeedback ?: "")
      }
      LaunchedEffect(sharedFeedback) {
        kotlinx.coroutines.delay(2500)
        sharedFeedback = null
      }
    }
  }
}

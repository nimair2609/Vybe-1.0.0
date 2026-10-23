package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Story
import com.example.data.model.StoryAudience
import com.example.data.model.StoryMediaType
import com.example.ui.VybeViewModel
import com.example.ui.components.StoryGradients
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * INSTAGRAM-STYLE FULL-SCREEN STORY VIEWER
 * Authentic Instagram story experience:
 * - Segmented progress bars at top with auto-advancement
 * - Pause on press-and-hold
 * - Tap left 30% for previous, tap right 70% for next story
 * - Author profile header with timestamp, Close Friends badge, and options
 * - Dynamic story content: High-res photos, typography gradients, music wave sticker, video
 * - Bottom Instagram interaction: "Send message...", direct quick-reply, heart reaction, emoji reactions
 */
@Composable
fun StoryViewerScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val stories by viewModel.stories.collectAsState()
  val currentIndex by viewModel.currentStoryIndex.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  if (stories.isEmpty()) {
    LaunchedEffect(Unit) {
      viewModel.navigateBack()
    }
    return
  }

  val safeIndex = currentIndex.coerceIn(0, stories.size - 1)
  val currentStory = stories[safeIndex]
  val isMe = currentStory.author.id == currentUser?.id

  // Interaction states
  var isPaused by remember { mutableStateOf(false) }
  var isMuted by remember { mutableStateOf(false) }
  var replyMessage by remember { mutableStateOf("") }
  var reactionToast by remember { mutableStateOf<String?>(null) }
  var showMenuDropdown by remember { mutableStateOf(false) }

  // Animated progress for current story segment (5-second duration)
  val progress = remember { Animatable(0f) }

  LaunchedEffect(safeIndex, isPaused) {
    progress.snapTo(0f)
    if (!isPaused) {
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
      )
      // Advance to next story when timer completes
      val hasNext = viewModel.nextStory()
      if (!hasNext) {
        viewModel.navigateBack()
      }
    }
  }

  // Clear reaction toast after delay
  LaunchedEffect(reactionToast) {
    if (reactionToast != null) {
      delay(1500)
      reactionToast = null
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("story_viewer_screen")
  ) {
    // 1. Story Visual Content Canvas with Gesture Handler
    Box(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(safeIndex) {
          detectTapGestures(
            onPress = {
              isPaused = true
              val released = tryAwaitRelease()
              isPaused = false
            },
            onTap = { offset ->
              val screenWidth = size.width
              if (offset.x < screenWidth * 0.33f) {
                // Tap left: previous story
                viewModel.prevStory()
              } else {
                // Tap right: next story
                val hasNext = viewModel.nextStory()
                if (!hasNext) {
                  viewModel.navigateBack()
                }
              }
            }
          )
        },
      contentAlignment = Alignment.Center
    ) {
      StoryCanvas(currentStory)
    }

    // 2. Reaction Floating Toast
    AnimatedVisibility(
      visible = reactionToast != null,
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut(),
      modifier = Modifier.align(Alignment.Center)
    ) {
      Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(24.dp)
      ) {
        Text(
          text = reactionToast ?: "",
          fontSize = 44.sp,
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
      }
    }

    // 3. Top Overlay: Segmented Bars & Header Profile
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
      // Segmented Progress Bars (Instagram style)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        stories.forEachIndexed { index, _ ->
          val segmentProgress = when {
            index < safeIndex -> 1f
            index == safeIndex -> progress.value
            else -> 0f
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .height(2.5.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(Color.White.copy(alpha = 0.35f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(segmentProgress)
                .background(Color.White)
            )
          }
        }
      }

      // Author Header Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Author avatar
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(
              width = 1.dp,
              color = if (currentStory.audience == StoryAudience.CLOSE_FRIENDS) Color(0xFF2ECC71) else Color.White.copy(alpha = 0.6f),
              shape = CircleShape
            ),
          contentAlignment = Alignment.Center
        ) {
          if (!currentStory.author.avatarUrl.isNullOrBlank()) {
            AsyncImage(
              model = currentStory.author.avatarUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(VybeViolet),
              contentAlignment = Alignment.Center
            ) {
              Text(
                currentStory.author.displayName.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Name & Timestamp
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = currentStory.author.username,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (currentStory.author.isVerified) {
              Spacer(modifier = Modifier.width(4.dp))
              VerificationBadge(user = currentStory.author)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = currentStory.timestamp,
              color = Color.White.copy(alpha = 0.7f),
              fontSize = 11.sp
            )
          }

          // VYBE Matches indicator (Renamed per Section 4)
          if (currentStory.audience == StoryAudience.VYBE_MATCHES || currentStory.audience == StoryAudience.CLOSE_FRIENDS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(10.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text("VYBE Matches", color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
          }
        }

        // Sound Toggle
        IconButton(onClick = { isMuted = !isMuted }) {
          Icon(
            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = "Sound toggle",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }

        // More options dropdown
        Box {
          IconButton(onClick = { showMenuDropdown = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
          }

          DropdownMenu(
            expanded = showMenuDropdown,
            onDismissRequest = { showMenuDropdown = false }
          ) {
            if (isMe) {
              DropdownMenuItem(
                text = { Text("Delete Story", color = VybeDanger) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VybeDanger) },
                onClick = {
                  viewModel.deleteStory(currentStory.id)
                  showMenuDropdown = false
                  val hasNext = viewModel.nextStory()
                  if (!hasNext) viewModel.navigateBack()
                }
              )
            } else {
              DropdownMenuItem(
                text = { Text("Report Inappropriate") },
                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                onClick = {
                  reactionToast = "Reported"
                  showMenuDropdown = false
                }
              )
            }
          }
        }

        // Close 'X' button
        IconButton(
          onClick = { viewModel.navigateBack() },
          modifier = Modifier.testTag("story_viewer_close_button")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close Story", tint = Color.White)
        }
      }
    }

    // 4. Bottom Overlay: Instagram Message Reply Field & Reactions
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      // Quick Emoji Reactions Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        listOf("❤️", "🔥", "😂", "😮", "😢", "👏").forEach { emoji ->
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.5f))
              .clickable {
                reactionToast = emoji
                viewModel.toggleStoryLike(currentStory.id)
              },
            contentAlignment = Alignment.Center
          ) {
            Text(emoji, fontSize = 20.sp)
          }
        }
      }

      // Input Row (Reply message & Heart button)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = replyMessage,
          onValueChange = { replyMessage = it },
          placeholder = {
            Text(
              "Send message to @${currentStory.author.username}...",
              color = Color.White.copy(alpha = 0.6f),
              fontSize = 12.sp
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White.copy(alpha = 0.8f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
            focusedContainerColor = Color.Black.copy(alpha = 0.5f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.4f)
          ),
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("story_reply_input"),
          singleLine = true,
          trailingIcon = {
            if (replyMessage.isNotBlank()) {
              IconButton(
                onClick = {
                  reactionToast = "Message sent ✈️"
                  replyMessage = ""
                }
              ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
              }
            }
          }
        )

        // Heart Like Button
        IconButton(
          onClick = {
            viewModel.toggleStoryLike(currentStory.id)
            reactionToast = if (!currentStory.isLikedByMe) "❤️" else null
          },
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .testTag("story_like_button")
        ) {
          Icon(
            imageVector = if (currentStory.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Like",
            tint = if (currentStory.isLikedByMe) Color(0xFFFF2D55) else Color.White,
            modifier = Modifier.size(24.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun StoryCanvas(story: Story) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    when (story.mediaType) {
      StoryMediaType.PHOTO -> {
        if (story.mediaUrl.isNotBlank()) {
          AsyncImage(
            model = story.mediaUrl,
            contentDescription = "Story photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Brush.verticalGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))))
          )
        }
      }

      StoryMediaType.TEXT -> {
        val gradient = StoryGradients.firstOrNull { it.first == story.textBackgroundColor }?.second
          ?: StoryGradients.first().second

        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(gradient))
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = story.caption,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
          )
        }
      }

      StoryMediaType.MUSIC -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF311042))
              )
            )
        ) {
          if (story.mediaUrl.isNotBlank()) {
            AsyncImage(
              model = story.mediaUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxSize()
                .alpha(0.35f)
            )
          }

          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(VybeMagenta, VybeBrandCyan))),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
              color = Color.Black.copy(alpha = 0.6f),
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = story.musicTrackTitle ?: "Trending Indian Indie Vibe",
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
              }
            }
          }
        }
      }

      StoryMediaType.VIDEO -> {
        if (story.mediaUrl.isNotBlank()) {
          AsyncImage(
            model = story.mediaUrl,
            contentDescription = "Story video still",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFF0D1117))
          )
        }

        Surface(
          color = Color.Black.copy(alpha = 0.5f),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.align(Alignment.Center)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Video Playing", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Story Caption & Location overlay pill (Instagram style)
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 120.dp)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      if (!story.location.isNullOrBlank()) {
        Surface(
          color = Color.Black.copy(alpha = 0.6f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.padding(bottom = 8.dp)
        ) {
          Text(
            text = story.location ?: "",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }

      if (story.caption.isNotBlank() && story.mediaType != StoryMediaType.TEXT) {
        Surface(
          color = Color.Black.copy(alpha = 0.65f),
          shape = RoundedCornerShape(14.dp)
        ) {
          Text(
            text = story.caption,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
          )
        }
      }
    }
  }
}

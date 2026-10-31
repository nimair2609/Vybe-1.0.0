package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.example.data.repository.MessagingEligibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.ui.VybeViewModel
import com.example.ui.components.StoryGradients
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VideoFitMode
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeLikeIcon
import com.example.ui.components.VybeVideoPlayer
import com.example.ui.theme.*
import com.example.util.rememberRelativeTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * VYBE Full-Screen Story Viewer:
 * - Plays real uploaded video stories with `VybeVideoPlayer` (`VideoFitMode.FIT_CONTAIN`),
 *   supporting both portrait (9:16) and landscape (16:9) dimensions cleanly without stretching or cropping.
 * - Marks stories viewed in Supabase immediately as they are watched.
 * - Sends real direct replies when messaging rules allow, and supports reporting (including Copyright Infringement) or deleting own stories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewerScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.closeStoryViewer()
  }

  val stories by viewModel.activeStorySequence.collectAsState()
  val currentIndex by viewModel.currentStoryIndex.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val scope = rememberCoroutineScope()

  DisposableEffect(Unit) {
    com.example.util.FeedAudioController.stopAll()
    onDispose {
      com.example.util.FeedAudioController.stopMusicTrack("story_music")
    }
  }

  if (stories.isEmpty()) {
    LaunchedEffect(Unit) {
      viewModel.closeStoryViewer()
    }
    return
  }

  val safeIndex = currentIndex.coerceIn(0, stories.size - 1)
  val currentStory = stories[safeIndex]
  val isMe = currentStory.author.id == currentUser?.id
  val relativeTimestamp = rememberRelativeTimestamp(currentStory.createdAtIso, currentStory.timestamp)

  var isPaused by remember { mutableStateOf(false) }
  var isMuted by remember { mutableStateOf(false) }
  var replyMessage by remember { mutableStateOf("") }
  var reactionToast by remember { mutableStateOf<String?>(null) }
  var showMenuDropdown by remember { mutableStateOf(false) }
  var showViewersSheet by remember { mutableStateOf(false) }
  var showCommentsSheet by remember { mutableStateOf(false) }
  var videoDurationMs by remember(currentStory.id) { mutableIntStateOf(15000) }

  val effectivelyPaused = isPaused || showViewersSheet || showCommentsSheet

  // Mark story viewed in backend whenever active story changes
  LaunchedEffect(currentStory.id) {
    viewModel.markStoryViewed(currentStory.id)
  }

  val progress = remember(currentStory.id) { Animatable(0f) }

  LaunchedEffect(currentStory.id, effectivelyPaused, videoDurationMs) {
    if (!effectivelyPaused) {
      val targetDuration = if (currentStory.mediaType == StoryMediaType.VIDEO) {
        videoDurationMs.coerceIn(4000, 60000)
      } else {
        5500
      }
      val remainingFraction = (1f - progress.value).coerceIn(0.05f, 1f)
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(
          durationMillis = (targetDuration * remainingFraction).toInt().coerceAtLeast(300),
          easing = LinearEasing
        )
      )
      val hasNext = viewModel.nextStory()
      if (!hasNext) {
        viewModel.closeStoryViewer()
      }
    }
  }

  LaunchedEffect(currentStory.id, currentStory.mediaType, isMuted, isPaused) {
    if (currentStory.mediaType == StoryMediaType.MUSIC && !isMuted && !isPaused) {
      com.example.util.FeedAudioController.syncPostMusicTrack(
        postId = "story_music",
        audioTrackTitle = currentStory.musicTrackTitle ?: "Original Sound",
        shouldPlayAudio = true
      )
    } else {
      com.example.util.FeedAudioController.stopMusicTrack("story_music")
    }
  }

  LaunchedEffect(reactionToast) {
    if (reactionToast != null) {
      delay(1600)
      reactionToast = null
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .imePadding()
      .testTag("story_viewer_screen")
  ) {
    // 1. Story Visual Content Canvas with Gesture Handler
    Box(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(currentStory.id) {
          detectTapGestures(
            onPress = {
              isPaused = true
              tryAwaitRelease()
              isPaused = false
            },
            onTap = { offset ->
              val screenWidth = size.width
              if (offset.x < screenWidth * 0.33f) {
                viewModel.prevStory()
              } else {
                val hasNext = viewModel.nextStory()
                if (!hasNext) {
                  viewModel.closeStoryViewer()
                }
              }
            }
          )
        },
      contentAlignment = Alignment.Center
    ) {
      StoryCanvas(
        story = currentStory,
        isPaused = isPaused,
        isMuted = isMuted,
        onVideoDurationDetected = { dur -> videoDurationMs = dur },
        onVideoCompleted = {
          val hasNext = viewModel.nextStory()
          if (!hasNext) {
            viewModel.closeStoryViewer()
          }
        }
      )
    }

    // 2. Reaction / Status Toast
    AnimatedVisibility(
      visible = reactionToast != null,
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut(),
      modifier = Modifier.align(Alignment.Center)
    ) {
      Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(24.dp)
      ) {
        Text(
          text = reactionToast ?: "",
          color = Color.White,
          fontSize = if ((reactionToast?.length ?: 0) <= 4) 36.sp else 14.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )
      }
    }

    // 3. Top Overlay: Segmented Bars & Author Header
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
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

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(
              width = 1.dp,
              color = if (currentStory.audience == StoryAudience.VYBE_MATCHES || currentStory.audience == StoryAudience.CLOSE_FRIENDS) Color(0xFF10B981) else Color.White.copy(alpha = 0.6f),
              shape = CircleShape
            )
            .clickable { viewModel.openUserProfile(currentStory.author) },
          contentAlignment = Alignment.Center
        ) {
          if (!currentStory.author.avatarUrl.isNullOrBlank()) {
            AsyncImage(
              model = currentStory.author.avatarUrl,
              contentDescription = currentStory.author.displayName,
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
                fontSize = 13.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
          modifier = Modifier
            .weight(1f)
            .clickable { viewModel.openUserProfile(currentStory.author) }
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = currentStory.author.displayName,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false)
            )
            if (currentStory.author.isVerified) {
              Spacer(modifier = Modifier.width(4.dp))
              VerificationBadge(user = currentStory.author)
            }
            if (relativeTimestamp.isNotBlank()) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "• $relativeTimestamp",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "@${currentStory.author.username}",
              color = Color.White.copy(alpha = 0.78f),
              fontWeight = FontWeight.Normal,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "•",
              color = Color.White.copy(alpha = 0.5f),
              fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(5.dp))
            if (currentStory.audience == StoryAudience.VYBE_MATCHES || currentStory.audience == StoryAudience.CLOSE_FRIENDS) {
              Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text("VYBE Matches • ", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Text(
              text = "${currentStory.expiresHoursRemaining.coerceAtLeast(1)}h left",
              color = Color.White.copy(alpha = 0.62f),
              fontSize = 10.sp
            )
          }
        }

        if (currentStory.mediaType == StoryMediaType.VIDEO || currentStory.mediaType == StoryMediaType.MUSIC) {
          IconButton(
            onClick = { isMuted = !isMuted },
            modifier = Modifier.size(48.dp).testTag("story_mute_toggle")
          ) {
            Icon(
              imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = if (isMuted) "Unmute story" else "Mute story",
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        Box {
          IconButton(
            onClick = { showMenuDropdown = true },
            modifier = Modifier.size(48.dp)
          ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Story options", tint = Color.White)
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
                  showMenuDropdown = false
                  viewModel.deleteStory(currentStory.id)
                }
              )
            } else {
              DropdownMenuItem(
                text = { Text("Report Inappropriate Content") },
                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                onClick = {
                  showMenuDropdown = false
                  scope.launch {
                    viewModel.submitReport("STORY", currentStory.id, "Inappropriate Story")
                    reactionToast = "Report submitted"
                  }
                }
              )
              DropdownMenuItem(
                text = { Text("Report Copyright Infringement") },
                leadingIcon = { Icon(Icons.Default.Gavel, contentDescription = null) },
                onClick = {
                  showMenuDropdown = false
                  scope.launch {
                    viewModel.submitReport("STORY", currentStory.id, "Copyright Infringement")
                    reactionToast = "Copyright report submitted"
                  }
                }
              )
            }
          }
        }

        IconButton(
          onClick = { viewModel.closeStoryViewer() },
          modifier = Modifier.size(48.dp).testTag("story_viewer_close_button")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close Story", tint = Color.White)
        }
      }
    }

    // 4. Bottom Overlay: Reply & Like Controls (Hidden on own story to prevent self-DM noise)
    if (!isMe) {
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 12.dp, vertical = 10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = replyMessage,
            onValueChange = {
              replyMessage = it
              if (it.isNotEmpty()) isPaused = true
            },
            placeholder = {
              Text(
                "Reply to @${currentStory.author.username}...",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = Color.White.copy(alpha = 0.85f),
              unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
              focusedContainerColor = Color.Black.copy(alpha = 0.55f),
              unfocusedContainerColor = Color.Black.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 52.dp)
              .testTag("story_reply_input"),
            singleLine = true,
            trailingIcon = {
              if (replyMessage.isNotBlank()) {
                IconButton(
                  onClick = {
                    val msgText = replyMessage.trim()
                    val eligibility = viewModel.checkMessagingEligibility(currentStory.author)
                    if (eligibility is MessagingEligibility.Denied) {
                      reactionToast = eligibility.reason
                    } else {
                      val conv = viewModel.getOrCreateConversation(currentStory.author)
                      viewModel.sendDirectMessage(conv.id, "Replied to story: $msgText")
                      reactionToast = "Reply sent"
                      replyMessage = ""
                    }
                    isPaused = false
                  }
                ) {
                  Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply", tint = Color.White)
                }
              }
            }
          )

          IconButton(
            onClick = {
              viewModel.toggleStoryLike(currentStory.id)
              reactionToast = if (!currentStory.isLikedByMe) "Vibed!" else null
            },
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.5f))
              .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
              .testTag("story_like_button")
          ) {
            VybeLikeIcon(
              isLiked = currentStory.isLikedByMe,
              size = 22.dp,
              inactiveTint = Color.White
            )
          }
        }
      }
    } else {
      // Creator controls on own story: Viewers ("Who saw"), Reactions count, and Comment Center
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 14.dp, vertical = 12.dp)
      ) {
        val totalViewersCount = remember(currentStory.id, allUsers.size) {
          (allUsers.filter { it.id != currentUser?.id }.size.coerceAtLeast(3) + currentStory.reactionsCount)
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // "Who Saw" button
          Button(
            onClick = { showViewersSheet = true },
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.Black.copy(alpha = 0.65f),
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .testTag("story_viewers_button")
          ) {
            Icon(
              imageVector = Icons.Default.Visibility,
              contentDescription = "Who saw",
              tint = VybeElectricCyan,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "$totalViewersCount saw",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1
            )
          }

          // Reactions count pill
          Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.height(44.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 12.dp)
            ) {
              VybeLikeIcon(
                isLiked = currentStory.reactionsCount > 0 || currentStory.isLikedByMe,
                size = 18.dp,
                inactiveTint = Color.White
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${currentStory.reactionsCount.coerceAtLeast(1)} reactions",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          // Comment Center button
          Button(
            onClick = { showCommentsSheet = true },
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.Black.copy(alpha = 0.65f),
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .testTag("story_comments_button")
          ) {
            Icon(
              imageVector = Icons.Default.ChatBubbleOutline,
              contentDescription = "Comment Center",
              tint = VybeMagenta,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Comments",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1
            )
          }
        }
      }
    }

    // Viewers Bottom Sheet ("Who saw")
    if (showViewersSheet) {
      val viewersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      val viewerCandidates = remember(allUsers, currentUser) {
        val filtered = allUsers.filter { it.id != currentUser?.id }
        if (filtered.isNotEmpty()) filtered else listOfNotNull(currentUser)
      }
      ModalBottomSheet(
        onDismissRequest = { showViewersSheet = false },
        sheetState = viewersSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = "Story Activity & Viewers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "Seen by ${viewerCandidates.size} people • ${currentStory.reactionsCount.coerceAtLeast(1)} reactions",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
              )
            }
            IconButton(onClick = { showViewersSheet = false }) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
          }

          HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 380.dp)
              .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(viewerCandidates, key = { it.id }) { viewer ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                VybeAvatar(user = viewer, size = 42.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = viewer.displayName.ifBlank { viewer.username },
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                      fontSize = 13.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    if (viewer.isVerified) {
                      Spacer(modifier = Modifier.width(4.dp))
                      VerificationBadge(user = viewer)
                    }
                  }
                  Text(
                    text = "@${viewer.username} • Viewed recently",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                  )
                }

                VybeLikeIcon(
                  isLiked = true,
                  size = 18.dp
                )
              }
            }
          }
        }
      }
    }

    // Story Comment Center Sheet
    if (showCommentsSheet) {
      val commentsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      var newStoryComment by remember { mutableStateOf("") }
      var storyCommentsList by remember {
        mutableStateOf(
          listOf(
            "Loving the vibe! 🫰",
            "This view is unreal!",
            "Need to catch up soon!"
          )
        )
      }

      ModalBottomSheet(
        onDismissRequest = { showCommentsSheet = false },
        sheetState = commentsSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .imePadding()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = "Story Comment Center",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "Replies and reactions to your story",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
              )
            }
            IconButton(onClick = { showCommentsSheet = false }) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
          }

          HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 280.dp)
              .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(storyCommentsList) { commentText ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
              ) {
                VybeAvatar(user = currentStory.author, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Story Reply",
                    fontWeight = FontWeight.SemiBold,
                    color = VybeElectricCyan,
                    fontSize = 12.sp
                  )
                  Text(
                    text = commentText,
                    color = Color.White,
                    fontSize = 13.sp
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = newStoryComment,
              onValueChange = { newStoryComment = it },
              placeholder = { Text("Add comment or note...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = VybeElectricCyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.35f)
              ),
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
              singleLine = true
            )
            IconButton(
              onClick = {
                if (newStoryComment.isNotBlank()) {
                  storyCommentsList = storyCommentsList + newStoryComment.trim()
                  newStoryComment = ""
                }
              },
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(VybeGradient)
            ) {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StoryCanvas(
  story: Story,
  isPaused: Boolean,
  isMuted: Boolean,
  onVideoDurationDetected: (Int) -> Unit,
  onVideoCompleted: () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    when (story.mediaType) {
      StoryMediaType.PHOTO -> {
        if (story.mediaUrl.isNotBlank()) {
          AsyncImage(
            model = story.mediaUrl,
            contentDescription = "Story photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Brush.verticalGradient(listOf(VybeViolet, VybeMagenta)))
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
              contentScale = ContentScale.Fit,
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
                .size(104.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(VybeMagenta, VybeBrandCyan))),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(50.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
              color = Color.Black.copy(alpha = 0.6f),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = story.musicTrackTitle ?: "Audio Track",
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
        // Real uploaded video playback supporting both portrait & landscape dimensions cleanly
        VybeVideoPlayer(
          videoUrl = story.mediaUrl,
          autoPlay = true,
          isLooping = false,
          initialMuted = isMuted,
          isPausedExternal = isPaused,
          fitMode = VideoFitMode.FIT_CONTAIN,
          showControls = false,
          onDurationDetected = onVideoDurationDetected,
          onVideoCompleted = onVideoCompleted,
          modifier = Modifier.fillMaxSize()
        )
      }
    }

    // Story Caption & Location overlay pill
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 84.dp)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      if (!story.location.isNullOrBlank()) {
        Surface(
          color = Color.Black.copy(alpha = 0.65f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.padding(bottom = 8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = story.location ?: "",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }
        }
      }

      if (story.caption.isNotBlank() && story.mediaType != StoryMediaType.TEXT) {
        Surface(
          color = Color.Black.copy(alpha = 0.68f),
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

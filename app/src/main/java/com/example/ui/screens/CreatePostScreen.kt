package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.MoodType
import com.example.ui.VybeViewModel
import com.example.ui.components.LocationPickerDialog
import com.example.ui.components.SelectedLocationResult
import com.example.ui.components.StoryCreateDialog
import com.example.ui.components.VideoFitMode
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeMoodOutlineIcon
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.components.VybeVideoPlayer
import com.example.ui.theme.*
import com.example.util.FeedAudioController
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

enum class PublishDestination {
  FEED_POST, VYBES_REEL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(viewModel: VybeViewModel) {
  val palette = LocalVybePalette.current
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  val currentUser by viewModel.currentUser.collectAsState()
  val isOnline by viewModel.isOnline.collectAsState()

  var publishDestination by remember { mutableStateOf(PublishDestination.FEED_POST) }
  var caption by remember { mutableStateOf("") }
  var selectedMood by remember { mutableStateOf<MoodType?>(null) }
  var selectedMediaUri by remember { mutableStateOf<String?>(null) }
  var selectedMediaType by remember { mutableStateOf(MediaType.TEXT) }
  var cropRatio by remember { mutableStateOf("1:1") }
  var detectedMediaRatio by remember { mutableStateOf<Float?>(null) }
  var showPreviewDialog by remember { mutableStateOf(false) }
  var selectedLocationResult by remember { mutableStateOf<SelectedLocationResult?>(null) }
  var selectedAudioTrack by remember { mutableStateOf<String?>(null) }
  var previewingAudioTrack by remember { mutableStateOf<String?>(null) }
  var showLocationDialog by remember { mutableStateOf(false) }
  var showStoryComposerDialog by remember { mutableStateOf(false) }
  var localValidationMessage by remember { mutableStateOf<String?>(null) }
  var isSubmitting by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    onDispose {
      FeedAudioController.stopMusicTrack("composer_preview")
    }
  }

  BackHandler(enabled = !isSubmitting) {
    FeedAudioController.stopMusicTrack("composer_preview")
    viewModel.navigateBack()
  }

  val isVideoMedia = selectedMediaUri != null && (
    selectedMediaType == MediaType.VIDEO ||
      selectedMediaType == MediaType.REEL ||
      selectedMediaUri!!.endsWith(".mp4", ignoreCase = true) ||
      selectedMediaUri!!.endsWith(".mov", ignoreCase = true) ||
      selectedMediaUri!!.endsWith(".webm", ignoreCase = true)
    )

  val preferredAspectRatio: Float? = when (cropRatio) {
    "4:5" -> 0.8f
    "16:9" -> 16f / 9f
    "1:1" -> 1.0f
    else -> detectedMediaRatio
  }

  val previewContainerRatio: Float = when {
    publishDestination == PublishDestination.VYBES_REEL && isVideoMedia -> (detectedMediaRatio ?: (9f / 16f)).coerceIn(0.56f, 1.78f)
    cropRatio == "4:5" -> 0.8f
    cropRatio == "16:9" -> 16f / 9f
    else -> 1.0f
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val cached = MediaUtils.copyUriToLocalCache(context, uri, "post_photo")
      selectedMediaUri = cached
      selectedMediaType = MediaType.IMAGE
      localValidationMessage = null
      val meta = MediaUtils.inspectMediaAspectRatio(context, cached)
      detectedMediaRatio = meta
      if (meta != null) {
        cropRatio = when {
          meta < 0.88f -> "4:5"
          meta > 1.25f -> "16:9"
          else -> "1:1"
        }
      }
    }
  }

  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val cached = MediaUtils.copyUriToLocalCache(context, uri, "post_video")
      selectedMediaUri = cached
      selectedMediaType = if (publishDestination == PublishDestination.VYBES_REEL) MediaType.REEL else MediaType.VIDEO
      localValidationMessage = null
      val meta = MediaUtils.inspectMediaAspectRatio(context, cached)
      detectedMediaRatio = meta
      if (meta != null) {
        cropRatio = when {
          meta < 0.88f -> "4:5"
          meta > 1.25f -> "16:9"
          else -> "1:1"
        }
      }
    }
  }

  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap ->
    if (bitmap != null) {
      val uri = MediaUtils.saveBitmapToCache(context, bitmap, prefix = "post_photo")
      selectedMediaUri = uri.toString()
      selectedMediaType = MediaType.IMAGE
      localValidationMessage = null
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      cameraLauncher.launch(null)
    } else {
      localValidationMessage = "Camera permission is required to capture a photo directly."
    }
  }

  fun submitPost() {
    if (isSubmitting) return
    if (!isOnline) {
      localValidationMessage = "No internet connection. Reconnect before publishing your post."
      return
    }
    if (publishDestination == PublishDestination.VYBES_REEL && !isVideoMedia) {
      localValidationMessage = "Select a video file to publish a VYBES Reel."
      return
    }
    if (caption.isBlank() && selectedMediaUri == null) {
      localValidationMessage = "Write a caption or attach a photo or video first."
      return
    }

    val resolvedType = when {
      selectedMediaUri == null -> MediaType.TEXT
      publishDestination == PublishDestination.VYBES_REEL -> MediaType.REEL
      isVideoMedia -> MediaType.VIDEO
      else -> MediaType.IMAGE
    }

    isSubmitting = true
    localValidationMessage = null
    FeedAudioController.stopMusicTrack("composer_preview")
    previewingAudioTrack = null
    coroutineScope.launch {
      val result = viewModel.publishPostAwait(
        caption = caption.trim(),
        mood = selectedMood,
        mediaType = resolvedType,
        imageUrl = selectedMediaUri,
        isVybes = publishDestination == PublishDestination.VYBES_REEL,
        locationName = selectedLocationResult?.displayName,
        latitude = selectedLocationResult?.latitude,
        longitude = selectedLocationResult?.longitude,
        audioTrackTitle = selectedAudioTrack,
        preferredAspectRatio = if (selectedMediaUri != null) preferredAspectRatio else null
      )
      isSubmitting = false
      if (result.isFailure) {
        localValidationMessage = result.exceptionOrNull()?.message
          ?: "Could not publish your post. Please verify your connection and try again."
      }
    }
  }

  val canSubmit = !isSubmitting && if (publishDestination == PublishDestination.VYBES_REEL) {
    isVideoMedia
  } else {
    caption.isNotBlank() || selectedMediaUri != null
  }

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = if (publishDestination == PublishDestination.FEED_POST) "Create Feed Post" else "Create VYBES Reel",
        onBackClick = { if (!isSubmitting) viewModel.navigateBack() },
        backTestTag = "create_post_back_button",
        actions = {
          TextButton(
            onClick = { submitPost() },
            enabled = canSubmit,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("publish_post_button")
          ) {
            if (isSubmitting) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = palette.accent
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Publishing...", fontWeight = FontWeight.Bold, color = palette.accent)
            } else {
              Text(
                text = "Publish",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (canSubmit) palette.accent else palette.textMuted
              )
            }
          }
        }
      )
    },
    bottomBar = {
      Surface(
        color = palette.card,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column {
          HorizontalDivider(thickness = 0.8.dp, color = palette.border)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .imePadding()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(
              modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              VybeSecondaryButton(
                text = "Story",
                icon = Icons.Default.AutoStories,
                onClick = { showStoryComposerDialog = true },
                enabled = !isSubmitting,
                testTag = "open_story_composer_from_post_btn"
              )

              VybePrimaryButton(
                text = when {
                  isSubmitting -> "Uploading & Saving..."
                  publishDestination == PublishDestination.FEED_POST -> "Post to VYBE Feed"
                  else -> "Publish VYBES Reel"
                },
                onClick = { submitPost() },
                enabled = canSubmit,
                isLoading = isSubmitting,
                modifier = Modifier.weight(1f),
                testTag = "publish_bottom_button"
              )
            }
          }
        }
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp)
          .verticalScroll(rememberScrollState())
      ) {
        if (currentUser != null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            VybeAvatar(user = currentUser!!, size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                currentUser!!.displayName,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
              )
              Text(
                text = if (publishDestination == PublishDestination.FEED_POST) {
                  if (currentUser!!.isPrivate) "Visible to Approved LINKUPS" else "Visible in Community Feed"
                } else {
                  "Visible in VYBES Short-Video Stream"
                },
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Publishing Destination (Feed Post vs VYBES Reel)
        Text(
          text = "PUBLISHING DESTINATION",
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = palette.textMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          val isFeedDest = publishDestination == PublishDestination.FEED_POST
          VybeSurfaceCard(
            onClick = if (!isSubmitting) {
              {
                publishDestination = PublishDestination.FEED_POST
                if (selectedMediaType == MediaType.REEL) {
                  selectedMediaType = MediaType.VIDEO
                }
                localValidationMessage = null
              }
            } else null,
            containerColor = if (isFeedDest) palette.accent.copy(alpha = 0.14f) else palette.card,
            borderColor = if (isFeedDest) palette.accent else palette.border,
            cornerRadius = VybeTokens.Radius.md,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.DynamicFeed, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Feed Post", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text("Text, photo, or video", fontSize = 11.sp, color = palette.textSecondary)
          }

          val isReelDest = publishDestination == PublishDestination.VYBES_REEL
          VybeSurfaceCard(
            onClick = if (!isSubmitting) {
              {
                publishDestination = PublishDestination.VYBES_REEL
                if (selectedMediaUri != null && !isVideoMedia) {
                  selectedMediaUri = null
                  selectedMediaType = MediaType.REEL
                } else if (isVideoMedia) {
                  selectedMediaType = MediaType.REEL
                }
                localValidationMessage = null
              }
            } else null,
            containerColor = if (isReelDest) palette.accentSecondary.copy(alpha = 0.14f) else palette.card,
            borderColor = if (isReelDest) palette.accentSecondary else palette.border,
            cornerRadius = VybeTokens.Radius.md,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.PlayCircle, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("VYBES Reel", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text("Full-screen short video", fontSize = 11.sp, color = palette.textSecondary)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
          value = caption,
          onValueChange = {
            caption = it
            localValidationMessage = null
          },
          enabled = !isSubmitting,
          placeholder = {
            Text(
              if (publishDestination == PublishDestination.FEED_POST)
                "Share what's on your mind..."
              else
                "Add a caption and #hashtags for your VYBES reel...",
              color = palette.textMuted
            )
          },
          minLines = 3,
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = palette.elevated,
            unfocusedContainerColor = palette.elevated,
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.border,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary
          ),
          shape = RoundedCornerShape(VybeTokens.Radius.md),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_post_caption_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mood Zone Tagging
        Text(
          text = "MOOD ZONE (OPTIONAL)",
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = palette.textMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(MoodType.values()) { mood ->
            val isSelected = selectedMood == mood
            Surface(
              color = if (isSelected) mood.accentColor.copy(alpha = 0.18f) else palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, if (isSelected) mood.accentColor else palette.border),
              modifier = Modifier
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable(enabled = !isSubmitting) {
                  selectedMood = if (isSelected) null else mood
                }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                VybeMoodOutlineIcon(
                  mood = mood,
                  size = 15.dp,
                  tint = if (isSelected) mood.accentColor else palette.textSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = mood.title,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) mood.accentColor else palette.textPrimary
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Media Selection Controls
        Text(
          text = if (publishDestination == PublishDestination.FEED_POST) "ATTACH MEDIA" else "SELECT REEL VIDEO",
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = palette.textMuted
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (publishDestination == PublishDestination.VYBES_REEL) {
          VybeSecondaryButton(
            text = "Choose Video from Gallery",
            icon = Icons.Default.Videocam,
            onClick = {
              videoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
              )
            },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            testTag = "pick_gallery_video_btn"
          )
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            VybeSecondaryButton(
              text = "Photo",
              icon = Icons.Default.PhotoLibrary,
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              enabled = !isSubmitting,
              modifier = Modifier.weight(1f),
              testTag = "pick_gallery_photo_btn"
            )

            VybeSecondaryButton(
              text = "Video",
              icon = Icons.Default.Videocam,
              onClick = {
                videoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
              },
              enabled = !isSubmitting,
              modifier = Modifier.weight(1f),
              testTag = "pick_gallery_video_btn"
            )

            VybeSecondaryButton(
              text = "Camera",
              icon = Icons.Default.CameraAlt,
              onClick = {
                val granted = ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                  cameraLauncher.launch(null)
                } else {
                  cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
              },
              enabled = !isSubmitting,
              modifier = Modifier.weight(1f),
              testTag = "capture_camera_media_btn"
            )
          }
        }

        // Live Media Preview
        if (selectedMediaUri != null) {
          Spacer(modifier = Modifier.height(14.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(previewContainerRatio)
              .clip(RoundedCornerShape(VybeTokens.Radius.lg))
              .background(Color.Black)
              .testTag("create_post_media_preview")
          ) {
            if (isVideoMedia) {
              VybeVideoPlayer(
                videoUrl = selectedMediaUri!!,
                autoPlay = true,
                isLooping = true,
                initialMuted = true,
                isPausedExternal = showPreviewDialog || isSubmitting,
                fitMode = VideoFitMode.FIT_CONTAIN,
                onAspectRatioDetected = { ratio ->
                  if (detectedMediaRatio == null) {
                    detectedMediaRatio = ratio
                  }
                },
                modifier = Modifier.fillMaxSize()
              )
            } else {
              AsyncImage(
                model = selectedMediaUri,
                contentDescription = "Selected photo preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            VybeSecondaryButton(
              text = "Full Preview",
              icon = Icons.Default.Fullscreen,
              onClick = { showPreviewDialog = true },
              enabled = !isSubmitting,
              modifier = Modifier.weight(1f)
            )

            VybeSecondaryButton(
              text = "Remove Media",
              icon = Icons.Default.DeleteOutline,
              onClick = {
                selectedMediaUri = null
                selectedMediaType = MediaType.TEXT
                detectedMediaRatio = null
              },
              enabled = !isSubmitting,
              modifier = Modifier.weight(1f),
              testTag = "remove_selected_media_button"
            )
          }

          if (publishDestination == PublishDestination.FEED_POST) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "FEED ASPECT RATIO",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.8.sp,
              color = palette.textMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(
                "1:1" to "1:1 Square",
                "4:5" to "4:5 Portrait",
                "16:9" to "16:9 Landscape"
              ).forEach { (ratio, label) ->
                val selected = cropRatio == ratio
                Surface(
                  color = if (selected) palette.accent else palette.elevated,
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  border = BorderStroke(1.dp, if (selected) palette.accent else palette.border),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.md))
                    .clickable(enabled = !isSubmitting) { cropRatio = ratio }
                ) {
                  Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                    Text(
                      text = label,
                      fontSize = 11.sp,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                      color = if (selected) Color.White else palette.textSecondary,
                      maxLines = 1
                    )
                  }
                }
              }
            }
          }
        }

        // Optional Feed Music Track Selector
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "FEED MUSIC (OPTIONAL)",
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = palette.textMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(FeedAudioController.PRESET_TRACKS) { track ->
            val isSelected = selectedAudioTrack == track
            val isPreviewing = previewingAudioTrack == track
            Surface(
              color = if (isSelected) palette.accent.copy(alpha = 0.16f) else palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, if (isSelected) palette.accent else palette.border),
              modifier = Modifier
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable(enabled = !isSubmitting) {
                  if (isSelected) {
                    selectedAudioTrack = null
                    previewingAudioTrack = null
                    FeedAudioController.stopMusicTrack("composer_preview")
                  } else {
                    selectedAudioTrack = track
                  }
                }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Outlined.MusicNote,
                  contentDescription = null,
                  tint = if (isSelected) palette.accent else palette.textSecondary,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = track,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) palette.accent else palette.textPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                  imageVector = if (isPreviewing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                  contentDescription = if (isPreviewing) "Stop preview" else "Preview track",
                  tint = if (isPreviewing) palette.accentSecondary else palette.textSecondary,
                  modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = !isSubmitting) {
                      if (isPreviewing) {
                        previewingAudioTrack = null
                        FeedAudioController.stopMusicTrack("composer_preview")
                      } else {
                        selectedAudioTrack = track
                        previewingAudioTrack = track
                        FeedAudioController.playMusicTrack("composer_preview", track)
                      }
                    }
                )
              }
            }
          }
        }

        // Real Google Maps Location Picker Row
        Spacer(modifier = Modifier.height(16.dp))
        VybeSurfaceCard(
          onClick = if (!isSubmitting) {
            { showLocationDialog = true }
          } else null,
          containerColor = palette.card,
          borderColor = palette.border,
          cornerRadius = VybeTokens.Radius.md,
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("open_location_picker_row")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.LocationOn,
              contentDescription = null,
              tint = palette.danger,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = selectedLocationResult?.displayName ?: "Add Location",
                fontWeight = if (selectedLocationResult != null) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = if (selectedLocationResult != null) palette.textPrimary else palette.textSecondary
              )
              Text(
                text = when {
                  selectedLocationResult == null -> "Pin on Google Maps, use GPS, or enter a manual place"
                  selectedLocationResult!!.isCoordinatesVerified -> "Map coordinates attached"
                  else -> "Manual location label"
                },
                fontSize = 11.sp,
                color = palette.textMuted
              )
            }
            if (selectedLocationResult != null) {
              IconButton(
                onClick = { selectedLocationResult = null },
                enabled = !isSubmitting,
                modifier = Modifier.size(40.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Remove location", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
              }
            } else {
              Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
            }
          }
        }

        if (!localValidationMessage.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            color = palette.danger.copy(alpha = 0.14f),
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = palette.danger,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = localValidationMessage!!,
                color = palette.danger,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }

  // Full-Screen Media Preview Dialog
  if (showPreviewDialog && selectedMediaUri != null) {
    Dialog(onDismissRequest = { showPreviewDialog = false }) {
      Surface(
        shape = RoundedCornerShape(VybeTokens.Radius.lg),
        color = palette.card,
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 540.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (isVideoMedia) "Video Preview ($cropRatio)" else "Photo Preview ($cropRatio)",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = palette.textPrimary
            )
            IconButton(onClick = { showPreviewDialog = false }, modifier = Modifier.size(48.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Close preview", tint = palette.textSecondary)
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .clip(RoundedCornerShape(VybeTokens.Radius.md))
              .background(Color.Black)
          ) {
            if (isVideoMedia) {
              VybeVideoPlayer(
                videoUrl = selectedMediaUri!!,
                autoPlay = true,
                isLooping = true,
                fitMode = VideoFitMode.FIT_CONTAIN,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              AsyncImage(
                model = selectedMediaUri,
                contentDescription = "Full media preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
              )
            }
          }
        }
      }
    }
  }

  if (showLocationDialog) {
    LocationPickerDialog(
      initialLocationName = selectedLocationResult?.displayName,
      onDismiss = { showLocationDialog = false },
      onLocationSelected = { picked ->
        selectedLocationResult = picked
      }
    )
  }

  if (showStoryComposerDialog) {
    StoryCreateDialog(
      viewModel = viewModel,
      onDismiss = { showStoryComposerDialog = false },
      onStoryPublished = { feedback ->
        showStoryComposerDialog = false
        viewModel.postBannerMessage(feedback)
        viewModel.navigateBack()
      }
    )
  }
}

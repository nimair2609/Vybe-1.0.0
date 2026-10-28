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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaType
import com.example.data.model.MoodType
import com.example.data.model.MusicAttachment
import com.example.data.model.formatMusicMillis
import com.example.ui.VybeViewModel
import com.example.ui.components.LocationPickerDialog
import com.example.ui.components.MusicPickerSheet
import com.example.ui.components.SelectedLocationResult
import com.example.ui.components.StoryCreateDialog
import com.example.ui.components.VideoFitMode
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeGradientButton
import com.example.ui.components.VybeMoodOutlineIcon
import com.example.ui.components.VybeMusicSticker
import com.example.ui.components.VybeVideoPlayer
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet
import com.example.util.FeedAudioController
import com.example.util.MediaUtils
import com.example.util.music.BindMusicPreviewLifecycle
import com.example.util.music.MusicPreviewController
import com.example.util.music.MusicPreviewState
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PublishDestination {
  FEED_POST, VYBES_REEL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(viewModel: VybeViewModel) {
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  val currentUser by viewModel.currentUser.collectAsState()
  val isOnline by viewModel.isOnline.collectAsState()

  BindMusicPreviewLifecycle(stopOnDispose = true)
  val musicPreviewState by MusicPreviewController.state.collectAsState()

  var publishDestination by remember { mutableStateOf(PublishDestination.FEED_POST) }
  var caption by remember { mutableStateOf("") }
  var selectedMood by remember { mutableStateOf<MoodType?>(null) }
  var selectedMediaUri by remember { mutableStateOf<String?>(null) }
  var selectedMediaType by remember { mutableStateOf(MediaType.TEXT) }
  var cropRatio by remember { mutableStateOf("1:1") }
  var detectedMediaRatio by remember { mutableStateOf<Float?>(null) }
  var showPreviewDialog by remember { mutableStateOf(false) }
  var selectedLocationResult by remember { mutableStateOf<SelectedLocationResult?>(null) }
  var selectedMusicAttachment by remember { mutableStateOf<MusicAttachment?>(null) }
  var showMusicPickerSheet by remember { mutableStateOf(false) }
  var showLocationDialog by remember { mutableStateOf(false) }
  var showStoryComposerDialog by remember { mutableStateOf(false) }
  var localValidationMessage by remember { mutableStateOf<String?>(null) }
  var isSubmitting by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    val pendingTrack = viewModel.consumePendingComposerMusicTrack()
    if (pendingTrack != null) {
      publishDestination = PublishDestination.VYBES_REEL
      selectedMediaType = MediaType.REEL
      selectedMusicAttachment = MusicAttachment.fromTrack(pendingTrack)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      FeedAudioController.stopMusicTrack("composer_preview")
      MusicPreviewController.stop()
    }
  }

  BackHandler(enabled = !isSubmitting) {
    FeedAudioController.stopMusicTrack("composer_preview")
    MusicPreviewController.stop()
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
    MusicPreviewController.stop()
    coroutineScope.launch {
      val sanitizedMusic = selectedMusicAttachment?.sanitized()
      val result = viewModel.publishPostAwait(
        caption = caption.trim(),
        mood = selectedMood,
        mediaType = resolvedType,
        imageUrl = selectedMediaUri,
        isVybes = publishDestination == PublishDestination.VYBES_REEL,
        locationName = selectedLocationResult?.displayName,
        latitude = selectedLocationResult?.latitude,
        longitude = selectedLocationResult?.longitude,
        audioTrackTitle = sanitizedMusic?.displayLabel,
        preferredAspectRatio = if (selectedMediaUri != null) preferredAspectRatio else null,
        musicAttachment = sanitizedMusic
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
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = {
              if (!isSubmitting) {
                FeedAudioController.stopMusicTrack("composer_preview")
                MusicPreviewController.stop()
                viewModel.navigateBack()
              }
            },
            enabled = !isSubmitting,
            modifier = Modifier
              .size(48.dp)
              .testTag("create_post_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text(
            text = if (publishDestination == PublishDestination.FEED_POST) "Create Feed Post" else "Create VYBES Reel",
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
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
                color = VybeViolet
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Publishing...", fontWeight = FontWeight.Bold, color = VybeViolet)
            } else {
              Text(
                text = "Publish",
                fontWeight = FontWeight.Bold,
                color = if (canSubmit) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    bottomBar = {
      // Dedicated composer footer cleanly separated from content & video preview controls
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
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
            OutlinedButton(
              onClick = {
                MusicPreviewController.stop()
                showStoryComposerDialog = true
              },
              enabled = !isSubmitting,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("open_story_composer_from_post_btn")
            ) {
              Icon(Icons.Default.AutoStories, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Story", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Box(modifier = Modifier.weight(1f)) {
              VybeGradientButton(
                text = when {
                  isSubmitting -> "Uploading & Saving..."
                  publishDestination == PublishDestination.FEED_POST -> "Post to VYBE Feed"
                  else -> "Publish VYBES Reel"
                },
                onClick = { submitPost() },
                enabled = canSubmit,
                modifier = Modifier.testTag("publish_bottom_button")
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
              Text(currentUser!!.displayName, fontWeight = FontWeight.Bold)
              Text(
                text = if (publishDestination == PublishDestination.FEED_POST) {
                  if (currentUser!!.isPrivate) "Visible to Approved Followers" else "Visible in Community Feed"
                } else {
                  "Visible in VYBES Short-Video Stream"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Publishing Destination (Feed Post vs VYBES Reel)
        Text(
          text = "Publishing Destination",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            color = if (publishDestination == PublishDestination.FEED_POST) VybeViolet.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            border = if (publishDestination == PublishDestination.FEED_POST) BorderStroke(1.5.dp, VybeViolet) else null,
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .clickable(enabled = !isSubmitting) {
                publishDestination = PublishDestination.FEED_POST
                if (selectedMediaType == MediaType.REEL) {
                  selectedMediaType = MediaType.VIDEO
                }
                localValidationMessage = null
              }
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DynamicFeed, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Feed Post", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
              Spacer(modifier = Modifier.height(2.dp))
              Text("Text, photo, or video", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Surface(
            color = if (publishDestination == PublishDestination.VYBES_REEL) VybeMagenta.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            border = if (publishDestination == PublishDestination.VYBES_REEL) BorderStroke(1.5.dp, VybeMagenta) else null,
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .clickable(enabled = !isSubmitting) {
                publishDestination = PublishDestination.VYBES_REEL
                if (selectedMediaUri != null && !isVideoMedia) {
                  selectedMediaUri = null
                  selectedMediaType = MediaType.REEL
                } else if (isVideoMedia) {
                  selectedMediaType = MediaType.REEL
                }
                localValidationMessage = null
              }
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("VYBES Reel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
              Spacer(modifier = Modifier.height(2.dp))
              Text("Full-screen short video", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                "Add a caption and #hashtags for your VYBES reel..."
            )
          },
          minLines = 3,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_post_caption_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mood Zone Tagging
        Text(
          text = "Mood Zone (Optional — defaults to General)",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(MoodType.values()) { mood ->
            val isSelected = selectedMood == mood
            Surface(
              color = if (isSelected) mood.accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              border = if (isSelected) BorderStroke(1.5.dp, mood.accentColor) else null,
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !isSubmitting) {
                  selectedMood = if (isSelected) null else mood
                }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                VybeMoodOutlineIcon(
                  mood = mood,
                  size = 15.dp,
                  tint = if (isSelected) mood.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = mood.title,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) mood.accentColor else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Media Selection Controls (Well-proportioned, unclipped 48dp touch targets)
        Text(
          text = if (publishDestination == PublishDestination.FEED_POST) "Attach Media" else "Select Reel Video",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (publishDestination == PublishDestination.VYBES_REEL) {
          OutlinedButton(
            onClick = {
              videoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
              )
            },
            enabled = !isSubmitting,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("pick_gallery_video_btn")
          ) {
            Icon(Icons.Default.Videocam, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose Video from Gallery", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              enabled = !isSubmitting,
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("pick_gallery_photo_btn")
            ) {
              Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Photo", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            }

            OutlinedButton(
              onClick = {
                videoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
              },
              enabled = !isSubmitting,
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("pick_gallery_video_btn")
            ) {
              Icon(Icons.Default.Videocam, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Video", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            }

            OutlinedButton(
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
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("capture_camera_media_btn")
            ) {
              Icon(Icons.Default.CameraAlt, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Camera", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            }
          }
        }

        // Live Media Preview + Separated Preview Action Bar (Never overlaps video controls)
        if (selectedMediaUri != null) {
          Spacer(modifier = Modifier.height(14.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(previewContainerRatio)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF0A0A0F))
              .testTag("create_post_media_preview")
          ) {
            val isMusicSyncPlaying = selectedMusicAttachment != null &&
              musicPreviewState is MusicPreviewState.Playing &&
              (musicPreviewState as MusicPreviewState.Playing).trackId == selectedMusicAttachment?.trackId
            val effectiveOriginalVolume = selectedMusicAttachment?.originalAudioVolume ?: 1f

            if (isVideoMedia) {
              VybeVideoPlayer(
                videoUrl = selectedMediaUri!!,
                autoPlay = true,
                isLooping = true,
                initialMuted = if (isMusicSyncPlaying) effectiveOriginalVolume <= 0.01f else true,
                isPausedExternal = showPreviewDialog || showMusicPickerSheet || isSubmitting,
                fitMode = VideoFitMode.FIT_CONTAIN,
                audioVolume = effectiveOriginalVolume,
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

            selectedMusicAttachment?.let { attached ->
              VybeMusicSticker(
                title = attached.title,
                artist = attached.artist,
                artworkUrl = attached.artworkUrl,
                segmentLabel = attached.formattedSegmentRange,
                onClick = {
                  MusicPreviewController.stop()
                  showMusicPickerSheet = true
                },
                modifier = Modifier
                  .align(Alignment.TopStart)
                  .padding(12.dp)
                  .widthIn(max = 260.dp)
                  .testTag("reel_composer_music_sticker")
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = { showPreviewDialog = true },
              enabled = !isSubmitting,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
            ) {
              Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Full Preview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
              onClick = {
                selectedMediaUri = null
                selectedMediaType = MediaType.TEXT
                detectedMediaRatio = null
              },
              enabled = !isSubmitting,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .testTag("remove_selected_media_button")
            ) {
              Icon(Icons.Default.DeleteOutline, contentDescription = "Remove media", modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Remove Media", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }

          if (publishDestination == PublishDestination.FEED_POST) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "Feed Aspect Ratio",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold
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
                FilterChip(
                  selected = cropRatio == ratio,
                  onClick = { if (!isSubmitting) cropRatio = ratio },
                  label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }

        // Licensed Music Integration for VYBES Reels & Video/Feed Posts
        Spacer(modifier = Modifier.height(14.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (publishDestination == PublishDestination.VYBES_REEL) {
              "Reel Music & Audio Mix (Optional)"
            } else {
              "Post Music (Optional — Licensed Catalog)"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
          )
          if (selectedMusicAttachment != null) {
            TextButton(
              onClick = {
                MusicPreviewController.stop()
                showMusicPickerSheet = true
              },
              enabled = !isSubmitting,
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("reel_change_music_button")
            ) {
              Text("Change Track", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VybeBrandCyan)
            }
          }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (selectedMusicAttachment == null) {
          OutlinedButton(
            onClick = {
              MusicPreviewController.stop()
              showMusicPickerSheet = true
            },
            enabled = !isSubmitting,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.2.dp, VybeMagenta.copy(alpha = 0.7f)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("reel_add_music_button")
          ) {
            Icon(
              imageVector = Icons.Default.MusicNote,
              contentDescription = "Add Music",
              tint = VybeMagenta,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.add_music_emoji),
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
              text = "Search, trim & mix",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          val music = selectedMusicAttachment!!
          val isTrackPreviewing = (musicPreviewState is MusicPreviewState.Playing &&
            (musicPreviewState as MusicPreviewState.Playing).trackId == music.trackId) ||
            (musicPreviewState is MusicPreviewState.Buffering &&
              (musicPreviewState as MusicPreviewState.Buffering).trackId == music.trackId)
          val showDualAudioControls = publishDestination == PublishDestination.VYBES_REEL || isVideoMedia

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.2.dp, VybeViolet.copy(alpha = 0.65f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reel_audio_studio_card")
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                VybeMusicSticker(
                  title = music.title,
                  artist = music.artist,
                  artworkUrl = music.artworkUrl,
                  segmentLabel = music.formattedSegmentRange,
                  onClick = {
                    MusicPreviewController.stop()
                    showMusicPickerSheet = true
                  },
                  modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                  onClick = {
                    MusicPreviewController.togglePreview(
                      track = music.toLicensedTrack(),
                      startTimeMs = music.startTimeMs,
                      clipDurationMs = music.clipDurationMs,
                      volume = music.musicVolume
                    )
                  },
                  enabled = !isSubmitting && !music.streamUrl.isNullOrBlank(),
                  modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VybeViolet.copy(alpha = 0.22f))
                    .testTag("reel_music_preview_button")
                ) {
                  Icon(
                    imageVector = if (isTrackPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isTrackPreviewing) "Stop music preview" else "Preview music with reel",
                    tint = if (isTrackPreviewing) VybeMagenta else VybeBrandCyan
                  )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                  onClick = {
                    MusicPreviewController.stop()
                    selectedMusicAttachment = null
                  },
                  enabled = !isSubmitting,
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("reel_remove_music_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove selected music",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              // Timeline / Start-Time Selector & Clip Duration Chips
              Spacer(modifier = Modifier.height(12.dp))
              val maxStartMs = (music.trackDurationMs - music.clipDurationMs).coerceAtLeast(0L)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Start Time (${music.formattedSegmentRange})",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Track ${formatMusicMillis(music.trackDurationMs)}",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Slider(
                value = music.startTimeMs.coerceIn(0L, maxStartMs).toFloat(),
                onValueChange = { rawStart ->
                  val updated = music.copy(startTimeMs = rawStart.toLong()).sanitized()
                  selectedMusicAttachment = updated
                  MusicPreviewController.updateSegmentBounds(updated.startTimeMs, updated.clipDurationMs)
                },
                valueRange = 0f..maxStartMs.coerceAtLeast(1L).toFloat(),
                enabled = !isSubmitting && maxStartMs > 0L,
                colors = SliderDefaults.colors(
                  thumbColor = VybeBrandCyan,
                  activeTrackColor = VybeBrandCyan
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("reel_start_time_slider")
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Length:",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(10_000L to "10s", 15_000L to "15s", 30_000L to "30s", 60_000L to "60s")
                  .filter { (dur, _) -> dur <= music.trackDurationMs || dur == 15_000L }
                  .forEach { (durMs, label) ->
                    val targetClip = minOf(durMs, music.trackDurationMs.coerceAtLeast(1_000L))
                    FilterChip(
                      selected = kotlin.math.abs(music.clipDurationMs - targetClip) < 500L,
                      onClick = {
                        if (!isSubmitting) {
                          val updated = music.copy(clipDurationMs = targetClip).sanitized()
                          selectedMusicAttachment = updated
                          MusicPreviewController.updateSegmentBounds(updated.startTimeMs, updated.clipDurationMs)
                        }
                      },
                      label = { Text(label, fontSize = 11.sp) }
                    )
                  }
              }

              Spacer(modifier = Modifier.height(8.dp))

              // Music Volume Control
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.MusicNote,
                  contentDescription = null,
                  tint = VybeMagenta,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Music ${(music.musicVolume * 100).roundToInt()}%",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.width(86.dp)
                )
                Slider(
                  value = music.musicVolume,
                  onValueChange = { vol ->
                    val updated = music.copy(musicVolume = vol).sanitized()
                    selectedMusicAttachment = updated
                    MusicPreviewController.setVolume(updated.musicVolume)
                  },
                  valueRange = 0f..1f,
                  enabled = !isSubmitting,
                  colors = SliderDefaults.colors(
                    thumbColor = VybeMagenta,
                    activeTrackColor = VybeMagenta
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("reel_music_volume_slider")
                )
              }

              // Separate Original Video Audio Volume Control
              if (showDualAudioControls) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = VybeBrandCyan,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Original ${(music.originalAudioVolume * 100).roundToInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(86.dp)
                  )
                  Slider(
                    value = music.originalAudioVolume,
                    onValueChange = { origVol ->
                      selectedMusicAttachment = music.copy(originalAudioVolume = origVol).sanitized()
                    },
                    valueRange = 0f..1f,
                    enabled = !isSubmitting,
                    colors = SliderDefaults.colors(
                      thumbColor = VybeBrandCyan,
                      activeTrackColor = VybeBrandCyan
                    ),
                    modifier = Modifier
                      .weight(1f)
                      .testTag("reel_original_volume_slider")
                  )
                }
              }
            }
          }
        }

        // Real Google Maps Location Picker Row
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !isSubmitting) { showLocationDialog = true }
            .testTag("open_location_picker_row")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.LocationOn,
              contentDescription = null,
              tint = Color(0xFFFF5252),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = selectedLocationResult?.displayName ?: "Add Location",
                fontWeight = if (selectedLocationResult != null) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = if (selectedLocationResult != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = when {
                  selectedLocationResult == null -> "Pin on Google Maps, use GPS, or enter a manual place"
                  selectedLocationResult!!.isCoordinatesVerified -> "Map coordinates attached"
                  else -> "Manual location label"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            if (selectedLocationResult != null) {
              IconButton(
                onClick = { selectedLocationResult = null },
                enabled = !isSubmitting,
                modifier = Modifier.size(40.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Remove location", modifier = Modifier.size(16.dp))
              }
            } else {
              Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }

        if (!localValidationMessage.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = localValidationMessage!!,
                color = MaterialTheme.colorScheme.onErrorContainer,
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

  // Full-Screen Media Preview Dialog (Supports both Photo and Video)
  if (showPreviewDialog && selectedMediaUri != null) {
    Dialog(onDismissRequest = { showPreviewDialog = false }) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
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
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
            IconButton(onClick = { showPreviewDialog = false }, modifier = Modifier.size(48.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Close preview")
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
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

  if (showMusicPickerSheet) {
    MusicPickerSheet(
      initialAttachment = selectedMusicAttachment,
      showOriginalAudioControl = publishDestination == PublishDestination.VYBES_REEL || isVideoMedia,
      defaultClipDurationMs = if (publishDestination == PublishDestination.VYBES_REEL) 30_000L else 15_000L,
      onDismiss = { showMusicPickerSheet = false },
      onMusicSelected = { attachment ->
        selectedMusicAttachment = attachment.sanitized()
        showMusicPickerSheet = false
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

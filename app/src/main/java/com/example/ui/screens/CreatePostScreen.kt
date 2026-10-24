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
import com.example.data.model.MediaType
import com.example.data.model.MoodType
import com.example.ui.VybeViewModel
import com.example.ui.components.LocationPickerDialog
import com.example.ui.components.SelectedLocationResult
import com.example.ui.components.StoryCreateDialog
import com.example.ui.components.VideoFitMode
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeGradientButton
import com.example.ui.components.VybeVideoPlayer
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

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

  var publishDestination by remember { mutableStateOf(PublishDestination.FEED_POST) }
  var caption by remember { mutableStateOf("") }
  var selectedMood by remember { mutableStateOf<MoodType?>(null) }
  var selectedMediaUri by remember { mutableStateOf<String?>(null) }
  var selectedMediaType by remember { mutableStateOf(MediaType.TEXT) }
  var cropRatio by remember { mutableStateOf("1:1") }
  var detectedMediaRatio by remember { mutableStateOf<Float?>(null) }
  var showPreviewDialog by remember { mutableStateOf(false) }
  var selectedLocationResult by remember { mutableStateOf<SelectedLocationResult?>(null) }
  var showLocationDialog by remember { mutableStateOf(false) }
  var showStoryComposerDialog by remember { mutableStateOf(false) }
  var localValidationMessage by remember { mutableStateOf<String?>(null) }
  var isSubmitting by remember { mutableStateOf(false) }

  BackHandler(enabled = !isSubmitting) {
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
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { if (!isSubmitting) viewModel.navigateBack() },
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
              onClick = { showStoryComposerDialog = true },
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
                Text(mood.emoji, fontSize = 14.sp)
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

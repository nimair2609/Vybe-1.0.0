package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.StoryAudience
import com.example.data.model.StoryMediaType
import com.example.ui.VybeViewModel
import com.example.ui.theme.*
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

val StoryGradients = listOf(
  "NEON_PURPLE" to listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)),
  "SUNSET_VIBE" to listOf(Color(0xFFF857A6), Color(0xFFFF5858)),
  "CYBER_TEAL" to listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
  "DEEP_SPACE" to listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
  "EMERALD" to listOf(Color(0xFF134E5E), Color(0xFF71B280)),
  "ROYAL_INDIGO" to listOf(Color(0xFF654EA3), Color(0xFFEAAFC8))
)

val IndianIndieMusicTracks = listOf(
  "Midnight Tape 04" to "Aarav Roy • Indie Chill",
  "Bombay Monsoon Beats" to "Kabir & The Vibe • Lo-Fi",
  "Streets of Colaba" to "Mira Sen • Ambient Acoustic",
  "Bengaluru Tech Sunsets" to "Electronic Pulse • Synthwave",
  "Paharganj Nightwalk" to "Delhi Sound Project • Bass"
)

enum class StoryCreateMode {
  CAMERA_PHOTO, TYPE_TEXT, MUSIC, VIDEO
}

/**
 * VYBE Story Composer Dialog:
 * - Real Photo, Camera & Video pickers with live `VybeVideoPlayer` preview.
 * - Action controls cleanly separated below the media canvas so they never overlap story preview or video controls.
 * - Awaits verified Storage + Database persistence (`publishStoryAwait`) before closing — never fakes publication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCreateDialog(
  viewModel: VybeViewModel,
  onDismiss: () -> Unit,
  onStoryPublished: (String) -> Unit
) {
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  val currentUser by viewModel.currentUser.collectAsState()
  val isOnline by viewModel.isOnline.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()

  val uploadStatus by viewModel.uploadStatus.collectAsState()

  var selectedMode by remember { mutableStateOf(StoryCreateMode.CAMERA_PHOTO) }
  var selectedAudience by remember { mutableStateOf(StoryAudience.EVERYONE) }
  var captionText by remember { mutableStateOf("") }
  var selectedLocation by remember { mutableStateOf<String?>(null) }
  var showLocationPickerDialog by remember { mutableStateOf(false) }
  var showVybeMatchesManager by remember { mutableStateOf(false) }
  var showEmptyMatchesAlert by remember { mutableStateOf(false) }
  var showMatchesConfirmDialog by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }
  var isPublishingStory by remember { mutableStateOf(false) }

  var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
  var pickedVideoUri by remember { mutableStateOf<Uri?>(null) }
  var pendingCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
  var pendingCameraVideoUri by remember { mutableStateOf<Uri?>(null) }
  var pendingCameraMode by remember { mutableStateOf("PHOTO") }
  var detectedVideoRatio by remember { mutableStateOf(9f / 16f) }

  var textBackgroundKey by remember { mutableStateOf("NEON_PURPLE") }
  val currentGradient = StoryGradients.firstOrNull { it.first == textBackgroundKey }?.second
    ?: StoryGradients.first().second

  var selectedTrack by remember { mutableStateOf(IndianIndieMusicTracks.first()) }

  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val localPath = MediaUtils.copyUriToLocalCache(context, uri, "story_photo")
      val validation = MediaUtils.validateMediaUri(context, localPath, MediaUtils.MAX_STORY_POST_BYTES)
      if (validation.isSuccess) {
        pickedPhotoUri = Uri.parse(localPath)
        validationError = null
      } else {
        validationError = validation.exceptionOrNull()?.message ?: "Unsupported photo format."
      }
    }
  }

  val cameraPhotoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success ->
    val targetUri = pendingCameraPhotoUri
    if (success && targetUri != null) {
      val validation = MediaUtils.validateMediaUri(context, targetUri.toString(), MediaUtils.MAX_STORY_POST_BYTES)
      if (validation.isSuccess) {
        pickedPhotoUri = targetUri
        validationError = null
      } else {
        validationError = validation.exceptionOrNull()?.message ?: "Captured photo could not be read."
      }
    }
  }

  val cameraVideoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CaptureVideo()
  ) { success ->
    val targetUri = pendingCameraVideoUri
    if (success && targetUri != null) {
      val validation = MediaUtils.validateMediaUri(context, targetUri.toString(), MediaUtils.MAX_STORY_POST_BYTES)
      if (validation.isSuccess) {
        pickedVideoUri = targetUri
        detectedVideoRatio = MediaUtils.extractMediaAspectRatio(context, targetUri, isVideo = true) ?: (9f / 16f)
        validationError = null
      } else {
        validationError = validation.exceptionOrNull()?.message ?: "Recorded video could not be read."
      }
    }
  }

  fun launchStoryCamera(mode: String) {
    try {
      if (mode == "VIDEO") {
        val vidUri = MediaUtils.createTempVideoUri(context)
        pendingCameraVideoUri = vidUri
        cameraVideoLauncher.launch(vidUri)
      } else {
        val imgUri = MediaUtils.createTempImageUri(context)
        pendingCameraPhotoUri = imgUri
        cameraPhotoLauncher.launch(imgUri)
      }
    } catch (e: Exception) {
      validationError = "Unable to open camera on this device: ${e.message ?: "check camera availability"}."
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      launchStoryCamera(pendingCameraMode)
    } else {
      validationError = "Camera permission is required to capture a story."
    }
  }

  val videoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val localPath = MediaUtils.copyUriToLocalCache(context, uri, "story_video")
      val validation = MediaUtils.validateMediaUri(context, localPath, MediaUtils.MAX_STORY_POST_BYTES)
      if (validation.isSuccess) {
        val localUri = Uri.parse(localPath)
        pickedVideoUri = localUri
        detectedVideoRatio = MediaUtils.extractMediaAspectRatio(context, localUri, isVideo = true) ?: (9f / 16f)
        validationError = null
      } else {
        validationError = validation.exceptionOrNull()?.message ?: "Unsupported or oversized video file."
      }
    }
  }

  fun validateStoryInputs(): Pair<Boolean, String> {
    if (!isOnline) {
      return false to "No internet connection. Reconnect before publishing your story."
    }
    return when (selectedMode) {
      StoryCreateMode.CAMERA_PHOTO -> {
        val uri = pickedPhotoUri
        if (uri == null) {
          false to "Please select or capture a photo before sharing your story."
        } else {
          val v = MediaUtils.validateMediaUri(context, uri.toString(), MediaUtils.MAX_STORY_POST_BYTES)
          if (v.isSuccess) true to uri.toString() else false to (v.exceptionOrNull()?.message ?: "Invalid photo file.")
        }
      }
      StoryCreateMode.VIDEO -> {
        val uri = pickedVideoUri
        if (uri == null) {
          false to "Please select or record a video before sharing your story."
        } else {
          val v = MediaUtils.validateMediaUri(context, uri.toString(), MediaUtils.MAX_STORY_POST_BYTES)
          if (v.isSuccess) true to uri.toString() else false to (v.exceptionOrNull()?.message ?: "Invalid video file.")
        }
      }
      StoryCreateMode.TYPE_TEXT -> {
        if (captionText.trim().isEmpty()) false to "Please enter text for your Type story."
        else true to ""
      }
      StoryCreateMode.MUSIC -> {
        true to (pickedPhotoUri?.toString() ?: "")
      }
    }
  }

  fun executeStoryPublish(targetAudience: StoryAudience) {
    if (isPublishingStory) return
    val (valid, resolvedUri) = validateStoryInputs()
    if (!valid) {
      validationError = resolvedUri
      return
    }

    val mediaType = when (selectedMode) {
      StoryCreateMode.CAMERA_PHOTO -> StoryMediaType.PHOTO
      StoryCreateMode.TYPE_TEXT -> StoryMediaType.TEXT
      StoryCreateMode.MUSIC -> StoryMediaType.MUSIC
      StoryCreateMode.VIDEO -> StoryMediaType.VIDEO
    }

    isPublishingStory = true
    validationError = null
    coroutineScope.launch {
      val result = viewModel.publishStoryAwait(
        mediaUrl = if (selectedMode == StoryCreateMode.TYPE_TEXT) "" else resolvedUri,
        caption = captionText.trim(),
        mediaType = mediaType,
        audience = targetAudience,
        musicTrackTitle = if (selectedMode == StoryCreateMode.MUSIC) "${selectedTrack.first} — ${selectedTrack.second}" else null,
        location = selectedLocation,
        textBackgroundColor = if (selectedMode == StoryCreateMode.TYPE_TEXT || (selectedMode == StoryCreateMode.MUSIC && resolvedUri.isBlank())) textBackgroundKey else null
      )
      isPublishingStory = false
      if (result.isSuccess) {
        val msg = if (targetAudience == StoryAudience.VYBE_MATCHES) {
          "Story published for ${vybeMatches.size} VYBE Matches!"
        } else {
          "Your story is now live!"
        }
        onStoryPublished(msg)
        onDismiss()
      } else {
        validationError = result.exceptionOrNull()?.message
          ?: "Failed to publish story. Please check your connection and try again."
      }
    }
  }

  Dialog(
    onDismissRequest = { if (!isPublishingStory) onDismiss() },
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = true
    )
  ) {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .imePadding(),
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Create Story",
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp
            )
          },
          navigationIcon = {
            IconButton(
              onClick = { if (!isPublishingStory) onDismiss() },
              enabled = !isPublishingStory,
              modifier = Modifier.size(48.dp).testTag("close_story_dialog_button")
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close")
            }
          },
          actions = {
            TextButton(
              onClick = { showVybeMatchesManager = true },
              enabled = !isPublishingStory,
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("manage_vybe_matches_header_button")
            ) {
              Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Matches (${vybeMatches.size})",
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }

            Button(
              onClick = {
                if (selectedAudience == StoryAudience.VYBE_MATCHES) {
                  val (valid, errOrUri) = validateStoryInputs()
                  if (!valid) {
                    validationError = errOrUri
                    return@Button
                  }
                  if (vybeMatches.isEmpty()) {
                    showEmptyMatchesAlert = true
                  } else {
                    showMatchesConfirmDialog = true
                  }
                } else {
                  executeStoryPublish(StoryAudience.EVERYONE)
                }
              },
              enabled = !isPublishingStory,
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(16.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier
                .padding(end = 8.dp)
                .heightIn(min = 36.dp)
                .testTag("publish_story_header_button")
            ) {
              if (isPublishingStory) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
              } else {
                Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
      },
      bottomBar = {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 12.dp,
          tonalElevation = 6.dp,
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("create_story_bottom_bar")
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(
              modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              // 1. VYBE Matches Button
              OutlinedButton(
                onClick = {
                  val (valid, errOrUri) = validateStoryInputs()
                  if (!valid) {
                    validationError = errOrUri
                    return@OutlinedButton
                  }
                  if (vybeMatches.isEmpty()) {
                    showEmptyMatchesAlert = true
                  } else {
                    showMatchesConfirmDialog = true
                  }
                },
                enabled = !isPublishingStory,
                border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(50.dp)
                  .testTag("publish_vybe_matches_button")
              ) {
                Icon(
                  Icons.Default.Star,
                  contentDescription = null,
                  tint = Color(0xFF10B981),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Matches (${vybeMatches.size})",
                  color = Color(0xFF10B981),
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              // 2. Share Story Button
              Button(
                onClick = {
                  if (selectedAudience == StoryAudience.VYBE_MATCHES) {
                    val (valid, errOrUri) = validateStoryInputs()
                    if (!valid) {
                      validationError = errOrUri
                      return@Button
                    }
                    if (vybeMatches.isEmpty()) {
                      showEmptyMatchesAlert = true
                    } else {
                      showMatchesConfirmDialog = true
                    }
                  } else {
                    executeStoryPublish(StoryAudience.EVERYONE)
                  }
                },
                enabled = !isPublishingStory,
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .height(50.dp)
                  .testTag("publish_your_story_button")
              ) {
                if (isPublishingStory) {
                  CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(15.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Sharing...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                  Icon(
                    Icons.Default.Upload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Share Story",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                  )
                }
              }
            }
          }
        }
      }
    ) { padding ->
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.TopCenter
      ) {
        val adaptivePreviewHeight = remember(maxHeight, selectedMode, detectedVideoRatio) {
          val baseTarget = if (selectedMode == StoryCreateMode.VIDEO && detectedVideoRatio > 1.15f) 220.dp else 285.dp
          minOf(baseTarget, (maxHeight * 0.44f).coerceAtLeast(180.dp))
        }
        Column(
          modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          if (validationError != null) {
            Surface(
              color = VybeDanger.copy(alpha = 0.14f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = validationError ?: "",
                  color = VybeDanger,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }

          if (isPublishingStory) {
            val statusLabel = when (val st = uploadStatus) {
              is com.example.data.repository.UploadStatus.Uploading -> "${st.label} (${st.progressPercent}%)"
              is com.example.data.repository.UploadStatus.Processing -> st.label
              else -> "Publishing story to VYBE..."
            }
            val progressVal = (uploadStatus as? com.example.data.repository.UploadStatus.Uploading)
              ?.progressPercent
              ?.let { (it / 100f).coerceIn(0.05f, 1f) }
            Surface(
              color = VybeViolet.copy(alpha = 0.14f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = statusLabel,
                  color = MaterialTheme.colorScheme.onSurface,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
                if (progressVal != null) {
                  LinearProgressIndicator(
                    progress = { progressVal },
                    color = VybeViolet,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                  )
                } else {
                  LinearProgressIndicator(
                    color = VybeViolet,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                  )
                }
              }
            }
          }

          // --- 1. Mode Switcher ---
          Text(
            text = "STORY FORMAT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf(
              Triple(StoryCreateMode.CAMERA_PHOTO, "Photo", Icons.Default.PhotoCamera),
              Triple(StoryCreateMode.VIDEO, "Video", Icons.Default.Videocam),
              Triple(StoryCreateMode.TYPE_TEXT, "Type", Icons.Default.TextFields),
              Triple(StoryCreateMode.MUSIC, "Music", Icons.Default.MusicNote)
            ).forEach { (mode, label, icon) ->
              val isSelected = selectedMode == mode
              Surface(
                color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 56.dp)
                  .clickable(enabled = !isPublishingStory) {
                    selectedMode = mode
                    validationError = null
                  }
                  .testTag("story_mode_${mode.name}")
              ) {
                Column(
                  modifier = Modifier.padding(vertical = 10.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          // --- 2. Clean Visual Preview Canvas (Adaptive height so bottom buttons never clip) ---
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(adaptivePreviewHeight)
              .clip(RoundedCornerShape(18.dp))
              .background(Color(0xFF0A0A0F)),
            contentAlignment = Alignment.Center
          ) {
            when (selectedMode) {
              StoryCreateMode.CAMERA_PHOTO -> {
                if (pickedPhotoUri != null) {
                  AsyncImage(
                    model = pickedPhotoUri,
                    contentDescription = "Selected story photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .background(Brush.verticalGradient(listOf(Color(0xFF231D33), Color(0xFF12101B))))
                      .clickable(enabled = !isPublishingStory) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                      }
                      .testTag("story_pick_photo_canvas"),
                    contentAlignment = Alignment.Center
                  ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                      Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(48.dp))
                      Spacer(modifier = Modifier.height(10.dp))
                      Text("Tap to Select Photo", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                      Spacer(modifier = Modifier.height(4.dp))
                      Text("Or use Gallery / Camera controls below", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                    }
                  }
                }
              }

              StoryCreateMode.VIDEO -> {
                if (pickedVideoUri != null) {
                  VybeVideoPlayer(
                    videoUrl = pickedVideoUri.toString(),
                    autoPlay = true,
                    isLooping = true,
                    initialMuted = true,
                    isPausedExternal = isPublishingStory,
                    fitMode = VideoFitMode.FIT_CONTAIN,
                    showControls = true,
                    onAspectRatioDetected = { ratio -> detectedVideoRatio = ratio },
                    modifier = Modifier.fillMaxSize()
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .background(Brush.verticalGradient(listOf(Color(0xFF192236), Color(0xFF0E131F))))
                      .clickable(enabled = !isPublishingStory) {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                      }
                      .testTag("story_pick_video_canvas"),
                    contentAlignment = Alignment.Center
                  ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                      Icon(Icons.Default.VideoCall, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(52.dp))
                      Spacer(modifier = Modifier.height(10.dp))
                      Text("Tap to Select Video", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                      Spacer(modifier = Modifier.height(4.dp))
                      Text("Supports portrait (9:16) and landscape (16:9) video playback", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                  }
                }
              }

              StoryCreateMode.TYPE_TEXT -> {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(currentGradient))
                    .padding(24.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = if (captionText.isNotBlank()) captionText else "Type your story message below...",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                  )
                }
              }

              StoryCreateMode.MUSIC -> {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))))
                    .padding(20.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                      modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(VybeMagenta, VybeBrandCyan))),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                      color = Color.White.copy(alpha = 0.14f),
                      shape = RoundedCornerShape(12.dp)
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(selectedTrack.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(selectedTrack.second, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                  }
                }
              }
            }
          }

          // Dedicated Media Source Controls cleanly BELOW preview canvas
          if (selectedMode == StoryCreateMode.CAMERA_PHOTO) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = {
                  photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !isPublishingStory,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
              ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (pickedPhotoUri != null) "Change Photo" else "Gallery Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }

              OutlinedButton(
                onClick = {
                  pendingCameraMode = "PHOTO"
                  val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                  if (granted) launchStoryCamera("PHOTO") else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                enabled = !isPublishingStory,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
              ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Take Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          } else if (selectedMode == StoryCreateMode.VIDEO) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = {
                  videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                enabled = !isPublishingStory,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
              ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (pickedVideoUri != null) "Change Video" else "Gallery Video", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }

              OutlinedButton(
                onClick = {
                  pendingCameraMode = "VIDEO"
                  val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                  if (granted) launchStoryCamera("VIDEO") else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                enabled = !isPublishingStory,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
              ) {
                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record Video", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }

          // --- 3. Caption / Text Content ---
          Text(
            text = if (selectedMode == StoryCreateMode.TYPE_TEXT) "STORY TEXT" else "CAPTION (OPTIONAL)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
          )

          OutlinedTextField(
            value = captionText,
            onValueChange = {
              captionText = it
              validationError = null
            },
            enabled = !isPublishingStory,
            placeholder = { Text(if (selectedMode == StoryCreateMode.TYPE_TEXT) "Write what's on your mind..." else "Add a caption...") },
            modifier = Modifier.fillMaxWidth().testTag("story_caption_input"),
            shape = RoundedCornerShape(14.dp),
            minLines = if (selectedMode == StoryCreateMode.TYPE_TEXT) 2 else 1
          )

          if (selectedMode == StoryCreateMode.TYPE_TEXT) {
            Text("BACKGROUND GRADIENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(StoryGradients) { (key, colors) ->
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(colors))
                    .border(
                      width = if (textBackgroundKey == key) 2.5.dp else 0.dp,
                      color = Color.White,
                      shape = CircleShape
                    )
                    .clickable(enabled = !isPublishingStory) { textBackgroundKey = key }
                )
              }
            }
          }

          if (selectedMode == StoryCreateMode.MUSIC) {
            Text("SELECT SOUNDTRACK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              IndianIndieMusicTracks.forEach { track ->
                val isTrackSelected = selectedTrack == track
                Surface(
                  color = if (isTrackSelected) VybeMagenta.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isPublishingStory) { selectedTrack = track }
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = if (isTrackSelected) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                      contentDescription = null,
                      tint = if (isTrackSelected) VybeMagenta else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(track.first, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                      Text(track.second, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isTrackSelected) {
                      Icon(Icons.Default.Check, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }

          // --- 4. Location Picker ---
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = { showLocationPickerDialog = true },
              enabled = !isPublishingStory,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("story_open_location_picker_button")
            ) {
              Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (selectedLocation != null) "Location: $selectedLocation" else "Add Location",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            if (selectedLocation != null) {
              IconButton(
                onClick = { selectedLocation = null },
                enabled = !isPublishingStory,
                modifier = Modifier.size(48.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Remove location")
              }
            }
          }

          // --- 5. Audience & VYBE Matches Control ---
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "AUDIENCE & PRIVACY",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              letterSpacing = 1.sp
            )
            TextButton(
              onClick = { showVybeMatchesManager = true },
              enabled = !isPublishingStory,
              modifier = Modifier.heightIn(min = 48.dp).testTag("story_edit_vybe_matches_button")
            ) {
              Text("Add / Remove Matches (${vybeMatches.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              color = if (selectedAudience == StoryAudience.EVERYONE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .clickable(enabled = !isPublishingStory) { selectedAudience = StoryAudience.EVERYONE }
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text("Your Story", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Text(
                    text = if (currentUser?.isPrivate == true) "Approved followers" else "Followers & public",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }

            Surface(
              color = if (selectedAudience == StoryAudience.VYBE_MATCHES) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .clickable(enabled = !isPublishingStory) { selectedAudience = StoryAudience.VYBE_MATCHES }
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    "VYBE Matches",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (selectedAudience == StoryAudience.VYBE_MATCHES) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    "${vybeMatches.size} selected",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
        }
      }
    }

    if (showLocationPickerDialog) {
      LocationPickerDialog(
        initialLocationName = selectedLocation,
        onDismiss = { showLocationPickerDialog = false },
        onLocationSelected = { res ->
          selectedLocation = res?.displayName
        }
      )
    }

    if (showVybeMatchesManager) {
      VybeMatchesManagerDialog(
        currentUserId = currentUser?.id.orEmpty(),
        allUsers = allUsers,
        selectedMatches = vybeMatches,
        onAddMatch = { viewModel.addVybeMatch(it) },
        onRemoveMatch = { viewModel.removeVybeMatch(it) },
        onDismiss = { showVybeMatchesManager = false }
      )
    }

    if (showEmptyMatchesAlert) {
      AlertDialog(
        onDismissRequest = { showEmptyMatchesAlert = false },
        title = {
          Text("No VYBE Matches Selected", fontWeight = FontWeight.Bold)
        },
        text = {
          Text(
            "You haven't added anyone to your VYBE Matches list yet. Add people first so your restricted story is shared only with your chosen audience."
          )
        },
        confirmButton = {
          Button(
            onClick = {
              showEmptyMatchesAlert = false
              showVybeMatchesManager = true
            },
            modifier = Modifier.testTag("empty_matches_add_people_button")
          ) {
            Text("Select People")
          }
        },
        dismissButton = {
          TextButton(onClick = { showEmptyMatchesAlert = false }) {
            Text("Cancel")
          }
        }
      )
    }

    if (showMatchesConfirmDialog) {
      val namesPreview = vybeMatches.take(5).joinToString(", ") { "@${it.username}" } +
        if (vybeMatches.size > 5) " +${vybeMatches.size - 5} more" else ""
      AlertDialog(
        onDismissRequest = { showMatchesConfirmDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF10B981))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirm VYBE Matches Audience", fontWeight = FontWeight.Bold, fontSize = 17.sp)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              "This story will be visible ONLY to your ${vybeMatches.size} selected VYBE Matches for 24 hours:"
            )
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = namesPreview,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(10.dp)
              )
            }
            TextButton(
              onClick = {
                showMatchesConfirmDialog = false
                showVybeMatchesManager = true
              }
            ) {
              Text("Edit VYBE Matches List")
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              showMatchesConfirmDialog = false
              executeStoryPublish(StoryAudience.VYBE_MATCHES)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            modifier = Modifier.testTag("confirm_publish_vybe_matches_button")
          ) {
            Text("Confirm & Share", color = Color.White, fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { showMatchesConfirmDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}

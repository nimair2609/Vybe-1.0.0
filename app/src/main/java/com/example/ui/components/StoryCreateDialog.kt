package com.example.ui.components

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
import coil.compose.AsyncImage
import com.example.data.model.StoryAudience
import com.example.data.model.StoryMediaType
import com.example.ui.VybeViewModel
import com.example.ui.theme.*
import com.example.util.MediaUtils

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
 * - Real Photo & Video pickers with live `VybeVideoPlayer` preview (supporting portrait & landscape cleanly).
 * - Google Maps + GPS + manual `LocationPickerDialog` integration.
 * - VYBE Matches Add/Remove manager (`VybeMatchesManagerDialog`), empty-audience safety guard,
 *   and explicit Audience Confirmation Dialog before publishing to VYBE Matches.
 * - Safe-area inset handling (`WindowInsets.safeDrawing` + `navigationBarsPadding()`) so bottom
 *   "VYBE Matches" and "Your Story" buttons are always visible and accessible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCreateDialog(
  viewModel: VybeViewModel,
  onDismiss: () -> Unit,
  onStoryPublished: (String) -> Unit
) {
  var selectedMode by remember { mutableStateOf(StoryCreateMode.CAMERA_PHOTO) }
  var selectedAudience by remember { mutableStateOf(StoryAudience.EVERYONE) }
  var captionText by remember { mutableStateOf("") }
  var selectedLocation by remember { mutableStateOf<String?>(null) }
  var showLocationPickerDialog by remember { mutableStateOf(false) }
  var showVybeMatchesManager by remember { mutableStateOf(false) }
  var showEmptyMatchesAlert by remember { mutableStateOf(false) }
  var showMatchesConfirmDialog by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val context = LocalContext.current
  val currentUser by viewModel.currentUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()

  var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
  var pickedVideoUri by remember { mutableStateOf<Uri?>(null) }
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
      pickedPhotoUri = Uri.parse(localPath)
      validationError = null
    }
  }

  val videoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val localPath = MediaUtils.copyUriToLocalCache(context, uri, "story_video")
      val localUri = Uri.parse(localPath)
      pickedVideoUri = localUri
      detectedVideoRatio = MediaUtils.extractMediaAspectRatio(context, localUri, isVideo = true) ?: (9f / 16f)
      validationError = null
    }
  }

  fun validateStoryInputs(): Pair<Boolean, String> {
    return when (selectedMode) {
      StoryCreateMode.CAMERA_PHOTO -> {
        if (pickedPhotoUri == null) false to "Please select a photo before sharing your story."
        else true to pickedPhotoUri.toString()
      }
      StoryCreateMode.VIDEO -> {
        if (pickedVideoUri == null) false to "Please select a video before sharing your story."
        else true to pickedVideoUri.toString()
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
    val (valid, resolvedUri) = validateStoryInputs()
    if (!valid) {
      validationError = resolvedUri
      return
    }
    publishStory(
      viewModel = viewModel,
      mode = selectedMode,
      audience = targetAudience,
      caption = captionText.trim(),
      mediaUri = resolvedUri,
      textBgKey = textBackgroundKey,
      musicTitle = selectedTrack.first,
      location = selectedLocation,
      onSuccess = {
        val msg = if (targetAudience == StoryAudience.VYBE_MATCHES) {
          "Uploading story for ${vybeMatches.size} VYBE Matches..."
        } else {
          "Uploading story to Your Story..."
        }
        onStoryPublished(msg)
        onDismiss()
      }
    )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false
    )
  ) {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
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
              onClick = onDismiss,
              modifier = Modifier.size(48.dp).testTag("close_story_dialog_button")
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close")
            }
          },
          actions = {
            TextButton(
              onClick = { showVybeMatchesManager = true },
              modifier = Modifier
                .padding(end = 8.dp)
                .heightIn(min = 48.dp)
                .testTag("manage_vybe_matches_header_button")
            ) {
              Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Matches (${vybeMatches.size})",
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
      },
      bottomBar = {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 10.dp,
          tonalElevation = 4.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // 1. VYBE Matches Button (with empty-audience guard & confirmation dialog)
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
              border = BorderStroke(1.5.dp, Color(0xFF10B981)),
              shape = RoundedCornerShape(24.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
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
                text = "VYBE Matches (${vybeMatches.size})",
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            // 2. Your Story Button
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
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(24.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("publish_your_story_button")
            ) {
              Text(
                text = "Your Story",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1
              )
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
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
                .clickable {
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

        // --- 2. Visual Preview Canvas (Supports Portrait & Landscape Cleanly) ---
        val canvasHeight = if (selectedMode == StoryCreateMode.VIDEO && detectedVideoRatio > 1.15f) 240.dp else 320.dp
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(canvasHeight)
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
                Surface(
                  color = Color.Black.copy(alpha = 0.65f),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clickable {
                      photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Photo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                  }
                }
              } else {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF231D33), Color(0xFF12101B))))
                    .clickable {
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
                    Text("Portrait and landscape photos supported", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
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
                  fitMode = VideoFitMode.FIT_CONTAIN,
                  showControls = true,
                  onAspectRatioDetected = { ratio -> detectedVideoRatio = ratio },
                  modifier = Modifier.fillMaxSize()
                )
                Surface(
                  color = Color.Black.copy(alpha = 0.68f),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clickable {
                      videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Video", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                  }
                }
              } else {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF192236), Color(0xFF0E131F))))
                    .clickable {
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

          if (selectedLocation != null) {
            Surface(
              color = Color.Black.copy(alpha = 0.7f),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clickable { selectedLocation = null }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(selectedLocation ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.Close, contentDescription = "Remove location", tint = Color.White, modifier = Modifier.size(13.dp))
              }
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
                  .clickable { textBackgroundKey = key }
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
                  .clickable { selectedTrack = track }
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

        // --- 4. Location Picker (Google Maps / GPS / Manual) ---
        OutlinedButton(
          onClick = { showLocationPickerDialog = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("story_open_location_picker_button")
        ) {
          Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (selectedLocation != null) "Location: $selectedLocation (Change)" else "Add Location",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
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
              .clickable { selectedAudience = StoryAudience.EVERYONE }
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
              .clickable { selectedAudience = StoryAudience.VYBE_MATCHES }
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

    // Empty VYBE Matches Guard: never silently publish to an empty or unintended audience!
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

    // Audience Confirmation Dialog before publishing to VYBE Matches
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

private fun publishStory(
  viewModel: VybeViewModel,
  mode: StoryCreateMode,
  audience: StoryAudience,
  caption: String,
  mediaUri: String,
  textBgKey: String,
  musicTitle: String,
  location: String?,
  onSuccess: () -> Unit
) {
  val mediaType = when (mode) {
    StoryCreateMode.CAMERA_PHOTO -> StoryMediaType.PHOTO
    StoryCreateMode.TYPE_TEXT -> StoryMediaType.TEXT
    StoryCreateMode.MUSIC -> StoryMediaType.MUSIC
    StoryCreateMode.VIDEO -> StoryMediaType.VIDEO
  }

  viewModel.createStory(
    mediaUrl = if (mode == StoryCreateMode.TYPE_TEXT) "" else mediaUri,
    caption = caption,
    mediaType = mediaType,
    audience = audience,
    musicTrackTitle = if (mode == StoryCreateMode.MUSIC) musicTitle else null,
    location = location,
    textBackgroundColor = if (mode == StoryCreateMode.TYPE_TEXT) textBgKey else null
  )
  onSuccess()
}

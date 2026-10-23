package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.StoryAudience
import com.example.data.model.StoryMediaType
import com.example.ui.VybeViewModel
import com.example.ui.theme.*

// Background gradient presets for Instagram-like Type / Text mode
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

val PopularLocations = listOf(
  "📍 Bandra West, Mumbai",
  "📍 Hauz Khas Village, Delhi",
  "📍 Indiranagar, Bengaluru",
  "📍 Koregaon Park, Pune",
  "📍 Jubilee Hills, Hyderabad"
)

enum class StoryCreateMode {
  CAMERA_PHOTO, TYPE_TEXT, MUSIC, VIDEO
}

/**
 * INSTAGRAM-STYLE SEPARATE STORY CREATION DIALOG
 * Provides separate fields like Instagram:
 * - Mode selector: Photo/Camera, Type (Text "Aa"), Music, Video
 * - Media selection & preview canvas
 * - Text overlay & caption field
 * - Separate audience field: "Your Story" vs "Close Friends ⭐️ (Green Ring)"
 * - Location sticker selector
 * - Music track picker with wave animation badge
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
  var showLocationPicker by remember { mutableStateOf(false) }

  val context = androidx.compose.ui.platform.LocalContext.current
  // Photo / Media state (starts clean without default pre-selected photo)
  var pickedMediaUri by remember { mutableStateOf<Uri?>(null) }

  // Text mode state
  var textBackgroundKey by remember { mutableStateOf("NEON_PURPLE") }
  val currentGradient = StoryGradients.firstOrNull { it.first == textBackgroundKey }?.second
    ?: StoryGradients.first().second

  // Music mode state
  var selectedTrack by remember { mutableStateOf(IndianIndieMusicTracks.first()) }

  // Photo Picker Launcher
  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val localPath = com.example.util.MediaUtils.copyUriToLocalCache(context, uri, "story_media")
      pickedMediaUri = Uri.parse(localPath)
    }
  }

  // Video Picker Launcher
  val videoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val localPath = com.example.util.MediaUtils.copyUriToLocalCache(context, uri, "story_video")
      pickedMediaUri = Uri.parse(localPath)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize().imePadding(),
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Add to Story",
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp
            )
          },
          navigationIcon = {
            IconButton(onClick = onDismiss) {
              Icon(Icons.Default.Close, contentDescription = "Close")
            }
          },
          actions = {
            // Audience toggle indicator in header
            Surface(
              color = if (selectedAudience == StoryAudience.VYBE_MATCHES || selectedAudience == StoryAudience.CLOSE_FRIENDS) Color(0xFF2ECC71).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .padding(end = 12.dp)
                .clickable {
                  selectedAudience = if (selectedAudience == StoryAudience.VYBE_MATCHES || selectedAudience == StoryAudience.CLOSE_FRIENDS) StoryAudience.EVERYONE else StoryAudience.VYBE_MATCHES
                }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (selectedAudience == StoryAudience.VYBE_MATCHES || selectedAudience == StoryAudience.CLOSE_FRIENDS) {
                  Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("VYBE Matches", color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                } else {
                  Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Public", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
      },
      bottomBar = {
        // Dual Bottom Action Bar (VYBE Matches / Your Story)
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shadowElevation = 8.dp,
          tonalElevation = 4.dp,
          modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // 1. VYBE Matches Button
            OutlinedButton(
              onClick = {
                publishStory(
                  viewModel = viewModel,
                  mode = selectedMode,
                  audience = StoryAudience.VYBE_MATCHES,
                  caption = captionText,
                  mediaUri = pickedMediaUri?.toString() ?: "",
                  textBgKey = textBackgroundKey,
                  musicTitle = selectedTrack.first,
                  location = selectedLocation,
                  onSuccess = {
                    onStoryPublished("Published to VYBE Matches! ⭐️")
                    onDismiss()
                  }
                )
              },
              border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2ECC71)),
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("publish_vybe_matches_button")
            ) {
              Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("VYBE Matches", color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // 2. Your Story Button (Insta signature colorful share pill)
            Button(
              onClick = {
                publishStory(
                  viewModel = viewModel,
                  mode = selectedMode,
                  audience = StoryAudience.EVERYONE,
                  caption = captionText,
                  mediaUri = pickedMediaUri?.toString() ?: "",
                  textBgKey = textBackgroundKey,
                  musicTitle = selectedTrack.first,
                  location = selectedLocation,
                  onSuccess = {
                    onStoryPublished("Published to Your Story!")
                    onDismiss()
                  }
                )
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = VybeViolet
              ),
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("publish_your_story_button")
            ) {
              Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Your Story", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
          .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // --- 1. SEPARATE FIELD: Mode Switcher (Instagram Story Tools) ---
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
            Triple(StoryCreateMode.TYPE_TEXT, "Type (Aa)", Icons.Default.TextFields),
            Triple(StoryCreateMode.MUSIC, "Music", Icons.Default.MusicNote),
            Triple(StoryCreateMode.VIDEO, "Video", Icons.Default.Videocam)
          ).forEach { (mode, label, icon) ->
            val isSelected = selectedMode == mode
            Surface(
              color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .clickable { selectedMode = mode }
                .testTag("story_mode_${mode.name}")
            ) {
              Column(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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

        // --- 2. SEPARATE FIELD: Visual Preview Canvas ---
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black),
          contentAlignment = Alignment.Center
        ) {
          when (selectedMode) {
            StoryCreateMode.CAMERA_PHOTO -> {
              if (pickedMediaUri != null) {
                AsyncImage(
                  model = pickedMediaUri,
                  contentDescription = "Selected photo",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(
                      Brush.verticalGradient(listOf(Color(0xFF2A2338), Color(0xFF13111C)))
                    )
                    .clickable {
                      photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Tap to Select Photo from Device", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Or choose another story format above", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                  }
                }
              }

              // Gallery Picker Overlay button
              if (pickedMediaUri != null) {
                Box(
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable {
                      photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Photo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }

            StoryCreateMode.TYPE_TEXT -> {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(Brush.linearGradient(currentGradient))
                  .padding(20.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = if (captionText.isNotBlank()) captionText else "Tap below to type your thought...",
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
                  .background(
                    Brush.verticalGradient(
                      listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                    )
                  )
                  .padding(20.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Box(
                    modifier = Modifier
                      .size(80.dp)
                      .clip(CircleShape)
                      .background(Brush.linearGradient(listOf(VybeMagenta, VybeBrandCyan))),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                  }
                  Spacer(modifier = Modifier.height(14.dp))
                  Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text("🎵 ${selectedTrack.first}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(selectedTrack.second, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
              }
            }

            StoryCreateMode.VIDEO -> {
              if (pickedMediaUri != null) {
                Box(
                  modifier = Modifier.fillMaxSize(),
                  contentAlignment = Alignment.Center
                ) {
                  VideoPlayerView(
                    videoUrl = pickedMediaUri.toString(),
                    modifier = Modifier.fillMaxSize()
                  )
                  // Overlay to change video
                  Box(
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .padding(12.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(Color.Black.copy(alpha = 0.6f))
                      .clickable {
                        videoPicker.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                      }
                      .padding(horizontal = 12.dp, vertical = 8.dp)
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Change Video", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              } else {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111827))
                    .clickable {
                      videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                      )
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Tap to Select Story Video", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Select any MP4/WebM video from your device", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                  }
                }
              }
            }
          }

          // Active Location Sticker on Canvas
          if (selectedLocation != null) {
            Surface(
              color = Color.Black.copy(alpha = 0.65f),
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
                Text(selectedLocation ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Close, contentDescription = "Remove location", tint = Color.White, modifier = Modifier.size(12.dp))
              }
            }
          }
        }

        // --- 3. SEPARATE FIELD: Text / Caption Field ---
        Text(
          text = if (selectedMode == StoryCreateMode.TYPE_TEXT) "TEXT CONTENT" else "CAPTION & OVERLAY",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          letterSpacing = 1.sp
        )

        OutlinedTextField(
          value = captionText,
          onValueChange = { captionText = it },
          placeholder = { Text(if (selectedMode == StoryCreateMode.TYPE_TEXT) "Type your thought here..." else "Add a caption or sticker...") },
          modifier = Modifier.fillMaxWidth().testTag("story_caption_input"),
          shape = RoundedCornerShape(14.dp),
          minLines = if (selectedMode == StoryCreateMode.TYPE_TEXT) 2 else 1
        )

        // Gradient Selector for Text Mode
        if (selectedMode == StoryCreateMode.TYPE_TEXT) {
          Text("CHOOSE BACKGROUND COLOR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(StoryGradients) { (key, colors) ->
              Box(
                modifier = Modifier
                  .size(36.dp)
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

        // Music Selector for Music Mode
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

        // --- 4. SEPARATE FIELD: Location Tag Field ---
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "LOCATION STICKER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
          )
          TextButton(onClick = { showLocationPicker = !showLocationPicker }) {
            Text(if (showLocationPicker) "Hide" else "Choose", fontSize = 12.sp)
          }
        }

        AnimatedVisibility(visible = showLocationPicker) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PopularLocations.forEach { loc ->
              val isLocSelected = selectedLocation == loc
              Surface(
                color = if (isLocSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    selectedLocation = if (isLocSelected) null else loc
                  }
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = loc,
                    fontSize = 12.sp,
                    fontWeight = if (isLocSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                  )
                  if (isLocSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }
          }
        }

        // --- 5. SEPARATE FIELD: Audience Selection (Instagram Style) ---
        Text(
          text = "AUDIENCE & PRIVACY",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          letterSpacing = 1.sp
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Public: Sunset Gradient Ring
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
              Box(
                modifier = Modifier
                  .size(14.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.sweepGradient(listOf(Color(0xFFFBAA47), Color(0xFFD91A46), Color(0xFFA60F93)))
                  )
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("Your Story", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("All followers", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          // VYBE Matches: Green Ring
          Surface(
            color = if (selectedAudience == StoryAudience.VYBE_MATCHES || selectedAudience == StoryAudience.CLOSE_FRIENDS) Color(0xFF2ECC71).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .clickable { selectedAudience = StoryAudience.VYBE_MATCHES }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF2ECC71), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("VYBE Matches", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedAudience == StoryAudience.VYBE_MATCHES || selectedAudience == StoryAudience.CLOSE_FRIENDS) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurface)
                Text("Exclusive matches list", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))
      }
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

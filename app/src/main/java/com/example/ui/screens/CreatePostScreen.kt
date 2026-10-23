package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.location.Address
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.MoodType
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeGradientButton
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet

enum class PublishDestination {
  FEED_POST, VYBES_REEL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val context = androidx.compose.ui.platform.LocalContext.current
  val currentUser by viewModel.currentUser.collectAsState()
  var publishDestination by remember { mutableStateOf(PublishDestination.FEED_POST) }
  var postType by remember { mutableStateOf("Text & Photo") }
  var caption by remember { mutableStateOf("") }
  var selectedMood by remember { mutableStateOf<MoodType?>(null) }
  var selectedImage by remember { mutableStateOf<String?>(null) }
  var selectedMediaType by remember { mutableStateOf<MediaType>(MediaType.IMAGE) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      selectedImage = com.example.util.MediaUtils.copyUriToLocalCache(context, uri, "post_photo")
      selectedMediaType = MediaType.IMAGE
    }
  }

  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      selectedImage = com.example.util.MediaUtils.copyUriToLocalCache(context, uri, "post_video")
      selectedMediaType = if (publishDestination == PublishDestination.VYBES_REEL) MediaType.REEL else MediaType.VIDEO
    }
  }

  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap ->
    if (bitmap != null) {
      val uri = com.example.util.MediaUtils.saveBitmapToCache(context, bitmap, prefix = "post_photo")
      selectedImage = uri.toString()
      selectedMediaType = MediaType.IMAGE
    }
  }

  val samplePhotos = listOf(
    "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600",
    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
    "https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=600",
    "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600"
  )

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("create_post_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text(
            if (publishDestination == PublishDestination.FEED_POST) "Create Feed Post" else "Create VYBES Reel",
            fontWeight = FontWeight.Bold
          )
        },
        actions = {
          TextButton(
            onClick = {
              if (caption.isNotBlank() || selectedImage != null) {
                viewModel.createPost(
                  caption = caption,
                  mood = selectedMood,
                  imageUrl = selectedImage,
                  mediaType = if (publishDestination == PublishDestination.VYBES_REEL) MediaType.REEL else selectedMediaType
                )
                viewModel.navigateBack()
              }
            },
            enabled = caption.isNotBlank() || selectedImage != null,
            modifier = Modifier.testTag("publish_post_button")
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = if (caption.isNotBlank() || selectedImage != null) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                if (publishDestination == PublishDestination.VYBES_REEL) "Send Reel" else "Publish",
                fontWeight = FontWeight.Bold,
                color = if (caption.isNotBlank() || selectedImage != null) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      if (currentUser != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          VybeAvatar(user = currentUser!!, size = 40.dp)
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(currentUser!!.displayName, fontWeight = FontWeight.Bold)
            Text(
              if (publishDestination == PublishDestination.FEED_POST) "Visible in Main Community Feed" else "Visible in VYBES Short-Video Stream",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Section 9: Clear Publishing Choice (Post vs VYBES)
      Text(
        text = "Publishing Destination:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Option 1: Post (Regular Feed)
        Surface(
          color = if (publishDestination == PublishDestination.FEED_POST) VybeViolet.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          border = if (publishDestination == PublishDestination.FEED_POST) androidx.compose.foundation.BorderStroke(1.5.dp, VybeViolet) else null,
          modifier = Modifier
            .weight(1f)
            .clickable { publishDestination = PublishDestination.FEED_POST }
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.DynamicFeed, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Post", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text("Publish to regular feed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        // Option 2: VYBES (Short-Video section)
        Surface(
          color = if (publishDestination == PublishDestination.VYBES_REEL) VybeMagenta.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          border = if (publishDestination == PublishDestination.VYBES_REEL) androidx.compose.foundation.BorderStroke(1.5.dp, VybeMagenta) else null,
          modifier = Modifier
            .weight(1f)
            .clickable { publishDestination = PublishDestination.VYBES_REEL }
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.PlayCircle, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("VYBES", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text("Publish to short-video stream", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = caption,
        onValueChange = { caption = it },
        placeholder = {
          Text(if (publishDestination == PublishDestination.FEED_POST) "What's on your mind? Share your energy..." else "Add a punchy title and #hashtags for this VYBE clip...")
        },
        minLines = 3,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("create_post_caption_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Mood Zone Tagging
      Text(
        text = "Tag a Mood Zone (Optional):",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MoodType.values()) { mood ->
          val isSelected = selectedMood == mood
          Surface(
            color = if (isSelected) mood.accentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, mood.accentColor) else null,
            modifier = Modifier.clickable {
              selectedMood = if (isSelected) null else mood
            }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(mood.emoji, fontSize = 14.sp)
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = mood.title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) mood.accentColor else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Attach Photo / Video / Camera
      Text(
        text = if (publishDestination == PublishDestination.FEED_POST) "Attach Media (Photo, Video, or Camera):" else "Select VYBES Video Media:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(8.dp))

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
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("pick_gallery_photo_btn")
        ) {
          Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Photo", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }

        OutlinedButton(
          onClick = {
            videoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("pick_gallery_video_btn")
        ) {
          Icon(Icons.Default.Videocam, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Video", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }

        OutlinedButton(
          onClick = { cameraLauncher.launch(null) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("capture_camera_media_btn")
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Camera", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
      }

      // Selected Media Preview & Crop / Aspect Ratio
      var cropRatio by remember { mutableStateOf("1:1") }
      var showPreviewDialog by remember { mutableStateOf(false) }
      var selectedLocation by remember { mutableStateOf<String?>(null) }
      var showLocationDialog by remember { mutableStateOf(false) }
      var locationSearchQuery by remember { mutableStateOf("") }

      if (selectedImage != null) {
        Spacer(modifier = Modifier.height(12.dp))
        val previewHeight = when (cropRatio) {
          "4:5" -> 240.dp
          "16:9" -> 140.dp
          else -> 200.dp
        }
        val isVideo = selectedMediaType == MediaType.VIDEO || selectedMediaType == MediaType.REEL || (selectedImage != null && selectedImage!!.endsWith(".mp4", ignoreCase = true))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(previewHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
        ) {
          if (isVideo) {
            com.example.ui.components.VideoPlayerView(
              videoUrl = selectedImage!!,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            AsyncImage(
              model = selectedImage,
              contentDescription = "Selected media preview",
              contentScale = when (cropRatio) {
                "16:9" -> ContentScale.FillWidth
                "4:5" -> ContentScale.Crop
                else -> ContentScale.Fit
              },
              modifier = Modifier.fillMaxSize()
            )
          }

          // Preview button overlay
          Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(8.dp)
              .clickable { showPreviewDialog = true }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Fullscreen, contentDescription = "Preview", tint = Color.White, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Preview Full", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }

          IconButton(
            onClick = { selectedImage = null },
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
              .size(28.dp)
              .clip(androidx.compose.foundation.shape.CircleShape)
              .background(Color.Black.copy(alpha = 0.65f))
          ) {
            Icon(Icons.Default.Close, contentDescription = "Remove media", tint = Color.White, modifier = Modifier.size(16.dp))
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Crop / Aspect Ratio bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Crop / Aspect Ratio:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1:1" to "Square", "4:5" to "Portrait", "16:9" to "Wide").forEach { (ratio, label) ->
              FilterChip(
                selected = cropRatio == ratio,
                onClick = { cropRatio = ratio },
                label = { Text("$ratio $label", fontSize = 10.sp) }
              )
            }
          }
        }
      }

      // Google Maps Location Chooser
      Spacer(modifier = Modifier.height(12.dp))
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { showLocationDialog = true }
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = selectedLocation ?: "Add Location with Google Maps",
              fontWeight = if (selectedLocation != null) FontWeight.Bold else FontWeight.Normal,
              fontSize = 12.sp,
              color = if (selectedLocation != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Search city, cafe, or pin on Google Maps",
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          if (selectedLocation != null) {
            IconButton(
              onClick = { selectedLocation = null },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
            }
          } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      // Full Preview Dialog
      if (showPreviewDialog && selectedImage != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showPreviewDialog = false }) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Image Preview ($cropRatio)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(onClick = { showPreviewDialog = false }) {
                  Icon(Icons.Default.Close, contentDescription = "Close")
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color.Black)
              ) {
                AsyncImage(
                  model = selectedImage,
                  contentDescription = "Full preview",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }
        }
      }

      // Google Maps Location Picker Modal
      if (showLocationDialog) {
        AlertDialog(
          onDismissRequest = { showLocationDialog = false },
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFFF5252))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Google Maps Location", fontWeight = FontWeight.Bold)
            }
          },
          text = {
            var geocodedList by remember { mutableStateOf<List<String>>(emptyList()) }
            var isSearching by remember { mutableStateOf(false) }

            LaunchedEffect(locationSearchQuery) {
              if (locationSearchQuery.trim().length >= 3) {
                isSearching = true
                try {
                  val geocoder = android.location.Geocoder(context)
                  val addresses: List<Address> = withContext(Dispatchers.IO) {
                    try {
                      geocoder.getFromLocationName(locationSearchQuery, 5) ?: emptyList<Address>()
                    } catch (e: Exception) {
                      emptyList<Address>()
                    }
                  }
                  geocodedList = addresses.mapNotNull { addr: Address ->
                    val name = addr.featureName ?: addr.thoroughfare ?: addr.locality ?: ""
                    val city = addr.locality ?: addr.adminArea ?: ""
                    val country = addr.countryName ?: ""
                    val label = listOfNotNull(name.ifBlank { null }, city.ifBlank { null }, country.ifBlank { null }).distinct().joinToString(", ")
                    if (label.isNotBlank()) "📍 $label (${String.format(java.util.Locale.US, "%.4f, %.4f", addr.latitude, addr.longitude)})" else null
                  }
                } catch (_: Exception) {}
                isSearching = false
              } else {
                geocodedList = emptyList()
              }
            }

            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedTextField(
                value = locationSearchQuery,
                onValueChange = { locationSearchQuery = it },
                placeholder = { Text("Search any city, cafe, or landmark...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )

              if (locationSearchQuery.isNotBlank()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = if (isSearching) "Searching real coordinates..." else if (geocodedList.isNotEmpty()) "Matched Locations (${geocodedList.size}):" else "Search Result:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeViolet
                  )
                  TextButton(
                    onClick = {
                      val mapUri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(locationSearchQuery)}")
                      val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, mapUri).apply {
                        setPackage("com.google.android.apps.maps")
                      }
                      try {
                        context.startActivity(mapIntent)
                      } catch (e: Exception) {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, mapUri))
                      }
                    }
                  ) {
                    Text("View in Google Maps 🗺️", fontSize = 11.sp)
                  }
                }
              }

              val displayList = if (geocodedList.isNotEmpty()) geocodedList else listOf(
                "📍 Bandra West, Mumbai (19.0596, 72.8295)",
                "📍 Indiranagar, Bengaluru (12.9784, 77.6408)",
                "📍 Hauz Khas Social, Delhi (28.5494, 77.2001)",
                "📍 Cyber Hub, Gurugram (28.4950, 77.0895)",
                "📍 Koregaon Park, Pune (18.5362, 73.8940)"
              )

              displayList.forEach { spot ->
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      selectedLocation = spot
                      showLocationDialog = false
                    }
                ) {
                  Text(spot, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
              }
            }
          },
          confirmButton = {
            if (locationSearchQuery.isNotBlank()) {
              Button(
                onClick = {
                  selectedLocation = "📍 ${locationSearchQuery.trim()}"
                  showLocationDialog = false
                }
              ) {
                Text("Select Location")
              }
            }
          },
          dismissButton = {
            TextButton(onClick = { showLocationDialog = false }) { Text("Cancel") }
          }
        )
      }

      Spacer(modifier = Modifier.height(12.dp))
      Text("Or pick quick vibe preset:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(samplePhotos) { photoUrl ->
          val isSelected = selectedImage == photoUrl
          Box(
            modifier = Modifier
              .size(68.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { selectedImage = if (isSelected) null else photoUrl }
          ) {
            AsyncImage(
              model = photoUrl,
              contentDescription = "Preset photo",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
            if (isSelected) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(VybeViolet.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      VybeGradientButton(
        text = if (publishDestination == PublishDestination.FEED_POST) "Post to VYBE Feed" else "Send & Publish VYBES Reel",
        onClick = {
          if (caption.isNotBlank() || selectedImage != null) {
            viewModel.createPost(
              caption = caption,
              mood = selectedMood,
              imageUrl = selectedImage,
              mediaType = if (publishDestination == PublishDestination.VYBES_REEL) MediaType.REEL else selectedMediaType
            )
            viewModel.navigateBack()
          }
        },
        enabled = caption.isNotBlank() || selectedImage != null,
        modifier = Modifier.testTag("publish_bottom_button")
      )
    }
  }
}

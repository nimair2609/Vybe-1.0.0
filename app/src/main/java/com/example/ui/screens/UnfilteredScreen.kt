package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.MoodType
import com.example.data.model.Post
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import com.example.util.MediaUtils

/**
 * VYBE UNFILTERED 2.0
 * Product philosophy: "Back to Real. Back to Us."
 * - Real phone camera capture & gallery photo picker.
 * - Everyday raw authentic moments.
 * - Zero beauty filters, zero face reshaping, zero skin smoothing.
 * - Preview, retake, caption, mood selection, and publishing.
 * - NO Like button, NO Share button, NO engagement popularity rankings.
 * - Conversation-first comments and authentic connections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnfilteredScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val unfilteredPosts by viewModel.unfilteredPosts.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var showCreateSheet by remember { mutableStateOf(false) }
  var postCaption by remember { mutableStateOf("") }
  var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var pickedUri by remember { mutableStateOf<Uri?>(null) }
  var selectedMood by remember { mutableStateOf<MoodType?>(MoodType.CHILL) }
  var activeCommentPostId by remember { mutableStateOf<String?>(null) }
  var commentText by remember { mutableStateOf("") }

  // Real Camera Launcher
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap: Bitmap? ->
    if (bitmap != null) {
      capturedBitmap = bitmap
      pickedUri = null
      showCreateSheet = true
    }
  }

  // Gallery Visual Media Picker
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      pickedUri = uri
      capturedBitmap = null
      showCreateSheet = true
    }
  }

  val launchCameraSafely = {
    try {
      cameraLauncher.launch(null)
    } catch (_: Exception) {
      photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("unfiltered_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text("VYBE UNFILTERED", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("Back to Real. Back to Us.", fontSize = 11.sp, color = VybeBrandCyan, fontWeight = FontWeight.Bold)
          }
        },
        actions = {
          // Camera button: Opens real device camera safely
          IconButton(
            onClick = { launchCameraSafely() },
            modifier = Modifier.testTag("unfiltered_camera_button")
          ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "Take Raw Camera Photo", tint = VybeViolet)
          }
          // Gallery button: Opens photo picker
          IconButton(
            onClick = {
              photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            modifier = Modifier.testTag("unfiltered_gallery_button")
          ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick Raw Photo from Gallery")
          }
          FilledTonalButton(
            onClick = { launchCameraSafely() },
            modifier = Modifier.padding(end = 8.dp).testTag("unfiltered_create_button")
          ) {
            Text("Capture", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Philosophy Banner
      item {
        Spacer(modifier = Modifier.height(4.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(16.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("Authentic Life • Zero Retouching", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Text(
                text = "Capture honest unedited moments with your camera. No likes, no popularity metrics, only pure conversations.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      // Quick Action Camera Card
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = VybeVioletDark.copy(alpha = 0.3f)),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Ready to show real life?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
              Text("Tap camera to snap with no face smoothing or beauty filters.", fontSize = 11.sp, color = VybeBrandCyan)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { launchCameraSafely() },
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Camera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
              OutlinedButton(
                onClick = {
                  photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Text("Gallery", fontSize = 12.sp)
              }
            }
          }
        }
      }

      items(unfilteredPosts, key = { it.id }) { post ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
          modifier = Modifier.fillMaxWidth().testTag("unfiltered_post_${post.id}")
        ) {
          Column {
            // Header
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                VybeAvatar(user = post.author, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(post.author.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                  Text("@${post.author.username} • ${post.timestamp}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              if (post.moodZone != null) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = post.moodZone.accentColor.copy(alpha = 0.2f)
                ) {
                  Text(
                    text = "${post.moodZone.emoji} ${post.moodZone.title}",
                    color = post.moodZone.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
            }

            // Raw Photo
            if (post.mediaUrls.isNotEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(300.dp)
              ) {
                AsyncImage(
                  model = post.mediaUrls.first(),
                  contentDescription = "Unfiltered moment",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
                // RAW watermark indicator
                Surface(
                  color = Color.Black.copy(alpha = 0.65f),
                  shape = RoundedCornerShape(6.dp),
                  modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.BottomEnd)
                ) {
                  Text(
                    text = "RAW CAPTURE • 0% FILTERS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                  )
                }
              }
            }

            // Caption
            if (post.caption.isNotBlank()) {
              Text(
                text = post.caption,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
              )
            }

            // No Like button. Only genuine comments
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              TextButton(
                onClick = {
                  activeCommentPostId = if (activeCommentPostId == post.id) null else post.id
                }
              ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Conversation (${post.comments.size})", fontSize = 12.sp)
              }
              Text("No Likes • Only Real Talk", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Comment Box
            if (activeCommentPostId == post.id) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                  .padding(12.dp)
              ) {
                post.comments.forEach { c ->
                  Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(c.author.displayName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = VybeViolet)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(c.content, fontSize = 11.sp)
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add honest thought...", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  IconButton(
                    onClick = {
                      if (commentText.isNotBlank()) {
                        viewModel.addUnfilteredComment(post.id, commentText)
                        commentText = ""
                      }
                    },
                    enabled = commentText.isNotBlank()
                  ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = VybeViolet)
                  }
                }
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(60.dp))
      }
    }
  }

  // Raw Capture Publishing Full-Screen Page
  if (showCreateSheet) {
    val context = LocalContext.current

    Dialog(
      onDismissRequest = {
        showCreateSheet = false
        capturedBitmap = null
        pickedUri = null
      },
      properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true
      )
    ) {
      Scaffold(
        topBar = {
          TopAppBar(
            navigationIcon = {
              IconButton(
                onClick = {
                  showCreateSheet = false
                  capturedBitmap = null
                  pickedUri = null
                }
              ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
              }
            },
            title = {
              Column {
                Text("Publish UNFILTERED", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Zero retouches • Pure reality", fontSize = 11.sp, color = VybeBrandCyan)
              }
            },
            actions = {
              Button(
                onClick = {
                  val realMediaUrl = pickedUri?.toString()
                    ?: (capturedBitmap?.let { bmp ->
                      MediaUtils.saveBitmapToCache(context, bmp, "unfilter_raw")?.toString()
                    })
                    ?: "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=600"

                  viewModel.createUnfilteredPost(postCaption.trim(), realMediaUrl)
                  postCaption = ""
                  capturedBitmap = null
                  pickedUri = null
                  showCreateSheet = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .testTag("unfiltered_publish_button")
              ) {
                Text("Publish", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            )
          )
        }
      ) { innerPadding ->
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Preview of captured photo or picked media
          item {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
              contentAlignment = Alignment.Center
            ) {
              if (capturedBitmap != null) {
                Image(
                  bitmap = capturedBitmap!!.asImageBitmap(),
                  contentDescription = "Captured Raw Camera Preview",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              } else if (pickedUri != null) {
                AsyncImage(
                  model = pickedUri,
                  contentDescription = "Picked Gallery Preview",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                Text("No photo selected", color = Color.White, fontSize = 13.sp)
              }

              // RAW watermark indicator
              Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .padding(12.dp)
                  .align(Alignment.BottomEnd)
              ) {
                Text(
                  text = "AUTHENTIC RAW CAPTURE",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color.White,
                  letterSpacing = 1.sp,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }

          // Retake / Reselect actions
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedButton(
                onClick = { launchCameraSafely() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp), tint = VybeViolet)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retake Camera", fontSize = 12.sp)
              }
              OutlinedButton(
                onClick = {
                  photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("From Gallery", fontSize = 12.sp)
              }
            }
          }

          // Mood Zone selection
          item {
            Text("Select Current Mood Zone", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(MoodType.values()) { mood ->
                FilterChip(
                  selected = selectedMood == mood,
                  onClick = { selectedMood = mood },
                  label = { Text("${mood.emoji} ${mood.title}", fontSize = 11.sp) }
                )
              }
            }
          }

          // Caption Input
          item {
            OutlinedTextField(
              value = postCaption,
              onValueChange = { postCaption = it },
              label = { Text("Add caption (Optional)") },
              placeholder = { Text("What happened just now? No show-off, just life...", fontSize = 13.sp) },
              minLines = 3,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("unfiltered_caption_input"),
              shape = RoundedCornerShape(14.dp)
            )
          }

          item {
            Surface(
              color = VybeVioletDark.copy(alpha = 0.35f),
              shape = RoundedCornerShape(12.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.3f))
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("0% Filter Policy Enforced", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                  Text(
                    text = "Images are published exactly as captured. No skin smoothing, reshaping, or deceptive alterations.",
                    fontSize = 11.sp,
                    color = VybeBrandCyan,
                    lineHeight = 15.sp
                  )
                }
              }
            }
          }

          item {
            Button(
              onClick = {
                val realMediaUrl = pickedUri?.toString()
                  ?: (capturedBitmap?.let { bmp ->
                    MediaUtils.saveBitmapToCache(context, bmp, "unfilter_raw")?.toString()
                  })
                  ?: "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=600"

                viewModel.createUnfilteredPost(postCaption.trim(), realMediaUrl)
                postCaption = ""
                capturedBitmap = null
                pickedUri = null
                showCreateSheet = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("unfiltered_publish_primary_button")
            ) {
              Text("Publish Raw Moment", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
          }

          item {
            Spacer(modifier = Modifier.height(30.dp))
          }
        }
      }
    }
  }
}

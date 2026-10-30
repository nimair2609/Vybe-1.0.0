package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.MoodType
import com.example.data.repository.UploadStatus
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeMoodOutlineIcon
import com.example.ui.theme.*
import com.example.util.MediaUtils
import com.example.util.rememberRelativeTimestamp
import kotlinx.coroutines.launch

/**
 * VYBE UNFILTERED 2.0
 * Product philosophy: "Back to Real. Back to Us."
 * - Strictly live phone camera capture ONLY (no gallery uploads).
 * - Full-resolution hardware camera output (`ActivityResultContracts.TakePicture`) with zero resizing,
 *   zero re-encoding, zero beauty filters, and zero artificial compression.
 * - Preview, retake, caption, mood selection, and verified publishing.
 * - NO Like button, NO Share button, NO engagement popularity rankings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnfilteredScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val isDark = LocalIsDarkTheme.current

  BackHandler {
    viewModel.navigateBack()
  }

  LaunchedEffect(Unit) {
    viewModel.refreshAllData()
  }

  val unfilteredPosts by viewModel.unfilteredPosts.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val uploadStatus by viewModel.uploadStatus.collectAsState()

  var showCreateSheet by remember { mutableStateOf(false) }
  var postCaption by remember { mutableStateOf("") }
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
  var capturedCameraUri by remember { mutableStateOf<Uri?>(null) }
  var cameraErrorNotice by remember { mutableStateOf<String?>(null) }
  var publishError by remember { mutableStateOf<String?>(null) }
  var isPublishingRaw by remember { mutableStateOf(false) }
  var selectedMood by remember { mutableStateOf<MoodType?>(MoodType.CHILL) }
  var activeCommentPostId by remember { mutableStateOf<String?>(null) }
  var commentText by remember { mutableStateOf("") }

  // Full-resolution direct file capture — preserves 100% original camera bytes without thumbnail downscaling
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success: Boolean ->
    val outputUri = pendingCameraUri
    if (success && outputUri != null) {
      capturedCameraUri = outputUri
      cameraErrorNotice = null
      publishError = null
      showCreateSheet = true
    }
  }

  fun startRawCameraCapture() {
    try {
      val (_, targetUri) = MediaUtils.createTempCameraCaptureFile(context, "unfiltered_raw")
      pendingCameraUri = targetUri
      cameraErrorNotice = null
      cameraLauncher.launch(targetUri)
    } catch (e: Exception) {
      cameraErrorNotice = "Unable to launch device camera. Please verify your device camera is available."
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      startRawCameraCapture()
    } else {
      cameraErrorNotice = "Camera permission is required for UNFILTERED. Gallery uploads are disabled to keep every moment 100% live and authentic."
    }
  }

  val launchCameraSafely = {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      startRawCameraCapture()
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  fun executeRawPublish() {
    if (isPublishingRaw) return
    val rawUriString = capturedCameraUri?.toString().orEmpty()
    if (rawUriString.isBlank()) {
      publishError = "Capture a live camera photo before publishing."
      return
    }
    isPublishingRaw = true
    publishError = null
    coroutineScope.launch {
      val res = viewModel.publishUnfilteredPostAwait(
        caption = postCaption.trim(),
        rawCameraUri = rawUriString,
        mood = selectedMood
      )
      isPublishingRaw = false
      res.onSuccess {
        postCaption = ""
        capturedCameraUri = null
        pendingCameraUri = null
        publishError = null
        showCreateSheet = false
        viewModel.postBannerMessage("Unfiltered camera moment published in original quality!")
      }.onFailure { err ->
        publishError = err.message ?: "Failed to publish raw moment. Check your connection and try again."
      }
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.size(48.dp).testTag("unfiltered_back_button")
          ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text("VYBE UNFILTERED", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(
              "Back to Real. Back to Us.",
              fontSize = 11.sp,
              color = if (isDark) VybeBrandCyan else VybeViolet,
              fontWeight = FontWeight.Bold
            )
          }
        },
        actions = {
          IconButton(
            onClick = { launchCameraSafely() },
            modifier = Modifier.size(48.dp).testTag("unfiltered_camera_button")
          ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = "Take Raw Camera Photo", tint = VybeViolet)
          }
          FilledTonalButton(
            onClick = { launchCameraSafely() },
            modifier = Modifier
              .padding(end = 8.dp)
              .heightIn(min = 40.dp)
              .testTag("unfiltered_create_button")
          ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
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
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (cameraErrorNotice != null) {
        item {
          Surface(
            color = VybeDanger.copy(alpha = 0.14f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = cameraErrorNotice.orEmpty(),
                color = VybeDanger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

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
            Icon(
              Icons.Default.CameraAlt,
              contentDescription = null,
              tint = if (isDark) VybeBrandCyan else VybeViolet,
              modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                "Live Camera Only • Original Quality",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "UNFILTERED captures live camera photos at native sensor quality — no gallery uploads, no filters, no likes.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      // Quick Action Live Camera Card (Camera-Only)
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (isDark) VybeVioletDark.copy(alpha = 0.3f) else VybeViolet.copy(alpha = 0.09f)
          ),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
              Text(
                "Ready to show real life?",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                "Snap directly from your camera with zero retouching or compression.",
                fontSize = 11.sp,
                color = if (isDark) VybeBrandCyan else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Button(
              onClick = { launchCameraSafely() },
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              modifier = Modifier.heightIn(min = 48.dp).testTag("unfiltered_open_camera_cta")
            ) {
              Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Open Camera", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }

      items(unfilteredPosts, key = { it.id }) { post ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
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
                  val relativePostTime = rememberRelativeTimestamp(post.createdAtIso, post.timestamp)
                  Text(
                    post.author.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    "@${post.author.username} • $relativePostTime",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.moodZone != null) {
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = post.moodZone.accentColor.copy(alpha = 0.2f)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      VybeMoodOutlineIcon(
                        mood = post.moodZone,
                        tint = post.moodZone.accentColor,
                        size = 13.dp
                      )
                      Text(
                        text = post.moodZone.title,
                        color = post.moodZone.accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }
                if (currentUser?.id == post.author.id) {
                  Spacer(modifier = Modifier.width(6.dp))
                  IconButton(
                    onClick = { viewModel.deletePost(post.id) },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("delete_unfiltered_post_${post.id}")
                  ) {
                    Icon(
                      Icons.Outlined.DeleteOutline,
                      contentDescription = "Delete raw post",
                      tint = VybeDanger,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }

            // Raw Photo — natural aspect ratio preserved without cropping distortion
            if (post.mediaUrls.isNotEmpty()) {
              val ratio = (post.mediaAspectRatio ?: 0.75f).coerceIn(0.55f, 1.8f)
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(ratio)
                  .background(Color.Black)
              ) {
                AsyncImage(
                  model = post.mediaUrls.first(),
                  contentDescription = "Unfiltered moment",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier.fillMaxSize()
                )
                Surface(
                  color = Color.Black.copy(alpha = 0.65f),
                  shape = RoundedCornerShape(6.dp),
                  modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.BottomEnd)
                ) {
                  Text(
                    text = "RAW CAMERA • ORIGINAL QUALITY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                  )
                }
              }
            }

            if (post.caption.isNotBlank()) {
              Text(
                text = post.caption,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
              )
            }

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

            if (activeCommentPostId == post.id) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                  .padding(12.dp)
              ) {
                post.comments.forEach { c ->
                  val relativeCommentTime = rememberRelativeTimestamp(c.createdAtIso, c.timestamp)
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(c.author.displayName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = VybeViolet)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(c.content, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(relativeCommentTime, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = VybeViolet)
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

  // Raw Camera Capture Publishing Full-Screen Sheet (Camera-Only, Verified Upload)
  if (showCreateSheet) {
    Dialog(
      onDismissRequest = {
        if (!isPublishingRaw) {
          showCreateSheet = false
          capturedCameraUri = null
          publishError = null
        }
      },
      properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = !isPublishingRaw
      )
    ) {
      Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
          TopAppBar(
            navigationIcon = {
              IconButton(
                onClick = {
                  if (!isPublishingRaw) {
                    showCreateSheet = false
                    capturedCameraUri = null
                    publishError = null
                  }
                },
                enabled = !isPublishingRaw,
                modifier = Modifier.size(48.dp)
              ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close")
              }
            },
            title = {
              Column {
                Text("Publish UNFILTERED", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                  "Original camera resolution • 0% filters",
                  fontSize = 11.sp,
                  color = if (isDark) VybeBrandCyan else VybeViolet
                )
              }
            },
            actions = {
              Button(
                onClick = { executeRawPublish() },
                enabled = !isPublishingRaw && capturedCameraUri != null,
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .heightIn(min = 40.dp)
                  .testTag("unfiltered_publish_button")
              ) {
                if (isPublishingRaw) {
                  CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                } else {
                  Text("Publish", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
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
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          if (publishError != null) {
            item {
              Surface(
                color = VybeDanger.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = publishError.orEmpty(),
                    color = VybeDanger,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }

          if (isPublishingRaw) {
            item {
              val statusLabel = when (val st = uploadStatus) {
                is UploadStatus.Uploading -> "${st.label} (${st.progressPercent}%)"
                is UploadStatus.Processing -> st.label
                else -> "Uploading original camera photo..."
              }
              Surface(
                color = VybeViolet.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(statusLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                  LinearProgressIndicator(color = VybeViolet, modifier = Modifier.fillMaxWidth())
                }
              }
            }
          }

          item {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
              contentAlignment = Alignment.Center
            ) {
              if (capturedCameraUri != null) {
                AsyncImage(
                  model = capturedCameraUri,
                  contentDescription = "Captured Raw Camera Preview",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                Text("No camera photo captured yet", color = Color.White, fontSize = 13.sp)
              }

              Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .padding(12.dp)
                  .align(Alignment.BottomEnd)
              ) {
                Text(
                  text = "AUTHENTIC CAMERA CAPTURE • UNCOMPRESSED",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color.White,
                  letterSpacing = 0.8.sp,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }

          // Retake Camera ONLY (Gallery strictly omitted)
          item {
            OutlinedButton(
              onClick = { launchCameraSafely() },
              enabled = !isPublishingRaw,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("unfiltered_retake_camera_button"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp), tint = VybeViolet)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Retake Live Camera Photo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
          }

          item {
            Text("Select Current Mood Zone", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(MoodType.values()) { mood ->
                FilterChip(
                  selected = selectedMood == mood,
                  onClick = { selectedMood = mood },
                  leadingIcon = {
                    VybeMoodOutlineIcon(
                      mood = mood,
                      tint = if (selectedMood == mood) mood.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                      size = 14.dp
                    )
                  },
                  label = { Text(mood.title, fontSize = 11.sp) }
                )
              }
            }
          }

          item {
            OutlinedTextField(
              value = postCaption,
              onValueChange = { postCaption = it },
              enabled = !isPublishingRaw,
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
              color = if (isDark) VybeVioletDark.copy(alpha = 0.35f) else VybeViolet.copy(alpha = 0.09f),
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.3f))
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  Icons.Outlined.Lock,
                  contentDescription = null,
                  tint = if (isDark) VybeBrandCyan else VybeViolet,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    "0% Filter & Original Camera Quality Enforced",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "Photos are uploaded directly from the camera output file without downscaling, re-compression, or gallery imports.",
                    fontSize = 11.sp,
                    color = if (isDark) VybeBrandCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                  )
                }
              }
            }
          }

          item {
            Button(
              onClick = { executeRawPublish() },
              enabled = !isPublishingRaw && capturedCameraUri != null,
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("unfiltered_publish_primary_button")
            ) {
              Text(
                if (isPublishingRaw) "Publishing Original Capture..." else "Publish Raw Moment",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
              )
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

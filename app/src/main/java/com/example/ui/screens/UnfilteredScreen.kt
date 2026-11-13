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
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeMoodOutlineIcon
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
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
@Composable
fun UnfilteredScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val palette = LocalVybePalette.current

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

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

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
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = "VYBE Unfiltered",
        subtitle = "Back to Real. Back to Us.",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "unfiltered_back_button",
        actions = {
          IconButton(
            onClick = { launchCameraSafely() },
            modifier = Modifier
              .size(48.dp)
              .testTag("unfiltered_camera_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.PhotoCamera,
              contentDescription = "Take Raw Camera Photo",
              tint = palette.accent
            )
          }
          Button(
            onClick = { launchCameraSafely() },
            colors = ButtonDefaults.buttonColors(
              containerColor = palette.accent,
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .heightIn(min = 40.dp)
              .testTag("unfiltered_create_button")
          ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Capture",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
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
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(top = 14.dp, bottom = 72.dp)
    ) {
      if (cameraErrorNotice != null) {
        item {
          VybeSurfaceCard(
            containerColor = palette.danger.copy(alpha = 0.12f),
            borderColor = palette.danger.copy(alpha = 0.4f),
            cornerRadius = VybeTokens.Radius.md,
            contentPadding = PaddingValues(12.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = palette.danger, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = cameraErrorNotice.orEmpty(),
                color = palette.danger,
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
        VybeSurfaceCard(
          containerColor = palette.elevated,
          contentPadding = PaddingValues(14.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .background(palette.accent.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Live Camera Only • Original Quality",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "UNFILTERED captures live camera photos at native sensor quality — no gallery uploads, no filters, no likes.",
                fontSize = 12.sp,
                color = palette.textSecondary,
                lineHeight = 16.sp
              )
            }
          }
        }
      }

      // Quick Action Live Camera Card (Camera-Only)
      item {
        VybeSurfaceCard(
          containerColor = palette.accent.copy(alpha = 0.10f),
          borderColor = palette.accent.copy(alpha = 0.35f),
          contentPadding = PaddingValues(14.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
              Text(
                text = "Ready to show real life?",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Snap directly from your camera with zero retouching or compression.",
                fontSize = 12.sp,
                color = palette.textSecondary,
                lineHeight = 16.sp
              )
            }
            VybePrimaryButton(
              text = "Open Camera",
              onClick = { launchCameraSafely() },
              icon = Icons.Default.PhotoCamera,
              testTag = "unfiltered_open_camera_cta"
            )
          }
        }
      }

      if (unfilteredPosts.isEmpty()) {
        item {
          VybeEmptyState(
            icon = Icons.Outlined.PhotoCamera,
            title = "No Unfiltered Moments Yet",
            subtitle = "Capture the first unedited, live-camera moment right now.",
            actionLabel = "Open Live Camera",
            onAction = { launchCameraSafely() }
          )
        }
      }

      items(unfilteredPosts, key = { it.id }) { post ->
        VybeSurfaceCard(
          contentPadding = PaddingValues(0.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("unfiltered_post_${post.id}")
        ) {
          // Header
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              VybeAvatar(user = post.author, size = 38.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                val relativePostTime = rememberRelativeTimestamp(post.createdAtIso, post.timestamp)
                Text(
                  text = post.author.displayName,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = palette.textPrimary
                )
                Text(
                  text = "@${post.author.username} • $relativePostTime",
                  fontSize = 11.sp,
                  color = palette.textSecondary
                )
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (post.moodZone != null) {
                Surface(
                  shape = RoundedCornerShape(VybeTokens.Radius.sm),
                  color = post.moodZone.accentColor.copy(alpha = 0.16f),
                  border = BorderStroke(1.dp, post.moodZone.accentColor.copy(alpha = 0.35f))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                  onClick = { viewModel.deletePost(post.id) },
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_unfiltered_post_${post.id}")
                ) {
                  Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete raw post",
                    tint = palette.danger,
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
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(VybeTokens.Radius.xs),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
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
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
              }
            }
          }

          if (post.caption.isNotBlank()) {
            Text(
              text = post.caption,
              fontSize = 13.sp,
              lineHeight = 19.sp,
              color = palette.textPrimary,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
          }

          HorizontalDivider(thickness = 0.8.dp, color = palette.border)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TextButton(
              onClick = {
                activeCommentPostId = if (activeCommentPostId == post.id) null else post.id
              },
              modifier = Modifier.heightIn(min = 48.dp)
            ) {
              Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Conversation (${post.comments.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary
              )
            }
            Text(
              text = "No Likes • Only Real Talk",
              fontSize = 11.sp,
              color = palette.textMuted
            )
          }

          if (activeCommentPostId == post.id) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .background(palette.elevated)
                .padding(14.dp)
            ) {
              post.comments.forEach { c ->
                val relativeCommentTime = rememberRelativeTimestamp(c.createdAtIso, c.timestamp)
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = c.author.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = palette.accent
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = c.content,
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(relativeCommentTime, fontSize = 10.sp, color = palette.textMuted)
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedTextField(
                  value = commentText,
                  onValueChange = { commentText = it },
                  placeholder = { Text("Add honest thought...", fontSize = 12.sp, color = palette.textMuted) },
                  colors = fieldColors,
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
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
                  enabled = commentText.isNotBlank(),
                  modifier = Modifier.size(48.dp)
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Send",
                    tint = if (commentText.isNotBlank()) palette.accent else palette.textMuted
                  )
                }
              }
            }
          }
        }
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
        containerColor = palette.canvas,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
          VybeSubScreenTopBar(
            title = "Publish Unfiltered",
            subtitle = "Original camera resolution • 0% filters",
            onBackClick = {
              if (!isPublishingRaw) {
                showCreateSheet = false
                capturedCameraUri = null
                publishError = null
              }
            },
            actions = {
              Button(
                onClick = { executeRawPublish() },
                enabled = !isPublishingRaw && capturedCameraUri != null,
                colors = ButtonDefaults.buttonColors(
                  containerColor = palette.accent,
                  contentColor = Color.White
                ),
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .heightIn(min = 40.dp)
                  .testTag("unfiltered_publish_button")
              ) {
                if (isPublishingRaw) {
                  CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                } else {
                  Text(
                    text = "Publish",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                  )
                }
              }
            }
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
          verticalArrangement = Arrangement.spacedBy(14.dp),
          contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp)
        ) {
          if (publishError != null) {
            item {
              VybeSurfaceCard(
                containerColor = palette.danger.copy(alpha = 0.12f),
                borderColor = palette.danger.copy(alpha = 0.4f),
                cornerRadius = VybeTokens.Radius.md,
                contentPadding = PaddingValues(12.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = palette.danger, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = publishError.orEmpty(),
                    color = palette.danger,
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
              VybeSurfaceCard(
                containerColor = palette.accent.copy(alpha = 0.12f),
                borderColor = palette.accent.copy(alpha = 0.35f),
                cornerRadius = VybeTokens.Radius.md,
                contentPadding = PaddingValues(12.dp)
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(statusLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                  LinearProgressIndicator(
                    color = palette.accent,
                    trackColor = palette.elevated,
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              }
            }
          }

          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.lg))
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
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(VybeTokens.Radius.xs),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
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
            VybeSecondaryButton(
              text = "Retake Live Camera Photo",
              onClick = { launchCameraSafely() },
              enabled = !isPublishingRaw,
              icon = Icons.Outlined.PhotoCamera,
              testTag = "unfiltered_retake_camera_button",
              modifier = Modifier.fillMaxWidth()
            )
          }

          item {
            Text(
              text = "Select Current Mood Zone",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(MoodType.values()) { mood ->
                val isSelected = selectedMood == mood
                FilterChip(
                  selected = isSelected,
                  onClick = { selectedMood = mood },
                  leadingIcon = {
                    VybeMoodOutlineIcon(
                      mood = mood,
                      tint = if (isSelected) mood.accentColor else palette.textSecondary,
                      size = 14.dp
                    )
                  },
                  label = {
                    Text(
                      mood.title,
                      fontSize = 12.sp,
                      color = if (isSelected) palette.textPrimary else palette.textSecondary
                    )
                  },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                    containerColor = palette.elevated
                  )
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
              placeholder = { Text("What happened just now? No show-off, just life...", fontSize = 13.sp, color = palette.textMuted) },
              colors = fieldColors,
              minLines = 3,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("unfiltered_caption_input"),
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            VybeSurfaceCard(
              containerColor = palette.elevated,
              borderColor = palette.accent.copy(alpha = 0.3f),
              cornerRadius = VybeTokens.Radius.md,
              contentPadding = PaddingValues(12.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Outlined.Lock,
                  contentDescription = null,
                  tint = palette.accent,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "0% Filter & Original Camera Quality Enforced",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = palette.textPrimary
                  )
                  Text(
                    text = "Photos are uploaded directly from the camera output file without downscaling, re-compression, or gallery imports.",
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    lineHeight = 15.sp
                  )
                }
              }
            }
          }

          item {
            VybePrimaryButton(
              text = if (isPublishingRaw) "Publishing Original Capture..." else "Publish Raw Moment",
              onClick = { executeRawPublish() },
              enabled = !isPublishingRaw && capturedCameraUri != null,
              isLoading = isPublishingRaw,
              testTag = "unfiltered_publish_primary_button",
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }
  }
}

package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeViolet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStudioScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val currentUser by viewModel.currentUser.collectAsState()
  val activeStream by viewModel.activeLiveStream.collectAsState()
  val comments by viewModel.liveStreamComments.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  val isViewerMode = activeStream != null && activeStream?.host?.id != currentUser?.id
  val isHostBroadcasting = activeStream != null && activeStream?.host?.id == currentUser?.id && activeStream?.isLive == true

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }
  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    hasCameraPermission = perms[Manifest.permission.CAMERA] ?: hasCameraPermission
    hasMicPermission = perms[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
  }

  var streamTitle by remember { mutableStateOf("") }
  var streamCategory by remember { mutableStateOf("LiveZone") }
  var startingBroadcast by remember { mutableStateOf(false) }
  var startError by remember { mutableStateOf<String?>(null) }
  var isFrontCamera by remember { mutableStateOf(true) }
  var isMuted by remember { mutableStateOf(false) }
  var isVideoPaused by remember { mutableStateOf(false) }
  var showEndConfirmDialog by remember { mutableStateOf(false) }

  var commentInput by remember { mutableStateOf("") }
  val commentListState = rememberLazyListState()

  // Auto-scroll comments when new real comments arrive from Supabase
  LaunchedEffect(comments.size) {
    if (comments.isNotEmpty()) {
      commentListState.animateScrollToItem(comments.size - 1)
    }
  }

  // Decode remote host video frame in Viewer Mode
  val remoteLiveBitmap = remember(activeStream?.latestFrameBase64) {
    val raw = activeStream?.latestFrameBase64
    if (raw.isNullOrBlank()) null else {
      try {
        val clean = raw.substringAfter("base64,", raw)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
      } catch (_: Throwable) {
        null
      }
    }
  }

  // Throttle outgoing host frames to ~1.2fps to keep Supabase light and responsive
  var lastFramePushedAt by remember { mutableLongStateOf(0L) }

  BackHandler {
    when {
      isHostBroadcasting -> showEndConfirmDialog = true
      isViewerMode -> viewModel.leaveOrEndLiveBroadcast()
      else -> viewModel.navigateBack()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0A0E17))
  ) {
    // 1. Video Layer (Viewer remote stream OR Host CameraX viewfinder)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFF1E2640),
              Color(0xFF0F172A),
              Color(0xFF090D16)
            )
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      if (isViewerMode) {
        if (remoteLiveBitmap != null) {
          Image(
            bitmap = remoteLiveBitmap,
            contentDescription = "Live broadcast video",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (activeStream?.host != null) {
              VybeAvatar(user = activeStream!!.host, size = 88.dp)
              Spacer(modifier = Modifier.height(14.dp))
            }
            Text(
              text = if (activeStream?.isLive == false) "Broadcast ended" else "Connecting live video stream…",
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
              fontSize = 14.sp
            )
          }
        }
      } else if (isVideoPaused) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.VideocamOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text("Camera Paused", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
        }
      } else if (!hasCameraPermission) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            tint = VybeViolet,
            modifier = Modifier.size(56.dp)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            "Camera & Mic Access Required",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            "To broadcast live to the VYBE community, grant camera and microphone permissions.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(20.dp))
          Button(
            onClick = {
              permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
          ) {
            Text("Grant Permissions", fontWeight = FontWeight.Bold)
          }
        }
      } else {
        CameraPreviewView(
          isFrontCamera = isFrontCamera,
          onFrameCaptured = { jpegBase64 ->
            if (isHostBroadcasting && !isVideoPaused) {
              val now = System.currentTimeMillis()
              if (now - lastFramePushedAt >= 850L) {
                lastFramePushedAt = now
                viewModel.pushLiveBroadcastFrame(jpegBase64)
              }
            }
          },
          modifier = Modifier.fillMaxSize()
        )
      }
    }

    // 2. Top Header Bar (HUD overlay)
    val displayHost = activeStream?.host ?: currentUser
    val displayTitle = activeStream?.title?.ifBlank { "VYBE Live" }
      ?: streamTitle.ifBlank { "Ready to go live" }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        IconButton(
          onClick = {
            when {
              isHostBroadcasting -> showEndConfirmDialog = true
              isViewerMode -> viewModel.leaveOrEndLiveBroadcast()
              else -> viewModel.navigateBack()
            }
          },
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("live_back_button")
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (displayHost != null) {
          VybeAvatar(user = displayHost, size = 34.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(displayHost.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(displayTitle, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isHostBroadcasting || isViewerMode) {
          Surface(
            color = VybeDanger,
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(Color.White)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("LIVE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
          }

          Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("${activeStream?.viewerCount ?: 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        if (!isViewerMode) {
          IconButton(
            onClick = { isFrontCamera = !isFrontCamera },
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.5f))
              .testTag("flip_camera_button")
          ) {
            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip Camera", tint = Color.White, modifier = Modifier.size(18.dp))
          }
        }
      }
    }

    // 3. Bottom Controls & Real-Time Synced Comments HUD
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .imePadding()
        .padding(16.dp)
    ) {
      if (isHostBroadcasting || isViewerMode) {
        if (comments.isNotEmpty()) {
          LazyColumn(
            state = commentListState,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 180.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            items(comments, key = { it.id }) { c ->
              val isCommentFromHost = c.authorId == activeStream?.host?.id
              Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = c.authorName,
                    color = if (isCommentFromHost) VybeViolet else VybeBrandCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = c.content,
                    color = Color.White,
                    fontSize = 12.sp
                  )
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = commentInput,
            onValueChange = { commentInput = it },
            placeholder = { Text("Comment live...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp) },
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedContainerColor = Color.Black.copy(alpha = 0.5f),
              unfocusedContainerColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("live_comment_input")
          )

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = {
              val clean = commentInput.trim()
              if (clean.isNotBlank()) {
                viewModel.sendLiveBroadcastComment(clean)
                commentInput = ""
              }
            },
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(VybeViolet)
              .testTag("send_live_comment")
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
          }

          if (isHostBroadcasting) {
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
              onClick = { isMuted = !isMuted },
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isMuted) VybeDanger else Color.Black.copy(alpha = 0.5f))
            ) {
              Icon(
                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Mute",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }

            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
              onClick = { isVideoPaused = !isVideoPaused },
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isVideoPaused) VybeDanger else Color.Black.copy(alpha = 0.5f))
            ) {
              Icon(
                if (isVideoPaused) Icons.Default.VideocamOff else Icons.Default.Videocam,
                contentDescription = "Toggle Camera",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }

            Spacer(modifier = Modifier.width(6.dp))
            Button(
              onClick = { showEndConfirmDialog = true },
              colors = ButtonDefaults.buttonColors(containerColor = VybeDanger),
              shape = RoundedCornerShape(20.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
              modifier = Modifier.testTag("end_live_button")
            ) {
              Text("End", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          } else {
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedButton(
              onClick = { viewModel.leaveOrEndLiveBroadcast() },
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.testTag("leave_live_button")
            ) {
              Text("Leave", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          }
        }
      } else {
        // Pre-Live Configuration Card (Host Setup)
        Surface(
          color = Color(0xFF131C2E).copy(alpha = 0.95f),
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("Go Live Across All VYBE Devices", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
              value = streamTitle,
              onValueChange = {
                streamTitle = it
                startError = null
              },
              placeholder = { Text("What are you streaming today?") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("stream_title_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("LiveZone", "Music & Jam", "Talk & Q&A", "Creator Lab").forEach { cat ->
                FilterChip(
                  selected = streamCategory == cat,
                  onClick = { streamCategory = cat },
                  label = { Text(cat, fontSize = 11.sp) }
                )
              }
            }
            if (startError != null) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(startError!!, color = VybeDanger, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = {
                if (hasCameraPermission && hasMicPermission) {
                  if (startingBroadcast) return@Button
                  startingBroadcast = true
                  startError = null
                  viewModel.startLiveBroadcast(
                    title = streamTitle.trim().ifBlank { "${currentUser?.displayName ?: "Creator"} Live" },
                    category = streamCategory
                  )
                  coroutineScope.launch {
                    kotlinx.coroutines.delay(1500)
                    startingBroadcast = false
                  }
                } else {
                  permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                  )
                }
              },
              enabled = !startingBroadcast,
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("start_broadcasting_button")
            ) {
              Icon(Icons.Default.Podcasts, contentDescription = null, tint = Color.White)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                if (startingBroadcast) "Starting Live Stream…" else "Go Live Now",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              )
            }
          }
        }
      }
    }
  }

  if (showEndConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showEndConfirmDialog = false },
      title = { Text("End Live Stream?", fontWeight = FontWeight.Bold) },
      text = { Text("Your broadcast will end across all connected devices.") },
      confirmButton = {
        Button(
          onClick = {
            showEndConfirmDialog = false
            viewModel.leaveOrEndLiveBroadcast()
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("End Stream", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showEndConfirmDialog = false }) {
          Text("Continue Live")
        }
      }
    )
  }
}

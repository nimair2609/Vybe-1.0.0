package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LiveComment(
  val id: String,
  val authorName: String,
  val text: String,
  val timestamp: String = "Just now",
  val isHost: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStudioScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val currentUser by viewModel.currentUser.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  // Permission states
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

  // Live session states
  var isBroadcasting by remember { mutableStateOf(false) }
  var streamTitle by remember { mutableStateOf("Vibing live with community ✨") }
  var isFrontCamera by remember { mutableStateOf(true) }
  var isMuted by remember { mutableStateOf(false) }
  var isVideoPaused by remember { mutableStateOf(false) }
  var viewerCount by remember { mutableStateOf(1) }
  var showEndConfirmDialog by remember { mutableStateOf(false) }

  // Comments stream
  var commentInput by remember { mutableStateOf("") }
  var comments by remember {
    mutableStateOf(
      listOf(
        LiveComment("c_1", "Aarav Beats", "Ayy sound check sounds clean! 🎧"),
        LiveComment("c_2", "Priya Sharma", "Great lighting today! ✨")
      )
    )
  }
  val commentListState = rememberLazyListState()

  BackHandler {
    if (isBroadcasting) {
      showEndConfirmDialog = true
    } else {
      viewModel.navigateBack()
    }
  }

  // Periodic simulated live comments and viewer fluctuations
  LaunchedEffect(isBroadcasting) {
    if (isBroadcasting) {
      viewerCount = 14
      while (isBroadcasting) {
        delay(6000)
        viewerCount = (viewerCount + (-1..3).random()).coerceAtLeast(5)
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0A0E17))
  ) {
    // 1. Camera Viewfinder (Simulated Fullscreen Live View with grid lines and video overlay)
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
      if (isVideoPaused) {
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
        // Real Live CameraX Viewfinder
        val lifecycleOwner = LocalLifecycleOwner.current
        AndroidView(
          factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
              scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
              try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                  it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = if (isFrontCamera) {
                  CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                  CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
              } catch (e: Exception) {
                e.printStackTrace()
              }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
          },
          update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
            cameraProviderFuture.addListener({
              try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                  it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = if (isFrontCamera) {
                  CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                  CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
              } catch (e: Exception) {
                e.printStackTrace()
              }
            }, ContextCompat.getMainExecutor(previewView.context))
          },
          modifier = Modifier.fillMaxSize()
        )
      }
    }

    // 2. Top Header Bar (HUD overlay)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = {
            if (isBroadcasting) showEndConfirmDialog = true else viewModel.navigateBack()
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

        if (currentUser != null) {
          VybeAvatar(user = currentUser!!, size = 34.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(currentUser!!.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(streamTitle, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isBroadcasting) {
          // LIVE Badge
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

          // Viewer Count
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
              Text("$viewerCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Camera flip button
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

    // 3. Bottom Controls & Live Comments HUD
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .imePadding()
        .padding(16.dp)
    ) {
      if (isBroadcasting) {
        // Comments Stream
        LazyColumn(
          state = commentListState,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(comments, key = { it.id }) { c ->
            Surface(
              color = Color.Black.copy(alpha = 0.45f),
              shape = RoundedCornerShape(10.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = c.authorName,
                  color = if (c.isHost) VybeViolet else Color(0xFF64B5F6),
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = c.text,
                  color = Color.White,
                  fontSize = 12.sp
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Comment Input + Actions Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = commentInput,
            onValueChange = { commentInput = it },
            placeholder = { Text("Comment live...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedContainerColor = Color.Black.copy(alpha = 0.5f),
              unfocusedContainerColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier.weight(1f).testTag("live_comment_input")
          )

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = {
              if (commentInput.isNotBlank()) {
                val newC = LiveComment(
                  id = "c_${System.currentTimeMillis()}",
                  authorName = currentUser?.displayName ?: "Host",
                  text = commentInput.trim(),
                  isHost = true
                )
                comments = comments + newC
                commentInput = ""
                coroutineScope.launch {
                  commentListState.animateScrollToItem(comments.size - 1)
                }
              }
            },
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(VybeViolet)
              .testTag("send_live_comment")
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
          }

          Spacer(modifier = Modifier.width(6.dp))

          // Mute toggle
          IconButton(
            onClick = { isMuted = !isMuted },
            modifier = Modifier
              .size(42.dp)
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

          // End Live Button
          Button(
            onClick = { showEndConfirmDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = VybeDanger),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.testTag("end_live_button")
          ) {
            Text("End", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      } else {
        // Pre-Live Configuration Card
        Surface(
          color = Color(0xFF131C2E).copy(alpha = 0.95f),
          shape = RoundedCornerShape(18.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("Set Stream Title:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = streamTitle,
              onValueChange = { streamTitle = it },
              placeholder = { Text("What are you sharing today?") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("stream_title_input")
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = {
                if (hasCameraPermission && hasMicPermission) {
                  isBroadcasting = true
                } else {
                  permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                  )
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_broadcasting_button")
            ) {
              Icon(Icons.Default.Podcasts, contentDescription = null, tint = Color.White)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Go Live Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
          }
        }
      }
    }
  }

  // End Live Confirmation Dialog
  if (showEndConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showEndConfirmDialog = false },
      title = { Text("End Live Stream?", fontWeight = FontWeight.Bold) },
      text = { Text("Your stream will end and viewers will be disconnected.") },
      confirmButton = {
        Button(
          onClick = {
            isBroadcasting = false
            showEndConfirmDialog = false
            viewModel.navigateBack()
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

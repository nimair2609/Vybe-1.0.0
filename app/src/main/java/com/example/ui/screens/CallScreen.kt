package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CallState
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

/**
 * VYBE CALL SCREEN 2.0
 * Real WebRTC & CameraX architecture state handler.
 * Provides live CameraX viewfinder, front/back lens switching, and call controls.
 */
@Composable
fun CallScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.endCall()
  }

  val activeCall by viewModel.activeCall.collectAsState()
  val context = LocalContext.current
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }
  var isFrontCamera by remember { mutableStateOf(true) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
  }

  LaunchedEffect(activeCall?.isVideo) {
    if (activeCall?.isVideo == true && !hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  if (activeCall == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Call ended")
    }
    return
  }

  val call = activeCall!!

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(ObsidianBackground, ObsidianSurface, ObsidianBackground)
        )
      )
  ) {
    // If video call and camera enabled and permission granted, render CameraX preview as background
    if (call.isVideo && !call.isCameraOff && hasCameraPermission) {
      CameraPreviewView(
        isFrontCamera = isFrontCamera,
        modifier = Modifier.fillMaxSize()
      )
      // Subtle dark vignette overlay for legibility
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.35f))
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top info: Call type + status
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
          color = VybeViolet.copy(alpha = 0.85f),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
              contentDescription = null,
              tint = VybeCyan,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (call.isVideo) "VYBE HD Video Call" else "VYBE Encrypted Voice Call",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = when (call.state) {
            CallState.OUTGOING -> "Ringing…"
            CallState.INCOMING -> "Incoming Call…"
            CallState.CONNECTED -> "Connected • 00:42"
            CallState.ENDED -> "Call ended"
            CallState.IDLE -> "Connecting…"
          },
          fontSize = 15.sp,
          fontWeight = FontWeight.Medium,
          color = Color.White
        )
      }

      // Middle: Peer profile & avatar (PiP tile if video on, or centered profile if voice/camera off)
      if (call.isVideo && !call.isCameraOff && hasCameraPermission) {
        // Floating PiP card showing peer avatar
        Surface(
          color = ObsidianSurface.copy(alpha = 0.85f),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.5f)),
          modifier = Modifier
            .padding(16.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            VybeAvatar(user = call.peerUser, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = call.peerUser.displayName,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                VerificationBadge(user = call.peerUser)
              }
              Text(
                text = "@${call.peerUser.username}",
                fontSize = 12.sp,
                color = ObsidianTextSecondary
              )
            }
          }
        }
      } else {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Box(
            modifier = Modifier
              .size(120.dp)
              .clip(CircleShape)
              .background(VybeViolet.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
          ) {
            VybeAvatar(user = call.peerUser, size = 100.dp)
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = call.peerUser.displayName,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            VerificationBadge(user = call.peerUser)
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "@${call.peerUser.username}",
            fontSize = 13.sp,
            color = ObsidianTextSecondary
          )
        }
      }

      // Bottom: Controls (Mute, Camera, Flip, End)
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Mute toggle
          IconButton(
            onClick = { viewModel.toggleCallMute() },
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(if (call.isMuted) VybeDanger else MaterialTheme.colorScheme.surfaceVariant)
              .testTag("call_mute_button")
          ) {
            Icon(
              imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
              contentDescription = "Mute",
              tint = Color.White
            )
          }

          // Camera toggle (if video call)
          if (call.isVideo) {
            IconButton(
              onClick = { viewModel.toggleCallCamera() },
              modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (call.isCameraOff) VybeDanger else MaterialTheme.colorScheme.surfaceVariant)
                .testTag("call_camera_button")
            ) {
              Icon(
                imageVector = if (call.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                contentDescription = "Camera",
                tint = Color.White
              )
            }

            // Flip camera lens button
            IconButton(
              onClick = { isFrontCamera = !isFrontCamera },
              modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("call_flip_camera_button")
            ) {
              Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Flip Camera",
                tint = Color.White
              )
            }
          }

          // End call
          IconButton(
            onClick = { viewModel.endCall() },
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .background(VybeDanger)
              .testTag("call_end_button")
          ) {
            Icon(
              imageVector = Icons.Default.CallEnd,
              contentDescription = "End Call",
              tint = Color.White,
              modifier = Modifier.size(28.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun CameraPreviewView(
  isFrontCamera: Boolean,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  AndroidView(
    factory = { ctx ->
      PreviewView(ctx).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
      }
    },
    update = { previewView ->
      val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
      cameraProviderFuture.addListener({
        try {
          val cameraProvider = cameraProviderFuture.get()
          val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
          }
          val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
          } else {
            CameraSelector.DEFAULT_BACK_CAMERA
          }
          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
        } catch (e: Exception) {
          Log.e("CallScreen", "Camera bind error: ${e.message}")
        }
      }, ContextCompat.getMainExecutor(context))
    },
    modifier = modifier
  )
}


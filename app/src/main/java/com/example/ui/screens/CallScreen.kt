package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * VYBE CALL SCREEN 2.0
 * Real-time Voice & Video calling screen with runtime Microphone + Camera permissions,
 * incoming Accept/Decline controls, live MM:SS duration timer, speaker/mute/camera/flip toggles,
 * stable single-slot CameraX viewfinder with upright downscaled JPEG streaming, and remote peer video rendering.
 */
@Composable
fun CallScreen(viewModel: VybeViewModel) {
  val activeCall by viewModel.activeCall.collectAsState()
  BackHandler {
    if (activeCall?.state == CallState.INCOMING) {
      viewModel.declineCall()
    } else {
      viewModel.endCall()
    }
  }

  val context = LocalContext.current
  var hasAudioPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )
  }
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }
  var permissionDeniedNotice by remember { mutableStateOf(false) }

  val multiPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    result[Manifest.permission.RECORD_AUDIO]?.let { granted ->
      hasAudioPermission = granted
    }
    result[Manifest.permission.CAMERA]?.let { granted ->
      hasCameraPermission = granted
    }
    val needsVideo = activeCall?.isVideo == true
    permissionDeniedNotice = !hasAudioPermission || (needsVideo && !hasCameraPermission)
    if (hasAudioPermission) {
      viewModel.onCallAudioPermissionReady()
    }
  }

  LaunchedEffect(activeCall?.callId, activeCall?.isVideo) {
    val call = activeCall ?: return@LaunchedEffect
    val needed = buildList {
      if (!hasAudioPermission) add(Manifest.permission.RECORD_AUDIO)
      if (call.isVideo && !hasCameraPermission) add(Manifest.permission.CAMERA)
    }
    if (needed.isNotEmpty()) {
      multiPermissionLauncher.launch(needed.toTypedArray())
    } else if (hasAudioPermission) {
      viewModel.onCallAudioPermissionReady()
    }
  }

  LaunchedEffect(hasAudioPermission, activeCall?.state) {
    if (hasAudioPermission && activeCall?.state == CallState.CONNECTED) {
      viewModel.onCallAudioPermissionReady()
    }
  }

  if (activeCall == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Call ended")
    }
    return
  }

  val call = activeCall!!
  val formattedDuration = remember(call.durationSeconds) {
    String.format(Locale.US, "%02d:%02d", call.durationSeconds / 60, call.durationSeconds % 60)
  }

  val remoteBitmap = remember(call.remoteVideoFrameBase64) {
    val encoded = call.remoteVideoFrameBase64
    if (encoded.isNullOrBlank()) {
      null
    } else {
      try {
        val clean = encoded.substringAfter("base64,", encoded)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
      } catch (_: Exception) {
        null
      }
    }
  }

  val showRemoteVideo = call.isVideo && !call.isPeerCameraOff && call.state == CallState.CONNECTED && remoteBitmap != null
  val showLocalCamera = call.isVideo && !call.isCameraOff && hasCameraPermission

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(ObsidianBackground, ObsidianSurface, ObsidianBackground)
        )
      )
  ) {
    // 1. Remote Peer Video Surface (Full-screen when peer frames are arriving)
    if (showRemoteVideo && remoteBitmap != null) {
      Image(
        bitmap = remoteBitmap,
        contentDescription = "Peer video stream",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .testTag("remote_video_surface")
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.20f))
      )
    }

    // 2. Single Persistent Local CameraX Surface
    // Keeping ONE stable composition slot prevents CameraX from unbinding when transitioning
    // from full-screen preview to Picture-in-Picture once the first remote frame arrives!
    if (showLocalCamera) {
      val cameraContainerModifier = if (showRemoteVideo) {
        Modifier
          .align(Alignment.TopEnd)
          .statusBarsPadding()
          .padding(top = 84.dp, end = 16.dp)
          .size(width = 112.dp, height = 158.dp)
          .clip(RoundedCornerShape(16.dp))
          .border(1.5.dp, VybeCyan.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
          .background(ObsidianSurface)
      } else {
        Modifier.fillMaxSize()
      }

      Box(modifier = cameraContainerModifier) {
        CameraPreviewView(
          isFrontCamera = call.isFrontCamera,
          streamFrames = call.state == CallState.CONNECTED && !call.isCameraOff,
          onFrameCaptured = { jpegBytes -> viewModel.pushLocalVideoFrame(jpegBytes) },
          modifier = Modifier.fillMaxSize()
        )
        if (!showRemoteVideo) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.30f))
          )
        }
      }
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
      // Top info: Call type + status + live MM:SS timer
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
            CallState.IDLE -> "Initializing…"
            CallState.OUTGOING -> "Ringing…"
            CallState.INCOMING -> "Incoming ${if (call.isVideo) "Video" else "Voice"} Call…"
            CallState.CONNECTING -> "Connecting…"
            CallState.CONNECTED -> "Connected • $formattedDuration"
            CallState.DECLINED -> "Call declined"
            CallState.MISSED -> "No answer"
            CallState.FAILED -> "Connection failed"
            CallState.ENDED -> "Call ended"
          },
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color.White,
          modifier = Modifier.testTag("call_status_label")
        )

        if (call.connectionStatusText.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = call.connectionStatusText,
            fontSize = 11.sp,
            color = ObsidianTextSecondary
          )
        }

        if (call.isPeerMuted || (call.isVideo && call.isPeerCameraOff && call.state == CallState.CONNECTED)) {
          Spacer(modifier = Modifier.height(6.dp))
          Surface(
            color = ObsidianSurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(10.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (call.isPeerMuted) {
                Icon(Icons.Default.MicOff, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(13.dp))
                Text("${call.peerUser.displayName} muted", fontSize = 11.sp, color = Color.White)
              }
              if (call.isVideo && call.isPeerCameraOff) {
                Icon(Icons.Default.VideocamOff, contentDescription = null, tint = ObsidianTextSecondary, modifier = Modifier.size(13.dp))
                Text("Camera paused", fontSize = 11.sp, color = ObsidianTextSecondary)
              }
            }
          }
        }

        if (permissionDeniedNotice) {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            color = VybeDanger.copy(alpha = 0.22f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.6f))
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (!hasAudioPermission) "Microphone permission needed for audio" else "Camera permission needed for video",
                fontSize = 11.sp,
                color = Color.White
              )
              Spacer(modifier = Modifier.width(8.dp))
              TextButton(
                onClick = {
                  val needed = buildList {
                    if (!hasAudioPermission) add(Manifest.permission.RECORD_AUDIO)
                    if (call.isVideo && !hasCameraPermission) add(Manifest.permission.CAMERA)
                  }
                  if (needed.isNotEmpty()) {
                    multiPermissionLauncher.launch(needed.toTypedArray())
                  }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
              ) {
                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VybeCyan)
              }
            }
          }
        }
      }

      // Middle: Peer profile & avatar (hidden when remote video is playing full-screen to keep video unobstructed)
      if (showRemoteVideo) {
        Spacer(modifier = Modifier.height(1.dp))
      } else if (showLocalCamera && call.state != CallState.INCOMING) {
        Surface(
          color = ObsidianSurface.copy(alpha = 0.82f),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.5f)),
          modifier = Modifier.padding(16.dp)
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
                text = if (call.state == CallState.CONNECTED) {
                  "Waiting for @${call.peerUser.username}'s camera…"
                } else {
                  "@${call.peerUser.username}"
                },
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
              .size(124.dp)
              .clip(CircleShape)
              .background(VybeViolet.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
          ) {
            VybeAvatar(user = call.peerUser, size = 102.dp)
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

      // Bottom Controls
      if (call.state == CallState.INCOMING) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { viewModel.declineCall() },
              modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(VybeDanger)
                .testTag("call_decline_button")
            ) {
              Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "Decline Call",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Decline", fontSize = 12.sp, color = ObsidianTextSecondary)
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { viewModel.acceptCall() },
              modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(VybeOnlineGreen)
                .testTag("call_accept_button")
            ) {
              Icon(
                imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Answer Call",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Answer", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
          }
        }
      } else {
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
                .size(52.dp)
                .clip(CircleShape)
                .background(if (call.isMuted) VybeDanger else MaterialTheme.colorScheme.surfaceVariant)
                .testTag("call_mute_button")
            ) {
              Icon(
                imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (call.isMuted) "Unmute Microphone" else "Mute Microphone",
                tint = Color.White
              )
            }

            // Speakerphone toggle
            IconButton(
              onClick = { viewModel.toggleCallSpeaker() },
              modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (call.isSpeakerOn) VybeViolet else MaterialTheme.colorScheme.surfaceVariant)
                .testTag("call_speaker_button")
            ) {
              Icon(
                imageVector = if (call.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PhoneInTalk,
                contentDescription = if (call.isSpeakerOn) "Speaker On" else "Earpiece",
                tint = Color.White
              )
            }

            // Camera toggle + Flip lens (if video call)
            if (call.isVideo) {
              IconButton(
                onClick = { viewModel.toggleCallCamera() },
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(if (call.isCameraOff) VybeDanger else MaterialTheme.colorScheme.surfaceVariant)
                  .testTag("call_camera_button")
              ) {
                Icon(
                  imageVector = if (call.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                  contentDescription = if (call.isCameraOff) "Turn Camera On" else "Turn Camera Off",
                  tint = Color.White
                )
              }

              IconButton(
                onClick = { viewModel.switchCallCamera() },
                modifier = Modifier
                  .size(52.dp)
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
                .size(62.dp)
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
}

@Composable
fun CameraPreviewView(
  isFrontCamera: Boolean,
  streamFrames: Boolean = false,
  onFrameCaptured: ((ByteArray) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentStreamFrames by rememberUpdatedState(streamFrames)
  val currentIsFrontCamera by rememberUpdatedState(isFrontCamera)
  val currentOnFrameCaptured by rememberUpdatedState(onFrameCaptured)
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
  var boundUseCases by remember { mutableStateOf<List<UseCase>>(emptyList()) }
  var lastFrameSentMs by remember { mutableLongStateOf(0L) }

  // Bind CameraX ONLY when lifecycleOwner, camera lens, or PreviewView instance changes —
  // NEVER inside AndroidView(update = ...) which fires on every timer/frame recomposition!
  LaunchedEffect(lifecycleOwner, isFrontCamera, previewViewRef) {
    val previewView = previewViewRef ?: return@LaunchedEffect
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
      try {
        val cameraProvider = cameraProviderFuture.get()
        if (boundUseCases.isNotEmpty()) {
          cameraProvider.unbind(*boundUseCases.toTypedArray())
        }
        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }
        val imageAnalysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
          .also { analysis ->
            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
              try {
                val now = System.currentTimeMillis()
                if (currentStreamFrames && currentOnFrameCaptured != null && now - lastFrameSentMs >= 320L) {
                  val jpeg = imageProxyToCompressedJpeg(
                    image = imageProxy,
                    mirrorHorizontal = currentIsFrontCamera
                  )
                  if (jpeg != null) {
                    lastFrameSentMs = now
                    currentOnFrameCaptured?.invoke(jpeg)
                  }
                }
              } catch (_: Exception) {
              } finally {
                imageProxy.close()
              }
            }
          }

        val cameraSelector = if (isFrontCamera) {
          CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
          CameraSelector.DEFAULT_BACK_CAMERA
        }
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        boundUseCases = listOf(preview, imageAnalysis)
      } catch (e: Exception) {
        Log.e("CallScreen", "Camera bind error: ${e.message}")
      }
    }, ContextCompat.getMainExecutor(context))
  }

  DisposableEffect(Unit) {
    onDispose {
      try {
        val provider = ProcessCameraProvider.getInstance(context).get()
        if (boundUseCases.isNotEmpty()) {
          provider.unbind(*boundUseCases.toTypedArray())
        }
      } catch (_: Exception) {
      }
      analysisExecutor.shutdown()
    }
  }

  AndroidView(
    factory = { ctx ->
      PreviewView(ctx).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        previewViewRef = this
      }
    },
    modifier = modifier
  )
}

/**
 * Converts a CameraX YUV_420_888 [ImageProxy] into a compact (~10-16 KB), upright,
 * downscaled JPEG so frames are never dropped by size limits and render right-side-up on peer devices.
 */
internal fun imageProxyToCompressedJpeg(
  image: ImageProxy,
  mirrorHorizontal: Boolean = false,
  maxDimensionPx: Int = 260
): ByteArray? {
  if (image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) return null
  val width = image.width
  val height = image.height
  val yPlane = image.planes[0]
  val uPlane = image.planes[1]
  val vPlane = image.planes[2]

  val yBuffer = yPlane.buffer
  val uBuffer = uPlane.buffer
  val vBuffer = vPlane.buffer

  val nv21 = ByteArray(width * height + (width * height / 2))
  var pos = 0
  val yRowStride = yPlane.rowStride
  val yPixelStride = yPlane.pixelStride
  for (row in 0 until height) {
    val rowStart = row * yRowStride
    for (col in 0 until width) {
      nv21[pos++] = yBuffer.get(rowStart + col * yPixelStride)
    }
  }

  val chromaHeight = height / 2
  val chromaWidth = width / 2
  val uRowStride = uPlane.rowStride
  val uPixelStride = uPlane.pixelStride
  val vRowStride = vPlane.rowStride
  val vPixelStride = vPlane.pixelStride
  for (row in 0 until chromaHeight) {
    val vRowStart = row * vRowStride
    val uRowStart = row * uRowStride
    for (col in 0 until chromaWidth) {
      nv21[pos++] = vBuffer.get(vRowStart + col * vPixelStride)
      nv21[pos++] = uBuffer.get(uRowStart + col * uPixelStride)
    }
  }

  val rawOut = ByteArrayOutputStream()
  val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
  if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), 72, rawOut)) return null
  val rawBytes = rawOut.toByteArray()

  val largestRawDim = max(width, height)
  var sampleSize = 1
  while (largestRawDim / (sampleSize * 2) >= maxDimensionPx) {
    sampleSize *= 2
  }
  val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
  val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return rawBytes

  val rotation = image.imageInfo.rotationDegrees
  val scale = (maxDimensionPx.toFloat() / max(decoded.width, decoded.height).toFloat()).coerceAtMost(1f)
  val matrix = Matrix().apply {
    if (rotation != 0) postRotate(rotation.toFloat())
    if (mirrorHorizontal) postScale(-scale, scale) else if (scale < 1f) postScale(scale, scale)
  }

  val transformed: Bitmap = try {
    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
  } catch (_: Throwable) {
    decoded
  }

  val finalOut = ByteArrayOutputStream()
  transformed.compress(Bitmap.CompressFormat.JPEG, 60, finalOut)
  if (transformed !== decoded) {
    transformed.recycle()
  }
  decoded.recycle()
  return finalOut.toByteArray()
}

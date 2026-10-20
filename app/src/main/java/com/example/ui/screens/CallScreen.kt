package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallState
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

@Composable
fun CallScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.endCall()
  }

  val activeCall by viewModel.activeCall.collectAsState()

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
          color = VybeViolet.copy(alpha = 0.2f),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
            CallState.OUTGOING -> "Ringing..."
            CallState.INCOMING -> "Incoming Call..."
            CallState.CONNECTED -> "Connected • 00:42"
            CallState.ENDED -> "Call Ended"
            CallState.IDLE -> ""
          },
          fontSize = 14.sp,
          color = ObsidianTextSecondary
        )
      }

      // Middle: Large Peer Avatar & Name
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                listOf(VybeViolet.copy(alpha = 0.4f), Color.Transparent)
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          VybeAvatar(user = call.peerUser, size = 110.dp, showPulseDot = false)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = call.peerUser.displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Spacer(modifier = Modifier.width(6.dp))
          VerificationBadge(user = call.peerUser)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "@${call.peerUser.username}",
          fontSize = 14.sp,
          color = ObsidianTextSecondary
        )
      }

      // Bottom Control Buttons
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Mute Button
          IconButton(
            onClick = { viewModel.toggleCallMute() },
            modifier = Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(if (call.isMuted) Color.White else ObsidianSurfaceElevated)
              .testTag("call_mute_button")
          ) {
            Icon(
              imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
              contentDescription = "Mute",
              tint = if (call.isMuted) Color.Black else Color.White
            )
          }

          // Camera Toggle Button
          IconButton(
            onClick = { viewModel.toggleCallCamera() },
            modifier = Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(if (call.isCameraOff) Color.White else ObsidianSurfaceElevated)
              .testTag("call_camera_button")
          ) {
            Icon(
              imageVector = if (call.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
              contentDescription = "Camera",
              tint = if (call.isCameraOff) Color.Black else Color.White
            )
          }

          // End Call Button
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
              modifier = Modifier.size(30.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // WebRTC Signaling indicator
        Text(
          text = "WebRTC Secure Peer Connection • STUN/TURN Active",
          fontSize = 10.sp,
          color = ObsidianTextMuted
        )
      }
    }
  }
}

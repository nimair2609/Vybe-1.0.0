package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.User
import com.example.ui.theme.*
import com.example.util.QrCodeGenerator

@Composable
fun ProfileQrCodeDialog(
  user: User,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val shareUrl = remember(user.id, user.username) {
    QrCodeGenerator.buildCanonicalProfileUrl(
      immutableUserId = user.id,
      username = user.username
    )
  }

  // Generate crisp QR bitmap in background
  val qrBitmap = remember(shareUrl) {
    // Generate sharp QR code with dark modules for maximum scan readability
    QrCodeGenerator.generateQrBitmap(
      content = shareUrl,
      sizePx = 512,
      foregroundHex = android.graphics.Color.BLACK,
      backgroundHex = android.graphics.Color.WHITE
    )
  }

  val palette = LocalVybePalette.current
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.78f))
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 400.dp)
          .clip(RoundedCornerShape(VybeTokens.Radius.xl))
          .border(
            width = 1.dp,
            color = palette.border,
            shape = RoundedCornerShape(VybeTokens.Radius.xl)
          )
          .testTag("profile_qr_code_card"),
        colors = CardDefaults.cardColors(containerColor = palette.card),
        shape = RoundedCornerShape(VybeTokens.Radius.xl)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(22.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Top bar with Close button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clip(CircleShape)
                  .background(palette.accent),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(16.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "VYBE Profile QR",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                fontFamily = PlusJakartaSansFontFamily,
                color = palette.textPrimary
              )
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(36.dp)
                .testTag("close_qr_dialog_button")
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = palette.textSecondary)
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          // User Header info
          VybeAvatar(user = user, size = 68.dp)
          Spacer(modifier = Modifier.height(10.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = user.displayName.ifBlank { user.username },
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              color = palette.textPrimary
            )
            VerificationBadge(user = user)
          }
          Text(
            text = "@${user.username}",
            fontSize = 13.sp,
            color = palette.accent,
            fontWeight = FontWeight.SemiBold
          )

          Spacer(modifier = Modifier.height(18.dp))

          // QR Code Container with High-Contrast White Background & Border
          Box(
            modifier = Modifier
              .size(230.dp)
              .clip(RoundedCornerShape(VybeTokens.Radius.lg))
              .background(Color.White)
              .padding(14.dp)
              .testTag("qr_code_image_container"),
            contentAlignment = Alignment.Center
          ) {
            if (qrBitmap != null) {
              Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Profile QR Code for @${user.username}",
                modifier = Modifier.fillMaxSize()
              )
            } else {
              CircularProgressIndicator(color = palette.accent)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Text(
            text = "Scan with any camera or QR scanner to view and LINK UP with @${user.username} on VYBE",
            fontSize = 12.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
          )

          Spacer(modifier = Modifier.height(20.dp))

          // Share Actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            VybeSecondaryButton(
              text = "Copy Link",
              icon = Icons.Default.ContentCopy,
              testTag = "copy_profile_link_button",
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("VYBE Profile Link", shareUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied profile link to clipboard!", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.weight(1f)
            )

            VybePrimaryButton(
              text = "Share Link",
              icon = Icons.Default.Share,
              testTag = "share_qr_profile_button",
              onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, "Connect with @${user.username} on VYBE")
                  putExtra(
                    Intent.EXTRA_TEXT,
                    "Connect with @${user.username} on VYBE: $shareUrl"
                  )
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Profile QR"))
              },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }
  }
}

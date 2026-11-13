package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalVybePalette
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.VybeTokens
import com.example.util.GitHubReleaseInfo

@Composable
fun InAppUpdateBanner(
  release: GitHubReleaseInfo,
  onUpdateClick: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = LocalVybePalette.current
  AnimatedVisibility(
    visible = true,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = modifier
  ) {
    Surface(
      color = palette.card.copy(alpha = 0.96f),
      shape = RoundedCornerShape(VybeTokens.Radius.md),
      border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.45f)),
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .testTag("in_app_update_banner")
    ) {
      Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(VybeTokens.Radius.sm))
            .background(palette.accent.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(22.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Update Available",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              color = palette.success.copy(alpha = 0.18f),
              shape = RoundedCornerShape(VybeTokens.Radius.xs)
            ) {
              Text(
                text = release.tagName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.success,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
              )
            }
          }
          Text(
            text = if (release.name.isNotBlank() && release.name != release.tagName) release.name else "In-place upgrade with zero data loss",
            fontSize = 11.sp,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
          onClick = onUpdateClick,
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
          shape = RoundedCornerShape(VybeTokens.Radius.sm),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("banner_update_button")
        ) {
          Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(28.dp).testTag("dismiss_update_banner_button")
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
fun UpdateInstallPermissionDialog(
  onGrantPermission: () -> Unit,
  onDismiss: () -> Unit
) {
  val palette = LocalVybePalette.current
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = palette.card,
    titleContentColor = palette.textPrimary,
    textContentColor = palette.textSecondary,
    shape = RoundedCornerShape(VybeTokens.Radius.xl),
    icon = {
      Icon(Icons.Default.Security, contentDescription = null, tint = palette.accent, modifier = Modifier.size(32.dp))
    },
    title = {
      Text(
        "Allow Seamless Updates",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
      )
    },
    text = {
      Text(
        text = "To install updates without reinstalling and without losing your login session or stored data, Android requires you to allow VYBE to install unknown apps.\n\nTap 'Allow Permission' to open Settings, switch on 'Allow from this source', and return to complete the update.",
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = palette.textSecondary
      )
    },
    confirmButton = {
      Button(
        onClick = onGrantPermission,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        shape = RoundedCornerShape(VybeTokens.Radius.sm),
        modifier = Modifier.testTag("grant_install_permission_button")
      ) {
        Text("Allow Permission", fontWeight = FontWeight.Bold, color = Color.White)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = palette.textSecondary)
      }
    }
  )
}

@Composable
fun DeveloperGitReleaseGuideDialog(
  onDismiss: () -> Unit
) {
  val palette = LocalVybePalette.current
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = palette.card,
    titleContentColor = palette.textPrimary,
    textContentColor = palette.textSecondary,
    shape = RoundedCornerShape(VybeTokens.Radius.xl),
    icon = {
      Icon(Icons.Default.CloudUpload, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(32.dp))
    },
    title = {
      Text(
        "How Git Releases Update Users",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
      )
    },
    text = {
      Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "When you publish a new version on GitHub, all installed users receive an in-app update prompt without ever needing to uninstall:\n",
          fontSize = 12.sp,
          color = palette.textPrimary
        )

        StepRow(step = "1", title = "Bump Version & Build APK", desc = "Increment versionCode / versionName in build.gradle.kts and run 'gradle assembleDebug' (or Export APK in AI Studio).")
        StepRow(step = "2", title = "Create a GitHub Release", desc = "Go to your GitHub repo -> Releases -> 'Draft a new release'. Tag it (e.g. v2.1.0).")
        StepRow(step = "3", title = "Attach the .apk File", desc = "Drag and drop your generated .apk file into the release assets and click 'Publish release'.")
        StepRow(step = "4", title = "Instant Over-The-Air Update", desc = "Users tap 'Update' in VYBE. Android's PackageInstaller updates the app in-place, keeping all user accounts, messages, and settings intact!")
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        shape = RoundedCornerShape(VybeTokens.Radius.sm)
      ) {
        Text("Got It", color = Color.White)
      }
    }
  )
}

@Composable
private fun StepRow(step: String, title: String, desc: String) {
  val palette = LocalVybePalette.current
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Surface(
      color = palette.accent.copy(alpha = 0.16f),
      shape = RoundedCornerShape(VybeTokens.Radius.xs),
      modifier = Modifier.size(22.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(step, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = palette.accent)
      }
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = palette.textPrimary)
      Text(desc, fontSize = 11.sp, color = palette.textSecondary)
    }
  }
}

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
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeViolet
import com.example.util.GitHubReleaseInfo
import java.util.Locale

@Composable
fun InAppUpdateBanner(
  release: GitHubReleaseInfo,
  onUpdateClick: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = true,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = modifier
  ) {
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.5f)),
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
            .clip(RoundedCornerShape(10.dp))
            .background(VybeViolet.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = VybeViolet,
            modifier = Modifier.size(22.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Update Available",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              color = VybeOnlineGreen.copy(alpha = 0.2f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = release.tagName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = VybeOnlineGreen,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
              )
            }
          }
          Text(
            text = if (release.name.isNotBlank() && release.name != release.tagName) release.name else "In-place upgrade with zero data loss",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
          onClick = onUpdateClick,
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("banner_update_button")
        ) {
          Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(28.dp).testTag("dismiss_update_banner_button")
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(Icons.Default.Security, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(32.dp))
    },
    title = {
      Text("Allow Seamless Updates", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    },
    text = {
      Text(
        text = "To install updates without reinstalling and without losing your login session or stored data, Android requires you to allow VYBE to install unknown apps.\n\nTap 'Allow Permission' to open Settings, switch on 'Allow from this source', and return to complete the update.",
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    },
    confirmButton = {
      Button(
        onClick = onGrantPermission,
        colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("grant_install_permission_button")
      ) {
        Text("Allow Permission", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun DeveloperGitReleaseGuideDialog(
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(Icons.Default.CloudUpload, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(32.dp))
    },
    title = {
      Text("How Git Releases Update Users", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    },
    text = {
      Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "When you publish a new version on GitHub, all installed users receive an in-app update prompt without ever needing to uninstall:\n",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurface
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
        colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
        shape = RoundedCornerShape(8.dp)
      ) {
        Text("Got It")
      }
    }
  )
}

@Composable
private fun StepRow(step: String, title: String, desc: String) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Surface(
      color = VybeViolet.copy(alpha = 0.2f),
      shape = RoundedCornerShape(6.dp),
      modifier = Modifier.size(22.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(step, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = VybeViolet)
      }
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
      Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

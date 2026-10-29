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
import com.example.util.GitHubUpdater
import kotlinx.coroutines.launch
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

@Composable
fun GitHubUpdateSectionCard(
  isOnline: Boolean,
  modifier: Modifier = Modifier
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var isChecking by remember { mutableStateOf(false) }
  var releaseInfo by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showGuide by remember { mutableStateOf(false) }

  if (showGuide) {
    DeveloperGitReleaseGuideDialog(onDismiss = { showGuide = false })
  }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = VybeViolet,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "App Version & Updates",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
        }

        Surface(
          color = VybeViolet.copy(alpha = 0.15f),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = "v${GitHubUpdater.INSTALLED_VERSION}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = VybeViolet,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      Text(
        text = "VYBE updates are distributed directly from the official GitHub repository with in-place zero-data-loss installation.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 16.sp
      )

      if (!isOnline) {
        Surface(
          color = VybeDanger.copy(alpha = 0.12f),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "You are currently offline. Connect to Wi-Fi or cellular data to check for updates.",
            color = VybeDanger,
            fontSize = 11.sp,
            modifier = Modifier.padding(10.dp)
          )
        }
      }

      releaseInfo?.let { release ->
        Surface(
          color = if (release.isNewerThanInstalled) VybeOnlineGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = if (release.isNewerThanInstalled) "🎉 New Version Available: ${release.tagName}" else "✓ App is up to date (${release.tagName})",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = if (release.isNewerThanInstalled) VybeOnlineGreen else MaterialTheme.colorScheme.onSurface
            )
            if (release.body.isNotBlank()) {
              Text(
                text = release.body.take(200),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
              )
            }
            if (release.isNewerThanInstalled && !release.downloadUrl.isNullOrBlank()) {
              Button(
                onClick = { GitHubUpdater.openDownloadUrl(context, release.downloadUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Download & Install Update", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      errorMessage?.let { err ->
        Text(text = err, color = VybeDanger, fontSize = 11.sp)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = {
            if (!isOnline) {
              errorMessage = "Cannot check for updates while offline."
              return@Button
            }
            isChecking = true
            errorMessage = null
            coroutineScope.launch {
              val result = GitHubUpdater.checkForUpdates(GitHubUpdater.DEFAULT_GITHUB_REPO)
              isChecking = false
              result.fold(
                onSuccess = { releaseInfo = it },
                onFailure = { errorMessage = it.message ?: "Failed to check updates." }
              )
            }
          },
          enabled = !isChecking,
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          if (isChecking) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Checking...", fontSize = 12.sp)
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Check for Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }

        OutlinedButton(
          onClick = { showGuide = true },
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Guide", fontSize = 12.sp)
        }
      }
    }
  }
}

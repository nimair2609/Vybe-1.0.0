package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeViolet
import com.example.util.ApkCompatibilityStatus
import com.example.util.ApkInspectionReport
import com.example.util.GitHubReleaseInfo
import com.example.util.GitHubUpdater
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GitHubUpdateSectionCard(isOnline: Boolean) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var githubRepoInput by remember { mutableStateOf(GitHubUpdater.getSavedRepo(context)) }
  var isCheckingUpdate by remember { mutableStateOf(false) }
  var updateResult by remember { mutableStateOf<Result<GitHubReleaseInfo>?>(null) }

  var isDownloadingApk by remember { mutableStateOf(false) }
  var downloadedBytes by remember { mutableLongStateOf(0L) }
  var totalBytes by remember { mutableLongStateOf(0L) }
  var downloadError by remember { mutableStateOf<String?>(null) }
  var inspectionReport by remember { mutableStateOf<ApkInspectionReport?>(null) }
  var pendingInstallerFile by remember { mutableStateOf<File?>(null) }
  var needsInstallPermissionPrompt by remember { mutableStateOf(false) }

  val installedSig = remember { GitHubUpdater.getInstalledSignatureSummary(context) }

  val unknownSourcesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) {
    val fileToInstall = pendingInstallerFile
    if (fileToInstall != null && GitHubUpdater.canInstallPackages(context)) {
      needsInstallPermissionPrompt = false
      GitHubUpdater.launchApkInstaller(context, fileToInstall).onFailure { err ->
        downloadError = "Could not launch system installer: ${err.localizedMessage ?: "Unknown error"}"
      }
    }
  }

  fun triggerCheckedInstall(apkFile: File) {
    pendingInstallerFile = apkFile
    if (GitHubUpdater.canInstallPackages(context)) {
      needsInstallPermissionPrompt = false
      GitHubUpdater.launchApkInstaller(context, apkFile).onFailure { err ->
        downloadError = "Could not open Android Package Installer: ${err.localizedMessage ?: "Try saving the APK instead."}"
      }
    } else {
      needsInstallPermissionPrompt = true
      unknownSourcesLauncher.launch(GitHubUpdater.createUnknownSourcesSettingsIntent(context))
    }
  }

  fun startInAppDownloadAndInstall(release: GitHubReleaseInfo) {
    val apkUrl = release.downloadUrl
    if (!release.hasValidApkAsset || apkUrl.isNullOrBlank()) {
      GitHubUpdater.openDownloadUrl(context, release.htmlUrl)
      return
    }
    isDownloadingApk = true
    downloadedBytes = 0L
    totalBytes = release.apkFileSize
    downloadError = null
    inspectionReport = null
    needsInstallPermissionPrompt = false

    coroutineScope.launch {
      val dlResult = GitHubUpdater.downloadApkWithProgress(
        context = context,
        downloadUrl = apkUrl,
        tagName = release.tagName
      ) { dl, total ->
        downloadedBytes = dl
        if (total > 0L) totalBytes = total
      }

      dlResult.onSuccess { apkFile ->
        val report = GitHubUpdater.inspectApkCompatibility(context, apkFile)
        inspectionReport = report
        isDownloadingApk = false
        if (report.status == ApkCompatibilityStatus.COMPATIBLE_UPDATE) {
          triggerCheckedInstall(apkFile)
        }
      }.onFailure { err ->
        isDownloadingApk = false
        downloadError = err.message ?: "Failed to download update APK."
      }
    }
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("github_updater_card")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.SystemUpdate,
          contentDescription = null,
          tint = VybeViolet,
          modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text("VYBE Software Update", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Text(
            text = "Installed: v${GitHubUpdater.INSTALLED_VERSION} (build ${GitHubUpdater.INSTALLED_VERSION_CODE}) • Key $installedSig",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = githubRepoInput,
        onValueChange = { githubRepoInput = it },
        label = { Text("GitHub Release Repository (owner/repo)") },
        placeholder = { Text(GitHubUpdater.DEFAULT_GITHUB_REPO) },
        singleLine = true,
        trailingIcon = {
          if (!githubRepoInput.trim().equals(GitHubUpdater.DEFAULT_GITHUB_REPO, ignoreCase = true)) {
            IconButton(
              onClick = {
                githubRepoInput = GitHubUpdater.resetSavedRepo(context)
                updateResult = null
                inspectionReport = null
                downloadError = null
              },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("reset_github_repo_button")
            ) {
              Icon(
                imageVector = Icons.Default.Restore,
                contentDescription = "Reset to official repository"
              )
            }
          }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("github_repo_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      if (!isOnline) {
        Surface(
          color = VybeDanger.copy(alpha = 0.14f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
        ) {
          Text(
            text = "You are currently offline. Reconnect to the internet to check GitHub Releases.",
            color = VybeDanger,
            fontSize = 12.sp,
            modifier = Modifier.padding(10.dp)
          )
        }
      }

      Button(
        onClick = {
          isCheckingUpdate = true
          updateResult = null
          downloadError = null
          inspectionReport = null
          needsInstallPermissionPrompt = false
          val normalized = GitHubUpdater.cleanRepoSlug(githubRepoInput)
          if (normalized.contains("/")) {
            githubRepoInput = normalized
            GitHubUpdater.saveRepo(context, normalized)
          }
          coroutineScope.launch {
            val res = GitHubUpdater.checkForUpdates(githubRepoInput)
            updateResult = res
            isCheckingUpdate = false
          }
        },
        enabled = !isCheckingUpdate && !isDownloadingApk && githubRepoInput.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .testTag("check_github_updates_button")
      ) {
        if (isCheckingUpdate) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Checking GitHub Releases...", fontSize = 13.sp, color = Color.White)
        } else {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Check for Updates", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
      }

      updateResult?.let { res ->
        Spacer(modifier = Modifier.height(14.dp))
        res.onSuccess { release ->
          val highlightColor = when {
            release.isNewerThanInstalled -> VybeOnlineGreen
            release.isOlderThanInstalled -> VybeViolet
            else -> VybeBrandCyan
          }
          val statusTitle = when {
            release.isNewerThanInstalled -> "Update Available: ${release.tagName}"
            release.isOlderThanInstalled -> "Installed App is Newer (v${GitHubUpdater.INSTALLED_VERSION} > ${release.tagName})"
            else -> "Up to Date (${release.tagName})"
          }

          Surface(
            color = highlightColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, highlightColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (release.isNewerThanInstalled) Icons.Default.NewReleases else Icons.Default.Verified,
                  contentDescription = null,
                  tint = highlightColor,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = statusTitle,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = highlightColor
                )
              }

              if (release.name.isNotBlank() && release.name != release.tagName) {
                Text(
                  text = release.name,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(top = 4.dp)
                )
              }

              if (release.apkFileName != null) {
                val sizeFormatted = GitHubUpdater.formatFileSize(release.apkFileSize)
                val details = if (sizeFormatted.isNotBlank()) "${release.apkFileName} • $sizeFormatted" else release.apkFileName
                Text(
                  text = "Release APK: $details",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 2.dp)
                )
              }

              if (release.assetStatusMessage != null) {
                Surface(
                  color = if (release.isOlderThanInstalled) VybeBrandCyan.copy(alpha = 0.12f) else VybeDanger.copy(alpha = 0.12f),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                ) {
                  Text(
                    text = release.assetStatusMessage,
                    color = if (release.isOlderThanInstalled) MaterialTheme.colorScheme.onSurface else VybeDanger,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp)
                  )
                }
              }

              if (release.body.isNotBlank() && !release.isOlderThanInstalled) {
                Text(
                  text = release.body.take(240) + if (release.body.length > 240) "..." else "",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 6.dp)
                )
              }

              if (isDownloadingApk) {
                Spacer(modifier = Modifier.height(12.dp))
                val progress = if (totalBytes > 0L) {
                  (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else null
                if (progress != null) {
                  LinearProgressIndicator(
                    progress = { progress },
                    color = highlightColor,
                    modifier = Modifier.fillMaxWidth()
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  val pct = (progress * 100).toInt()
                  Text(
                    text = "Downloading fresh APK... $pct% (${GitHubUpdater.formatFileSize(downloadedBytes)} / ${GitHubUpdater.formatFileSize(totalBytes)})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                } else {
                  LinearProgressIndicator(color = highlightColor, modifier = Modifier.fillMaxWidth())
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Downloading fresh APK... ${GitHubUpdater.formatFileSize(downloadedBytes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              downloadError?.let { dlErr ->
                Surface(
                  color = VybeDanger.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                ) {
                  Text(
                    text = dlErr,
                    color = VybeDanger,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp)
                  )
                }
              }

              if (needsInstallPermissionPrompt && pendingInstallerFile != null) {
                Surface(
                  color = VybeBrandCyan.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                      text = "Enable 'Allow from this source' in Android Settings so VYBE can launch the downloaded update.",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                      onClick = { pendingInstallerFile?.let { triggerCheckedInstall(it) } },
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                    ) {
                      Text("Install Downloaded APK Now", fontSize = 12.sp)
                    }
                  }
                }
              }

              inspectionReport?.let { report ->
                Spacer(modifier = Modifier.height(10.dp))
                val isCleanMatch = report.status == ApkCompatibilityStatus.COMPATIBLE_UPDATE
                val bannerColor = if (isCleanMatch) VybeOnlineGreen else VybeDanger
                Surface(
                  color = bannerColor.copy(alpha = 0.13f),
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                      text = when (report.status) {
                        ApkCompatibilityStatus.COMPATIBLE_UPDATE -> "Package Verified — Launching Installer"
                        ApkCompatibilityStatus.SIGNATURE_MISMATCH -> "One-Time Clean Install Required (Signing Key Changed)"
                        ApkCompatibilityStatus.VERSION_DOWNGRADE -> "Older Build Detected Inside APK"
                        ApkCompatibilityStatus.DIFFERENT_PACKAGE_ID -> "Different Android Package ID"
                        ApkCompatibilityStatus.INVALID_APK -> "Invalid APK Archive"
                      },
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = bannerColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = report.userSummary,
                      fontSize = 11.sp,
                      lineHeight = 16.sp,
                      color = MaterialTheme.colorScheme.onSurface
                    )

                    if (report.status == ApkCompatibilityStatus.SIGNATURE_MISMATCH) {
                      Spacer(modifier = Modifier.height(10.dp))
                      Button(
                        onClick = { GitHubUpdater.openAppInfoForCleanInstall(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                          .fillMaxWidth()
                          .heightIn(min = 48.dp)
                          .testTag("open_app_info_uninstall_button")
                      ) {
                        Icon(Icons.Default.SettingsApplications, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1. Open App Info to Uninstall Old Build", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                      }
                      Spacer(modifier = Modifier.height(6.dp))
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                          onClick = { GitHubUpdater.shareOrSaveApkFile(context, report.apkFile) },
                          shape = RoundedCornerShape(10.dp),
                          modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_or_share_apk_button")
                        ) {
                          Text("Save / Share APK", fontSize = 11.sp)
                        }
                        OutlinedButton(
                          onClick = { triggerCheckedInstall(report.apkFile) },
                          shape = RoundedCornerShape(10.dp),
                          modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("force_launch_installer_button")
                        ) {
                          Text("Try Installer", fontSize = 11.sp)
                        }
                      }
                    } else if (report.status == ApkCompatibilityStatus.COMPATIBLE_UPDATE) {
                      Spacer(modifier = Modifier.height(8.dp))
                      OutlinedButton(
                        onClick = { triggerCheckedInstall(report.apkFile) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                          .fillMaxWidth()
                          .heightIn(min = 48.dp)
                          .testTag("force_launch_installer_button")
                      ) {
                        Text("Re-open Android Installer (v${report.apkVersionName})", fontSize = 12.sp)
                      }
                    } else if (report.status == ApkCompatibilityStatus.DIFFERENT_PACKAGE_ID) {
                      Spacer(modifier = Modifier.height(8.dp))
                      OutlinedButton(
                        onClick = { triggerCheckedInstall(report.apkFile) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                          .fillMaxWidth()
                          .heightIn(min = 48.dp)
                          .testTag("force_launch_installer_button")
                      ) {
                        Text("Install Separate Package (${report.apkPackageName})", fontSize = 11.sp)
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              if (release.isOlderThanInstalled || !release.hasValidApkAsset) {
                // Never trick the user into downloading an older APK over a newer installed build
                OutlinedButton(
                  onClick = { GitHubUpdater.openDownloadUrl(context, release.htmlUrl) },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("download_update_button")
                ) {
                  Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "View Releases on GitHub",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                }
              } else {
                val sizeTag = GitHubUpdater.formatFileSize(release.apkFileSize)
                val btnLabel = if (release.isNewerThanInstalled) {
                  if (sizeTag.isNotBlank()) "Download & Install Update ($sizeTag)" else "Download & Install Update"
                } else {
                  "Verify & Reinstall ${release.tagName}"
                }
                Button(
                  onClick = { startInAppDownloadAndInstall(release) },
                  enabled = !isDownloadingApk,
                  colors = ButtonDefaults.buttonColors(containerColor = highlightColor),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("download_update_button")
                ) {
                  Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = btnLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                  )
                }
              }
            }
          }
        }.onFailure { err ->
          Surface(
            color = VybeDanger.copy(alpha = 0.14f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Update Check Status", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VybeDanger)
              }
              Text(
                text = err.message ?: "Could not verify release status.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp)
              )
            }
          }
        }
      }
    }
  }
}

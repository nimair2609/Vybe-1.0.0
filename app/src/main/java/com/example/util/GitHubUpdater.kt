package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
  val tagName: String,
  val name: String,
  val body: String,
  val htmlUrl: String,
  val downloadUrl: String?,
  val apkFileName: String? = null,
  val apkSize: Long = 0L,
  val isUpdateAvailable: Boolean = false
)

object GitHubUpdater {
  private const val PREFS_NAME = "vybe_updater_prefs"
  private const val KEY_SAVED_REPO = "github_repo"
  const val DEFAULT_FALLBACK_REPO = "nrnext/vybe"

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  /**
   * Retrieves saved GitHub repository slug (e.g. "user/repo")
   */
  fun getSavedRepo(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_SAVED_REPO, DEFAULT_FALLBACK_REPO) ?: DEFAULT_FALLBACK_REPO
  }

  /**
   * Saves user's GitHub repository
   */
  fun saveRepo(context: Context, repo: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SAVED_REPO, cleanRepoSlug(repo)).apply()
  }

  fun cleanRepoSlug(raw: String): String {
    return raw.trim()
      .removePrefix("https://github.com/")
      .removePrefix("http://github.com/")
      .removePrefix("github.com/")
      .trim('/')
  }

  /**
   * Compare two version strings (e.g., "v2.1.0" vs "v2.0.0" or "2.0.1" vs "2.0.0")
   * Returns true if remote is strictly newer than installed.
   */
  fun isNewerVersion(installed: String, remote: String): Boolean {
    val cleanInstalled = installed.trim().removePrefix("v").removePrefix("V")
    val cleanRemote = remote.trim().removePrefix("v").removePrefix("V")

    val installedParts = cleanInstalled.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
    val remoteParts = cleanRemote.split(".", "-", "_").mapNotNull { it.toIntOrNull() }

    val maxLen = maxOf(installedParts.size, remoteParts.size)
    for (i in 0 until maxLen) {
      val inst = installedParts.getOrElse(i) { 0 }
      val rem = remoteParts.getOrElse(i) { 0 }
      if (rem > inst) return true
      if (rem < inst) return false
    }
    // If numeric parts are identical, check if string representation differs
    return cleanRemote != cleanInstalled && !cleanInstalled.contains(cleanRemote)
  }

  /**
   * Check GitHub Releases API for the latest release tag and APK asset.
   */
  suspend fun checkForUpdates(
    repo: String,
    currentVersion: String = "2.0.0"
  ): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
    try {
      val cleanRepo = cleanRepoSlug(repo)
      if (!cleanRepo.contains("/")) {
        return@withContext Result.failure(
          IllegalArgumentException("Please format GitHub repository as 'owner/repo' (e.g. yourname/vybe)")
        )
      }
      val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
      val request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github.v3+json")
        .header("User-Agent", "VYBE-Android-App")
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        if (response.code == 404) {
          return@withContext Result.failure(
            Exception("No releases found on GitHub repo '$cleanRepo'. To enable auto-updates, create a Release on GitHub and attach the APK asset.")
          )
        }
        return@withContext Result.failure(Exception("GitHub API returned error HTTP ${response.code}"))
      }

      val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response from GitHub"))
      val json = JSONObject(bodyStr)
      val tagName = json.optString("tag_name", "latest")
      val name = json.optString("name", tagName)
      val body = json.optString("body", "New update published on GitHub.")
      val htmlUrl = json.optString("html_url", "https://github.com/$cleanRepo/releases")

      var downloadUrl: String? = null
      var apkFileName: String? = null
      var apkSize: Long = 0L

      val assets = json.optJSONArray("assets")
      if (assets != null) {
        for (i in 0 until assets.length()) {
          val asset = assets.getJSONObject(i)
          val assetName = asset.optString("name", "")
          if (assetName.endsWith(".apk", ignoreCase = true)) {
            downloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
            apkFileName = assetName
            apkSize = asset.optLong("size", 0L)
            break
          }
        }
      }

      val isNewer = isNewerVersion(currentVersion, tagName)

      Result.success(
        GitHubReleaseInfo(
          tagName = tagName,
          name = name,
          body = body,
          htmlUrl = htmlUrl,
          downloadUrl = downloadUrl ?: htmlUrl,
          apkFileName = apkFileName,
          apkSize = apkSize,
          isUpdateAvailable = isNewer
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Downloads the APK file to cache and invokes progress callback (0.0 to 1.0, bytesRead, totalBytes).
   */
  suspend fun downloadApk(
    context: Context,
    apkUrl: String,
    fileName: String = "vybe-update.apk",
    onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
  ): Result<File> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url(apkUrl)
        .header("User-Agent", "VYBE-Android-App")
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        return@withContext Result.failure(Exception("Download failed with HTTP ${response.code}"))
      }

      val responseBody = response.body ?: return@withContext Result.failure(Exception("Download body was empty"))
      val contentLength = responseBody.contentLength()

      val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
      val targetFile = File(updatesDir, fileName)
      if (targetFile.exists()) {
        targetFile.delete()
      }

      var inputStream: InputStream? = null
      var outputStream: FileOutputStream? = null

      try {
        inputStream = responseBody.byteStream()
        outputStream = FileOutputStream(targetFile)

        val buffer = ByteArray(8 * 1024)
        var totalBytesRead = 0L
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
          outputStream.write(buffer, 0, bytesRead)
          totalBytesRead += bytesRead
          val progress = if (contentLength > 0) totalBytesRead.toFloat() / contentLength.toFloat() else 0f
          withContext(Dispatchers.Main) {
            onProgress(progress, totalBytesRead, contentLength)
          }
        }
        outputStream.flush()
      } finally {
        inputStream?.close()
        outputStream?.close()
      }

      Result.success(targetFile)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Checks if app has permission to request package installs (Android 8.0+)
   */
  fun canRequestPackageInstalls(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  /**
   * Opens settings for user to grant "Install unknown apps" permission for this app.
   */
  fun openInstallPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
          data = Uri.parse("package:${context.packageName}")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  /**
   * Triggers the Android Package Installer via FileProvider.
   * This performs an in-place upgrade, keeping user login, sessions, and databases intact.
   */
  fun triggerApkInstall(context: Context, apkFile: File): Result<Unit> {
    return try {
      if (!apkFile.exists()) {
        return Result.failure(Exception("Downloaded APK file not found at ${apkFile.absolutePath}"))
      }

      val apkUri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
      )

      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
          Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
          Intent.FLAG_ACTIVITY_NEW_TASK
      }

      context.startActivity(intent)
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Fallback to open URL in external browser if needed
   */
  fun openDownloadUrl(context: Context, url: String) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}

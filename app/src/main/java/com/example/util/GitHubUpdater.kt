package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
  val tagName: String,
  val name: String,
  val body: String,
  val htmlUrl: String,
  val downloadUrl: String?,
  val isNewerThanInstalled: Boolean = true,
  val apkFileName: String? = null,
  val apkFileSize: Long = 0L,
  val hasValidApkAsset: Boolean = false,
  val assetStatusMessage: String? = null
)

object GitHubUpdater {
  val INSTALLED_VERSION: String
    get() = try {
      com.example.BuildConfig.VERSION_NAME
    } catch (_: Throwable) {
      "1.0.0"
    }

  const val DEFAULT_GITHUB_REPO = "nrnext/vybe"

  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(java.util.Locale.US, mb)
  }

  private val client = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .build()

  fun cleanRepoSlug(repo: String): String {
    return repo
      .trim()
      .removePrefix("https://github.com/")
      .removePrefix("http://github.com/")
      .removePrefix("github.com/")
      .removeSuffix(".git")
      .trim('/')
  }

  fun isNewerVersion(installedVersion: String, remoteVersion: String): Boolean {
    val cleanInstalled = installedVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    if (cleanRemote.isBlank() || cleanRemote.equals("latest", ignoreCase = true)) return false
    if (cleanInstalled == cleanRemote) return false

    val installedParts = cleanInstalled.split(".").map { it.toIntOrNull() ?: 0 }
    val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(installedParts.size, remoteParts.size)
    for (i in 0 until maxLen) {
      val inst = installedParts.getOrElse(i) { 0 }
      val rem = remoteParts.getOrElse(i) { 0 }
      if (rem > inst) return true
      if (rem < inst) return false
    }
    return false
  }

  suspend fun checkForUpdates(repo: String = DEFAULT_GITHUB_REPO): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
    try {
      val cleanRepo = cleanRepoSlug(repo)

      if (cleanRepo.isBlank() || !cleanRepo.contains("/") || cleanRepo.equals("my-github-username/vybe", ignoreCase = true)) {
        return@withContext Result.failure(
          IllegalArgumentException("Enter a valid GitHub repository in 'owner/repo' format (for example, 'nrnext/vybe').")
        )
      }

      val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
      val request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "VYBE-Android-Updater/$INSTALLED_VERSION")
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext when (response.code) {
            404 -> Result.failure(
              Exception("No published GitHub Releases were found for '$cleanRepo' (or the repository is private). Publish a Release on GitHub to distribute APK updates.")
            )
            403, 429 -> Result.failure(
              Exception("GitHub API rate limit reached (HTTP ${response.code}). Please wait a few minutes and try again.")
            )
            else -> Result.failure(
              Exception("Update check failed with HTTP ${response.code}. Verify the repository name and try again.")
            )
          }
        }

        val bodyStr = response.body?.string()
          ?: return@withContext Result.failure(Exception("GitHub returned an empty release response."))

        val json = JSONObject(bodyStr)
        val tagName = json.optString("tag_name", "").ifBlank { "latest" }
        val name = json.optString("name", tagName).ifBlank { tagName }
        val body = json.optString("body", "Release notes not provided.")
        val htmlUrl = json.optString("html_url", "https://github.com/$cleanRepo/releases")

        var selectedDownloadUrl: String? = null
        var selectedApkName: String? = null
        var selectedApkSize: Long = 0L
        var hasValidApk = false

        val assets = json.optJSONArray("assets")
        if (assets != null && assets.length() > 0) {
          val apkCandidates = mutableListOf<Triple<String, String, Long>>()
          for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val assetName = asset.optString("name", "")
            val assetUrl = asset.optString("browser_download_url", "")
            val assetSize = asset.optLong("size", 0L)
            if (assetName.endsWith(".apk", ignoreCase = true) && assetUrl.isNotBlank()) {
              apkCandidates.add(Triple(assetName, assetUrl, assetSize))
            }
          }

          if (apkCandidates.isNotEmpty()) {
            // Prioritize APKs that are non-empty and not marked as unsigned
            val preferred = apkCandidates.firstOrNull { (assetName, _, size) ->
              size > 0 && !assetName.contains("unsigned", ignoreCase = true)
            } ?: apkCandidates.firstOrNull { (_, _, size) -> size > 0 } ?: apkCandidates.first()

            selectedApkName = preferred.first
            selectedDownloadUrl = preferred.second
            selectedApkSize = preferred.third
            hasValidApk = true
          }
        }

        val assetStatusMessage = when {
          !hasValidApk -> "Release found, but no installable .apk asset is attached to this release. The release author must attach the compiled APK (e.g. app-debug.apk) to the release assets."
          selectedApkName?.contains("unsigned", ignoreCase = true) == true -> "Warning: The asset '$selectedApkName' appears to be unsigned. Android requires updates to be signed with the matching key."
          else -> null
        }

        val downloadUrl = if (hasValidApk) selectedDownloadUrl else htmlUrl
        val isNewer = isNewerVersion(INSTALLED_VERSION, tagName)

        Result.success(
          GitHubReleaseInfo(
            tagName = tagName,
            name = name,
            body = body,
            htmlUrl = htmlUrl,
            downloadUrl = downloadUrl,
            isNewerThanInstalled = isNewer,
            apkFileName = selectedApkName,
            apkFileSize = selectedApkSize,
            hasValidApkAsset = hasValidApk,
            assetStatusMessage = assetStatusMessage
          )
        )
      }
    } catch (_: UnknownHostException) {
      Result.failure(Exception("No internet connection. Connect to Wi-Fi or mobile data and try checking for updates again."))
    } catch (_: SocketTimeoutException) {
      Result.failure(Exception("Connection to GitHub timed out. Check your internet connection and try again."))
    } catch (e: IOException) {
      Result.failure(Exception("Network error while checking for updates: ${e.localizedMessage ?: "Unable to reach GitHub."}"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

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

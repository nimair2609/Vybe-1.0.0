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
  val isNewerThanInstalled: Boolean = true
)

object GitHubUpdater {
  const val INSTALLED_VERSION = "2.0.0"
  const val DEFAULT_GITHUB_REPO = "nrnext/vybe"

  private val client = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .build()

  suspend fun checkForUpdates(repo: String = DEFAULT_GITHUB_REPO): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
    try {
      val cleanRepo = repo
        .trim()
        .removePrefix("https://github.com/")
        .removePrefix("http://github.com/")
        .removePrefix("github.com/")
        .trim('/')

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

        var downloadUrl: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
          for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val assetName = asset.optString("name", "")
            if (assetName.endsWith(".apk", ignoreCase = true)) {
              downloadUrl = asset.optString("browser_download_url", null)
              break
            }
          }
        }
        if (downloadUrl == null) {
          downloadUrl = htmlUrl
        }

        val normalizedTag = tagName.removePrefix("v").removePrefix("V").trim()
        val isNewer = normalizedTag != INSTALLED_VERSION

        Result.success(
          GitHubReleaseInfo(
            tagName = tagName,
            name = name,
            body = body,
            htmlUrl = htmlUrl,
            downloadUrl = downloadUrl,
            isNewerThanInstalled = isNewer
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

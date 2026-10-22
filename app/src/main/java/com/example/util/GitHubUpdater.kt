package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GitHubReleaseInfo(
  val tagName: String,
  val name: String,
  val body: String,
  val htmlUrl: String,
  val downloadUrl: String?
)

object GitHubUpdater {
  private val client = OkHttpClient()

  suspend fun checkForUpdates(repo: String): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
    try {
      val cleanRepo = repo.trim().removePrefix("https://github.com/").removePrefix("github.com/").trim('/')
      if (!cleanRepo.contains("/")) {
        return@withContext Result.failure(Exception("Please specify repository as 'owner/repo' (e.g. yourname/vybe)"))
      }
      val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
      val request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github.v3+json")
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        if (response.code == 404) {
          return@withContext Result.failure(Exception("No releases found on GitHub repo '$cleanRepo'. Publish a release on GitHub to enable updates."))
        }
        return@withContext Result.failure(Exception("GitHub responded with HTTP ${response.code}"))
      }
      val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
      val json = JSONObject(bodyStr)
      val tagName = json.optString("tag_name", "latest")
      val name = json.optString("name", tagName)
      val body = json.optString("body", "Latest build release.")
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

      Result.success(GitHubReleaseInfo(tagName, name, body, htmlUrl, downloadUrl))
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

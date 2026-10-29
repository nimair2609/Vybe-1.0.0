package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.squareup.moshi.Json
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class GitHubAssetDto(
  @param:Json(name = "name") val name: String? = null,
  @param:Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
  @param:Json(name = "size") val size: Long? = null,
  @param:Json(name = "updated_at") val updatedAt: String? = null,
  @param:Json(name = "created_at") val createdAt: String? = null
)

data class GitHubReleaseDto(
  @param:Json(name = "tag_name") val tagName: String? = null,
  @param:Json(name = "name") val name: String? = null,
  @param:Json(name = "body") val body: String? = null,
  @param:Json(name = "html_url") val htmlUrl: String? = null,
  @param:Json(name = "draft") val draft: Boolean? = null,
  @param:Json(name = "prerelease") val prerelease: Boolean? = null,
  @param:Json(name = "published_at") val publishedAt: String? = null,
  @param:Json(name = "created_at") val createdAt: String? = null,
  @param:Json(name = "assets") val assets: List<GitHubAssetDto>? = null
)

data class GitHubCommitterDto(
  @param:Json(name = "date") val date: String? = null
)

data class GitHubCommitDetailDto(
  @param:Json(name = "committer") val committer: GitHubCommitterDto? = null
)

data class GitHubCommitItemDto(
  @param:Json(name = "sha") val sha: String? = null,
  @param:Json(name = "commit") val commit: GitHubCommitDetailDto? = null
)

data class GitHubWorkflowRunDto(
  @param:Json(name = "run_number") val runNumber: Int? = null,
  @param:Json(name = "status") val status: String? = null,
  @param:Json(name = "conclusion") val conclusion: String? = null,
  @param:Json(name = "html_url") val htmlUrl: String? = null
)

data class GitHubWorkflowRunsEnvelopeDto(
  @param:Json(name = "workflow_runs") val workflowRuns: List<GitHubWorkflowRunDto>? = null
)

data class GitHubReleaseInfo(
  val tagName: String,
  val name: String,
  val body: String,
  val htmlUrl: String,
  val downloadUrl: String?,
  val isNewerThanInstalled: Boolean = true,
  val isOlderThanInstalled: Boolean = false,
  val apkFileName: String? = null,
  val apkFileSize: Long = 0L,
  val hasValidApkAsset: Boolean = false,
  val assetStatusMessage: String? = null,
  val publishedAt: String? = null,
  val hasUnpublishedNewerCommits: Boolean = false,
  val latestCommitShortSha: String? = null,
  val latestSuccessfulActionBuildNumber: Int? = null,
  val latestActionRunUrl: String? = null
)

enum class ApkCompatibilityStatus {
  COMPATIBLE_UPDATE,
  SIGNATURE_MISMATCH,
  VERSION_DOWNGRADE,
  DIFFERENT_PACKAGE_ID,
  INVALID_APK
}

data class ApkInspectionReport(
  val status: ApkCompatibilityStatus,
  val apkFile: File,
  val apkPackageName: String,
  val installedPackageName: String,
  val apkVersionName: String,
  val installedVersionName: String,
  val apkVersionCode: Long,
  val installedVersionCode: Long,
  val apkSignatureShort: String?,
  val installedSignatureShort: String?,
  val savedDownloadsFileName: String? = null,
  val userSummary: String
)

object GitHubUpdater {
  val INSTALLED_VERSION: String
    get() = try {
      com.example.BuildConfig.VERSION_NAME
    } catch (_: Throwable) {
      "1.2.62"
    }

  val INSTALLED_VERSION_CODE: Long
    get() = try {
      com.example.BuildConfig.VERSION_CODE.toLong()
    } catch (_: Throwable) {
      162L
    }

  const val DEFAULT_GITHUB_REPO = "nimair2609/Vybe-1.0.0"
  private const val LEGACY_PLACEHOLDER_REPO = "nrnext/vybe"
  private const val PREFS_NAME = "vybe_updater_prefs"
  private const val KEY_SAVED_REPO = "github_release_repo"
  private const val APK_UPDATES_DIR = "apk_updates"

  private fun safeLogWarn(msg: String) {
    try {
      android.util.Log.w("GitHubUpdater", msg)
    } catch (_: Throwable) {
    }
  }

  fun getSavedRepo(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val stored = prefs.getString(KEY_SAVED_REPO, null)?.trim().orEmpty()
    if (stored.isBlank() || stored.equals(LEGACY_PLACEHOLDER_REPO, ignoreCase = true)) {
      return DEFAULT_GITHUB_REPO
    }
    return stored
  }

  fun saveRepo(context: Context, repo: String) {
    val cleaned = cleanRepoSlug(repo)
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (cleaned.isNotBlank() && cleaned.contains("/")) {
      prefs.edit().putString(KEY_SAVED_REPO, cleaned).apply()
    }
  }

  fun resetSavedRepo(context: Context): String {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_SAVED_REPO, DEFAULT_GITHUB_REPO)
      .apply()
    return DEFAULT_GITHUB_REPO
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(Locale.US, mb)
  }

  private val apiClient = OkHttpClient.Builder()
    .dns(okhttp3.Dns.SYSTEM)
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .cache(null)
    .build()

  private val downloadClient = OkHttpClient.Builder()
    .dns(okhttp3.Dns.SYSTEM)
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .cache(null)
    .build()

  fun cleanRepoSlug(repo: String): String {
    return repo
      .trim()
      .removePrefix("https://github.com/")
      .removePrefix("http://github.com/")
      .removePrefix("github.com/")
      .removeSuffix(".git")
      .substringBefore("/releases")
      .substringBefore("/actions")
      .substringBefore("/tree/")
      .trim('/')
  }

  /**
   * Compares two semantic version strings (e.g. "1.2.56" vs "v1.2.62").
   * Returns > 0 if v1 > v2, < 0 if v1 < v2, and 0 if equal.
   */
  fun compareVersions(v1: String, v2: String): Int {
    val clean1 = v1.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")
    val clean2 = v2.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")
    if (clean1.equals(clean2, ignoreCase = true)) return 0

    val parts1 = clean1.split(".").map { segment ->
      segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
    val parts2 = clean2.split(".").map { segment ->
      segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
      val p1 = parts1.getOrElse(i) { 0 }
      val p2 = parts2.getOrElse(i) { 0 }
      if (p1 != p2) return p1.compareTo(p2)
    }
    return 0
  }

  fun isNewerVersion(installedVersion: String, remoteVersion: String): Boolean {
    val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    if (cleanRemote.isBlank() || cleanRemote.equals("latest", ignoreCase = true)) return false
    return compareVersions(remoteVersion, installedVersion) > 0
  }

  fun isOlderVersion(installedVersion: String, remoteVersion: String): Boolean {
    val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    if (cleanRemote.isBlank() || cleanRemote.equals("latest", ignoreCase = true)) return false
    return compareVersions(remoteVersion, installedVersion) < 0
  }

  private data class ApkAssetCandidate(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val updatedAt: String
  )

  private val releasesListAdapter by lazy {
    val type = Types.newParameterizedType(List::class.java, GitHubReleaseDto::class.java)
    com.example.data.remote.SupabaseNetworkClient.moshi.adapter<List<GitHubReleaseDto>>(type)
  }

  private val commitsListAdapter by lazy {
    val type = Types.newParameterizedType(List::class.java, GitHubCommitItemDto::class.java)
    com.example.data.remote.SupabaseNetworkClient.moshi.adapter<List<GitHubCommitItemDto>>(type)
  }

  private val runsEnvelopeAdapter by lazy {
    com.example.data.remote.SupabaseNetworkClient.moshi.adapter(GitHubWorkflowRunsEnvelopeDto::class.java)
  }

  fun parseAndSelectBestRelease(
    releasesArrayStr: String,
    cleanRepo: String,
    installedVersion: String = INSTALLED_VERSION,
    latestCommitIso: String? = null,
    latestCommitSha: String? = null,
    latestSuccessfulRunNumber: Int? = null,
    latestActionRunUrl: String? = null
  ): GitHubReleaseInfo? {
    val parsedList = releasesListAdapter.fromJson(releasesArrayStr).orEmpty()
    val publishedReleases = parsedList.filter { it.draft != true }
    if (publishedReleases.isEmpty()) return null

    fun hasApk(rel: GitHubReleaseDto): Boolean {
      return rel.assets.orEmpty().any { asset ->
        val n = asset.name.orEmpty()
        val u = asset.browserDownloadUrl.orEmpty()
        n.endsWith(".apk", ignoreCase = true) && u.isNotBlank()
      }
    }

    val bestRelease = publishedReleases.sortedWith { a, b ->
      val aHasApk = hasApk(a)
      val bHasApk = hasApk(b)
      if (aHasApk != bHasApk) {
        return@sortedWith if (bHasApk) 1 else -1
      }
      val tagCmp = compareVersions(b.tagName.orEmpty(), a.tagName.orEmpty())
      if (tagCmp != 0) {
        return@sortedWith tagCmp
      }
      val dateA = a.publishedAt ?: a.createdAt ?: ""
      val dateB = b.publishedAt ?: b.createdAt ?: ""
      dateB.compareTo(dateA)
    }.first()

    return parseSingleReleaseObject(
      dto = bestRelease,
      cleanRepo = cleanRepo,
      installedVersion = installedVersion,
      latestCommitIso = latestCommitIso,
      latestCommitSha = latestCommitSha,
      latestSuccessfulRunNumber = latestSuccessfulRunNumber,
      latestActionRunUrl = latestActionRunUrl
    )
  }

  private fun parseSingleReleaseObject(
    dto: GitHubReleaseDto,
    cleanRepo: String,
    installedVersion: String,
    latestCommitIso: String?,
    latestCommitSha: String?,
    latestSuccessfulRunNumber: Int?,
    latestActionRunUrl: String?
  ): GitHubReleaseInfo {
    val tagName = dto.tagName.orEmpty().ifBlank { "latest" }
    val cleanTagVer = tagName.trim().removePrefix("v").removePrefix("V")
    val releaseBuildNum = cleanTagVer.substringAfterLast(".", "").takeWhile { it.isDigit() }.toIntOrNull()
    val name = dto.name.orEmpty().ifBlank { tagName }
    val body = dto.body.orEmpty().ifBlank { "Release notes not provided." }
    val htmlUrl = dto.htmlUrl.orEmpty().ifBlank { "https://github.com/$cleanRepo/releases" }
    val publishedAt = (dto.publishedAt ?: dto.createdAt).orEmpty().ifBlank { null }

    var selectedDownloadUrl: String? = null
    var selectedApkName: String? = null
    var selectedApkSize = 0L
    var hasValidApk = false

    val apkCandidates = dto.assets.orEmpty().mapNotNull { asset ->
      val assetName = asset.name.orEmpty()
      val assetUrl = asset.browserDownloadUrl.orEmpty()
      if (assetName.endsWith(".apk", ignoreCase = true) && assetUrl.isNotBlank()) {
        ApkAssetCandidate(
          name = assetName,
          downloadUrl = assetUrl,
          size = asset.size ?: 0L,
          updatedAt = asset.updatedAt ?: asset.createdAt ?: ""
        )
      } else {
        null
      }
    }

    if (apkCandidates.isNotEmpty()) {
      val preferred = apkCandidates.sortedWith { a, b ->
        val aValidSigned = a.size > 0 && !a.name.contains("unsigned", ignoreCase = true)
        val bValidSigned = b.size > 0 && !b.name.contains("unsigned", ignoreCase = true)
        if (aValidSigned != bValidSigned) return@sortedWith if (bValidSigned) 1 else -1

        val aMatchesTag = cleanTagVer.isNotBlank() && a.name.contains(cleanTagVer, ignoreCase = true)
        val bMatchesTag = cleanTagVer.isNotBlank() && b.name.contains(cleanTagVer, ignoreCase = true)
        if (aMatchesTag != bMatchesTag) return@sortedWith if (bMatchesTag) 1 else -1

        b.updatedAt.compareTo(a.updatedAt)
      }.first()

      selectedApkName = preferred.name
      selectedDownloadUrl = preferred.downloadUrl
      selectedApkSize = preferred.size
      hasValidApk = true
    }

    val isNewer = isNewerVersion(installedVersion, tagName)
    val isOlder = isOlderVersion(installedVersion, tagName)
    val hasNewerActionBuild = latestSuccessfulRunNumber != null &&
      releaseBuildNum != null &&
      latestSuccessfulRunNumber > releaseBuildNum
    val hasNewerCommits = hasNewerActionBuild || (
      !latestCommitIso.isNullOrBlank() &&
        !publishedAt.isNullOrBlank() &&
        latestCommitIso > publishedAt
      )
    val shortSha = latestCommitSha?.take(7)?.takeIf { it.isNotBlank() }

    val assetStatusMessage = when {
      !hasValidApk ->
        "Release $tagName was found, but no installable .apk file is attached to its assets."
      selectedApkName?.contains("unsigned", ignoreCase = true) == true ->
        "Warning: '$selectedApkName' is unsigned. Android requires updates to be signed with a matching key."
      hasNewerActionBuild && isOlder ->
        "Published GitHub Release ($tagName) is older than your installed v$installedVersion and GitHub Actions Build #$latestSuccessfulRunNumber. Push the restored Build & Release workflow so future builds automatically publish to GitHub Releases."
      hasNewerActionBuild ->
        "Note: GitHub Actions Build #$latestSuccessfulRunNumber finished more recently than published Release $tagName. Push the updated repository workflow so Build #${latestSuccessfulRunNumber + 1}+ publishes directly to Releases."
      isOlder && hasNewerCommits ->
        "GitHub Release $tagName is older than your installed v$installedVersion, and newer commits (${shortSha ?: "recent"}) have not been published as a Release yet. Push the updated repository workflow to auto-publish new APK releases."
      isOlder ->
        "Published GitHub Release ($tagName) is older than your installed v$installedVersion."
      else -> null
    }

    return GitHubReleaseInfo(
      tagName = tagName,
      name = name,
      body = body,
      htmlUrl = htmlUrl,
      downloadUrl = if (hasValidApk) selectedDownloadUrl else htmlUrl,
      isNewerThanInstalled = isNewer,
      isOlderThanInstalled = isOlder,
      apkFileName = selectedApkName,
      apkFileSize = selectedApkSize,
      hasValidApkAsset = hasValidApk,
      assetStatusMessage = assetStatusMessage,
      publishedAt = publishedAt,
      hasUnpublishedNewerCommits = hasNewerCommits,
      latestCommitShortSha = shortSha,
      latestSuccessfulActionBuildNumber = latestSuccessfulRunNumber,
      latestActionRunUrl = latestActionRunUrl
    )
  }

  private fun fetchLatestBranchCommitMeta(cleanRepo: String): Pair<String?, String?> {
    return try {
      val req = Request.Builder()
        .url("https://api.github.com/repos/$cleanRepo/commits?per_page=1")
        .header("Accept", "application/vnd.github+json")
        .header("Cache-Control", "no-cache, no-store, max-age=0")
        .header("User-Agent", "VYBE-Android-Updater/$INSTALLED_VERSION")
        .build()
      apiClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null to null
        val commits = commitsListAdapter.fromJson(resp.body?.string().orEmpty()).orEmpty()
        val first = commits.firstOrNull() ?: return null to null
        val sha = first.sha?.ifBlank { null }
        val date = first.commit?.committer?.date?.ifBlank { null }
        date to sha
      }
    } catch (_: Exception) {
      null to null
    }
  }

  private fun fetchLatestSuccessfulWorkflowRun(cleanRepo: String): Pair<Int?, String?> {
    return try {
      val req = Request.Builder()
        .url("https://api.github.com/repos/$cleanRepo/actions/runs?per_page=5")
        .header("Accept", "application/vnd.github+json")
        .header("Cache-Control", "no-cache, no-store, max-age=0")
        .header("User-Agent", "VYBE-Android-Updater/$INSTALLED_VERSION")
        .build()
      apiClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null to null
        val env = runsEnvelopeAdapter.fromJson(resp.body?.string().orEmpty())
        val latestSuccess = env?.workflowRuns.orEmpty().firstOrNull {
          it.status.equals("completed", ignoreCase = true) && it.conclusion.equals("success", ignoreCase = true)
        } ?: return null to null
        latestSuccess.runNumber to latestSuccess.htmlUrl
      }
    } catch (_: Exception) {
      null to null
    }
  }

  suspend fun checkForUpdates(repo: String = DEFAULT_GITHUB_REPO): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
    try {
      val cleanRepo = cleanRepoSlug(repo)

      if (
        cleanRepo.isBlank() ||
        !cleanRepo.contains("/") ||
        cleanRepo.count { it == '/' } != 1 ||
        cleanRepo.equals("my-github-username/vybe", ignoreCase = true)
      ) {
        return@withContext Result.failure(
          IllegalArgumentException("Enter a valid GitHub repository in 'owner/repo' format (for example, '$DEFAULT_GITHUB_REPO').")
        )
      }

      val releasesUrl = "https://api.github.com/repos/$cleanRepo/releases?per_page=20"
      val request = Request.Builder()
        .url(releasesUrl)
        .header("Accept", "application/vnd.github+json")
        .header("Cache-Control", "no-cache, no-store, max-age=0")
        .header("Pragma", "no-cache")
        .header("User-Agent", "VYBE-Android-Updater/$INSTALLED_VERSION")
        .build()

      apiClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext when (response.code) {
            404 -> Result.failure(
              Exception("Repository '$cleanRepo' was not found on GitHub (or is private). Verify the owner/repo name.")
            )
            403, 429 -> Result.failure(
              Exception("GitHub API rate limit reached (HTTP ${response.code}). Wait a minute and try again.")
            )
            else -> Result.failure(
              Exception("Update check failed with HTTP ${response.code}. Verify '$cleanRepo' and try again.")
            )
          }
        }

        val bodyStr = response.body?.string()
          ?: return@withContext Result.failure(Exception("GitHub returned an empty release list."))

        val (latestCommitIso, latestCommitSha) = fetchLatestBranchCommitMeta(cleanRepo)
        val (latestRunNumber, latestRunUrl) = fetchLatestSuccessfulWorkflowRun(cleanRepo)
        val best = parseAndSelectBestRelease(
          releasesArrayStr = bodyStr,
          cleanRepo = cleanRepo,
          installedVersion = INSTALLED_VERSION,
          latestCommitIso = latestCommitIso,
          latestCommitSha = latestCommitSha,
          latestSuccessfulRunNumber = latestRunNumber,
          latestActionRunUrl = latestRunUrl
        )

        if (best != null) {
          Result.success(best)
        } else {
          Result.failure(
            Exception("No published GitHub Releases exist yet in '$cleanRepo'. Push the latest code to GitHub so GitHub Actions automatically publishes a signed APK Release.")
          )
        }
      }
    } catch (_: UnknownHostException) {
      Result.failure(Exception("No internet connection or DNS blocked. Connect to Wi-Fi or mobile data and try again."))
    } catch (_: SocketTimeoutException) {
      Result.failure(Exception("Connection to GitHub timed out. Check your internet connection and try again."))
    } catch (e: IOException) {
      Result.failure(Exception("Network error while checking GitHub Releases: ${e.localizedMessage ?: "Unable to reach GitHub."}"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun downloadApkWithProgress(
    context: Context,
    downloadUrl: String,
    tagName: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
  ): Result<File> = withContext(Dispatchers.IO) {
    val updatesDir = File(context.cacheDir, APK_UPDATES_DIR)
    if (!updatesDir.exists()) {
      updatesDir.mkdirs()
    }
    updatesDir.listFiles()?.forEach { old ->
      runCatching { old.delete() }
    }

    val safeTag = tagName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "latest" }
    val partFile = File(updatesDir, "VYBE-${safeTag}.apk.part")
    val finalFile = File(updatesDir, "VYBE-${safeTag}.apk")

    try {
      val sep = if (downloadUrl.contains("?")) "&" else "?"
      val cacheBustedUrl = "${downloadUrl}${sep}_cb=${System.currentTimeMillis()}"

      val request = Request.Builder()
        .url(cacheBustedUrl)
        .header("Accept", "application/octet-stream,*/*")
        .header("Cache-Control", "no-cache, no-store, max-age=0")
        .header("Pragma", "no-cache")
        .header("User-Agent", "VYBE-Android-Updater/$INSTALLED_VERSION")
        .build()

      downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext Result.failure(
            IOException("Failed to download APK from GitHub (HTTP ${response.code}).")
          )
        }
        val body = response.body
          ?: return@withContext Result.failure(IOException("GitHub returned an empty APK download stream."))

        val totalBytes = body.contentLength()
        var downloadedBytes = 0L
        var lastEmitAt = 0L

        body.byteStream().use { input ->
          FileOutputStream(partFile).use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
              coroutineContext.ensureActive()
              val read = input.read(buffer)
              if (read == -1) break
              output.write(buffer, 0, read)
              downloadedBytes += read
              val now = System.currentTimeMillis()
              if (now - lastEmitAt >= 60L || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                lastEmitAt = now
                onProgress(downloadedBytes, totalBytes)
              }
            }
            output.flush()
          }
        }

        if (downloadedBytes < 1024L) {
          partFile.delete()
          return@withContext Result.failure(IOException("Downloaded file is too small to be a valid APK."))
        }

        val header = ByteArray(4)
        FileInputStream(partFile).use { fis ->
          if (fis.read(header) != 4 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
            partFile.delete()
            return@withContext Result.failure(
              IOException("Downloaded file is not a valid Android APK archive (received web page instead of binary asset).")
            )
          }
        }

        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
          partFile.copyTo(finalFile, overwrite = true)
          partFile.delete()
        }
        onProgress(downloadedBytes, downloadedBytes)
        Result.success(finalFile)
      }
    } catch (e: Exception) {
      runCatching { partFile.delete() }
      Result.failure(e)
    }
  }

  suspend fun inspectApkCompatibility(context: Context, apkFile: File): ApkInspectionReport = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val installedPkg = context.packageName
    val installedInfo = getInstalledPackageInfoWithCerts(pm, installedPkg)
    val installedVerName = installedInfo?.versionName ?: INSTALLED_VERSION
    val installedVerCode = installedInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: INSTALLED_VERSION_CODE
    val installedCertHex = installedInfo?.let { extractSignatureSha256(it) }

    val archiveInfo = getArchivePackageInfoWithCerts(pm, apkFile.absolutePath)
      ?: return@withContext ApkInspectionReport(
        status = ApkCompatibilityStatus.INVALID_APK,
        apkFile = apkFile,
        apkPackageName = "unknown",
        installedPackageName = installedPkg,
        apkVersionName = "unknown",
        installedVersionName = installedVerName,
        apkVersionCode = 0L,
        installedVersionCode = installedVerCode,
        apkSignatureShort = null,
        installedSignatureShort = formatShortCert(installedCertHex),
        userSummary = "The downloaded APK file appears corrupted or unreadable."
      )

    val apkPkg = archiveInfo.packageName.orEmpty()
    val apkVerName = archiveInfo.versionName ?: "unknown"
    val apkVerCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
    val apkCertHex = extractSignatureSha256(archiveInfo)

    if (apkPkg != installedPkg) {
      return@withContext ApkInspectionReport(
        status = ApkCompatibilityStatus.DIFFERENT_PACKAGE_ID,
        apkFile = apkFile,
        apkPackageName = apkPkg,
        installedPackageName = installedPkg,
        apkVersionName = apkVerName,
        installedVersionName = installedVerName,
        apkVersionCode = apkVerCode,
        installedVersionCode = installedVerCode,
        apkSignatureShort = formatShortCert(apkCertHex),
        installedSignatureShort = formatShortCert(installedCertHex),
        userSummary = "This APK uses package ID '$apkPkg' instead of '$installedPkg'. Installing it will create a separate app icon rather than replacing this copy."
      )
    }

    if (
      !apkCertHex.isNullOrBlank() &&
      !installedCertHex.isNullOrBlank() &&
      !apkCertHex.equals(installedCertHex, ignoreCase = true)
    ) {
      val exportName = "VYBE-v${apkVerName.removePrefix("v").removePrefix("V")}.apk"
      val savedName = saveApkToPublicDownloads(context, apkFile, exportName)
      val savedNote = if (savedName != null) {
        "We already saved '$savedName' to your phone's Downloads folder."
      } else {
        "Tap 'Save / Share APK' below to save a copy first."
      }
      return@withContext ApkInspectionReport(
        status = ApkCompatibilityStatus.SIGNATURE_MISMATCH,
        apkFile = apkFile,
        apkPackageName = apkPkg,
        installedPackageName = installedPkg,
        apkVersionName = apkVerName,
        installedVersionName = installedVerName,
        apkVersionCode = apkVerCode,
        installedVersionCode = installedVerCode,
        apkSignatureShort = formatShortCert(apkCertHex),
        installedSignatureShort = formatShortCert(installedCertHex),
        savedDownloadsFileName = savedName,
        userSummary = "Signing Key Mismatch Detected (why Android shows 'Package conflicts with an existing package'): " +
          "Your currently installed VYBE build was signed with key ${formatShortCert(installedCertHex)}, while this GitHub APK is signed with ${formatShortCert(apkCertHex)}. " +
          "$savedNote Uninstall this old build once and open '$exportName' from Downloads — all future GitHub updates will then install seamlessly in-place!"
      )
    }

    if (apkVerCode < installedVerCode) {
      return@withContext ApkInspectionReport(
        status = ApkCompatibilityStatus.VERSION_DOWNGRADE,
        apkFile = apkFile,
        apkPackageName = apkPkg,
        installedPackageName = installedPkg,
        apkVersionName = apkVerName,
        installedVersionName = installedVerName,
        apkVersionCode = apkVerCode,
        installedVersionCode = installedVerCode,
        apkSignatureShort = formatShortCert(apkCertHex),
        installedSignatureShort = formatShortCert(installedCertHex),
        userSummary = "This GitHub Release APK contains an older build (v$apkVerName, versionCode $apkVerCode) than your installed app (v$installedVerName, versionCode $installedVerCode). Android blocks downgrading over a newer build."
      )
    }

    return@withContext ApkInspectionReport(
      status = ApkCompatibilityStatus.COMPATIBLE_UPDATE,
      apkFile = apkFile,
      apkPackageName = apkPkg,
      installedPackageName = installedPkg,
      apkVersionName = apkVerName,
      installedVersionName = installedVerName,
      apkVersionCode = apkVerCode,
      installedVersionCode = installedVerCode,
      apkSignatureShort = formatShortCert(apkCertHex),
      installedSignatureShort = formatShortCert(installedCertHex),
      userSummary = "Verified v$apkVerName (build $apkVerCode) • Package & signing certificate match."
    )
  }

  fun getInstalledSignatureSummary(context: Context): String {
    return try {
      val info = getInstalledPackageInfoWithCerts(context.packageManager, context.packageName)
      val cert = info?.let { extractSignatureSha256(it) }
      formatShortCert(cert) ?: "Standard"
    } catch (_: Exception) {
      "Standard"
    }
  }

  private fun getInstalledPackageInfoWithCerts(pm: PackageManager, packageName: String): PackageInfo? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(
          packageName,
          PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        )
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun getArchivePackageInfoWithCerts(pm: PackageManager, archivePath: String): PackageInfo? {
    return try {
      val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(
          archivePath,
          PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        )
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        @Suppress("DEPRECATION")
        pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNING_CERTIFICATES)
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNATURES)
      }
      if (primary != null && extractSignatureSha256(primary) != null) {
        return primary
      }
      @Suppress("DEPRECATION")
      pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNATURES) ?: primary
    } catch (_: Exception) {
      null
    }
  }

  private fun extractSignatureSha256(packageInfo: PackageInfo): String? {
    return try {
      val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = packageInfo.signingInfo
        val signers = if (signingInfo != null) {
          if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
          else signingInfo.signingCertificateHistory
        } else null
        signers?.firstOrNull()?.toByteArray() ?: run {
          @Suppress("DEPRECATION")
          packageInfo.signatures?.firstOrNull()?.toByteArray()
        }
      } else {
        @Suppress("DEPRECATION")
        packageInfo.signatures?.firstOrNull()?.toByteArray()
      }
      if (certBytes == null || certBytes.isEmpty()) return null
      val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
      digest.joinToString(":") { "%02X".format(Locale.US, it) }
    } catch (_: Exception) {
      null
    }
  }

  private fun formatShortCert(fullSha256: String?): String? {
    if (fullSha256.isNullOrBlank()) return null
    return fullSha256.split(":").take(6).joinToString(":")
  }

  fun saveApkToPublicDownloads(context: Context, sourceApk: File, displayFileName: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return try {
      val resolver = context.contentResolver
      val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, displayFileName)
        put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.Downloads.IS_PENDING, 1)
      }
      val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
      resolver.openOutputStream(uri)?.use { out ->
        FileInputStream(sourceApk).use { input ->
          input.copyTo(out)
        }
      }
      val completeValues = ContentValues().apply {
        put(MediaStore.Downloads.IS_PENDING, 0)
      }
      resolver.update(uri, completeValues, null, null)
      displayFileName
    } catch (e: Exception) {
      safeLogWarn("MediaStore Downloads export failed: ${e.message}")
      null
    }
  }

  fun canInstallPackages(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  fun createUnknownSourcesSettingsIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
      )
    } else {
      Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
  }

  fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> {
    return try {
      val authority = "${context.packageName}.fileprovider"
      val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      Result.success(Unit)
    } catch (e: Exception) {
      safeLogWarn("Failed to launch APK installer: ${e.message}")
      Result.failure(e)
    }
  }

  fun shareOrSaveApkFile(context: Context, apkFile: File) {
    try {
      val authority = "${context.packageName}.fileprovider"
      val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(shareIntent, "Save or Share VYBE APK").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
    } catch (e: Exception) {
      safeLogWarn("Unable to share APK file: ${e.message}")
    }
  }

  fun openAppInfoForCleanInstall(context: Context) {
    try {
      val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      safeLogWarn("Unable to open App Info: ${e.message}")
    }
  }

  fun openDownloadUrl(context: Context, url: String) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      safeLogWarn("Unable to open download URL: ${e.message}")
    }
  }
}

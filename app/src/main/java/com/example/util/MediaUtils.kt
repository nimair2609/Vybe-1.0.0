package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class LocalMediaPayload(
  val bytes: ByteArray,
  val mimeType: String,
  val extension: String,
  val isVideo: Boolean,
  val aspectRatio: Float?
)

object MediaUtils {

  fun saveBitmapToCache(context: Context, bitmap: Bitmap, prefix: String = "vybe_img"): Uri {
    val dir = File(context.cacheDir, "camera_media").apply { mkdirs() }
    val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    }
    return try {
      val authority = "${context.packageName}.fileprovider"
      FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
      Uri.fromFile(file)
    }
  }

  fun createTempImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_media").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    val authority = "${context.packageName}.fileprovider"
    return try {
      FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
      Uri.fromFile(file)
    }
  }

  fun createTempVideoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_media").apply { mkdirs() }
    val file = File(dir, "vid_${System.currentTimeMillis()}.mp4")
    val authority = "${context.packageName}.fileprovider"
    return try {
      FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
      Uri.fromFile(file)
    }
  }

  fun detectMimeType(context: Context, uri: Uri): String {
    val resolverMime = try {
      context.contentResolver.getType(uri)
    } catch (_: Exception) {
      null
    }
    if (!resolverMime.isNullOrBlank()) return resolverMime

    val lower = uri.toString().lowercase()
    return when {
      lower.endsWith(".mp4") || lower.contains("vid_") || lower.contains("/video") -> "video/mp4"
      lower.endsWith(".webm") -> "video/webm"
      lower.endsWith(".mov") -> "video/quicktime"
      lower.endsWith(".png") -> "image/png"
      lower.endsWith(".webp") -> "image/webp"
      else -> "image/jpeg"
    }
  }

  fun isVideoUri(context: Context?, uriString: String): Boolean {
    val lower = uriString.lowercase()
    if (
      lower.endsWith(".mp4") ||
      lower.endsWith(".webm") ||
      lower.endsWith(".mov") ||
      lower.endsWith(".mkv") ||
      lower.contains(".mp4?") ||
      lower.contains(".webm?") ||
      lower.contains(".mov?") ||
      lower.contains("/video/")
    ) {
      return true
    }
    if (context != null && (lower.startsWith("content://") || lower.startsWith("file://"))) {
      val mime = detectMimeType(context, Uri.parse(uriString))
      return mime.startsWith("video/", ignoreCase = true)
    }
    return false
  }

  private fun extensionForMime(mimeType: String, uriString: String): String {
    val lower = uriString.lowercase()
    val fromMap = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    if (!fromMap.isNullOrBlank()) return fromMap
    return when {
      mimeType.startsWith("video/webm") || lower.endsWith(".webm") -> "webm"
      mimeType.startsWith("video/quicktime") || lower.endsWith(".mov") -> "mov"
      mimeType.startsWith("video/") || lower.endsWith(".mp4") -> "mp4"
      mimeType == "image/png" || lower.endsWith(".png") -> "png"
      mimeType == "image/webp" || lower.endsWith(".webp") -> "webp"
      else -> "jpg"
    }
  }

  /**
   * Copies selected photo or video URI to app-private staging storage while preserving
   * the true media extension (.mp4, .png, .jpg, etc.) so local previews and uploads never break.
   * Original file is preserved until publishing succeeds.
   */
  fun copyUriToLocalCache(context: Context, uri: Uri, prefix: String = "vybe_media"): String {
    return try {
      val dir = File(context.filesDir, "user_media").apply { mkdirs() }
      val mime = detectMimeType(context, uri)
      val ext = extensionForMime(mime, uri.toString())
      val destFile = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
      context.contentResolver.openInputStream(uri)?.use { input ->
        destFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }
      if (destFile.exists() && destFile.length() > 0L) {
        Uri.fromFile(destFile).toString()
      } else {
        uri.toString()
      }
    } catch (e: Exception) {
      uri.toString()
    }
  }

  /**
   * Reads bytes, MIME type, and natural aspect ratio (width / height) for local URIs (`content://` or `file://`).
   */
  fun readMediaPayload(context: Context, uriString: String): LocalMediaPayload? {
    return try {
      val uri = Uri.parse(uriString)
      val mimeType = detectMimeType(context, uri)
      val isVideo = mimeType.startsWith("video/", ignoreCase = true) || isVideoUri(context, uriString)
      val resolvedMime = if (isVideo && !mimeType.startsWith("video/")) "video/mp4" else mimeType
      val ext = extensionForMime(resolvedMime, uriString)

      val bytes: ByteArray? = when (uri.scheme?.lowercase()) {
        "file" -> {
          val file = File(uri.path ?: return null)
          if (file.exists()) FileInputStream(file).use { it.readBytes() } else null
        }
        "content" -> {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        else -> {
          val directFile = File(uriString)
          if (directFile.exists()) FileInputStream(directFile).use { it.readBytes() } else null
        }
      }

      if (bytes == null || bytes.isEmpty()) return null
      val aspectRatio = computeAspectRatio(context, uri, bytes, isVideo)

      LocalMediaPayload(
        bytes = bytes,
        mimeType = resolvedMime,
        extension = ext,
        isVideo = isVideo,
        aspectRatio = aspectRatio
      )
    } catch (_: Exception) {
      null
    }
  }

  fun computeAspectRatio(
    context: Context,
    uri: Uri,
    bytes: ByteArray? = null,
    isVideo: Boolean = false
  ): Float? {
    return try {
      if (isVideo) {
        val retriever = MediaMetadataRetriever()
        try {
          if (uri.scheme == "file" && uri.path != null) {
            retriever.setDataSource(uri.path)
          } else {
            retriever.setDataSource(context, uri)
          }
          val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
          val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
          val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
          if (rawW != null && rawH != null && rawW > 0f && rawH > 0f) {
            val rotated = rotation == 90 || rotation == 270
            val w = if (rotated) rawH else rawW
            val h = if (rotated) rawW else rawH
            (w / h).coerceIn(0.45f, 2.4f)
          } else null
        } finally {
          try { retriever.release() } catch (_: Exception) {}
        }
      } else if (bytes != null) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) {
          (opts.outWidth.toFloat() / opts.outHeight.toFloat()).coerceIn(0.5f, 2.2f)
        } else null
      } else {
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun extractMediaAspectRatio(context: Context, uri: Uri, isVideo: Boolean = false): Float? {
    return try {
      if (isVideo) {
        computeAspectRatio(context, uri, bytes = null, isVideo = true)
      } else {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (uri.scheme == "file" && uri.path != null) {
          BitmapFactory.decodeFile(uri.path, opts)
        } else {
          context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, opts)
          }
        }
        if (opts.outWidth > 0 && opts.outHeight > 0) {
          (opts.outWidth.toFloat() / opts.outHeight.toFloat()).coerceIn(0.5f, 2.2f)
        } else null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun inspectMediaAspectRatio(context: Context, uriString: String): Float? {
    return try {
      val uri = Uri.parse(uriString)
      val video = isVideoUri(context, uriString)
      extractMediaAspectRatio(context, uri, isVideo = video)
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Safely removes staged local cache file ONLY after cloud upload + DB publish succeed.
   */
  fun cleanupStagedFileIfOwned(context: Context, uriString: String) {
    try {
      if (!uriString.startsWith("file://")) return
      val path = Uri.parse(uriString).path ?: return
      val file = File(path)
      val stagingRoot = File(context.filesDir, "user_media").canonicalPath
      if (file.exists() && file.canonicalPath.startsWith(stagingRoot)) {
        file.delete()
      }
    } catch (_: Exception) {
    }
  }
}

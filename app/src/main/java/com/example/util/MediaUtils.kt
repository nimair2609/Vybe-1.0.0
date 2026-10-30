package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
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

  const val MAX_STORY_POST_BYTES: Long = 100L * 1024L * 1024L // 100 MB (matches Supabase bucket limit)
  const val MAX_CHAT_MEDIA_BYTES: Long = 50L * 1024L * 1024L // 50 MB

  private val ALLOWED_IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/webp")
  private val ALLOWED_VIDEO_MIMES = setOf("video/mp4", "video/webm", "video/quicktime")

  fun saveBitmapToCache(context: Context, bitmap: Bitmap, prefix: String = "vybe_img"): Uri {
    val dir = File(context.cacheDir, "camera_media").apply { mkdirs() }
    val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    return try {
      val authority = "${context.packageName}.fileprovider"
      FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
      Uri.fromFile(file)
    }
  }

  /**
   * Creates a full-resolution camera output File + FileProvider Uripair so `TakePicture`
   * writes original camera hardware bytes directly without Binder thumbnail downscaling.
   */
  fun createTempCameraCaptureFile(context: Context, prefix: String = "capture"): Pair<File, Uri> {
    val dir = File(context.cacheDir, "camera_media").apply { mkdirs() }
    val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    val authority = "${context.packageName}.fileprovider"
    val uri = try {
      FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
      Uri.fromFile(file)
    }
    return file to uri
  }

  fun createTempImageUri(context: Context): Uri {
    return createTempCameraCaptureFile(context, "capture").second
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
    if (!resolverMime.isNullOrBlank() && !resolverMime.equals("application/octet-stream", ignoreCase = true)) {
      return resolverMime.lowercase()
    }

    val lower = uri.toString().lowercase()
    return when {
      lower.endsWith(".mp4") || lower.contains("vid_") || lower.contains("/video") -> "video/mp4"
      lower.endsWith(".webm") -> "video/webm"
      lower.endsWith(".mov") || lower.endsWith(".qt") -> "video/quicktime"
      lower.endsWith(".3gp") || lower.endsWith(".mkv") || lower.endsWith(".m4v") -> "video/mp4"
      lower.endsWith(".png") -> "image/png"
      lower.endsWith(".webp") -> "image/webp"
      lower.endsWith(".heic") -> "image/heic"
      lower.endsWith(".heif") -> "image/heif"
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
      lower.endsWith(".3gp") ||
      lower.endsWith(".m4v") ||
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
    return when (mimeType.lowercase()) {
      "image/jpeg", "image/jpg" -> "jpg"
      "image/png" -> "png"
      "image/webp" -> "webp"
      "video/webm" -> "webm"
      "video/quicktime" -> "mov"
      "video/mp4" -> "mp4"
      else -> {
        val fromMap = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        when {
          !fromMap.isNullOrBlank() -> fromMap
          mimeType.startsWith("video/") || lower.endsWith(".mp4") -> "mp4"
          lower.endsWith(".png") -> "png"
          lower.endsWith(".webp") -> "webp"
          else -> "jpg"
        }
      }
    }
  }

  /**
   * Copies selected photo or video URI to app-private staging storage while preserving
   * the true media extension (.mp4, .png, .jpg, etc.) so local previews and uploads never break.
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
   * Validates media readability, non-zero byte length, and size limit prior to upload.
   */
  fun validateMediaUri(
    context: Context,
    uriString: String,
    maxBytes: Long = MAX_STORY_POST_BYTES,
    preserveRawBytes: Boolean = false
  ): Result<LocalMediaPayload> {
    if (uriString.isBlank()) {
      return Result.failure(IllegalArgumentException("No media file selected."))
    }
    val payload = readMediaPayload(context, uriString, preserveRawBytes = preserveRawBytes)
      ?: return Result.failure(IllegalArgumentException("Selected media file could not be read or is empty."))
    if (payload.bytes.isEmpty()) {
      return Result.failure(IllegalArgumentException("Selected media file is empty (0 bytes)."))
    }
    if (payload.bytes.size.toLong() > maxBytes) {
      val maxMb = maxBytes / (1024L * 1024L)
      return Result.failure(IllegalArgumentException("Media file exceeds the ${maxMb}MB upload limit."))
    }
    return Result.success(payload)
  }

  /**
   * Reads bytes, normalizes MIME type for Supabase Storage compatibility, and computes natural aspect ratio.
   *
   * When [preserveRawBytes] is `true` (used by UNFILTERED camera capture), the exact captured camera bytes
   * are returned untouched with zero decoding, resizing, or JPEG re-compression.
   */
  fun readMediaPayload(
    context: Context,
    uriString: String,
    preserveRawBytes: Boolean = false
  ): LocalMediaPayload? {
    return try {
      val uri = Uri.parse(uriString)
      val rawMime = detectMimeType(context, uri).lowercase()
      val isVideo = rawMime.startsWith("video/", ignoreCase = true) || isVideoUri(context, uriString)

      val rawBytes: ByteArray? = when (uri.scheme?.lowercase()) {
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

      if (rawBytes == null || rawBytes.isEmpty()) return null

      if (preserveRawBytes) {
        // UNFILTERED path: preserve 100% original camera bytes without any re-encoding or compression
        val normalizedHeaderMime = when {
          isVideo && rawMime in ALLOWED_VIDEO_MIMES -> rawMime
          isVideo -> "video/mp4"
          rawMime == "image/png" -> "image/png"
          rawMime == "image/webp" -> "image/webp"
          else -> "image/jpeg"
        }
        val ext = extensionForMime(normalizedHeaderMime, uriString)
        val aspectRatio = computeAspectRatio(context, uri, rawBytes, isVideo)
        return LocalMediaPayload(
          bytes = rawBytes,
          mimeType = normalizedHeaderMime,
          extension = ext,
          isVideo = isVideo,
          aspectRatio = aspectRatio
        )
      }

      // Normalize MIME types for Supabase Storage buckets (stories, posts, chat-media)
      var finalBytes = rawBytes
      val finalMime: String = if (isVideo) {
        when {
          rawMime in ALLOWED_VIDEO_MIMES -> rawMime
          rawMime.contains("webm") -> "video/webm"
          rawMime.contains("quicktime") || rawMime.contains("mov") -> "video/quicktime"
          else -> "video/mp4"
        }
      } else {
        when {
          rawMime == "image/jpg" || rawMime == "image/jpeg" -> "image/jpeg"
          rawMime in ALLOWED_IMAGE_MIMES -> rawMime
          else -> {
            // Transcode HEIC / HEIF / AVIF / BMP to high-fidelity JPEG so Supabase Storage accepts it
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (decoded != null) {
              val out = ByteArrayOutputStream()
              decoded.compress(Bitmap.CompressFormat.JPEG, 95, out)
              val converted = out.toByteArray()
              if (converted.isNotEmpty()) {
                finalBytes = converted
              }
            }
            "image/jpeg"
          }
        }
      }

      val ext = extensionForMime(finalMime, uriString)
      val aspectRatio = computeAspectRatio(context, uri, finalBytes, isVideo)

      LocalMediaPayload(
        bytes = finalBytes,
        mimeType = finalMime,
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

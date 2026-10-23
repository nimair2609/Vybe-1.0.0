package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

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

  fun copyUriToLocalCache(context: Context, uri: Uri, prefix: String = "vybe_media"): String {
    return try {
      val dir = File(context.filesDir, "user_media").apply { mkdirs() }
      val uriStr = uri.toString().lowercase()
      val ext = when {
        uriStr.contains(".mp4") || uriStr.contains("video") -> "mp4"
        uriStr.contains(".png") -> "png"
        uriStr.contains(".webp") -> "webp"
        else -> "jpg"
      }
      val destFile = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
      context.contentResolver.openInputStream(uri)?.use { input ->
        destFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }
      Uri.fromFile(destFile).toString()
    } catch (e: Exception) {
      uri.toString()
    }
  }

  fun getBytesAndMimeType(context: Context, pathOrUri: String): Pair<ByteArray, String>? {
    return try {
      val uri = if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
        Uri.parse(pathOrUri)
      } else {
        Uri.fromFile(File(pathOrUri))
      }
      val mimeType = context.contentResolver.getType(uri) ?: when {
        pathOrUri.endsWith(".mp4", true) -> "video/mp4"
        pathOrUri.endsWith(".png", true) -> "image/png"
        pathOrUri.endsWith(".webp", true) -> "image/webp"
        else -> "image/jpeg"
      }
      val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: File(uri.path ?: pathOrUri).readBytes()
      Pair(bytes, mimeType)
    } catch (e: Exception) {
      null
    }
  }
}

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
}

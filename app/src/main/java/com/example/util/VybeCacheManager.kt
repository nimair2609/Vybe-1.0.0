package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import coil.disk.DiskCache
import coil.key.Keyer
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.Options
import com.example.data.remote.SupabaseNetworkClient
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class VybeCacheStats(
  val imageCacheBytes: Long,
  val videoCacheBytes: Long,
  val tempCacheBytes: Long,
  val totalDisposableBytes: Long,
  val videoCacheLimitBytes: Long
)

/**
 * Centralized application-level media cache manager for VYBE.
 *
 * Guarantees:
 * - Strictly ONE Media3 [SimpleCache] instance with configurable bounded LRU eviction (default 500 MB).
 * - Strictly ONE shared Coil [ImageLoader] with persistent disk & memory caching and stable cache keys.
 * - Safe inspection and disposal of image, video, and temporary caches without touching auth tokens,
 *   user preferences, or Supabase database/storage records.
 */
@OptIn(UnstableApi::class)
object VybeCacheManager {

  private const val TAG = "VybeCacheManager"
  private const val PREFS_NAME = "vybe_cache_prefs"
  private const val KEY_VIDEO_CACHE_LIMIT_BYTES = "video_cache_limit_bytes"

  const val DEFAULT_VIDEO_CACHE_LIMIT_BYTES: Long = 500L * 1024L * 1024L // 500 MB
  const val DEFAULT_IMAGE_DISK_CACHE_LIMIT_BYTES: Long = 150L * 1024L * 1024L // 150 MB

  private const val VIDEO_CACHE_DIR = "vybe_video_cache"
  private const val IMAGE_CACHE_DIR = "vybe_image_cache"
  private val TEMP_CACHE_SUBDIRS = listOf("camera_media", "audio_mix_tmp")

  private val lock = Any()

  @Volatile
  private var appContext: Context? = null

  @Volatile
  private var simpleCache: SimpleCache? = null

  @Volatile
  private var databaseProvider: StandaloneDatabaseProvider? = null

  @Volatile
  private var sharedImageLoader: ImageLoader? = null

  @Volatile
  private var configuredVideoLimitBytes: Long = DEFAULT_VIDEO_CACHE_LIMIT_BYTES

  private val activePlayers: MutableSet<ExoPlayer> =
    Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

  fun init(context: Context) {
    val appCtx = context.applicationContext
    synchronized(lock) {
      appContext = appCtx
      val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val savedLimit = prefs.getLong(KEY_VIDEO_CACHE_LIMIT_BYTES, DEFAULT_VIDEO_CACHE_LIMIT_BYTES)
      if (savedLimit in (50L * 1024L * 1024L)..(2048L * 1024L * 1024L)) {
        configuredVideoLimitBytes = savedLimit
      }
    }
  }

  fun getVideoCacheLimitBytes(context: Context): Long {
    init(context)
    return configuredVideoLimitBytes
  }

  /**
   * Updates the bounded disk cache limit for Media3 video playback and reinitializes [SimpleCache] cleanly.
   */
  fun setVideoCacheLimitBytes(context: Context, limitBytes: Long) {
    val clamped = limitBytes.coerceIn(50L * 1024L * 1024L, 2048L * 1024L * 1024L)
    val appCtx = context.applicationContext
    synchronized(lock) {
      appContext = appCtx
      configuredVideoLimitBytes = clamped
      appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_VIDEO_CACHE_LIMIT_BYTES, clamped)
        .apply()
      releaseSimpleCacheLocked()
    }
  }

  /**
   * Produces a canonical, stable cache key for remote URLs (stripping ephemeral signed URL `?token=` params
   * so re-signed Supabase URLs still hit local disk/memory caches across sessions).
   */
  fun stableMediaCacheKey(rawUri: String?): String {
    val value = rawUri?.trim().orEmpty()
    if (value.isBlank()) return ""
    if (!value.startsWith("http://", ignoreCase = true) && !value.startsWith("https://", ignoreCase = true)) {
      return value
    }
    return try {
      val uri = Uri.parse(value)
      val path = uri.path.orEmpty()
      if (path.contains("/storage/v1/object/")) {
        val canonicalPath = path
          .replace("/storage/v1/object/sign/", "/storage/v1/object/")
          .replace("/storage/v1/object/authenticated/", "/storage/v1/object/")
          .replace("/storage/v1/object/public/", "/storage/v1/object/")
        "${uri.scheme}://${uri.host}$canonicalPath"
      } else {
        value.substringBefore("#")
      }
    } catch (_: Exception) {
      value
    }
  }

  /**
   * Returns the single application-wide Media3 [SimpleCache] instance.
   */
  fun getSimpleCache(context: Context): SimpleCache {
    init(context)
    val existing = simpleCache
    if (existing != null) return existing

    synchronized(lock) {
      simpleCache?.let { return it }
      val appCtx = appContext ?: context.applicationContext
      val cacheDir = File(appCtx.cacheDir, VIDEO_CACHE_DIR).apply { mkdirs() }
      val dbProvider = databaseProvider ?: StandaloneDatabaseProvider(appCtx).also {
        databaseProvider = it
      }
      val evictor = LeastRecentlyUsedCacheEvictor(configuredVideoLimitBytes)
      return SimpleCache(cacheDir, evictor, dbProvider).also {
        simpleCache = it
      }
    }
  }

  /**
   * Builds a [CacheDataSource.Factory] backed by the single shared [SimpleCache] and OkHttp network stack.
   * Supports cached replay as well as local `file://` and `content://` URIs seamlessly.
   */
  fun getCacheDataSourceFactory(context: Context): DataSource.Factory {
    val appCtx = context.applicationContext
    val okHttpFactory = OkHttpDataSource.Factory(SupabaseNetworkClient.okHttpClient)
    val upstreamFactory = DefaultDataSource.Factory(appCtx, okHttpFactory)
    val cache = getSimpleCache(appCtx)

    return CacheDataSource.Factory()
      .setCache(cache)
      .setUpstreamDataSourceFactory(upstreamFactory)
      .setCacheKeyFactory(
        CacheKeyFactory { dataSpec ->
          val rawKey = dataSpec.key ?: dataSpec.uri.toString()
          stableMediaCacheKey(rawKey)
        }
      )
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  }

  fun registerActivePlayer(player: ExoPlayer) {
    activePlayers.add(player)
  }

  fun unregisterActivePlayer(player: ExoPlayer) {
    activePlayers.remove(player)
  }

  /**
   * Returns the single shared Coil [ImageLoader] configured with persistent disk & memory caches
   * and stable cache keys.
   */
  fun getImageLoader(context: Context): ImageLoader {
    sharedImageLoader?.let { return it }
    val appCtx = context.applicationContext
    synchronized(lock) {
      sharedImageLoader?.let { return it }
      val imageCacheDir = File(appCtx.cacheDir, IMAGE_CACHE_DIR).apply { mkdirs() }
      return ImageLoader.Builder(appCtx)
        .okHttpClient(SupabaseNetworkClient.okHttpClient)
        .memoryCache {
          MemoryCache.Builder(appCtx)
            .maxSizePercent(0.25)
            .build()
        }
        .diskCache {
          DiskCache.Builder()
            .directory(imageCacheDir)
            .maxSizeBytes(DEFAULT_IMAGE_DISK_CACHE_LIMIT_BYTES)
            .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .respectCacheHeaders(false)
        .components {
          add(
            Keyer<String> { data: String, _: Options ->
              stableMediaCacheKey(data).ifBlank { null }
            }
          )
          add(
            Keyer<Uri> { data: Uri, _: Options ->
              stableMediaCacheKey(data.toString()).ifBlank { null }
            }
          )
        }
        .crossfade(true)
        .build()
        .also { sharedImageLoader = it }
    }
  }

  fun getCacheStats(context: Context): VybeCacheStats {
    val appCtx = context.applicationContext
    init(appCtx)
    val imageBytes = computeDirBytes(File(appCtx.cacheDir, IMAGE_CACHE_DIR)) +
      computeDirBytes(File(appCtx.cacheDir, "image_cache"))
    val videoBytes = computeDirBytes(File(appCtx.cacheDir, VIDEO_CACHE_DIR))
    val tempBytes = computeTempCacheBytes(appCtx)
    return VybeCacheStats(
      imageCacheBytes = imageBytes,
      videoCacheBytes = videoBytes,
      tempCacheBytes = tempBytes,
      totalDisposableBytes = imageBytes + videoBytes + tempBytes,
      videoCacheLimitBytes = configuredVideoLimitBytes
    )
  }

  fun clearImageCache(context: Context): VybeCacheStats {
    val appCtx = context.applicationContext
    synchronized(lock) {
      try {
        sharedImageLoader?.memoryCache?.clear()
        sharedImageLoader?.diskCache?.clear()
      } catch (e: Throwable) {
        Log.w(TAG, "ImageLoader clear notice: ${e.message}")
      }
      deleteDirContents(File(appCtx.cacheDir, IMAGE_CACHE_DIR))
      deleteDirContents(File(appCtx.cacheDir, "image_cache"))
    }
    return getCacheStats(appCtx)
  }

  fun clearVideoCache(context: Context): VybeCacheStats {
    val appCtx = context.applicationContext
    stopActivePlayersSafely()
    synchronized(lock) {
      releaseSimpleCacheLocked()
      val videoDir = File(appCtx.cacheDir, VIDEO_CACHE_DIR)
      deleteDirContents(videoDir)
      videoDir.mkdirs()
    }
    return getCacheStats(appCtx)
  }

  fun clearTempCache(context: Context): VybeCacheStats {
    val appCtx = context.applicationContext
    synchronized(lock) {
      TEMP_CACHE_SUBDIRS.forEach { subdir ->
        deleteDirContents(File(appCtx.cacheDir, subdir))
      }
      appCtx.cacheDir.listFiles()?.forEach { child ->
        if (child.isFile && isDisposableTempFile(child.name)) {
          child.delete()
        }
      }
    }
    return getCacheStats(appCtx)
  }

  fun clearAllDisposableCache(context: Context): VybeCacheStats {
    clearImageCache(context)
    clearVideoCache(context)
    clearTempCache(context)
    return getCacheStats(context)
  }

  private fun stopActivePlayersSafely() {
    val snapshot = synchronized(activePlayers) { activePlayers.toList() }
    if (snapshot.isEmpty()) return
    val stopAction = Runnable {
      snapshot.forEach { player ->
        try {
          player.playWhenReady = false
          player.stop()
          player.clearMediaItems()
        } catch (_: Throwable) {}
      }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      stopAction.run()
    } else {
      val latch = CountDownLatch(1)
      Handler(Looper.getMainLooper()).post {
        try {
          stopAction.run()
        } finally {
          latch.countDown()
        }
      }
      try {
        latch.await(1500L, TimeUnit.MILLISECONDS)
      } catch (_: InterruptedException) {}
    }
  }

  private fun releaseSimpleCacheLocked() {
    simpleCache?.let { cache ->
      try {
        cache.release()
      } catch (e: Throwable) {
        Log.w(TAG, "SimpleCache release notice: ${e.message}")
      }
    }
    simpleCache = null
  }

  private fun computeTempCacheBytes(appCtx: Context): Long {
    var total = 0L
    TEMP_CACHE_SUBDIRS.forEach { subdir ->
      total += computeDirBytes(File(appCtx.cacheDir, subdir))
    }
    appCtx.cacheDir.listFiles()?.forEach { child ->
      if (child.isFile && isDisposableTempFile(child.name)) {
        total += child.length()
      }
    }
    return total
  }

  private fun isDisposableTempFile(fileName: String): Boolean {
    val lower = fileName.lowercase()
    return lower.endsWith(".tmp") ||
      lower.endsWith(".jpg") ||
      lower.endsWith(".webp") ||
      lower.endsWith(".mp4") ||
      lower.startsWith("vybe_tmp") ||
      lower.startsWith("mixed_") ||
      lower.startsWith("story_render")
  }

  private fun computeDirBytes(dir: File): Long {
    if (!dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    var total = 0L
    dir.listFiles()?.forEach { child ->
      total += if (child.isDirectory) computeDirBytes(child) else child.length()
    }
    return total
  }

  private fun deleteDirContents(dir: File) {
    if (!dir.exists()) return
    dir.listFiles()?.forEach { child ->
      if (child.isDirectory) {
        child.deleteRecursively()
      } else {
        child.delete()
      }
    }
  }
}

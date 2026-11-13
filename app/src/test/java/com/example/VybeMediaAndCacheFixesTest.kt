package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.MediaUploadTarget
import com.example.util.MediaUtils
import com.example.util.VybeCacheManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VybeMediaAndCacheFixesTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    VybeCacheManager.init(context)
  }

  @Test
  fun `VybeCacheManager strips ephemeral signed tokens for stable cache keys`() {
    val signed1 = "https://example.supabase.co/storage/v1/object/sign/posts/user1/clip.mp4?token=abc123"
    val signed2 = "https://example.supabase.co/storage/v1/object/sign/posts/user1/clip.mp4?token=xyz999"
    val publicUrl = "https://example.supabase.co/storage/v1/object/public/posts/user1/clip.mp4"

    val k1 = VybeCacheManager.stableMediaCacheKey(signed1)
    val k2 = VybeCacheManager.stableMediaCacheKey(signed2)
    val kPub = VybeCacheManager.stableMediaCacheKey(publicUrl)

    assertEquals("Signed URLs for same object must resolve to identical cache key", k1, k2)
    assertEquals("Public URL and signed URL for same object share canonical key", k1, kPub)
    assertFalse("Cache key must not contain ephemeral token parameter", k1.contains("token="))
  }

  @Test
  fun `VybeCacheManager returns single shared ImageLoader and bounded video limit and safely resets disposable caches`() {
    val loaderA = VybeCacheManager.getImageLoader(context)
    val loaderB = VybeCacheManager.getImageLoader(context)
    assertSame("Coil ImageLoader must be a single shared instance", loaderA, loaderB)

    assertEquals(VybeCacheManager.DEFAULT_VIDEO_CACHE_LIMIT_BYTES, VybeCacheManager.getVideoCacheLimitBytes(context))

    val imgDir = File(context.cacheDir, "vybe_image_cache").apply { mkdirs() }
    val vidDir = File(context.cacheDir, "vybe_video_cache").apply { mkdirs() }
    val tmpDir = File(context.cacheDir, "camera_media").apply { mkdirs() }

    File(imgDir, "img1.bin").writeBytes(ByteArray(2048) { 1 })
    File(vidDir, "vid1.bin").writeBytes(ByteArray(4096) { 2 })
    File(tmpDir, "tmp1.jpg").writeBytes(ByteArray(1024) { 3 })

    val beforeStats = VybeCacheManager.getCacheStats(context)
    assertTrue(beforeStats.imageCacheBytes >= 2048L)
    assertTrue(beforeStats.videoCacheBytes >= 4096L)
    assertTrue(beforeStats.tempCacheBytes >= 1024L)

    val afterStats = VybeCacheManager.clearAllDisposableCache(context)
    assertEquals(0L, afterStats.imageCacheBytes)
    assertEquals(0L, afterStats.videoCacheBytes)
    assertEquals(0L, afterStats.tempCacheBytes)
    assertEquals(0L, afterStats.totalDisposableBytes)
  }

  @Test
  fun `MediaUtils resizes oversized images proportionally per uploadTarget and compresses output`() {
    val hugeBitmap = Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.rgb(42, 128, 220))
    }
    val rawStream = ByteArrayOutputStream()
    hugeBitmap.compress(Bitmap.CompressFormat.PNG, 100, rawStream)
    val rawPngBytes = rawStream.toByteArray()
    hugeBitmap.recycle()

    val tempFile = File(context.cacheDir, "oversized_upload_test.png")
    tempFile.writeBytes(rawPngBytes)

    val avatarPayload = MediaUtils.readMediaPayload(
      context = context,
      uriString = Uri.fromFile(tempFile).toString(),
      preserveRawBytes = false,
      uploadTarget = MediaUploadTarget.AVATAR
    )
    assertNotNull(avatarPayload)
    assertTrue("Avatar max width must fit within 512px", (avatarPayload!!.width ?: 9999) <= 512)
    assertTrue("Avatar aspect ratio preserved (~2:1)", (avatarPayload.width ?: 0) >= 2 * (avatarPayload.height ?: 1) - 2)
    assertTrue(
      "Compressed payload (${avatarPayload.compressedSizeBytes}B) must be smaller than raw PNG (${avatarPayload.originalSizeBytes}B)",
      avatarPayload.compressedSizeBytes < avatarPayload.originalSizeBytes
    )

    val feedPayload = MediaUtils.readMediaPayload(
      context = context,
      uriString = Uri.fromFile(tempFile).toString(),
      preserveRawBytes = false,
      uploadTarget = MediaUploadTarget.FEED
    )
    assertNotNull(feedPayload)
    assertTrue("Feed max dimension must fit within 1440px", (feedPayload!!.width ?: 9999) <= 1440)

    tempFile.delete()
  }
}

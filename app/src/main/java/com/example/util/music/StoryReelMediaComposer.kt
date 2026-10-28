package com.example.util.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import com.example.data.model.MusicAttachment
import com.example.data.remote.SupabaseNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure, deterministic 16-bit PCM audio mixing and segment trimming primitives used by
 * [StoryReelMediaComposer] for dual-volume mixing (original video audio + licensed music segment).
 */
object PcmAudioMixer {

  fun mixPcm16Samples(
    originalSample: Short,
    musicSample: Short,
    originalVolume: Float,
    musicVolume: Float
  ): Short {
    val origGain = originalVolume.coerceIn(0f, 1f)
    val musicGain = musicVolume.coerceIn(0f, 1f)
    val mixed = (originalSample.toFloat() * origGain + musicSample.toFloat() * musicGain).roundToInt()
    return mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
  }

  /**
   * Mixes an optional [originalPcm] buffer with a trimmed [musicPcm] buffer across [targetSamples].
   * - Preserves original video audio when [originalVolume] > 0f.
   * - Loops [musicPcm] seamlessly if the video is longer than the selected music segment.
   */
  fun mixPcm16Buffers(
    originalPcm: ShortArray?,
    musicPcm: ShortArray,
    originalVolume: Float,
    musicVolume: Float,
    targetSamples: Int = originalPcm?.size?.takeIf { it > 0 } ?: musicPcm.size
  ): ShortArray {
    if (targetSamples <= 0) return ShortArray(0)
    val output = ShortArray(targetSamples)
    val hasMusic = musicPcm.isNotEmpty() && musicVolume > 0.001f
    val hasOriginal = originalPcm != null && originalPcm.isNotEmpty() && originalVolume > 0.001f

    for (i in 0 until targetSamples) {
      val origSample: Short = if (hasOriginal && i < originalPcm!!.size) originalPcm[i] else 0
      val musSample: Short = if (hasMusic) musicPcm[i % musicPcm.size] else 0
      output[i] = mixPcm16Samples(origSample, musSample, originalVolume, musicVolume)
    }
    return output
  }

  /**
   * Slices a 16-bit interleaved PCM array to `[startTimeMs, startTimeMs + clipDurationMs]`.
   */
  fun slicePcmSegment(
    pcm: ShortArray,
    sampleRateHz: Int,
    channelCount: Int,
    startTimeMs: Long,
    clipDurationMs: Long
  ): ShortArray {
    if (pcm.isEmpty() || sampleRateHz <= 0 || channelCount <= 0) return ShortArray(0)
    val samplesPerMs = (sampleRateHz * channelCount) / 1000.0
    val rawStartIndex = ((startTimeMs.coerceAtLeast(0L) * samplesPerMs).toInt() / channelCount) * channelCount
    val startIndex = rawStartIndex.coerceIn(0, pcm.size)
    val requestedSamples = ((clipDurationMs.coerceAtLeast(1_000L) * samplesPerMs).toInt() / channelCount) * channelCount
    val endIndex = (startIndex + requestedSamples).coerceIn(startIndex, pcm.size)
    if (endIndex <= startIndex) return ShortArray(0)
    return pcm.copyOfRange(startIndex, endIndex)
  }

  /**
   * Normalizes decoded PCM samples to stereo 44,100 Hz so original video audio and music track
   * align sample-accurately prior to mixing and AAC encoding.
   */
  fun normalizeToStereo44k1(
    inputPcm: ShortArray,
    sourceSampleRate: Int,
    sourceChannels: Int,
    targetSampleRate: Int = 44_100
  ): ShortArray {
    if (inputPcm.isEmpty() || sourceSampleRate <= 0 || sourceChannels <= 0) return ShortArray(0)
    val sourceFrames = inputPcm.size / sourceChannels
    if (sourceFrames <= 0) return ShortArray(0)

    if (sourceSampleRate == targetSampleRate && sourceChannels == 2) {
      return inputPcm
    }

    val targetFrames = ((sourceFrames.toLong() * targetSampleRate) / sourceSampleRate).toInt().coerceAtLeast(1)
    val out = ShortArray(targetFrames * 2)
    val ratio = sourceFrames.toDouble() / targetFrames.toDouble()

    for (frame in 0 until targetFrames) {
      val srcFrame = (frame * ratio).toInt().coerceIn(0, sourceFrames - 1)
      val baseIdx = srcFrame * sourceChannels
      val left = inputPcm[baseIdx]
      val right = if (sourceChannels >= 2 && baseIdx + 1 < inputPcm.size) inputPcm[baseIdx + 1] else left
      out[frame * 2] = left
      out[frame * 2 + 1] = right
    }
    return out
  }
}

/**
 * Hardware-accelerated Android MediaCodec + MediaMuxer renderer for:
 * 1. Converting Photo Stories + selected licensed music segment into playable 9:16 `.mp4` Story videos.
 * 2. Mixing licensed music segments + original video audio (with independent volume controls) into
 *    Video Stories and Reels during publishing.
 *
 * Temporary music streams are cached strictly in `cacheDir/music_render_tmp` and deleted immediately
 * after muxing — standalone music files are never duplicated into Supabase Storage.
 */
object StoryReelMediaComposer {

  private const val TARGET_SAMPLE_RATE = 44_100
  private const val TARGET_CHANNELS = 2
  private const val AAC_BITRATE = 128_000
  private const val STORY_VIDEO_WIDTH = 720
  private const val STORY_VIDEO_HEIGHT = 1280
  private const val STORY_VIDEO_FPS = 15
  private const val STORY_VIDEO_BITRATE = 2_200_000

  suspend fun renderPhotoStoryWithMusic(
    context: Context,
    photoUriString: String,
    attachment: MusicAttachment
  ): Result<String> = withContext(Dispatchers.IO) {
    val cleanAttachment = attachment.sanitized()
    val streamUrl = cleanAttachment.streamUrl
    if (streamUrl.isNullOrBlank()) {
      return@withContext Result.failure(
        IllegalStateException("Selected music track has no licensed stream URL for rendering.")
      )
    }

    var tempMusicFile: File? = null
    var outputVideoFile: File? = null
    try {
      val sourceBitmap = decodePhotoBitmap(context, photoUriString)
        ?: return@withContext Result.failure(IllegalStateException("Could not decode Story photo for rendering."))

      tempMusicFile = downloadTempMusicStream(context, streamUrl, cleanAttachment.trackId)
      val durationMs = cleanAttachment.clipDurationMs.coerceIn(5_000L, 15_000L)

      val rawMusicPcm = decodeAudioSegmentToStereo44k1(
        context = context,
        sourceUri = Uri.fromFile(tempMusicFile),
        startTimeMs = cleanAttachment.startTimeMs,
        durationMs = durationMs
      )
      if (rawMusicPcm.isEmpty()) {
        sourceBitmap.recycle()
        return@withContext Result.failure(IllegalStateException("Unable to decode licensed music segment."))
      }

      val totalFramesSamples = ((durationMs * TARGET_SAMPLE_RATE) / 1000L).toInt() * TARGET_CHANNELS
      val scaledMusicPcm = PcmAudioMixer.mixPcm16Buffers(
        originalPcm = null,
        musicPcm = rawMusicPcm,
        originalVolume = 0f,
        musicVolume = cleanAttachment.musicVolume,
        targetSamples = totalFramesSamples
      )

      val composedFrame = compose9x16StoryFrame(sourceBitmap)
      sourceBitmap.recycle()

      val outDir = File(context.filesDir, "user_media").apply { mkdirs() }
      outputVideoFile = File(outDir, "story_music_${System.currentTimeMillis()}.mp4")

      encodeStillBitmapWithPcmAudioToMp4(
        frameBitmap = composedFrame,
        stereo44k1Pcm = scaledMusicPcm,
        durationMs = durationMs,
        outputFile = outputVideoFile
      )
      composedFrame.recycle()

      if (!outputVideoFile.exists() || outputVideoFile.length() <= 0L) {
        return@withContext Result.failure(IllegalStateException("Rendered Story video was empty."))
      }
      Result.success(Uri.fromFile(outputVideoFile).toString())
    } catch (e: Exception) {
      outputVideoFile?.takeIf { it.exists() }?.delete()
      Result.failure(e)
    } finally {
      tempMusicFile?.takeIf { it.exists() }?.delete()
    }
  }

  suspend fun mixMusicIntoVideo(
    context: Context,
    sourceVideoUriString: String,
    attachment: MusicAttachment
  ): Result<String> = withContext(Dispatchers.IO) {
    val cleanAttachment = attachment.sanitized()
    val streamUrl = cleanAttachment.streamUrl
    if (streamUrl.isNullOrBlank()) {
      return@withContext Result.failure(
        IllegalStateException("Selected music track has no licensed stream URL for mixing.")
      )
    }

    var tempMusicFile: File? = null
    var outputVideoFile: File? = null
    val videoExtractor = MediaExtractor()
    try {
      val sourceUri = Uri.parse(sourceVideoUriString)
      configureExtractorDataSource(context, videoExtractor, sourceUri)

      var sourceVideoTrackIndex = -1
      var videoFormat: MediaFormat? = null
      for (i in 0 until videoExtractor.trackCount) {
        val format = videoExtractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("video/")) {
          sourceVideoTrackIndex = i
          videoFormat = format
          break
        }
      }
      if (sourceVideoTrackIndex < 0 || videoFormat == null) {
        return@withContext Result.failure(IllegalStateException("No valid video track found in source video."))
      }

      val videoDurationMs = extractDurationMs(context, sourceUri, videoFormat).coerceIn(1_000L, 90_000L)
      val rotationDegrees = extractRotationDegrees(context, sourceUri, videoFormat)

      tempMusicFile = downloadTempMusicStream(context, streamUrl, cleanAttachment.trackId)
      val musicPcm = decodeAudioSegmentToStereo44k1(
        context = context,
        sourceUri = Uri.fromFile(tempMusicFile),
        startTimeMs = cleanAttachment.startTimeMs,
        durationMs = cleanAttachment.clipDurationMs
      )
      if (musicPcm.isEmpty()) {
        return@withContext Result.failure(IllegalStateException("Unable to decode licensed music audio."))
      }

      val originalPcm: ShortArray? = if (cleanAttachment.originalAudioVolume > 0.01f) {
        try {
          decodeAudioSegmentToStereo44k1(
            context = context,
            sourceUri = sourceUri,
            startTimeMs = 0L,
            durationMs = videoDurationMs
          ).takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
          null
        }
      } else {
        null
      }

      val targetTotalSamples = ((videoDurationMs * TARGET_SAMPLE_RATE) / 1000L).toInt() * TARGET_CHANNELS
      val mixedStereoPcm = PcmAudioMixer.mixPcm16Buffers(
        originalPcm = originalPcm,
        musicPcm = musicPcm,
        originalVolume = cleanAttachment.originalAudioVolume,
        musicVolume = cleanAttachment.musicVolume,
        targetSamples = targetTotalSamples
      )

      val outDir = File(context.filesDir, "user_media").apply { mkdirs() }
      outputVideoFile = File(outDir, "vybe_mixed_${System.currentTimeMillis()}.mp4")

      muxCopiedVideoWithEncodedPcmAudio(
        videoExtractor = videoExtractor,
        sourceVideoTrackIndex = sourceVideoTrackIndex,
        sourceVideoFormat = videoFormat,
        rotationDegrees = rotationDegrees,
        stereo44k1Pcm = mixedStereoPcm,
        outputFile = outputVideoFile
      )

      if (!outputVideoFile.exists() || outputVideoFile.length() <= 0L) {
        return@withContext Result.failure(IllegalStateException("Mixed video file could not be written."))
      }
      Result.success(Uri.fromFile(outputVideoFile).toString())
    } catch (e: Exception) {
      outputVideoFile?.takeIf { it.exists() }?.delete()
      Result.failure(e)
    } finally {
      try {
        videoExtractor.release()
      } catch (_: Exception) {}
      tempMusicFile?.takeIf { it.exists() }?.delete()
    }
  }

  private fun downloadTempMusicStream(context: Context, streamUrl: String, trackId: String): File {
    val tmpDir = File(context.cacheDir, "music_render_tmp").apply { mkdirs() }
    val safeId = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
    val tempFile = File(tmpDir, "tmp_stream_${safeId}_${System.currentTimeMillis()}.audio")

    if (streamUrl.startsWith("file://")) {
      val local = File(Uri.parse(streamUrl).path ?: throw IllegalArgumentException("Invalid local stream path"))
      local.copyTo(tempFile, overwrite = true)
      return tempFile
    }

    val req = Request.Builder().url(streamUrl).get().build()
    SupabaseNetworkClient.okHttpClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful || resp.body == null) {
        throw IllegalStateException("Music stream download failed (HTTP ${resp.code}).")
      }
      resp.body!!.byteStream().use { input ->
        tempFile.outputStream().use { out ->
          input.copyTo(out)
        }
      }
    }
    if (!tempFile.exists() || tempFile.length() <= 0L) {
      throw IllegalStateException("Downloaded music stream was empty.")
    }
    return tempFile
  }

  private fun decodePhotoBitmap(context: Context, uriString: String): Bitmap? {
    val uri = Uri.parse(uriString)
    val bytes = when (uri.scheme?.lowercase()) {
      "file" -> File(uri.path ?: return null).takeIf { it.exists() }?.readBytes()
      "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      else -> File(uriString).takeIf { it.exists() }?.readBytes()
    } ?: return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > STORY_VIDEO_WIDTH * 2 ||
      bounds.outHeight / sampleSize > STORY_VIDEO_HEIGHT * 2
    ) {
      sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
  }

  private fun compose9x16StoryFrame(source: Bitmap): Bitmap {
    val frame = Bitmap.createBitmap(STORY_VIDEO_WIDTH, STORY_VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(frame)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f,
        0f,
        0f,
        STORY_VIDEO_HEIGHT.toFloat(),
        intArrayOf(0xFF120C24.toInt(), 0xFF090910.toInt(), 0xFF180B24.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, STORY_VIDEO_WIDTH.toFloat(), STORY_VIDEO_HEIGHT.toFloat(), bgPaint)

    val scale = min(
      STORY_VIDEO_WIDTH.toFloat() / source.width.coerceAtLeast(1).toFloat(),
      STORY_VIDEO_HEIGHT.toFloat() / source.height.coerceAtLeast(1).toFloat()
    )
    val dstW = source.width * scale
    val dstH = source.height * scale
    val left = (STORY_VIDEO_WIDTH - dstW) / 2f
    val top = (STORY_VIDEO_HEIGHT - dstH) / 2f
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, null, RectF(left, top, left + dstW, top + dstH), bitmapPaint)
    return frame
  }

  private fun configureExtractorDataSource(context: Context, extractor: MediaExtractor, uri: Uri) {
    if (uri.scheme == "file" && uri.path != null) {
      extractor.setDataSource(uri.path!!)
    } else if (uri.scheme.isNullOrBlank()) {
      extractor.setDataSource(uri.toString())
    } else {
      extractor.setDataSource(context, uri, null)
    }
  }

  private fun extractDurationMs(context: Context, uri: Uri, videoFormat: MediaFormat): Long {
    if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) {
      val durUs = videoFormat.getLong(MediaFormat.KEY_DURATION)
      if (durUs > 0L) return durUs / 1000L
    }
    val retriever = MediaMetadataRetriever()
    return try {
      if (uri.scheme == "file" && uri.path != null) {
        retriever.setDataSource(uri.path)
      } else {
        retriever.setDataSource(context, uri)
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15_000L
    } catch (_: Exception) {
      15_000L
    } finally {
      try { retriever.release() } catch (_: Exception) {}
    }
  }

  private fun extractRotationDegrees(context: Context, uri: Uri, videoFormat: MediaFormat): Int {
    if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
      return videoFormat.getInteger(MediaFormat.KEY_ROTATION)
    }
    val retriever = MediaMetadataRetriever()
    return try {
      if (uri.scheme == "file" && uri.path != null) {
        retriever.setDataSource(uri.path)
      } else {
        retriever.setDataSource(context, uri)
      }
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
    } catch (_: Exception) {
      0
    } finally {
      try { retriever.release() } catch (_: Exception) {}
    }
  }

  private fun decodeAudioSegmentToStereo44k1(
    context: Context,
    sourceUri: Uri,
    startTimeMs: Long,
    durationMs: Long
  ): ShortArray {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    try {
      configureExtractorDataSource(context, extractor, sourceUri)
      var audioTrackIndex = -1
      var inputFormat: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("audio/")) {
          audioTrackIndex = i
          inputFormat = format
          break
        }
      }
      if (audioTrackIndex < 0 || inputFormat == null) return ShortArray(0)

      extractor.selectTrack(audioTrackIndex)
      val startUs = startTimeMs.coerceAtLeast(0L) * 1000L
      val endUs = startUs + durationMs.coerceAtLeast(1_000L) * 1000L
      if (startUs > 0L) {
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      }

      val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return ShortArray(0)
      var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
        inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      } else TARGET_SAMPLE_RATE
      var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      } else TARGET_CHANNELS

      val codec = MediaCodec.createDecoderByType(mime)
      decoder = codec
      codec.configure(inputFormat, null, null, 0)
      codec.start()

      val pcmByteStream = ByteArrayOutputStream()
      val bufferInfo = MediaCodec.BufferInfo()
      var sawInputEos = false
      var sawOutputEos = false

      while (!sawOutputEos) {
        if (!sawInputEos) {
          val inputIndex = codec.dequeueInputBuffer(10_000L)
          if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex)!!
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            val sampleTimeUs = extractor.sampleTime
            if (sampleSize < 0 || (sampleTimeUs > endUs && sampleTimeUs > 0L)) {
              codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              sawInputEos = true
            } else {
              codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs.coerceAtLeast(0L), 0)
              extractor.advance()
            }
          }
        }

        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
        when {
          outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val outFmt = codec.outputFormat
            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
          }
          outputIndex >= 0 -> {
            val outputBuffer = codec.getOutputBuffer(outputIndex)
            if (outputBuffer != null && bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= startUs) {
              outputBuffer.position(bufferInfo.offset)
              outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
              val chunk = ByteArray(bufferInfo.size)
              outputBuffer.get(chunk)
              pcmByteStream.write(chunk)
            }
            codec.releaseOutputBuffer(outputIndex, false)
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ||
              bufferInfo.presentationTimeUs >= endUs
            ) {
              sawOutputEos = true
            }
          }
        }
      }

      val rawBytes = pcmByteStream.toByteArray()
      if (rawBytes.size < 2) return ShortArray(0)
      val shortCount = rawBytes.size / 2
      val rawShorts = ShortArray(shortCount)
      ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(rawShorts)

      return PcmAudioMixer.normalizeToStereo44k1(
        inputPcm = rawShorts,
        sourceSampleRate = sampleRate,
        sourceChannels = channelCount,
        targetSampleRate = TARGET_SAMPLE_RATE
      )
    } finally {
      try { decoder?.stop() } catch (_: Exception) {}
      try { decoder?.release() } catch (_: Exception) {}
      try { extractor.release() } catch (_: Exception) {}
    }
  }

  private fun encodeStillBitmapWithPcmAudioToMp4(
    frameBitmap: Bitmap,
    stereo44k1Pcm: ShortArray,
    durationMs: Long,
    outputFile: File
  ) {
    var videoEncoder: MediaCodec? = null
    var inputSurface: Surface? = null
    var audioEncoder: MediaCodec? = null
    var muxer: MediaMuxer? = null
    var muxerStarted = false

    try {
      val videoFormat = MediaFormat.createVideoFormat(
        MediaFormat.MIMETYPE_VIDEO_AVC,
        STORY_VIDEO_WIDTH,
        STORY_VIDEO_HEIGHT
      ).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, STORY_VIDEO_BITRATE)
        setInteger(MediaFormat.KEY_FRAME_RATE, STORY_VIDEO_FPS)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      }

      val vCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
      videoEncoder = vCodec
      vCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      val surface = vCodec.createInputSurface()
      inputSurface = surface
      vCodec.start()

      val audioFormat = createTargetAacFormat()
      val aCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
      audioEncoder = aCodec
      aCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      aCodec.start()

      val mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      muxer = mediaMuxer

      var muxerVideoTrack = -1
      var muxerAudioTrack = -1
      val totalVideoFrames = ((durationMs * STORY_VIDEO_FPS) / 1000L).toInt().coerceAtLeast(1)
      var submittedVideoFrames = 0
      var videoSignalEosSent = false
      var videoOutputDone = false

      var pcmReadOffset = 0
      var audioInputEosSent = false
      var audioOutputDone = false

      val vInfo = MediaCodec.BufferInfo()
      val aInfo = MediaCodec.BufferInfo()
      val pendingVideoSamples = mutableListOf<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
      val pendingAudioSamples = mutableListOf<Pair<ByteBuffer, MediaCodec.BufferInfo>>()

      fun startMuxerIfReady() {
        if (!muxerStarted && muxerVideoTrack >= 0 && muxerAudioTrack >= 0) {
          mediaMuxer.start()
          muxerStarted = true
          pendingVideoSamples.forEach { (buf, info) -> mediaMuxer.writeSampleData(muxerVideoTrack, buf, info) }
          pendingVideoSamples.clear()
          pendingAudioSamples.forEach { (buf, info) -> mediaMuxer.writeSampleData(muxerAudioTrack, buf, info) }
          pendingAudioSamples.clear()
        }
      }

      while (!videoOutputDone || !audioOutputDone) {
        if (!videoSignalEosSent) {
          if (submittedVideoFrames < totalVideoFrames) {
            val canvas = surface.lockCanvas(null)
            try {
              canvas.drawBitmap(frameBitmap, 0f, 0f, null)
            } finally {
              surface.unlockCanvasAndPost(canvas)
            }
            submittedVideoFrames++
          } else {
            vCodec.signalEndOfInputStream()
            videoSignalEosSent = true
          }
        }

        if (!audioInputEosSent) {
          val inIdx = aCodec.dequeueInputBuffer(5_000L)
          if (inIdx >= 0) {
            val inBuf = aCodec.getInputBuffer(inIdx)!!
            inBuf.clear()
            if (pcmReadOffset >= stereo44k1Pcm.size) {
              val eosPtsUs = ((pcmReadOffset / TARGET_CHANNELS).toLong() * 1_000_000L) / TARGET_SAMPLE_RATE
              aCodec.queueInputBuffer(inIdx, 0, 0, eosPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              audioInputEosSent = true
            } else {
              val maxShorts = min(inBuf.remaining() / 2, stereo44k1Pcm.size - pcmReadOffset)
              val ptsUs = ((pcmReadOffset / TARGET_CHANNELS).toLong() * 1_000_000L) / TARGET_SAMPLE_RATE
              val tempBytes = ByteBuffer.allocate(maxShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
              tempBytes.asShortBuffer().put(stereo44k1Pcm, pcmReadOffset, maxShorts)
              inBuf.put(tempBytes.array(), 0, maxShorts * 2)
              aCodec.queueInputBuffer(inIdx, 0, maxShorts * 2, ptsUs, 0)
              pcmReadOffset += maxShorts
            }
          }
        }

        if (!videoOutputDone) {
          val outIdx = vCodec.dequeueOutputBuffer(vInfo, 5_000L)
          when {
            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              muxerVideoTrack = mediaMuxer.addTrack(vCodec.outputFormat)
              startMuxerIfReady()
            }
            outIdx >= 0 -> {
              val outBuf = vCodec.getOutputBuffer(outIdx)
              if (outBuf != null && vInfo.size > 0 && (vInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                val framePtsUs = ((submittedVideoFrames.coerceAtLeast(1) - 1).toLong() * 1_000_000L) / STORY_VIDEO_FPS
                val copyInfo = MediaCodec.BufferInfo().apply {
                  set(0, vInfo.size, if (vInfo.presentationTimeUs > 0) vInfo.presentationTimeUs else framePtsUs, vInfo.flags)
                }
                val copyBuf = copyEncodedBuffer(outBuf, vInfo)
                if (muxerStarted) {
                  mediaMuxer.writeSampleData(muxerVideoTrack, copyBuf, copyInfo)
                } else {
                  pendingVideoSamples.add(copyBuf to copyInfo)
                }
              }
              vCodec.releaseOutputBuffer(outIdx, false)
              if ((vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                videoOutputDone = true
              }
            }
          }
        }

        if (!audioOutputDone) {
          val outIdx = aCodec.dequeueOutputBuffer(aInfo, 5_000L)
          when {
            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              muxerAudioTrack = mediaMuxer.addTrack(aCodec.outputFormat)
              startMuxerIfReady()
            }
            outIdx >= 0 -> {
              val outBuf = aCodec.getOutputBuffer(outIdx)
              if (outBuf != null && aInfo.size > 0 && (aInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                val copyInfo = MediaCodec.BufferInfo().apply {
                  set(0, aInfo.size, aInfo.presentationTimeUs, aInfo.flags)
                }
                val copyBuf = copyEncodedBuffer(outBuf, aInfo)
                if (muxerStarted) {
                  mediaMuxer.writeSampleData(muxerAudioTrack, copyBuf, copyInfo)
                } else {
                  pendingAudioSamples.add(copyBuf to copyInfo)
                }
              }
              aCodec.releaseOutputBuffer(outIdx, false)
              if ((aInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                audioOutputDone = true
              }
            }
          }
        }
      }
    } finally {
      try { inputSurface?.release() } catch (_: Exception) {}
      try { videoEncoder?.stop() } catch (_: Exception) {}
      try { videoEncoder?.release() } catch (_: Exception) {}
      try { audioEncoder?.stop() } catch (_: Exception) {}
      try { audioEncoder?.release() } catch (_: Exception) {}
      if (muxerStarted) {
        try { muxer?.stop() } catch (_: Exception) {}
      }
      try { muxer?.release() } catch (_: Exception) {}
    }
  }

  private fun muxCopiedVideoWithEncodedPcmAudio(
    videoExtractor: MediaExtractor,
    sourceVideoTrackIndex: Int,
    sourceVideoFormat: MediaFormat,
    rotationDegrees: Int,
    stereo44k1Pcm: ShortArray,
    outputFile: File
  ) {
    var audioEncoder: MediaCodec? = null
    var muxer: MediaMuxer? = null
    var muxerStarted = false

    try {
      val aCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
      audioEncoder = aCodec
      aCodec.configure(createTargetAacFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      aCodec.start()

      val mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      muxer = mediaMuxer
      if (rotationDegrees == 90 || rotationDegrees == 180 || rotationDegrees == 270) {
        mediaMuxer.setOrientationHint(rotationDegrees)
      }

      val muxerVideoTrack = mediaMuxer.addTrack(sourceVideoFormat)
      var muxerAudioTrack = -1

      val aInfo = MediaCodec.BufferInfo()
      var pcmReadOffset = 0
      var audioInputEosSent = false
      var audioOutputDone = false

      // 1. Wait for AAC encoder format so muxer can start with both tracks registered
      while (muxerAudioTrack < 0 && !audioOutputDone) {
        if (!audioInputEosSent) {
          val inIdx = aCodec.dequeueInputBuffer(10_000L)
          if (inIdx >= 0) {
            val inBuf = aCodec.getInputBuffer(inIdx)!!
            inBuf.clear()
            if (pcmReadOffset >= stereo44k1Pcm.size) {
              aCodec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              audioInputEosSent = true
            } else {
              val maxShorts = min(inBuf.remaining() / 2, stereo44k1Pcm.size - pcmReadOffset)
              val ptsUs = ((pcmReadOffset / TARGET_CHANNELS).toLong() * 1_000_000L) / TARGET_SAMPLE_RATE
              val tempBytes = ByteBuffer.allocate(maxShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
              tempBytes.asShortBuffer().put(stereo44k1Pcm, pcmReadOffset, maxShorts)
              inBuf.put(tempBytes.array(), 0, maxShorts * 2)
              aCodec.queueInputBuffer(inIdx, 0, maxShorts * 2, ptsUs, 0)
              pcmReadOffset += maxShorts
            }
          }
        }
        val outIdx = aCodec.dequeueOutputBuffer(aInfo, 10_000L)
        if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          muxerAudioTrack = mediaMuxer.addTrack(aCodec.outputFormat)
          mediaMuxer.start()
          muxerStarted = true
        } else if (outIdx >= 0) {
          aCodec.releaseOutputBuffer(outIdx, false)
        }
      }

      if (!muxerStarted) {
        throw IllegalStateException("Audio encoder did not emit AAC track format.")
      }

      // 2. Copy compressed video frames losslessly
      videoExtractor.selectTrack(sourceVideoTrackIndex)
      videoExtractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      val maxVideoInputSize = if (sourceVideoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
        sourceVideoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(512 * 1024)
      } else {
        1024 * 1024
      }
      val videoPacketBuffer = ByteBuffer.allocateDirect(maxVideoInputSize)
      val videoBufferInfo = MediaCodec.BufferInfo()

      while (true) {
        val sampleSize = videoExtractor.readSampleData(videoPacketBuffer, 0)
        if (sampleSize < 0) break
        videoBufferInfo.offset = 0
        videoBufferInfo.size = sampleSize
        videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime.coerceAtLeast(0L)
        var muxerFlags = 0
        val sampleFlags = videoExtractor.sampleFlags
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
          muxerFlags = muxerFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
          muxerFlags = muxerFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        videoBufferInfo.flags = muxerFlags
        mediaMuxer.writeSampleData(muxerVideoTrack, videoPacketBuffer, videoBufferInfo)
        videoExtractor.advance()
      }

      // 3. Encode & mux mixed stereo PCM audio stream
      while (!audioOutputDone) {
        if (!audioInputEosSent) {
          val inIdx = aCodec.dequeueInputBuffer(10_000L)
          if (inIdx >= 0) {
            val inBuf = aCodec.getInputBuffer(inIdx)!!
            inBuf.clear()
            if (pcmReadOffset >= stereo44k1Pcm.size) {
              val eosPtsUs = ((pcmReadOffset / TARGET_CHANNELS).toLong() * 1_000_000L) / TARGET_SAMPLE_RATE
              aCodec.queueInputBuffer(inIdx, 0, 0, eosPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              audioInputEosSent = true
            } else {
              val maxShorts = min(inBuf.remaining() / 2, stereo44k1Pcm.size - pcmReadOffset)
              val ptsUs = ((pcmReadOffset / TARGET_CHANNELS).toLong() * 1_000_000L) / TARGET_SAMPLE_RATE
              val tempBytes = ByteBuffer.allocate(maxShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
              tempBytes.asShortBuffer().put(stereo44k1Pcm, pcmReadOffset, maxShorts)
              inBuf.put(tempBytes.array(), 0, maxShorts * 2)
              aCodec.queueInputBuffer(inIdx, 0, maxShorts * 2, ptsUs, 0)
              pcmReadOffset += maxShorts
            }
          }
        }

        val outIdx = aCodec.dequeueOutputBuffer(aInfo, 10_000L)
        if (outIdx >= 0) {
          val outBuf = aCodec.getOutputBuffer(outIdx)
          if (outBuf != null && aInfo.size > 0 && (aInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            outBuf.position(aInfo.offset)
            outBuf.limit(aInfo.offset + aInfo.size)
            mediaMuxer.writeSampleData(muxerAudioTrack, outBuf, aInfo)
          }
          aCodec.releaseOutputBuffer(outIdx, false)
          if ((aInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            audioOutputDone = true
          }
        }
      }
    } finally {
      try { audioEncoder?.stop() } catch (_: Exception) {}
      try { audioEncoder?.release() } catch (_: Exception) {}
      if (muxerStarted) {
        try { muxer?.stop() } catch (_: Exception) {}
      }
      try { muxer?.release() } catch (_: Exception) {}
    }
  }

  private fun createTargetAacFormat(): MediaFormat {
    return MediaFormat.createAudioFormat(
      MediaFormat.MIMETYPE_AUDIO_AAC,
      TARGET_SAMPLE_RATE,
      TARGET_CHANNELS
    ).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
      setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
    }
  }

  private fun copyEncodedBuffer(source: ByteBuffer, info: MediaCodec.BufferInfo): ByteBuffer {
    source.position(info.offset)
    source.limit(info.offset + info.size)
    val copy = ByteBuffer.allocateDirect(info.size)
    copy.put(source)
    copy.flip()
    return copy
  }
}

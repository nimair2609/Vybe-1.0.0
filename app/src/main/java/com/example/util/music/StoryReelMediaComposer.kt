package com.example.util.music

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.model.MusicAttachment
import java.io.File
import java.nio.ByteBuffer

/**
 * Composes Story and Reel media with licensed music track streams when local muxing is supported,
 * and falls back cleanly to synchronized stream-metadata playback when codec/format remuxing is
 * unavailable so Story and Reel uploads never fail or corrupt original media.
 */
object StoryReelMediaComposer {

  fun mixMusicIntoVideo(
    context: Context,
    videoUriString: String,
    attachment: MusicAttachment
  ): Result<String> {
    val streamUrl = attachment.streamUrl?.trim().orEmpty()
    if (streamUrl.isBlank()) {
      return Result.failure(IllegalArgumentException("No audio stream URL configured for track."))
    }

    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    val outputFile = File(context.cacheDir, "vybe_mixed_${System.currentTimeMillis()}.mp4")

    return try {
      val videoUri = Uri.parse(videoUriString)
      if (videoUri.scheme == "content" || videoUri.scheme == "file") {
        videoExtractor.setDataSource(context, videoUri, null)
      } else {
        videoExtractor.setDataSource(videoUriString)
      }

      var videoTrackIndex = -1
      var videoFormat: MediaFormat? = null
      for (i in 0 until videoExtractor.trackCount) {
        val fmt = videoExtractor.getTrackFormat(i)
        val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("video/")) {
          videoTrackIndex = i
          videoFormat = fmt
          break
        }
      }
      if (videoTrackIndex < 0 || videoFormat == null) {
        return Result.failure(IllegalStateException("Source video track not found."))
      }

      audioExtractor.setDataSource(streamUrl)
      var audioTrackIndex = -1
      var audioFormat: MediaFormat? = null
      for (i in 0 until audioExtractor.trackCount) {
        val fmt = audioExtractor.getTrackFormat(i)
        val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("audio/mp4a") || mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
          audioTrackIndex = i
          audioFormat = fmt
          break
        }
      }
      if (audioTrackIndex < 0 || audioFormat == null) {
        // Non-AAC stream (e.g., MP3/OGG preview): preserve original video and play synchronized track metadata
        return Result.failure(UnsupportedOperationException("Audio stream uses synchronized player attachment."))
      }

      val videoDurationUs = if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) {
        videoFormat.getLong(MediaFormat.KEY_DURATION)
      } else {
        30_000_000L
      }

      muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      val outVideoTrack = muxer.addTrack(videoFormat)
      val outAudioTrack = muxer.addTrack(audioFormat)
      muxer.start()

      val buffer = ByteBuffer.allocate(512 * 1024)
      val bufferInfo = MediaCodec.BufferInfo()

      videoExtractor.selectTrack(videoTrackIndex)
      while (true) {
        bufferInfo.offset = 0
        bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
        if (bufferInfo.size < 0) break
        bufferInfo.presentationTimeUs = videoExtractor.sampleTime
        bufferInfo.flags = if ((videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
          MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
          0
        }
        muxer.writeSampleData(outVideoTrack, buffer, bufferInfo)
        videoExtractor.advance()
      }

      val audioStartOffsetUs = attachment.startTimeSeconds.coerceAtLeast(0) * 1_000_000L
      audioExtractor.selectTrack(audioTrackIndex)
      if (audioStartOffsetUs > 0L) {
        audioExtractor.seekTo(audioStartOffsetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
      }
      val baseAudioPts = audioExtractor.sampleTime.coerceAtLeast(0L)
      while (true) {
        bufferInfo.offset = 0
        bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
        if (bufferInfo.size < 0) break
        val normalizedPts = (audioExtractor.sampleTime - baseAudioPts).coerceAtLeast(0L)
        if (normalizedPts > videoDurationUs) break
        bufferInfo.presentationTimeUs = normalizedPts
        bufferInfo.flags = if ((audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
          MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
          0
        }
        muxer.writeSampleData(outAudioTrack, buffer, bufferInfo)
        audioExtractor.advance()
      }

      muxer.stop()
      Result.success(Uri.fromFile(outputFile).toString())
    } catch (e: Throwable) {
      if (outputFile.exists()) outputFile.delete()
      Result.failure(e)
    } finally {
      try { videoExtractor.release() } catch (_: Throwable) {}
      try { audioExtractor.release() } catch (_: Throwable) {}
      try { muxer?.release() } catch (_: Throwable) {}
    }
  }

  fun renderPhotoStoryWithMusic(
    context: Context,
    photoUriString: String,
    attachment: MusicAttachment
  ): Result<String> {
    // Photo stories preserve crisp full-resolution image quality while StoryViewerScreen plays
    // the attached licensed track segment via MusicPreviewController / FeedAudioController.
    if (photoUriString.isBlank() || attachment.trackId.isBlank()) {
      return Result.failure(IllegalArgumentException("Missing photo story or music attachment."))
    }
    return Result.failure(UnsupportedOperationException("Photo story uses lossless image + synchronized music attachment."))
  }
}

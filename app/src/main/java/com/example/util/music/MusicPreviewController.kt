package com.example.util.music

import com.example.data.model.MusicAttachment
import com.example.util.FeedAudioController

object MusicPreviewController {
  @Volatile
  private var currentPlayingTrackId: String? = null

  fun isPlayingTrack(trackId: String): Boolean {
    return currentPlayingTrackId != null && currentPlayingTrackId == trackId
  }

  fun playTrack(attachment: MusicAttachment) {
    currentPlayingTrackId = attachment.trackId
    FeedAudioController.syncPostMusicTrack(
      postId = "preview_${attachment.trackId}",
      audioTrackTitle = attachment.displayLabel,
      shouldPlayAudio = true
    )
  }

  fun playAttachment(attachment: MusicAttachment, muted: Boolean = false) {
    if (muted) {
      stop()
      return
    }
    playTrack(attachment)
  }

  fun stop() {
    currentPlayingTrackId = null
    FeedAudioController.stopAllFeedAudio()
  }
}

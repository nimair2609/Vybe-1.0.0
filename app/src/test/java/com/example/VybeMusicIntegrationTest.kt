package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VybeLocalStore
import com.example.data.model.LicensedMusicTrack
import com.example.data.model.MediaType
import com.example.data.model.MoodType
import com.example.data.model.MusicAttachment
import com.example.data.model.MusicCategory
import com.example.data.model.MusicMetadataCodec
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.remote.SupabaseNetworkClient
import com.example.data.remote.SupabaseStoryDto
import com.example.data.music.MusicCatalogProvider
import com.example.data.music.MusicCatalogRepository
import com.example.data.music.MusicCatalogResult
import com.example.data.music.MusicCatalogSearchQuery
import com.example.data.music.MusicCatalogStatus
import com.example.data.repository.VybeRepository
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.MusicPickerContent
import com.example.ui.theme.VybeTheme
import com.example.util.music.BindMusicPreviewLifecycle
import com.example.util.music.MusicPreviewController
import com.example.util.music.MusicPreviewState
import com.example.util.music.PcmAudioMixer
import com.example.util.music.PreviewAudioEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VybeMusicIntegrationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private class FakePreviewAudioEngine(
    private val simulatedDurationMs: Long = 120_000L,
    private val shouldError: Boolean = false
  ) : PreviewAudioEngine {
    var preparedStreamUrl: String? = null
    var lastSeekMs: Long = -1L
    var currentVolume: Float = 0f
    var startedCount: Int = 0
    var pausedCount: Int = 0
    var releasedCount: Int = 0
    private var playing: Boolean = false
    private var positionMs: Long = 0L

    override fun prepareAsync(
      streamUrl: String,
      initialVolume: Float,
      onPrepared: (durationMs: Long) -> Unit,
      onError: (String) -> Unit,
      onCompletion: () -> Unit
    ) {
      preparedStreamUrl = streamUrl
      currentVolume = initialVolume
      if (shouldError) {
        onError("Simulated stream error")
      } else {
        onPrepared(simulatedDurationMs)
      }
    }

    override fun seekTo(ms: Long) {
      lastSeekMs = ms
      positionMs = ms
    }

    override fun start() {
      startedCount++
      playing = true
    }

    override fun pause() {
      pausedCount++
      playing = false
    }

    override fun setVolume(volume: Float) {
      currentVolume = volume
    }

    override fun currentPositionMs(): Long = positionMs

    override fun isPlaying(): Boolean = playing

    override fun release() {
      releasedCount++
      playing = false
    }
  }

  private class TestLicensedMusicProvider(
    private val status: MusicCatalogStatus = MusicCatalogStatus.OK,
    private val catalog: List<LicensedMusicTrack> = emptyList(),
    private val message: String? = null
  ) : MusicCatalogProvider {
    override val adapterId: String = "test_licensed_provider"
    var lastQuery: MusicCatalogSearchQuery? = null

    override suspend fun searchCatalog(searchQuery: MusicCatalogSearchQuery): MusicCatalogResult {
      lastQuery = searchQuery
      if (status != MusicCatalogStatus.OK) {
        return MusicCatalogResult(
          status = status,
          providerName = adapterId,
          licenseVerified = false,
          availableCategories = MusicCategory.entries,
          tracks = emptyList(),
          diagnosticMessage = message
        )
      }
      val q = searchQuery.query.lowercase()
      val filtered = catalog.filter { t ->
        q.isBlank() ||
          t.title.lowercase().contains(q) ||
          t.artist.lowercase().contains(q) ||
          (t.genre?.lowercase()?.contains(q) == true) ||
          (t.mood?.lowercase()?.contains(q) == true)
      }
      return MusicCatalogResult(
        status = MusicCatalogStatus.OK,
        providerName = adapterId,
        licenseVerified = true,
        availableCategories = MusicCategory.entries,
        tracks = filtered
      )
    }

    override suspend fun getTrackById(trackId: String): LicensedMusicTrack? {
      return catalog.firstOrNull { it.id == trackId }
    }
  }

  @Before
  fun setUp() {
    MusicPreviewController.resetEngineFactory()
    MusicCatalogRepository.resetToDefaultProvider()
  }

  @After
  fun tearDown() {
    MusicPreviewController.resetEngineFactory()
    MusicCatalogRepository.resetToDefaultProvider()
  }

  @Test
  fun `private music provider API keys are never exposed in Android BuildConfig`() {
    val fieldNames = BuildConfig::class.java.declaredFields.map { it.name }.toSet()
    assertFalse("MUSIC_PROVIDER_API_KEY must never exist in client BuildConfig", fieldNames.contains("MUSIC_PROVIDER_API_KEY"))
    assertFalse("JAMENDO_CLIENT_ID must never exist in client BuildConfig", fieldNames.contains("JAMENDO_CLIENT_ID"))
    assertFalse("MUSIC_PROVIDER_BASE_URL must never exist in client BuildConfig", fieldNames.contains("MUSIC_PROVIDER_BASE_URL"))
    assertFalse("MUSIC_LICENSE_APPROVED must never exist in client BuildConfig", fieldNames.contains("MUSIC_LICENSE_APPROVED"))
  }

  @Test
  fun `provider adapter handles unconfigured, license-blocked, network errors, and licensed search cleanly`() = runBlocking {
    // 1. Unconfigured provider returns honest status with zero fake songs
    MusicCatalogRepository.setProviderAdapter(
      TestLicensedMusicProvider(
        status = MusicCatalogStatus.PROVIDER_NOT_CONFIGURED,
        message = "Provider credentials missing on server."
      )
    )
    val unconfigured = MusicCatalogRepository.searchTracks(query = "arijit", category = MusicCategory.HINDI)
    assertEquals(MusicCatalogStatus.PROVIDER_NOT_CONFIGURED, unconfigured.status)
    assertFalse(unconfigured.licenseVerified)
    assertTrue("Unconfigured catalog must never fabricate fake tracks", unconfigured.tracks.isEmpty())

    // 2. License approval missing blocks production tracks
    MusicCatalogRepository.setProviderAdapter(
      TestLicensedMusicProvider(
        status = MusicCatalogStatus.LICENSE_APPROVAL_REQUIRED,
        message = "Commercial sync license verification required."
      )
    )
    val licensePending = MusicCatalogRepository.searchTracks(category = MusicCategory.BOLLYWOOD)
    assertEquals(MusicCatalogStatus.LICENSE_APPROVAL_REQUIRED, licensePending.status)
    assertTrue(licensePending.tracks.isEmpty())

    // 3. Licensed catalog supports search by title, artist, genre, and mood + empty results
    val sampleTracks = listOf(
      LicensedMusicTrack(
        id = "trk_punjabi_01",
        provider = "licensed_partner",
        title = "Dil Da Safar",
        artist = "Karan Veer",
        artworkUrl = "https://cdn.example.com/art1.jpg",
        previewStreamUrl = "https://cdn.example.com/stream1.mp3",
        durationMs = 95_000L,
        genre = "Punjabi Pop",
        mood = "Energetic",
        language = "pa",
        isSyncLicensed = true,
        isAvailable = true,
        licenseSummary = "Commercial Sync Approved"
      ),
      LicensedMusicTrack(
        id = "trk_inst_02",
        provider = "licensed_partner",
        title = "Monsoon Sitar Drift",
        artist = "Aarav Studio",
        previewStreamUrl = "https://cdn.example.com/stream2.mp3",
        durationMs = 62_000L,
        genre = "Instrumental",
        mood = "Chill",
        language = "hi",
        isSyncLicensed = true,
        isAvailable = true
      )
    )
    val licensedProvider = TestLicensedMusicProvider(status = MusicCatalogStatus.OK, catalog = sampleTracks)
    MusicCatalogRepository.setProviderAdapter(licensedProvider)

    val byArtist = MusicCatalogRepository.searchTracks(query = "Karan", category = MusicCategory.PUNJABI)
    assertEquals(1, byArtist.tracks.size)
    assertEquals("trk_punjabi_01", byArtist.tracks.first().id)

    val byMood = MusicCatalogRepository.searchTracks(query = "Chill", category = MusicCategory.CHILL)
    assertEquals(1, byMood.tracks.size)
    assertEquals("trk_inst_02", byMood.tracks.first().id)

    val emptySearch = MusicCatalogRepository.searchTracks(query = "nonexistent_song_xyz")
    assertEquals(MusicCatalogStatus.OK, emptySearch.status)
    assertTrue(emptySearch.tracks.isEmpty())
  }

  @Test
  fun `preview controller enforces sync license check, segment trimming, volume updates, and stops on screen exit`() {
    var createdEngine: FakePreviewAudioEngine? = null
    MusicPreviewController.setEngineFactoryForTesting {
      FakePreviewAudioEngine(simulatedDurationMs = 90_000L).also { createdEngine = it }
    }

    // Unlicensed track MUST be rejected and never start playback
    val unlicensedTrack = LicensedMusicTrack(
      id = "unlicensed_99",
      provider = "unknown",
      title = "Unverified Commercial Song",
      artist = "Famous Artist",
      previewStreamUrl = "https://cdn.example.com/unlicensed.mp3",
      durationMs = 180_000L,
      isSyncLicensed = false,
      isAvailable = true
    )
    MusicPreviewController.playTrackPreview(unlicensedTrack)
    assertTrue(MusicPreviewController.state.value is MusicPreviewState.Error)
    assertNull("Engine must never be instantiated for unlicensed tracks", createdEngine)

    // Licensed track seeks to startTimeMs, plays segment, updates volume, and stops immediately when screen disposes
    val licensedTrack = LicensedMusicTrack(
      id = "licensed_01",
      provider = "licensed_partner",
      title = "Noor Nights",
      artist = "Riya Sen",
      previewStreamUrl = "https://cdn.example.com/noor.mp3",
      durationMs = 90_000L,
      isSyncLicensed = true,
      isAvailable = true
    )

    var showCreationScreen by mutableStateOf(true)
    composeTestRule.setContent {
      VybeTheme {
        if (showCreationScreen) {
          BindMusicPreviewLifecycle(stopOnDispose = true)
        }
      }
    }

    composeTestRule.runOnIdle {
      MusicPreviewController.playTrackPreview(
        track = licensedTrack,
        startTimeMs = 12_000L,
        clipDurationMs = 15_000L,
        volume = 0.8f
      )
    }

    val engine = createdEngine
    assertNotNull(engine)
    assertEquals(12_000L, engine!!.lastSeekMs)
    assertEquals(0.8f, engine.currentVolume, 0.01f)
    assertTrue(engine.isPlaying())
    val playingState = MusicPreviewController.state.value as MusicPreviewState.Playing
    assertEquals(12_000L, playingState.startTimeMs)
    assertEquals(27_000L, playingState.endTimeMs)

    // Adjust trim & music volume live
    composeTestRule.runOnIdle {
      MusicPreviewController.updateSegmentBounds(startTimeMs = 20_000L, clipDurationMs = 30_000L)
      MusicPreviewController.setVolume(0.45f)
    }
    assertEquals(20_000L, engine.lastSeekMs)
    assertEquals(0.45f, engine.currentVolume, 0.01f)

    // Leaving the creation screen MUST stop music immediately and release the engine
    showCreationScreen = false
    composeTestRule.waitForIdle()

    assertEquals(MusicPreviewState.Idle, MusicPreviewController.state.value)
    assertFalse("Audio engine must stop playing when leaving creation screen", engine.isPlaying())
    assertTrue("Audio engine must be released when leaving creation screen", engine.releasedCount >= 1)
  }

  @Test
  fun `PCM audio mixer trims segments accurately and mixes original video audio with music volume`() {
    // 1. Segment slicing at 44.1kHz stereo (2 channels -> 88,200 samples per second)
    val twoSecondsStereo = ShortArray(44_100 * 2 * 2) { idx -> (idx % 1000).toShort() }
    val halfSecondSlice = PcmAudioMixer.slicePcmSegment(
      pcm = twoSecondsStereo,
      sampleRateHz = 44_100,
      channelCount = 2,
      startTimeMs = 500L,
      clipDurationMs = 1_000L // minimum sanitized clip duration is 1000ms -> 88,200 samples
    )
    assertEquals(88_200, halfSecondSlice.size)
    assertEquals(twoSecondsStereo[44_100], halfSecondSlice[0])

    // 2. Dual-volume mixing preserves original audio when desired
    val originalVideoPcm = shortArrayOf(10_000, -10_000, 8_000, -8_000)
    val musicPcm = shortArrayOf(6_000, 6_000)

    val mixedBoth = PcmAudioMixer.mixPcm16Buffers(
      originalPcm = originalVideoPcm,
      musicPcm = musicPcm,
      originalVolume = 0.5f,
      musicVolume = 0.5f
    )
    // Sample 0: 10000*0.5 + 6000*0.5 = 8000; Sample 1: -10000*0.5 + 6000*0.5 = -2000
    assertEquals(8000.toShort(), mixedBoth[0])
    assertEquals((-2000).toShort(), mixedBoth[1])

    // 3. Muting original audio (0f) isolates music cleanly
    val musicOnly = PcmAudioMixer.mixPcm16Buffers(
      originalPcm = originalVideoPcm,
      musicPcm = musicPcm,
      originalVolume = 0f,
      musicVolume = 1f
    )
    assertEquals(6000.toShort(), musicOnly[0])
    assertEquals(6000.toShort(), musicOnly[1])

    // 4. Saturation protection prevents 16-bit integer wraparound clipping
    val loud = PcmAudioMixer.mixPcm16Samples(30_000, 30_000, 1f, 1f)
    assertEquals(Short.MAX_VALUE, loud)
  }

  @Test
  fun `cross-device metadata sync round-trips dedicated columns, fallback envelope, local store, and preserves content without music`() {
    val attachment = MusicAttachment(
      trackId = "trk_sync_777",
      provider = "jamendo_licensed",
      title = "Raat Ki Roshni",
      artist = "Kabir Malik",
      artworkUrl = "https://cdn.example.com/raat.jpg",
      streamUrl = "https://cdn.example.com/raat.mp3",
      trackDurationMs = 120_000L,
      startTimeMs = 15_000L,
      clipDurationMs = 30_000L,
      musicVolume = 0.82f,
      originalAudioVolume = 0.55f,
      genre = "Bollywood Indie",
      mood = "Romantic",
      isRenderedWithMixedAudio = true
    ).sanitized()

    // 1. Dedicated Supabase columns round-trip
    val (labelFromCols, decodedFromCols) = MusicMetadataCodec.resolveFromColumns(
      rawMusicTitle = attachment.displayLabel,
      musicTrackId = attachment.trackId,
      musicProvider = attachment.provider,
      musicArtist = attachment.artist,
      musicArtworkUrl = attachment.artworkUrl,
      musicStreamUrl = attachment.streamUrl,
      musicStartMs = attachment.startTimeMs.toInt(),
      musicDurationMs = attachment.clipDurationMs.toInt(),
      musicVolume = attachment.musicVolume.toDouble(),
      originalAudioVolume = attachment.originalAudioVolume.toDouble(),
      musicMetadataJson = MusicMetadataCodec.toJson(attachment)
    )
    assertEquals("Raat Ki Roshni • Kabir Malik", labelFromCols)
    assertNotNull(decodedFromCols)
    assertEquals("trk_sync_777", decodedFromCols!!.trackId)
    assertEquals(15_000L, decodedFromCols.startTimeMs)
    assertEquals(30_000L, decodedFromCols.clipDurationMs)
    assertEquals(0.82f, decodedFromCols.musicVolume, 0.01f)
    assertEquals(0.55f, decodedFromCols.originalAudioVolume, 0.01f)

    // 2. Legacy `music_title` envelope fallback round-trip (when remote migration hasn't run yet)
    val encodedLegacy = MusicMetadataCodec.encodeLegacyMusicTitleColumn(attachment.displayLabel, attachment)
    val (cleanLegacyLabel, decodedFromEnvelope) = MusicMetadataCodec.resolveFromColumns(rawMusicTitle = encodedLegacy)
    assertEquals("Raat Ki Roshni • Kabir Malik", cleanLegacyLabel)
    assertNotNull(decodedFromEnvelope)
    assertEquals("trk_sync_777", decodedFromEnvelope!!.trackId)
    assertEquals(15_000L, decodedFromEnvelope.startTimeMs)

    // 3. Existing Stories and Reels WITHOUT music remain completely unaffected
    val (noMusicLabel, noMusicAttachment) = MusicMetadataCodec.resolveFromColumns(rawMusicTitle = null)
    assertNull(noMusicLabel)
    assertNull(noMusicAttachment)

    val creator = User(id = "u_creator_1", username = "creator_one", displayName = "Creator One")
    val plainReel = Post(
      id = "reel_no_music_1",
      author = creator,
      caption = "Original street skate session",
      mediaType = MediaType.REEL,
      mediaUrls = listOf("https://cdn.example.com/skate.mp4"),
      timestamp = "Just now",
      isVybesReel = true,
      musicAttachment = null
    )
    val musicReel = Post(
      id = "reel_with_music_1",
      author = creator,
      caption = "Monsoon drive reel",
      mediaType = MediaType.REEL,
      mediaUrls = listOf("https://cdn.example.com/drive.mp4"),
      timestamp = "Just now",
      isVybesReel = true,
      audioTrackTitle = attachment.displayLabel,
      musicAttachment = attachment
    )
    val context = ApplicationProvider.getApplicationContext<Context>()
    val localStore = VybeLocalStore.getInstance(context)
    localStore.savePosts(listOf(plainReel, musicReel))

    val loadedPosts = localStore.loadPosts()
    val loadedPlainReel = loadedPosts.first { it.id == "reel_no_music_1" }
    val loadedMusicReel = loadedPosts.first { it.id == "reel_with_music_1" }
    assertNull("Reel without music must keep null musicAttachment", loadedPlainReel.musicAttachment)
    assertEquals("trk_sync_777", loadedMusicReel.musicAttachment?.trackId)
    assertEquals(0.55f, loadedMusicReel.musicAttachment?.originalAudioVolume ?: 0f, 0.01f)

    // Verify SupabaseStoryDto wire round-trip across devices for Stories with and without music
    val storyDtoAdapter = SupabaseNetworkClient.moshi.adapter(SupabaseStoryDto::class.java)
    val plainStoryJson = storyDtoAdapter.toJson(
      SupabaseStoryDto(
        id = "story_no_music_1",
        authorId = creator.id,
        mediaUrl = "https://cdn.example.com/photo.jpg",
        mediaType = "PHOTO",
        caption = "Morning coffee",
        musicTitle = null,
        musicTrackId = null
      )
    )
    val parsedPlainStory = storyDtoAdapter.fromJson(plainStoryJson)!!
    val (_, resolvedPlainStoryMusic) = MusicMetadataCodec.resolveFromColumns(
      rawMusicTitle = parsedPlainStory.musicTitle,
      musicTrackId = parsedPlainStory.musicTrackId
    )
    assertNull("Story without music must keep null musicAttachment", resolvedPlainStoryMusic)

    val musicStoryJson = storyDtoAdapter.toJson(
      SupabaseStoryDto(
        id = "story_with_music_1",
        authorId = creator.id,
        mediaUrl = "https://cdn.example.com/story_rendered.mp4",
        mediaType = "VIDEO",
        caption = "Evening vibes",
        musicTitle = MusicMetadataCodec.encodeLegacyMusicTitleColumn(attachment.displayLabel, attachment),
        musicTrackId = attachment.trackId,
        musicProvider = attachment.provider,
        musicArtist = attachment.artist,
        musicArtworkUrl = attachment.artworkUrl,
        musicStreamUrl = attachment.streamUrl,
        musicStartMs = attachment.startTimeMs.toInt(),
        musicDurationMs = attachment.clipDurationMs.toInt(),
        musicVolume = attachment.musicVolume.toDouble(),
        originalAudioVolume = attachment.originalAudioVolume.toDouble()
      )
    )
    val parsedMusicStory = storyDtoAdapter.fromJson(musicStoryJson)!!
    val (storyMusicLabel, resolvedStoryMusic) = MusicMetadataCodec.resolveFromColumns(
      rawMusicTitle = parsedMusicStory.musicTitle,
      musicTrackId = parsedMusicStory.musicTrackId,
      musicProvider = parsedMusicStory.musicProvider,
      musicArtist = parsedMusicStory.musicArtist,
      musicArtworkUrl = parsedMusicStory.musicArtworkUrl,
      musicStreamUrl = parsedMusicStory.musicStreamUrl,
      musicStartMs = parsedMusicStory.musicStartMs,
      musicDurationMs = parsedMusicStory.musicDurationMs,
      musicVolume = parsedMusicStory.musicVolume,
      originalAudioVolume = parsedMusicStory.originalAudioVolume
    )
    assertEquals("Raat Ki Roshni • Kabir Malik", storyMusicLabel)
    assertEquals("trk_sync_777", resolvedStoryMusic?.trackId)
    assertEquals(15_000L, resolvedStoryMusic?.startTimeMs)
  }

  @Test
  fun `music picker UI selects, previews, trims licensed track and track page discovers matching reels`() {
    var fakeEngine: FakePreviewAudioEngine? = null
    MusicPreviewController.setEngineFactoryForTesting {
      FakePreviewAudioEngine(simulatedDurationMs = 75_000L).also { fakeEngine = it }
    }

    val trackA = LicensedMusicTrack(
      id = "trk_bollywood_88",
      provider = "licensed_partner",
      title = "Sufi Skyline",
      artist = "Zoya Khan",
      artworkUrl = "https://cdn.example.com/sufi.jpg",
      previewStreamUrl = "https://cdn.example.com/sufi.mp3",
      durationMs = 75_000L,
      genre = "Bollywood",
      mood = "Romantic",
      isSyncLicensed = true,
      isAvailable = true
    )
    MusicCatalogRepository.setProviderAdapter(
      TestLicensedMusicProvider(status = MusicCatalogStatus.OK, catalog = listOf(trackA))
    )

    var confirmedAttachment: MusicAttachment? = null
    composeTestRule.setContent {
      VybeTheme {
        MusicPickerContent(
          initialAttachment = null,
          showOriginalAudioControl = true,
          defaultClipDurationMs = 15_000L,
          onCancel = {},
          onConfirm = { picked ->
            MusicPreviewController.stop()
            confirmedAttachment = picked
          }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("music_picker_search_input").performTextInput("Zoya")
    // Advance past 260ms search debounce
    composeTestRule.mainClock.advanceTimeBy(350L)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Sufi Skyline").assertIsDisplayed()
    composeTestRule.onNodeWithTag("music_track_item_trk_bollywood_88").performClick()
    composeTestRule.waitForIdle()

    // Segment mixer + dual volume controls appear inside scrollable list while Attach Music button stays pinned
    composeTestRule.onNodeWithTag("music_segment_mixer_panel").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("picker_music_volume_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("picker_original_volume_slider").performScrollTo().assertIsDisplayed()

    // Start preview & switch clip duration to 30s
    composeTestRule.onNodeWithTag("music_trim_preview_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertNotNull(fakeEngine)
    assertTrue(fakeEngine!!.isPlaying())

    composeTestRule.onNodeWithTag("music_clip_duration_30s").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Pinned confirm button is directly visible and clickable without scrolling
    composeTestRule.onNodeWithTag("music_picker_confirm_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertNotNull(confirmedAttachment)
    assertEquals("trk_bollywood_88", confirmedAttachment!!.trackId)
    assertEquals(30_000L, confirmedAttachment!!.clipDurationMs)
    assertEquals(MusicPreviewState.Idle, MusicPreviewController.state.value)

    // Verify Track Page navigation & Reel discovery by shared trackId
    val repo = VybeRepository.getInstance()
    repo.loginUser("creator@vybe.app", "Password123!")
    val me = repo.currentUser.value!!

    val matchingReel1 = Post(
      id = "reel_sufi_1",
      author = me,
      caption = "Golden hour terrace",
      mediaType = MediaType.REEL,
      mediaUrls = listOf("https://cdn.example.com/r1.mp4"),
      timestamp = "Just now",
      moodZone = MoodType.CHILL,
      isVybesReel = true,
      audioTrackTitle = confirmedAttachment!!.displayLabel,
      musicAttachment = confirmedAttachment,
      clientUploadId = "upload_reel_sufi_1"
    )
    val matchingReel2 = Post(
      id = "reel_sufi_2",
      author = me,
      caption = "City lights drive",
      mediaType = MediaType.REEL,
      mediaUrls = listOf("https://cdn.example.com/r2.mp4"),
      timestamp = "Just now",
      isVybesReel = true,
      audioTrackTitle = confirmedAttachment!!.displayLabel,
      musicAttachment = confirmedAttachment,
      clientUploadId = "upload_reel_sufi_2"
    )
    val otherTrackReel = Post(
      id = "reel_other_3",
      author = me,
      caption = "Different soundtrack",
      mediaType = MediaType.REEL,
      mediaUrls = listOf("https://cdn.example.com/r3.mp4"),
      timestamp = "Just now",
      isVybesReel = true,
      musicAttachment = confirmedAttachment!!.copy(trackId = "trk_different_999"),
      clientUploadId = "upload_reel_other_3"
    )

    repo.recordVerifiedPostForState(matchingReel1)
    repo.recordVerifiedPostForState(matchingReel2)
    repo.recordVerifiedPostForState(otherTrackReel)

    val viewModel = VybeViewModel()
    viewModel.openMusicTrackPage(confirmedAttachment!!)

    assertEquals(Screen.MUSIC_TRACK_DETAIL, viewModel.currentScreen.value)
    assertEquals("trk_bollywood_88", viewModel.selectedMusicTrackAttachment.value?.trackId)
    val discoveredIds = viewModel.trackPageReels.value.map { it.id }.toSet()
    assertTrue(discoveredIds.contains("reel_sufi_1"))
    assertTrue(discoveredIds.contains("reel_sufi_2"))
    assertFalse("Unrelated track reels must not appear on track page", discoveredIds.contains("reel_other_3"))

    // Verify "Use Audio in Reel" routes to CREATE_POST and preloads the track once
    viewModel.useTrackInReel(confirmedAttachment!!.toLicensedTrack())
    assertEquals(Screen.CREATE_POST, viewModel.currentScreen.value)
    val consumed = viewModel.consumePendingComposerMusicTrack()
    assertEquals("trk_bollywood_88", consumed?.id)
    assertNull("Pending composer track must be consumed once", viewModel.consumePendingComposerMusicTrack())
  }
}

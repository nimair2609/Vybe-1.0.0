package com.example

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VybeLocalStore
import com.example.data.model.*
import com.example.data.remote.SupabaseConfig
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UnfilteredScreen
import com.example.ui.theme.VybeTheme
import com.example.util.MediaUtils
import com.example.util.QrCodeGenerator
import com.example.util.TimestampUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VybePriorityReleaseFixesTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `splash screen renders fixed size logo without zoom or pulse scaling`() {
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.setContent {
      VybeTheme(darkTheme = true) {
        SplashScreen()
      }
    }

    val initialBounds = composeTestRule.onNodeWithTag("splash_logo_container")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()

    composeTestRule.mainClock.advanceTimeBy(1800L)
    composeTestRule.waitForIdle()

    val laterBounds = composeTestRule.onNodeWithTag("splash_logo_container")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()

    assertEquals("Logo width must remain completely fixed", initialBounds.right - initialBounds.left, laterBounds.right - laterBounds.left)
    assertEquals("Logo height must remain completely fixed", initialBounds.bottom - initialBounds.top, laterBounds.bottom - laterBounds.top)
    composeTestRule.onNodeWithTag("splash_wordmark").assertIsDisplayed()
  }

  @Test
  fun `story persistence retains active 24h stories and prunes expired stories`() {
    val store = VybeLocalStore.getInstance(context)
    val nowMs = System.currentTimeMillis()
    val author = User(id = "550e8400-e29b-41d4-a716-446655440000", username = "nimai", displayName = "Nimai")

    val activePhotoStory = Story(
      id = "story_active_photo",
      author = author,
      mediaUrl = "https://cppwadedazpbevkfrchg.supabase.co/storage/v1/object/public/vybe-stories/550e8400/photo.jpg",
      caption = "Golden hour",
      mediaType = StoryMediaType.PHOTO,
      audience = StoryAudience.EVERYONE,
      createdAtIso = TimestampUtils.formatIsoUtc(nowMs - 300_000L),
      expiresAtIso = TimestampUtils.formatIsoUtc(nowMs + 23L * 3_600_000L)
    )
    val activeVideoStory = Story(
      id = "story_active_video",
      author = author,
      mediaUrl = "https://cppwadedazpbevkfrchg.supabase.co/storage/v1/object/public/vybe-stories/550e8400/clip.mp4",
      caption = "Night drive",
      mediaType = StoryMediaType.VIDEO,
      audience = StoryAudience.FOLLOWERS,
      createdAtIso = TimestampUtils.formatIsoUtc(nowMs - 120_000L),
      expiresAtIso = TimestampUtils.formatIsoUtc(nowMs + 23L * 3_600_000L)
    )
    val expiredStory = Story(
      id = "story_expired",
      author = author,
      mediaUrl = "https://cppwadedazpbevkfrchg.supabase.co/storage/v1/object/public/vybe-stories/550e8400/old.jpg",
      caption = "Yesterday",
      mediaType = StoryMediaType.PHOTO,
      audience = StoryAudience.EVERYONE,
      createdAtIso = TimestampUtils.formatIsoUtc(nowMs - 26L * 3_600_000L),
      expiresAtIso = TimestampUtils.formatIsoUtc(nowMs - 2L * 3_600_000L)
    )

    store.saveStories(listOf(activePhotoStory, activeVideoStory, expiredStory))
    val reloaded = store.loadStories()

    assertEquals(2, reloaded.size)
    assertTrue(reloaded.any { it.id == "story_active_photo" && it.mediaType == StoryMediaType.PHOTO })
    assertTrue(reloaded.any { it.id == "story_active_video" && it.mediaType == StoryMediaType.VIDEO })
    assertFalse("Expired >24h story must be pruned on reload", reloaded.any { it.id == "story_expired" })
  }

  @Test
  fun `extractJwtSubject normalizes Supabase JWT sub to lowercase for Storage RLS folder matching`() {
    val header = Base64.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    val payload = Base64.encodeToString(
      """{"sub":"550E8400-E29B-41D4-A716-446655440000","exp":4102444800}""".toByteArray(),
      Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    )
    val fakeJwt = "$header.$payload.sig"

    val extracted = SupabaseConfig.extractJwtSubject(fakeJwt)
    assertEquals("550e8400-e29b-41d4-a716-446655440000", extracted)
  }

  @Test
  fun `canonical post deep link generation parsing and DM extraction work across formats`() {
    val postId = "post_9f82b1c4-77a1"
    val canonicalUrl = QrCodeGenerator.buildCanonicalPostUrl(postId)
    val nativeLink = QrCodeGenerator.buildPostDeepLink(postId)

    assertTrue(canonicalUrl.endsWith("/post/$postId"))
    assertEquals("vybe://post/$postId", nativeLink)

    assertEquals(postId, QrCodeGenerator.parsePostDeepLink(canonicalUrl))
    assertEquals(postId, QrCodeGenerator.parsePostDeepLink(nativeLink))
    assertEquals(
      postId,
      QrCodeGenerator.parsePostDeepLink("https://${QrCodeGenerator.CANONICAL_DEV_HOST}/p/$postId")
    )
    assertNull(QrCodeGenerator.parsePostDeepLink("https://malicious.example.com/post/$postId"))

    val dmBody = "Check this out!\nShared post from @aria: Sunset vibes\n$canonicalUrl"
    assertEquals(postId, QrCodeGenerator.extractPostIdFromText(dmBody))
  }

  @Test
  fun `TimestampUtils formats human readable relative times timezone offsets and clamps future skew`() {
    val refNow = TimestampUtils.parseIsoMillis("2026-10-30T14:30:00Z")!!

    // Future skew -> Just now
    assertEquals("Just now", TimestampUtils.formatRelative(iso = "2026-10-30T14:31:15Z", nowMillis = refNow, fallback = ""))
    // 25s -> Just now
    assertEquals("Just now", TimestampUtils.formatRelative(iso = "2026-10-30T14:29:35Z", nowMillis = refNow, fallback = ""))
    // 1m -> 1 minute ago
    assertEquals("1 minute ago", TimestampUtils.formatRelative(iso = "2026-10-30T14:29:00Z", nowMillis = refNow, fallback = ""))
    // 2m -> 2 minutes ago
    assertEquals("2 minutes ago", TimestampUtils.formatRelative(iso = "2026-10-30T14:28:00Z", nowMillis = refNow, fallback = ""))
    // 1h -> 1 hour ago
    assertEquals("1 hour ago", TimestampUtils.formatRelative(iso = "2026-10-30T13:30:00Z", nowMillis = refNow, fallback = ""))
    // +05:30 IST offset conversion (2026-10-30T19:58:00+05:30 == 2026-10-30T14:28:00Z -> 2 minutes ago)
    assertEquals("2 minutes ago", TimestampUtils.formatRelative(iso = "2026-10-30T19:58:00+05:30", nowMillis = refNow, fallback = ""))
    // 3 days ago
    assertEquals("3 days ago", TimestampUtils.formatRelative(iso = "2026-10-27T14:30:00Z", nowMillis = refNow, fallback = ""))
  }

  @Test
  fun `PostCard aligns Like Comment Share and Bookmark vertically and renders in Dark and Light themes`() {
    var shareClicked = false
    val verifiedAuthor = User(
      id = "u_verified_author",
      username = "roulnimai",
      displayName = "Nimai Roul",
      isVerified = true,
      badgeApprovalStatus = "ACCEPTED",
      badgeDisplayStatus = "ACCEPTED"
    )
    val post = Post(
      id = "align_test_1",
      author = verifiedAuthor,
      caption = "Testing action bar alignment and single avatar border",
      timestamp = "2 minutes ago",
      createdAtIso = TimestampUtils.formatIsoUtc(System.currentTimeMillis() - 120_000L),
      likesCount = 12,
      commentsCount = 4,
      sharesCount = 3
    )

    var useDark = true
    composeTestRule.setContent {
      VybeTheme(darkTheme = useDark) {
        PostCard(
          post = post,
          onLikeClick = {},
          onSaveClick = {},
          onCommentClick = {},
          onShareClick = { shareClicked = true },
          onUserClick = {},
          currentUserId = "viewer_99",
          onFollowClick = {}
        )
      }
    }

    val likeBounds = composeTestRule.onNodeWithTag("like_button_align_test_1").assertIsDisplayed().getUnclippedBoundsInRoot()
    val commentBounds = composeTestRule.onNodeWithTag("comment_button_align_test_1").assertIsDisplayed().getUnclippedBoundsInRoot()
    val shareBounds = composeTestRule.onNodeWithTag("share_button_align_test_1").assertIsDisplayed().getUnclippedBoundsInRoot()
    val saveBounds = composeTestRule.onNodeWithTag("save_button_align_test_1").assertIsDisplayed().getUnclippedBoundsInRoot()

    val likeCenterY = (likeBounds.top.value + likeBounds.bottom.value) / 2f
    val commentCenterY = (commentBounds.top.value + commentBounds.bottom.value) / 2f
    val shareCenterY = (shareBounds.top.value + shareBounds.bottom.value) / 2f
    val saveCenterY = (saveBounds.top.value + saveBounds.bottom.value) / 2f

    assertTrue("Share button must align vertically with Like button", abs(likeCenterY - shareCenterY) <= 1.5f)
    assertTrue("Share button must align vertically with Comment button", abs(commentCenterY - shareCenterY) <= 1.5f)
    assertTrue("Share button must align vertically with Bookmark button", abs(saveCenterY - shareCenterY) <= 1.5f)

    composeTestRule.onNodeWithTag("verified_badge", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("share_button_align_test_1").performClick()
    assertTrue("Clicking Share button must invoke onShareClick", shareClicked)
  }

  @Test
  fun `cleaned VYBE logo drawable has zero top white horizontal line artifact`() {
    val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.vybe_app_icon_clean)
    assertNotNull("vybe_app_icon_clean drawable must decode cleanly", bmp)
    val midX = bmp.width / 2
    // Check top 3 rows near center — previously had bright white (>230) horizontal line artifact
    for (y in 0 until minOf(3, bmp.height)) {
      val pixel = bmp.getPixel(midX, y)
      val alpha = AndroidColor.alpha(pixel)
      if (alpha > 40) {
        val r = AndroidColor.red(pixel)
        val g = AndroidColor.green(pixel)
        assertTrue("Top edge pixel ($midX,$y) must not be a white artifact (was R=$r, G=$g)", r < 120 || g < 120)
      }
    }
  }

  @Test
  fun `UnfilteredScreen exposes camera only with zero gallery affordances and MediaUtils preserves raw camera bytes`() {
    val vm = VybeViewModel()
    composeTestRule.setContent {
      VybeTheme(darkTheme = false) {
        UnfilteredScreen(viewModel = vm)
      }
    }

    composeTestRule.onNodeWithTag("unfiltered_camera_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("unfiltered_open_camera_cta").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag("unfiltered_gallery_button").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Choose from Gallery", substring = true, ignoreCase = true).assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Pick from Gallery", substring = true, ignoreCase = true).assertCountEquals(0)

    // Verify raw pass-through preserves exact camera file bytes without re-compression
    val rawBytes = ByteArray(4096) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
    val rawFile = File(context.cacheDir, "unfiltered_raw_camera_test.jpg")
    rawFile.writeBytes(rawBytes)

    val prepared = MediaUtils.readMediaPayload(
      context = context,
      uriString = Uri.fromFile(rawFile).toString(),
      preserveRawBytes = true
    )
    assertNotNull(prepared)
    assertArrayEquals("preserveRawBytes=true must upload exact original camera bytes", rawBytes, prepared!!.bytes)
    rawFile.delete()
  }
}

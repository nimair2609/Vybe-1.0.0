package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VybeLocalStore
import com.example.data.model.AppThemeMode
import com.example.data.model.User
import com.example.data.repository.VybeRepository
import com.example.data.session.SessionManager
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.GrowCircleWorkspace
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.VybeExperienceMode
import com.example.ui.theme.VybeTheme
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h915dp")
class Vybe2PolishAndSeparationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var repository: VybeRepository
  private lateinit var localStore: VybeLocalStore
  private lateinit var sessionManager: SessionManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    localStore = VybeLocalStore.getInstance(context)
    sessionManager = SessionManager.getInstance(context)
    sessionManager.saveExperienceMode("SOCIAL")
    repository = VybeRepository.getInstance()
  }

  private fun seedUser(): User {
    val me = User(
      id = "u_vybe2_test_1",
      username = "vybe_architect",
      displayName = "Vybe Architect",
      bio = "Building clean social and growth spaces."
    )
    sessionManager.saveSession(user = me, accessToken = "", refreshToken = "")
    repository.initSession(context)
    return me
  }

  @Test
  fun `ViewModel mode persistence rapid toggle stability and Circle routing into Grow`() {
    seedUser()
    val vm = VybeViewModel()
    assertEquals(VybeExperienceMode.SOCIAL, vm.experienceMode.value)

    // 20 rapid switches between Social and Grow must remain deterministic
    repeat(20) { i ->
      val target = if (i % 2 == 0) VybeExperienceMode.GROW else VybeExperienceMode.SOCIAL
      vm.setExperienceMode(target)
      assertEquals(target, vm.experienceMode.value)
      assertEquals(target.name, sessionManager.getExperienceMode())
    }

    // Redirect legacy Screen.VYBE_CIRCLE into single Grow Circle workspace
    vm.navigateTo(Screen.VYBE_CIRCLE)
    assertEquals(Screen.MAIN, vm.currentScreen.value)
    assertEquals(VybeExperienceMode.GROW, vm.experienceMode.value)
    assertEquals("CIRCLE", vm.growTab.value)

    // Switching back to Social resets to Feed
    vm.setExperienceMode(VybeExperienceMode.SOCIAL)
    assertEquals(MainTab.FEED, vm.selectedTab.value)

    // Restoring a fresh ViewModel reads persisted GROW mode from SessionManager
    vm.setExperienceMode(VybeExperienceMode.GROW)
    val restoredVm = VybeViewModel()
    assertEquals(VybeExperienceMode.GROW, restoredVm.experienceMode.value)
  }

  @Test
  fun `VybeModeSwitch maintains invariant position size and equal segment width across all Social and Grow tabs`() {
    seedUser()
    val vm = VybeViewModel()

    composeTestRule.setContent {
      val mode by vm.experienceMode.collectAsState()
      VybeTheme(themeMode = AppThemeMode.DARK, experienceMode = mode) {
        MainScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    val baselineSwitch = composeTestRule.onNodeWithTag("vybe_mode_switch")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()
    val socialSeg = composeTestRule.onNodeWithTag("mode_switch_social").getUnclippedBoundsInRoot()
    val growSeg = composeTestRule.onNodeWithTag("mode_switch_grow").getUnclippedBoundsInRoot()

    val socialWidth = (socialSeg.right - socialSeg.left).value
    val growWidth = (growSeg.right - growSeg.left).value
    assertTrue(
      "Social and Grow segments must have equal widths (was $socialWidth vs $growWidth)",
      abs(socialWidth - growWidth) <= 1f
    )

    fun assertSwitchInvariant(contextLabel: String) {
      composeTestRule.waitForIdle()
      val currentBounds = composeTestRule.onNodeWithTag("vybe_mode_switch")
        .assertIsDisplayed()
        .getUnclippedBoundsInRoot()
      assertTrue(
        "Mode switch left shifted on $contextLabel (${baselineSwitch.left} -> ${currentBounds.left})",
        abs((baselineSwitch.left - currentBounds.left).value) <= 1f
      )
      assertTrue(
        "Mode switch top shifted on $contextLabel (${baselineSwitch.top} -> ${currentBounds.top})",
        abs((baselineSwitch.top - currentBounds.top).value) <= 1f
      )
      assertTrue(
        "Mode switch width changed on $contextLabel",
        abs(((baselineSwitch.right - baselineSwitch.left) - (currentBounds.right - currentBounds.left)).value) <= 1f
      )
      assertTrue(
        "Mode switch height changed on $contextLabel",
        abs(((baselineSwitch.bottom - baselineSwitch.top) - (currentBounds.bottom - currentBounds.top)).value) <= 1f
      )
    }

    // Navigate through Social tabs
    composeTestRule.onNodeWithTag("nav_tab_explore").performClick()
    assertSwitchInvariant("Social Explore")

    composeTestRule.onNodeWithTag("nav_tab_inbox").performClick()
    assertSwitchInvariant("Social Inbox")

    composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
    assertSwitchInvariant("Social Profile")

    composeTestRule.onNodeWithTag("nav_tab_feed").performClick()
    assertSwitchInvariant("Social Feed")

    // Switch to Grow mode and verify invariant bounds across all 5 Grow tabs
    composeTestRule.onNodeWithTag("mode_switch_grow").performClick()
    assertSwitchInvariant("Grow Home")
    composeTestRule.onNodeWithTag("grow_home_workspace").assertIsDisplayed()

    composeTestRule.onNodeWithTag("grow_nav_circle").performClick()
    assertSwitchInvariant("Grow Circle")
    composeTestRule.onNodeWithTag("grow_circle_workspace").assertIsDisplayed()

    composeTestRule.onNodeWithTag("grow_nav_projects").performClick()
    assertSwitchInvariant("Grow Projects")
    composeTestRule.onNodeWithTag("grow_projects_workspace").assertIsDisplayed()

    composeTestRule.onNodeWithTag("grow_nav_opportunities").performClick()
    assertSwitchInvariant("Grow Opportunities")
    composeTestRule.onNodeWithTag("grow_opportunities_workspace").assertIsDisplayed()

    composeTestRule.onNodeWithTag("grow_nav_profile").performClick()
    assertSwitchInvariant("Grow Profile")
    composeTestRule.onNodeWithTag("grow_profile_workspace").assertIsDisplayed()

    // Switch back to Social
    composeTestRule.onNodeWithTag("mode_switch_social").performClick()
    assertSwitchInvariant("Back to Social Feed")
    assertEquals(VybeExperienceMode.SOCIAL, vm.experienceMode.value)
  }

  @Test
  fun `VYBE header logo keeps identical position dimensions and aspect ratio across Social Grow 20 switches and Light Dark themes`() {
    seedUser()
    val vm = VybeViewModel()
    var themeMode by mutableStateOf(AppThemeMode.DARK)

    composeTestRule.setContent {
      val mode by vm.experienceMode.collectAsState()
      VybeTheme(themeMode = themeMode, experienceMode = mode) {
        MainScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    val socialLogoBounds = composeTestRule.onNodeWithTag("vybe_header_brand_logo", useUnmergedTree = true)
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()
    val socialIconBounds = composeTestRule.onNodeWithTag("vybe_header_logo_icon", useUnmergedTree = true)
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()

    val iconWidth = (socialIconBounds.right - socialIconBounds.left).value
    val iconHeight = (socialIconBounds.bottom - socialIconBounds.top).value
    assertTrue("VYBE logo icon must maintain 1:1 aspect ratio ($iconWidth x $iconHeight)", abs(iconWidth - iconHeight) <= 0.5f)

    fun assertLogoIdentical(stepLabel: String) {
      composeTestRule.waitForIdle()
      val logoBounds = composeTestRule.onNodeWithTag("vybe_header_brand_logo", useUnmergedTree = true)
        .assertIsDisplayed()
        .getUnclippedBoundsInRoot()
      val iconBounds = composeTestRule.onNodeWithTag("vybe_header_logo_icon", useUnmergedTree = true)
        .assertIsDisplayed()
        .getUnclippedBoundsInRoot()

      assertTrue("$stepLabel: logo left shifted", abs((socialLogoBounds.left - logoBounds.left).value) <= 1f)
      assertTrue("$stepLabel: logo top shifted", abs((socialLogoBounds.top - logoBounds.top).value) <= 1f)
      assertTrue("$stepLabel: icon width changed", abs(((socialIconBounds.right - socialIconBounds.left) - (iconBounds.right - iconBounds.left)).value) <= 0.5f)
      assertTrue("$stepLabel: icon height changed", abs(((socialIconBounds.bottom - socialIconBounds.top) - (iconBounds.bottom - iconBounds.top)).value) <= 0.5f)
      assertTrue("$stepLabel: icon left shifted", abs((socialIconBounds.left - iconBounds.left).value) <= 1f)
      assertTrue("$stepLabel: icon top shifted", abs((socialIconBounds.top - iconBounds.top).value) <= 1f)
    }

    // 20 consecutive Social/Grow UI switches + theme toggle
    repeat(20) { i ->
      if (i % 2 == 0) {
        composeTestRule.onNodeWithTag("mode_switch_grow").performClick()
        assertLogoIdentical("Switch #$i Grow")
      } else {
        composeTestRule.onNodeWithTag("mode_switch_social").performClick()
        assertLogoIdentical("Switch #$i Social")
      }
    }

    // Light mode parity across Social and Grow
    themeMode = AppThemeMode.LIGHT
    composeTestRule.waitForIdle()
    assertLogoIdentical("Light Social")
    composeTestRule.onNodeWithTag("mode_switch_grow").performClick()
    assertLogoIdentical("Light Grow")
  }

  @Test
  fun `Social Home and Explore exclude Grow and Circle gateways while preserving Social features`() {
    seedUser()
    val vm = VybeViewModel()

    composeTestRule.setContent {
      VybeTheme(themeMode = AppThemeMode.DARK, experienceMode = VybeExperienceMode.SOCIAL) {
        HomeScreen(viewModel = vm, showTopHeader = false)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("gateway_vybe_circle").assertDoesNotExist()
    composeTestRule.onNodeWithTag("gateway_vybe_grow").assertDoesNotExist()
    composeTestRule.onNodeWithText("VYBE Circle").assertDoesNotExist()
    composeTestRule.onNodeWithText("VYBE Grow").assertDoesNotExist()
  }

  @Test
  fun `ExploreScreen excludes Circles filter and tab while search filters and 6 signature features work`() {
    seedUser()
    val vm = VybeViewModel()

    composeTestRule.setContent {
      VybeTheme(themeMode = AppThemeMode.LIGHT, experienceMode = VybeExperienceMode.SOCIAL) {
        ExploreScreen(viewModel = vm, showTopBrandRow = false)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Circles").assertDoesNotExist()
    composeTestRule.onNodeWithTag("explore_six_features_header_grid").assertIsDisplayed()
    composeTestRule.onNodeWithTag("explore_pill_Trending").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("explore_smart_search_input").performTextInput("vybe")
    composeTestRule.waitForIdle()
    assertEquals("vybe", vm.searchQuery.value)
  }

  @Test
  fun `Unified GrowCircleWorkspace renders clean two-tab switcher categories and create dialog without Communities leak`() {
    seedUser()
    val vm = VybeViewModel()

    composeTestRule.setContent {
      VybeTheme(themeMode = AppThemeMode.LIGHT, experienceMode = VybeExperienceMode.GROW) {
        GrowCircleWorkspace(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("grow_circle_discover_tab").assertIsDisplayed()
    composeTestRule.onNodeWithTag("grow_circle_my_tab").assertIsDisplayed()
    composeTestRule.onNodeWithText("Communities").assertDoesNotExist()
    composeTestRule.onNodeWithTag("grow_circle_category_all").assertIsDisplayed()
    composeTestRule.onNodeWithTag("grow_circle_category_tech_&_founders").assertIsDisplayed()

    composeTestRule.onNodeWithTag("grow_circle_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_circle_name_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_circle_submit_button").assertIsDisplayed()
  }

  @Test
  fun `Minimal SettingsScreen preserves Account Center Privacy Cache Manager and clean About label`() {
    seedUser()
    val vm = VybeViewModel()

    composeTestRule.setContent {
      VybeTheme(themeMode = AppThemeMode.DARK) {
        SettingsScreen(viewModel = vm)
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_account_center_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("private_account_switch").assertIsDisplayed()
    composeTestRule.onNodeWithText("Who can see your content").assertIsDisplayed()
    composeTestRule.onNodeWithText("How you use VYBE").assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_storage_cache_item").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("clear_image_cache_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("clear_video_cache_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("clear_all_cache_button").performScrollTo().assertIsDisplayed()

    // Navigate back to Settings root then open About
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_about_vybe_item").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("PROUDLY MADE IN INDIA").assertIsDisplayed()
  }

  @Test
  fun `SplashScreen displays clean India label without flag emoji`() {
    composeTestRule.setContent {
      VybeTheme(themeMode = AppThemeMode.DARK) {
        SplashScreen()
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("PROUDLY MADE IN INDIA").assertIsDisplayed()
  }
}

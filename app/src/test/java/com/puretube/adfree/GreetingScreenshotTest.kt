package com.puretube.adfree

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.puretube.adfree.ui.components.PureTopBar
import com.puretube.adfree.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun topBar_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        PureTopBar(
          currentTitle = "PureTube",
          currentUrl = "https://m.youtube.com",
          isLoading = false,
          progress = 100,
          totalAdsBlocked = 12,
          isAdBlockEnabled = true,
          onSearchSubmit = {},
          onOpenStats = {},
          onOpenSpeed = {},
          onOpenSettings = {},
          onEnterPiP = {},
          onRefresh = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

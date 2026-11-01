package com.puretube.adfree

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.puretube.adfree.data.AdBlocker
import com.puretube.adfree.util.DefaultAppHelper
import com.puretube.adfree.viewmodel.PureTubeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PureTube", appName)
  }

  @Test
  fun `test ad blocker url detection`() {
    assertTrue(AdBlocker.isAdUrl("https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub"))
    assertTrue(AdBlocker.isAdUrl("https://www.youtube.com/api/stats/ads?v=123"))
    assertTrue(AdBlocker.isAdUrl("https://static.doubleclick.net/instream/ad_status.js"))
    assertFalse(AdBlocker.isAdUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertFalse(AdBlocker.isAdUrl("https://www.youtube.com/feed/trending"))
  }

  @Test
  fun `test default link handler state in viewmodel`() {
    val viewModel = PureTubeViewModel()
    assertFalse(viewModel.uiState.value.isDefaultLinkHandler)
    viewModel.setDefaultLinkHandlerState(true)
    assertTrue(viewModel.uiState.value.isDefaultLinkHandler)
  }
}

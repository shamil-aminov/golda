package sh.aminov.golda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.Tags
import java.util.TimeZone

/** The tabs swipe sideways and so does a finger on the bars: on the bars, the bars win. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h700dp-xxhdpi")
class InsightsPagerTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var pager: PagerState

    @Before
    fun open() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
        val data = ScreenshotTest().demo()
        compose.setContent {
            pager = rememberPagerState(initialPage = 1) { 3 }
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    if (page == 1) AnalyticsScreen(data, PaddingValues()) {} else Box(Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun week() {
        compose.onNodeWithContentDescription("Период").performClick()
        compose.onNodeWithText("Неделя").performClick()
    }

    @Test
    fun aFingerAcrossTheBarsStaysOnTheTab() {
        week()
        compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.1f) }
        compose.waitForIdle()
        assertEquals(1, pager.currentPage)
    }

    @Test
    fun aFingerUnderTheBarsStaysOnTheTabToo() {
        week()
        // On the axis, a little under the bars.
        compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput {
            down(Offset(width * 0.9f, height + 30f))
            repeat(12) { moveBy(Offset(-width / 14f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(1, pager.currentPage)
    }

    @Test
    fun aFingerOverTheBarsStaysOnTheTabToo() {
        week()
        // In the pill's row, over the bars.
        compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput {
            down(Offset(width * 0.9f, -30f))
            repeat(12) { moveBy(Offset(-width / 14f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(1, pager.currentPage)
    }

    @Test
    fun aFingerUpTheBarsScrollsTheScreen() {
        week()
        val before = compose.onNodeWithTag(Tags.INSIGHTS_BARS).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput {
            down(Offset(width / 2f, height / 2f))
            repeat(10) { moveBy(Offset(0f, -60f)) }
            up()
        }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag(Tags.INSIGHTS_BARS).fetchSemanticsNode().boundsInRoot.top < before)
    }

    @Test
    fun theTabsStillSwipeEverywhereElse() {
        compose.onRoot().performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.1f) }
        compose.waitForIdle()
        assertEquals(2, pager.currentPage)
    }
}

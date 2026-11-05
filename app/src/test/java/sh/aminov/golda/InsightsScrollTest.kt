package sh.aminov.golda

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

/** Scrolled to the end, a pick shortens the list: the chart under the finger must not move for it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1300dp-xxhdpi")
class InsightsScrollTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun open() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
        val data = ScreenshotTest().demo()
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { AnalyticsScreen(data, PaddingValues()) {} } }
    }

    private fun toTheEnd() {
        repeat(4) {
            compose.onRoot().performTouchInput {
                down(Offset(width / 2f, height * 0.9f))
                repeat(20) { moveBy(Offset(0f, -height * 0.04f)) }
                advanceEventTime(1_000)
                up()
            }
            compose.waitForIdle()
        }
    }

    private fun barsTop() = compose.onNodeWithTag(Tags.INSIGHTS_BARS).fetchSemanticsNode().positionInRoot.y
    private fun tap(x: Float) = compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput { click(Offset(width * x, height / 2f)) }

    @Test
    fun aBarPickedAtTheEndLeavesTheChartWhereItIs() {
        val start = barsTop()
        toTheEnd()
        val top = barsTop()
        assertTrue("it scrolled: $start to $top", top < start)
        tap(0.9f)
        compose.waitForIdle()
        assertEquals(top, barsTop(), 1f)
        tap(0.7f)
        compose.waitForIdle()
        assertEquals(top, barsTop(), 1f)
        compose.onNodeWithContentDescription("Снять выбор дня").performClick()
        compose.waitForIdle()
        assertEquals(top, barsTop(), 1f)
    }

    @Test
    fun aKindPickedAtTheEndLeavesTheChartWhereItIs() {
        toTheEnd()
        val top = barsTop()
        compose.onAllNodesWithText("Разовые")[0].performClick()
        compose.waitForIdle()
        assertEquals(top, barsTop(), 1f)
        compose.onNodeWithContentDescription("Снять выбор вида").performClick()
        compose.waitForIdle()
        assertEquals(top, barsTop(), 1f)
    }
}

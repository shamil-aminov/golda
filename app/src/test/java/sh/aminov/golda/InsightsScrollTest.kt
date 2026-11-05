package sh.aminov.golda

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
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

/** The card keeps its height whatever is picked: nothing under it moves, and nothing over it. */
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

    private fun barsTop() = compose.onNodeWithTag(Tags.INSIGHTS_BARS).fetchSemanticsNode().positionInRoot.y
    private fun incomeTop() = compose.onNodeWithText("Доходы и обмен").fetchSemanticsNode().positionInRoot.y
    private fun tap(x: Float) = compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput { click(Offset(width * x, height / 2f)) }

    private fun stays(step: () -> Unit) {
        val bars = barsTop()
        val income = incomeTop()
        step()
        compose.waitForIdle()
        assertEquals(bars, barsTop(), 1f)
        assertEquals(income, incomeTop(), 1f)
    }

    @Test
    fun aBarPickedChangedAndLetGoMovesNothing() {
        stays { tap(0.9f) }
        stays { tap(0.7f) }
        stays { compose.onNodeWithContentDescription("Снять выбор дня").performClick() }
    }

    @Test
    fun aKindPickedAndLetGoKeepsTheCardsHeight() {
        // The header's figure grows when it has fewer digits, so only the card itself is held to it.
        val height = incomeTop() - barsTop()
        compose.onNodeWithText("Разовые").performClick()
        compose.waitForIdle()
        assertEquals(height, incomeTop() - barsTop(), 1f)
        compose.onNodeWithContentDescription("Снять выбор вида").performClick()
        compose.waitForIdle()
        assertEquals(height, incomeTop() - barsTop(), 1f)
    }
}

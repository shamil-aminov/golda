package sh.aminov.golda

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.PeriodKind
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.InsightsDetails
import sh.aminov.golda.ui.InsightsPick
import sh.aminov.golda.ui.Tags
import java.time.LocalDate
import java.util.TimeZone

/** The card's second level: everything in full, and one pick shared with the first screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class InsightsDetailsTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fix() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    /** The card and its page by turns, as the app has them. */
    private fun open(pick: InsightsPick = InsightsPick(), page: Boolean = true) {
        val data = ScreenshotTest().demo()
        compose.setContent {
            var details by remember { mutableStateOf(page) }
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                if (details) InsightsDetails(data, pick, { details = false }, {})
                else AnalyticsScreen(data, PaddingValues(), pick, { details = true }) {}
            }
        }
    }

    private fun shown(text: String, times: Int = 1) = compose.onAllNodesWithText(text).assertCountEquals(times)
    private fun line(words: String) = compose.onAllNodesWithText(words, substring = true).assertCountEquals(1)
    private fun back() = compose.onNodeWithContentDescription("Назад").performClick()
    private fun week() {
        compose.onNodeWithContentDescription("Период").performClick()
        compose.onNodeWithText("Неделя").performClick()
    }

    @Test
    fun everyCategoryWithNoPick() {
        open()
        shown("Все траты")
        shown("Жильё")
        shown("Транспорт")
        line("За 30 дней ·")
    }

    private fun kind(from: String, to: String) {
        compose.onNodeWithText(from).performClick()
        compose.onAllNodesWithText(to).onLast().performClick()
    }

    @Test
    fun aKindNarrowsItAndNamesIt() {
        open(InsightsPick(filter = Bucket.DAILY))
        shown("Ежедневные")
        shown("Жильё", 0)
    }

    @Test
    fun aDaysPurchases() {
        open(InsightsPick(PeriodKind.WEEK, bar = LocalDate.of(2026, 10, 24)))
        shown("Шаурма")
        line("За субботу, 24 октября ·")
    }

    @Test
    fun theCrossStandsInTheCalendarsPlaceOnThePageToo() {
        open(InsightsPick(PeriodKind.WEEK, bar = LocalDate.of(2026, 10, 24)))
        compose.onAllNodesWithContentDescription("Период").assertCountEquals(0)
        compose.onNodeWithContentDescription("Снять выбор дня").performClick()
        compose.onNodeWithContentDescription("Период").assertExists()
        shown("Шаурма", 0)
    }

    @Test
    fun aWeeksCategories() {
        open(InsightsPick(bar = LocalDate.of(2026, 10, 19)))
        shown("Жильё", 0)
        shown("Еда")
        shown("Шаурма", 0)
    }

    @Test
    fun aCategoryOpensItsPurchases() {
        open()
        compose.onNodeWithText("Еда").performClick()
        compose.waitForIdle()
        shown("Шаурма")
    }

    @Test
    fun thePeriodChosenOnThePageIsTheFirstScreens() {
        open()
        week()
        line("За 7 дней ·")
        back()
        shown("За 7 дней")
    }

    @Test
    fun theKindChosenOnThePageIsTheFirstScreens() {
        open()
        kind("Все траты", "Разовые")
        shown("Разовые")
        back()
        shown("Разовые")
    }

    @Test
    fun whatTheFirstScreenPickedIsOnThePage() {
        open(page = false)
        kind("Все траты", "Разовые")
        compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput { click(Offset(width - 2f, height / 2f)) }
        compose.onAllNodesWithText("Покупки", useUnmergedTree = true)[0].performClick()
        back()
        compose.onAllNodesWithContentDescription("Снять выбор дня").assertCountEquals(1)
    }

    @Test
    fun anotherKindOrPeriodLetsTheBarGo() {
        val pick = InsightsPick(PeriodKind.WEEK, bar = LocalDate.of(2026, 10, 24))
        open(pick)
        kind("Все траты", "Ежедневные")
        assertNull(pick.bar)
        val again = InsightsPick(bar = LocalDate.of(2026, 10, 19))
        again.choosePeriod(PeriodKind.WEEK)
        assertNull(again.bar)
    }

    @Test
    fun thePickKeepsTheBarWhenNothingChanges() {
        val day = LocalDate.of(2026, 10, 19)
        val pick = InsightsPick(bar = day, filter = Bucket.BIG)
        pick.choosePeriod(PeriodKind.MONTH)
        pick.chooseKind(Bucket.BIG)
        assertEquals(day, pick.bar)
        pick.chooseRange(day, day)
        assertNull(pick.bar)
        assertEquals(Period(day, day), pick.period(day))
    }

    @Test
    fun theCrossLetsTheBarGoAndKeepsTheKind() {
        val pick = InsightsPick(filter = Bucket.BIG, bar = LocalDate.of(2026, 10, 19))
        pick.letBarGo()
        assertNull(pick.bar)
        assertEquals(Bucket.BIG, pick.filter)
    }
}

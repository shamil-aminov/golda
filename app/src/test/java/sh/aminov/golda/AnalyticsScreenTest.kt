package sh.aminov.golda

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.Review
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Period
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.InsightsPick
import sh.aminov.golda.ui.Tags
import java.time.LocalDate
import java.util.TimeZone

/** Insights on the demo data, by hand: the title's menu narrows the one card, a bar its figure and legend, the rest of the card opens the details. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class AnalyticsScreenTest {
    @get:Rule val compose = createComposeRule()

    private var reviewed: Period? = null
    private var opens = 0
    private var opened: Long? = null
    private var reviews by mutableStateOf(emptyList<Review>())
    private val pick = InsightsPick()

    @Before
    fun open() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
        val data = ScreenshotTest().demo()
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { AnalyticsScreen(data, PaddingValues(), pick, { opens++ }, reviews, { id, _ -> opened = id }) { reviewed = it } } }
    }

    private fun shown(text: String, times: Int = 1) = compose.onAllNodesWithText(text).assertCountEquals(times)
    private fun line(words: String) = compose.onAllNodesWithText(words, substring = true).assertCountEquals(1)
    private fun week() {
        compose.onNodeWithContentDescription("Период").performClick()
        compose.onNodeWithText("Неделя").performClick()
    }
    private fun kind(from: String, to: String) {
        compose.onNodeWithText(from).performClick()
        compose.onAllNodesWithText(to).onLast().performClick()
    }
    private fun figure(rub: String) = compose.onNodeWithTag(Tags.INSIGHTS_TOTAL, useUnmergedTree = true).assert(hasAnyDescendant(hasText(rub, substring = true)))
    private fun review(id: Long, fromDay: LocalDate, toDay: LocalDate, written: LocalDate) =
        Review(id, fromDay.toEpochDay(), toDay.toEpochDay(), written.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), "")
    private fun tapLastBar() = compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput { click(Offset(width - 2f, height / 2f)) }

    @Test
    fun theTitlesMenuNarrowsTheCardAndAllSpendingLetsGo() {
        shown("Все траты")
        shown("Жильё")
        kind("Все траты", "Ежедневные")
        shown("Ежедневные")
        shown("Все траты", 0)
        shown("Жильё", 0)
        shown("Еда")
        kind("Ежедневные", "Все траты")
        shown("Ежедневные", 0)
        shown("Жильё")
    }

    @Test
    fun theMenuNamesAllTheKinds() {
        compose.onNodeWithText("Все траты").performClick()
        shown("Все траты", 2)
        shown("Ежедневные")
        shown("Регулярные")
        shown("Разовые")
    }

    @Test
    fun anotherKindTakesOver() {
        kind("Все траты", "Ежедневные")
        kind("Ежедневные", "Разовые")
        shown("Ежедневные", 0)
        shown("Разовые")
        shown("Еда", 0)
    }

    @Test
    fun theKindsMenuDoesNotOpenTheDetails() {
        compose.onNodeWithText("Все траты").performClick()
        assertEquals(0, opens)
    }

    @Test
    fun thePlanOnlyForTheDailyByDaysAndNoAverage() {
        kind("Все траты", "Ежедневные")
        // A month is in weeks: a plan for a day means nothing against them.
        compose.onAllNodesWithText("План", substring = true).assertCountEquals(0)
        week()
        line(" в день")
        compose.onAllNodesWithText("План", substring = true).assertCountEquals(1)
        kind("Ежедневные", "Разовые")
        compose.onAllNodesWithText("План", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("В среднем", substring = true).assertCountEquals(0)
    }

    @Test
    fun aWeeksBarNarrowsTheFigureAndTheCategoriesAndTheCrossBringsThemBack() {
        shown("Все траты")
        shown("За 30 дней")
        compose.onAllNodesWithContentDescription("Снять выбор дня").assertCountEquals(0)
        tapLastBar()
        shown("За 19–24 октября")
        shown("За 30 дней", 0)
        // The rent is in an earlier week; the legend is this bar's, the title as it was.
        shown("Жильё", 0)
        shown("Еда")
        shown("Все траты")
        compose.onNodeWithContentDescription("Снять выбор дня").performClick()
        shown("Жильё")
        shown("За 30 дней")
    }

    @Test
    fun aDaysBarGivesItsSumAndASecondTapBringsTheWholeBack() {
        week()
        kind("Все траты", "Ежедневные")
        figure("355")
        tapLastBar()
        shown("Еда")
        shown("Покупки", 0)
        shown("За субботу, 24 октября")
        figure("793")
        tapLastBar()
        shown("За 7 дней")
        figure("355")
    }

    @Test
    fun theCrossLetsTheDayGoAndTheKindStays() {
        week()
        kind("Все траты", "Ежедневные")
        tapLastBar()
        compose.onNodeWithContentDescription("Снять выбор дня").performClick()
        compose.onAllNodesWithText("За субботу", substring = true).assertCountEquals(0)
        shown("Ежедневные")
        shown("Еда")
        compose.onAllNodesWithContentDescription("Снять выбор дня").assertCountEquals(0)
    }

    @Test
    fun aKindWithNothingSaysSoAndTheMenuLetsItGo() {
        week()
        kind("Все траты", "Регулярные")
        shown("Регулярные")
        shown("За эти дни трат нет")
        compose.onAllNodesWithContentDescription("Подробнее").assertCountEquals(0)
        kind("Регулярные", "Все траты")
        shown("За эти дни трат нет", 0)
    }

    @Test
    fun anotherKindLetsTheDayGo() {
        week()
        tapLastBar()
        kind("Все траты", "Разовые")
        compose.onAllNodesWithText("За субботу", substring = true).assertCountEquals(0)
    }

    @Test
    fun theLegendRepeatsTheBarsLayers() {
        shown("Жильё")
        shown("Еда")
        shown("Остальное")
        shown("Транспорт", 0)
    }

    @Test
    fun theFigureAndTheLegendOpenTheDetailsWithWhatIsPicked() {
        compose.onNodeWithTag(Tags.INSIGHTS_TOTAL, useUnmergedTree = true).performClick()
        assertEquals(1, opens)
        kind("Все траты", "Ежедневные")
        tapLastBar()
        compose.onNodeWithText("Еда", useUnmergedTree = true).performClick()
        assertEquals(2, opens)
        assertEquals(Bucket.DAILY, pick.filter)
        assertEquals(LocalDate.of(2026, 10, 19), pick.bar)
    }

    @Test
    fun theBarsAndTheCrossDoNotOpenTheDetails() {
        tapLastBar()
        compose.onNodeWithContentDescription("Снять выбор дня").performClick()
        assertEquals(0, opens)
    }

    @Test
    fun theCardSaysDetailsAloud() {
        compose.onNodeWithContentDescription("Подробнее").assertExists()
    }

    @Test
    fun withoutReviewsThereIsTheTitleAndThePlusAlone() {
        shown("Разборы")
        shown("Сделать разбор", 0)
        compose.onNodeWithContentDescription("Сделать разбор").performClick()
        assertNotNull(reviewed)
        assertEquals(29L, java.time.temporal.ChronoUnit.DAYS.between(reviewed!!.from, reviewed!!.to))
    }

    @Test
    fun theLastThreeReviewsByDateOpenAndAllAreOnTheirPage() {
        val october = LocalDate.of(2026, 10, 1)
        val week = review(4, LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 24))
        val month = review(3, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), october)
        val old = review(2, LocalDate.of(2025, 9, 2), LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 1))
        reviews = listOf(week, month, old)
        shown("24 октября")
        shown("за 7 дней")
        shown("1 октября")
        shown("за сентябрь")
        shown("1 октября 2025")
        shown("за 30 дней")
        shown("Все разборы", 0)
        compose.onNodeWithText("24 октября", useUnmergedTree = true).performClick()
        assertEquals(4L, opened)
        reviews = listOf(week, month, old, review(1, october, october, LocalDate.of(2025, 9, 1)))
        compose.onAllNodesWithText("1 сентября 2025").assertCountEquals(0)
        compose.onNodeWithText("Все разборы").performClick()
        assertNotNull(reviewed)
    }

    @Test
    fun theCalendarChangesThePeriod() {
        shown("За 30 дней")
        compose.onNodeWithContentDescription("Период").performClick()
        compose.onNodeWithText("Неделя").performClick()
        shown("За 7 дней")
    }
}

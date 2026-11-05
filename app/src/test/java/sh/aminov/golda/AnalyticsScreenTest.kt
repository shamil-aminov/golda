package sh.aminov.golda

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Period
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.Tags
import java.util.TimeZone

/** Insights on the demo data, by hand: the kind's tile narrows the one card, a bar swaps its rows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class AnalyticsScreenTest {
    @get:Rule val compose = createComposeRule()

    private var reviewed: Period? = null

    @Before
    fun open() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
        val data = ScreenshotTest().demo()
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { AnalyticsScreen(data, PaddingValues()) { reviewed = it } } }
    }

    private fun shown(text: String, times: Int = 1) = compose.onAllNodesWithText(text).assertCountEquals(times)
    private fun line(words: String) = compose.onAllNodesWithText(words, substring = true).assertCountEquals(1)
    private fun week() {
        compose.onNodeWithContentDescription("Период").performClick()
        compose.onNodeWithText("Неделя").performClick()
    }
    private fun tapLastBar() = compose.onNodeWithTag(Tags.INSIGHTS_BARS).performTouchInput { click(Offset(width - 2f, height / 2f)) }

    @Test
    fun aTileNarrowsTheCardAndASecondTapLetsGo() {
        shown("Все траты")
        shown("Жильё")
        compose.onNodeWithText("Ежедневные").performClick()
        // The tile and the card's title.
        shown("Ежедневные", 2)
        shown("Все траты", 0)
        shown("Жильё", 0)
        shown("Еда")
        compose.onAllNodesWithText("Ежедневные")[0].performClick()
        shown("Ежедневные")
        shown("Все траты")
        shown("Жильё")
    }

    @Test
    fun anotherTileTakesOver() {
        compose.onNodeWithText("Ежедневные").performClick()
        compose.onNodeWithText("Разовые").performClick()
        shown("Ежедневные")
        shown("Разовые", 2)
        shown("Еда", 0)
    }

    @Test
    fun theDayPlanShowsOnlyForTheDailyByDays() {
        compose.onNodeWithText("Ежедневные").performClick()
        // A month is in weeks: a plan for a day means nothing against them.
        compose.onAllNodesWithText("План", substring = true).assertCountEquals(0)
        week()
        compose.onAllNodesWithText("План", substring = true).assertCountEquals(1)
        compose.onNodeWithText("Разовые").performClick()
        compose.onAllNodesWithText("План", substring = true).assertCountEquals(0)
    }

    @Test
    fun aWeeksBarNarrowsTheCategoriesAndTheCrossBringsThemBack() {
        shown("Все траты")
        line("За 30 дней ·")
        compose.onAllNodesWithContentDescription("Снять выбор дня").assertCountEquals(0)
        tapLastBar()
        line("За 19–24 октября ·")
        // The rent is in an earlier week; no single purchases, the same rows, the card's title as it was.
        shown("Жильё", 0)
        shown("Еда")
        shown("Шаурма", 0)
        shown("Все траты")
        compose.onNodeWithContentDescription("Снять выбор дня").performClick()
        shown("Жильё")
        line("За 30 дней ·")
    }

    @Test
    fun aDaysBarSwapsTheCategoriesForItsPurchasesAndASecondTapBringsThemBack() {
        week()
        tapLastBar()
        shown("Шаурма")
        shown("Покупки", 0)
        line("За субботу, 24 октября ·")
        tapLastBar()
        shown("Шаурма", 0)
        shown("Покупки")
    }

    @Test
    fun theCrossLetsTheDayGoThenTheKind() {
        week()
        compose.onNodeWithText("Ежедневные").performClick()
        tapLastBar()
        shown("Шаурма")
        shown("Еда", 2)
        compose.onNodeWithContentDescription("Снять выбор дня").performClick()
        shown("Шаурма", 0)
        // The kind stays: its tile, the title, its category.
        shown("Ежедневные", 2)
        shown("Еда")
        compose.onAllNodesWithContentDescription("Снять выбор дня").assertCountEquals(0)
        compose.onNodeWithContentDescription("Снять выбор вида").performClick()
        shown("Ежедневные")
        shown("Все траты")
        compose.onAllNodesWithContentDescription("Снять выбор вида").assertCountEquals(0)
    }

    @Test
    fun aKindWithNothingStaysATileToPickAndLetGo() {
        week()
        compose.onNodeWithText("Регулярные").performClick()
        shown("Регулярные", 2)
        shown("За эти дни трат нет")
        // Still picked, and the cross is there to let it go.
        compose.onNodeWithContentDescription("Снять выбор вида").assertExists()
        compose.onAllNodesWithText("Регулярные")[0].performClick()
        shown("Регулярные")
        shown("Все траты")
    }

    @Test
    fun anotherKindLetsTheDayGo() {
        week()
        tapLastBar()
        compose.onNodeWithText("Разовые").performClick()
        shown("Шаурма", 0)
        shown("Покупки")
    }

    @Test
    fun everyCategoryIsOnTheCard() {
        shown("Жильё")
        shown("Транспорт")
    }

    @Test
    fun theReviewMarkOpensTheReviewsForThePeriod() {
        compose.onNodeWithContentDescription("Разбор").performClick()
        assertNotNull(reviewed)
        assertEquals(29L, java.time.temporal.ChronoUnit.DAYS.between(reviewed!!.from, reviewed!!.to))
    }

    @Test
    fun theCalendarChangesThePeriod() {
        shown("За 30 дней")
        compose.onNodeWithContentDescription("Период").performClick()
        compose.onNodeWithText("Неделя").performClick()
        shown("За 7 дней")
    }
}

package sh.aminov.golda

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.PeriodKind
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.InsightsDetails
import java.time.LocalDate
import java.util.TimeZone

/** The card's second level: the categories in full, a picked day's purchases, the way back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
class InsightsDetailsTest {
    @get:Rule val compose = createComposeRule()

    private var back = false

    @Before
    fun fix() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun open(filter: Bucket? = null, kind: PeriodKind = PeriodKind.MONTH, bar: Period? = null) {
        val data = ScreenshotTest().demo()
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { InsightsDetails(data, filter, Analytics.period(kind, data.today), bar, { back = true }, {}) } }
    }

    private fun shown(text: String, times: Int = 1) = compose.onAllNodesWithText(text).assertCountEquals(times)

    @Test
    fun everyCategoryWithNoPick() {
        open()
        shown("Все траты")
        shown("Жильё")
        shown("Транспорт")
        compose.onAllNodesWithText("За 30 дней ·", substring = true).assertCountEquals(1)
    }

    @Test
    fun aKindNarrowsItAndNamesIt() {
        open(Bucket.DAILY)
        shown("Ежедневные")
        shown("Жильё", 0)
    }

    @Test
    fun aDaysPurchases() {
        val day = LocalDate.of(2026, 10, 24)
        open(kind = PeriodKind.WEEK, bar = Period(day, day))
        shown("Шаурма")
        compose.onAllNodesWithText("За субботу, 24 октября ·", substring = true).assertCountEquals(1)
    }

    @Test
    fun aWeeksCategories() {
        open(bar = Period(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24)))
        shown("Жильё", 0)
        shown("Еда")
        shown("Шаурма", 0)
    }

    @Test
    fun aCategoryOpensItsPurchases() {
        open()
        compose.onNodeWithText("Еда").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Шаурма").assertCountEquals(1)
    }

    @Test
    fun theArrowGoesBack() {
        open()
        compose.onNodeWithContentDescription("Назад").performClick()
        assertTrue(back)
    }
}

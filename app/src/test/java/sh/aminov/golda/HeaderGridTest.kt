package sh.aminov.golda

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.Tags
import java.util.TimeZone

/** The tabs' headers by one rule: a title, a caption where there is one, the figure, the corner's mark in one place. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HeaderGridTest {
    @get:Rule val compose = createComposeRule()

    private var periods = 0

    @Before
    fun world() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun home(data: AppData = ScreenshotTest().demo()) = compose.setContent {
        GoldaTheme("golda", "LIGHT", "ROOMY") { HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, { periods++ }, onEdit = {}) }
    }

    private fun bounds(description: String) = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
    private fun tagged(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun theFigureStandsOnOneLineOnAllThreeTabsAndTheGearWhereTheCalendarIs() {
        val tab = androidx.compose.runtime.mutableStateOf(0)
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                when (tab.value) {
                    0 -> HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                    1 -> AnalyticsScreen(data, PaddingValues()) {}
                    else -> AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> })
                }
            }
        }
        val gear = bounds("Настройки")
        val home = tagged(Tags.HOME_BIG)
        tab.value = 1
        compose.waitForIdle()
        assertEquals(gear, bounds("Период"))
        assertEquals(home.top, tagged(Tags.INSIGHTS_TOTAL).top, 1f)
        assertEquals(home.left, tagged(Tags.INSIGHTS_TOTAL).left, 1f)
        tab.value = 2
        compose.waitForIdle()
        assertEquals(home.top, tagged(Tags.ACCOUNTS_TOTAL).top, 1f)
        assertEquals(home.left, tagged(Tags.ACCOUNTS_TOTAL).left, 1f)
    }

    @Test
    fun theStatusIsTheTitleItsLineChangesThePeriodAndTheCaptionNamesTheFigure() {
        home()
        compose.onAllNodesWithText("Сверх нормы", substring = true, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Можно сегодня", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("План", substring = true, useUnmergedTree = true).assertCountEquals(1)
        assertEquals(true, tagged(Tags.HOME_RUNWAY).bottom < tagged(Tags.HOME_BIG).top)
        compose.onNodeWithTag(Tags.HOME_PERIOD).performClick()
        assertEquals(1, periods)
    }

    @Test
    fun withoutAPeriodThereIsTheTitleAndItsCaptionAlone() {
        val demo = ScreenshotTest().demo()
        home(AppData(demo.settings, emptyList(), emptyList(), demo.categories, emptyList(), today = demo.today))
        compose.onNodeWithText("Период не задан", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Можно сегодня", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(Tags.HOME_BIG, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag(Tags.HOME_PERIOD).assertCountEquals(0)
    }

    private fun accounts(data: AppData) = compose.setContent {
        GoldaTheme("golda", "LIGHT", "ROOMY") { AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> }) }
    }

    @Test
    fun accountsOpenWithTheMoneyThereItsDebtsAndTheOtherCurrenciesUnderTheFigure() {
        val data = ScreenshotTest().demo()
        accounts(data)
        compose.onAllNodesWithText("На счетах", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Долги " + data.base.whole(21_500_000), useUnmergedTree = true).assertCountEquals(1)
        val others = data.others(Debts.heldRub(data.states.values), data.base.code)
        val under = compose.onAllNodesWithText(others, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertEquals(true, under.top > tagged(Tags.ACCOUNTS_TOTAL).bottom)
    }

    @Test
    fun withoutDebtsTheCaptionSaysSoAndTheFigureKeepsItsLine() {
        val demo = ScreenshotTest().demo()
        val free = AppData(demo.settings, demo.accounts.filterNot { with(Debts) { it.isDebt } }, demo.operations, demo.categories, emptyList(), today = demo.today)
        accounts(free)
        compose.onAllNodesWithText("Долгов нет", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Долги", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}

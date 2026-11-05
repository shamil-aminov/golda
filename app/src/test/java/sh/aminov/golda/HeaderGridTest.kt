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
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
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
    fun theFigureStandsOnOneLineOnAllThreeTabs() {
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
        val home = tagged(Tags.HOME_BIG)
        tab.value = 1
        compose.waitForIdle()
        assertEquals(home.top, tagged(Tags.INSIGHTS_TOTAL).top, 1f)
        assertEquals(home.left, tagged(Tags.INSIGHTS_TOTAL).left, 1f)
        tab.value = 2
        compose.waitForIdle()
        assertEquals(home.top, tagged(Tags.ACCOUNTS_TOTAL).top, 1f)
        assertEquals(home.left, tagged(Tags.ACCOUNTS_TOTAL).left, 1f)
    }

    @Test
    fun theLineUnderTheBarSaysHowItGoesAndChangesThePeriod() {
        home()
        compose.onAllNodesWithText("На сегодня", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Перерасход", substring = true, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText(" до ", substring = true, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("план", substring = true, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(true, tagged(Tags.HOME_RUNWAY).top > tagged(Tags.HOME_BIG).bottom)
        compose.onNodeWithTag(Tags.HOME_PERIOD).performClick()
        assertEquals(1, periods)
    }

    @Test
    fun withoutAPeriodThereIsTheTitleAndItsCaptionAlone() {
        val demo = ScreenshotTest().demo()
        home(AppData(demo.settings, emptyList(), emptyList(), demo.categories, emptyList(), today = demo.today))
        compose.onNodeWithText("Период не задан", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("На сегодня", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(Tags.HOME_BIG, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag(Tags.HOME_PERIOD).assertCountEquals(0)
    }

    private fun accounts(data: AppData) = compose.setContent {
        GoldaTheme("golda", "LIGHT", "ROOMY") { AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> }) }
    }

    private fun copy(demo: AppData, accounts: List<Account> = demo.accounts) =
        AppData(demo.settings, accounts, demo.operations, demo.categories, emptyList(), today = demo.today)

    private fun count(words: String, times: Int) =
        compose.onAllNodesWithText(words, substring = true, useUnmergedTree = true).assertCountEquals(times)

    @Test
    fun accountsOpenWithTheMoneyThereAndTheOtherCurrenciesUnderTheFigure() {
        val data = ScreenshotTest().demo()
        accounts(data)
        count("На счетах", 1)
        count("Сверено", 0)
        count("Проценты по долгам", 1)
        count("Долги", 0)
        val others = data.others(Debts.heldRub(data.states.values), data.base.code)
        val under = compose.onAllNodesWithText(others, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertEquals(true, under.top > tagged(Tags.ACCOUNTS_TOTAL).bottom)
    }

    @Test
    fun withoutDebtsThereIsNoBlockOfThem() {
        val demo = ScreenshotTest().demo()
        accounts(copy(demo, accounts = demo.accounts.filterNot { with(Debts) { it.isDebt } }))
        count("Проценты", 0)
    }

    @Test
    fun aDebtWithoutARateIsNamed() {
        val demo = ScreenshotTest().demo()
        accounts(copy(demo, accounts = demo.accounts.map { if (it.type == AccountType.LOAN) it.copy(interestRate = null) else it }))
        count("Проценты, кроме 1 долга", 1)
    }

    @Test
    fun noRatesAtAllMeanNoBlock() {
        val demo = ScreenshotTest().demo()
        accounts(copy(demo, accounts = demo.accounts.map { it.copy(interestRate = null) }))
        count("Проценты", 0)
    }
}

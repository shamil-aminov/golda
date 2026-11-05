package sh.aminov.golda

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
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
        GoldaTheme("golda", "LIGHT", "ROOMY") { HomeScreen(data, PaddingValues(), rememberLazyListState(), { periods++ }, onEdit = {}) }
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
                    0 -> HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, onEdit = {})
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
    fun theTilesSayWhatWentAndWhatIsLeftAndThePillChangesThePeriod() {
        home()
        compose.onAllNodesWithText("Остаток дня", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Ушло", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Осталось", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Ещё ", substring = true, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Настройки").assertCountEquals(0)
        assertEquals(true, tagged(Tags.HOME_SPENT).top > tagged(Tags.HOME_BIG).bottom)
        // Left the burn, right the day's plan, each its tile's one node.
        compose.onAllNodesWithText("122 в день", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("47 в день", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText(" из 47", substring = true, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag(Tags.HOME_PERIOD).performClick()
        assertEquals(1, periods)
    }

    @Test
    fun aDayPastItsPlanHasNoOutOfAndThePaceStays() {
        val demo = ScreenshotTest().demo()
        val noon = demo.today.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val more = OperationFull(
            Operation(9_999, OpType.EXPENSE, noon, categoryId = demo.categories.first().id, note = "Ужин"),
            listOf(Posting(operationId = 9_999, accountId = demo.envelope!!.account.id, amountMinor = -6_000, rubMinor = -204_000)),
        )
        val past = AppData(demo.settings, demo.accounts, demo.operations + more, demo.categories, emptyList(), demo.obligations, demo.today)
        assertEquals(true, past.envelope!!.leftTodayMinor < 0)
        home(past)
        compose.onAllNodesWithText(" из ", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("в день", substring = true, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun withoutAPeriodThereIsTheTitleAndItsCaptionAlone() {
        val demo = ScreenshotTest().demo()
        home(AppData(demo.settings, emptyList(), emptyList(), demo.categories, emptyList(), today = demo.today))
        compose.onNodeWithText("Период не задан", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Остаток дня", useUnmergedTree = true).assertCountEquals(1)
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
        count("Всего денег", 1)
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

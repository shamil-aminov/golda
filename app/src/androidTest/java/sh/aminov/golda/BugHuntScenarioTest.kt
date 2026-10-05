package sh.aminov.golda

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.tr
import sh.aminov.golda.ui.Tags

/** The bug hunt's UI findings, each driven the way it was found. */
@RunWith(AndroidJUnit4::class)
class BugHuntScenarioTest : GoldaUiTest() {

    private fun openEntry() {
        compose.onNodeWithContentDescription(tr("Записать вручную", "Add by hand")).performClick()
        compose.waitForIdle()
    }

    /** The symbol picked in the entry form's currency group. */
    private fun pickedCurrency(): String? =
        compose.onAllNodes(toggle).fetchSemanticsNodes()
            .filter { it.text() in setOf("₽", "$", "₾") }
            .firstOrNull { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On }
            ?.text()

    /** #2: an amount of 25 digits is simply not a valid amount. */
    @Test
    fun aVeryLongAmountDoesNotCrash() {
        openEntry()
        typeIntoFirstField("1234567890123456789012345")
        compose.onNodeWithText(tr("Записать", "Save")).assertIsNotEnabled()
        typeIntoFirstField("1e25")
        compose.onNodeWithText(tr("Записать", "Save")).assertIsNotEnabled()
        closeEntry()
    }

    /** #9: Transfer and back to Expense keeps the purchase in the local currency. */
    @Test
    fun switchingToTransferAndBackKeepsTheLocalCurrency() {
        runBlocking { repo.settings.update { it.copy(localCurrency = "GEL") } }
        compose.waitForIdle()
        openEntry()
        typeIntoFirstField("15")
        assertEquals("₾", pickedCurrency())
        compose.onNode(hasText(tr("Перевод", "Transfer")) and toggle).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(tr("Расход", "Expense")) and toggle).performClick()
        compose.waitForIdle()
        assertEquals("₾", pickedCurrency())
        closeEntry()
    }

    /** Polish: a credit card is reconciled by what is owed, as the bank shows it. */
    @Test
    fun aDebtIsReconciledByWhatIsOwed() {
        runBlocking { repo.saveAccount(Account(name = "Visa", currency = "RUB", type = AccountType.CREDIT, includeInFree = false, sort = 3), -1_500_000) }
        compose.onNodeWithContentDescription(tr("Счета", "Accounts")).performClick()
        compose.waitForIdle()
        waitForText("Visa")
        tap("Visa")
        waitForText(tr("Сверить", "Reconcile"))
        tap(tr("Сверить", "Reconcile"))
        typeIntoFirstField("15000")
        compose.onNodeWithText(tr("Сходится", "It matches")).assertIsDisplayed()
    }

    /** Polish: back on another tab goes Home, not out of the app. */
    @Test
    fun backOnAnotherTabGoesHome() {
        compose.onNodeWithContentDescription(tr("Аналитика", "Insights")).performClick()
        compose.waitForIdle()
        back()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(tr("Можно сегодня", "Safe to spend today"))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(Tags.HOME_OTHERS, useUnmergedTree = true).assertIsDisplayed()
    }

    /** Polish: a wish's reminder opens that wish in "Сомневаюсь". */
    @Test
    fun aWishReminderOpensTheWish() {
        val id = runBlocking {
            repo.wishes.think(VoiceAction.Consider("Наушники", 1_500_000, "RUB"))
            repo.wishes.wishes.first().single().id
        }
        scenario.close()
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java).putExtra(EXTRA_WISH, id)
        scenario = ActivityScenario.launch(intent)
        waitForText(tr("Беру", "Buy"))
        assertTrue(compose.onAllNodes(hasText("Наушники")).fetchSemanticsNodes().isNotEmpty())
        runBlocking { repo.wishes.cancelReminders() }
    }
}

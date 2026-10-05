package sh.aminov.golda

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import sh.aminov.golda.domain.tr
import sh.aminov.golda.ui.Tags

/** Settings → Основная валюта → $: the big numbers and totals follow; back to ₽ gives the old picture. */
@RunWith(AndroidJUnit4::class)
class BaseCurrencyScenarioTest : GoldaUiTest() {

    /** A big number reads out as its whole text ("1 849 ₽"); it sits under the tagged box. */
    private fun bigNumber(tag: String): String {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.ContentDescription) != null }
        }
        return compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .firstNotNullOf { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("") }
    }

    private fun openTab(name: String) {
        compose.onNodeWithContentDescription(name).performClick()
        compose.waitForIdle()
    }

    private fun pickBase(label: String) {
        openSettings()
        openRow(tr("Основная валюта", "Main currency"))
        // The choice in the sheet, not the row behind it that may say the same.
        compose.onNode(hasText(label) and toggle).performClick()
        compose.waitForIdle()
        back()
    }

    @Test
    fun mainCurrencyMovesTheBigNumbersAndComesBack() {
        val homeBefore = bigNumber(Tags.HOME_BIG)
        val othersBefore = textOf(Tags.HOME_OTHERS)
        assertTrue("starts in rubles: $homeBefore", homeBefore.endsWith(" ₽"))

        pickBase("$ USD")
        assertEquals("USD", runBlocking { repo.settings.flow.first().baseCurrency })

        // Home: the big number is in dollars, and rubles moved to the line of the others.
        val home = bigNumber(Tags.HOME_BIG)
        assertTrue("home is in dollars: $home", home.endsWith(" $"))
        val others = textOf(Tags.HOME_OTHERS)
        assertTrue("others has rubles: $others", others.split(" · ").any { it.endsWith("₽") })
        assertFalse("others has no dollars: $others", others.split(" · ").any { it.endsWith("$") })

        // Accounts: "Всего" is in dollars too.
        openTab(tr("Счета", "Accounts"))
        val total = bigNumber(Tags.ACCOUNTS_TOTAL)
        assertTrue("accounts total is in dollars: $total", total.endsWith(" $"))

        // Back to rubles: the old big number, and the same currencies under it. (The figures under it may
        // move by a kopeck's worth: the rates can refresh from the network while the test runs.)
        openTab(tr("Главная", "Home"))
        pickBase("₽ RUB")
        assertEquals(homeBefore, bigNumber(Tags.HOME_BIG))
        fun symbols(line: String) = line.split(" · ").map { it.substringAfterLast(' ') }
        assertEquals(symbols(othersBefore), symbols(textOf(Tags.HOME_OTHERS)))
        openTab(tr("Счета", "Accounts"))
        assertTrue(bigNumber(Tags.ACCOUNTS_TOTAL).endsWith(" ₽"))
    }
}

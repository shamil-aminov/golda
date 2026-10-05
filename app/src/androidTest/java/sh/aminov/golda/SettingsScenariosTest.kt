package sh.aminov.golda

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.tr
import sh.aminov.golda.ui.Tags
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** "I changed X in settings, and it really changed": each test goes Settings → change → back → look where it shows. */
@RunWith(AndroidJUnit4::class)
class SettingsScenariosTest : GoldaUiTest() {

    private fun openEntry() {
        compose.onNodeWithContentDescription(tr("Записать вручную", "Add by hand")).performClick()
        compose.waitForIdle()
    }

    /** The currency symbols of the entry form's currency group, in order, and which one is picked. */
    private fun currencyGroup(symbols: Set<String>): List<Pair<String, Boolean>> =
        compose.onAllNodes(toggle).fetchSemanticsNodes()
            .map { node -> node.text() to (node.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On) }
            .filter { (label, _) -> label in symbols }

    @Test
    fun localCurrencyIsWhereANewPurchaseStarts() {
        openSettings()
        openRow(tr("Местная валюта", "Local currency"))
        tap("$ USD")
        compose.onNodeWithText("$ USD").assertIsDisplayed() // the row now says it
        back()

        // Home: the local currency leads the line of other currencies.
        val others = textOf(Tags.HOME_OTHERS)
        assertTrue("others line starts with dollars: $others", others.substringBefore(" · ").endsWith("$"))

        // A new expense: from the dollar account, with dollars first and picked in the currency group
        // (the picked currency is the one the amount is typed in).
        openEntry()
        waitForText("Доллары · $")
        compose.onNodeWithText("Доллары · $").assertIsDisplayed()
        val group = currencyGroup(setOf("₽", "$", "₾"))
        assertEquals("$", group.first().first)
        assertTrue("dollars are picked: $group", group.first().second)
        closeEntry()
    }

    @Test
    fun shownCurrenciesAreExactlyTheOthersLine() {
        assertEquals(setOf("$", "₾"), othersSymbols())

        openSettings()
        openRow(tr("Показывать суммы в", "Show amounts in"))
        tap("₾ GEL") // off
        tap(tr("Готово", "Done"))
        back()
        assertEquals(setOf("$"), othersSymbols())

        openSettings()
        openRow(tr("Показывать суммы в", "Show amounts in"))
        tap("฿ THB") // on
        tap(tr("Готово", "Done"))
        back()
        assertEquals(setOf("$", "฿"), othersSymbols())
    }

    private fun othersSymbols(): Set<String> =
        textOf(Tags.HOME_OTHERS).split(" · ").map { it.trim().substringAfterLast(' ') }.toSet()

    @Test
    fun paydayMovesTheDaysLeft() {
        val today = LocalDate.now()
        val day = ((today.dayOfMonth + 9) % 28 + 1).let { if (it == 15) 16 else it }
        openSettings()
        openRow(tr("День зарплаты", "Payday"))
        compose.onNodeWithText(day.toString(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        back()

        val days = ChronoUnit.DAYS.between(today, Settings(payday = day).nextPayday(today)).coerceAtLeast(1)
        val word = plural(days, "день", "дня", "дней", "day", "days")
        assertEquals(tr(" · $days $word до зарплаты", " · $days $word to payday"), textOf(Tags.HOME_PAYDAY))
    }

    @Test
    fun rateAndTaxMoveTheHourAndTheHoursOfWork() {
        openSettings()
        openRow(tr("Ставка", "Rate"))
        typeIntoFirstField("3000")
        tap(tr("Сохранить", "Save"))
        openRow(tr("Налог и часы", "Tax and hours"))
        typeIntoFirstField("20")
        tap(tr("Сохранить", "Save"))
        // 3 000 ₽ an hour, 20 % tax: 2 400 ₽ on hand.
        assertEquals(Fmt.approx(2_400.0, "RUB"), textOf(Tags.HOUR_NET))
        back()

        // 4 800 ₽ is two hours of that work.
        openEntry()
        typeIntoFirstField("4800")
        back() // the keyboard
        tap(tr("Сомневаюсь", "Not sure"))
        compose.waitUntil(5_000) { runCatching { textOf(Tags.DECIDE_HOURS) != "—" }.getOrDefault(false) }
        assertEquals(Fmt.number(2.0, 1), textOf(Tags.DECIDE_HOURS))
        back() // back to the form
        back() // close
    }

    @Test
    fun markupMovesTheRates() {
        openSettings()
        openRow(tr("Наценка к ЦБ", "Markup over the CBR"))
        typeIntoFirstField("20")
        tap(tr("Сохранить", "Save"))
        compose.onNodeWithText("20 %").assertIsDisplayed()
        // The dollar row is the CBR rate plus 20 %, whatever the rate is today.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText(dollarRow(0.20)).assertIsDisplayed(); true }.getOrDefault(false)
        }
    }

    private fun dollarRow(markup: Double): String {
        val rates = runBlocking { repo.rates.first() }.associate { it.code to it.rubPerUnit }
        return "1 $ = ${Fmt.number(Rates(rates, markup).display("USD")!!, 2)} ₽"
    }

    @Test
    fun settingsSurviveRecreatingTheActivity() {
        openSettings()
        openRow(tr("Местная валюта", "Local currency"))
        tap("₾ GEL")
        openRow(tr("Наценка к ЦБ", "Markup over the CBR"))
        typeIntoFirstField("15")
        tap(tr("Сохранить", "Save"))

        // Settings stays open across the recreation, showing what was stored.
        scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("₾ GEL").assertIsDisplayed()
        compose.onNodeWithText("15 %").assertIsDisplayed()
        val stored = runBlocking { repo.settings.flow.first() }
        assertEquals("GEL", stored.localCurrency)
        assertEquals(0.15, stored.markup, 1e-9)
        assertFalse(stored.displayCurrencies.isEmpty())
        compose.onNodeWithTag(Tags.HOUR_NET, useUnmergedTree = true).assertExists()
    }
}

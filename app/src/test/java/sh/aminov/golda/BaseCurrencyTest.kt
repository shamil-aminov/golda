package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Backup
import sh.aminov.golda.data.BackupFormat
import sh.aminov.golda.data.Rate
import sh.aminov.golda.domain.Base
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.toggleDisplayCurrency

/** The main currency: ruble aggregates shown in it at today's display rate, and nothing else changes. */
class BaseCurrencyTest {
    private val rates = Rates(mapOf("USD" to 83.25, "GEL" to 31.96), markup = 0.10)

    // 1 849 ₽ "можно сегодня", 2 648 ₽ a day.
    private val left = 184_900L
    private val perDay = 264_800L

    @Test
    fun rublesStayExactlyAsTheyWere() {
        val base = Base(rates, "RUB")
        assertEquals(Fmt.whole(left, "RUB") + " ₽", base.whole(left))
        assertEquals(Fmt.approx(perDay / 100.0, "RUB"), base.approx(perDay))
        assertEquals(left, base.minor(left))
        assertEquals("RUB", base.code)
    }

    @Test
    fun aggregatesConvertAtTheDisplayRate() {
        val usd = Base(rates, "USD")
        val dollar = 83.25 * 1.10
        assertEquals(1_849.0 / dollar, usd.major(left), 1e-9)
        assertEquals(Fmt.approx(2_648.0 / dollar, "USD"), usd.approx(perDay))
        assertEquals("20 $", usd.whole(left)) // 20,19 $
        assertEquals(2_019L, usd.minor(left))

        val lari = Base(rates, "GEL")
        assertEquals("53 ₾", lari.whole(left)) // 52,59 ₾, rounded half up
        assertEquals(Fmt.approx(1_849.0 / (31.96 * 1.10), "GEL"), lari.approx(left))
    }

    @Test
    fun aCurrencyWithoutARateFallsBackToRubles() {
        val base = Base(rates, "THB")
        assertEquals("RUB", base.code)
        assertEquals(base.whole(left), Base(rates, "RUB").whole(left))
    }

    @Test
    fun theOthersLineLeavesOutTheMainCurrencyAndKeepsRubles() {
        val settings = Settings(displayCurrencies = listOf("RUB", "USD", "GEL"), localCurrency = "GEL", baseCurrency = "GEL")
        val data = AppData(settings, emptyList(), emptyList(), emptyList(), listOf(Rate("USD", 83.25, "d"), Rate("GEL", 31.96, "d")))
        val symbols = data.others(left, data.base.code).split(" · ").map { it.substringAfterLast(' ') }
        assertEquals(listOf("₽", "$"), symbols)
    }

    @Test
    fun hidingTheMainCurrencyFallsBackToRubles() {
        val s = Settings(displayCurrencies = listOf("RUB", "USD", "GEL"), localCurrency = "RUB", baseCurrency = "USD")
        val hidden = toggleDisplayCurrency(s, "USD")
        assertEquals("RUB", hidden.baseCurrency)
        assertFalse("USD" in hidden.displayCurrencies)
        // Hiding another one leaves it alone.
        assertEquals("USD", toggleDisplayCurrency(s, "GEL").baseCurrency)
    }

    @Test
    fun theMainCurrencyTravelsWithABackup() {
        val backup = Backup(
            exportedAt = 0,
            settings = Settings(baseCurrency = "GEL"),
            accounts = emptyList(), categories = emptyList(), operations = emptyList(), postings = emptyList(),
            rates = emptyList(), obligations = emptyList(), goals = emptyList(), wishes = emptyList(),
        )
        val text = BackupFormat.encode(backup)
        assertTrue(text.contains("\"baseCurrency\": \"GEL\""))
        assertEquals("GEL", BackupFormat.decode(text).settings.baseCurrency)

        // A backup from before the setting existed: rubles.
        val old = text.lines().filterNot { "baseCurrency" in it }.joinToString("\n")
        assertEquals("RUB", BackupFormat.decode(old).settings.baseCurrency)
    }
}

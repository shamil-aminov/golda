package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Rate
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.toggleDisplayCurrency
import java.time.LocalDate
import java.time.ZoneOffset

/** Where each setting reaches the logic: a change in Settings has to change what these return. */
class SettingsEffectsTest {
    private val rubCard = Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true, sort = 0)
    private val usdCard = Account(2, "Доллары", "USD", AccountType.CARD, includeInFree = true, sort = 1)
    private val gelCash = Account(3, "Лари", "GEL", AccountType.CASH, includeInFree = true, sort = 2)
    private val accounts = listOf(rubCard, usdCard, gelCash)
    private val rates = listOf(Rate("USD", 83.25, "2026-10-02"), Rate("GEL", 31.96, "2026-10-02"))

    private fun data(s: Settings) = AppData(s, accounts, emptyList(), emptyList(), rates)

    @Test
    fun theLocalCurrencyLeadsTheCurrencyChoicesAndTheOthersLine() {
        val shown = listOf("RUB", "USD", "GEL")
        assertEquals(listOf("RUB", "USD", "GEL"), data(Settings(displayCurrencies = shown, localCurrency = "RUB")).currencyChoices())
        assertEquals(listOf("GEL", "RUB", "USD"), data(Settings(displayCurrencies = shown, localCurrency = "GEL")).currencyChoices())
        val others = data(Settings(displayCurrencies = shown, localCurrency = "GEL")).others(100_000, "RUB")
        assertEquals(listOf("₾", "$"), others.split(" · ").map { it.substringAfterLast(' ') })
    }

    @Test
    fun theOthersLineShowsExactlyTheShownCurrencies() {
        val line = data(Settings(displayCurrencies = listOf("RUB", "USD"), localCurrency = "RUB")).others(100_000, "RUB")
        assertEquals(listOf("$"), line.split(" · ").map { it.substringAfterLast(' ') })
    }

    @Test
    fun aNewPurchaseIsPaidFromAnAccountInTheLocalCurrency() {
        // The entry form asks the same question as voice: who pays in this currency?
        val usedLast = Settings(lastAccountId = rubCard.id)
        assertEquals(gelCash.id, VoiceMapper.pick(accounts, usedLast.copy(localCurrency = "GEL").localCurrency, usedLast)?.id)
        assertEquals(usdCard.id, VoiceMapper.pick(accounts, "USD", usedLast)?.id)
        assertEquals(rubCard.id, VoiceMapper.pick(accounts, "RUB", usedLast)?.id)
        // No account in the local currency: the last one used, converted.
        assertEquals(rubCard.id, VoiceMapper.pick(accounts, "THB", usedLast)?.id)
    }

    @Test
    fun hidingTheLocalCurrencyFallsBackToRubles() {
        val s = Settings(displayCurrencies = listOf("RUB", "USD", "GEL"), localCurrency = "GEL")
        val hidden = toggleDisplayCurrency(s, "GEL")
        assertEquals(listOf("RUB", "USD"), hidden.displayCurrencies)
        assertEquals("RUB", hidden.localCurrency)
        // Rubles can't be hidden: they are the base.
        assertEquals(s, toggleDisplayCurrency(s, "RUB"))
    }

    @Test
    fun theDailyLimitSetsWhatIsLeftToday() {
        val today = LocalDate.of(2026, 10, 2)
        val states = Ledger.states(accounts, emptyList())
        fun left(limit: Long) = Budget.today(states, emptyList(), emptyMap(), Settings(dailyLimitRub = limit), today, ZoneOffset.UTC)
        assertEquals(150_000L, left(150_000).leftTodayRub)
        assertEquals(false, left(150_000).autoLimit)
        // Zero means no limit: the pace, here none yet.
        assertEquals(0L, left(0).limitRub)
        assertEquals(true, left(0).autoLimit)
    }
}

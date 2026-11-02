package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.VoiceItem
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.domain.VoiceResult
import java.time.LocalDate
import java.time.ZoneOffset

/** The bug hunt of 2026-10-05, finding by finding (the numbers are the report's). */
class BugHuntTest {
    private val zone = ZoneOffset.UTC
    private val rates = Rates(mapOf("USD" to 83.25, "GEL" to 31.96), markup = 0.10)
    private fun at(date: LocalDate) = date.atTime(12, 0).toInstant(zone).toEpochMilli()

    // #2: a long amount, a pasted one or a misheard "1e25" is not an amount, and never a crash.
    @Test
    fun absurdAmountsAreRejectedNotThrown() {
        assertNull(Fmt.parseMinor("1".repeat(25), "RUB"))
        assertNull(Fmt.parseMinor("123456789012345678", "RUB"))
        assertNull(Fmt.parseMinor("1e25", "USD"))
        assertNull(Fmt.parseMinor("9".repeat(400), "RUB"))
        assertNull(Fmt.parseMinor("1e999999999999", "RUB"))
        assertNull(Fmt.parseMinor("-5", "RUB"))
        // The largest amount still taken, and an ordinary one.
        assertEquals(Fmt.MAX_MAJOR * 100, Fmt.parseMinor(Fmt.MAX_MAJOR.toString(), "RUB"))
        assertEquals(150_025L, Fmt.parseMinor("1 500,25", "RUB"))
        assertNull(Fmt.parseDouble("1e400"))
    }

    // #2 via voice: Gemini's "1e25" becomes "not understood", not a crash.
    @Test
    fun aMisheardHugeAmountIsNotUnderstood() {
        val card = Account(1, "Карта", "RUB", AccountType.CARD, includeInFree = true)
        val item = VoiceItem("expense", "1e25", "RUB", "кофе", null, null, null, null, null)
        val action = VoiceMapper.actions(VoiceResult("…", listOf(item)), listOf(card), emptyList(), Settings(), rates, at(LocalDate.of(2026, 10, 5)), zone).single()
        assertTrue(action is VoiceAction.NotUnderstood)
    }

    // #10: a paid-off card costs nothing a day; a small debt only what is owed.
    @Test
    fun debtPaymentsFollowWhatIsOwed() {
        val card = Account(5, "Кредитка", "RUB", AccountType.CREDIT, includeInFree = false, paymentDay = 20, paymentMinor = 300_000)
        val cash = Account(6, "Карта", "RUB", AccountType.CARD, includeInFree = true)
        val today = LocalDate.of(2026, 10, 5)
        val opening = OperationFull(Operation(1, OpType.OPENING, at(today.minusDays(10))), listOf(Posting(1, 1, cash.id, 2_000_000, 2_000_000)))
        val paidOff = Ledger.states(listOf(card, cash), opening.postings)
        assertEquals(0L, Budget.today(paidOff, listOf(opening), emptyMap(), Settings(), today, zone).regularPerDayRub)
        val owing = OperationFull(Operation(2, OpType.EXPENSE, at(today.minusDays(3))), listOf(Posting(2, 2, card.id, -100_000, -100_000)))
        val states = Ledger.states(listOf(card, cash), opening.postings + owing.postings)
        assertEquals(100_000L * 12 / 365, Budget.today(states, listOf(opening, owing), emptyMap(), Settings(), today, zone).regularPerDayRub)
    }

    // #13: income said in another currency is an estimate, and keeps what was said.
    @Test
    fun aForeignIncomeKeepsItsAmountAndIsAnEstimate() {
        val card = Account(1, "Карта", "RUB", AccountType.CARD, includeInFree = true)
        val item = VoiceItem("income", "100", "USD", "подарок", null, null, null, null, null)
        val draft = (VoiceMapper.actions(VoiceResult("…", listOf(item)), listOf(card), emptyList(), Settings(), rates, at(LocalDate.of(2026, 10, 5)), zone).single() as VoiceAction.Record).draft
        assertTrue(draft.isEstimate)
        assertEquals(10_000L, draft.purchaseAmountMinor)
        assertEquals("USD", draft.purchaseCurrency)
        assertEquals(rates.convert(10_000, "USD", "RUB"), draft.amountMinor)

        // A transfer said in another currency than its source is an estimate too.
        val usd = Account(2, "Доллары", "USD", AccountType.CARD, includeInFree = true)
        val move = VoiceItem("transfer", "100", "USD", "", null, "1", "2", "1.2", null)
        val transfer = (VoiceMapper.actions(VoiceResult("…", listOf(move)), listOf(card, usd), emptyList(), Settings(), rates, at(LocalDate.of(2026, 10, 5)), zone).single() as VoiceAction.Record).draft
        assertTrue(transfer.isEstimate)
    }
}

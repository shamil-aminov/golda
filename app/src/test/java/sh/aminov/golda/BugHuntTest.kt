package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Goal
import sh.aminov.golda.data.Obligation
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.PeriodKind
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

    // #10: a paid-off card reserves nothing; a small debt reserves only what is owed.
    @Test
    fun obligationsFollowWhatIsOwed() {
        val card = Account(5, "Кредитка", "RUB", AccountType.CREDIT, includeInFree = false, paymentDay = 20, paymentMinor = 300_000)
        val cash = Account(6, "Карта", "RUB", AccountType.CARD, includeInFree = true)
        val today = LocalDate.of(2026, 10, 5)
        val settings = Settings(payday = 25)
        val opening = OperationFull(Operation(1, OpType.OPENING, at(today.minusDays(10))), listOf(Posting(1, 1, cash.id, 2_000_000, 2_000_000)))
        val paidOff = Ledger.states(listOf(card, cash), opening.postings)
        val free = Budget.today(paidOff, listOf(opening), settings, today, zone, Debts.obligations(paidOff.values))
        assertEquals(0L, free.obligationsRub)
        val owing = OperationFull(Operation(2, OpType.EXPENSE, at(today.minusDays(3))), listOf(Posting(2, 2, card.id, -100_000, -100_000)))
        val states = Ledger.states(listOf(card, cash), opening.postings + owing.postings)
        assertEquals(100_000L, Budget.today(states, listOf(opening, owing), settings, today, zone, Debts.obligations(states.values)).obligationsRub)
    }

    // Polish: a payment made early is not set aside a second time.
    @Test
    fun anObligationPaidEarlyIsNotReservedAgain() {
        val today = LocalDate.of(2026, 10, 20)
        val rent = Obligation(id = 1, name = "Аренда", amountMinor = 4_000_000, currency = "RUB", dayOfMonth = 25)
        val paid = OperationFull(Operation(1, OpType.EXPENSE, at(today), note = "Аренда"), listOf(Posting(1, 1, 1, -4_000_000, -4_000_000)))
        assertEquals(4_000_000L, Budget.unpaid(rent, emptyList(), today, zone))
        assertEquals(0L, Budget.unpaid(rent, listOf(paid), today, zone))
        // Last month's rent (paid on its due day, 25 September) does not cover October's.
        val september = OperationFull(Operation(2, OpType.EXPENSE, at(LocalDate.of(2026, 9, 25)), note = "Аренда"), listOf(Posting(2, 2, 1, -4_000_000, -4_000_000)))
        assertEquals(4_000_000L, Budget.unpaid(rent, listOf(september), today, zone))

        // A loan's payment counts as made by money moved into the loan account.
        val loan = Obligation(id = -9, name = "Кредит", amountMinor = 1_000_000, currency = "RUB", dayOfMonth = 25)
        val transfer = OperationFull(
            Operation(3, OpType.TRANSFER, at(today.minusDays(2))),
            listOf(Posting(3, 3, 1, -600_000, -600_000), Posting(4, 3, 9, 600_000, 600_000)),
        )
        assertEquals(400_000L, Budget.unpaid(loan, listOf(transfer), today, zone))
    }

    // #12: paydays on the 29th to the 31st start the period on the right day.
    @Test
    fun sincePaydayStartsOnTheLastPayday() {
        val s = Settings(payday = 31)
        assertEquals(LocalDate.of(2026, 1, 31), Analytics.period(PeriodKind.SINCE_PAYDAY, LocalDate.of(2026, 2, 10), s).from)
        assertEquals(LocalDate.of(2026, 3, 31), Analytics.period(PeriodKind.SINCE_PAYDAY, LocalDate.of(2026, 4, 10), s).from)
        // Payday itself starts a new period.
        assertEquals(LocalDate.of(2026, 10, 15), Settings(payday = 15).lastPayday(LocalDate.of(2026, 10, 15)))
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

    // #11: a reached goal is paid from the account it is saved on, when that holds enough.
    @Test
    fun aGoalIsBoughtFromItsOwnAccount() {
        val card = Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true)
        val savings = Account(2, "Накопительный", "RUB", AccountType.SAVINGS, includeInFree = false)
        val accounts = listOf(card, savings)
        val goal = Goal(1, "Подушка", targetMinor = 10_000_000, currency = "RUB", accountId = savings.id)
        val settings = Settings(lastAccountId = card.id)
        fun states(saved: Long) = Ledger.states(accounts, listOf(Posting(1, 1, savings.id, saved, saved), Posting(2, 2, card.id, 500_000, 500_000)))

        val enough = VoiceMapper.buyGoal(goal, states(12_000_000), accounts, settings, rates, 0)!!
        assertEquals(savings.id, enough.accountId)
        assertEquals(10_000_000L, enough.amountMinor)

        // Not enough there: the usual rule (the last account used).
        assertEquals(card.id, VoiceMapper.buyGoal(goal, states(1_000_000), accounts, settings, rates, 0)!!.accountId)

        // A dollar goal on a ruble savings account: charged like a card purchase, from that account.
        val dollars = goal.copy(targetMinor = 50_000, currency = "USD")
        val converted = VoiceMapper.buyGoal(dollars, states(12_000_000), accounts, settings, rates, 0)!!
        assertEquals(savings.id, converted.accountId)
        assertEquals(50_000L, converted.purchaseAmountMinor)
        assertTrue(converted.isEstimate)
        assertFalse(converted.amountMinor == 50_000L)
    }
}

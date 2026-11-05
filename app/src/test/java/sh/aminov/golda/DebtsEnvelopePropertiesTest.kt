package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.AccountState
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Envelope
import sh.aminov.golda.domain.Envelopes
import sh.aminov.golda.domain.Light
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import java.time.LocalDate
import java.time.ZoneOffset

/** Debts and the spending envelope: rules that hold for any balance, rate, payment or day. */
class DebtsEnvelopePropertiesTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun aBiggerPaymentNeverTakesLonger() = prop {
        checkAll(Arb.long(10_000L..100_000_000L), Arb.double(0.0..60.0), Arb.long(1_000L..10_000_000L), Arb.long(1L..1_000_000L)) { owed, rate, pay, more ->
            val n = Debts.monthsLeft(owed, rate, pay) ?: return@checkAll
            val m = Debts.monthsLeft(owed, rate, pay + more)!!
            assertTrue("$n then $m", m <= n + 1e-9)
            // Every month's payment adds up to at least what is owed.
            assertTrue(pay * n >= owed - 1)
            assertTrue(Debts.interestLeft(owed, rate, pay)!! >= 0)
        }
    }

    @Test
    fun theEdgesOfAPayoff() {
        assertEquals(0.0, Debts.monthsLeft(0, 20.0, 1_000)!!, 0.0)
        assertNull(Debts.monthsLeft(100_000, 20.0, 0))
        // Without interest the months are just the debt over the payment.
        assertEquals(4.0, Debts.monthsLeft(100_000, 0.0, 25_000)!!, 1e-12)
        // A payment exactly the month's interest never pays it off.
        assertNull(Debts.monthsLeft(1_200_000, 12.0, 12_000))
        // A kopeck more does, eventually.
        assertTrue(Debts.monthsLeft(1_200_000, 12.0, 12_001)!! > 100)
        assertNull(Debts.interestLeft(1_200_000, 12.0, 12_000))
        assertEquals(0L, Debts.interestLeft(100_000, 0.0, 25_000))
    }

    @Test
    fun prepayingNeverCostsMore() = prop {
        checkAll(Arb.long(100_000L..50_000_000L), Arb.double(1.0..40.0), Arb.int(2..60), Arb.long(1L..50_000_000L)) { owed, rate, months, extra ->
            val r = rate / 100 / 12
            val pay = (owed * r / (1 - Math.pow(1 + r, -months.toDouble()))).toLong() + 1
            val p = Debts.prepay(owed, rate, pay, extra, null) ?: return@checkAll
            assertTrue(p.monthsAfter <= p.monthsNow)
            assertTrue(p.savedByTermMinor >= 0 && p.savedByPaymentMinor >= 0)
            assertTrue(p.paymentAfterMinor <= pay)
            assertEquals(minOf(extra, owed), p.extraMinor)
            assertNull(p.savingsWouldEarnMinor)
        }
    }

    @Test
    fun prepayingEverythingEndsIt() {
        val p = Debts.prepay(1_000_000, 20.0, 100_000, 5_000_000, savingsPercent = 12.0)!!
        assertEquals(1_000_000L, p.extraMinor)
        assertEquals(0, p.monthsAfter)
        assertEquals(0L, p.paymentAfterMinor)
        assertTrue(p.savingsWouldEarnMinor!! > 0)
        // Without interest: the same payment spread over the same months, smaller.
        val flat = Debts.prepay(1_200_000, 0.0, 100_000, 600_000, null)!!
        assertEquals(50_000L, flat.paymentAfterMinor)
        assertEquals(6, flat.monthsAfter)
        assertEquals(12, flat.monthsNow)
        // Savings at 12 % a year earn on the extra over the months it would have been paid down.
        val saved = Debts.prepay(1_200_000, 0.0, 100_000, 120_000, savingsPercent = 12.0)!!
        assertEquals(120_000 * (Math.pow(1.01, 12.0) - 1), saved.savingsWouldEarnMinor!!.toDouble(), 1.0)
    }

    @Test
    fun onlyOwedDebtsWithAPlanAreObligations() {
        fun debt(id: Long, type: AccountType, day: Int?, payment: Long?) =
            Account(id, "d$id", "RUB", type, includeInFree = false, interestRate = 20.0, paymentDay = day, paymentMinor = payment)
        val states = listOf(
            AccountState(debt(1, AccountType.LOAN, 10, 30_000), -1_000_000, -1_000_000),   // owed, planned
            AccountState(debt(2, AccountType.CREDIT, 5, 50_000), -20_000, -20_000),        // owes less than the payment
            AccountState(debt(3, AccountType.LOAN, 10, 30_000), 0, 0),                     // paid off
            AccountState(debt(4, AccountType.LOAN, null, 30_000), -500_000, -500_000),     // no day
            AccountState(debt(5, AccountType.LOAN, 10, 0), -500_000, -500_000),            // no payment
            AccountState(debt(6, AccountType.CARD, 10, 30_000), -500_000, -500_000),       // not a debt
        )
        val got = Debts.obligations(states)
        assertEquals(listOf(-1L, -2L), got.map { it.id })
        assertEquals(listOf(30_000L, 20_000L), got.map { it.amountMinor })
        assertEquals(listOf(10, 5), got.map { it.dayOfMonth })
        assertEquals("d1", got.first().name)
    }

    @Test
    fun aYearsRateMakesAMonthsInterest() {
        assertEquals(10_000L, Debts.monthlyInterest(1_000_000, 12.0))
    }

    @Test
    fun theNextDueDayIsNeverInThePast() = prop {
        checkAll(Arb.int(1..31), Arb.long(0L..20_000L)) { day, offset ->
            val today = LocalDate.of(2020, 1, 1).plusDays(offset)
            val due = Budget.nextDue(day, today)
            assertFalse(due.isBefore(today))
            assertTrue(due.isBefore(today.plusMonths(1).plusDays(1)))
            // The day itself, or the month's last day when the month is shorter.
            assertEquals(minOf(day, due.lengthOfMonth()), due.dayOfMonth)
        }
    }

    // The envelope.
    private val card = Account(1, "Карта", "GEL", AccountType.CARD, includeInFree = true)
    private val cash = Account(2, "Наличные", "GEL", AccountType.CASH, includeInFree = true)
    private val loan = Account(3, "Кредит", "GEL", AccountType.LOAN, includeInFree = false)
    private val start = LocalDate.of(2026, 10, 15)
    private fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun op(id: Long, type: OpType, day: LocalDate, vararg p: Pair<Account, Long>) =
        OperationFull(Operation(id, type, at(day)), p.map { (a, m) -> Posting(id * 10 + a.id, id, a.id, m, 0) })

    @Test
    fun perDayIsWhatIsLeftOverTheDaysLeft() = prop {
        checkAll(Arb.long(0L..10_000_000L), Arb.int(0..30), Arb.int(0..30)) { topUp, daysIn, length ->
            val today = start.plusDays(daysIn.toLong())
            val end = today.plusDays(length.toLong())
            val ops = listOf(op(1, OpType.INCOME, start, card to topUp))
            val e = Envelopes.of(card, start, end, ops, today, zone)
            assertEquals(length + 1L, e.daysLeft)
            assertEquals(topUp / (length + 1), e.perDayMinor)
            assertEquals(e.perDayMinor, e.leftTodayMinor)
            assertNull(e.burnMinor)
            assertTrue(e.lasts(today))
        }
    }

    @Test
    fun spendingIsWhatLeftForGood() {
        val today = start.plusDays(4) // the fifth day
        val ops = listOf(
            op(1, OpType.INCOME, start, card to 100_000),
            op(2, OpType.EXPENSE, start, card to -5_000),
            op(3, OpType.TRANSFER, start.plusDays(1), card to -20_000, cash to 20_000), // to cash: not spent
            op(4, OpType.ADJUSTMENT, start.plusDays(2), card to -5_000),             // reconciled down: spent
            op(5, OpType.ADJUSTMENT, start.plusDays(2), card to 1_000),              // reconciled up: not spending
            op(6, OpType.EXPENSE, today, card to -2_000),
            op(7, OpType.EXPENSE, start.minusDays(1), card to -7_000),               // before the period
            op(8, OpType.EXPENSE, today.plusDays(1), card to -9_000),                // after today
        )
        val e = Envelopes.of(card, start, start.plusMonths(1), ops, today, zone)
        assertEquals(100_000L - 5_000 - 20_000 - 5_000 + 1_000 - 2_000 - 7_000 - 9_000, e.balanceMinor)
        assertEquals(12_000L, e.spentMinor)
        assertEquals(2_000L, e.spentTodayMinor)
        assertEquals(12_000L / 5, e.burnMinor)
        assertEquals(e.balanceMinor / (12_000L / 5), e.runwayDays)
        assertEquals(e.perDayMinor - 2_000, e.leftTodayMinor)
    }

    @Test
    fun itLastsExactlyWhenTheRunwayCoversTheDays() {
        val today = start.plusDays(9)
        // Spent 1 000 over 10 days, 100 a day; 1 000 left lasts 10 days, and 10 are left.
        val ops = listOf(op(1, OpType.INCOME, start, card to 2_000), op(2, OpType.EXPENSE, start, card to -1_000))
        val e = Envelopes.of(card, start, today.plusDays(9), ops, today, zone)
        assertEquals(10L, e.daysLeft)
        assertEquals(10L, e.runwayDays)
        assertTrue(e.lasts(today))
        val short = Envelopes.of(card, start, today.plusDays(10), ops, today, zone)
        assertFalse(short.lasts(today))
        assertEquals(today.plusDays(10), short.runsOut(today))
        assertEquals(19L, short.lastsDays(today))
    }

    @Test
    fun theNormIsTheMoneyAtAnEvenPace() {
        // 1 800 for 26 October to 26 November, 32 days; on the 10th 820 is gone where 562,50 would have.
        val today = start.plusDays(9)
        val ops = listOf(op(1, OpType.INCOME, start, card to 180_000), op(2, OpType.EXPENSE, today, card to -82_000))
        val e = Envelopes.of(card, start, start.plusDays(31), ops, today, zone)
        assertEquals(32L, e.totalDays)
        assertEquals(10L, e.elapsedDays)
        assertEquals(180_000L, e.fundsMinor)
        assertEquals(56_250L, e.normMinor)
        assertEquals(25_750L, e.overNormMinor)
        assertEquals(10f / 32, e.timeShare)
        assertEquals(82_000f / 180_000, e.spentShare)
        // Spent less than the norm: within it.
        val slow = Envelopes.of(card, start, start.plusDays(31), listOf(ops[0], op(2, OpType.EXPENSE, today, card to -44_000)), today, zone)
        assertEquals(44_000L - 56_250, slow.overNormMinor)
    }

    @Test
    fun theNormRunsFromADayToAllTheMoney() = prop {
        checkAll(Arb.long(0L..10_000_000L), Arb.long(0L..10_000_000L), Arb.int(0..40), Arb.int(0..40)) { topUp, spend, daysIn, after ->
            val today = start.plusDays(daysIn.toLong())
            val end = today.plusDays(after.toLong())
            val ops = listOf(op(1, OpType.INCOME, start, card to topUp), op(2, OpType.EXPENSE, today, card to -spend))
            val e = Envelopes.of(card, start, end, ops, today, zone)
            assertEquals(daysIn + after + 1L, e.totalDays)
            assertEquals(daysIn + 1L, e.elapsedDays)
            assertEquals(topUp, e.fundsMinor)
            assertEquals(spend, e.normMinor + e.overNormMinor)
            assertTrue(e.normMinor in 0..topUp)
            assertTrue(e.timeShare > 0f && e.timeShare <= 1f)
            assertTrue(e.spentShare in 0f..1f)
            // The last day the norm is all of it; the first, a day's worth.
            if (after == 0) assertEquals(topUp, e.normMinor)
            if (daysIn == 0) assertEquals(topUp / e.totalDays, e.normMinor)
            // Nothing on the card and something spent: the money is all gone.
            if (spend > 0 && topUp == 0L) assertEquals(1f, e.spentShare)
            if (spend == 0L) assertEquals(0f, e.spentShare)
        }
    }

    @Test
    fun theUsualLengths() {
        assertEquals(LocalDate.of(2026, 10, 22), Envelopes.end(start, Envelopes.Length.WEEK))
        assertEquals(LocalDate.of(2026, 10, 29), Envelopes.end(start, Envelopes.Length.TWO_WEEKS))
        assertEquals(LocalDate.of(2026, 11, 15), Envelopes.end(start, Envelopes.Length.MONTH))
        assertEquals(Envelopes.Length.MONTH, Envelopes.of(card, start, start.plusMonths(1), emptyList(), start, zone).length)
        assertNull(Envelopes.of(card, start, start.plusDays(10), emptyList(), start, zone).length)
    }

    @Test
    fun theCardIsTheOneSpentFromMost() {
        val ops = listOf(
            op(1, OpType.EXPENSE, start, cash to -100),
            op(2, OpType.EXPENSE, start, card to -100),
            op(3, OpType.EXPENSE, start, card to -100),
            op(4, OpType.EXPENSE, start, loan to -100),
            op(5, OpType.EXPENSE, start, loan to -100),
            op(6, OpType.EXPENSE, start, loan to -100),
            op(7, OpType.INCOME, start, cash to 100),
            op(8, OpType.INCOME, start, cash to 100),
        )
        val all = listOf(card, cash, loan)
        // Debts are not spent from, and income doesn't count.
        assertEquals(card, Envelopes.account(all, ops, Settings()))
        // The one set wins.
        assertEquals(cash, Envelopes.account(all, ops, Settings(spendAccountId = cash.id)))
        // Nothing spent yet: the last used account, else the first spendable one.
        assertEquals(cash, Envelopes.account(all, emptyList(), Settings(lastAccountId = cash.id)))
        assertEquals(card, Envelopes.account(all, emptyList(), Settings(lastAccountId = loan.id)))
        assertEquals(card, Envelopes.account(all, emptyList(), Settings(spendAccountId = 99)))
        assertNull(Envelopes.account(listOf(loan), emptyList(), Settings()))
    }

    @Test
    fun thePeriodIsTheSetOneWhileItLasts() {
        val today = start.plusDays(3)
        val ops = listOf(
            op(1, OpType.INCOME, start.plusDays(1), card to 1_000),
            op(2, OpType.TRANSFER, start.plusDays(2), cash to -500, card to 500),
            op(3, OpType.TRANSFER, start.plusDays(2), card to -100, cash to 100), // money leaving: not a top-up
            op(4, OpType.INCOME, today.plusDays(1), card to 1_000),               // tomorrow: not yet
        )
        assertEquals(start.plusDays(2), Envelopes.lastTopUp(card.id, ops, today, zone))
        assertEquals(start.plusDays(1), Envelopes.lastTopUp(card.id, ops, start.plusDays(1), zone))
        assertNull(Envelopes.lastTopUp(card.id, emptyList(), today, zone))
        // A set period that includes today, its first and last days included.
        val set = Settings(periodStart = today.toEpochDay(), periodEnd = today.toEpochDay())
        assertEquals(today to today, Envelopes.period(card.id, ops, set, today, zone))
        // Over: the months roll on from where it was set, whatever came in since.
        val over = Settings(periodStart = start.minusMonths(2).toEpochDay(), periodEnd = start.minusMonths(1).toEpochDay())
        val (from, to) = Envelopes.period(card.id, ops, over, today, zone)
        assertTrue(!from.isAfter(today) && !to.isBefore(today) && to == from.plusMonths(1))
        // Not started yet: the same.
        val future = Settings(periodStart = today.plusDays(1).toEpochDay(), periodEnd = today.plusDays(9).toEpochDay())
        assertEquals(start.plusDays(2), Envelopes.period(card.id, ops, future, today, zone).first)
        // A top-up long ago: the month ends today at the latest.
        val old = listOf(op(1, OpType.INCOME, start.minusMonths(3), card to 1_000))
        assertEquals(start.minusMonths(3) to today, Envelopes.period(card.id, old, Settings(), today, zone))
        // Nothing yet: a month from today.
        assertEquals(today to today.plusMonths(1), Envelopes.period(card.id, emptyList(), Settings(), today, zone))
    }

    @Test
    fun currentPutsTheCardAndPeriodTogether() {
        val ops = listOf(op(1, OpType.INCOME, start, card to 31_000))
        val e = Envelopes.current(listOf(card), ops, Settings(), start, zone)!!
        assertEquals(card, e.account)
        assertEquals(start, e.start)
        assertEquals(32L, e.daysLeft)
        assertEquals("GEL", e.currency)
        assertNull(Envelopes.current(emptyList(), ops, Settings(), start, zone))
    }

    @Test
    fun theMoneyHeldLessTheDebtsIsTheNetTotal() = prop {
        val types = AccountType.entries
        checkAll(Arb.list(Arb.pair(Arb.int(types.indices), Arb.long(-1_000_000_000L..1_000_000_000L)), 0..8)) { rows ->
            val states = rows.mapIndexed { i, (type, rub) ->
                AccountState(Account(i + 1L, "a$i", "RUB", types[type], includeInFree = false, sort = i), rub, rub)
            }
            val held = Debts.heldRub(states)
            val owed = Debts.owedRub(states)
            assertTrue(held >= 0 && owed >= 0)
            assertEquals(states.sumOf { it.rubMinor }, held - owed)
        }
    }

    @Test
    fun aCreditCardInThePlusIsMoneyHeldAndADebtTakesNothingOff() {
        fun state(id: Long, type: AccountType, rub: Long) = AccountState(Account(id, "a", "RUB", type, includeInFree = false, sort = 0), rub, rub)
        val states = listOf(state(1, AccountType.CREDIT, 700), state(2, AccountType.CARD, 300), state(3, AccountType.LOAN, -500_000_000))
        assertEquals(1000L, Debts.heldRub(states))
        assertEquals(500_000_000L, Debts.owedRub(states))
    }

    private val noRates = Rates(emptyMap(), 0.0)
    private val day = LocalDate.of(2026, 11, 5)

    private fun debt(id: Long, rate: Double?, type: AccountType = AccountType.LOAN, code: String = "RUB", grace: Long? = null) =
        Account(id, "d$id", code, type, includeInFree = false, interestRate = rate, graceUntil = grace)

    private fun owing(vararg debts: Pair<Account, Long>) = debts.associate { (a, owed) -> a.id to AccountState(a, -owed, -owed) }

    @Test
    fun theInterestIsEveryDebtsTogether() = prop {
        checkAll(Arb.long(100L..1_000_000_000L), Arb.long(100L..1_000_000_000L), Arb.double(1.0, 60.0), Arb.double(1.0, 60.0)) { a, b, x, y ->
            val one = debt(1, x)
            val two = debt(2, y)
            val both = Debts.interest(listOf(one, two), owing(one to a, two to b), day, noRates)
            val alone = listOfNotNull(Debts.interest(listOf(one), owing(one to a), day, noRates), Debts.interest(listOf(two), owing(two to b), day, noRates))
            assertEquals(alone.sumOf { it.rubMinor }, both?.rubMinor ?: 0)
        }
    }

    @Test
    fun aDebtLeftOutTakesNothingAndIsCounted() = prop {
        checkAll(Arb.long(10_000L..1_000_000_000L), Arb.long(1L..1_000_000_000L)) { owed, other ->
            val rated = debt(1, 24.0)
            val flat = debt(2, null)
            val foreign = debt(3, 12.0, code = "GEL")
            val all = Debts.interest(listOf(rated, flat, foreign), owing(rated to owed, flat to other, foreign to other), day, noRates)!!
            assertEquals(Debts.monthlyInterest(owed, 24.0), all.rubMinor)
            assertEquals(2, all.without)
        }
    }

    @Test
    fun aCardInItsGraceCostsNothingToItsLastDay() {
        val last = day.toEpochDay()
        val inGrace = debt(1, 30.0, AccountType.CREDIT, grace = last)
        val past = debt(1, 30.0, AccountType.CREDIT, grace = last - 1)
        assertNull(Debts.interest(listOf(inGrace), owing(inGrace to 1_000_000), day, noRates))
        assertEquals(25_000L, Debts.interest(listOf(past), owing(past to 1_000_000), day, noRates)!!.rubMinor)
    }

    @Test
    fun anotherCurrencyGoesAtTodaysRateAndTheCardInThePlusOwesNothing() {
        val dollars = debt(1, 12.0, code = "USD")
        val rates = Rates(mapOf("USD" to 100.0), 0.0)
        assertEquals(100_000L, Debts.interest(listOf(dollars), owing(dollars to 100_000), day, rates)!!.rubMinor)
        val plus = debt(2, 12.0, AccountType.CREDIT)
        assertNull(Debts.interest(listOf(plus), mapOf(2L to AccountState(plus, 500, 500)), day, noRates))
        val saved = Account(3, "s", "RUB", AccountType.SAVINGS, includeInFree = false, interestRate = 12.0)
        assertNull(Debts.interest(listOf(saved), owing(saved to 1_000_000), day, noRates))
        assertNull(Debts.interest(listOf(debt(4, 12.0)), emptyMap(), day, noRates))
    }

    @Test
    fun theMarginIsTheOtherSideOfTheOverage() = prop {
        checkAll(Arb.long(0L..1_000_000_000L), Arb.long(0L..1_000_000_000L), Arb.long(1L..60L)) { balance, spent, left ->
            val e = Envelope(debt(1, null), day, day.plusDays(60), balance, spent, 0, left, 0, 0, null, null)
            assertEquals(e.normMinor - e.spentMinor, e.marginMinor)
            assertEquals(-e.overNormMinor, e.marginMinor)
        }
    }

    private fun envelope(balance: Long, spent: Long) =
        Envelope(debt(1, null), day, day.plusDays(9), balance, spent, 0, 5, 0, 0, null, null)

    @Test
    fun theLightGoesByTheMoneyAgainstTheNorm() = prop {
        checkAll(Arb.long(0L..1_000_000_000L), Arb.long(0L..1_000_000_000L)) { balance, spent ->
            val e = envelope(balance, spent)
            val near = kotlin.math.abs(e.marginMinor) * 20 <= e.fundsMinor
            assertEquals(if (near) Light.YELLOW else if (e.marginMinor > 0) Light.GREEN else Light.RED, e.light)
        }
    }

    @Test
    fun theLightsEdges() {
        // Six days of ten gone, 600 of 1 000: on the norm.
        assertEquals(Light.YELLOW, envelope(40_000, 60_000).light)
        // 50 past the norm is 5 % of the money, still near it; 51 is not.
        assertEquals(Light.YELLOW, envelope(35_000, 65_000).light)
        assertEquals(Light.RED, envelope(34_000, 66_000).light)
        assertEquals(Light.GREEN, envelope(60_000, 40_000).light)
    }
}

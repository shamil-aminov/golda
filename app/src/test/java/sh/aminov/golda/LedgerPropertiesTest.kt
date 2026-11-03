package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
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
import sh.aminov.golda.domain.AccountState
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Rates

/**
 * Rules the books must keep for any amounts, not just the ones someone thought of: money is never
 * made or lost by moving it, and spending an account down to zero leaves no ruble cost behind.
 */
class LedgerPropertiesTest {
    private val rates = Rates(mapOf("USD" to 83.2454, "GEL" to 31.9597), markup = 0.10)
    private val rub = Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true)
    private val usd = Account(2, "Доллары", "USD", AccountType.CARD, includeInFree = true)
    private val gel = Account(3, "Лари", "GEL", AccountType.CASH, includeInFree = true)
    private val usd2 = Account(4, "Доллары 2", "USD", AccountType.CARD, includeInFree = true)
    private val accounts = listOf(rub, usd, gel, usd2)

    /** A tiny ledger: drafts in, postings kept. */
    private inner class Book {
        val postings = mutableListOf<Posting>()
        fun states() = Ledger.states(accounts, postings)
        fun state(a: Account): AccountState = states().getValue(a.id)
        fun book(d: Draft): List<Posting> = Ledger.postings(d, states(), rates).also { postings += it }
    }

    @Test
    fun aTransferInOneCurrencyMakesAndLosesNothing() = prop {
        checkAll(Arb.long(1L..10_000_000_000L), Arb.long(1L..10_000_000_000L)) { opening, amount ->
            val b = Book()
            b.book(Draft(OpType.OPENING, 0, usd.id, opening))
            val moved = b.book(Draft(OpType.TRANSFER, 1, usd.id, amount, toAccountId = usd2.id))
            assertEquals(0L, moved.sumOf { it.amountMinor })
            // The ruble cost moves with the money: what leaves one account arrives on the other.
            assertEquals(0L, moved.sumOf { it.rubMinor })
            assertEquals(opening, b.state(usd).balanceMinor + b.state(usd2).balanceMinor)
        }
    }

    @Test
    fun spendingAnAccountDownToZeroLeavesNoRubleCost() = prop {
        // Buy dollars a few times at different prices, then spend them in random pieces to the last cent.
        checkAll(
            Arb.list(Arb.long(1_000L..5_000_000L), 1..5),
            Arb.list(Arb.int(1..97), 1..8),
        ) { buys, cuts ->
            val b = Book()
            b.book(Draft(OpType.OPENING, 0, rub.id, 1_000_000_000_000))
            buys.forEachIndexed { i, cents ->
                val rubles = cents * (80 + i * 3) // a different price each time
                b.book(Draft(OpType.TRANSFER, 1L + i, rub.id, rubles, toAccountId = usd.id, toAmountMinor = cents))
            }
            for (cut in cuts) {
                val left = b.state(usd).balanceMinor
                if (left <= 1) break
                b.book(Draft(OpType.EXPENSE, 10, usd.id, (left * cut / 100).coerceIn(1, left - 1)))
            }
            b.book(Draft(OpType.EXPENSE, 20, usd.id, b.state(usd).balanceMinor))
            assertEquals(0L, b.state(usd).balanceMinor)
            assertEquals(0L, b.state(usd).rubMinor)
        }
    }

    @Test
    fun partOfABalanceLeavesAtTheAverageCost() = prop {
        checkAll(Arb.long(100L..1_000_000L), Arb.int(80..120), Arb.int(1..99)) { cents, price, percent ->
            val b = Book()
            b.book(Draft(OpType.OPENING, 0, rub.id, 1_000_000_000_000))
            b.book(Draft(OpType.TRANSFER, 1, rub.id, cents * price, toAccountId = usd.id, toAmountMinor = cents))
            val part = (cents * percent / 100).coerceAtLeast(1)
            val spent = b.book(Draft(OpType.EXPENSE, 2, usd.id, part)).single()
            // At the average cost, rounded to the kopeck: never at the official rate.
            assertEquals(-part * price, spent.rubMinor)
        }
    }

    @Test
    fun overspendingValuesTheRestAtTheDisplayRate() {
        val b = Book()
        b.book(Draft(OpType.OPENING, 0, rub.id, 10_000_000))
        b.book(Draft(OpType.TRANSFER, 1, rub.id, 9_000, toAccountId = usd.id, toAmountMinor = 100)) // 1 $ for 90 ₽
        // 3 $ spent with 1 $ there: the dollar there at its cost, the other two at the display rate.
        val spent = b.book(Draft(OpType.EXPENSE, 2, usd.id, 300)).single()
        assertEquals(-(9_000 + rates.rubMinor(200, "USD")!!), spent.rubMinor)
        // Already below zero: everything at the display rate.
        val more = b.book(Draft(OpType.EXPENSE, 3, usd.id, 100)).single()
        assertEquals(-rates.rubMinor(100, "USD")!!, more.rubMinor)
    }

    @Test
    fun takingExactlyTheBalanceUsesOnlyItsCost() {
        val state = AccountState(usd, balanceMinor = 1_000, rubMinor = 95_000)
        assertEquals(95_000L, Ledger.outflowRub(state, 1_000, rates))
        // One cent more: the extra cent is valued at the display rate.
        assertEquals(95_000L + rates.rubMinor(1, "USD")!!, Ledger.outflowRub(state, 1_001, rates))
        // An empty account has no cost to spend: all of it at the display rate.
        assertEquals(rates.rubMinor(500, "USD"), Ledger.outflowRub(AccountState(usd, 0, 0), 500, rates))
    }

    @Test
    fun rublesAreAlwaysWorthThemselves() = prop {
        checkAll(Arb.long(1L..1_000_000_000_000L)) { amount ->
            val b = Book()
            assertEquals(amount, b.book(Draft(OpType.INCOME, 0, rub.id, amount)).single().rubMinor)
            assertEquals(-amount, b.book(Draft(OpType.EXPENSE, 1, rub.id, amount)).single().rubMinor)
            assertEquals(amount, Ledger.inflowRub(rub, amount, rates))
        }
    }

    @Test
    fun foreignIncomeIsValuedAtTheDisplayRate() = prop {
        checkAll(Arb.long(1L..100_000_000L)) { cents ->
            val got = Book().book(Draft(OpType.INCOME, 0, gel.id, cents)).single()
            assertEquals(cents, got.amountMinor)
            assertEquals(rates.rubMinor(cents, "GEL"), got.rubMinor)
        }
    }

    @Test
    fun aDownwardReconcilingCostsWhatSpendingTheSameWould() = prop {
        checkAll(Arb.long(1_000L..1_000_000L), Arb.long(1L..999L)) { cents, down ->
            val a = Book(); val e = Book()
            for (b in listOf(a, e)) {
                b.book(Draft(OpType.OPENING, 0, rub.id, 1_000_000_000))
                b.book(Draft(OpType.TRANSFER, 1, rub.id, cents * 91, toAccountId = usd.id, toAmountMinor = cents))
            }
            val adjusted = a.book(Draft(OpType.ADJUSTMENT, 2, usd.id, -down)).single()
            val spent = e.book(Draft(OpType.EXPENSE, 2, usd.id, down)).single()
            assertEquals(spent.amountMinor, adjusted.amountMinor)
            assertEquals(spent.rubMinor, adjusted.rubMinor)
            // Upward, it is new money at the display rate.
            assertEquals(rates.rubMinor(down, "USD"), a.book(Draft(OpType.ADJUSTMENT, 3, usd.id, down)).single().rubMinor)
        }
    }

    @Test
    fun rublesArrivingAreWorthWhatArrived() = prop {
        checkAll(Arb.long(100L..1_000_000L), Arb.long(1_000L..100_000_000L)) { cents, rubles ->
            val b = Book()
            b.book(Draft(OpType.OPENING, 0, usd.id, cents))
            val moved = b.book(Draft(OpType.TRANSFER, 1, usd.id, cents, toAccountId = rub.id, toAmountMinor = rubles))
            assertEquals(rubles, moved.last().rubMinor)
            assertEquals(rubles, moved.last().amountMinor)
        }
    }

    @Test
    fun costBasisNeedsMoneyToAverage() {
        assertEquals(1.0, AccountState(rub, 0, 0).costBasis!!, 0.0)
        assertNull(AccountState(usd, 0, 0).costBasis)
        assertNull(AccountState(usd, -100, -9_000).costBasis)
        assertEquals(90.0, AccountState(usd, 1, 90).costBasis!!, 1e-9)
    }

    @Test
    fun theMarkupATransferRevealsStaysInItsRange() {
        val states = Ledger.states(accounts, emptyList())
        // A round CBR rate, so the ruble amounts below are exact: 100 ₽ for a dollar.
        val round = Rates(mapOf("USD" to 100.0, "GEL" to 30.0), markup = 0.0)
        fun markupFor(rubles: Long, cents: Long, r: Rates = round) =
            Ledger.learnedMarkup(Draft(OpType.TRANSFER, 0, rub.id, rubles, toAccountId = usd.id, toAmountMinor = cents), states, r)
        // 110 ₽ a dollar at a CBR rate of 100 is 10 % over it.
        assertEquals(0.10, markupFor(110_00, 1_00)!!, 1e-9)
        // Just inside the believable range is kept; just beyond it is a typo, not a markup.
        assertEquals(-0.19, markupFor(81_00, 1_00)!!, 1e-9)
        assertEquals(0.59, markupFor(159_00, 1_00)!!, 1e-9)
        assertNull(markupFor(79_00, 1_00))
        assertNull(markupFor(161_00, 1_00))
        // Nothing arrived, an estimate, or not rubles to a currency: nothing to learn.
        assertNull(markupFor(10_000, 0))
        assertNull(Ledger.learnedMarkup(Draft(OpType.TRANSFER, 0, rub.id, 10_000, toAccountId = usd.id, toAmountMinor = 100, isEstimate = true), states, rates))
        assertNull(Ledger.learnedMarkup(Draft(OpType.TRANSFER, 0, usd.id, 100, toAccountId = gel.id, toAmountMinor = 300), states, rates))
        assertNull(Ledger.learnedMarkup(Draft(OpType.EXPENSE, 0, rub.id, 100), states, rates))
        assertNull(Ledger.learnedMarkup(Draft(OpType.TRANSFER, 0, rub.id, 100, toAccountId = usd.id), states, rates))
    }

    @Test
    fun conversionsGoBothWaysWithoutDrift() = prop {
        checkAll(Arb.long(1L..1_000_000_000L)) { cents ->
            val lari = rates.convert(cents, "USD", "GEL")!!
            val back = rates.convert(lari, "GEL", "USD")!!
            assertTrue("$cents → $lari → $back", kotlin.math.abs(back - cents) <= 1)
            assertTrue(lari > 0 || cents < 10)
        }
        assertNull(rates.convert(100, "XYZ", "USD"))
        assertNull(rates.convert(100, "USD", "XYZ"))
        assertNull(rates.cardCharge(100, "XYZ", "USD"))
        assertNull(rates.cardCharge(100, "USD", "XYZ"))
        // A card charge is the official cross rate plus the card's 2 %.
        assertEquals(Math.round(10_000 * 83.2454 / 31.9597 * 1.02), rates.cardCharge(10_000, "USD", "GEL"))
    }

    @Test
    fun booksSumToTheBalances() = prop {
        checkAll(Arb.list(Arb.long(-1_000_000L..1_000_000L), 0..20)) { moves ->
            val b = Book()
            b.book(Draft(OpType.OPENING, 0, gel.id, 50_000_000))
            moves.forEachIndexed { i, m ->
                when {
                    m > 0 -> b.book(Draft(OpType.INCOME, i + 1L, gel.id, m))
                    m < 0 -> b.book(Draft(OpType.EXPENSE, i + 1L, gel.id, -m))
                }
            }
            assertEquals(50_000_000 + moves.sum(), b.state(gel).balanceMinor)
        }
    }

    @Test
    fun anOperationsPostingsAreWhatTheStatesSum() {
        val ops = listOf(
            OperationFull(Operation(1, OpType.OPENING, 0), listOf(Posting(1, 1, usd.id, 500, 45_000))),
            OperationFull(Operation(2, OpType.EXPENSE, 1), listOf(Posting(2, 2, usd.id, -200, -18_000))),
        )
        val s = Ledger.states(accounts, ops.flatMap { it.postings })
        assertEquals(300L, s.getValue(usd.id).balanceMinor)
        assertEquals(27_000L, s.getValue(usd.id).rubMinor)
        assertEquals(0L, s.getValue(gel.id).balanceMinor)
    }
}

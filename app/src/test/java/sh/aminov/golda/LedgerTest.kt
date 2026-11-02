package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Rates
import java.time.LocalDate
import java.time.ZoneOffset

class LedgerTest {
    private val rates = Rates(mapOf("USD" to 83.2454, "GEL" to 31.9597, "THB" to 2.47438), markup = 0.10)
    private val rubCard = Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true)
    private val multiUsd = Account(2, "Мультивалютная USD", "USD", AccountType.CARD, includeInFree = true)
    private val cash = Account(3, "Наличные ₾", "GEL", AccountType.CASH, includeInFree = true)
    private val accounts = listOf(rubCard, multiUsd, cash)
    private val ops = mutableListOf<OperationFull>()
    private val zone = ZoneOffset.UTC

    private fun book(draft: Draft): List<Posting> {
        val states = Ledger.states(accounts, ops.flatMap { it.postings })
        val postings = Ledger.postings(draft, states, rates)
        val id = ops.size + 1L
        ops += OperationFull(Operation(id, draft.type, draft.timestamp), postings.map { it.copy(operationId = id) })
        return postings
    }

    private fun state(account: Account) = Ledger.states(accounts, ops.flatMap { it.postings }).getValue(account.id)

    @Test
    fun transferCarriesRubleCostAndAverages() {
        book(Draft(OpType.OPENING, 0, rubCard.id, 10_000_000))
        book(Draft(OpType.TRANSFER, 1, rubCard.id, 900_000, toAccountId = multiUsd.id, toAmountMinor = 10_000)) // 100 $ for 9 000 ₽
        book(Draft(OpType.TRANSFER, 2, rubCard.id, 4_600_000, toAccountId = multiUsd.id, toAmountMinor = 50_000)) // 500 $ for 46 000 ₽
        assertEquals(60_000, state(multiUsd).balanceMinor)
        assertEquals(91.666, state(multiUsd).costBasis!!, 0.001)
        assertEquals(4_500_000, state(rubCard).balanceMinor)
    }

    @Test
    fun spendingUsesAverageCostNotOfficialRate() {
        book(Draft(OpType.OPENING, 0, rubCard.id, 4_600_000))
        book(Draft(OpType.TRANSFER, 1, rubCard.id, 4_600_000, toAccountId = multiUsd.id, toAmountMinor = 50_000))
        // 80 ฿ coffee charged as 2.41 $: 2.41 × 92 = 221.72 ₽, not 2.41 × 83.25.
        val coffee = book(Draft(OpType.EXPENSE, 2, multiUsd.id, 241))
        assertEquals(-22_172, coffee.single().rubMinor)
    }

    @Test
    fun spendingTheWholeBalanceLeavesNoRubleDust() {
        book(Draft(OpType.OPENING, 0, rubCard.id, 1_000_000))
        book(Draft(OpType.TRANSFER, 1, rubCard.id, 1_000_000, toAccountId = multiUsd.id, toAmountMinor = 10_900))
        book(Draft(OpType.EXPENSE, 2, multiUsd.id, 3_333))
        book(Draft(OpType.EXPENSE, 3, multiUsd.id, 7_567))
        assertEquals(0, state(multiUsd).balanceMinor)
        assertEquals(0, state(multiUsd).rubMinor)
    }

    @Test
    fun atmWithdrawalMovesCostIntoCash() {
        book(Draft(OpType.OPENING, 0, rubCard.id, 4_600_000))
        book(Draft(OpType.TRANSFER, 1, rubCard.id, 4_600_000, toAccountId = multiUsd.id, toAmountMinor = 50_000))
        book(Draft(OpType.TRANSFER, 2, multiUsd.id, 10_000, toAccountId = cash.id, toAmountMinor = 26_500))
        // 100 $ at 92 ₽ became 265 ₾, so a lari cost 34.72 ₽.
        assertEquals(34.716, state(cash).costBasis!!, 0.001)
    }

    @Test
    fun markupIsLearnedFromRubleTransfers() {
        val states = Ledger.states(accounts, emptyList())
        val draft = Draft(OpType.TRANSFER, 0, rubCard.id, 4_600_000, toAccountId = multiUsd.id, toAmountMinor = 50_000)
        assertEquals(0.1051, Ledger.learnedMarkup(draft, states, rates)!!, 0.0001)
        assertNull(Ledger.learnedMarkup(draft.copy(type = OpType.EXPENSE), states, rates))
        // An amount the app guessed itself says nothing about the real rate.
        assertNull(Ledger.learnedMarkup(draft.copy(isEstimate = true), states, rates))
    }

    @Test
    fun displayRatesCarryThePersonalMarkup() {
        // 20 ₾ at the CBR rate plus a 10 % markup.
        assertEquals(703.1, rates.rubMinor(2_000, "GEL")!! / 100.0, 0.1)
        assertEquals(7.68, rates.convert(2_000, "GEL", "USD")!! / 100.0, 0.01)
        assertEquals(258.3, rates.convert(2_000, "GEL", "THB")!! / 100.0, 0.1)
    }

    @Test
    fun cardChargeUsesOfficialCrossPlusCardMarkup() {
        // 80 ฿ from a USD card: 80 × 2.47438 / 83.2454 × 1.02 = 2.43 $
        assertEquals(243L, rates.cardCharge(8_000, "THB", "USD"))
    }

    @Test
    fun savingsInterestUsesMonthlyMinimum() {
        val savings = Account(4, "Накопительный", "RUB", AccountType.SAVINGS, includeInFree = false, interestRate = 12.0)
        val today = LocalDate.of(2026, 10, 20)
        fun at(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay().toInstant(zone).toEpochMilli()
        val history = listOf(
            OperationFull(Operation(1, OpType.OPENING, at(2)), listOf(Posting(1, 1, 4, 15_000_000, 15_000_000))),
            OperationFull(Operation(2, OpType.TRANSFER, at(10)), listOf(Posting(2, 2, 4, -5_000_000, -5_000_000))),
            OperationFull(Operation(3, OpType.TRANSFER, at(12)), listOf(Posting(3, 3, 4, 2_000_000, 2_000_000))),
        )
        // Minimum is 100 000 ₽: × 12 % × 31 / 365 = 1 019.18 ₽.
        assertEquals(101_918L, Budget.interestForecast(savings, history, today, zone))
    }

    @Test
    fun parsingAndFormatting() {
        assertEquals(1_550L, Fmt.parseMinor("15,5", "GEL"))
        assertEquals(150_025L, Fmt.parseMinor("1 500.25", "RUB"))
        assertNull(Fmt.parseMinor("abc", "RUB"))
        assertEquals("15 ₾", Fmt.amount(1_500, "GEL"))
        assertEquals("4 210,50 ₽", Fmt.amount(421_050, "RUB"))
        assertEquals("5,5 $", Fmt.approx(5.5, "USD"))
        assertEquals("181 ฿", Fmt.approx(181.4, "THB"))
        assertEquals("15", Fmt.editable(1_500, "GEL"))
    }
}

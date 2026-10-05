package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Ledger

class DebtsTest {
    // 300 000 ₽ at 24.9 % paid 10 000 ₽ a month.
    private val balance = 30_000_000L
    private val rate = 24.9
    private val payment = 1_000_000L

    @Test
    fun monthsLeftOnAnAnnuity() {
        val n = Debts.monthsLeft(balance, rate, payment)!!
        assertEquals(47.4, n, 0.1)
        assertNull(Debts.monthsLeft(balance, rate, 500_000)) // 5 000 ₽ does not even cover the interest
        assertEquals(30.0, Debts.monthsLeft(balance, 0.0, payment)!!, 1e-9)
    }

    @Test
    fun prepayingShortensTheTermOrLowersThePayment() {
        val p = Debts.prepay(balance, rate, payment, 5_000_000, savingsPercent = 12.0)!!
        assertEquals(48, p.monthsNow)
        assertEquals(36, p.monthsAfter)
        // Keeping the payment saves more interest than lowering it.
        assertTrue(p.savedByTermMinor > p.savedByPaymentMinor)
        assertTrue(p.savedByPaymentMinor > 0)
        assertTrue(p.paymentAfterMinor in 800_000..850_000)
        // 50 000 ₽ at 12 % for 48 months earns less than the loan's interest saved.
        assertTrue(p.savingsWouldEarnMinor!! < p.savedByTermMinor)
    }

    @Test
    fun interestLeftIsWhatIsPaidOnTopOfTheDebt() {
        val interest = Debts.interestLeft(balance, rate, payment)!!
        assertEquals(1_000_000 * Debts.monthsLeft(balance, rate, payment)!! - balance, interest.toDouble(), 1.0)
    }

    @Test
    fun debtPaymentsBecomeObligations() {
        val loan = Account(1, "Кредит", "RUB", AccountType.LOAN, includeInFree = false, interestRate = 24.9, paymentDay = 10, paymentMinor = 750_000)
        val card = Account(2, "Карта", "RUB", AccountType.CARD, includeInFree = true, paymentDay = 5, paymentMinor = 1)
        val owed = Ledger.states(listOf(loan, card), listOf(Posting(accountId = 1, amountMinor = -balance, rubMinor = -balance)))
        val obligations = Debts.obligations(owed.values)
        assertEquals(1, obligations.size)
        assertEquals(750_000L, obligations.single().amountMinor)
        assertEquals(10, obligations.single().dayOfMonth)
    }

    @Test
    fun aPaidOffDebtReservesNothingAndNeverMoreThanIsOwed() {
        val card = Account(1, "Кредитка", "RUB", AccountType.CREDIT, includeInFree = false, paymentDay = 20, paymentMinor = 300_000)
        // Nothing owed: nothing set aside.
        assertTrue(Debts.obligations(Ledger.states(listOf(card), emptyList()).values).isEmpty())
        // Owing 1 000 ₽ of a 3 000 ₽ minimum: 1 000 ₽ set aside.
        val little = Ledger.states(listOf(card), listOf(Posting(accountId = 1, amountMinor = -100_000, rubMinor = -100_000)))
        assertEquals(100_000L, Debts.obligations(little.values).single().amountMinor)
    }

    @Test
    fun adviceComparesWithSavings() {
        val loan = Account(1, "Кредит", "RUB", AccountType.LOAN, includeInFree = false, interestRate = 24.9)
        val savings = Account(2, "Накопительный", "RUB", AccountType.SAVINGS, includeInFree = false, interestRate = 12.0)
        val states = Ledger.states(listOf(loan, savings), listOf(Posting(accountId = 1, amountMinor = -balance, rubMinor = -balance)))
        assertTrue(Debts.advice(listOf(loan, savings), states)!!.contains("дороже"))
        // Nothing owed, nothing to advise.
        assertNull(Debts.advice(listOf(loan, savings), Ledger.states(listOf(loan, savings), emptyList())))
    }
}

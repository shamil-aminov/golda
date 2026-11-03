package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Obligation
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/** What paying [extraMinor] early does to an annuity loan, both ways banks offer it. */
data class Prepay(
    val extraMinor: Long,
    val monthsNow: Int,
    /** Keep the payment, finish sooner. */
    val monthsAfter: Int,
    val savedByTermMinor: Long,
    /** Keep the term, pay less each month. */
    val paymentAfterMinor: Long,
    val savedByPaymentMinor: Long,
    /** What the same money would earn on a savings account over the remaining term, if one is given. */
    val savingsWouldEarnMinor: Long?,
)

object Debts {
    val Account.isDebt get() = type == AccountType.CREDIT || type == AccountType.LOAN

    /** Months left on an annuity: n = −ln(1 − rB/P) / ln(1 + r). Null when the payment does not even cover the interest. */
    fun monthsLeft(balanceMinor: Long, ratePercent: Double, paymentMinor: Long): Double? {
        if (balanceMinor <= 0) return 0.0
        if (paymentMinor <= 0) return null
        val r = ratePercent / 100 / 12
        if (r == 0.0) return balanceMinor.toDouble() / paymentMinor
        val k = 1 - r * balanceMinor / paymentMinor
        if (k <= 0) return null
        return -ln(k) / ln(1 + r)
    }

    /** Interest still to be paid: everything paid minus what is owed. */
    fun interestLeft(balanceMinor: Long, ratePercent: Double, paymentMinor: Long): Long? {
        val n = monthsLeft(balanceMinor, ratePercent, paymentMinor) ?: return null
        return (paymentMinor * n - balanceMinor).toLong().coerceAtLeast(0)
    }

    fun prepay(balanceMinor: Long, ratePercent: Double, paymentMinor: Long, extraMinor: Long, savingsPercent: Double?): Prepay? {
        val n = monthsLeft(balanceMinor, ratePercent, paymentMinor) ?: return null
        val extra = extraMinor.coerceAtMost(balanceMinor)
        val rest = balanceMinor - extra
        val nAfter = monthsLeft(rest, ratePercent, paymentMinor) ?: return null
        val r = ratePercent / 100 / 12
        val total = paymentMinor * n - balanceMinor
        val byTerm = total - (paymentMinor * nAfter - rest)
        val paymentAfter = when {
            rest <= 0 -> 0.0
            r == 0.0 -> rest / n
            else -> rest * r / (1 - (1 + r).pow(-n))
        }
        val byPayment = total - (paymentAfter * n - rest)
        val savings = savingsPercent?.let { s -> extra * ((1 + s / 100 / 12).pow(n) - 1) }
        return Prepay(
            extraMinor = extra,
            monthsNow = ceil(n).toInt(),
            monthsAfter = ceil(nAfter).toInt(),
            savedByTermMinor = byTerm.toLong().coerceAtLeast(0),
            paymentAfterMinor = paymentAfter.toLong(),
            savedByPaymentMinor = byPayment.toLong().coerceAtLeast(0),
            savingsWouldEarnMinor = savings?.toLong(),
        )
    }

    /**
     * Monthly debt payments set aside before payday like any other obligation: only while something
     * is owed, and never more than is owed. The id is the account's, negated.
     */
    fun obligations(states: Collection<AccountState>): List<Obligation> = states
        .filter { s -> s.account.isDebt && s.account.paymentDay != null && (s.account.paymentMinor ?: 0) > 0 && s.balanceMinor < 0 }
        .map { s ->
            val a = s.account
            Obligation(id = -a.id, name = a.name, amountMinor = minOf(a.paymentMinor!!, -s.balanceMinor), currency = a.currency, dayOfMonth = a.paymentDay!!)
        }

    /**
     * Which debt to pay off first: the most expensive one, and whether paying
     * it beats keeping the money on the best savings account.
     */
    /**
     * One line under the total: what the dearest debt's interest eats this month, on what is owed now.
     * It is the interest part of the payment, so it shrinks with every early repayment. Rounded to
     * hundreds: it is a feel, not a statement.
     */
    fun advice(accounts: List<Account>, states: Map<Long, AccountState>): String? {
        val top = accounts.filter { it.isDebt && it.interestRate != null && (states[it.id]?.balanceMinor ?: 0) < 0 }
            .maxByOrNull { it.interestRate!! } ?: return null
        val owed = -states.getValue(top.id).balanceMinor
        val interest = monthlyInterest(owed, top.interestRate!!).let { if (it >= 100_000) (it + 5_000) / 10_000 * 10_000 else it }
        val what = when (top.type) {
            AccountType.CREDIT -> tr("по кредитке", "on the credit card")
            else -> tr("по кредиту", "on the loan")
        }
        return tr("Проценты $what ≈ ${Fmt.amount(interest, top.currency)} в месяц", "Interest $what ≈ ${Fmt.amount(interest, top.currency)} a month")
    }

    /** A month's interest on [owedMinor] at a yearly [ratePercent]. */
    fun monthlyInterest(owedMinor: Long, ratePercent: Double): Long = (owedMinor * ratePercent / 100 / 12).roundToLong()
}

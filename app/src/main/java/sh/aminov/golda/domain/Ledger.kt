package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Official rates plus the personal markup. "Display" rates are what money in
 * that currency really costs you, and every multi-currency line uses them.
 */
class Rates(private val rubPerUnit: Map<String, Double>, val markup: Double) {
    fun official(code: String): Double? = if (code == "RUB") 1.0 else rubPerUnit[code]

    /** Every currency there is a rate for, rubles included. */
    val codes: Set<String> get() = rubPerUnit.keys + "RUB"

    fun display(code: String): Double? = if (code == "RUB") 1.0 else official(code)?.times(1 + markup)

    /** Kopecks that [minor] of [code] are worth at the display rate. */
    fun rubMinor(minor: Long, code: String): Long? =
        display(code)?.let { (Currencies.toMajor(minor, code) * it * 100).roundToLong() }

    /** [rubMinor] kopecks expressed in [code], major units. */
    fun fromRub(rubMinor: Long, code: String): Double? = display(code)?.let { rubMinor / 100.0 / it }

    /** Same money in another currency at display rates (cross rates keep the markup out). */
    fun convert(minor: Long, from: String, to: String): Long? {
        val a = display(from) ?: return null
        val b = display(to) ?: return null
        return Currencies.toMinor(Currencies.toMajor(minor, from) * a / b, to)
    }

    /** What a card in [account] currency is charged for a purchase in [purchase] currency. */
    fun cardCharge(minor: Long, purchase: String, account: String, cardMarkup: Double = CARD_MARKUP): Long? {
        val a = official(purchase) ?: return null
        val b = official(account) ?: return null
        return Currencies.toMinor(Currencies.toMajor(minor, purchase) * a / b * (1 + cardMarkup), account)
    }

    companion object {
        /** Visa/Mastercard conversion plus the bank's cut, until we learn better. */
        const val CARD_MARKUP = 0.02
    }
}

data class AccountState(val account: Account, val balanceMinor: Long, val rubMinor: Long) {
    val currency get() = account.currency

    /** Rubles one unit on this account cost on average; null when there is nothing to average. */
    val costBasis: Double?
        get() = when {
            currency == "RUB" -> 1.0
            balanceMinor > 0 -> rubMinor / 100.0 / Currencies.toMajor(balanceMinor, currency)
            else -> null
        }
}

/** Everything a new operation needs; [amountMinor] is in the (from) account's currency. */
data class Draft(
    val type: OpType,
    val timestamp: Long,
    val accountId: Long,
    /** Positive for expense, income and transfer; signed for adjustment and opening. */
    val amountMinor: Long,
    val toAccountId: Long? = null,
    val toAmountMinor: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val purchaseAmountMinor: Long? = null,
    val purchaseCurrency: String? = null,
    val isEstimate: Boolean = false,
    val voiceText: String? = null,
    /** An expense's own bucket, when it is not its category's. */
    val bucket: Bucket? = null,
    val id: Long = 0,
)

object Ledger {
    fun states(accounts: List<Account>, postings: List<Posting>): Map<Long, AccountState> {
        val byAccount = postings.groupBy { it.accountId }
        return accounts.associate { account ->
            val own = byAccount[account.id].orEmpty()
            account.id to AccountState(account, own.sumOf { it.amountMinor }, own.sumOf { it.rubMinor })
        }
    }

    /** Ruble value of money leaving [state]: at its average cost while it lasts, then at the display rate. */
    fun outflowRub(state: AccountState, minor: Long, rates: Rates): Long {
        if (state.currency == "RUB") return minor
        val balance = state.balanceMinor
        val covered = if (balance > 0) minOf(minor, balance) else 0
        val fromBasis = if (covered > 0) (state.rubMinor.toDouble() * covered / balance).roundToLong() else 0
        val rest = minor - covered
        return fromBasis + if (rest > 0) rates.rubMinor(rest, state.currency) ?: 0 else 0
    }

    fun inflowRub(account: Account, minor: Long, rates: Rates): Long =
        if (account.currency == "RUB") minor else rates.rubMinor(minor, account.currency) ?: 0

    /** Turns a draft into postings, valuing each side in rubles against the current balances. */
    fun postings(draft: Draft, states: Map<Long, AccountState>, rates: Rates): List<Posting> {
        val from = states.getValue(draft.accountId)
        val amount = draft.amountMinor
        return when (draft.type) {
            OpType.EXPENSE -> listOf(Posting(accountId = from.account.id, amountMinor = -amount, rubMinor = -outflowRub(from, amount, rates)))
            OpType.INCOME -> listOf(Posting(accountId = from.account.id, amountMinor = amount, rubMinor = inflowRub(from.account, amount, rates)))
            OpType.ADJUSTMENT, OpType.OPENING -> {
                val rub = if (amount >= 0) inflowRub(from.account, amount, rates) else -outflowRub(from, -amount, rates)
                listOf(Posting(accountId = from.account.id, amountMinor = amount, rubMinor = rub))
            }
            OpType.TRANSFER -> {
                val to = states.getValue(requireNotNull(draft.toAccountId))
                val received = draft.toAmountMinor ?: amount
                val paid = outflowRub(from, amount, rates)
                // Rubles that arrive are worth exactly what they are; anything else carries the cost of what was paid.
                val carried = if (to.currency == "RUB") received else paid
                listOf(
                    Posting(accountId = from.account.id, amountMinor = -amount, rubMinor = -paid),
                    Posting(accountId = to.account.id, amountMinor = received, rubMinor = carried),
                )
            }
        }
    }

    /**
     * The markup a ruble-to-currency transfer reveals: 46 000 ₽ for 500 $ at a
     * CBR rate of 83.25 means +10.5 %. Null when the transfer says nothing about it.
     */
    fun learnedMarkup(draft: Draft, states: Map<Long, AccountState>, rates: Rates): Double? {
        if (draft.type != OpType.TRANSFER || draft.isEstimate) return null
        val from = states[draft.accountId] ?: return null
        val to = states[draft.toAccountId ?: return null] ?: return null
        if (from.currency != "RUB" || to.currency == "RUB") return null
        val received = draft.toAmountMinor ?: return null
        if (received <= 0) return null
        val official = rates.official(to.currency) ?: return null
        val paid = draft.amountMinor / 100.0 / Currencies.toMajor(received, to.currency)
        return (paid / official - 1).takeIf { it in -0.2..0.6 }
    }

    fun localDate(timestamp: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}

object Buckets {
    /** Where each built-in category lives (SPEC §5.3); one of your own is everyday unless set. */
    fun default(key: String?): Bucket = when (key) {
        "housing", "telecom", "subscriptions", "loans", "home" -> Bucket.REGULAR
        "shopping", "travel", "health", "other", "clothes" -> Bucket.BIG
        else -> Bucket.DAILY
    }

    fun of(category: Category?): Bucket = category?.bucket ?: default(category?.key)

    fun of(op: Operation, categories: Map<Long, Category>): Bucket = op.bucket ?: of(op.categoryId?.let { categories[it] })
}

object Budget {
    /** The next time a monthly payment on [day] comes, today included. */
    fun nextDue(day: Int, today: LocalDate): LocalDate {
        fun inMonth(month: YearMonth) = month.atDay(day.coerceIn(1, month.lengthOfMonth()))
        val thisMonth = inMonth(YearMonth.from(today))
        return if (thisMonth.isBefore(today)) inMonth(YearMonth.from(today).plusMonths(1)) else thisMonth
    }

    /**
     * This month's interest on a savings account that pays on the monthly
     * minimum. Opening balances count as if they were there since the 1st.
     */
    fun interestForecast(
        account: Account,
        operations: List<OperationFull>,
        today: LocalDate,
        zone: ZoneId,
    ): Long? {
        val rate = account.interestRate ?: return null
        val monthStart = today.withDayOfMonth(1)
        val moves = operations
            .flatMap { full -> full.postings.filter { it.accountId == account.id }.map { full.op to it } }
            .sortedBy { it.first.timestamp }
        var balance = 0L
        var minimum: Long? = null
        for ((op, posting) in moves) {
            val before = op.type == OpType.OPENING || Ledger.localDate(op.timestamp, zone) < monthStart
            balance += posting.amountMinor
            if (!before) minimum = minOf(minimum ?: (balance - posting.amountMinor), balance)
        }
        val base = minOf(minimum ?: balance, balance).coerceAtLeast(0)
        return (base * rate / 100 * today.lengthOfMonth() / 365).roundToLong()
    }
}

/**
 * Deleting an account must not move money on the accounts that stay. Its own operations go with it;
 * a transfer to or from another account stays there as an adjustment of that account, so the
 * 40 793 ₽ that left the savings for a closed credit card stays gone from the savings.
 */
object AccountRemoval {
    /** What stays of an operation: the same operation, now an adjustment, with the other accounts' postings. */
    data class Kept(val op: Operation, val postings: List<Posting>)

    data class Plan(val delete: List<Long>, val keep: List<Kept>)

    fun plan(account: Account, operations: List<OperationFull>): Plan {
        val touching = operations.filter { full -> full.postings.any { it.accountId == account.id } }
        val (shared, own) = touching.partition { full -> full.postings.any { it.accountId != account.id } }
        val keep = shared.map { full ->
            val rest = full.postings.filter { it.accountId != account.id }
            // "Перевод на «Кредитка»": the list says where the money went once the account is gone.
            val out = rest.sumOf { it.amountMinor } < 0
            val note = full.op.note.ifBlank {
                if (out) tr("Перевод на «${account.name}»", "Transfer to “${account.name}”") else tr("Перевод с «${account.name}»", "Transfer from “${account.name}”")
            }
            Kept(full.op.copy(type = OpType.ADJUSTMENT, categoryId = null, note = note), rest)
        }
        return Plan(own.map { it.op.id }, keep)
    }
}

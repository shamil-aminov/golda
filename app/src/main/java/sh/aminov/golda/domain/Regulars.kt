package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.Obligation
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Regular payments (subscriptions, the phone, a server): each an [Obligation] with its day of the
 * month. Two days before it is charged a reminder asks whether it is still needed; an amount of 0
 * is a free trial, and the reminder says when it ends.
 */
object Regulars {
    /** How many days before the charge the reminder comes. */
    const val AHEAD = 2L

    /** How far back the "regular" spending is looked through for suggestions. */
    private const val LOOKBACK_DAYS = 62L

    /**
     * Regular spending of the last two months that is not in the list yet, the latest of each
     * (by note, or by category when there is none), as payments ready to add. Loans are left out:
     * debts have their own reminders.
     */
    fun suggest(
        operations: List<OperationFull>,
        accounts: Map<Long, Account>,
        categories: Map<Long, Category>,
        known: List<Obligation>,
        today: LocalDate,
        zone: ZoneId,
    ): List<Obligation> {
        val since = today.minusDays(LOOKBACK_DAYS)
        val names = known.map { it.name.trim().lowercase() }.toSet()
        return operations
            .asSequence()
            .filter { it.op.type == OpType.EXPENSE && Buckets.of(it.op, categories) == Bucket.REGULAR }
            .filter { it.op.categoryId?.let(categories::get)?.key != "loans" }
            .filter { !Ledger.localDate(it.op.timestamp, zone).isBefore(since) }
            .sortedByDescending { it.op.timestamp }
            .mapNotNull { full ->
                val name = full.op.note.trim().ifBlank { full.op.categoryId?.let(categories::get)?.label().orEmpty() }
                if (name.isBlank()) return@mapNotNull null
                val posting = full.postings.firstOrNull() ?: return@mapNotNull null
                val currency = full.op.purchaseCurrency ?: accounts[posting.accountId]?.currency ?: return@mapNotNull null
                val amount = full.op.purchaseAmountMinor ?: -posting.amountMinor
                Obligation(
                    name = name.replaceFirstChar { it.titlecase(I18n.locale) },
                    amountMinor = amount.coerceAtLeast(0),
                    currency = currency,
                    dayOfMonth = Ledger.localDate(full.op.timestamp, zone).dayOfMonth,
                )
            }
            .distinctBy { it.name.lowercase() }
            .filter { it.name.lowercase() !in names }
            .toList()
    }

    /** The next charge on or after [today]. */
    fun nextCharge(payment: Obligation, today: LocalDate): LocalDate = Budget.nextDue(payment.dayOfMonth, today)

    /**
     * What the reminder says on [today], or null when it is not the day for one: [AHEAD] days
     * before the charge, and on the day itself.
     */
    fun reminder(payment: Obligation, today: LocalDate): String? {
        val due = nextCharge(payment, today)
        val date = due.format(DateTimeFormatter.ofPattern(if (I18n.russian) "d MMMM" else "MMMM d", I18n.locale))
        val amount = Fmt.amount(payment.amountMinor, payment.currency)
        val trial = payment.amountMinor == 0L
        return when (ChronoUnit.DAYS.between(today, due)) {
            AHEAD ->
                if (trial) tr("$date кончается пробный период. Ещё пользуешься?", "The free trial ends on $date. Still using it?")
                else tr("$date спишется $amount. Ещё пользуешься?", "$amount will be charged on $date. Still using it?")
            0L ->
                if (trial) tr("Сегодня кончается пробный период", "The free trial ends today")
                else tr("Сегодня спишется $amount", "$amount will be charged today")
            else -> null
        }
    }

    /** What the list costs a month in roubles; a currency without a rate counts as nothing. */
    fun monthlyRub(payments: List<Obligation>, rates: Rates): Long =
        payments.sumOf { rates.rubMinor(it.amountMinor, it.currency) ?: 0L }
}

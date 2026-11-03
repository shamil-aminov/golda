package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The money on the spending card for a period (SPEC §5): put 1 800 ₾ on the card for a month, and
 * this says how much a day is left for it, how fast it actually goes, and when it would run out.
 * Everything is in the card's own currency and measured by the card's balance, so spending that was
 * never written down still counts once the card is reconciled.
 */
data class Envelope(
    val account: Account,
    val start: LocalDate,
    val end: LocalDate,
    /** On the card now. */
    val balanceMinor: Long,
    /** Gone from the card since [start]: expenses and downward reconciliations, not transfers out. */
    val spentMinor: Long,
    val spentTodayMinor: Long,
    /** Days from today to [end], both included. */
    val daysLeft: Long,
    /** What may go a day from today to [end]: the money as of this morning over the days left. */
    val perDayMinor: Long,
    val leftTodayMinor: Long,
    /**
     * What actually goes a day since [start], big purchases aside: they already left the balance, and
     * one early in the period would otherwise count again as a pace. Null while nothing has gone.
     */
    val burnMinor: Long?,
    /** Days the card lasts at [burnMinor]; null with no burn yet. */
    val runwayDays: Long?,
) {
    val currency get() = account.currency

    /** The day the money runs out at this burn. */
    fun runsOut(today: LocalDate): LocalDate? = runwayDays?.let { today.plusDays(it) }

    /** It lasts the [daysLeft] at this burn (or nothing is spent yet): "хватит на 9 из 13 дней" is no. */
    fun lasts(today: LocalDate): Boolean = runwayDays?.let { it >= daysLeft } ?: true

    /** How many days the money was meant for: a month from 15 October is 31. */
    val plannedDays: Long get() = ChronoUnit.DAYS.between(start, end)

    /**
     * How many days the money will have lasted at this burn, counted from [start]: the days gone plus
     * the days the rest lasts. "Meant for 31, lasts 28." Null with no burn yet.
     */
    fun lastsDays(today: LocalDate): Long? = runsOut(today)?.let { ChronoUnit.DAYS.between(start, it) }

    /** The period's length in words when it is one of the usual ones. */
    val length: Envelopes.Length? get() = Envelopes.Length.entries.firstOrNull { Envelopes.end(start, it) == end }
}

object Envelopes {
    /** Lengths a period is usually set to; a date of your own is the fourth choice. */
    enum class Length { WEEK, TWO_WEEKS, MONTH }

    fun end(start: LocalDate, length: Length): LocalDate = when (length) {
        Length.WEEK -> start.plusWeeks(1)
        Length.TWO_WEEKS -> start.plusWeeks(2)
        Length.MONTH -> start.plusMonths(1)
    }

    /**
     * The card spent from: the one set, else the account most expenses came from (debts and accounts
     * nothing was spent from aside), else the usual account.
     */
    fun account(accounts: List<Account>, operations: List<OperationFull>, settings: Settings): Account? {
        settings.spendAccountId?.let { id -> accounts.firstOrNull { it.id == id }?.let { return it } }
        val spendable = accounts.filter { it.type != AccountType.LOAN && it.type != AccountType.CREDIT }
        val counts = operations.filter { it.op.type == OpType.EXPENSE }.flatMap { it.postings }.groupingBy { it.accountId }.eachCount()
        return spendable.maxByOrNull { counts[it.id] ?: 0 }?.takeIf { (counts[it.id] ?: 0) > 0 }
            ?: settings.lastAccountId?.let { id -> spendable.firstOrNull { it.id == id } }
            ?: spendable.firstOrNull()
    }

    /** The last day money came onto [accountId] (a top-up, an income), today included. */
    fun lastTopUp(accountId: Long, operations: List<OperationFull>, today: LocalDate, zone: ZoneId): LocalDate? = operations
        .filter { it.op.type == OpType.TRANSFER || it.op.type == OpType.INCOME }
        .filter { full -> full.postings.any { it.accountId == accountId && it.amountMinor > 0 } }
        .map { Ledger.localDate(it.op.timestamp, zone) }
        .filter { !it.isAfter(today) }
        .maxOrNull()

    /**
     * The period: the one set while it lasts; otherwise a month from the card's last top-up (or from
     * today, with none), so the screen means something before anything is set.
     */
    fun period(accountId: Long, operations: List<OperationFull>, settings: Settings, today: LocalDate, zone: ZoneId): Pair<LocalDate, LocalDate> {
        if (settings.periodEnd > 0 && settings.periodStart > 0) {
            val start = LocalDate.ofEpochDay(settings.periodStart)
            val end = LocalDate.ofEpochDay(settings.periodEnd)
            if (!end.isBefore(today) && !start.isAfter(today)) return start to end
        }
        val start = lastTopUp(accountId, operations, today, zone) ?: today
        val end = end(start, Length.MONTH).let { if (it.isBefore(today)) today else it }
        return start to end
    }

    fun of(
        account: Account,
        start: LocalDate,
        end: LocalDate,
        operations: List<OperationFull>,
        today: LocalDate,
        zone: ZoneId,
        categories: Map<Long, Category> = emptyMap(),
    ): Envelope {
        class Move(val day: LocalDate, val type: OpType, val amount: Long, val big: Boolean)
        val own = operations.flatMap { full ->
            val big = full.op.type == OpType.EXPENSE && Buckets.of(full.op, categories) == Bucket.BIG
            full.postings.filter { it.accountId == account.id }.map { Move(Ledger.localDate(full.op.timestamp, zone), full.op.type, it.amountMinor, big) }
        }
        val balance = own.sumOf { it.amount }
        // Spending is what left the card for good: expenses and reconciliations down. Money moved to
        // another account of yours (cash from an ATM) is not spent yet.
        fun spent(from: LocalDate, withBig: Boolean = true) = -own
            .filter { !it.day.isBefore(from) && !it.day.isAfter(today) && it.amount < 0 && (it.type == OpType.EXPENSE || it.type == OpType.ADJUSTMENT) }
            .filter { withBig || !it.big }
            .sumOf { it.amount }
        val spentSince = spent(start)
        val spentToday = spent(today)
        val daysLeft = (ChronoUnit.DAYS.between(today, end) + 1).coerceAtLeast(1)
        val perDay = ((balance + spentToday) / daysLeft).coerceAtLeast(0)
        val elapsed = (ChronoUnit.DAYS.between(start, today) + 1).coerceAtLeast(1)
        // The pace is everyday life; a big purchase is already off the balance the pace divides.
        val usual = spent(start, withBig = false)
        val burn = (usual / elapsed).takeIf { usual > 0 && it > 0 }
        val runway = burn?.let { balance.coerceAtLeast(0) / it }
        return Envelope(account, start, end, balance, spentSince, spentToday, daysLeft, perDay, perDay - spentToday, burn, runway)
    }

    /** The envelope as the app shows it: the card and the period worked out from [settings]. */
    fun current(
        accounts: List<Account>,
        operations: List<OperationFull>,
        settings: Settings,
        today: LocalDate,
        zone: ZoneId,
        categories: Map<Long, Category> = emptyMap(),
    ): Envelope? {
        val account = account(accounts, operations, settings) ?: return null
        val (start, end) = period(account.id, operations, settings, today, zone)
        return of(account, start, end, operations, today, zone, categories)
    }
}

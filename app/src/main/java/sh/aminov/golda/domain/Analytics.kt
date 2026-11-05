package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** Both ends included. */
data class Period(val from: LocalDate, val to: LocalDate) {
    val days: List<LocalDate> get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
}

enum class PeriodKind { WEEK, MONTH, CUSTOM }

data class CategorySpend(val categoryId: Long?, val rubMinor: Long)

data class Report(
    val period: Period,
    /** Spending per day of the period, every expense, in rubles (kopecks), oldest first. */
    val days: List<Pair<LocalDate, Long>>,
    /** Everyday spending alone per day, for the review: one rent would dwarf every other day. */
    val dailyDays: List<Pair<LocalDate, Long>>,
    val spentRub: Long,
    /** All of [spentRub] over the days counted so far: plain arithmetic, the total a day. */
    val averagePerDayRub: Long,
    /** Everyday spending a day, for the review: regular payments and big one-offs would make it jump. */
    val dailyPerDayRub: Long,
    /** The period's spending by bucket; the three add up to [spentRub]. */
    val dailyRub: Long,
    val regularRub: Long,
    val bigRub: Long,
    /** Biggest first. */
    val categories: List<CategorySpend>,
    val incomeRub: Long,
    /** What currency exchanges cost against the CBR rate. */
    val fxLossRub: Long,
    /** How many rubles' worth went through exchanges. */
    val fxVolumeRub: Long,
)

object Analytics {
    /** Tapped again, what was picked lets go; anything else takes its place. */
    fun <T> toggled(current: T?, tapped: T): T? = if (current == tapped) null else tapped

    /**
     * The most bars the chart holds: as many as still take a fingertip each, and just enough for two
     * weeks of days and for the thirteen calendar months a year from any day touches.
     */
    const val MAX_BARS = 14

    enum class Bar { DAY, WEEK, MONTH, YEAR }

    /** The finest unit that keeps [first]..[last] within [MAX_BARS]; past fourteen years, years all the same. */
    fun barFor(first: LocalDate, last: LocalDate): Bar = Bar.entries.firstOrNull { barCount(first, last, it) <= MAX_BARS } ?: Bar.YEAR

    private fun barCount(first: LocalDate, last: LocalDate, bar: Bar): Long = when (bar) {
        Bar.DAY -> ChronoUnit.DAYS.between(first, last) + 1
        Bar.WEEK -> ChronoUnit.DAYS.between(calendarStart(first, bar), calendarStart(last, bar)) / 7 + 1
        Bar.MONTH -> ChronoUnit.MONTHS.between(calendarStart(first, bar), calendarStart(last, bar)) + 1
        Bar.YEAR -> (last.year - first.year).toLong() + 1
    }

    /** The first day of the calendar's week (from Monday), month or year around [date]. */
    private fun calendarStart(date: LocalDate, bar: Bar): LocalDate = when (bar) {
        Bar.DAY -> date
        Bar.WEEK -> date.minusDays(date.dayOfWeek.value - 1L)
        Bar.MONTH -> date.withDayOfMonth(1)
        Bar.YEAR -> date.withDayOfYear(1)
    }

    /**
     * The day a [date] counts under, for bars of [bar] over a period from [first]: the start of its
     * calendar unit, never before [first]. Calendar units, so a bar is a week one knows; only the
     * period's edges can cut one short, and its label says by how much.
     */
    fun barStart(date: LocalDate, first: LocalDate, bar: Bar): LocalDate = calendarStart(date, bar).let { if (it.isBefore(first)) first else it }

    /** The last day of the bar that starts on [start], never after [last]. */
    fun barEnd(start: LocalDate, last: LocalDate, bar: Bar): LocalDate = when (bar) {
        Bar.DAY -> start
        Bar.WEEK -> calendarStart(start, bar).plusDays(6)
        Bar.MONTH -> start.withDayOfMonth(start.lengthOfMonth())
        Bar.YEAR -> start.withDayOfYear(start.lengthOfYear())
    }.let { if (it.isAfter(last)) last else it }

    /** Days summed into bars of the unit [barFor] gives. */
    fun bars(days: List<Pair<LocalDate, Long>>): List<Pair<LocalDate, Long>> {
        val first = days.firstOrNull()?.first ?: return emptyList()
        val bar = barFor(first, days.last().first)
        if (bar == Bar.DAY) return days
        return days.groupBy { barStart(it.first, first, bar) }.map { (start, list) -> start to list.sumOf { it.second } }
    }

    fun period(kind: PeriodKind, today: LocalDate, custom: Period? = null): Period = when (kind) {
        PeriodKind.WEEK -> Period(today.minusDays(6), today)
        PeriodKind.MONTH -> Period(today.minusDays(29), today)
        PeriodKind.CUSTOM -> custom ?: Period(today.minusDays(6), today)
    }

    fun report(
        operations: List<OperationFull>,
        accounts: Map<Long, Account>,
        categories: Map<Long, Category>,
        period: Period,
        today: LocalDate,
        zone: ZoneId,
        rates: Rates,
    ): Report {
        val inPeriod = operations.filter {
            val day = Ledger.localDate(it.op.timestamp, zone)
            !day.isBefore(period.from) && !day.isAfter(period.to)
        }
        val expenses = inPeriod.filter { it.op.type == OpType.EXPENSE }
        fun cost(full: OperationFull) = -full.postings.sumOf { it.rubMinor }

        val spent = expenses.sumOf(::cost)
        val byBucket = expenses.groupBy { Buckets.of(it.op, categories) }.mapValues { (_, list) -> list.sumOf(::cost) }
        val daily = byBucket[Bucket.DAILY] ?: 0
        // The bars are what the total is: every expense. A group's own bars are its report alone.
        fun perDay(list: List<OperationFull>) = list.groupBy { Ledger.localDate(it.op.timestamp, zone) }.mapValues { (_, l) -> l.sumOf(::cost) }
        val byDay = perDay(expenses)
        val dailyByDay = perDay(expenses.filter { Buckets.of(it.op, categories) == Bucket.DAILY })
        val lastCounted = minOf(period.to, today)
        val countedDays = (ChronoUnit.DAYS.between(period.from, lastCounted) + 1).coerceAtLeast(1)

        var loss = 0.0
        var volume = 0.0
        for (full in inPeriod.filter { it.op.type == OpType.TRANSFER }) {
            val out = full.postings.firstOrNull { it.amountMinor < 0 } ?: continue
            val into = full.postings.firstOrNull { it.amountMinor > 0 } ?: continue
            val fromCode = accounts[out.accountId]?.currency ?: continue
            val toCode = accounts[into.accountId]?.currency ?: continue
            if (fromCode == toCode) continue
            val fromRate = full.op.cbrFrom ?: rates.official(fromCode) ?: continue
            val toRate = full.op.cbrTo ?: rates.official(toCode) ?: continue
            val sent = Currencies.toMajor(-out.amountMinor, fromCode) * fromRate
            val received = Currencies.toMajor(into.amountMinor, toCode) * toRate
            loss += sent - received
            volume += sent
        }

        return Report(
            period = period,
            days = period.days.map { it to (byDay[it] ?: 0L) },
            dailyDays = period.days.map { it to (dailyByDay[it] ?: 0L) },
            spentRub = spent,
            averagePerDayRub = spent / countedDays,
            dailyPerDayRub = daily / countedDays,
            dailyRub = daily,
            regularRub = byBucket[Bucket.REGULAR] ?: 0,
            bigRub = byBucket[Bucket.BIG] ?: 0,
            categories = expenses.groupBy { it.op.categoryId }
                .map { (id, list) -> CategorySpend(id, list.sumOf(::cost)) }
                .sortedByDescending { it.rubMinor },
            incomeRub = inPeriod.filter { it.op.type == OpType.INCOME }.sumOf { full -> full.postings.sumOf { it.rubMinor } },
            fxLossRub = (loss * 100).roundToLong(),
            fxVolumeRub = (volume * 100).roundToLong(),
        )
    }
}

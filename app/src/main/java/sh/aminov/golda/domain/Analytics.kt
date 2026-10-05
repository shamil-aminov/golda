package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
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

enum class PeriodKind { WEEK, MONTH, SINCE_PAYDAY, CUSTOM }

data class CategorySpend(val categoryId: Long?, val rubMinor: Long)

data class Report(
    val period: Period,
    /** Spending per day of the period, in rubles (kopecks), oldest first. */
    val days: List<Pair<LocalDate, Long>>,
    val spentRub: Long,
    val averagePerDayRub: Long,
    /** Biggest first. */
    val categories: List<CategorySpend>,
    val incomeRub: Long,
    /** What currency exchanges cost against the CBR rate. */
    val fxLossRub: Long,
    /** How many rubles' worth went through exchanges. */
    val fxVolumeRub: Long,
)

object Analytics {
    fun period(kind: PeriodKind, today: LocalDate, settings: Settings, custom: Period? = null): Period = when (kind) {
        PeriodKind.WEEK -> Period(today.minusDays(6), today)
        PeriodKind.MONTH -> Period(today.minusDays(29), today)
        PeriodKind.SINCE_PAYDAY -> Period(settings.lastPayday(today), today)
        PeriodKind.CUSTOM -> custom ?: Period(today.minusDays(6), today)
    }

    fun report(
        operations: List<OperationFull>,
        accounts: Map<Long, Account>,
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

        val byDay = expenses.groupBy { Ledger.localDate(it.op.timestamp, zone) }.mapValues { (_, list) -> list.sumOf(::cost) }
        val spent = expenses.sumOf(::cost)
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
            spentRub = spent,
            averagePerDayRub = spent / countedDays,
            categories = expenses.groupBy { it.op.categoryId }
                .map { (id, list) -> CategorySpend(id, list.sumOf(::cost)) }
                .sortedByDescending { it.rubMinor },
            incomeRub = inPeriod.filter { it.op.type == OpType.INCOME }.sumOf { full -> full.postings.sumOf { it.rubMinor } },
            fxLossRub = (loss * 100).roundToLong(),
            fxVolumeRub = (volume * 100).roundToLong(),
        )
    }
}

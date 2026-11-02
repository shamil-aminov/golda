package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.PeriodKind
import sh.aminov.golda.domain.Rates
import java.time.LocalDate
import java.time.ZoneOffset

class AnalyticsTest {
    private val zone = ZoneOffset.UTC
    private val rates = Rates(mapOf("USD" to 83.2454, "GEL" to 31.9597), markup = 0.10)
    private val rubCard = Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true)
    private val multiUsd = Account(2, "Мультивалютная USD", "USD", AccountType.CARD, includeInFree = true)
    private val accounts = mapOf(1L to rubCard, 2L to multiUsd)
    private val today = LocalDate.of(2026, 10, 2)
    private var nextId = 1L

    private fun at(day: Int, hour: Int = 12) = LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(zone).toEpochMilli()
    private fun atSep(day: Int) = LocalDate.of(2026, 9, day).atTime(12, 0).toInstant(zone).toEpochMilli()

    private fun op(type: OpType, time: Long, category: Long?, vararg postings: Posting, cbrFrom: Double? = null, cbrTo: Double? = null): OperationFull {
        val id = nextId++
        return OperationFull(Operation(id, type, time, category, cbrFrom = cbrFrom, cbrTo = cbrTo), postings.map { it.copy(operationId = id) })
    }

    private val ops = listOf(
        op(OpType.EXPENSE, at(2, 9), 1, Posting(accountId = 1, amountMinor = -50_000, rubMinor = -50_000)),
        op(OpType.EXPENSE, at(2, 13), 2, Posting(accountId = 2, amountMinor = -1_000, rubMinor = -92_000)),
        op(OpType.EXPENSE, at(1), 1, Posting(accountId = 1, amountMinor = -30_000, rubMinor = -30_000)),
        op(OpType.EXPENSE, atSep(20), 1, Posting(accountId = 1, amountMinor = -999_900, rubMinor = -999_900)),
        op(OpType.INCOME, at(1), null, Posting(accountId = 1, amountMinor = 100_000, rubMinor = 100_000)),
        // 46 000 ₽ → 500 $ while the CBR said 83.25: 4 377 ₽ lost on the way.
        op(
            OpType.TRANSFER, at(1), null,
            Posting(accountId = 1, amountMinor = -4_600_000, rubMinor = -4_600_000),
            Posting(accountId = 2, amountMinor = 50_000, rubMinor = 4_600_000),
            cbrFrom = 1.0, cbrTo = 83.2454,
        ),
    )

    @Test
    fun weekReport() {
        val period = Analytics.period(PeriodKind.WEEK, today)
        assertEquals(Period(LocalDate.of(2026, 9, 26), today), period)
        val r = Analytics.report(ops, accounts, emptyMap(), period, today, zone, rates)
        assertEquals(7, r.days.size)
        assertEquals(142_000L, r.days.last().second) // 500 ₽ + 10 $ that cost 920 ₽
        assertEquals(30_000L, r.days[r.days.size - 2].second)
        assertEquals(172_000L, r.spentRub)
        assertEquals(172_000L / 7, r.averagePerDayRub)
        assertEquals(listOf(1L to 80_000L, 2L to 92_000L).sortedByDescending { it.second }, r.categories.map { it.categoryId to it.rubMinor })
        assertEquals(100_000L, r.incomeRub)
        assertEquals(437_730L, r.fxLossRub)
        assertEquals(4_600_000L, r.fxVolumeRub)
    }

    @Test
    fun averageCountsOnlyDaysThatHappened() {
        val period = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val r = Analytics.report(ops, accounts, emptyMap(), period, today, zone, rates)
        assertEquals(31, r.days.size)
        assertEquals(172_000L / 2, r.averagePerDayRub)
    }

    @Test
    fun rentAndBigPurchasesAreApartFromEverydaySpending() {
        val categories = listOf(
            Category(1, "groceries", "Продукты", "", CategoryKind.EXPENSE),
            Category(2, "housing", "Жильё", "", CategoryKind.EXPENSE),
        ).associateBy { it.id }
        val period = Analytics.period(PeriodKind.WEEK, today)
        val withBig = ops + op(OpType.EXPENSE, at(1), 1, Posting(accountId = 1, amountMinor = -500_000, rubMinor = -500_000)).let {
            it.copy(op = it.op.copy(bucket = Bucket.BIG))
        }
        val r = Analytics.report(withBig, accounts, categories, period, today, zone, rates)
        // Groceries: 500 + 300 ₽; the 10 $ lunch is in "housing" here, so regular; the 5 000 ₽ is big.
        assertEquals(80_000L, r.dailyRub)
        assertEquals(92_000L, r.regularRub)
        assertEquals(500_000L, r.bigRub)
        assertEquals(r.spentRub, r.dailyRub + r.regularRub + r.bigRub)
        // The average and the bars are everyday spending only.
        assertEquals(80_000L / 7, r.averagePerDayRub)
        assertEquals(50_000L, r.days.last().second)
        assertEquals(30_000L, r.days[r.days.size - 2].second)
    }

    @Test
    fun nextDueRollsOverAndClamps() {
        assertEquals(LocalDate.of(2026, 10, 2), Budget.nextDue(2, today))
        assertEquals(LocalDate.of(2026, 11, 1), Budget.nextDue(1, today))
        assertEquals(LocalDate.of(2027, 2, 28), Budget.nextDue(31, LocalDate.of(2027, 2, 1)))
    }
}

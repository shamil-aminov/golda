package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.DefaultCategories
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.data.Rate
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.ReviewPrompt
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.ui.AppData
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Years of records: five years at a dozen purchases a day. Everything a screen works out from them
 * stays well under a frame budget's worth of seconds, and a ten-year range is no harder than a month.
 */
class ScaleTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val zone = ZoneOffset.UTC
    private val categories = DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }
    private val accounts = listOf(
        Account(1, "Карта", "RUB", AccountType.CARD, includeInFree = true),
        Account(2, "Лари", "GEL", AccountType.CARD, includeInFree = true),
    )

    private fun years(n: Int): List<OperationFull> {
        val r = Random(7)
        val out = ArrayList<OperationFull>()
        var id = 1L
        val start = today.minusYears(n.toLong())
        var day = start
        while (!day.isAfter(today)) {
            repeat(12) {
                val ts = day.atTime(8 + r.nextInt(14), r.nextInt(60)).toInstant(zone).toEpochMilli()
                val minor = 100L + r.nextLong(20_000)
                val acc = 1L + r.nextInt(2)
                out += OperationFull(
                    Operation(id, OpType.EXPENSE, ts, categoryId = 1L + r.nextInt(12), note = "покупка ${r.nextInt(40)}"),
                    listOf(Posting(id, id, acc, -minor, -minor * if (acc == 2L) 34 else 1)),
                )
                id++
            }
            day = day.plusDays(1)
        }
        return out
    }

    private inline fun ms(block: () -> Unit): Long {
        val t = System.nanoTime(); block(); return (System.nanoTime() - t) / 1_000_000
    }

    @Test
    fun fiveYearsOfRecordsStayQuick() {
        val ops = years(5)
        val rates = listOf(Rate("GEL", 34.0, today.toString()))
        // Warm the JVM up once, then measure.
        AppData(Settings(), accounts, ops.take(1_000), categories, rates, today = today).envelope
        lateinit var data: AppData
        val build = ms { data = AppData(Settings(), accounts, ops, categories, rates, today = today); data.envelope }
        val month = ms { Analytics.report(ops, data.accountById, data.categoryById, Period(today.minusDays(29), today), today, zone, data.rates) }
        val decade = ms { Analytics.report(ops, data.accountById, data.categoryById, Period(today.minusYears(10), today), today, zone, data.rates) }
        val review = ms { ReviewPrompt.data(Period(today.minusYears(10), today), ops, data.accountById, data.categoryById, data.rates, today, zone) }
        println("${ops.size} operations: data ${build} ms, a month ${month} ms, ten years ${decade} ms, review data ${review} ms")
        assertTrue("data took $build ms", build < 2_000)
        assertTrue("a month took $month ms", month < 1_000)
        assertTrue("ten years took $decade ms", decade < 3_000)
        assertTrue("review data took $review ms", review < 5_000)
    }

    @Test
    fun longRangesAreDrawnInWeeksOrMonths() {
        fun days(n: Int) = (0 until n).map { today.minusDays((n - 1 - it).toLong()) to 100L }
        assertEquals(Analytics.Bar.DAY, Analytics.barFor(62))
        assertEquals(Analytics.Bar.WEEK, Analytics.barFor(63))
        assertEquals(Analytics.Bar.WEEK, Analytics.barFor(400))
        assertEquals(Analytics.Bar.MONTH, Analytics.barFor(401))
        // A month stays in days.
        assertEquals(days(30), Analytics.bars(days(30)))
        // Ninety days in weeks from the first day: 13 bars, the last one short, nothing lost.
        val weeks = Analytics.bars(days(90))
        assertEquals(13, weeks.size)
        assertEquals(700L, weeks.first().second)
        assertEquals(600L, weeks.last().second)
        assertEquals(9_000L, weeks.sumOf { it.second })
        assertEquals(today.minusDays(89), weeks.first().first)
        // Ten years in months: about 120 bars, the first starting on the first day, not before it.
        val decade = days(3_653)
        val months = Analytics.bars(decade)
        assertTrue("${months.size} bars", months.size in 120..122)
        assertEquals(decade.first().first, months.first().first)
        assertEquals(1, months[1].first.dayOfMonth)
        assertEquals(365_300L, months.sumOf { it.second })
        assertTrue(Analytics.bars(emptyList()).isEmpty())
    }
}

package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.KindBlock
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.PeriodKind
import java.time.LocalDate

/** The rules of Insights' picks: a second tap lets go, the kinds fit their block. */
class InsightsPicksTest {
    @Test
    fun aSecondTapLetsGoAndAnotherTakesOver() = prop {
        checkAll(Arb.int(0..5), Arb.int(0..5)) { a, b ->
            assertEquals(a, Analytics.toggled(null, a))
            assertNull(Analytics.toggled(a, a))
            if (a != b) assertEquals(b, Analytics.toggled(a, b))
        }
    }

    private fun days(first: LocalDate, count: Int, each: (Int) -> Long = { 100L }) = (0 until count).map { first.plusDays(it.toLong()) to each(it) }

    @Test
    fun barsStayWithinTheCapAndLoseNothing() = prop {
        // Up to twelve years: any such span touches at most fourteen calendar ones.
        checkAll(Arb.int(0..20_000), Arb.int(1..4_500), Arb.long(0L..1_000_000L)) { offset, count, money ->
            val all = days(LocalDate.of(2000, 1, 1).plusDays(offset.toLong()), count) { (money * (it % 7)) }
            val bars = Analytics.bars(all)
            assertTrue("${bars.size} bars", bars.size <= Analytics.MAX_BARS)
            assertEquals(all.sumOf { it.second }, bars.sumOf { it.second })
        }
    }

    @Test
    fun theUnitIsTheFinestThatFits() = prop {
        checkAll(Arb.int(0..20_000), Arb.int(1..1_500)) { offset, count ->
            val first = LocalDate.of(2000, 1, 1).plusDays(offset.toLong())
            val last = first.plusDays(count - 1L)
            val bar = Analytics.barFor(first, last)
            if (bar != Analytics.Bar.DAY) {
                // One unit finer would not have fitted.
                val finer = Analytics.Bar.entries[bar.ordinal - 1]
                val starts = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map { Analytics.barStart(it, first, finer) }.distinct().count()
                assertTrue("$first $last $bar", starts > Analytics.MAX_BARS)
            }
        }
    }

    @Test
    fun aBarsSpanHoldsItsDaysAndNoOthers() = prop {
        checkAll(Arb.int(0..20_000), Arb.int(1..1_500)) { offset, count ->
            val first = LocalDate.of(2000, 1, 1).plusDays(offset.toLong())
            val last = first.plusDays(count - 1L)
            val bar = Analytics.barFor(first, last)
            var day = first
            while (!day.isAfter(last)) {
                val start = Analytics.barStart(day, first, bar)
                val end = Analytics.barEnd(start, last, bar)
                assertTrue("$day $start $end", !day.isBefore(start) && !day.isAfter(end) && !start.isBefore(first) && !end.isAfter(last))
                // The next day starts the next bar or stays in this one.
                if (day == end && day != last) assertEquals(day.plusDays(1), Analytics.barStart(day.plusDays(1), first, bar))
                day = day.plusDays(1)
            }
        }
    }

    @Test
    fun theUnitsByPeriod() {
        val sat = LocalDate.of(2026, 10, 24)
        fun back(n: Long) = Analytics.barFor(sat.minusDays(n - 1), sat)
        assertEquals(Analytics.Bar.DAY, back(7))
        assertEquals(Analytics.Bar.DAY, back(14))
        assertEquals(Analytics.Bar.WEEK, back(15))
        assertEquals(Analytics.Bar.WEEK, back(30))
        assertEquals(Analytics.Bar.MONTH, back(365))
        assertEquals(Analytics.Bar.YEAR, back(5 * 365))
    }

    @Test
    fun aMonthsWeeksAreTheCalendars() {
        val first = LocalDate.of(2026, 9, 25)
        val bars = Analytics.bars(days(first, 30))
        // Fri 25 to Sun 27 is cut short by the period; the weeks after it start on Mondays.
        assertEquals(listOf(first, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19)), bars.map { it.first })
        assertEquals(listOf(300L, 700L, 700L, 700L, 600L), bars.map { it.second })
        assertEquals(LocalDate.of(2026, 9, 27), Analytics.barEnd(first, LocalDate.of(2026, 10, 24), Analytics.Bar.WEEK))
        assertEquals(LocalDate.of(2026, 10, 24), Analytics.barEnd(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24), Analytics.Bar.WEEK))
    }

    @Test
    fun monthsAndYearsEndWithTheCalendar() {
        val last = LocalDate.of(2030, 6, 1)
        assertEquals(LocalDate.of(2026, 2, 28), Analytics.barEnd(LocalDate.of(2026, 2, 3), last, Analytics.Bar.MONTH))
        assertEquals(LocalDate.of(2028, 12, 31), Analytics.barEnd(LocalDate.of(2028, 1, 1), last, Analytics.Bar.YEAR))
        assertEquals(last, Analytics.barEnd(LocalDate.of(2030, 1, 1), last, Analytics.Bar.YEAR))
        assertEquals(LocalDate.of(2028, 3, 3), Analytics.barEnd(LocalDate.of(2028, 3, 3), last, Analytics.Bar.DAY))
        assertEquals(LocalDate.of(2026, 3, 1), Analytics.barStart(LocalDate.of(2026, 3, 17), LocalDate.of(2025, 1, 1), Analytics.Bar.MONTH))
        assertEquals(LocalDate.of(2026, 1, 1), Analytics.barStart(LocalDate.of(2026, 3, 17), LocalDate.of(2020, 1, 1), Analytics.Bar.YEAR))
        assertEquals(LocalDate.of(2026, 3, 10), Analytics.barStart(LocalDate.of(2026, 3, 17), LocalDate.of(2026, 3, 10), Analytics.Bar.YEAR))
    }

    @Test
    fun thePeriods() {
        val today = LocalDate.of(2026, 10, 24)
        val own = Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1))
        assertEquals(Period(LocalDate.of(2026, 10, 18), today), Analytics.period(PeriodKind.WEEK, today))
        assertEquals(Period(LocalDate.of(2026, 9, 25), today), Analytics.period(PeriodKind.MONTH, today))
        assertEquals(own, Analytics.period(PeriodKind.CUSTOM, today, own))
        // Nothing picked yet: a week, not an empty screen.
        assertEquals(Period(LocalDate.of(2026, 10, 18), today), Analytics.period(PeriodKind.CUSTOM, today))
    }

    @Test
    fun theLastWeekAndMonthOfTheCapAreStillThere() {
        // Monday to Sunday, fourteen weeks exactly, and a day over.
        val monday = LocalDate.of(2026, 1, 5)
        assertEquals(Analytics.Bar.WEEK, Analytics.barFor(monday, monday.plusDays(14 * 7 - 1L)))
        assertEquals(Analytics.Bar.MONTH, Analytics.barFor(monday, monday.plusDays(14 * 7L)))
        // Fourteen calendar months, and one over.
        assertEquals(Analytics.Bar.MONTH, Analytics.barFor(LocalDate.of(2025, 1, 31), LocalDate.of(2026, 2, 1)))
        assertEquals(Analytics.Bar.YEAR, Analytics.barFor(LocalDate.of(2025, 1, 31), LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun aBarHoldsThreeLayersAtMost() {
        assertEquals(listOf(0, 1, 2, 3, 2, 2, 2), (0..6).map { Analytics.coloured(it) })
    }

    @Test
    fun layersFillTheirBarAndNoneVanishes() = prop {
        checkAll(Arb.list(Arb.long(1L..10_000_000L), 1..3), Arb.int(12..96)) { amounts, height ->
            val layers = Analytics.layerHeights(amounts, height.toFloat(), 4f)
            assertEquals(height.toFloat(), layers.sum(), 0.01f)
            assertTrue("$layers", layers.all { it >= 4f - 0.01f })
        }
    }

    @Test
    fun layersGoByShareWhereTheyCan() {
        assertEquals(listOf(60f, 30f, 10f), Analytics.layerHeights(listOf(600, 300, 100), 100f, 4f))
        // Too low for three of 4: plain shares.
        assertEquals(listOf(5f, 3f, 2f), Analytics.layerHeights(listOf(500, 300, 200), 10f, 4f))
        assertEquals(listOf(0f, 0f), Analytics.layerHeights(listOf(0, 0), 10f, 4f))
        // The thin one's 4 comes off the two others by what they have to spare.
        val layers = Analytics.layerHeights(listOf(899, 100, 1), 100f, 4f)
        assertEquals(4f, layers[2], 0.01f)
        assertEquals(100f, layers.sum(), 0.01f)
        assertTrue(layers[0] < 89.9f && layers[1] < 10f)
    }

    @Test
    fun columnsOnlyForSharesThatReadAsEqual() {
        fun cols(a: Long, b: Long, c: Long, width: Float = 395f) = KindBlock.columns(a, b, c, width, 4f, 116f)
        assertEquals(listOf(129f, 129f, 129f), cols(100, 100, 100))
        assertEquals(listOf(139.32f, 123.84f, 123.84f), cols(36, 32, 32)!!.map { Math.round(it * 100) / 100f })
        assertNull(cols(36, 33, 31))
        assertNull(cols(40, 35, 25))
        assertNull(cols(0, 0, 0))
        // The words would not fit: the block's.
        assertNull(cols(100, 100, 100, 304f))
    }

    @Test
    fun columnsShareOutTheirRoom() = prop {
        checkAll(Arb.long(1L..1_000_000L), Arb.int(0..4), Arb.int(0..4)) { base, a, b ->
            val big = base * 100 + base * a
            val small = base * 100
            KindBlock.columns(big, small + base * minOf(a, b), small, 500f, 4f, 10f)?.let {
                assertEquals(492f, it.sum(), 0.1f)
            }
        }
    }

    @Test
    fun noDaysNoBars() = assertTrue(Analytics.bars(emptyList()).isEmpty())

    @Test
    fun theKindsFitTheirBlockAndStayReadable() = prop {
        checkAll(Arb.long(0L..10_000_000L), Arb.long(0L..10_000_000L), Arb.long(0L..10_000_000L), Arb.int(300..1_000)) { a, b, c, width ->
            val (big, middle, small) = listOf(a, b, c).sortedDescending()
            val w = width.toFloat()
            val split = KindBlock.split(big, middle, small, w, 136f, 4f, 128f, 56f)
            val right = w - 4f - split.left
            assertTrue("$split", split.left > 0 && right >= 128f - 0.01f)
            assertTrue("$split", split.low >= 56f - 0.01f && 132f - split.low >= split.low - 0.01f)
        }
    }

    @Test
    fun theAreasFollowTheSharesWhenNothingIsTooSmall() {
        val split = KindBlock.split(600, 300, 100, 1004f, 136f, 4f, 128f, 20f)
        assertEquals(600f, split.left, 0.01f)
        assertEquals(33f, split.low, 0.01f)
    }

    @Test
    fun nothingSpentStillGivesThreeTiles() {
        val split = KindBlock.split(0, 0, 0, 400f, 136f, 4f, 128f, 56f)
        assertEquals(132f, split.left, 0.01f)
        assertEquals(56f, split.low, 0.01f)
    }
}

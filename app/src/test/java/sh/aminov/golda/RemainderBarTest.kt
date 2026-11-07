package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.domain.Envelope
import java.time.LocalDate

/** The solid remainder and ghost use one scale, including exhausted and newly funded periods. */
class RemainderBarTest {
    private fun envelope(funds: Long, spent: Long, daysLeft: Long = 22) = Envelope(
        Account(1, "Card", "GEL", AccountType.CARD, includeInFree = true),
        LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14),
        balanceMinor = funds - spent, spentMinor = spent, spentTodayMinor = 1_500,
        daysLeft = daysLeft, perDayMinor = 4_700, leftTodayMinor = 3_200,
        burnMinor = 12_200, runwayDays = 8,
    )

    @Test fun bothEndsAreFractionsOfTheSameFunds() = prop {
        checkAll(Arb.long(1L..100_000_000L), Arb.long(-100_000_000L..200_000_000L), Arb.int(1..31)) { funds, spent, days ->
            val e = envelope(funds, spent, days.toLong())
            assertEquals(((funds - spent).toDouble() / funds).coerceIn(0.0, 1.0).toFloat(), e.leftShare, 0.000001f)
            val expected = funds - funds * (32 - days) / 31
            assertEquals(expected, e.plannedLeftMinor)
            assertEquals((expected.toDouble() / funds).toFloat(), e.plannedLeftShare, 0f)
            assertTrue(e.leftShare in 0f..1f && e.plannedLeftShare in 0f..1f)
        }
    }

    @Test fun behindPlanTheGhostRunsFromTheFillToTheNorm() = prop {
        checkAll(Arb.long(1_000L..100_000_000L)) { funds ->
            val plan = envelope(funds, 0).normMinor
            val e = envelope(funds, plan + funds / 10)
            assertTrue(e.leftShare < e.plannedLeftShare)
            assertEquals((e.spentMinor - e.normMinor).toFloat() / funds, e.plannedLeftShare - e.leftShare, 0.000001f)
        }
    }

    @Test fun aheadOfPlanTheSolidFillCoversTheGhostAndOnPlanTheirEndsMeet() = prop {
        checkAll(Arb.long(1_000L..100_000_000L)) { funds ->
            val plan = envelope(funds, 0).normMinor
            val ahead = envelope(funds, plan - funds / 10)
            assertTrue(ahead.leftShare > ahead.plannedLeftShare)
            val onPlan = envelope(funds, plan)
            assertEquals(onPlan.leftShare, onPlan.plannedLeftShare, 0f)
        }
    }

    @Test fun spendingDrainsTheFillAndATopUpGrowsIt() = prop {
        checkAll(Arb.long(10_000L..100_000_000L), Arb.long(1L..10_000L)) { funds, extra ->
            val e = envelope(funds, funds / 2)
            assertTrue(envelope(funds, e.spentMinor + extra).leftShare <= e.leftShare)
            assertTrue(envelope(funds + extra, e.spentMinor).leftShare >= e.leftShare)
        }
    }

    @Test fun belowZeroTodayStillShowsThePeriodsRemainder() {
        val e = envelope(241_200, 144_610).copy(spentTodayMinor = 7_500, leftTodayMinor = -2_800)
        assertEquals((96_590.0 / 241_200).toFloat(), e.leftShare, 0f)
        assertEquals(e.leftShare, e.copy(leftTodayMinor = 3_200).leftShare, 0f)
        assertTrue(e.plannedLeftShare > e.leftShare)
    }

    @Test fun atTheStartTheFillIsFullAndTheNormAlreadyIncludesToday() = prop {
        checkAll(Arb.long(31L..100_000_000L)) { funds ->
            val e = envelope(funds, 0, daysLeft = 31)
            assertEquals(1f, e.leftShare, 0f)
            assertEquals(funds - funds / 31, e.plannedLeftMinor)
            assertEquals(((funds - funds / 31).toDouble() / funds).toFloat(), e.plannedLeftShare, 0f)
        }
    }

    @Test fun emptyAndOverdrawnPeriodsHaveNoInventedFill() {
        listOf(envelope(0, 0), envelope(0, 100), envelope(-100, 0)).forEach {
            assertEquals(0f, it.leftShare, 0f)
            assertEquals(0L, it.plannedLeftMinor)
            assertEquals(0f, it.plannedLeftShare, 0f)
        }
        assertEquals(0f, envelope(100, 100).leftShare, 0f)
        assertEquals(0f, envelope(100, 200).leftShare, 0f)
        assertEquals(1f, envelope(100, 0).leftShare, 0f)
        assertEquals(0f, envelope(100, 0, daysLeft = 1).plannedLeftShare, 0f)
    }
}

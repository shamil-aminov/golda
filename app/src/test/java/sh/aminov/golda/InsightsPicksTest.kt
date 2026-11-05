package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.KindBlock

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

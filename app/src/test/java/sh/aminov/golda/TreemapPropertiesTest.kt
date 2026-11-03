package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.domain.TreeTile
import sh.aminov.golda.domain.Treemap
import kotlin.math.max
import kotlin.math.min

/** The mosaic for any spending: every category one tile, sized by its share, the whole area and no overlaps. */
class TreemapPropertiesTest {
    private fun overlap(a: TreeTile, b: TreeTile): Double {
        val w = min(a.x + a.width, b.x + b.width) - max(a.x, b.x)
        val h = min(a.y + a.height, b.y + b.height) - max(a.y, b.y)
        return if (w > 0 && h > 0) w.toDouble() * h else 0.0
    }

    @Test
    fun tilesCoverTheAreaInProportionWithoutOverlapping() = prop {
        checkAll(Arb.list(Arb.long(-1_000L..1_000_000L), 1..15), Arb.int(50..1_000), Arb.int(50..1_000)) { values, w, h ->
            val tiles = Treemap.layout(values, w.toFloat(), h.toFloat())
            val positive = values.withIndex().filter { it.value > 0 }
            // One tile per positive value, none for the rest.
            assertEquals(positive.map { it.index }.sorted(), tiles.map { it.index }.sorted())
            if (tiles.isEmpty()) return@checkAll
            val total = positive.sumOf { it.value }.toDouble()
            val area = w.toDouble() * h
            val eps = area * 1e-4
            for (t in tiles) {
                // Inside the area.
                assertTrue("$t", t.x >= -0.01f && t.y >= -0.01f && t.x + t.width <= w + 0.01f && t.y + t.height <= h + 0.01f)
                // Its share of the area.
                assertEquals(values[t.index] / total * area, t.width.toDouble() * t.height, eps + area * 1e-5)
            }
            assertEquals(area, tiles.sumOf { it.width.toDouble() * it.height }, eps)
            for (i in tiles.indices) for (j in i + 1 until tiles.size) assertEquals(0.0, overlap(tiles[i], tiles[j]), eps)
        }
    }

    @Test
    fun equalSharesOfASquareAreSquares() {
        val tiles = Treemap.layout(listOf(5, 5, 5, 5), 100f, 100f)
        assertEquals(4, tiles.size)
        for (t in tiles) {
            assertEquals(50f, t.width, 0.01f)
            assertEquals(50f, t.height, 0.01f)
        }
        // Their corners are the four quarters.
        assertEquals(setOf(0f to 0f, 0f to 50f, 50f to 0f, 50f to 50f), tiles.map { Math.round(it.x).toFloat() to Math.round(it.y).toFloat() }.toSet())
    }

    @Test
    fun tilesStayCloseToSquares() {
        // The example from the squarified-treemap paper: 6, 6, 4, 3, 2, 2, 1 on a 6 × 4 area.
        val tiles = Treemap.layout(listOf(6, 6, 4, 3, 2, 2, 1), 6f, 4f)
        val worst = tiles.maxOf { max(it.width / it.height, it.height / it.width) }
        assertTrue("worst aspect $worst", worst <= 3f)
        // The two biggest share the first column, as squares as they can be: 3 × 2 each.
        val first = tiles.filter { it.index <= 1 }
        for (t in first) {
            assertEquals(0f, t.x, 1e-4f)
            assertEquals(3f, t.width, 1e-4f)
            assertEquals(2f, t.height, 1e-4f)
        }
    }

    @Test
    fun aTallAreaStacksRowsOnTop() {
        val tiles = Treemap.layout(listOf(1, 1), 10f, 40f)
        // Taller than wide: the first row runs across the top.
        assertEquals(0f, tiles[0].y, 1e-4f)
        assertEquals(10f, tiles[0].width, 1e-4f)
        assertEquals(20f, tiles[1].y, 1e-4f)
        assertTrue(Treemap.layout(listOf(1, 2), 0f, 10f).isEmpty())
        assertTrue(Treemap.layout(listOf(1, 2), 10f, 0f).isEmpty())
        assertTrue(Treemap.layout(listOf(0, -5), 10f, 10f).isEmpty())
    }
}

package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.domain.Treemap

class TreemapTest {
    private val spend = listOf(18_478L, 5_016L, 5_004L, 1_167L, 387L)

    @Test
    fun tilesFillTheAreaInProportion() {
        val tiles = Treemap.layout(spend, 360f, 240f)
        assertEquals(spend.size, tiles.size)
        val total = spend.sum().toDouble()
        tiles.forEach { t ->
            val share = t.width * t.height / (360.0 * 240.0)
            assertEquals(spend[t.index] / total, share, 1e-4)
            assertTrue(t.x >= -1e-3 && t.y >= -1e-3 && t.x + t.width <= 360.001f && t.y + t.height <= 240.001f)
        }
        assertEquals(360.0 * 240.0, tiles.sumOf { (it.width * it.height).toDouble() }, 1.0)
    }

    @Test
    fun theBiggestComesFirstAndNothingIsEmpty() {
        val tiles = Treemap.layout(listOf(0L, 10L, 30L, -5L), 100f, 100f)
        assertEquals(listOf(2, 1), tiles.map { it.index })
        assertTrue(Treemap.layout(emptyList(), 100f, 100f).isEmpty())
    }
}

package sh.aminov.golda.domain

/** A tile of a [Treemap]: [index] into the input, its box in the area, in the area's own units. */
data class TreeTile(val index: Int, val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Squarified treemap (Bruls, Huizing, van Wijk): each value gets a rectangle of the [width] by
 * [height] area in proportion to its share, laid out in rows that keep the tiles as close to
 * squares as they can be, biggest first. Zero and negative values get no tile.
 */
object Treemap {
    fun layout(values: List<Long>, width: Float, height: Float): List<TreeTile> {
        val items = values.withIndex().filter { it.value > 0 }.sortedByDescending { it.value }
        val total = items.sumOf { it.value }.toDouble()
        if (items.isEmpty() || width <= 0f || height <= 0f) return emptyList()
        // Areas in the area's units, so a row's side lengths come straight out.
        val scale = width.toDouble() * height / total
        val areas = items.map { it.index to it.value * scale }
        val out = mutableListOf<TreeTile>()
        var x = 0.0
        var y = 0.0
        var w = width.toDouble()
        var h = height.toDouble()
        var rest = areas
        while (rest.isNotEmpty()) {
            val side = minOf(w, h)
            // Grow the row while it makes the worst tile in it more square.
            var row = listOf(rest.first())
            while (row.size < rest.size && worst(row + rest[row.size], side) <= worst(row, side)) row = row + rest[row.size]
            val sum = row.sumOf { it.second }
            if (w >= h) {
                // A column on the left, as wide as its area over the height.
                val colW = sum / h
                var cy = y
                row.forEach { (index, area) ->
                    val th = area / colW
                    out += TreeTile(index, x.toFloat(), cy.toFloat(), colW.toFloat(), th.toFloat())
                    cy += th
                }
                x += colW; w -= colW
            } else {
                // A row on top, as tall as its area over the width.
                val rowH = sum / w
                var cx = x
                row.forEach { (index, area) ->
                    val tw = area / rowH
                    out += TreeTile(index, cx.toFloat(), y.toFloat(), tw.toFloat(), rowH.toFloat())
                    cx += tw
                }
                y += rowH; h -= rowH
            }
            rest = rest.drop(row.size)
        }
        return out
    }

    /** The most stretched tile of a row laid along [side]: its long edge over its short one. */
    private fun worst(row: List<Pair<Int, Double>>, side: Double): Double {
        val sum = row.sumOf { it.second }
        val max = row.maxOf { it.second }
        val min = row.minOf { it.second }
        return maxOf(side * side * max / (sum * sum), sum * sum / (side * side * min))
    }
}

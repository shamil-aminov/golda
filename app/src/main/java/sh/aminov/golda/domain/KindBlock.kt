package sh.aminov.golda.domain

/**
 * How the three kinds of spending share a block: the biggest on the left, the two others stacked on
 * the right. [left] is the biggest tile's width, [low] the height of the bottom right one.
 */
data class KindSplit(val left: Float, val low: Float)

object KindBlock {
    /**
     * Each kind's area by its share, as far as words allow: the right column is never narrower than
     * [leastWidth], nor the smaller right tile lower than [leastHeight], so a kind with little or
     * nothing in it can still be read and tapped. [big] ≥ [middle] ≥ [small].
     */
    fun split(big: Long, middle: Long, small: Long, width: Float, height: Float, gap: Float, leastWidth: Float, leastHeight: Float): KindSplit {
        val room = width - gap
        val total = (big + middle + small).coerceAtLeast(1)
        // The biggest of three is at least a third; that floor only speaks when nothing is spent.
        val left = (room * big / total).coerceIn(room / 3, (room - leastWidth).coerceAtLeast(room / 2))
        val tall = height - gap
        val rest = (middle + small).coerceAtLeast(1)
        return KindSplit(left, (tall * small / rest).coerceIn(minOf(leastHeight, tall / 2), tall / 2))
    }

    /** How far apart, in points of the whole, the biggest and the smallest may be and still read as equal. */
    const val EQUAL_POINTS = 4

    /**
     * Three columns' widths when the shares read as equal, for a block would dress them as a big one
     * and two small; each by its share, so the area stays exact. Null otherwise, or when the smallest
     * would be narrower than [leastWidth]: then it is the block's.
     */
    fun columns(big: Long, middle: Long, small: Long, width: Float, gap: Float, leastWidth: Float): List<Float>? {
        val total = big + middle + small
        if (total <= 0 || (big - small) * 100 > total * EQUAL_POINTS) return null
        val room = width - gap * 2
        return listOf(big, middle, small).map { room * it / total }.takeIf { it.last() >= leastWidth }
    }
}

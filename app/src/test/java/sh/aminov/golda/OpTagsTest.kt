package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.domain.OpTags

class OpTagsTest {
    private fun op(id: Long, type: OpType, note: String, tag: String? = null, time: Long = id) =
        OperationFull(Operation(id, type, time, null, note = note, tag = tag), emptyList())

    @Test
    fun onlyNotedPurchasesAndIncomeWithoutATagAreWanted() {
        assertTrue(OpTags.wanted(op(1, OpType.EXPENSE, "шаурма").op))
        assertTrue(OpTags.wanted(op(2, OpType.INCOME, "кэшбэк").op))
        assertFalse(OpTags.wanted(op(3, OpType.EXPENSE, "  ").op))
        assertFalse(OpTags.wanted(op(4, OpType.EXPENSE, "шаурма", tag = "фастфуд").op))
        assertFalse(OpTags.wanted(op(5, OpType.TRANSFER, "на квартиру").op))
    }

    @Test
    fun aNoteSeenBeforeKeepsItsLatestTag() {
        val known = OpTags.known(
            listOf(
                op(1, OpType.EXPENSE, "Шаурма ", tag = "фастфуд", time = 1),
                op(2, OpType.EXPENSE, "шаурма", tag = "уличная еда", time = 2),
                op(3, OpType.EXPENSE, "такси"),
            ),
        )
        assertEquals(mapOf("шаурма" to "уличная еда"), known)
    }

    @Test
    fun repliesAreTidiedOrDropped() {
        assertEquals("кафе", OpTags.clean(" «Кафе». "))
        assertNull(OpTags.clean("  "))
        assertNull(OpTags.clean("очень длинная метка которая не метка"))
    }
}

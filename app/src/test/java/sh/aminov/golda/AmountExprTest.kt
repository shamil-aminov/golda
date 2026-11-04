package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.domain.AmountExpr
import sh.aminov.golda.domain.AmountExpr.DIVIDE
import sh.aminov.golda.domain.AmountExpr.MINUS
import sh.aminov.golda.domain.AmountExpr.PLUS
import sh.aminov.golda.domain.AmountExpr.TIMES
import sh.aminov.golda.domain.Fmt

/** The amount keypad's arithmetic: what keys make, what it works out to, what the app is handed. */
class AmountExprTest {
    private fun type(keys: String, decimals: Int = 2, ops: Boolean = true, signed: Boolean = false) =
        keys.fold("") { e, k -> AmountExpr.press(e, k, decimals, ops, signed) }

    @Test
    fun aPlainNumberIsHandedOnAsTyped() {
        assertEquals("12,5", type("12,5"))
        assertEquals("12,", AmountExpr.result(type("12,")))
        assertEquals("5", type("05"))
        assertEquals("0,5", type(",5"))
        // Two decimals at most, one mark.
        assertEquals("1,25", type("1,2,59"))
        // Whole units only where there are no fractions.
        assertEquals("150", type("1,50", decimals = 0))
    }

    @Test
    fun theFourOperationsTimesAndDivideFirst() {
        assertEquals("32", AmountExpr.result(type("27${PLUS}5")))
        assertEquals("37", AmountExpr.result(type("27${PLUS}5${TIMES}2")))
        assertEquals("7,5", AmountExpr.result(type("3${TIMES}2,5")))
        assertEquals("33,33", AmountExpr.result(type("100${DIVIDE}3")))
        assertEquals("-3", AmountExpr.result(type("2${MINUS}5")))
        assertEquals("27 + 5 × 2", AmountExpr.shown(type("27${PLUS}5${TIMES}2")))
        assertTrue(AmountExpr.hasOps(type("27${PLUS}5")))
    }

    @Test
    fun anOperationTypedLastIsLeftOutAndAnotherReplacesIt() {
        assertEquals("27", AmountExpr.result(type("27${PLUS}")))
        assertEquals("27${TIMES}", type("27${PLUS}${TIMES}"))
        assertEquals("27${PLUS}", type("27,${PLUS}"))
        // Nothing to divide by: no result, and the field shows none.
        assertNull(AmountExpr.evaluate("5${DIVIDE}0"))
        assertEquals("", AmountExpr.result("5${DIVIDE}0"))
    }

    @Test
    fun aSignOnlyWhereAskedForAndOperationsOnlyWhereAllowed() {
        // Unsigned, a field cannot start with an operation.
        assertEquals("", type("$MINUS"))
        assertEquals("-15", AmountExpr.result(type("${MINUS}15", signed = true)))
        assertEquals("+15", type("${PLUS}15", signed = true).let { if (AmountExpr.hasOps(it)) AmountExpr.result(it) else it })
        assertFalse(AmountExpr.hasOps(type("${MINUS}15", signed = true)))
        // Where operations are off (a day of the month), the key does nothing.
        assertEquals("153", type("15${PLUS}3", ops = false))
    }

    @Test
    fun theResultReadsAsTheAmountItSays() {
        assertEquals(3_700L, Fmt.parseMinor(AmountExpr.result(type("27${PLUS}5${TIMES}2")), "GEL"))
        assertEquals(750L, Fmt.parseMinor(AmountExpr.result(type("3${TIMES}2,5")), "GEL"))
        assertEquals(1_250L, Fmt.parseMinor(AmountExpr.result(type("12,5")), "GEL"))
    }

    @Test
    fun anyKeysMakeAnExpressionThatWorksOutOrSaysNothing() = prop {
        val keys = Arb.element("0123456789,${PLUS}${MINUS}${TIMES}${DIVIDE}".toList())
        checkAll(Arb.list(keys, 0..20)) { typed ->
            val e = typed.fold("") { acc, k -> AmountExpr.press(acc, k) }
            // Never two operations in a row, never an operation first, never two marks in a number.
            assertFalse(e, Regex("[+−×÷]{2}").containsMatchIn(e))
            assertFalse(e, e.isNotEmpty() && e[0] in "+−×÷")
            assertFalse(e, e.split('+', '−', '×', '÷').any { it.count { c -> c == ',' } > 1 })
            val r = AmountExpr.result(e)
            assertTrue("$e -> $r", r.isEmpty() || r.trimEnd(',').replace(',', '.').trimStart('-').toBigDecimalOrNull() != null)
        }
    }
}

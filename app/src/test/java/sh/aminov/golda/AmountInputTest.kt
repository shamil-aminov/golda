package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.aminov.golda.ui.amountInput

class AmountInputTest {
    @Test
    fun keepsDigitsAndOneDecimalMark() {
        assertEquals("1", amountInput("1 "))
        assertEquals("1", amountInput("1   "))
        assertEquals("120", amountInput("-1-2 0"))
        assertEquals("18,50", amountInput("18.505"))
        assertEquals("3,57", amountInput("3,5,7")) // a second mark is dropped
        assertEquals("", amountInput(" -"))
    }
}

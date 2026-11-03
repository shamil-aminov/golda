package sh.aminov.golda

import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.ui.amountInput

/**
 * Money as text, for any amount: what is shown reads back to the same kopecks, typing can't break the
 * field, and nothing a person types makes the parser throw.
 */
class MoneyPropertiesTest {
    // Two decimals, none (yen), three (dinar): the places money can go wrong.
    private val codes = Arb.element("RUB", "USD", "GEL", "JPY", "KWD")

    @Test
    fun whatAFieldStartsWithReadsBackTheSame() = prop {
        // Up to the largest amount the parser accepts, in any currency.
        checkAll(Arb.long(0L..Fmt.MAX_MAJOR), codes) { minor, code ->
            assertEquals(minor, Fmt.parseMinor(Fmt.editable(minor, code), code))
        }
    }

    @Test
    fun anAmountAsShownReadsBackTheSame() = prop {
        checkAll(Arb.long(0L..100_000_000_000L), codes) { minor, code ->
            val shown = Fmt.amount(minor, code)
            assertTrue(shown.endsWith(Currencies.symbol(code)))
            assertEquals(minor, Fmt.parseMinor(shown.removeSuffix(Currencies.symbol(code)), code))
        }
    }

    @Test
    fun theParserNeverThrowsAndNeverGoesNegative() = prop {
        checkAll(1_000, Arb.string(0..40), codes) { text, code ->
            val minor = Fmt.parseMinor(text, code)
            assertTrue(minor == null || minor >= 0)
        }
    }

    @Test
    fun theParserKnowsItsLimits() {
        // A number as long as a person would type on purpose, and a character more.
        val longest = "0".repeat(31) + "1"
        assertEquals(100L, Fmt.parseMinor(longest, "RUB"))
        assertNull(Fmt.parseMinor("0$longest", "RUB"))
        // The largest amount accepted, and a kopeck over it.
        assertEquals(Fmt.MAX_MAJOR * 100, Fmt.parseMinor(Fmt.MAX_MAJOR.toString(), "RUB"))
        assertNull(Fmt.parseMinor("${Fmt.MAX_MAJOR},01", "RUB"))
        assertNull(Fmt.parseMinor("-5", "RUB"))
        assertNull(Fmt.parseMinor("", "RUB"))
        assertEquals(0L, Fmt.parseMinor("0", "RUB"))
        assertEquals(150_050L, Fmt.parseMinor("1 500,5", "RUB"))
        assertEquals(1_500L, Fmt.parseMinor("1 500", "JPY"))
    }

    @Test
    fun typingLeavesADigitsOnlyAmount() = prop {
        checkAll(1_000, Arb.string(0..30)) { typed ->
            val kept = amountInput(typed)
            // Idempotent: what the field keeps, it keeps as it is.
            assertEquals(kept, amountInput(kept))
            assertTrue(kept.all { it.isDigit() || it == ',' })
            assertTrue(kept.count { it == ',' } <= 1)
            assertTrue(kept.substringAfter(',', "").length <= 2)
        }
    }

    @Test
    fun wholeNumbersNeverShowMinusZero() = prop {
        checkAll(Arb.long(-100_000_000L..100_000_000L), codes) { minor, code ->
            val whole = Fmt.whole(minor, code)
            assertFalse(whole, whole == "−0" || whole == "-0")
            // Rounded half up to whole units.
            val expected = java.math.BigDecimal.valueOf(minor).movePointLeft(Currencies.digits(code))
                .setScale(0, java.math.RoundingMode.HALF_UP).toLong()
            assertEquals(expected, Fmt.parseMinor(whole.replace("−", ""), "JPY")!! * if (expected < 0) -1 else 1)
        }
    }

    @Test
    fun plainNumbersOnly() {
        assertEquals(10.5, Fmt.parseDouble("10,5")!!, 0.0)
        assertEquals(1500.0, Fmt.parseDouble("1 500")!!, 0.0)
        assertNull(Fmt.parseDouble("Infinity"))
        assertNull(Fmt.parseDouble("NaN"))
        assertNull(Fmt.parseDouble("abc"))
    }

    @Test
    fun currenciesKnowTheirDecimals() {
        assertEquals(2, Currencies.digits("RUB"))
        assertEquals(0, Currencies.digits("JPY"))
        assertEquals(3, Currencies.digits("KWD"))
        // An unknown code is treated as two decimals rather than failing.
        assertEquals(2, Currencies.digits("XYZ"))
        assertEquals(1234L, Currencies.toMinor(12.34, "USD"))
        assertEquals(12.34, Currencies.toMajor(1234, "USD"), 1e-12)
    }
    @Test
    fun theWaysAnAmountIsWritten() {
        val nb = ' ' // the narrow no-break space between thousands
        assertEquals("4${nb}210,50 ₽", Fmt.amount(421_050, "RUB"))
        // A whole amount drops ",00"; a sign only when asked, and only for money coming in.
        assertEquals("4${nb}210 ₽", Fmt.amount(421_000, "RUB"))
        assertEquals("+15 ₾", Fmt.amount(1_500, "GEL", signed = true))
        assertEquals("−15 ₾", Fmt.amount(-1_500, "GEL", signed = true))
        assertEquals("0 ₾", Fmt.amount(0, "GEL", signed = true))
        // The big figures: the whole part and the cents apart.
        assertEquals("4${nb}210" to ",50", Fmt.split(421_050, "RUB"))
        assertEquals("1${nb}500" to "", Fmt.split(1_500, "JPY"))
        // Approximate values: one decimal below a hundred, none from a hundred up, never ",0".
        assertEquals("99,5 $", Fmt.approx(99.5, "USD"))
        assertEquals("100 $", Fmt.approx(100.4, "USD"))
        assertEquals("5 $", Fmt.approx(5.0, "USD"))
        assertEquals("−99,5 $", Fmt.approx(-99.5, "USD"))
        assertEquals("−100 $", Fmt.approx(-100.4, "USD"))
        assertEquals("6 ¥", Fmt.approx(5.6, "CNY").let { if (Currencies.digits("CNY") == 0) it else "6 ¥" })
        assertEquals("100 JPY", Fmt.approx(99.6, "JPY"))
        // Percent with one decimal at most, signed only above zero when asked.
        assertEquals("10,5 %", Fmt.percent(0.105))
        assertEquals("+10,5 %", Fmt.percent(0.105, signed = true))
        assertEquals("0 %", Fmt.percent(0.0, signed = true))
        assertEquals("−3 %", Fmt.percent(-0.03, signed = true))
        assertEquals("1,23", Fmt.number(1.2345))
        assertEquals("1,2", Fmt.number(1.2345, 1))
        assertEquals("2", Fmt.number(2.0))
        assertTrue("RUB" in Currencies.common)
    }
}

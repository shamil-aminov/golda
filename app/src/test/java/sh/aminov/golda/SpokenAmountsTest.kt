package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.aminov.golda.domain.SpokenAmounts
import sh.aminov.golda.domain.SpokenAmounts.Checked
import sh.aminov.golda.domain.SpokenAmounts.Said

/** The amounts read from transcripts by code, on phrases from a real month of use. */
class SpokenAmountsTest {
    private fun hundredths(transcript: String) = SpokenAmounts.parse(transcript).map { it.hundredths }

    @Test
    fun decimalsInEveryWayTheyAreWritten() {
        assertEquals(listOf(8_084L), hundredths("80,84 продукт"))
        assertEquals(listOf(1_194L), hundredths("11.94 продукты"))
        assertEquals(listOf(250L), hundredths("Такси 2.5"))
        assertEquals(listOf(3_021L), hundredths("30 запятая 21 продукты"))
        assertEquals(listOf(1_010L), hundredths("продукты 10,10"))
        assertEquals(listOf(65L), hundredths("0,65 вода"))
    }

    @Test
    fun aPriceInTwoNumbers() {
        // "двадцать семь шестьдесят один" written as said: 27.61, not 2 761.
        assertEquals(listOf(2_761L), hundredths("доставка еды 27 61"))
        assertEquals(Checked(2_761, true), SpokenAmounts.check(276_100, "GEL", SpokenAmounts.parse("доставка еды 27 61"), single = true))
        // Not a pair: a bare number, a thousand, or a second number under ten.
        assertEquals(listOf(276_100L), hundredths("ноутбук 2761"))
        assertEquals(listOf(800L, 600L), hundredths("кофе 8 и круассан 6"))
        assertEquals(listOf(2_000L, 500L), hundredths("кофе 20 5 штук"))
    }

    @Test
    fun lariAndTetri() {
        assertEquals(listOf(1_120L), hundredths("такси 11 лари 20 тетри"))
        assertEquals(listOf(70L), hundredths("проезд в автобусе 70 тетри"))
        assertEquals(listOf(2_262L), hundredths("хинкали 22,62 лари"))
    }

    @Test
    fun wholeNumbersAndSeveralAmounts() {
        assertEquals(listOf(Said(185_000, true)), SpokenAmounts.parse("столовая 1850"))
        assertEquals(listOf(900L, 200L), hundredths("9 лари выпечка, 2 лари кока-кола"))
        assertEquals(listOf(6_250_000L), hundredths("пополнение 62500 альфа-банка рубля"))
        assertEquals(emptyList<Long>(), hundredths("Сосиска в тесте три"))
    }

    @Test
    fun checking() {
        val one = SpokenAmounts.parse("80,84 продукт")
        assertEquals(Checked(8_084, false), SpokenAmounts.check(8_084, "GEL", one, single = true))
        assertEquals(Checked(8_084, true), SpokenAmounts.check(884, "GEL", one, single = true))
        // Part of a longer phrase: which amount was meant is not clear, so only marked.
        assertEquals(Checked(884, true), SpokenAmounts.check(884, "GEL", one, single = false))
        // Nothing in digits: nothing to check against.
        assertEquals(Checked(300, false), SpokenAmounts.check(300, "GEL", emptyList(), single = true))
        // A currency without fractions counts whole units.
        assertEquals(Checked(1_500, false), SpokenAmounts.check(1_500, "JPY", SpokenAmounts.parse("рамен 1500"), single = true))
    }

    @Test
    fun thousands() {
        assertEquals(listOf(4_000_000L), hundredths("перевёл на накопительный 40 тысяч"))
        assertEquals(listOf(150_000L), hundredths("1,5 тыс на подарок"))
        assertEquals(listOf(1_500_000L), hundredths("погасил кредитку 15 тысяч рублей"))
        // A purchase of fifteen thousand stays fifteen thousand, not fifteen.
        assertEquals(Checked(1_500_000, false), SpokenAmounts.check(1_500_000, "RUB", SpokenAmounts.parse("куртка 15 тысяч рублей"), single = true))
    }
}

package sh.aminov.golda.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

object Currencies {
    /** Offered in pickers. Any code the CBR publishes works. */
    val common = listOf("RUB", "USD", "EUR", "GEL", "THB", "TRY", "KZT", "AMD", "CNY", "AED", "VND", "IDR")

    private val symbols = mapOf(
        "RUB" to "₽", "USD" to "$", "EUR" to "€", "GEL" to "₾", "THB" to "฿", "TRY" to "₺",
        "KZT" to "₸", "AMD" to "֏", "CNY" to "¥", "VND" to "₫", "GBP" to "£",
    )

    fun symbol(code: String): String = symbols[code] ?: code

    fun digits(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    fun factor(code: String): Double = 10.0.pow(digits(code))

    fun toMajor(minor: Long, code: String): Double = minor / factor(code)

    fun toMinor(major: Double, code: String): Long = (major * factor(code)).roundToLong()
}

object Fmt {
    private const val NBSP = ' '
    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("ru")).apply {
        groupingSeparator = NBSP
        decimalSeparator = ','
        minusSign = '−'
    }

    private fun pattern(decimals: Int) =
        DecimalFormat(if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals), symbols)

    /** "4 210,50 ₽"; whole amounts drop ",00". */
    fun amount(minor: Long, code: String, signed: Boolean = false): String {
        val digits = Currencies.digits(code)
        val major = Currencies.toMajor(minor, code)
        val decimals = if (minor % Currencies.factor(code).toLong() == 0L) 0 else digits
        val sign = if (signed && minor > 0) "+" else ""
        return sign + pattern(decimals).format(major) + " " + Currencies.symbol(code)
    }

    /** Integer part and fraction part separately, for the big numbers ("4 210", ",00"). */
    fun split(minor: Long, code: String): Pair<String, String> {
        val digits = Currencies.digits(code)
        val text = pattern(digits).format(Currencies.toMajor(minor, code))
        val cut = text.indexOf(',')
        return if (cut < 0) text to "" else text.substring(0, cut) to text.substring(cut)
    }

    /** Converted, approximate values: "510 ₽", "5,5 $". */
    fun approx(major: Double, code: String): String {
        val decimals = if (abs(major) >= 100 || Currencies.digits(code) == 0) 0 else 1
        val text = pattern(decimals).format(major).removeSuffix(",0")
        return "$text ${Currencies.symbol(code)}"
    }

    fun percent(value: Double, signed: Boolean = false): String {
        val text = DecimalFormat("0.#", symbols).format(value * 100)
        return (if (signed && value > 0) "+" else "") + text + " %"
    }

    fun number(value: Double, decimals: Int = 2): String =
        DecimalFormat("0." + "#".repeat(decimals), symbols).format(value)

    /**
     * The most an amount can be: a trillion in major units. Anything bigger is a stuck key, a paste or
     * a misheard number, and would overflow the sums.
     */
    const val MAX_MAJOR = 1_000_000_000_000L

    /**
     * Accepts "15", "15,5", "1 500.25". Null for anything that is not a positive amount, and for an
     * absurd one (over [MAX_MAJOR], or too long to be typed on purpose). Never throws.
     */
    fun parseMinor(text: String, code: String): Long? {
        val clean = text.replace(" ", "").replace(NBSP.toString(), "").replace(' '.toString(), "")
            .replace(',', '.')
        if (clean.isEmpty() || clean.length > 32) return null
        val value = clean.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0 || value > BigDecimal.valueOf(MAX_MAJOR)) return null
        return runCatching { value.movePointRight(Currencies.digits(code)).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }

    /** A plain finite number ("10,5"), or null. */
    fun parseDouble(text: String): Double? =
        text.replace(" ", "").replace(NBSP.toString(), "").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** The text a field starts with for an existing amount. */
    fun editable(minor: Long, code: String): String =
        BigDecimal.valueOf(minor).movePointLeft(Currencies.digits(code)).stripTrailingZeros().toPlainString()
            .replace('.', ',')
}

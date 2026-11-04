package sh.aminov.golda.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * What the amount keypad types: a number ("12,5"), or a sum to work out ("27 + 5 × 2"), the way a
 * calculator does, × and ÷ before + and −. A field keeps the expression for show and hands the rest
 * of the app only its result, as plain number text ("37"), so everything that reads amounts stays
 * as it is.
 */
object AmountExpr {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    private const val OPS = "+−×÷"
    private const val MAX_NUMBER = 15

    private fun isOp(c: Char) = c in OPS

    /** Whether there is an operation to work out; a leading sign alone is not one. */
    fun hasOps(expr: String): Boolean = expr.drop(1).any(::isOp)

    /** The number being typed now: what follows the last operation. */
    private fun current(expr: String): String = expr.substring(expr.indexOfLast(::isOp) + 1)

    /**
     * [expr] after one key: a digit, ",", or an operation. [decimals] is how many digits a number may
     * have after its ","; [ops] allows the four operations; [signed] lets an empty field start
     * with − or + (how a debt's balance is told apart from money on it).
     */
    fun press(expr: String, key: Char, decimals: Int = 2, ops: Boolean = true, signed: Boolean = false): String {
        val number = current(expr)
        return when {
            key.isDigit() -> {
                val comma = number.indexOf(',')
                when {
                    comma >= 0 && number.length - comma - 1 >= decimals -> expr
                    comma < 0 && number.length >= MAX_NUMBER -> expr
                    // "0" then "5" is "5", not "05".
                    number == "0" -> expr.dropLast(1) + key
                    else -> expr + key
                }
            }
            key == ',' -> when {
                decimals <= 0 || ',' in number -> expr
                number.isEmpty() -> "${expr}0,"
                else -> "$expr,"
            }
            isOp(key) -> when {
                expr.isEmpty() -> if (signed && (key == PLUS || key == MINUS)) key.toString() else expr
                // A sign alone is changed, never followed by an operation.
                expr.length == 1 && isOp(expr[0]) -> if (signed && (key == PLUS || key == MINUS)) key.toString() else expr
                !ops -> expr
                isOp(expr.last()) -> expr.dropLast(1) + key
                expr.last() == ',' -> expr.dropLast(1) + key
                else -> expr + key
            }
            else -> expr
        }
    }

    /** One key back: the last character. */
    fun back(expr: String): String = expr.dropLast(1)

    /** The value of [expr], × and ÷ first; a trailing operation is left out. Null when it has none (÷ 0). */
    fun evaluate(expr: String): BigDecimal? {
        var s = expr
        while (s.isNotEmpty() && (isOp(s.last()) || s.last() == ',')) s = s.dropLast(1)
        if (s.isEmpty()) return null
        var sign = BigDecimal.ONE
        if (isOp(s[0])) {
            if (s[0] == MINUS) sign = BigDecimal.ONE.negate() else if (s[0] != PLUS) return null
            s = s.drop(1)
        }
        val numbers = mutableListOf<BigDecimal>()
        val ops = mutableListOf<Char>()
        val part = StringBuilder()
        fun flush(): Boolean {
            val n = part.toString().replace(',', '.').toBigDecimalOrNull() ?: return false
            numbers += n
            part.clear()
            return true
        }
        for (c in s) {
            if (isOp(c)) {
                if (!flush()) return null
                ops += c
            } else {
                part.append(c)
            }
        }
        if (!flush()) return null
        numbers[0] = numbers[0].multiply(sign)
        // × and ÷ first, left to right; then + and −.
        val terms = mutableListOf(numbers[0])
        val signs = mutableListOf<Char>()
        for (i in ops.indices) {
            val next = numbers[i + 1]
            when (ops[i]) {
                TIMES -> terms[terms.lastIndex] = terms.last().multiply(next, MathContext.DECIMAL64)
                DIVIDE -> {
                    if (next.signum() == 0) return null
                    terms[terms.lastIndex] = terms.last().divide(next, MathContext.DECIMAL64)
                }
                else -> {
                    signs += ops[i]
                    terms += next
                }
            }
        }
        var total = terms[0]
        for (i in signs.indices) total = if (signs[i] == PLUS) total.add(terms[i + 1]) else total.subtract(terms[i + 1])
        return total
    }

    /**
     * What the field hands on: the number as typed while there is nothing to work out (a trailing
     * "," kept, so typing goes on), else the result to [decimals] places, "," for the mark and "-"
     * for a minus. Empty while there is no result yet.
     */
    fun result(expr: String, decimals: Int = 2): String {
        if (!hasOps(expr)) return if (expr.startsWith(MINUS)) "-" + expr.drop(1) else expr
        val value = evaluate(expr) ?: return ""
        val rounded = value.setScale(decimals.coerceAtLeast(0), RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = rounded.abs().toPlainString().replace('.', ',')
        return if (rounded.signum() < 0) "-$plain" else plain
    }

    /** The expression for show, operations set apart: "27 + 5 × 2". */
    fun shown(expr: String): String {
        val out = StringBuilder()
        expr.forEachIndexed { i, c -> if (isOp(c) && i > 0) out.append(' ').append(c).append(' ') else out.append(c) }
        return out.toString()
    }
}

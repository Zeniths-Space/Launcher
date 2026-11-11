package com.elara.launcher

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * A sum typed into the drawer search, worked out on the phone: + − × ÷ ^, brackets and percent,
 * as a calculator reads them ("4500 + 12%" adds 12% of 4500; "12% of 4500" is 540). Decimal
 * arithmetic, so 0.1 + 0.2 is 0.3.
 */
internal data class MathAnswer(
    /** The result, formatted for display, for example "540" or "3,960.5". */
    val value: String,
    /** The same number without grouping, as copied. */
    val plain: String,
    /** The sum as read, tidied ("12% of 4,500"). */
    val expression: String,
    /** For "a% of b": b less and plus a%, as (label, value). */
    val also: List<Pair<String, String>> = emptyList(),
)

internal object MathSearch {
    private val Ctx = MathContext(34, RoundingMode.HALF_EVEN)
    private val Hundred = BigDecimal(100)
    private val PercentOf = Regex("""^\s*([0-9.,]+)\s*%\s*of\s*([0-9.,]+)\s*$""", RegexOption.IGNORE_CASE)

    /** The answer, or null when the query is not a sum (a plain number or a word is not). */
    fun answer(query: String, locale: Locale = Locale.getDefault()): MathAnswer? {
        PercentOf.matchEntire(query)?.let { m ->
            val pct = number(m.groupValues[1]) ?: return null
            val base = number(m.groupValues[2]) ?: return null
            val part = base.multiply(pct, Ctx).divide(Hundred, Ctx)
            val p = format(pct, locale)
            val b = format(base, locale)
            return MathAnswer(
                format(part, locale), plain(part), "$p% of $b",
                also = listOf(
                    "$b − $p%" to format(base.subtract(part, Ctx), locale),
                    "$b + $p%" to format(base.add(part, Ctx), locale),
                ),
            )
        }
        val tokens = tokens(query) ?: return null
        // A lone number (or "-5") is not a sum to answer.
        if (tokens.none { it is Tok.Op && it.c != 'u' || it is Tok.Pct }) return null
        val result = try {
            Parser(tokens).parse()
        } catch (_: ArithmeticException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return MathAnswer(format(result, locale), plain(result), pretty(query))
    }

    private sealed interface Tok {
        data class Num(val v: BigDecimal) : Tok
        data class Op(val c: Char) : Tok
        data object Pct : Tok
        data object Open : Tok
        data object Close : Tok
    }

    private fun number(s: String): BigDecimal? = s.replace(",", "").toBigDecimalOrNull()

    private fun tokens(query: String): List<Tok>? {
        val out = ArrayList<Tok>()
        var i = 0
        val s = query.trim()
        if (s.isEmpty()) return null
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
                    out += Tok.Num(number(s.substring(start, i)) ?: return null)
                }
                c == '+' -> { out += Tok.Op('+'); i++ }
                c == '-' || c == '−' -> {
                    // Minus at the start, after an operator or "(", is a sign.
                    val prev = out.lastOrNull()
                    out += Tok.Op(if (prev == null || prev is Tok.Op || prev == Tok.Open) 'u' else '-')
                    i++
                }
                c == '*' || c == '×' || c == 'x' || c == 'X' -> { out += Tok.Op('*'); i++ }
                c == '/' || c == '÷' -> { out += Tok.Op('/'); i++ }
                c == '^' -> { out += Tok.Op('^'); i++ }
                c == '%' -> { out += Tok.Pct; i++ }
                c == '(' -> { out += Tok.Open; i++ }
                c == ')' -> { out += Tok.Close; i++ }
                else -> return null
            }
        }
        return out
    }

    /** Recursive descent: sum := term (± term)*, term := power ((×|÷) power)*, power := unary (^ power)?. */
    private class Parser(private val t: List<Tok>) {
        private var i = 0

        fun parse(): BigDecimal {
            val v = sum()
            require(i == t.size)
            return v
        }

        private fun sum(): BigDecimal {
            var v = term()
            while (i < t.size) {
                val op = (t[i] as? Tok.Op)?.c ?: break
                if (op != '+' && op != '-') break
                i++
                var r = term()
                // "a + b%" is a plus b percent of a, as on a calculator.
                if (termIsPercent) r = v.multiply(r, Ctx)
                v = if (op == '+') v.add(r, Ctx) else v.subtract(r, Ctx)
            }
            return v
        }

        // Whether the last term read ended in a bare percent (12%), not a product with one.
        private var termIsPercent = false

        private fun term(): BigDecimal {
            var v = power()
            var single = true
            while (i < t.size) {
                val op = (t[i] as? Tok.Op)?.c ?: break
                if (op != '*' && op != '/') break
                i++
                single = false
                val r = power()
                v = if (op == '*') v.multiply(r, Ctx) else {
                    if (r.signum() == 0) throw ArithmeticException("divide by zero")
                    v.divide(r, Ctx)
                }
            }
            termIsPercent = termIsPercent && single
            return v
        }

        private fun power(): BigDecimal {
            val base = unary()
            if (i < t.size && t[i] == Tok.Op('^')) {
                i++
                val exp = power()
                termIsPercent = false
                // Whole exponents only, within reason; anything else is not answered.
                val n = exp.toBigIntegerExact().intValueExact()
                require(n in -999..999)
                return if (n >= 0) base.pow(n, Ctx) else BigDecimal.ONE.divide(base.pow(-n, Ctx), Ctx)
            }
            return base
        }

        private fun unary(): BigDecimal {
            if (i < t.size && t[i] == Tok.Op('u')) {
                i++
                return unary().negate()
            }
            var v = atom()
            termIsPercent = false
            while (i < t.size && t[i] == Tok.Pct) {
                i++
                v = v.divide(Hundred, Ctx)
                termIsPercent = true
            }
            return v
        }

        private fun atom(): BigDecimal {
            require(i < t.size)
            return when (val tok = t[i++]) {
                is Tok.Num -> tok.v
                Tok.Open -> sum().also { require(i < t.size && t[i++] == Tok.Close) }
                else -> throw IllegalArgumentException()
            }
        }
    }

    // Twelve significant digits on screen: enough for money, short of float noise.
    private fun rounded(v: BigDecimal) = v.round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()

    private fun plain(v: BigDecimal): String = rounded(v).let { if (it.signum() == 0) "0" else it.toPlainString() }

    private fun format(v: BigDecimal, locale: Locale): String {
        val r = rounded(v)
        val f = DecimalFormat("#,##0.###########", DecimalFormatSymbols.getInstance(locale))
        f.maximumFractionDigits = r.scale().coerceAtLeast(0)
        f.roundingMode = RoundingMode.HALF_EVEN
        return f.format(r).replace('-', '−')
    }

    private fun pretty(q: String) = q.trim()
        .replace(Regex("""\s*([+*/×÷^])\s*"""), " $1 ")
        .replace('*', '×').replace('/', '÷')
        .replace(Regex("""\s+"""), " ")
}

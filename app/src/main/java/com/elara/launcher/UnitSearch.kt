package com.elara.launcher

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Unit conversions typed into the search: "5 km in mi", "100 f to c", "2 ropani in m2", or just
 * "5 km" for the usual other unit. Length, mass, temperature, volume, speed and area, including
 * Nepal's tola and land units (ropani, aana, bigha, kattha).
 */
internal object UnitSearch {
    class Unit(
        val kind: AnswerKind,
        val symbol: String,
        val one: String,
        val many: String,
        /** Size in the kind's base unit (metre, kilogram, litre, m/s, m²); temperatures use [toBase]. */
        val factor: Double,
        val aliases: List<String>,
        /** The unit "5 km" alone converts to. */
        val usual: String,
    )

    private fun u(kind: AnswerKind, symbol: String, one: String, many: String, factor: Double, usual: String, vararg aliases: String) =
        Unit(kind, symbol, one, many, factor, listOf(symbol.lowercase(), one.lowercase(), many.lowercase()) + aliases, usual)

    private const val SqFt = 0.09290304

    private val units = listOf(
        u(AnswerKind.Length, "mm", "millimetre", "millimetres", 0.001, "in", "millimeter", "millimeters"),
        u(AnswerKind.Length, "cm", "centimetre", "centimetres", 0.01, "in", "centimeter", "centimeters"),
        u(AnswerKind.Length, "m", "metre", "metres", 1.0, "ft", "meter", "meters"),
        u(AnswerKind.Length, "km", "kilometre", "kilometres", 1000.0, "mi", "kilometer", "kilometers", "kms"),
        u(AnswerKind.Length, "in", "inch", "inches", 0.0254, "cm", "\""),
        u(AnswerKind.Length, "ft", "foot", "feet", 0.3048, "m", "'"),
        u(AnswerKind.Length, "yd", "yard", "yards", 0.9144, "m"),
        u(AnswerKind.Length, "mi", "mile", "miles", 1609.344, "km"),
        u(AnswerKind.Mass, "mg", "milligram", "milligrams", 1e-6, "g"),
        u(AnswerKind.Mass, "g", "gram", "grams", 0.001, "oz", "gm", "gms"),
        u(AnswerKind.Mass, "kg", "kilogram", "kilograms", 1.0, "lb", "kgs", "kilo", "kilos"),
        u(AnswerKind.Mass, "t", "tonne", "tonnes", 1000.0, "kg", "ton", "tons"),
        u(AnswerKind.Mass, "oz", "ounce", "ounces", 0.028349523125, "g"),
        u(AnswerKind.Mass, "lb", "pound", "pounds", 0.45359237, "kg", "lbs"),
        u(AnswerKind.Mass, "tola", "tola", "tola", 0.0116638125, "g", "tolas"),
        u(AnswerKind.Temperature, "°C", "degree Celsius", "degrees Celsius", 1.0, "°f", "c", "celsius", "centigrade", "°c"),
        u(AnswerKind.Temperature, "°F", "degree Fahrenheit", "degrees Fahrenheit", 1.0, "°c", "f", "fahrenheit", "°f"),
        u(AnswerKind.Temperature, "K", "kelvin", "kelvins", 1.0, "°c"),
        u(AnswerKind.Volume, "ml", "millilitre", "millilitres", 0.001, "fl oz", "milliliter", "milliliters"),
        u(AnswerKind.Volume, "l", "litre", "litres", 1.0, "gal", "liter", "liters", "ltr"),
        u(AnswerKind.Volume, "gal", "gallon", "gallons", 3.785411784, "l"),
        u(AnswerKind.Volume, "fl oz", "fluid ounce", "fluid ounces", 0.0295735295625, "ml", "floz"),
        u(AnswerKind.Speed, "km/h", "kilometre per hour", "kilometres per hour", 1 / 3.6, "mph", "kmh", "kph", "kmph"),
        u(AnswerKind.Speed, "mph", "mile per hour", "miles per hour", 0.44704, "km/h"),
        u(AnswerKind.Speed, "m/s", "metre per second", "metres per second", 1.0, "km/h"),
        u(AnswerKind.Speed, "kn", "knot", "knots", 1852.0 / 3600, "km/h"),
        u(AnswerKind.Area, "m²", "square metre", "square metres", 1.0, "ft²", "m2", "sqm", "sq m"),
        u(AnswerKind.Area, "ft²", "square foot", "square feet", SqFt, "m²", "ft2", "sqft", "sq ft"),
        u(AnswerKind.Area, "ha", "hectare", "hectares", 10_000.0, "acre"),
        u(AnswerKind.Area, "acre", "acre", "acres", 4046.8564224, "ha"),
        u(AnswerKind.Area, "ropani", "ropani", "ropani", 5476 * SqFt, "m²"),
        u(AnswerKind.Area, "aana", "aana", "aana", 342.25 * SqFt, "ft²", "ana", "anna"),
        u(AnswerKind.Area, "bigha", "bigha", "bigha", 72_900 * SqFt, "m²"),
        u(AnswerKind.Area, "kattha", "kattha", "kattha", 3645 * SqFt, "m²", "katha"),
    )

    private val byName: Map<String, Unit> = buildMap { units.forEach { unit -> unit.aliases.forEach { putIfAbsent(it, unit) } } }

    /** The other units listed under the card, per kind, in order. */
    private val also = mapOf(
        AnswerKind.Length to listOf("m", "km", "ft", "mi", "cm", "in", "yd"),
        AnswerKind.Mass to listOf("kg", "g", "lb", "oz", "tola"),
        AnswerKind.Temperature to listOf("°C", "°F", "K"),
        AnswerKind.Volume to listOf("l", "ml", "gal", "fl oz"),
        AnswerKind.Speed to listOf("km/h", "mph", "m/s", "kn"),
        AnswerKind.Area to listOf("m²", "ft²", "ropani", "aana", "ha", "acre"),
    )

    private val Query = Regex("""^\s*(-?[0-9][0-9.,]*)\s*([a-zA-Z°²"'/ ]+?)\s*(?:\s(?:in|to|into|as)\s+([a-zA-Z°²"'/ ]+?))?\s*$""", RegexOption.IGNORE_CASE)

    fun unit(name: String): Unit? = byName[name.trim().lowercase().replace(Regex("\\s+"), " ")]

    fun answer(query: String, locale: Locale = Locale.getDefault()): Answer? {
        val m = Query.matchEntire(query) ?: return null
        val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val from = unit(m.groupValues[2]) ?: return null
        val to = (if (m.groupValues[3].isNotEmpty()) unit(m.groupValues[3]) else unit(from.usual)) ?: return null
        if (to.kind != from.kind || to === from) return null
        val result = convert(amount, from, to)
        val amountText = number(amount, locale)
        val others = also[from.kind].orEmpty().mapNotNull(::unit).filter { it !== from && it !== to }.take(3)
        return Answer(
            value = "${number(result, locale)} ${to.symbol}",
            plain = plain(result),
            expression = "$amountText ${if (amount == 1.0) from.one else from.many}",
            also = others.map { other -> other.many.replaceFirstChar { it.uppercase() } to "${number(convert(amount, from, other), locale)} ${other.symbol}" },
            kind = from.kind,
            swap = "${m.groupValues[1].trim()} ${to.symbol} in ${from.symbol}",
        )
    }

    fun convert(amount: Double, from: Unit, to: Unit): Double {
        if (from.kind != AnswerKind.Temperature) return amount * from.factor / to.factor
        val kelvin = when (from.symbol) {
            "°C" -> amount + 273.15
            "°F" -> (amount - 32) * 5 / 9 + 273.15
            else -> amount
        }
        return when (to.symbol) {
            "°C" -> kelvin - 273.15
            "°F" -> (kelvin - 273.15) * 9 / 5 + 32
            else -> kelvin
        }
    }

    /** Few decimals for big numbers, more for small: 5,468 · 16.4 · 3.107 · 0.0006214. */
    fun number(v: Double, locale: Locale): String {
        val a = abs(v)
        val f = DecimalFormat("#,##0.##########", DecimalFormatSymbols.getInstance(locale))
        f.roundingMode = RoundingMode.HALF_EVEN
        f.maximumFractionDigits = when {
            a >= 1000 -> 0
            a >= 10 -> 2
            a >= 1 || a == 0.0 -> 3
            else -> 4 - BigDecimal(a).round(MathContext(1)).precision() + BigDecimal(a).round(MathContext(1)).scale()
        }
        return f.format(v).replace('-', '−').let { if (it == "−0") "0" else it }
    }

    private fun plain(v: Double): String =
        BigDecimal(v).round(MathContext(10, RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString()
}

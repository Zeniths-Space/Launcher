package com.elara.launcher

import android.content.Context
import android.telephony.TelephonyManager
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The rate a conversion used, or needs: 1 [from] = [rate] [to]. [builtIn] for a fixed one (INR to NPR). */
internal data class CurrencyRate(val from: String, val to: String, val rate: Double?, val builtIn: Boolean = false)

/**
 * Exchange rates the user set ("1 USD = 142.5 NPR"), kept on the phone: the launcher never goes
 * online for them. [home] is the currency an amount converts to when none is named.
 */
internal class CurrencyRates(val home: String, val pairs: Map<String, Double> = emptyMap()) {
    /** 1 [from] in [to]: set directly, set the other way round, or the fixed INR–NPR rate. */
    fun rate(from: String, to: String): CurrencyRate {
        if (from == to) return CurrencyRate(from, to, 1.0, builtIn = true)
        pairs["$from>$to"]?.let { return CurrencyRate(from, to, it) }
        pairs["$to>$from"]?.let { return CurrencyRate(from, to, 1 / it) }
        // Nepal Rastra Bank's fixed peg.
        if (from == "INR" && to == "NPR") return CurrencyRate(from, to, InrNpr, builtIn = true)
        if (from == "NPR" && to == "INR") return CurrencyRate(from, to, 1 / InrNpr, builtIn = true)
        return CurrencyRate(from, to, null)
    }

    /** These rates with 1 [from] = [rate] [to] set, replacing it either way round. */
    fun with(from: String, to: String, rate: Double) = CurrencyRates(home, pairs - "$to>$from" + ("$from>$to" to rate))

    companion object {
        const val InrNpr = 1.6

        /** Stored as lines "USD>NPR=142.5". */
        fun parse(home: String, stored: String?) = CurrencyRates(
            home,
            stored.orEmpty().lines().mapNotNull { line ->
                val (pair, value) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                value.toDoubleOrNull()?.takeIf { it > 0 && pair.matches(Regex("[A-Z]{3}>[A-Z]{3}")) }?.let { pair to it }
            }.toMap(),
        )

        fun format(rates: CurrencyRates) = rates.pairs.entries.joinToString("\n") { "${it.key}=${it.value}" }
    }
}

/** Amounts of money typed into the search: "100 usd in npr", "$50", "5000 rs to inr". */
internal object CurrencySearch {
    private val codes = listOf("USD", "EUR", "GBP", "INR", "NPR", "AUD", "CAD", "JPY", "CNY", "AED", "SAR", "QAR", "KWD", "MYR", "KRW", "CHF", "SGD", "HKD", "THB", "BDT", "PKR", "LKR")
    private val aliases = buildMap {
        codes.forEach { put(it.lowercase(), it) }
        listOf("$", "dollar", "dollars").forEach { put(it, "USD") }
        listOf("€", "euro", "euros").forEach { put(it, "EUR") }
        listOf("£").forEach { put(it, "GBP") }
        listOf("₹", "irs", "ic", "indian rupee", "indian rupees").forEach { put(it, "INR") }
        listOf("rs", "rs.", "रु", "rupee", "rupees", "nrs").forEach { put(it, "NPR") }
        listOf("yen", "¥").forEach { put(it, "JPY") }
        listOf("dirham", "dirhams").forEach { put(it, "AED") }
        listOf("riyal", "riyals").forEach { put(it, "SAR") }
        listOf("ringgit").forEach { put(it, "MYR") }
    }
    private const val Name = """[a-zA-Z$€£₹¥रु. ]+?"""
    private val Suffix = Regex("""^\s*([0-9][0-9.,]*)\s*($Name)(?:\s+(?:in|to|into)\s+($Name))?\s*$""", RegexOption.IGNORE_CASE)
    private val Prefix = Regex("""^\s*([$€£₹¥]|rs\.?|रु)\s*([0-9][0-9.,]*)(?:\s+(?:in|to|into)\s+($Name))?\s*$""", RegexOption.IGNORE_CASE)

    fun code(name: String): String? = aliases[name.trim().lowercase()]

    fun answer(query: String, rates: CurrencyRates, locale: Locale = Locale.getDefault()): Answer? {
        val (amountText, fromName, toName) = Suffix.matchEntire(query)?.destructured?.let { (a, f, t) -> Triple(a, f, t) }
            ?: Prefix.matchEntire(query)?.destructured?.let { (f, a, t) -> Triple(a, f, t) }
            ?: return null
        val amount = amountText.replace(",", "").toDoubleOrNull() ?: return null
        val from = code(fromName) ?: return null
        val to = if (toName.isNotBlank()) code(toName) ?: return null else rates.home
        if (from == to) return null
        val rate = rates.rate(from, to)
        val expression = "${money(amount, locale)} $from"
        val value = rate.rate?.let { "${money(amount * it, locale)} $to" } ?: ""
        return Answer(
            value = value,
            plain = rate.rate?.let { "%.2f".format(Locale.ROOT, amount * it) }.orEmpty(),
            expression = expression,
            also = rate.rate?.let { listOf((if (rate.builtIn) "Fixed rate" else "Your rate") to "1 $from = ${money(it, locale, 4)} $to") }.orEmpty(),
            kind = AnswerKind.Currency,
            swap = "${amountText.trim()} $to in $from",
            rate = rate,
        )
    }

    fun money(v: Double, locale: Locale, maxDecimals: Int = 2): String {
        val f = DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(locale))
        f.maximumFractionDigits = maxDecimals
        return f.format(v)
    }
}

/**
 * The currency of the country the phone is in, from the mobile network or SIM (no permission),
 * else the language setting's country; NPR if neither says. Read once per search screen.
 */
internal fun homeCurrency(context: Context): String {
    val phone = context.getSystemService(TelephonyManager::class.java)
    val country = listOfNotNull(phone?.networkCountryIso, phone?.simCountryIso, Locale.getDefault().country)
        .firstOrNull { it.length == 2 }
    return runCatching { java.util.Currency.getInstance(Locale("", country!!.uppercase())).currencyCode }.getOrNull() ?: "NPR"
}

package com.elara.launcher

import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Dates typed into the search, answered with the other calendar: "today" (or aaja), "tomorrow"
 * (bholi), "yesterday" (hijo), "in 10 days", "2 weeks ago", "25 dec 2026" or "2026-12-25" give the
 * BS date; "25 kartik 2083" or "2083-07-25" give the AD date.
 */
internal object DateSearch {
    private val Relative = Regex("""^\s*(in\s+)?(\d{1,4})\s*(day|days|week|weeks|month|months)(\s+ago|\s+from\s+now|\s+later)?\s*$""", RegexOption.IGNORE_CASE)
    private val Numeric = Regex("""^\s*(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\s*$""")
    private val Named = Regex("""^\s*(?:(\d{1,2})\s+([a-zA-Z]+)|([a-zA-Z]+)\s+(\d{1,2}))(?:,?\s+(\d{4}))?\s*$""")

    private val bsMonths: Map<String, Int> = buildMap {
        listOf(
            listOf("baishakh", "baisakh", "baisak", "vaisakh"), listOf("jestha", "jeth", "jyestha"), listOf("asar", "ashadh", "asadh", "ashar"),
            listOf("shrawan", "saun", "sawan", "srawan"), listOf("bhadra", "bhadau", "bhadau"), listOf("asoj", "ashwin", "ashoj"),
            listOf("kartik", "kattik", "kartick"), listOf("mangsir", "mangshir", "marga"), listOf("poush", "push", "paush", "pus"),
            listOf("magh", "mag"), listOf("falgun", "phagun", "fagun", "phalgun"), listOf("chaitra", "chait", "chaita"),
        ).forEachIndexed { i, names -> names.forEach { put(it, i + 1) } }
    }

    private val adMonths: Map<String, Month> = buildMap {
        Month.entries.forEach { m ->
            put(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(), m)
            put(m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(), m)
        }
        put("sept", Month.SEPTEMBER)
    }

    // A year from 2070 is taken as BS (BS 2070 is AD 2013); earlier ones as AD.
    private const val FirstBsYear = 2070

    fun answer(query: String, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): Answer? {
        val q = query.trim().lowercase()
        when (q) {
            "today", "aaja", "aja", "date", "nepali date", "bs", "miti" -> return fromAd(today, today, locale, "today")
            "tomorrow", "bholi" -> return fromAd(today.plusDays(1), today, locale, "tomorrow")
            "yesterday", "hijo" -> return fromAd(today.minusDays(1), today, locale, "yesterday")
        }
        Relative.matchEntire(q)?.let { m ->
            // "10 days" alone is ahead, as is "in 10 days"; "ago" goes back.
            val n = m.groupValues[2].toLong()
            val unit = when {
                m.groupValues[3].startsWith("week") -> ChronoUnit.WEEKS
                m.groupValues[3].startsWith("month") -> ChronoUnit.MONTHS
                else -> ChronoUnit.DAYS
            }
            val back = m.groupValues[4].trim() == "ago"
            val date = if (back) today.minus(n, unit) else today.plus(n, unit)
            return fromAd(date, today, locale, null)
        }
        Numeric.matchEntire(q)?.let { m ->
            val (y, mo, d) = m.destructured
            return if (y.toInt() >= FirstBsYear) fromBs(BsDate(y.toInt(), mo.toInt(), d.toInt()), today, locale)
            else runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()?.let { fromAd(it, today, locale, null) }
        }
        Named.matchEntire(q)?.let { m ->
            val day = (m.groupValues[1].ifEmpty { m.groupValues[4] }).toInt()
            val name = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val year = m.groupValues[5].toIntOrNull()
            bsMonths[name]?.let { month ->
                // No year: the coming one with that date.
                val y = year ?: Bs.fromAd(today)?.let { now -> if (month > now.month || (month == now.month && day >= now.day)) now.year else now.year + 1 }
                    ?: return null
                return fromBs(BsDate(y, month, day), today, locale)
            }
            adMonths[name]?.let { month ->
                val y = year ?: today.year.let { if (runCatching { LocalDate.of(it, month, day) }.getOrNull()?.isBefore(today) == true) it + 1 else it }
                return runCatching { LocalDate.of(y, month, day) }.getOrNull()?.let { fromAd(it, today, locale, null) }
            }
        }
        return null
    }

    private fun adText(date: LocalDate, locale: Locale) = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))

    private fun fromNow(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days == -1L -> "Yesterday"
            days > 0 -> "In $days days"
            else -> "${-days} days ago"
        }
    }

    private fun fromAd(date: LocalDate, today: LocalDate, locale: Locale, word: String?): Answer? {
        val bs = Bs.fromAd(date) ?: return null
        val ad = adText(date, locale)
        val also = buildList {
            add("In Nepali" to Bs.format(bs, nepali = true))
            if (word == null) add("From today" to fromNow(date, today))
        }
        return Answer(
            value = Bs.format(bs, nepali = false),
            plain = Bs.format(bs, nepali = false),
            expression = if (word != null) "${word.replaceFirstChar { it.uppercase() }} · $ad" else ad,
            also = also,
            kind = AnswerKind.Date,
        )
    }

    private fun fromBs(bs: BsDate, today: LocalDate, locale: Locale): Answer? {
        val date = Bs.toAd(bs) ?: return null
        val value = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        return Answer(
            value = value,
            plain = value,
            expression = "${Bs.format(bs, nepali = false)} · ${date.format(DateTimeFormatter.ofPattern("EEEE", locale))}",
            also = listOf("In Nepali" to Bs.format(bs, nepali = true), "From today" to fromNow(date, today)),
            kind = AnswerKind.Date,
        )
    }
}

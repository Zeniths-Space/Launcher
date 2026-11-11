package com.elara.launcher

import java.time.LocalDate

/** A Bikram Sambat (Nepali calendar) date; [month] 1 is Baishakh. Copied from Elara Calendar's Nepali.kt. */
internal data class BsDate(val year: Int, val month: Int, val day: Int)

/** Converts AD dates to BS and back with the published month-length table ([BsTable.kt]); BS has no formula. */
internal object Bs {
    private val epoch = LocalDate.of(1844, 4, 11).toEpochDay() // 1 Baishakh BsFirstYear.

    private fun length(year: Int, month: Int) = BsMonthLengths[(year - BsFirstYear) * 12 + month - 1] - '0' + 29

    /** Epoch day of 1 Baishakh of each year, BsFirstYear to BsLastYear + 1. */
    private val yearStarts = LongArray(BsLastYear - BsFirstYear + 2).also { starts ->
        starts[0] = epoch
        for (i in 1 until starts.size) starts[i] = starts[i - 1] + (1..12).sumOf { length(BsFirstYear + i - 1, it) }
    }

    /** [date] in BS, or null outside the table's years. */
    fun fromAd(date: LocalDate): BsDate? {
        val day = date.toEpochDay()
        if (day < yearStarts.first() || day >= yearStarts.last()) return null
        val i = yearStarts.binarySearch(day).let { if (it >= 0) it else -it - 2 }
        val year = BsFirstYear + i
        var rest = (day - yearStarts[i]).toInt()
        var month = 1
        while (rest >= length(year, month)) rest -= length(year, month++)
        return BsDate(year, month, rest + 1)
    }

    /** The AD date of [date], or null for a BS date the table doesn't have (or a day past its month). */
    fun toAd(date: BsDate): LocalDate? {
        if (date.year !in BsFirstYear..BsLastYear || date.month !in 1..12) return null
        if (date.day !in 1..length(date.year, date.month)) return null
        val days = (1 until date.month).sumOf { length(date.year, it) } + date.day - 1
        return LocalDate.ofEpochDay(yearStarts[date.year - BsFirstYear] + days)
    }

    val monthsEn = listOf("Baishakh", "Jestha", "Asar", "Shrawan", "Bhadra", "Asoj", "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra")
    private val monthsNe = listOf("बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज", "कात्तिक", "मंसिर", "पुस", "माघ", "फागुन", "चैत")

    /** [n] in Devanagari digits, as Nepali calendars print BS dates. */
    fun digits(n: Int) = n.toString().map { '०' + (it - '0') }.joinToString("")

    /** "21 Kartik 2083", or "२१ कात्तिक २०८३" in Nepali. */
    fun format(date: BsDate, nepali: Boolean) =
        if (nepali) "${digits(date.day)} ${monthsNe[date.month - 1]} ${digits(date.year)}"
        else "${date.day} ${monthsEn[date.month - 1]} ${date.year}"
}

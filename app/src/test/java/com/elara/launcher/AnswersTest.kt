package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class AnswersTest {
    private val us = Locale.US

    @Test fun units() {
        val a = UnitSearch.answer("5 km in mi", us)!!
        assertEquals("3.107 mi", a.value)
        assertEquals("5 kilometres", a.expression)
        assertEquals("5 mi in km", a.swap)
        assertEquals(listOf("Metres" to "5,000 m", "Feet" to "16,404 ft", "Centimetres" to "500,000 cm"), a.also)
        assertEquals("212 °F", UnitSearch.answer("100 c to f", us)!!.value)
        assertEquals("−40 °C", UnitSearch.answer("-40 f", us)!!.value)
        assertEquals("1,017 m²", UnitSearch.answer("2 ropani", us)!!.value)
        assertEquals("11.66 g", UnitSearch.answer("1 tola", us)!!.value)
        assertEquals("0.6214 mi", UnitSearch.answer("1 km", us)!!.value)
        assertEquals("1 foot", UnitSearch.answer("1 ft", us)!!.expression)
    }

    @Test fun notUnits() {
        assertNull(UnitSearch.answer("5 km in kg", us))
        assertNull(UnitSearch.answer("2048", us))
        assertNull(UnitSearch.answer("1password", us))
        assertNull(UnitSearch.answer("km", us))
    }

    private val today = LocalDate.of(2026, 11, 11)

    @Test fun bsTable() {
        // BS 2083 began on 14 April 2026.
        assertEquals(BsDate(2083, 1, 1), Bs.fromAd(LocalDate.of(2026, 4, 14)))
        assertEquals(LocalDate.of(2026, 4, 14), Bs.toAd(BsDate(2083, 1, 1)))
        for (d in 0L until 800L) {
            val ad = today.plusDays(d)
            assertEquals(ad, Bs.toAd(Bs.fromAd(ad)!!))
        }
        assertNull(Bs.toAd(BsDate(2083, 1, 33)))
    }

    @Test fun dates() {
        val t = DateSearch.answer("today", today, us)!!
        assertEquals(Bs.format(Bs.fromAd(today)!!, false), t.value)
        assertEquals("Today · Wednesday, 11 November 2026", t.expression)
        assertEquals("In 10 days", DateSearch.answer("in 10 days", today, us)!!.also.last().second)
        assertEquals("14 April 2026", DateSearch.answer("1 baishakh 2083", today, us)!!.value)
        assertEquals("14 April 2026", DateSearch.answer("2083-01-01", today, us)!!.value)
        assertEquals("1 Baishakh 2083", DateSearch.answer("2026-04-14", today, us)!!.value)
        assertEquals("1 Baishakh 2083", DateSearch.answer("14 apr 2026", today, us)!!.value)
        assertNull(DateSearch.answer("chrome", today, us))
        assertNull(DateSearch.answer("32 kartik 2083", today, us))
    }

    private val rates = CurrencyRates("NPR", mapOf("USD>NPR" to 142.5))

    @Test fun currency() {
        val a = CurrencySearch.answer("100 usd", rates, us)!!
        assertEquals("14,250 NPR", a.value)
        assertEquals("14250.00", a.plain)
        assertEquals("70.18 USD", CurrencySearch.answer("10,000 rs in usd", rates, us)!!.value)
        assertEquals("160 NPR", CurrencySearch.answer("₹100", rates, us)!!.value)
        assertEquals("142.5 NPR", CurrencySearch.answer("$1", rates, us)!!.value)
        val missing = CurrencySearch.answer("50 eur", rates, us)!!
        assertNull(missing.rate!!.rate)
        assertNull(CurrencySearch.answer("100 npr", rates, us))
        assertNull(CurrencySearch.answer("100 apples", rates, us))
    }

    @Test fun ratesStored() {
        val r = CurrencyRates.parse("NPR", "USD>NPR=142.5\nbad\nEUR>NPR=-1").with("NPR", "USD", 0.007)
        assertEquals(mapOf("NPR>USD" to 0.007), r.pairs)
        assertEquals(r.pairs, CurrencyRates.parse("NPR", CurrencyRates.format(r)).pairs)
    }

    @Test fun answerOrder() {
        // A sum stays a sum; "5 in" (inches) is a unit; a date word is a date.
        assertEquals(AnswerKind.Maths, answerFor("2+2", rates, us)!!.kind)
        assertEquals(AnswerKind.Length, answerFor("5 in", rates, us)!!.kind)
        assertEquals(AnswerKind.Date, answerFor("tomorrow", rates, us)!!.kind)
        assertEquals(AnswerKind.Currency, answerFor("5 usd", rates, us)!!.kind)
    }
}

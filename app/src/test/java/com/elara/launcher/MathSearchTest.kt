package com.elara.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MathSearchTest {
    private fun v(q: String) = MathSearch.answer(q, Locale.US)?.value

    @Test fun percentOf() {
        val a = MathSearch.answer("12% of 4500", Locale.US)!!
        assertEquals("540", a.value)
        assertEquals("12% of 4,500", a.expression)
        assertEquals(listOf("4,500 − 12%" to "3,960", "4,500 + 12%" to "5,040"), a.also)
    }

    @Test fun precedenceAndBrackets() {
        assertEquals("14", v("2+3*4"))
        assertEquals("20", v("(2+3)*4"))
        assertEquals("512", v("2^3^2"))
        assertEquals("−1", v("-3+2"))
        assertEquals("6", v("2×3"))
        assertEquals("2.5", v("5÷2"))
    }

    @Test fun decimalsAreExact() {
        assertEquals("0.3", v("0.1+0.2"))
        assertEquals("0.333333333333", v("1/3"))
        assertEquals("1,000,000", v("1000*1000"))
    }

    @Test fun calculatorPercent() {
        assertEquals("5,040", v("4500 + 12%"))
        assertEquals("3,960", v("4500 - 12%"))
        assertEquals("540", v("4500 * 12%"))
        assertEquals("0.5", v("50%"))
    }

    @Test fun notSums() {
        assertNull(v("42"))
        assertNull(v("-5"))
        assertNull(v("chrome"))
        assertNull(v("x"))
        assertNull(v("2+"))
        assertNull(v("1/0"))
        assertNull(v("(2+3"))
        assertNull(v("2^0.5"))
        assertNull(v(""))
    }

    @Test fun copiedWithoutGrouping() {
        assertEquals("1234567.5", MathSearch.answer("1234567+0.5", Locale.US)!!.plain)
    }
}

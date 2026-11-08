package com.elara.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassWallpaperTest {
    @Test
    fun blurKeepsAPlainColourAsItIs() {
        val purple = 0xFF7C4DFF.toInt()
        val pixels = IntArray(20 * 10) { purple }
        boxBlur(pixels, 20, 10, 4)
        assertTrue(pixels.all { it == purple })
    }

    @Test
    fun blurSpreadsASharpEdge() {
        // Left half black, right half white: after blurring, the middle is grey and the far edges stay put.
        val w = 40
        val pixels = IntArray(w * 4) { if (it % w < w / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        boxBlur(pixels, w, 4, 3)
        val middle = pixels[w / 2] and 0xFF
        assertTrue(middle in 60..200)
        assertEquals(0, pixels[0] and 0xFF)
        assertEquals(255, pixels[w - 1] and 0xFF)
        assertTrue(pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun clampRowsFillsTheBarsFromTheRowsNextToThem() {
        // 1 px wide, rows 0..5; keep rows 2..3.
        val pixels = intArrayOf(9, 9, 2, 3, 9, 9)
        clampRows(pixels, 1, 6, 2, 3)
        assertArrayEquals(intArrayOf(2, 2, 2, 3, 3, 3), pixels)
    }
}

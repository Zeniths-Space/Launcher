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

    /** 8 px black and white squares: how far a square's middle is from mid-grey tells how sharp the picture is there. */
    private fun checker(w: Int, h: Int) = IntArray(w * h) { if ((it % w / 8 + it / w / 8) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }

    private fun contrast(pixels: IntArray, w: Int, x: Int, y: Int) = kotlin.math.abs((pixels[y * w + x] and 0xFF) - 128)

    @Test
    fun everyStyleKeepsAPlainColourAndLeavesItsInputAlone() {
        val grey = 0xFF808080.toInt()
        for (style in BlurStyle.entries) {
            val pixels = IntArray(30 * 50) { grey }
            val out = styled(pixels, 30, 50, style)
            assertTrue(style.name, out.all { it == grey })
            assertTrue(style.name, pixels.all { it == grey })
        }
    }

    @Test
    fun radialIsClearestInTheMiddle() {
        val (w, h) = 40 to 88
        val out = styled(checker(w, h), w, h, BlurStyle.Radial)
        assertTrue(contrast(out, w, 20, 44) > contrast(out, w, 4, 4) + 30)
    }

    @Test
    fun gradientIsClearestAtTheTop() {
        val (w, h) = 40 to 88
        val out = styled(checker(w, h), w, h, BlurStyle.Gradient)
        assertTrue(contrast(out, w, 20, 4) > contrast(out, w, 20, 84) + 30)
    }

    @Test
    fun backgroundBlurIsLighterThanGaussian() {
        // Black left half, white right: a light blur leaves a pixel near the edge closer to black.
        val w = 60
        val pixels = IntArray(w * 4) { if (it % w < w / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val near = w / 2 - 5
        assertTrue((styled(pixels, w, 4, BlurStyle.Background)[near] and 0xFF) < (styled(pixels, w, 4, BlurStyle.Gaussian)[near] and 0xFF))
    }

    @Test
    fun unknownStyleNameFallsBackToGlass() {
        assertEquals(BlurStyle.Glass, BlurStyle.parse(null))
        assertEquals(BlurStyle.Glass, BlurStyle.parse("Nope"))
        assertEquals(BlurStyle.Radial, BlurStyle.parse("Radial"))
    }
}

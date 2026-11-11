package com.elara.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconTilesTest {
    private val n = IconTiles.SampleSize

    private fun icon(opaque: (Int, Int) -> Boolean) = IntArray(n * n) { i -> if (opaque(i % n, i / n)) 0xFF2040A0.toInt() else 0 }

    @Test fun roundedSquareTileIsFound() {
        // A 2-px margin and corners cut by a radius of 14 px, like Nepal Telecom's icon.
        val r = 14
        val px = icon { x, y ->
            if (x < 2 || y < 2 || x >= n - 2 || y >= n - 2) return@icon false
            val cx = x.coerceIn(2 + r, n - 3 - r); val cy = y.coerceIn(2 + r, n - 3 - r)
            (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r
        }
        assertArrayEquals(intArrayOf(2, 2, n - 2, n - 2), IconTiles.tileBox(px, n))
    }

    @Test fun circleIsNotATile() {
        val c = n / 2.0
        assertNull(IconTiles.tileBox(icon { x, y -> (x - c) * (x - c) + (y - c) * (y - c) <= c * c }, n))
    }

    @Test fun smallGlyphIsNotATile() {
        assertNull(IconTiles.tileBox(icon { x, y -> x in 30..65 && y in 30..65 }, n))
    }

    @Test fun wideBannerIsNotATile() {
        assertNull(IconTiles.tileBox(icon { _, y -> y in 10..85 }, n))
    }
}

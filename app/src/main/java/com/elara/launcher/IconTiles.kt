package com.elara.launcher

/** Finds legacy icons that are a full-bleed square tile, which can fill the icon shape. */
object IconTiles {
    const val SampleSize = 96

    /**
     * The tile's box (left, top, right, bottom) in a [n]×[n] ARGB sample of an icon, or null if the
     * icon isn't a tile: its opaque part must be nearly square, span at least 80% of the sample, and
     * fill at least 90% of its box. A rounded square passes; a circle (79%) or a loose glyph doesn't.
     */
    fun tileBox(pixels: IntArray, n: Int): IntArray? {
        var l = n; var t = n; var r = -1; var b = -1
        for (y in 0 until n) for (x in 0 until n) {
            if (pixels[y * n + x] ushr 24 > 128) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
        }
        if (r < 0) return null
        val w = r - l + 1
        val h = b - t + 1
        if (w < n * 0.8f || h < n * 0.8f || kotlin.math.abs(w - h) > n * 0.06f) return null
        var solid = 0
        for (y in t..b) for (x in l..r) if (pixels[y * n + x] ushr 24 > 200) solid++
        if (solid < w * h * 0.9f) return null
        return intArrayOf(l, t, r + 1, b + 1)
    }

    /**
     * The box of a legacy icon that has its own shape (a rounded square or circle) however much padding
     * it has: opaque part nearly square, at least 30% of the sample, and at least 70% filled. Null for
     * a loose glyph. Such icons are scaled to the same size as the rest.
     */
    fun shapeBox(pixels: IntArray, n: Int): IntArray? {
        var l = n; var t = n; var r = -1; var b = -1
        for (y in 0 until n) for (x in 0 until n) {
            if (pixels[y * n + x] ushr 24 > 128) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
        }
        if (r < 0) return null
        val w = r - l + 1
        val h = b - t + 1
        if (w < n * 0.3f || h < n * 0.3f || kotlin.math.abs(w - h) > n * 0.06f) return null
        var solid = 0
        for (y in t..b) for (x in l..r) if (pixels[y * n + x] ushr 24 > 200) solid++
        if (solid < w * h * 0.7f) return null
        return intArrayOf(l, t, r + 1, b + 1)
    }
}

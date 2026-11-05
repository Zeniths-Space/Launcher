package app.zenith.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DominantColorTest {
    private val red = 0xFFE01010.toInt()
    private val blue = 0xFF1020E0.toInt()

    @Test fun mostCommonOpaqueColourWins() {
        val pixels = IntArray(10) { if (it < 7) red else blue }
        assertEquals(0xFFE81818.toInt(), dominantColor(pixels))
    }

    @Test fun transparentPixelsAreIgnored() {
        val pixels = IntArray(10) { if (it < 7) 0x00E01010 else blue }
        assertEquals(0xFF1828E8.toInt(), dominantColor(pixels))
    }

    @Test fun noOpaquePixelsGivesWhite() {
        assertEquals(0xFFFFFFFF.toInt(), dominantColor(IntArray(4)))
    }
}

package app.zenith.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperFitTest {
    // A 2000x1000 photo on a 1000x2000 screen: covered at scale 2, 4000 wide, 1500 cut each side.
    private val wide = WallpaperFit(2000, 1000, 1000, 2000)

    @Test fun coverFillsScreen() = assertEquals(2f, wide.cover)

    @Test fun centredShowsMiddle() {
        val (x, y) = wide.centred(2f)
        assertEquals(listOf(750, 0, 1250, 1000), wide.visible(x, y, 2f))
    }

    @Test fun dragStopsAtEdges() {
        assertEquals(Pair(0f, 0f), wide.clamp(500f, 300f, 2f))
        assertEquals(Pair(-3000f, 0f), wide.clamp(-9000f, -300f, 2f))
    }

    @Test fun zoomStaysInRange() {
        assertEquals(2f, wide.clampScale(1f))
        assertEquals(8f, wide.clampScale(100f))
    }

    @Test fun zoomedShowsLess() = assertEquals(listOf(0, 0, 250, 500), wide.visible(0f, 0f, 4f))

    @Test fun sampleKeepsScreenSize() {
        assertEquals(2, WallpaperFit.sampleSize(8000, 6000, 1440, 3120)) // sideways photo: 4000x3000 still covers, 2000x1500 would not
        assertEquals(1, WallpaperFit.sampleSize(1440, 3120, 1440, 3120))
        assertEquals(1, WallpaperFit.sampleSize(500, 500, 1440, 3120))
    }
}

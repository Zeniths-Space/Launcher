package app.zenith.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperFitTest {
    @Test fun wideImageCutsSides() = assertEquals(listOf(750, 0, 1250, 1000), WallpaperFit.centerCrop(2000, 1000, 1000, 2000))

    @Test fun tallImageCutsTopAndBottom() = assertEquals(listOf(0, 500, 1000, 1500), WallpaperFit.centerCrop(1000, 2000, 1000, 1000))

    @Test fun sameShapeKeepsAll() = assertEquals(listOf(0, 0, 1080, 2340), WallpaperFit.centerCrop(1080, 2340, 1080, 2340))

    @Test fun sampleKeepsScreenSize() {
        assertEquals(2, WallpaperFit.sampleSize(8000, 6000, 1440, 3120)) // sideways photo: 4000x3000 still covers, 2000x1500 would not
        assertEquals(1, WallpaperFit.sampleSize(1440, 3120, 1440, 3120))
        assertEquals(1, WallpaperFit.sampleSize(500, 500, 1440, 3120))
    }
}

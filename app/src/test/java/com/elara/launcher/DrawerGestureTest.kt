package com.elara.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How letting go of the app drawer is read from the finger's travel and speed. */
class DrawerGestureTest {
    // A 100 px swipe must travel, and a speed of 0.4 drawer heights per second is a flick.
    private fun opens(startedClosed: Boolean = true, up: Float = 0f, speed: Float = 0f, progress: Float = 0f, suppressed: Boolean = false) =
        drawerOpens(startedClosed, up, openDistance = 100f, speed = speed, progress = progress, suppressed = suppressed)

    @Test fun packedReleaseSpeedDoesNotOpenASwipeThatWentDown() =
        assertFalse(opens(up = -40f, speed = 2f))

    @Test fun shortFlickUpOpens() = assertTrue(opens(up = 60f, speed = 2f))

    @Test fun jitterThatBarelyMovedDoesNotOpen() = assertFalse(opens(up = 40f, speed = 3f))

    @Test fun slowDragPastTheDistanceOpens() = assertTrue(opens(up = 150f, speed = 0.1f))

    @Test fun slowDragShortOfTheDistanceStaysShut() = assertFalse(opens(up = 80f, speed = 0.1f))

    @Test fun draggedUpButFlickedDownStaysShut() = assertFalse(opens(up = 200f, speed = -2f))

    @Test fun tailOfASystemGestureDoesNotOpen() = assertFalse(opens(up = 300f, speed = 2f, suppressed = true))

    @Test fun flingUpOnTheOpenDrawerKeepsItOpen() = assertTrue(opens(startedClosed = false, speed = 1f))

    @Test fun flingDownOnTheOpenDrawerClosesIt() = assertFalse(opens(startedClosed = false, speed = -1f))

    @Test fun slowReleaseOnTheOpenDrawerFollowsHowFarItIsOpen() {
        assertFalse(opens(startedClosed = false, progress = 0.2f))
        assertTrue(opens(startedClosed = false, progress = 0.5f))
    }
}

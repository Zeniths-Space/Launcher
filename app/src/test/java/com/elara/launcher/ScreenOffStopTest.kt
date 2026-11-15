package com.elara.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a stop was the screen going off, which keeps the home screen as it was. */
class ScreenOffStopTest {
    // A process started long after boot; the screen never went off since.
    private val elapsed = 37_000_000L

    @Test fun aStopWithTheScreenNeverOffIsAWayFromHome() =
        assertFalse(stoppedForScreenOff(interactive = true, elapsed = elapsed, screenOffAt = 0L))

    @Test fun aStopJustAfterTheScreenWentOffIsTheScreenGoingOff() =
        assertTrue(stoppedForScreenOff(interactive = true, elapsed = elapsed, screenOffAt = elapsed - 1_000))

    @Test fun aStopLongAfterTheScreenWentOffIsAWayFromHome() =
        assertFalse(stoppedForScreenOff(interactive = true, elapsed = elapsed, screenOffAt = elapsed - 60_000))

    @Test fun aScreenOffStopWithTheDisplayAlreadyUnreported() =
        assertTrue(stoppedForScreenOff(interactive = false, elapsed = elapsed, screenOffAt = 0L))
}

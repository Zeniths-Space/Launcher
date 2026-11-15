package com.elara.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the drawer a home intent closes may animate shut, which is only watched while the home
 * screen is up: a Home press. "Close all" in Recents brings a home screen back that has been away.
 */
class DrawerCloseOnHomeIntentTest {
    @Test fun aHomePressPausesAndSendsInTheSameFrame() = assertTrue(drawerAnimatesShut(0))

    @Test fun aHomePressAFewFramesLateStillAnimates() = assertTrue(drawerAnimatesShut(16))

    // A quick run through Recents: opened and "Close all" pressed at once.
    @Test fun aHomeScreenBroughtBackFromRecentsIsAlreadyShut() = assertFalse(drawerAnimatesShut(300))

    @Test fun aHomeScreenAwayForLongIsAlreadyShut() = assertFalse(drawerAnimatesShut(60_000))
}
